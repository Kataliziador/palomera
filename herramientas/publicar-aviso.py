#!/usr/bin/env python3
"""Genera la página pública del aviso de privacidad desde `AVISO_PRIVACIDAD.md`.

## Por qué se genera y no se copia

El aviso tiene que estar publicado en una dirección permanente —Google Play no
acepta el envío sin ella— pero el documento del repositorio **no se puede subir
tal cual**: lleva un bloque de notas para nosotros al principio y un apéndice
marcado «no publicar» al final, que es la correspondencia entre cada frase y la
línea de código que la sostiene.

Copiarlo a mano crearía dos versiones, y la que se queda atrás es siempre la
publicada — que es justo la que tiene valor legal. Así que la fuente es una,
`AVISO_PRIVACIDAD.md`, y la página se vuelve a generar.

## Uso

    python3 herramientas/publicar-aviso.py        # escribe docs/privacidad.md
    python3 herramientas/publicar-aviso.py --ver  # solo comprueba que está al día

El segundo modo es el que importa en una revisión: sale con 1 si la página
publicada no corresponde a la fuente.
"""

from __future__ import annotations

import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "AVISO_PRIVACIDAD.md"
PAGINA = RAIZ / "docs/privacidad.md"

CABECERA = """---
title: Aviso de privacidad — Palomera
---

<!--
  GENERADO. No editar a mano: se reescribe desde AVISO_PRIVACIDAD.md con
  `python3 herramientas/publicar-aviso.py`.
-->

"""


def generar() -> str:
	texto = FUENTE.read_text(encoding="utf-8")

	# El apéndice es para nosotros y lo dice en su propio título.
	marca = "\n## Apéndice para nosotros — no publicar"
	if marca in texto:
		texto = texto[: texto.index(marca)]

	# El bloque de notas de arriba son instrucciones internas: habla de la
	# dirección donde hay que publicar esto y de qué debería revisar el
	# abogado. Nada de eso le dice nada a un abonado.
	lineas = [l for l in texto.splitlines() if not l.startswith(">")]

	# Quitar el separador y las líneas en blanco que deja el bloque al irse.
	while lineas and (not lineas[0].strip() or lineas[0].strip() == "---"):
		lineas.pop(0)
	while lineas and not lineas[-1].strip():
		lineas.pop()

	# El documento separa sus secciones con reglas horizontales, y al quitar
	# el bloque de notas y el apéndice quedan dos huérfanas: una justo bajo el
	# título y otra al final, que anunciaba el apéndice que ya no está.
	while lineas and lineas[-1].strip() in ("", "---"):
		lineas.pop()
	cuerpo = "\n".join(lineas).rstrip() + "\n"
	cuerpo = cuerpo.replace("# Aviso de privacidad — Palomera\n\n\n---\n", "# Aviso de privacidad — Palomera\n")

	if "⟨" in cuerpo:
		sys.exit("el aviso todavía tiene huecos ⟨así⟩; no se publica a medias")

	return CABECERA + cuerpo


def main() -> int:
	nueva = generar()
	if "--ver" in sys.argv:
		actual = PAGINA.read_text(encoding="utf-8") if PAGINA.exists() else ""
		if actual != nueva:
			print("docs/privacidad.md no corresponde a AVISO_PRIVACIDAD.md")
			print("corre: python3 herramientas/publicar-aviso.py")
			return 1
		print("la página publicada está al día")
		return 0

	PAGINA.parent.mkdir(parents=True, exist_ok=True)
	PAGINA.write_text(nueva, encoding="utf-8")
	print(f"escrito {PAGINA.relative_to(RAIZ)} ({len(nueva.splitlines())} líneas)")
	return 0


if __name__ == "__main__":
	sys.exit(main())
