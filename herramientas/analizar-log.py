#!/usr/bin/env python3
"""El informe estándar de una sesión larga, siempre con las mismas cifras.

## Por qué existe

Cada análisis de remojo se venía escribiendo desde cero: el 10-oct fueron
quince trozos de python improvisado para sacar unas cifras que deberían ser
siempre las mismas. El problema no es el tiempo, es que **las cifras no eran
comparables entre sesiones** —cada vez calculaba lo que se me ocurría— y que
los hallazgos que costaron más trabajo no quedaban dentro de ningún
instrumento.

Dos de ellos sí están aquí dentro, y son el motivo real de la herramienta:

- **La firma de parada del origen.** Antes de cada corte, el buffer cae 2 s por
  cada 2 s de reloj durante unos diez segundos, mientras en marcha normal
  oscila entre 11 y 15. Eso distingue «el origen dejó de publicar» de «fallamos
  nosotros», y es lo que convirtió 19 cortes en un problema del proveedor.
- **La tabla de saltos al borde** con la ganancia, que es lo que destapó que
  tres de cada nueve iban hacia atrás (pendientes 70, 71 y 73).

## Uso

    python3 herramientas/analizar-log.py <volcado.txt>
    python3 herramientas/analizar-log.py --caja      # trae el más nuevo por adb

Con `--caja` se trae el volcado más reciente de la caja de pruebas, incluidos
los **automáticos** que la app escribe sola al primer error: no hace falta
esperar a que nadie vuelque nada a mano.
"""

from __future__ import annotations

import re
import statistics as est
import subprocess
import sys
import tempfile
from pathlib import Path

CAJA = "192.168.20.132:5555"
DESCARGAS = "/sdcard/Download"

# De `LiveTuning`: el latido va cada 2 s, y de ahí sale el tiempo que
# representa cada línea.
LATIDO_MS = 2_000

# Objetivos de `CLAUDE.md` §10, para decir si una cifra cumple sin tener que
# abrir el documento.
OBJETIVOS = {
	"ratio": (0.3, 1.0),        # %, objetivo y aceptable
	"cortes_hora": (1.0, 3.0),
	"primer_fotograma_ms": (4_000, 6_000),
}

NEGRITA, GRIS, ROJO, VERDE, AMBAR, FIN = (
	"\033[1m", "\033[90m", "\033[31m", "\033[32m", "\033[33m", "\033[0m",
)


def veredicto(valor: float, objetivo: float, aceptable: float) -> str:
	if valor <= objetivo:
		return f"{VERDE}cumple{FIN}"
	if valor <= aceptable:
		return f"{AMBAR}aceptable{FIN}"
	return f"{ROJO}fuera{FIN}"


def traer_de_la_caja() -> Path:
	"""El volcado más reciente de la caja, sea manual o automático."""
	r = subprocess.run(
		["adb", "-s", CAJA, "shell", f"ls -t {DESCARGAS}/livetv-*.txt 2>/dev/null | head -1"],
		capture_output=True, text=True,
	)
	remoto = r.stdout.strip()
	if not remoto:
		sys.exit("no hay ningún volcado en la caja (¿está conectada?)")
	destino = Path(tempfile.gettempdir()) / Path(remoto).name
	subprocess.run(["adb", "-s", CAJA, "pull", remoto, str(destino)],
	               capture_output=True, text=True, check=True)
	print(f"{GRIS}traído {remoto}{FIN}")
	return destino


def campos(linea: str) -> dict[str, str]:
	return dict(re.findall(r"(\w+)=([^\s]+)", linea))


def main() -> int:
	if len(sys.argv) < 2:
		sys.exit(__doc__.strip().splitlines()[-1])
	ruta = traer_de_la_caja() if sys.argv[1] == "--caja" else Path(sys.argv[1])
	lineas = ruta.read_text(errors="replace").splitlines()

	cabecera = [l for l in lineas if l.startswith("#")]
	cuerpo = [l for l in lineas if not l.startswith("#") and l.strip()]

	print(f"\n{NEGRITA}{ruta.name}{FIN}")
	for l in cabecera:
		if any(k in l for k in ("entradas:", "tramo:", "channel:", "device:", "Codec:")):
			print(f"{GRIS}{l}{FIN}")

	# --- qué pasó, por tipo -------------------------------------------------
	tipos: dict[str, int] = {}
	for l in cuerpo:
		m = re.search(r"\b([a-z]+\.[a-zA-Z]\w*)", l)
		if m:
			tipos[m.group(1)] = tipos.get(m.group(1), 0) + 1

	def segundos(linea: str) -> float:
		"""Hora de la línea en segundos del día. Las líneas son HH:MM:SS.mmm."""
		m = re.match(r"(\d\d):(\d\d):(\d\d)\.(\d+)", linea)
		return (int(m[1]) * 3600 + int(m[2]) * 60 + int(m[3]) + int(m[4]) / 1000) if m else -1.0

	lineas_latido = [l for l in cuerpo if " latido " in l]
	latidos = [campos(l) for l in lineas_latido]
	if not latidos:
		sys.exit("el volcado no tiene latidos: no hay nada que medir")

	minutos = len(latidos) * LATIDO_MS / 60_000
	cortes = tipos.get("player.rebuffer", 0)
	parados = sum(1 for d in latidos if d.get("reproduciendo") == "false")
	ratio = 100 * parados / len(latidos)
	por_hora = cortes / (minutos / 60) if minutos else 0

	print(f"\n{NEGRITA}La sesión{FIN}")
	print(f"  {len(latidos)} latidos = {minutos:.0f} min de reproducción")
	print(f"  cortes            {cortes:>6}      {por_hora:>5.1f}/h   {veredicto(por_hora, *OBJETIVOS['cortes_hora'])}")
	print(f"  ratio de rebuffer {ratio:>9.2f} %        {veredicto(ratio, *OBJETIVOS['ratio'])}")

	primer = next((campos(l).get("msDesdePrepare") for l in cuerpo if "frames.first" in l), None)
	if primer:
		print(f"  primer fotograma  {primer:>6} ms      "
		      f"{veredicto(float(primer), *OBJETIVOS['primer_fotograma_ms'])}")

	# --- salud de la caja ---------------------------------------------------
	def serie(clave: str) -> list[float]:
		"""Los valores numéricos de una clave del latido.

		El latido `n=0` que sigue a un reinicio trae `cpu=null` y
		`ramLibreMb=null`: el muestreador todavía no ha producido lectura. Un
		`null` es «no medido», no un cero, así que se descarta —promediarlo
		como 0 hundiría la mediana de CPU y fingiría una fuga de RAM.
		"""
		salida: list[float] = []
		for d in latidos:
			v = d.get(clave, "").rstrip("%")
			try:
				salida.append(float(v))
			except ValueError:
				continue
		return salida

	cpu, ram = serie("cpu"), serie("ramLibreMb")
	buf, directo = serie("bufferS"), serie("directoS")
	print(f"\n{NEGRITA}La caja{FIN}")
	if cpu:
		print(f"  cpu        mediana {est.median(cpu):>5.0f} %   p95 {sorted(cpu)[int(.95 * len(cpu))]:>5.0f} %")
	if len(ram) >= 60:
		# Comparar el primer latido con el último daba «posible fuga» sobre una
		# serie plana: el 10-oct la RAM libre osciló entre 692 y 745 MB las
		# cinco horas y el último latido cayó en un valle. Se comparan las
		# medianas de los quince primeros minutos y de los quince últimos, que
		# es lo que distingue una tendencia de un valle.
		tramo = min(450, len(ram) // 3)
		ini, fin = est.median(ram[:tramo]), est.median(ram[-tramo:])
		deriva = fin - ini
		señal = "sin fuga" if deriva > -50 else f"{ROJO}posible fuga{FIN}"
		print(f"  ram libre  {ini:.0f} → {fin:.0f} MB   ({deriva:+.0f})   "
		      f"oscila {min(ram):.0f}–{max(ram):.0f}   {señal}")
	elif ram:
		print(f"  ram libre  {ram[0]:.0f} → {ram[-1]:.0f} MB   "
		      f"{GRIS}(sesión corta: no se juzga la deriva){FIN}")
	if buf:
		print(f"  buffer     mediana {est.median(buf):>5.1f} s   p5 {sorted(buf)[int(.05 * len(buf))]:>5.1f} s   mínimo {min(buf):.1f} s")
	if directo:
		print(f"  directo    mediana {est.median(directo):>5.1f} s   p95 {sorted(directo)[int(.95 * len(directo))]:>5.1f} s   máximo {max(directo):.1f} s")

	# --- la vuelta al borde -------------------------------------------------
	saltos = [(l[:12], campos(l)) for l in cuerpo
	          if "offset.vuelveAlBorde" in l or "offset.saltoNoAdelanta" in l
	          or "offset.margenCorto" in l]
	# `trasElSalto` se emparejaba por índice, y eso se rompió el 11-oct en
	# cuanto hubo un salto **declinado**: el declinado no deja medida posterior,
	# así que la columna «después» de la primera fila mostraba la medida del
	# salto siguiente. Un dato correcto en la fila equivocada es peor que un
	# hueco. Se empareja por cercanía en el tiempo, que es lo que de verdad los
	# liga: la medida se escribe 2 ms después del salto.
	tras = [(segundos(l), campos(l)) for l in cuerpo if "offset.trasElSalto" in l]
	# La columna `gananciaMs` es del 10-oct; en los volcados anteriores se
	# deriva, que es exactamente la resta que hice a mano para encontrar el
	# pendiente 73. Sin esto la herramienta no sirve para releer el pasado,
	# que es la mitad de para qué existe.
	for _, d in saltos:
		if "gananciaMs" not in d:
			pos, dest = d.get("posAntesMs") or d.get("posMs"), d.get("destinoMs")
			if pos and dest and pos.lstrip("-").isdigit() and dest.lstrip("-").isdigit():
				d["gananciaMs"] = str(int(dest) - int(pos))
				d["gananciaDerivada"] = "sí"

	if saltos:
		atras = sum(1 for _, d in saltos
		            if d.get("gananciaMs", "").lstrip("-").isdigit() and int(d["gananciaMs"]) < 0)
		derivadas = any("gananciaDerivada" in d for _, d in saltos)
		print(f"\n{NEGRITA}Vueltas al borde{FIN}  ({len(saltos)}; "
		      f"{atras} con el destino hacia atrás"
		      + (", ganancia derivada" if derivadas else "") + ")")
		print(f"{GRIS}  hora         offset  ventana   posAntes   destino  ganancia   después"
		      + ("   (* derivada)" if derivadas else "") + FIN)
		for i, (hora, d) in enumerate(saltos):
			def n(k: str) -> str:
				v = d.get(k)
				return f"{int(v):>8}" if v and v.lstrip("-").isdigit() else f"{'—':>8}"
			despues = next(
				(d2.get("offsetMs", "—") for t2, d2 in tras
				 if 0 <= t2 - segundos(f"{hora} ") <= 2.0),
				"—",
			)
			pos = "posAntesMs" if "posAntesMs" in d else "posMs"
			# `offset.margenCorto` (pendiente 75) mide el colchón que gana el
			# salto —hacia atrás— donde los otros dos miden la ganancia hacia
			# el directo. Es el mismo número con el signo al revés, así que se
			# normaliza para que la columna se pueda leer de un vistazo.
			if "colchonMs" in d and "gananciaMs" not in d:
				d["gananciaMs"] = str(-int(d["colchonMs"]))
			marca = "*" if "gananciaDerivada" in d else " "
			print(f"  {hora} {n('offsetMs')} {n('ventanaMs')} {n(pos)} {n('destinoMs')} {n('gananciaMs')}{marca} {despues:>8}")

	# --- la firma de parada del origen --------------------------------------
	#
	# Diez segundos de buffer cayendo 2 s por cada 2 s de reloj. En marcha
	# normal oscila; aquí baja monótono hasta vaciarse. Es lo único que separa
	# «el origen dejó de publicar» de una avería nuestra.
	# Y hay que descontar las caídas que provocamos nosotros: **después de un
	# salto el buffer se vacía y se vuelve a llenar**, y eso tiene la misma
	# forma. Contándolas, el volcado del 10-oct daba seis paradas donde hubo
	# tres. No se filtra por una ventana de tiempo arbitraria sino por la
	# causa: una caída dentro de los 30 s siguientes a un salto es del salto.
	TRAS_UN_SALTO_S = 30.0
	saltos_s = [segundos(l) for l in cuerpo
	            if "offset.vuelveAlBorde" in l or "vigilante.saltaAlBorde" in l
	            or "offset.margenCorto" in l]

	paradas: list[int] = []
	racha = 0
	for i in range(1, len(latidos)):
		b0, b1 = latidos[i - 1].get("bufferS"), latidos[i].get("bufferS")
		if b0 and b1 and float(b1) < float(b0) - 1.5:
			racha += 1
			if racha == 4:  # cuatro latidos = 8 s de caída sostenida
				t = segundos(lineas_latido[i])
				if not any(0 <= t - s_ <= TRAS_UN_SALTO_S for s_ in saltos_s):
					paradas.append(i)
		else:
			racha = 0

	if paradas:
		print(f"\n{NEGRITA}El origen dejó de entregar{FIN}  ({len(paradas)} veces)")
		for i in paradas:
			print(f"  {lineas_latido[i][:8]}  el buffer cae 2 s por cada 2 s de reloj y se vacía")
		print(f"{GRIS}  desde el cliente no hay nada que hacer; para confirmarlo,{FIN}")
		print(f"{GRIS}  herramientas/vigilar-playlist.py a la vez que la sesión{FIN}")

	# --- el bucle de inanición ----------------------------------------------
	#
	# Descubierto el 11-oct, y es el que más cortes cuesta de todo lo medido:
	# 11 cortes en 38 s y 13 en 37 s, dos ráfagas de la misma sesión. La forma
	# es inconfundible: la posición se queda a 2-4 s del borde de un origen que
	# publica más despacio que el reloj, así que cada segmento llega tarde y el
	# reproductor se queda sin nada cada tres segundos, indefinidamente. No es
	# una parada del origen —hay datos, llegan— y no es un corte suelto.
	#
	# Lo que lo distingue de una parada: aquí el buffer **no se vacía hacia
	# cero**, se queda oscilando entre 1,5 y 5 s. Por eso el detector de
	# paradas no lo ve y hacía falta éste.
	# El techo se mide sobre la **mediana** y no sobre el máximo: el último
	# latido de la racha es justo el momento en que el origen suelta de golpe
	# todo lo que no publicó —el buffer salta a 66 s— y con el máximo el
	# detector se quedaba mudo precisamente en las dos rachas que lo motivaron.
	HUECO_MAX_S, MINIMO_CORTES, BUFFER_TECHO_S = 5.0, 4, 6.0
	cortes_t = [segundos(l) for l in cuerpo if "player.rebuffer" in l]
	buffers_t = [(segundos(l), float(d["bufferS"]))
	             for l, d in zip(lineas_latido, latidos) if "bufferS" in d]

	bucles: list[tuple[float, float, int]] = []
	racha_t: list[float] = []
	for t in cortes_t + [float("inf")]:
		if racha_t and t - racha_t[-1] <= HUECO_MAX_S:
			racha_t.append(t)
			continue
		if len(racha_t) >= MINIMO_CORTES:
			dentro = [b for s_, b in buffers_t if racha_t[0] <= s_ <= racha_t[-1]]
			if dentro and est.median(dentro) <= BUFFER_TECHO_S:
				bucles.append((racha_t[0], racha_t[-1], len(racha_t)))
		racha_t = [t] if t != float("inf") else []

	if bucles:
		print(f"\n{NEGRITA}Bucle de inanición{FIN}  ({len(bucles)})")
		for ini, fin, n in bucles:
			def hhmmss(s_: float) -> str:
				return f"{int(s_ // 3600):02d}:{int(s_ % 3600 // 60):02d}:{int(s_ % 60):02d}"
			dentro = [b for s_, b in buffers_t if ini <= s_ <= fin]
			print(f"  {hhmmss(ini)}–{hhmmss(fin)}  {fin - ini:.0f} s  "
			      f"{n} cortes  un corte cada {(fin - ini) / max(n - 1, 1):.1f} s  "
			      f"buffer mediano {est.median(dentro):.1f} s")
			# Pendiente 76: si el contador de playlists no sube durante la
			# racha, el origen tenía la lista congelada y la vuelta al borde no
			# se evaluó porque su método no se llamó. Es la pregunta que el
			# 11-oct quedó sin contestar, y ahora la contesta el propio volcado.
			tl = [int(d["timelines"]) for l, d in zip(lineas_latido, latidos)
			      if "timelines" in d and ini <= segundos(l) <= fin]
			if tl:
				print(f"           playlists nuevos durante la racha: {max(tl) - min(tl)}"
				      + ("  → la lista estaba congelada" if max(tl) - min(tl) == 0 else ""))
		print(f"{GRIS}  la posición está pegada al borde de un origen lento;{FIN}")
		print(f"{GRIS}  la cura medida es alejarla, no acercarla{FIN}")

	# --- lo que no encaja en ninguna sección --------------------------------
	avisos = {t: n for t, n in tipos.items()
	          if t.startswith(("vigilante.", "player.error", "codec.", "abr.", "audio.noNorm",
	                           "offset.noSeEvalua", "offset.superaLaVentana",
	                           "offset.sinSitioDetras", "canal.noDisponible"))}
	if avisos:
		print(f"\n{NEGRITA}Avisos{FIN}")
		for t, n in sorted(avisos.items(), key=lambda x: -x[1]):
			print(f"  {n:>4}  {t}")

	return 0


if __name__ == "__main__":
	sys.exit(main())
