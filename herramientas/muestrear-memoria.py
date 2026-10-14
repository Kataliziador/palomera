#!/usr/bin/env python3
"""Muestrea la memoria de la app durante un remojo, guardando el volcado entero.

## Por qué existe

El muestreador del remojo del 14-oct ejecutaba `dumpsys meminfo` cada 10
minutos y se quedaba con **tres** cifras: `TOTAL PSS`, `Java Heap` y
`Native Heap`. Con eso se vio que el heap nativo subía +1,63 MB/h durante
12 horas y no se pudo decir qué subía dentro, porque el volcado que lo
contestaba se descartaba en el mismo `awk` que lo leía.

Lo que se tiraba:

- las **tres** columnas de `Native Heap` —`Pss`, `Heap Size` y `Heap Alloc`—.
  Son las que distinguen «el asignador retiene arena que no devuelve al
  sistema» de «la app pide memoria y no la suelta», y es exactamente la
  pregunta que había que contestar;
- la tabla por categorías (`GL mtrack`, `.so mmap`, `Dalvik Other`,
  `Unknown`…), o sea en qué parte del proceso crece;
- el **censo de objetos**: `Views`, `Activities`, `AppContexts`,
  `Local Binders`, `Proxy Binders`, `Parcel count`, `Death Recipients`. Un
  `Activities: 7` donde debería haber 1 es una fuga encontrada, no una pista.

## El principio: guardar crudo, aplanar después

El bucle **no interpreta nada**: escribe el volcado completo con su marca de
hora y sigue. El aplanado a CSV es un segundo paso (`--aplanar`) sobre el
archivo crudo. Así, si el parseo tiene un error, se corrige y se vuelve a
aplanar sin haber perdido el experimento — que en un remojo de 30 h es lo
único irreemplazable.

## Y por qué es un proceso aparte

Se diseñó para arrancar con un remojo **ya en marcha**, sin tocar el
`capturar.sh` que lo está grabando. Bash lee el script de forma incremental
mientras corre: editar el que tiene el bucle vivo puede romper la captura del
logcat y costar las horas ya grabadas. Esta herramienta escribe en archivos
suyos y no comparte estado con nadie.

## Uso

    # arrancar, desatendido, junto a un remojo en curso
    setsid nohup python3 herramientas/muestrear-memoria.py \\
        --caja 192.168.20.132:5555 \\
        --paquete mx.kataliziador.palco.debug \\
        --destino /ruta/del/remojo --cada 600 >/dev/null 2>&1 &

    # una sola muestra, para ver que la caja contesta
    python3 herramientas/muestrear-memoria.py --caja ... --paquete ... --una

    # convertir lo grabado en una tabla
    python3 herramientas/muestrear-memoria.py --aplanar memoria-crudo.txt
"""

import argparse
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

SEPARADOR = "===== muestra"


def adb(caja: str, orden: str, espera: int = 30) -> str:
	"""Una orden por adb, devolviendo texto limpio de retornos de carro.

	Nunca lanza: un fallo de red a mitad de un remojo de 30 h no puede matar
	al muestreador, así que el error viaja como texto y queda en el volcado.
	"""
	try:
		hecho = subprocess.run(
			["adb", "-s", caja, "shell", orden],
			capture_output=True, text=True, timeout=espera,
		)
		return (hecho.stdout + hecho.stderr).replace("\r", "")
	except subprocess.TimeoutExpired:
		return f"<<adb agotó los {espera} s>>\n"
	except OSError as e:
		return f"<<no se pudo ejecutar adb: {e}>>\n"


def una_muestra(caja: str, paquete: str) -> str:
	"""El bloque de texto de una muestra: meminfo entero más los contadores.

	Los contadores de `/proc` van por `run-as` porque el usuario de `adb
	shell` no puede leer `/proc/<pid>/maps` ni `/proc/<pid>/fd` de otro
	proceso — da «Permission denied» y un cero que parece un dato. Con la app
	depurable, `run-as` sí los lee.

	`maps`, `fd` y `Threads` están aquí porque un `MediaCodec` que no se
	libera se ve en los tres antes de verse en el PSS.
	"""
	ahora = datetime.now(timezone.utc)
	pid = adb(caja, f"pidof {paquete}").strip().split(" ")[0]

	partes = [f"{SEPARADOR} {ahora.isoformat(timespec='seconds')} pid={pid or 'ninguno'}\n"]
	if not pid.isdigit():
		partes.append("<<el proceso no está vivo>>\n")
		return "".join(partes)

	partes.append("--- meminfo\n")
	partes.append(adb(caja, f"dumpsys meminfo {pid}"))
	partes.append("--- proc\n")
	partes.append(adb(
		caja,
		f"run-as {paquete} sh -c '"
		f"echo fds=$(ls /proc/{pid}/fd 2>/dev/null | wc -l); "
		f"echo maps=$(wc -l < /proc/{pid}/maps); "
		f"grep -E \"^(Threads|VmRSS|VmSize|VmSwap):\" /proc/{pid}/status'",
	))
	return "".join(partes)


# --------------------------------------------------------------------------
# Aplanado
# --------------------------------------------------------------------------

# Filas de la tabla por categorías: nombre con espacios y luego sólo números.
FILA_CATEGORIA = re.compile(r"^\s{2,}(\S(?:[^\d]*\S)?)\s{2,}((?:\d+\s+)*\d+)\s*$")
PAR_OBJETO = re.compile(r"([A-Za-z][A-Za-z_ ]*?):\s*(\d+)")
TOTALES = re.compile(r"TOTAL (PSS|RSS|SWAP PSS):\s*(\d+)")
CONTADOR_PROC = re.compile(r"^(fds|maps)=(\d+)")
ESTADO_PROC = re.compile(r"^(Threads|VmRSS|VmSize|VmSwap):\s*(\d+)")

# Las dos únicas categorías que traen las columnas de heap del asignador.
COLUMNAS_HEAP = {"Native Heap", "Dalvik Heap"}


def clave(texto: str) -> str:
	"""`Native Heap` -> `NativeHeap`, para que sirva de nombre de columna."""
	return "".join(p.capitalize() if p.islower() else p for p in texto.split())


def aplanar_bloque(bloque: str) -> dict:
	"""Una muestra de texto a un diccionario plano de números.

	Deliberadamente tolerante: una sección que falte deja sus columnas sin
	poner, y el aplanado sigue. Un volcado truncado por un corte de adb vale
	para las secciones que sí llegaron.
	"""
	fila: dict = {}
	seccion = None

	for linea in bloque.splitlines():
		if linea.startswith(SEPARADOR):
			m = re.search(r"(\d{4}-\d\d-\d\dT[\d:+\-]+)\s+pid=(\S+)", linea)
			if m:
				fila["reloj"] = m.group(1)
				fila["pid"] = m.group(2)
			continue

		desnuda = linea.strip()
		if desnuda.startswith("App Summary"):
			seccion = "res"
			continue
		if desnuda.startswith("Objects"):
			seccion = "obj"
			continue
		if desnuda.startswith("SQL"):
			seccion = "sql"
			continue
		if desnuda.startswith("DATABASES"):
			seccion = None  # tabla con texto; no aporta a la serie
			continue
		if linea.startswith("--- proc"):
			seccion = "proc"
			continue
		if linea.startswith("--- meminfo"):
			seccion = "cat"
			continue

		if seccion == "proc":
			m = CONTADOR_PROC.match(desnuda) or ESTADO_PROC.match(desnuda)
			if m:
				fila[f"proc.{m.group(1)}"] = int(m.group(2))
			continue

		# La línea de totales va aparte y **no** sigue al resto de las reglas:
		# cae dentro de `App Summary`, así que sin este `continue` el parseo
		# de pares la cazaría otra vez y saldría duplicada.
		if TOTALES.search(linea):
			for nombre, valor in TOTALES.findall(linea):
				fila[f"total.{clave(nombre)}"] = int(valor)
			continue

		if seccion == "cat":
			m = FILA_CATEGORIA.match(linea)
			if m:
				nombre, numeros = m.group(1), m.group(2).split()
				k = clave(nombre)
				if nombre == "TOTAL":
					continue  # ya viene por `TOTAL PSS`
				fila[f"cat.{k}.pss"] = int(numeros[0])
				# Pss, PrivateDirty, PrivateClean, SwapPss, Rss [, Size, Alloc, Free]
				if nombre in COLUMNAS_HEAP and len(numeros) >= 8:
					fila[f"cat.{k}.heapSize"] = int(numeros[5])
					fila[f"cat.{k}.heapAlloc"] = int(numeros[6])
					fila[f"cat.{k}.heapFree"] = int(numeros[7])
			continue

		if seccion in ("res", "obj", "sql"):
			for nombre, valor in PAR_OBJETO.findall(linea):
				nombre = nombre.strip()
				if nombre:
					fila[f"{seccion}.{clave(nombre)}"] = int(valor)

	return fila


def aplanar(ruta: Path) -> list:
	crudo = ruta.read_text(errors="replace")
	bloques = [b for b in crudo.split(SEPARADOR) if b.strip()]
	filas = [aplanar_bloque(SEPARADOR + b) for b in bloques]
	return [f for f in filas if "reloj" in f]


def escribir_csv(filas: list, salida) -> None:
	import csv
	# Unión de columnas, con `reloj` primero: una sección que aparezca a mitad
	# del remojo no puede desplazar las columnas de las filas anteriores.
	columnas = ["reloj", "pid"]
	for f in filas:
		for k in f:
			if k not in columnas:
				columnas.append(k)
	w = csv.DictWriter(salida, fieldnames=columnas, restval="")
	w.writeheader()
	w.writerows(filas)


def informar_pendientes(filas: list) -> None:
	"""Las columnas ordenadas por cuánto crecen, que es lo que se va a leer.

	Sin esto el CSV tiene ~60 columnas y encontrar la que sube es trabajo
	manual. La pendiente se saca de la primera y la última muestra: con un
	remojo largo y crecimiento recto basta, y con crecimiento a escalones se
	ve igual en el CSV.
	"""
	if len(filas) < 2:
		print("hacen falta al menos dos muestras para una pendiente", file=sys.stderr)
		return
	horas = 0.0
	try:
		t0 = datetime.fromisoformat(filas[0]["reloj"])
		t1 = datetime.fromisoformat(filas[-1]["reloj"])
		horas = (t1 - t0).total_seconds() / 3600
	except (ValueError, KeyError):
		pass
	if horas <= 0:
		print("las marcas de hora no dan un intervalo utilizable", file=sys.stderr)
		return

	print(f"{len(filas)} muestras en {horas:.2f} h\n")
	print(f"{'columna':34s} {'primera':>10s} {'última':>10s} {'por hora':>10s}")
	pendientes = []
	for col in filas[0]:
		if col in ("reloj", "pid"):
			continue
		valores = [f[col] for f in filas if isinstance(f.get(col), int)]
		if len(valores) < 2:
			continue
		pendientes.append(((valores[-1] - valores[0]) / horas, col, valores[0], valores[-1]))
	for ph, col, pri, ult in sorted(pendientes, reverse=True):
		if abs(ph) < 0.01:
			continue
		print(f"{col:34s} {pri:10d} {ult:10d} {ph:+10.2f}")


def main() -> int:
	p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	p.add_argument("--caja", help="serie de adb, p. ej. 192.168.20.132:5555")
	p.add_argument("--paquete", default="mx.kataliziador.palco.debug")
	p.add_argument("--destino", type=Path, help="directorio donde escribir el crudo")
	p.add_argument("--cada", type=int, default=600, help="segundos entre muestras (600)")
	p.add_argument("--una", action="store_true", help="una sola muestra, a la salida estándar")
	p.add_argument("--aplanar", type=Path, metavar="CRUDO", help="convertir un crudo en CSV")
	p.add_argument("--csv", type=Path, help="con --aplanar: escribir aquí en vez de informar")
	args = p.parse_args()

	if args.aplanar:
		filas = aplanar(args.aplanar)
		if not filas:
			print(f"{args.aplanar}: ninguna muestra reconocida", file=sys.stderr)
			return 1
		if args.csv:
			with args.csv.open("w", newline="") as f:
				escribir_csv(filas, f)
			print(f"{len(filas)} muestras -> {args.csv}")
		else:
			informar_pendientes(filas)
		return 0

	if not args.caja:
		p.error("hace falta --caja (o --aplanar)")

	if args.una:
		sys.stdout.write(una_muestra(args.caja, args.paquete))
		return 0

	if not args.destino:
		p.error("hace falta --destino para el bucle")
	args.destino.mkdir(parents=True, exist_ok=True)
	crudo = args.destino / "memoria-crudo.txt"

	while True:
		bloque = una_muestra(args.caja, args.paquete)
		with crudo.open("a") as f:
			f.write(bloque)
			f.flush()
		time.sleep(args.cada)


if __name__ == "__main__":
	sys.exit(main())
