#!/usr/bin/env python3
"""Rehace las capturas de la ficha de Play, con una orden.

## Por qué existe

`FICHA.md` §5 decía de las cuatro capturas: «son el único archivo de toda la
ficha que no se puede rehacer con una orden». Eso no era una nota al pie — es
la causa de que se quedaran atrás. Se sacaron el 02-nov y el 04-nov tres de
ellas ya mentían:

| captura | qué la caducó |
|---|---|
| 1 · la sección | `39109f28e`, la guía pasó a fichas |
| 2 · la barra de canal | el mismo commit la reordenó, y `7f8450d2d` le quitó un número duplicado |
| 4 · el inicio | `f18ad1141` borró la fila «TV en vivo» que la captura enseña |

Una ficha con capturas viejas no es un detalle estético: el revisor de Google
compara la foto con lo que ve al abrir la app, y `CLAUDE.md` §7 congeló la
interfaz justo por eso. Si rehacerlas cuesta media tarde, no se rehacen.

## Qué hace, y qué no

Levanta el emulador `tv34` si hace falta, instala el **release** —no la
depuración, que enseña el botón «Diagnóstico» y la píldora ámbar—, lo pone en
es-MX y hora de México, entra a la **demo** y saca las capturas 1, 2 y 4.

**La 3 no la saca, y no es un olvido:** es la pantalla de activación con el
código, y aparece sólo si el servidor tiene QuickConnect encendido. La demo lo
tiene **apagado a propósito**, para que el probador vaya directo a usuario y
contraseña. La captura 3 del 02-nov sigue siendo válida —nada la ha tocado— y
se conserva.

## Uso

    python3 -I herramientas/capturar-ficha.py

**No hay que escribir ninguna clave.** Las credenciales de la demo ya existen
en `/home/proyectos/palco-demo/.env` —`PALCO_DEMO_USUARIO` y
`PALCO_DEMO_CLAVE`, con el nombre que tenían cuando la app se llamaba Palco— y
esto las lee de ahí.

**Se leen, no se copian**, y eso es deliberado: un secreto duplicado en dos
archivos diverge, y el día que alguien cambie la contraseña de la demo sólo se
acordará de uno. Si hiciera falta otra cuenta, `DEMO_USUARIO` y `DEMO_CLAVE`
del entorno mandan sobre el `.env`.

La clave **nunca se imprime**, ni en los errores, ni entra en la línea de
órdenes que `adb` registra.

## Contra qué demo

Contra la **copia local** y no contra la de OVH: la de OVH es la que va a usar
el revisor de Google y sus cuentas no deben gastarse en los intentos de una
automatización, porque Jellyfin desactiva una cuenta tras varios fallos.

**Pero por la IP de LAN de esta máquina, no por `10.0.2.2`.** Las dos llegan al
mismo Jellyfin; lo que cambia es lo que la app le dice al registro, y el
registro decide por ahí. Medido el 04-nov en `/v1/resolve`:

| host | |
|---|---|
| el de OVH | `authorized=true`, site `demo` |
| la IP de LAN de esta máquina | `authorized=true`, site `demo` |
| `10.0.2.2` | **`authorized=false`** → «Este servidor no está dado de alta» |

`DEMO_HOST` del entorno lo cambia.

## Cómo sabe dónde está

No por relojes: **espera a que aparezca un texto concreto** en el árbol de
vistas (`uiautomator dump`), que Compose expone por accesibilidad. Un `sleep`
fijo convierte un emulador lento en una captura de una pantalla a medio pintar,
y eso se ve en la foto pero no en el código.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = Path("/home/proyectos/palomera-ficha")
APK = RAIZ / "app/build/outputs/apk/release/palomera-v0.1.0-rc.1-release.apk"
PAQUETE = "mx.kataliziador.palomera"
AVD = "tv34"
SERIE = "emulator-5556"
PUERTO = "5556"

ROJO, VERDE, GRIS, AMARILLO, FIN = "\033[31m", "\033[32m", "\033[90m", "\033[33m", "\033[0m"


def adb(*args: str, texto: bool = True, binario: bool = False):
    orden = ["adb", "-s", SERIE, *args]
    r = subprocess.run(orden, capture_output=True, text=not binario)
    return r.stdout if not binario else r.stdout


def shell(orden: str) -> str:
    return adb("shell", orden) or ""


def tecla(k: str) -> None:
    shell(f"input keyevent {k}")


def pantalla_dice() -> str:
    """El texto visible, del árbol de vistas."""
    shell("uiautomator dump /sdcard/v.xml 2>/dev/null")
    xml = shell("cat /sdcard/v.xml 2>/dev/null")
    return " │ ".join(sorted(set(re.findall(r'text="([^"]{1,})"', xml))))


def esperar(fragmento: str, segundos: int = 45, que: str = "") -> bool:
    """Espera a que un texto aparezca en pantalla. Devuelve si apareció."""
    limite = time.time() + segundos
    visto = ""
    while time.time() < limite:
        visto = pantalla_dice()
        if fragmento.lower() in visto.lower():
            return True
        time.sleep(1.5)
    print(f"{ROJO}  ✗ no apareció «{fragmento}»{' — ' + que if que else ''}{FIN}")
    print(f"{GRIS}    lo que había: {visto[:300]}{FIN}")
    return False


def foco() -> str:
    """El texto del elemento enfocado, **incluido el de sus hijos**.

    En Compose el nodo que recibe el foco casi nunca lleva el texto: lo lleva
    un hijo. Medido el 04-nov sobre la barra principal — el nodo enfocado tenía
    `text=""` mientras «Inicio», «TV en vivo» y «Buscar» colgaban de él—, y por
    eso una versión anterior de esto no encontraba nunca el botón que buscaba.
    Se mira el subárbol entero.
    """
    shell("uiautomator dump /sdcard/v.xml 2>/dev/null")
    xml = shell("cat /sdcard/v.xml 2>/dev/null")
    try:
        raiz = ET.fromstring(xml)
    except ET.ParseError:
        return ""

    def textos(nodo) -> str:
        propio = nodo.attrib.get("text", "")
        return " ".join(filter(None, [propio, *(textos(h) for h in nodo)])).strip()

    for nodo in raiz.iter():
        if nodo.attrib.get("focused") == "true":
            return textos(nodo)
    return ""


def enfocar(destino: str, direccion: str = "KEYCODE_DPAD_DOWN", pasos: int = 8) -> bool:
    """Mueve el foco hasta el elemento que diga `destino`.

    **Mira dónde está el foco en vez de contar pulsaciones.** Contar funciona
    hasta el día que alguien añade una fila, y entonces la automatización
    pulsa «Cancelar» en silencio y la captura sale de otra pantalla.
    """
    for _ in range(pasos):
        if destino.lower() in foco().lower():
            return True
        tecla(direccion)
        time.sleep(0.6)
    return destino.lower() in foco().lower()


def captura(nombre: str) -> Path:
    destino = DESTINO / nombre
    destino.parent.mkdir(parents=True, exist_ok=True)
    r = subprocess.run(["adb", "-s", SERIE, "exec-out", "screencap", "-p"], capture_output=True)
    destino.write_bytes(r.stdout)
    print(f"{VERDE}  ✓ {nombre}  ({len(r.stdout) / 1024:.0f} kB){FIN}")
    return destino


def emulador_vivo() -> bool:
    r = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    return any(l.startswith(SERIE) and "device" in l for l in r.stdout.splitlines())


def levantar_emulador() -> bool:
    emu = Path("/opt/android-sdk/emulator/emulator")
    if not emu.exists():
        print(f"{ROJO}no encuentro el emulador en {emu}{FIN}")
        return False
    print(f"{GRIS}levantando {AVD}…{FIN}")
    subprocess.Popen(
        [str(emu), "-avd", AVD, "-no-window", "-no-audio", "-no-snapshot",
         "-no-boot-anim", "-gpu", "swiftshader_indirect", "-port", PUERTO],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    subprocess.run(["adb", "-s", SERIE, "wait-for-device"], timeout=300)
    limite = time.time() + 300
    while time.time() < limite:
        if shell("getprop sys.boot_completed").strip() == "1":
            return True
        time.sleep(3)
    print(f"{ROJO}el emulador no terminó de arrancar{FIN}")
    return False


ENV_DEMO = Path("/home/proyectos/palco-demo/.env")


def credenciales() -> tuple[str, str]:
    """El usuario y la clave de la demo, del entorno o del `.env` de la demo.

    El entorno manda, para poder apuntar a otra cuenta sin tocar nada. Si no
    dice nada, se leen de donde ya viven: duplicar un secreto es garantizar que
    un día las dos copias digan cosas distintas.
    """
    usuario = os.environ.get("DEMO_USUARIO", "").strip()
    clave = os.environ.get("DEMO_CLAVE", "")
    if usuario and clave:
        return usuario, clave
    if not ENV_DEMO.exists():
        return "", ""
    env = {}
    for linea in ENV_DEMO.read_text().splitlines():
        if "=" in linea and not linea.strip().startswith("#"):
            k, _, v = linea.partition("=")
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env.get("PALCO_DEMO_USUARIO", ""), env.get("PALCO_DEMO_CLAVE", "")


def host_por_defecto() -> str:
    """La demo local, vista con la dirección que el **registro autoriza**.

    No vale `10.0.2.2`, que es como el emulador ve a esta máquina: el registro
    decide por el nombre que la app le manda, y `10.0.2.2` no está dado de
    alta — medido el 04-nov, `/v1/resolve` devuelve `authorized=false` y la
    sección enseña «Este servidor no está dado de alta». Con la IP de LAN de
    esta máquina sí: `authorized=true`, site `demo`.

    Se averigua en vez de escribirla, por las dos razones a la vez: `CLAUDE.md`
    §9 no deja ninguna dirección en el repositorio, y la IP cambia.
    """
    import socket

    con = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        con.connect(("192.0.2.1", 9))  # no manda nada: pregunta la ruta de salida
        return f"{con.getsockname()[0]}:8096"
    except OSError:
        return ""
    finally:
        con.close()


def demo_viva(host: str) -> bool:
    """¿Contesta el Jellyfin de la demo? Desde aquí, no desde el emulador.

    `10.0.2.2` es como el emulador ve a esta máquina, así que para comprobarlo
    desde aquí hay que traducirlo a `127.0.0.1`.
    """
    import urllib.error
    import urllib.request

    local = host
    try:
        with urllib.request.urlopen(f"http://{local}/System/Info/Public", timeout=8) as r:
            return b"ServerName" in r.read()
    except (urllib.error.URLError, OSError):
        return False


def main() -> int:
    host = os.environ.get("DEMO_HOST", "").strip() or host_por_defecto()
    if not host:
        print(f"{ROJO}no pude averiguar la dirección de esta máquina{FIN}")
        print(f"{GRIS}  pon DEMO_HOST=‹host›:8096 a mano{FIN}")
        return 2
    usuario, clave = credenciales()
    if not usuario or not clave:
        print(f"{ROJO}no encuentro las credenciales de la demo{FIN}")
        print(f"{GRIS}  deberían estar en {ENV_DEMO} como PALCO_DEMO_USUARIO y PALCO_DEMO_CLAVE,{FIN}")
        print(f"{GRIS}  o en el entorno como DEMO_USUARIO y DEMO_CLAVE{FIN}")
        return 2

    if not demo_viva(host):
        print(f"{ROJO}la demo no contesta en {host}{FIN}")
        print(f"{GRIS}  si es la copia local: cd /home/proyectos/palco-demo && docker compose up -d{FIN}")
        return 2

    if not APK.exists():
        print(f"{ROJO}no está el APK de release{FIN}")
        print(f"{GRIS}  JELLYFIN_VERSION=0.1.0-rc.1 ./gradlew :app:assembleRelease{FIN}")
        return 2

    if not emulador_vivo() and not levantar_emulador():
        return 1

    tam = shell("wm size").strip()
    if "1920x1080" not in tam:
        print(f"{AMARILLO}  ~ el emulador no está a 1920×1080 sino «{tam}»{FIN}")
        print(f"{GRIS}    Play pide capturas de TV a 1920×1080{FIN}")

    print("preparando la caja")
    subprocess.run(["adb", "-s", SERIE, "uninstall", PAQUETE], capture_output=True)
    r = subprocess.run(["adb", "-s", SERIE, "install", "-r", str(APK)], capture_output=True, text=True)
    if "Success" not in r.stdout:
        print(f"{ROJO}no se pudo instalar: {r.stdout.strip() or r.stderr.strip()}{FIN}")
        return 1
    shell(f"cmd locale set-app-locales {PAQUETE} --locales es-MX")
    shell("cmd alarm set-timezone America/Mexico_City")
    print(f"{VERDE}  ✓ release instalado, es-MX, hora de México{FIN}")

    print("entrando a la demo")
    shell(f"monkey -p {PAQUETE} -c android.intent.category.LAUNCHER 1")
    if not esperar("Conectar con mi proveedor", 60, "la pantalla de bienvenida"):
        return 1
    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("escribe la dirección del servidor", 30, "el selector de proveedor"):
        return 1
    tecla("KEYCODE_DPAD_DOWN"); tecla("KEYCODE_DPAD_DOWN"); tecla("KEYCODE_DPAD_CENTER")
    time.sleep(2)
    shell(f"input text '{host}'")
    time.sleep(1)
    tecla("KEYCODE_BACK")
    time.sleep(1)
    tecla("KEYCODE_DPAD_DOWN"); tecla("KEYCODE_DPAD_CENTER")
    if not esperar("Quién está viendo", 60, "la lista de cuentas del servidor"):
        return 1
    print(f"{VERDE}  ✓ conectado al servidor de la demo{FIN}")

    # Dos caminos, y los dos existen de verdad: la demo **local** no publica
    # usuarios —`/Users/Public` devuelve `[]`, que es el valor de fábrica de
    # Jellyfin— así que hay que entrar por «Agregar cuenta»; la de OVH sí los
    # publica y entonces se elige la cuenta. Se mira cuál es en vez de suponer.
    visible = f" │ {pantalla_dice()} │ "
    objetivo = usuario if f" │ {usuario} │ " in visible else "Agregar cuenta"
    if not enfocar(objetivo, "KEYCODE_DPAD_RIGHT", 6) and not enfocar(objetivo, "KEYCODE_DPAD_DOWN", 4):
        print(f"{ROJO}  ✗ no pude enfocar «{objetivo}» en la lista de cuentas{FIN}")
        return 1
    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("Activa tu televisión", 45, "la pantalla de acceso"):
        return 1

    # Con QuickConnect encendido la primera pantalla es la del código. Es la
    # captura 3, y la demo local **sí** lo tiene encendido —la de OVH no—, así
    # que aquí se puede rehacer lo que `FICHA.md` daba por irrepetible.
    if "dale este código" in pantalla_dice().lower():
        time.sleep(2)
        captura("3-activacion.png")
        if not enfocar("Usar contraseña"):
            print(f"{ROJO}  ✗ no encontré el botón «Usar contraseña»{FIN}")
            return 1
        tecla("KEYCODE_DPAD_CENTER")
        if not esperar("Nombre de usuario", 30, "el formulario de usuario y contraseña"):
            return 1

    tecla("KEYCODE_DPAD_CENTER")
    time.sleep(2)
    shell(f"input text '{usuario}'")
    time.sleep(1)
    tecla("KEYCODE_BACK")
    time.sleep(1)
    tecla("KEYCODE_DPAD_DOWN"); tecla("KEYCODE_DPAD_CENTER")
    time.sleep(2)
    # La clave va por argumento de `adb`, nunca por una cadena de shell: así no
    # queda en ningún log de órdenes ni se rompe con un carácter raro.
    subprocess.run(["adb", "-s", SERIE, "shell", "input", "text", clave], capture_output=True)
    time.sleep(1)
    tecla("KEYCODE_BACK")
    time.sleep(1)
    tecla("KEYCODE_DPAD_DOWN"); tecla("KEYCODE_DPAD_CENTER")
    if not esperar("Mis medios", 90, "el inicio, ya con sesión"):
        return 1
    print(f"{VERDE}  ✓ sesión iniciada{FIN}")

    print("capturando")
    time.sleep(5)
    # El inicio se captura pero **no se sube** (ver no-se-suben/LEEME.txt):
    # desde que se borró la fila «TV en vivo» esa pantalla queda casi vacía y
    # en un carrusel de tienda una pantalla vacía resta. Se sigue sacando
    # porque es una comprobación barata de que el inicio sigue en pie.
    captura("no-se-suben/4-inicio.png")

    # Subir a la barra principal y caminar hasta «TV en vivo». Dos direcciones
    # porque desde el inicio hay que subir, y una vez arriba moverse de lado.
    for _ in range(3):
        if "TV en vivo" in foco():
            break
        tecla("KEYCODE_DPAD_UP")
        time.sleep(0.8)
    if not enfocar("TV en vivo", "KEYCODE_DPAD_RIGHT", 4):
        print(f"{ROJO}  ✗ no pude enfocar «TV en vivo» en la barra{FIN}")
        print(f"{GRIS}    el foco está en: «{foco()}»{FIN}")
        return 1
    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("canales", 60, "la sección de canales con sus grupos"):
        return 1
    time.sleep(4)
    captura("1-seccion-de-canales.png")

    # Sin bajar: al llegar a la sección el foco **ya está** en la primera ficha
    # del primer grupo. El «abajo» que había aquí saltaba al grupo siguiente y
    # sintonizaba el 201, no el 101.
    print(f"{GRIS}  sintonizando el canal enfocado, que es el primero…{FIN}")
    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("cambiar de canal", 90, "la barra de canal sobre el vídeo"):
        return 1
    time.sleep(2)
    captura("2-barra-de-canal.png")

    print()
    print(f"{VERDE}capturas rehechas en {DESTINO}{FIN}")
    print(f"{AMARILLO}Míralas antes de subirlas.{FIN} Una automatización de interfaz puede")
    print(f"{GRIS}  capturar una pantalla a medio pintar y el código no se entera.{FIN}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
