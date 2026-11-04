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

    set -a; . herramientas/entorno.local; set +a
    DEMO_USUARIO=probador DEMO_CLAVE=… python3 -I herramientas/capturar-ficha.py

`DEMO_CLAVE` **nunca se imprime**, ni en los errores, ni queda en el historial
si se pone en `herramientas/entorno.local`, que es donde debería vivir.

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


def captura(nombre: str) -> Path:
    DESTINO.mkdir(parents=True, exist_ok=True)
    destino = DESTINO / nombre
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


def main() -> int:
    host = os.environ.get("PALOMERA_DEMO_HOST", "").strip()
    usuario = os.environ.get("DEMO_USUARIO", "").strip()
    clave = os.environ.get("DEMO_CLAVE", "")
    if not host or not usuario or not clave:
        print(f"{ROJO}faltan PALOMERA_DEMO_HOST, DEMO_USUARIO o DEMO_CLAVE{FIN}")
        print(f"{GRIS}  el host vive en herramientas/entorno.local;{FIN}")
        print(f"{GRIS}  la clave del demo también debería, y nunca en el repositorio ni en un chat{FIN}")
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
    shell(f"input text '{host}:8096'")
    time.sleep(1)
    tecla("KEYCODE_BACK")
    time.sleep(1)
    tecla("KEYCODE_DPAD_DOWN"); tecla("KEYCODE_DPAD_CENTER")
    if not esperar(usuario, 60, "la lista de cuentas del servidor"):
        return 1
    print(f"{VERDE}  ✓ conectado al servidor de la demo{FIN}")

    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("contrase", 30, "la pantalla de contraseña"):
        return 1
    # La clave no se imprime y no entra en la línea de órdenes del log.
    subprocess.run(["adb", "-s", SERIE, "shell", "input", "text", clave], capture_output=True)
    time.sleep(1)
    tecla("KEYCODE_BACK")
    time.sleep(1)
    tecla("KEYCODE_ENTER")
    if not esperar("Mis medios", 90, "el inicio, ya con sesión"):
        return 1
    print(f"{VERDE}  ✓ sesión iniciada{FIN}")

    print("capturando")
    time.sleep(4)
    captura("4-inicio.png")

    tecla("KEYCODE_DPAD_UP"); time.sleep(1); tecla("KEYCODE_DPAD_UP"); time.sleep(1)
    if not esperar("TV en vivo", 30, "la barra principal"):
        return 1
    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("canales", 60, "la sección de canales con sus grupos"):
        return 1
    time.sleep(3)
    captura("1-seccion-de-canales.png")

    print(f"{GRIS}  sintonizando el primer canal…{FIN}")
    tecla("KEYCODE_DPAD_DOWN"); time.sleep(1)
    tecla("KEYCODE_DPAD_CENTER")
    if not esperar("cambiar de canal", 90, "la barra de canal sobre el vídeo"):
        return 1
    time.sleep(2)
    captura("2-barra-de-canal.png")

    print()
    print(f"{VERDE}tres capturas rehechas en {DESTINO}{FIN}")
    print(f"{GRIS}  la 3 (activación con código) no se rehace: la demo tiene QuickConnect{FIN}")
    print(f"{GRIS}  apagado a propósito. La del 02-nov sigue siendo válida.{FIN}")
    print(f"{AMARILLO}Míralas antes de subirlas.{FIN} Una automatización de interfaz puede")
    print(f"{GRIS}  capturar una pantalla a medio pintar y el código no se entera.{FIN}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
