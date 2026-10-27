#!/usr/bin/env bash
#
# Genera los PNG de marca que hacen falta FUERA del APK.
#
# Dentro del APK la marca va en vector y no hay nada que generar. Fuera hacen
# falta mapas de bits en tres sitios, y los tres los pide alguien que no
# compila:
#
#   palomera-logo.png         la ranura `logo` del registro y del servidor de
#                             canales (CONFIG_SCHEMA §6). El autorizador solo
#                             acepta PNG —un SVG puede llevar script—, de 28 a
#                             1000 px de alto y hasta 2000 de ancho.
#   palomera-icono-512.png    el icono de la ficha de Play (PLAY.md §6).
#   palomera-banner-1280.png  el banner de TV de la ficha (PLAY.md §6).
#
# **Nada de esto se dibuja aquí.** Todo sale de los vectores de
# `app/src/main/res/drawable/`, convertidos por `vector-a-svg.py`. Si alguien
# retoca el logotipo, se vuelve a correr esto y los tres se actualizan; una
# copia del dibujo en este directorio habría garantizado que un día dejaran de
# coincidir.
#
# Requiere `rsvg-convert` (paquete `librsvg2-bin`).
#
set -euo pipefail

aqui="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
raiz="$(cd "$aqui/../.." && pwd)"
vectores="$raiz/app/src/main/res/drawable"
salida="${1:-$aqui/png}"

command -v rsvg-convert >/dev/null || {
	echo "falta rsvg-convert: apt-get install librsvg2-bin" >&2
	exit 1
}

mkdir -p "$salida"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

convertir() { python3 "$aqui/vector-a-svg.py" "$@"; }

# El logo de marca: alto fijo y ancho libre, como lo pinta `LogoDeMarca`. 144 px
# de alto son 28 dp a densidad xxhdpi con margen de sobra para una tele 4K, y
# pesa unos pocos KB.
convertir "$vectores/app_logo.xml" > "$tmp/logo.svg"
rsvg-convert -h 144 -f png -o "$salida/palomera-logo.png" "$tmp/logo.svg"

# El icono y el banner llevan fondo: se apilan los dos vectores en un lienzo,
# que es lo mismo que hace Android con el icono adaptativo. La ficha de Play no
# aplica máscara, así que lo que se sube es el cuadrado completo.
convertir "$vectores/app_icon_background.xml" "$vectores/app_icon_foreground.xml" > "$tmp/icono.svg"
rsvg-convert -w 512 -h 512 -f png -o "$salida/palomera-icono-512.png" "$tmp/icono.svg"

# Y una segunda versión del icono, con el símbolo más grande, **para la ficha**.
#
# El icono adaptativo encoge su símbolo a la zona segura de 66 dp sobre 108
# porque el lanzador puede recortarlo en círculo. Play no recorta así: aplica
# una esquina redondeada suave, y un símbolo dibujado para sobrevivir al círculo
# se ve encogido y tímido en la tienda. Es un fallo conocido de las fichas, no
# una cuestión de gusto.
#
# El factor sale de la geometría, no del ojo: 89/66 lleva la zona segura
# circular al borde de la zona segura rectangular, que es lo que Play respeta.
# El factor sale de la geometría, no del ojo: la zona segura del adaptativo es
# un círculo de 66 dp sobre 108 y Play respeta un cuadrado de unos 89, así que
# 89/66 lleva uno al borde del otro. Se escala el dibujo entero —el fondo es un
# rectángulo que llena el lienzo y al crecer lo sigue llenando—.
convertir --escala 1.3485 \
	"$vectores/app_icon_background.xml" "$vectores/app_icon_foreground.xml" \
	> "$tmp/icono-ficha.svg"
rsvg-convert -w 512 -h 512 -f png -o "$salida/palomera-icono-512-ficha.png" "$tmp/icono-ficha.svg"

convertir "$vectores/app_banner_background.xml" "$vectores/app_banner_foreground.xml" > "$tmp/banner.svg"
rsvg-convert -w 1280 -h 720 -f png -o "$salida/palomera-banner-1280.png" "$tmp/banner.svg"

printf '\n%s\n' "en $salida:"
for f in "$salida"/*.png; do
	python3 - "$f" <<-'PY'
	import struct, sys, os
	d = open(sys.argv[1], 'rb').read()
	w, h = struct.unpack('>II', d[16:24])
	print(f"  {os.path.basename(sys.argv[1]):24} {w}x{h}  {len(d) // 1024 or 1} KB")
	PY
done
