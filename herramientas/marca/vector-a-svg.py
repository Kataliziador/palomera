#!/usr/bin/env python3
"""Convierte un VectorDrawable de Android a SVG.

**Existe para no duplicar el logotipo.** Los PNG que hacen falta fuera del APK
—la ranura de marca del registro, el icono y el banner de la ficha de Play— son
el mismo dibujo que `res/drawable/app_logo.xml` y compañía. Mantener a mano una
copia en SVG significa que el día que alguien retoque el vector, los PNG se
quedan atrás y nadie se entera hasta verlos en la tienda.

Cubre **el subconjunto que usan nuestros vectores y ninguno más**: `<path>` con
`pathData`, `fillColor`, `strokeColor`, `strokeWidth`, `strokeLineCap` y
`strokeLineJoin`, y `<group>` con `translateX`/`translateY`, `scaleX`/`scaleY`
y `pivotX`/`pivotY`. Si algún día se usa `rotation`, `pathData` animado,
degradados o `clip-path`, esto
falla **en voz alta** en vez de dibujar algo distinto en silencio: un logotipo
que sale mal sin avisar es peor que uno que no sale.

`android:pathData` es sintaxis de path de SVG, así que se copia tal cual.
"""

import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"

# Lo que sabemos traducir. Cualquier otro atributo aborta.
PATH_OK = {
    "pathData", "fillColor", "strokeColor", "strokeWidth",
    "strokeLineCap", "strokeLineJoin", "fillAlpha", "strokeAlpha", "name",
}
GROUP_OK = {"translateX", "translateY", "scaleX", "scaleY", "pivotX", "pivotY", "name"}
VECTOR_OK = {
    "width", "height", "viewportWidth", "viewportHeight", "tint", "name",
    "alpha", "autoMirrored",
}


def attr(elemento, nombre):
    return elemento.get(A + nombre)


def revisar(elemento, permitidos, donde):
    sobran = {k.replace(A, "") for k in elemento.attrib if k.startswith(A)} - permitidos
    if sobran:
        raise SystemExit(
            f"{donde}: atributos que este conversor no sabe traducir: {sorted(sobran)}.\n"
            "Añádelos aquí antes de regenerar, o el PNG saldrá distinto del vector."
        )


def pinta(elemento, tinte):
    """Los atributos de pintura de un `<path>`, en SVG.

    El `tint` del vector sustituye al color, que es lo que hace Android: por eso
    los paths pueden declarar `#FFFFFF` y verse del color del tinte.
    """
    trozos = []
    relleno = attr(elemento, "fillColor")
    trazo = attr(elemento, "strokeColor")
    trozos.append(f'fill="{tinte or relleno}"' if relleno else 'fill="none"')
    if trazo:
        trozos.append(f'stroke="{tinte or trazo}"')
        for android, svg in (
            ("strokeWidth", "stroke-width"),
            ("strokeLineCap", "stroke-linecap"),
            ("strokeLineJoin", "stroke-linejoin"),
            ("strokeAlpha", "stroke-opacity"),
        ):
            valor = attr(elemento, android)
            if valor:
                trozos.append(f'{svg}="{valor}"')
    alfa = attr(elemento, "fillAlpha")
    if alfa:
        trozos.append(f'fill-opacity="{alfa}"')
    return " ".join(trozos)


def transforma(grupo):
    """El `transform` SVG equivalente a un `<group>` de VectorDrawable.

    Android compone el grupo como
    `translate(t) · translate(pivot) · rotate · scale · translate(-pivot)`,
    y **ese orden importa**: con el pivote fuera de (0,0), escalar antes o
    después de trasladar deja el dibujo en sitios distintos. Se emite igual,
    omitiendo las partes neutras para que el SVG se pueda leer.
    """
    g = lambda n, d: float(attr(grupo, n) or d)
    tx, ty = g("translateX", 0), g("translateY", 0)
    sx, sy = g("scaleX", 1), g("scaleY", 1)
    px, py = g("pivotX", 0), g("pivotY", 0)

    partes = []
    if (tx, ty) != (0, 0):
        partes.append(f"translate({tx:g},{ty:g})")
    if (sx, sy) != (1, 1):
        if (px, py) != (0, 0):
            partes.append(f"translate({px:g},{py:g})")
        partes.append(f"scale({sx:g},{sy:g})")
        if (px, py) != (0, 0):
            partes.append(f"translate({-px:g},{-py:g})")
    return " ".join(partes) or "translate(0,0)"


def nodos(elemento, tinte, sangria="  "):
    salida = []
    for hijo in elemento:
        etiqueta = hijo.tag.split("}")[-1]
        if etiqueta == "path":
            revisar(hijo, PATH_OK, "<path>")
            datos = attr(hijo, "pathData")
            if not datos:
                raise SystemExit("<path> sin pathData")
            salida.append(f'{sangria}<path d="{datos}" {pinta(hijo, tinte)}/>')
        elif etiqueta == "group":
            revisar(hijo, GROUP_OK, "<group>")
            salida.append(f'{sangria}<g transform="{transforma(hijo)}">')
            salida.extend(nodos(hijo, tinte, sangria + "  "))
            salida.append(f"{sangria}</g>")
        elif etiqueta == "clip-path":
            raise SystemExit("<clip-path>: no se traduce; añádelo al conversor")
        else:
            raise SystemExit(f"<{etiqueta}>: no se traduce; añádelo al conversor")
    return salida


def convertir(ruta):
    raiz = ET.parse(ruta).getroot()
    revisar(raiz, VECTOR_OK, "<vector>")
    ancho = attr(raiz, "viewportWidth")
    alto = attr(raiz, "viewportHeight")
    if not ancho or not alto:
        raise SystemExit(f"{ruta}: sin viewportWidth/viewportHeight")
    tinte = attr(raiz, "tint")
    cuerpo = "\n".join(nodos(raiz, tinte))
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {ancho} {alto}">\n'
        f"{cuerpo}\n</svg>\n"
    )


def envolver(cuerpos, lado, escala):
    """Escala todo el dibujo alrededor del centro del lienzo.

    Hace falta para el icono de la ficha de Play. El icono adaptativo encoge su
    símbolo a la zona segura **circular** de 66 dp sobre 108 porque el lanzador
    puede recortarlo en círculo; Play no recorta así, y un símbolo dibujado para
    sobrevivir al círculo se ve encogido en la tienda. Escalarlo es recuperar el
    tamaño que el formato de la tienda permite, no un retoque de gusto.

    Se escala **todo**, fondo incluido, y por eso funciona: el fondo es un
    rectángulo que llena el lienzo, así que al crecer lo sigue llenando.
    """
    desplazamiento = lado / 2 - (lado / 2) * escala
    return (
        [f'  <g transform="translate({desplazamiento:.4f},{desplazamiento:.4f}) '
         f'scale({escala:.4f})">']
        + ["  " + c for c in cuerpos]
        + ["  </g>"]
    )


if __name__ == "__main__":
    escala = None
    argumentos = sys.argv[1:]
    if "--escala" in argumentos:
        i = argumentos.index("--escala")
        escala = float(argumentos[i + 1])
        del argumentos[i:i + 2]
    sys.argv = [sys.argv[0]] + argumentos
    if len(sys.argv) < 2:
        raise SystemExit(
            "uso: vector-a-svg.py [--escala N] <vector.xml> [más.xml …] > salida.svg"
        )
    # Varios vectores se apilan en el mismo lienzo, en orden: así se compone el
    # icono adaptativo (fondo y después símbolo) sin un paso aparte.
    partes = [convertir(r) for r in sys.argv[1:]]
    cabeceras = {p.splitlines()[0] for p in partes}
    if len(cabeceras) > 1:
        # Apilarlos con lienzos distintos dibujaría el símbolo a otra escala que
        # el fondo, y eso se ve raro pero no se ve *mal*: justo el fallo que hay
        # que impedir, no detectar mirando.
        raise SystemExit(
            "los vectores que se apilan tienen viewports distintos:\n  "
            + "\n  ".join(sorted(cabeceras))
        )
    cabecera = partes[0].splitlines()[0]
    cuerpos = [l for p in partes for l in p.splitlines()[1:-1]]
    if escala is not None:
        # El lienzo es cuadrado en todo lo que escalamos hoy; si algún día no lo
        # fuera, el centro ya no sería el mismo en los dos ejes.
        lado = float(cabecera.split('viewBox="0 0 ')[1].split('"')[0].split()[0])
        cuerpos = envolver(cuerpos, lado, escala)
    print(cabecera)
    print("\n".join(cuerpos))
    print("</svg>")
