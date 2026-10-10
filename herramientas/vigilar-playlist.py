#!/usr/bin/env python3
"""Vigila si el origen deja de publicar segmentos, desde fuera de la caja.

## Por qué existe

Cuando un canal se corta, el log de la app dice que se quedó sin datos, pero no
dice **de quién es la culpa**: puede ser que Flussonic dejara de publicar o que
la caja perdiera la red. Son dos averías distintas con dos dueños distintos y
desde el log de la caja no se distinguen.

Esto mira el mismo playlist desde otra máquina y al mismo tiempo. Si las dos
cosas se paran a la vez, el origen se paró; si solo se para la caja, es la red
de la caja.

Medido el 10-oct en `mileniosd`: tres paradas de la fuente en 3,4 h —11:57,
12:12 y 12:29— que en el log de la app aparecen como el buffer cayendo 2 s por
cada 2 s de reloj y, unos 50 s después, un `PlaylistStuckException`.

## Uso

    python3 herramientas/vigilar-playlist.py <url> [segundos] [--umbral 2.0]

La `<url>` puede ser el master (`.../index.m3u8`) o un media playlist: si es un
master, se sigue la primera variante. `segundos` por defecto son 3600; con 0 no
termina hasta que se lo corten.

Imprime una línea **en el momento** en que detecta una parada, con la hora de
reloj, para poder cruzarla con el volcado de la app. No escribe archivos y no
toca nada: solo pide el playlist una vez por segundo.
"""

import re
import sys
import time
import urllib.parse
import urllib.request

CADA_S = 1.0
UMBRAL_S = 2.0  # cuánto puede tardar un segmento más de lo que declara


def pedir(url: str) -> str:
	with urllib.request.urlopen(url, timeout=10) as r:
		return r.read().decode("utf-8", "replace")


def resolver(url: str) -> str:
	"""Si es un master, devuelve la URL de la primera variante."""
	txt = pedir(url)
	if "#EXT-X-STREAM-INF" not in txt:
		return url
	lineas = txt.splitlines()
	for i, l in enumerate(lineas):
		if l.startswith("#EXT-X-STREAM-INF") and i + 1 < len(lineas):
			variante = lineas[i + 1].strip()
			return urllib.parse.urljoin(url, variante)
	raise SystemExit(f"el master no declara ninguna variante: {url}")


def main() -> int:
	if len(sys.argv) < 2:
		raise SystemExit(__doc__.strip().splitlines()[-1])
	segundos = int(sys.argv[2]) if len(sys.argv) > 2 else 3600
	umbral = UMBRAL_S
	if "--umbral" in sys.argv:
		umbral = float(sys.argv[sys.argv.index("--umbral") + 1])

	url = resolver(sys.argv[1])
	print(f"# vigilando {url}")
	print(f"# aviso cuando un segmento tarde más de {umbral:.1f} s sobre lo que declara")

	visto: set[str] = set()
	anterior: tuple[float, float] | None = None  # (instante, duración declarada)
	retrasos: list[tuple[float, float]] = []     # (hueco, duración del anterior)
	paradas = 0
	errores = 0
	fin = time.time() + segundos if segundos else float("inf")

	while time.time() < fin:
		ahora = time.time()
		try:
			txt = pedir(url)
		except Exception as e:  # red, 404, timeout: es dato, no excepción
			errores += 1
			print(f"{time.strftime('%H:%M:%S')}  ERROR  {e}", flush=True)
			time.sleep(CADA_S)
			continue

		# La primera vuelta ve la ventana entera de golpe. Esos doce segmentos
		# no «llegaron» ahora: llegaron antes de que esto arrancara, y contarlos
		# daría doce huecos de cero segundos que estropean la mediana.
		primera = not visto

		for dur, nombre in re.findall(r"#EXTINF:([0-9.]+),\s*\n(\S+)", txt):
			if nombre in visto:
				continue
			visto.add(nombre)
			if anterior is not None and not primera:
				hueco = ahora - anterior[0]
				retrasos.append((hueco, anterior[1]))
				tarde = hueco - anterior[1]
				if tarde > umbral:
					paradas += 1
					print(
						f"{time.strftime('%H:%M:%S')}  PARADA  el origen tardó "
						f"{hueco:.1f} s en publicar un segmento de {anterior[1]:.1f} s "
						f"(+{tarde:.1f} s)",
						flush=True,
					)
			anterior = (ahora, float(dur))

		time.sleep(CADA_S)

	print(f"\n# segmentos nuevos vistos: {len(retrasos) + 1}")
	print(f"# paradas por encima de {umbral:.1f} s: {paradas}")
	print(f"# errores de red al pedir el playlist: {errores}")
	if retrasos:
		tardes = sorted(h - d for h, d in retrasos)
		print(f"# retraso mediano {tardes[len(tardes) // 2]:+.2f} s, "
		      f"p95 {tardes[int(0.95 * len(tardes))]:+.2f} s, "
		      f"peor {tardes[-1]:+.2f} s")
	return 0


if __name__ == "__main__":
	sys.exit(main())
