#!/usr/bin/env python3
"""Fabrica el golden sample de lluvia: un MPEG-TS roto como lo rompe la banda Ku.

## Por qué existe

`LIVETV_PLAN.md` llama al golden sample «la mejor prueba que existe» y lleva
abierto desde M7 porque nunca hubo uno. No lo había porque esperar a que
lloviera de verdad **sobre la antena correcta y con alguien grabando** no es un
plan; y porque el otro instrumento que teníamos, cortar el cable, produce el
fallo equivocado: un corte limpio, que la banda Ku casi nunca da.

Lo que la lluvia hace de verdad, cuando el FEC ya no da más de sí:

- **pierde paquetes TS enteros**, no bytes sueltos —el error llega en bloques
  de 188 bytes porque esa es la unidad que el demodulador entrega o descarta—;
- **y los pierde a ráfagas.** Un desvanecimiento dura decenas o cientos de
  milisegundos, así que se van docenas de paquetes seguidos. Esto importa más
  que el porcentaje: un 1 % repartido uniformemente es ruido que el decoder
  absorbe, y un 1 % en ráfagas de 200 paquetes son cuadros enteros que faltan.
  Un golden sample con pérdida uniforme probaría algo más fácil que la realidad.
- **y además corrompe sin perder**: cuando el error no se detecta, el payload
  llega con bits cambiados y el decoder pinta macrobloques de basura en vez de
  quedarse sin datos. Son dos fallos distintos y el cliente reacciona distinto.

## Lo que NO se toca, y por qué se puede cambiar

Por defecto se respetan **PAT y los PMT**, que se localizan leyendo el PAT. No
porque la lluvia los perdone —se los lleva igual— sino porque perderlos al
principio deja el archivo indescifrable desde el primer byte y entonces el
sample no mide la resistencia del decoder, mide que no hay stream. Con
`--sin-piedad` entran al sorteo, y ese es el caso duro: el cliente tiene que
volver a encontrar las tablas él solo.

## Uso

    # 1. grabar limpio del headend (de solo lectura, solo se lee)
    ffmpeg -y -i http://192.168.11.250:8181/test-sat77w-estrellassd-mp2v/index.m3u8 \\
           -c copy -t 60 -f mpegts limpio.ts

    # 2. romperlo
    python3 herramientas/corromper-ts.py limpio.ts lluvia.ts --perdida 1.5 --rafaga 120 --bits 0.3

    # 3. servirlo en bucle como si fuera directo
    ffmpeg -re -stream_loop -1 -i lluvia.ts -c copy -f hls \\
           -hls_time 2 -hls_list_size 4 -hls_flags delete_segments+omit_endlist \\
           /srv/lluvia/index.m3u8
    python3 -m http.server 8098 --directory /srv/lluvia

El paso 3 deja un directo permanente con lluvia que no depende de que llueva ni
del headend compartido, y es reproducible byte a byte: el mismo `--semilla` da
el mismo destrozo, así que dos versiones de la app se pueden comparar contra
exactamente la misma avería.

## Dónde están las grabaciones, y por qué no aquí

En `/home/proyectos/muestras-lluvia/`, **fuera del repositorio**. Son emisión
ajena y este repositorio va a ser público para cumplir la oferta de código de
la GPL-2.0 (`PLAY.md` §5). Lo que se versiona es esta herramienta y la semilla,
que bastan para rehacer el archivo byte a byte.
"""

from __future__ import annotations

import argparse
import random
import sys
from collections import Counter
from pathlib import Path

PAQUETE = 188
SYNC = 0x47


def pids_de_psi(datos: bytes) -> set[int]:
	"""PAT y los PMT que el PAT declara.

	Se lee del propio archivo en vez de pedirlos por parámetro porque un número
	escrito a mano se queda atrás en cuanto cambia el canal, y entonces el
	sample «respeta las tablas» sin respetarlas.
	"""
	protegidos = {0x0000, 0x1FFF}  # PAT y relleno
	for off in range(0, len(datos) - PAQUETE, PAQUETE):
		cab = datos[off:off + 4]
		if cab[0] != SYNC:
			continue
		pid = ((cab[1] & 0x1F) << 8) | cab[2]
		if pid != 0x0000:
			continue
		inicio_carga = (cab[1] & 0x40) != 0
		tiene_adapt = (cab[3] & 0x20) != 0
		p = off + 4
		if tiene_adapt:
			p += 1 + datos[p]
		if inicio_carga:
			p += 1 + datos[p]        # puntero de campo
		if p + 8 > off + PAQUETE:
			continue
		longitud = ((datos[p + 1] & 0x0F) << 8) | datos[p + 2]
		fin = min(p + 3 + longitud - 4, off + PAQUETE)   # sin el CRC
		q = p + 8
		while q + 4 <= fin:
			programa = (datos[q] << 8) | datos[q + 1]
			pid_mapa = ((datos[q + 2] & 0x1F) << 8) | datos[q + 3]
			if programa != 0:
				protegidos.add(pid_mapa)
			q += 4
		break
	return protegidos


def main() -> int:
	p = argparse.ArgumentParser(description=__doc__,
	                            formatter_class=argparse.RawDescriptionHelpFormatter)
	p.add_argument("entrada", type=Path)
	p.add_argument("salida", type=Path)
	p.add_argument("--perdida", type=float, default=1.0,
	               help="%% de paquetes que desaparecen (en ráfagas)")
	p.add_argument("--rafaga", type=int, default=100,
	               help="paquetes que se van juntos en cada desvanecimiento")
	p.add_argument("--bits", type=float, default=0.2,
	               help="%% de paquetes que llegan con el payload alterado")
	p.add_argument("--bits-por-paquete", type=int, default=6)
	p.add_argument("--tei", action="store_true",
	               help="además, marcar el bit de error de transporte en los alterados")
	p.add_argument("--sin-piedad", action="store_true",
	               help="PAT y PMT también entran al sorteo")
	p.add_argument("--semilla", type=int, default=20261014)
	a = p.parse_args()

	datos = a.entrada.read_bytes()
	if len(datos) < PAQUETE or datos[0] != SYNC:
		print(f"{a.entrada}: no empieza con 0x47; ¿es MPEG-TS?", file=sys.stderr)
		return 1
	total = len(datos) // PAQUETE
	protegidos: set[int] = set() if a.sin_piedad else pids_de_psi(datos)

	rnd = random.Random(a.semilla)
	# Las ráfagas se reparten primero: cuántos desvanecimientos hacen falta para
	# que el porcentaje salga, redondeando a ráfaga entera.
	objetivo = int(total * a.perdida / 100)
	cuantas = max(1, round(objetivo / a.rafaga)) if objetivo else 0
	inicios = sorted(rnd.randrange(0, max(1, total - a.rafaga)) for _ in range(cuantas))
	a_perder: set[int] = set()
	for i in inicios:
		a_perder.update(range(i, min(total, i + a.rafaga)))

	fuera = bytearray()
	perdidos = alterados = respetados = 0
	por_pid: Counter[int] = Counter()

	for n in range(total):
		off = n * PAQUETE
		paq = bytearray(datos[off:off + PAQUETE])
		if paq[0] != SYNC:
			fuera += paq            # desincronizado de origen: se copia tal cual
			continue
		pid = ((paq[1] & 0x1F) << 8) | paq[2]

		if pid in protegidos:
			respetados += 1
			fuera += paq
			continue

		if n in a_perder:
			perdidos += 1
			por_pid[pid] += 1
			continue                 # el paquete sencillamente no está

		if a.bits > 0 and rnd.random() * 100 < a.bits:
			tiene_adapt = (paq[3] & 0x20) != 0
			inicio = 4 + (1 + paq[4] if tiene_adapt else 0)
			if inicio < PAQUETE - 1:
				for _ in range(a.bits_por_paquete):
					i = rnd.randrange(inicio, PAQUETE)
					paq[i] ^= 1 << rnd.randrange(8)
				if a.tei:
					paq[1] |= 0x80
				alterados += 1
		fuera += paq

	a.salida.write_bytes(bytes(fuera))

	print(f"entrada  {a.entrada}  {total} paquetes  {len(datos)/1e6:.1f} MB")
	print(f"salida   {a.salida}  {len(fuera)//PAQUETE} paquetes  {len(fuera)/1e6:.1f} MB")
	print(f"perdidos {perdidos} ({100*perdidos/total:.2f} %) en {cuantas} "
	      f"ráfaga(s) de {a.rafaga}")
	print(f"alterados {alterados} ({100*alterados/total:.2f} %)"
	      f"{' con TEI' if a.tei else ''}, {a.bits_por_paquete} bits cada uno")
	print(f"respetados {respetados} de PSI "
	      f"(PIDs {' '.join(hex(x) for x in sorted(protegidos)) or 'ninguno'})")
	if por_pid:
		print("pérdidas por PID: " +
		      ", ".join(f"{hex(k)}={v}" for k, v in por_pid.most_common(6)))
	print(f"semilla {a.semilla} — repetible")
	return 0


if __name__ == "__main__":
	sys.exit(main())
