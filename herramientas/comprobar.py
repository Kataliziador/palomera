#!/usr/bin/env python3
"""Las comprobaciones obligatorias antes de un commit, ejecutables.

## Por qué existe

`CLAUDE.md` §4.7 define dos verificaciones que son la condición de que el VOD
no se rompa, y hasta hoy vivían **como markdown para copiar y pegar**: se
cumplían solo si alguien se acordaba.

La tercera no estaba en §4.7 y es la que más veces ha fallado: que un
interruptor nuevo esté **cableado de punta a punta**. Los pendientes 56, 57 y
66 son el mismo fallo tres veces en dos días —código escrito, probado por
unidad y sin conectar al final del cable—, y ninguna prueba lo veía porque las
tres listas que hay que tocar son independientes entre sí.

## Uso

    python3 herramientas/comprobar.py                   # todo
    python3 herramientas/comprobar.py --rapido          # sin pruebas de unidad
    python3 herramientas/comprobar.py --instalar-gancho # en cada clon nuevo

Sale con 0 si todo pasa. El gancho de pre-commit corre la versión rápida;
`.git/hooks/` no se versiona, de ahí el instalador.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DEVOPTS = RAIZ / "livetv/src/main/kotlin/diagnostics/DevOptions.kt"
MENU = RAIZ / "app/src/main/java/org/jellyfin/androidtv/ui/livetv/LiveTvDevMenuFragment.kt"

ROJO, VERDE, GRIS, FIN = "\033[31m", "\033[32m", "\033[90m", "\033[0m"

# Las seis categorías de §4.7, una a una. Si aparece algo que no está aquí, o
# es un error o hay que justificarlo en el commit **y** añadirlo a §4.7; eso
# último lo manda el propio documento.
PERMITIDO = [
	# archivos de compilación
	r"^\.gitignore$",
	r"^settings\.gradle\.kts$",
	r"^app/build\.gradle\.kts$",
	r"^gradle/libs\.versions\.toml$",
	# enganches de navegación
	r"^app/src/main/java/org/jellyfin/androidtv/ui/navigation/Destinations\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/MainToolbar\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentLiveTVRow\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment\.kt$",
	# la excepción registrada de deviceProfile (retirada de nextlib)
	r"^app/src/main/java/org/jellyfin/androidtv/util/profile/deviceProfile\.kt$",
	# marca y avisos legales
	r"^app/src/main/AndroidManifest\.xml$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/preference/category/about\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/ConnectHelpAlertFragment\.kt$",
	# el pie de versión llamaba «jellyfin-androidtv» a la app en la primera
	# pantalla de toda instalación; §8 no lo permite ahí (14-oct)
	r"^app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SelectServerFragment\.kt$",
	r"^app/src/main/res/values[^/]*/strings\.xml$",
	# la pantalla de acceso mandaba al abonado a instalarse Jellyfin. Las
	# cadenas de upstream vienen traducidas a cuarenta idiomas, así que
	# sobrescribirlas en `values/` habría dejado el texto viejo en treinta y
	# nueve: hay que apuntar los layouts a cadenas propias (27-oct)
	r"^app/src/main/res/layout/fragment_user_login(_quick_connect)?\.xml$",
	# el nombre con el que la app se presenta al Jellyfin del ISP: el
	# `ClientInfo` del SDK, la cabecera de autorización del informe de fallos y
	# el encabezado de los dos informes que se suben al servidor. Decía
	# «Jellyfin Android TV» en la lista de sesiones del site (27-oct)
	r"^app/src/main/java/org/jellyfin/androidtv/di/AppModule\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/telemetry/TelemetryService\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/util/profile/deviceProfileReport\.kt$",
	r"^app/src/main/res/drawable[^/]*/(app_logo|app_banner_\w+|app_icon_\w+|ic_jellyfin|qr_jellyfin_docs)\.xml$",
	r"^app/src/main/res/mipmap[^/]*/(app_icon|app_banner)\.(png|xml)$",
	# la puerta de la sesión del abonado: 403 (suspendido) y «no contesta» se
	# trataban como un 401 y mandaban a la pantalla del código (28-oct)
	r"^app/src/main/java/org/jellyfin/androidtv/auth/model/LoginState\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/auth/model/SessionFailure\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/auth/repository/SessionRepository\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/auth/repository/AuthenticationRepository\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/ServerFragment\.kt$",
	r"^app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/UserLogin(Credentials|QuickConnect)Fragment\.kt$",
	r"^app/src/test/kotlin/auth/",
	# utilería fuera de la app
	r"^herramientas/",
	r"^headend/",
	r"^\.claude/",
	# la conexión con el registro
	r"^app/src/main/res/xml/network_security_config\.xml$",
	# la excepción registrada del camino de VOD
	r"^playback/media3/exoplayer/build\.gradle\.kts$",
]

# `K_OVERLAY_MODE` está fuera de `CLAVES_DEL_MENU` a propósito: el overlay es
# instrumento de medida, no una degradación. Lo dice el comentario de la lista.
EXENTAS = {"K_OVERLAY_MODE"}


class Informe:
	def __init__(self) -> None:
		self.fallos = 0

	def bien(self, que: str) -> None:
		print(f"{VERDE}  ✓ {que}{FIN}")

	def mal(self, que: str, detalle: list[str] = ()) -> None:
		print(f"{ROJO}  ✗ {que}{FIN}")
		for d in detalle:
			print(f"{GRIS}    {d}{FIN}")
		self.fallos += 1


def git(*args: str) -> list[str]:
	r = subprocess.run(["git", *args], cwd=RAIZ, capture_output=True, text=True)
	return [l for l in r.stdout.splitlines() if l.strip()]


def fuera_del_territorio(inf: Informe) -> None:
	"""§4.7 a) — nada tocado fuera de las seis categorías."""
	tocados = git(
		"diff", "--name-only", "upstream-base..HEAD", "--",
		":!livetv/", ":!app/src/main/java/org/jellyfin/androidtv/ui/livetv/",
		":!*/flavors/*", ":!*.md",
	)
	intrusos = [f for f in tocados if not any(re.search(p, f) for p in PERMITIDO)]
	if intrusos:
		inf.mal(
			"a) hay archivos tocados que §4.7 no registra",
			[*intrusos, "o es un error, o se justifica en el commit y se añade a §4.7"],
		)
	else:
		inf.bien(f"a) fuera del módulo, solo las categorías de §4.7 ({len(tocados)} archivos)")


def camino_de_vod(inf: Informe) -> None:
	"""§4.7 b) — el invariante: películas y series como en el upstream."""
	tocados = git(
		"diff", "--name-only", "upstream-base..HEAD", "--",
		"app/src/main/java/org/jellyfin/androidtv/ui/playback/", "playback/",
	)
	sobran = [f for f in tocados if f != "playback/media3/exoplayer/build.gradle.kts"]
	if sobran:
		inf.mal(
			"b) el camino de VOD está tocado, y eso rompe el invariante de §4",
			[*sobran, "sin que Edgar valide VOD contra la APK de M0, esto no se entrega"],
		)
	else:
		inf.bien("b) el camino de VOD, intacto")


def _propiedades(fuente: str) -> dict[str, str]:
	"""Qué propiedad de `DevOptions` lee cada clave `K_*`.

	`var` y `val`: `registryUrlOverride` es de solo lectura y la primera versión
	de esto la dio por inexistente.
	"""
	salida: dict[str, str] = {}
	for m in re.finditer(
		r"\b(?:var|val)\s+(\w+)\s*:\s*[\w?<>, ]+?\s*(?:\n\s*)?get\(\)[^\n]*?\b(K_[A-Z_]+)\b",
		fuente,
	):
		salida.setdefault(m.group(2), m.group(1))
	return salida


def _envoltorios(fuente: str) -> dict[str, set[str]]:
	"""Qué propiedad de `DevOptions` se apoya en qué otra.

	Hace falta porque el motor **no** lee `minBufferOverrideMs`: lee
	`minBufferMs`, que es `minBufferOverrideMs ?: LiveTuning.MIN_BUFFER_MS`. Sin
	seguir ese salto, esta comprobación grita que los dos ajustes de buffer
	están muertos, y es mentira. Fue su primer falso positivo.
	"""
	de: dict[str, set[str]] = {}
	for m in re.finditer(r"\b(?:var|val)\s+(\w+)\s*:[^\n]*\n?\s*get\(\)\s*=([^\n]*)", fuente):
		de[m.group(1)] = set(re.findall(r"\b([a-z]\w+)\b", m.group(2)))
	return de


def _bloque(fuente: str, desde: str, hasta: str) -> str:
	i = fuente.find(desde)
	if i < 0:
		return ""
	j = fuente.find(hasta, i)
	return fuente[i : j if j > 0 else len(fuente)]


def _consumidas_fuera() -> str:
	"""Todo el código que puede leer una opción, menos el propio DevOptions."""
	partes = []
	for raiz in (RAIZ / "livetv/src/main", RAIZ / "app/src/main/java/org/jellyfin/androidtv/ui/livetv"):
		for f in raiz.rglob("*.kt"):
			if f.name != "DevOptions.kt":
				partes.append(f.read_text())
	return "\n".join(partes)


def interruptores_cableados(inf: Informe) -> None:
	"""El cable entero: clave → propiedad → CLAVES_DEL_MENU → describe() → quien la usa."""
	fuente = DEVOPTS.read_text()
	menu = MENU.read_text()
	afuera = _consumidas_fuera()

	declaradas = set(re.findall(r'const val (K_[A-Z_]+) = "', fuente))
	propiedad = _propiedades(fuente)
	envoltorios = _envoltorios(fuente)
	# Anclado en la **declaración**, no en el primer "CLAVES_DEL_MENU" del
	# archivo, que es su uso en `isDegraded` cincuenta líneas antes. Con el
	# ancla mala el bloque se comía media clase y esta comprobación no
	# detectaba nada: lo dijo la prueba de quitar una clave a propósito.
	lista = _bloque(fuente, "private val CLAVES_DEL_MENU = listOf(", "\n\t\t)")
	describe = _bloque(fuente, "fun describe(", "\n\t}")

	def la_usa_alguien(prop: str) -> bool:
		if re.search(rf"\.{prop}\b", afuera):
			return True
		# Un salto de indirección: alguien de fuera lee un envoltorio que la lee.
		return any(
			prop in apoyos and re.search(rf"\.{envoltura}\b", afuera)
			for envoltura, apoyos in envoltorios.items()
		)

	fallos: list[str] = []
	avisos: list[str] = []
	for clave in sorted(declaradas - EXENTAS):
		prop = propiedad.get(clave)
		falta = []
		if prop is None:
			falta.append("ninguna propiedad la lee")
		else:
			if clave not in describe and not re.search(rf"\b{prop}\b", describe):
				falta.append("describe()")
			if not la_usa_alguien(prop):
				falta.append("NADIE LA USA: está escrita y no llega al motor")
		if clave not in lista:
			falta.append("CLAVES_DEL_MENU")
		if falta:
			fallos.append(f"{clave} → falta en: {', '.join(falta)}")
		elif f"dev.{prop}" not in menu:
			# No es un fallo: hay opciones que solo se ponen empujando el XML por
			# adb (`INTEGRACION_CANALES.md`), y es su uso legítimo.
			avisos.append(f"{clave} ({prop}) no tiene fila en el menú; solo por adb")

	if fallos:
		inf.mal(
			"c) hay interruptores a medio cablear — el fallo de los pendientes 56, 57 y 66",
			fallos,
		)
	else:
		inf.bien(f"c) los {len(declaradas - EXENTAS)} interruptores llegan al final del cable")
	for a in avisos:
		print(f"{GRIS}    · {a}{FIN}")


def confianza_en_cada_conexion(inf: Informe) -> None:
	"""Toda conexión propia que pueda ir por TLS pasa por `RegistryTrust`.

	El fallo que esto atrapa no es de lógica: es de **olvido**. La confianza
	ampliada estaba bien construida y probada desde el 05-oct, y al
	`config.json` no se le aplicaba —tres semanas, anotado sin tachar en
	`LIVETV_PLAN.md`—. No da error visible: la caja vieja entra a Jellyfin,
	consigue autorización y se queda con la sección de canales vacía.

	Grano de archivo, no de llamada: si un archivo abre conexiones, tiene que
	nombrar `RegistryTrust.aplicarA`. No distingue dos conexiones en el mismo
	archivo donde solo una lo use; para eso están las pruebas de unidad.
	"""
	culpables = []
	for ruta in sorted((RAIZ / "livetv/src/main").rglob("*.kt")):
		fuente = ruta.read_text(encoding="utf-8")
		if "openConnection()" not in fuente:
			continue
		if "RegistryTrust.aplicarA" not in fuente:
			culpables.append(str(ruta.relative_to(RAIZ)))
	if culpables:
		inf.mal(
			"e) hay conexiones propias sin la confianza de RegistryTrust",
			[*culpables, "una caja con Android 5.0-7.1.0 se queda sin lista y sin error visible"],
		)
	else:
		inf.bien("e) las conexiones propias pasan por RegistryTrust")


def pruebas(inf: Informe) -> None:
	r = subprocess.run(
		["./gradlew", ":livetv:test", "--console=plain", "-q"],
		cwd=RAIZ, capture_output=True, text=True,
	)
	if r.returncode == 0:
		inf.bien("d) :livetv:test en verde")
	else:
		inf.mal("d) :livetv:test falla", (r.stdout + r.stderr).splitlines()[-20:])


GANCHO = """#!/bin/sh
# Las comprobaciones obligatorias de CLAUDE.md §4.7, más el cable de los
# interruptores. Para saltárselo a propósito: git commit --no-verify
exec python3 -I "$(git rev-parse --show-toplevel)/herramientas/comprobar.py" --rapido
"""


def instalar_gancho() -> int:
	"""`.git/hooks/` no se versiona, así que el gancho se reinstala por clon."""
	destino = RAIZ / ".git/hooks/pre-commit"
	if destino.exists() and GANCHO.strip() not in destino.read_text():
		print(f"{ROJO}ya hay un pre-commit distinto en {destino}; no lo piso{FIN}")
		return 1
	destino.write_text(GANCHO)
	destino.chmod(0o755)
	print(f"{VERDE}gancho instalado en {destino}{FIN}")
	return 0


def main() -> int:
	if "--instalar-gancho" in sys.argv:
		return instalar_gancho()

	inf = Informe()
	fuera_del_territorio(inf)
	camino_de_vod(inf)
	interruptores_cableados(inf)
	confianza_en_cada_conexion(inf)
	if "--rapido" in sys.argv:
		print(f"{GRIS}    d) pruebas de unidad omitidas (--rapido){FIN}")
	else:
		pruebas(inf)

	print()
	if inf.fallos:
		print(f"{ROJO}{inf.fallos} comprobación(es) fallando{FIN}")
		return 1
	print(f"{VERDE}todo en orden{FIN}")
	return 0


if __name__ == "__main__":
	sys.exit(main())
