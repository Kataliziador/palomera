#!/usr/bin/env python3
"""Las capturas de **teléfono** de la ficha de Play, con una orden.

Hermana de `capturar-ficha.py`, que hace las de televisión. Las reglas son las
mismas y están en `FICHA.md` §5, porque el riesgo es el mismo:

- **de la demo, nunca del site de un cliente** (`PLAY.md` §6). Lo que sale en
  una captura de tienda se publica, y el catálogo de un ISP tiene derechos de
  terceros. La demo trae canales «Palomera Uno»…«Palomera Ocho» con logos que
  son cuadrados de color nuestros, y dos películas de carta de ajuste;
- **de una compilación de `release`**, no de depuración: el APK de depuración
  enseña botones que no existen en el que se publica;
- **y hay que mirarlas antes de subirlas.** Una automatización puede acertar
  todos los pasos y encuadrar mal el último.

## Lo que Play exige para capturas de teléfono

Lado de 320 a 3840 px y **relación máxima 2:1**. Por eso el emulador se
configura a **1080×1920** (1,78:1) y no a los 1080×2400 de un teléfono
moderno, que son 2,22:1 y **Play rechaza**. Medido el 11-nov.

## Las tres que se suben

Las mismas que en televisión y por el mismo motivo — cuentan la historia
entera: qué canales hay, cómo se ve uno y cómo se activa el aparato.

El inicio y el catálogo se capturan a `no-se-suben/` como comprobación barata
de que siguen en pie. No se suben porque la demo tiene **dos** títulos, y una
pantalla medio vacía en un carrusel de tienda trabaja en contra — es la misma
razón por la que se descartó la cuarta de televisión el 04-nov.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
from pathlib import Path

VERDE, ROJO, FIN = "\033[32m", "\033[31m", "\033[0m"

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = Path("/home/proyectos/palomera-ficha/telefono")
PAQUETE = "mx.kataliziador.palomera"
AVD = "tel34"
SERIE = "emulator-5560"
PUERTO = "5560"
ADB = "/opt/android-sdk/platform-tools/adb"
EMULADOR = "/opt/android-sdk/emulator/emulator"
ENV_DEMO = Path("/home/proyectos/palco-demo/.env")

# 9:16. Un teléfono real es 9:20, que Play rechaza por pasar de 2:1.
ANCHO, ALTO, DENSIDAD = 1080, 1920, 420


def adb(*a: str, binario: bool = False):
    r = subprocess.run([ADB, "-s", SERIE, *a], capture_output=True, timeout=300)
    return r.stdout if binario else r.stdout.decode("utf-8", "replace")


def shell(orden: str) -> str:
    return adb("shell", orden)


def arbol() -> list[tuple[str, int, int]]:
    """Los textos en pantalla con su centro.

    **Aviso de instrumento:** `uiautomator` no ve los composables del
    reproductor —devuelve vacío aunque la captura los muestre—, así que ahí se
    usan coordenadas y no esta función. En las demás pantallas sí los ve.
    """
    shell("uiautomator dump /sdcard/u.xml >/dev/null 2>&1")
    x = shell("cat /sdcard/u.xml")
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        t = m.group(1)
        a, b, c, d = map(int, m.groups()[1:])
        if t.strip():
            out.append((t, (a + c) // 2, (b + d) // 2))
    return out


def esperar(frag: str, segundos: int = 45):
    """Espera a que aparezca un texto. **No usa relojes fijos**: un `sleep`
    captura una pantalla a medio pintar, y eso ya pasó con las de televisión."""
    fin = time.time() + segundos
    while time.time() < fin:
        for t, x, y in arbol():
            if frag.lower() in t.lower():
                return (t, x, y)
        time.sleep(1.5)
    return None


def tocar(frag: str, dy: int = 0, segundos: int = 45) -> bool:
    p = esperar(frag, segundos)
    if not p:
        print(f"{ROJO}  no apareció: {frag}{FIN}")
        return False
    shell(f"input tap {p[1]} {p[2] + dy}")
    return True


def captura(nombre: str) -> Path:
    d = DESTINO / nombre
    d.parent.mkdir(parents=True, exist_ok=True)
    d.write_bytes(adb("exec-out", "screencap", "-p", binario=True))
    print(f"{VERDE}  {nombre}{FIN}")
    return d


def credenciales() -> tuple[str, str]:
    """Del entorno o del `.env` de la demo. **Nunca escritas aquí.**"""
    u, c = os.environ.get("DEMO_USUARIO", "").strip(), os.environ.get("DEMO_CLAVE", "")
    if u and c:
        return u, c
    if not ENV_DEMO.exists():
        return "", ""
    env = {}
    for linea in ENV_DEMO.read_text().splitlines():
        if "=" in linea and not linea.strip().startswith("#"):
            k, _, v = linea.partition("=")
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env.get("PALCO_DEMO_USUARIO", ""), env.get("PALCO_DEMO_CLAVE", "")


def host_demo() -> str:
    """La demo local por la IP de LAN de esta máquina.

    No vale `10.0.2.2`: el registro decide por el nombre que la app manda y esa
    no está dada de alta. Se averigua en vez de escribirse, porque `CLAUDE.md`
    §9 no deja direcciones en el repositorio y además cambia.
    """
    import socket

    con = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        con.connect(("192.0.2.1", 9))
        return f"{con.getsockname()[0]}:8096"
    except OSError:
        return ""
    finally:
        con.close()


def apk_de_release() -> Path | None:
    """El APK de `release` más reciente. **No vale el de depuración.**"""
    carpeta = RAIZ / "app/build/outputs/apk/release"
    apks = sorted(carpeta.glob("*-release.apk"), key=lambda p: p.stat().st_mtime)
    return apks[-1] if apks else None


def dimensionar() -> None:
    """Deja el AVD en 1080×1920. Play rechaza más de 2:1."""
    cfg = Path.home() / f".android/avd/{AVD}.avd/config.ini"
    if not cfg.exists():
        return
    fuera = {"hw.lcd.width", "hw.lcd.height", "hw.lcd.density"}
    lineas = [l for l in cfg.read_text().splitlines() if l.split("=")[0].strip() not in fuera]
    lineas += [f"hw.lcd.width={ANCHO}", f"hw.lcd.height={ALTO}", f"hw.lcd.density={DENSIDAD}"]
    cfg.write_text("\n".join(lineas) + "\n")


def levantar() -> bool:
    if SERIE in adb("devices"):
        if shell("getprop sys.boot_completed").strip() == "1":
            return True
    dimensionar()
    subprocess.Popen(
        [EMULADOR, "-avd", AVD, "-no-window", "-no-audio", "-no-snapshot",
         "-no-boot-anim", "-gpu", "swiftshader_indirect", "-port", PUERTO],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    fin = time.time() + 300
    while time.time() < fin:
        if shell("getprop sys.boot_completed").strip() == "1":
            return True
        time.sleep(6)
    print(f"{ROJO}el emulador no terminó de arrancar{FIN}")
    return False


def main() -> int:
    apk = apk_de_release()
    if not apk:
        print(f"{ROJO}no hay APK de release. Compila con "
              f"JELLYFIN_VERSION=<version> ./gradlew :app:assembleRelease{FIN}")
        return 1
    usuario, clave = credenciales()
    if not usuario or not clave:
        print(f"{ROJO}sin credenciales de la demo (ver {ENV_DEMO}){FIN}")
        return 1
    host = os.environ.get("DEMO_HOST", "").strip() or host_demo()
    if not host:
        print(f"{ROJO}no se pudo averiguar la dirección de la demo{FIN}")
        return 1

    if not levantar():
        return 1
    print(f"  APK: {apk.name}")
    adb("install", "-r", str(apk))
    shell(f"cmd locale set-app-locales {PAQUETE} --locales es-MX")
    shell(f"pm clear {PAQUETE}")
    shell(f"monkey -p {PAQUETE} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")

    # --- la puerta, por la ruta 3: escribir la dirección ---
    if not esperar("¿Quién te da el servicio?", 60):
        print(f"{ROJO}no abrió la puerta{FIN}")
        return 1
    tocar("Escribir la dirección")
    if not esperar("Escribe la dirección"):
        return 1
    tocar("Dirección del servidor", dy=90)
    time.sleep(1)
    shell(f"input text '{host}'")
    time.sleep(1)
    tocar("Continuar")

    if not esperar("Activa este teléfono", 60):
        print(f"{ROJO}no llegó a la activación{FIN}")
        return 1
    time.sleep(2)
    captura("3-activacion.png")

    # --- y se entra con contraseña, que es el camino del revisor de Google ---
    tocar("Usar usuario y contraseña")
    if not esperar("Entra con tu cuenta"):
        return 1
    campos = {t: (x, y) for t, x, y in arbol()}
    ux, uy = campos["Usuario"]
    shell(f"input tap 540 {uy + 94}")
    time.sleep(1)
    shell(f"input text '{usuario}'")
    time.sleep(1)
    cx, cy = campos["Contraseña"]
    shell(f"input tap 540 {cy + 94}")
    time.sleep(1)
    shell(f"input text '{clave}'")
    time.sleep(1)
    tocar("Entrar")
    if not esperar("Catálogo", 60):
        print(f"{ROJO}no entró{FIN}")
        return 1
    time.sleep(4)
    captura("no-se-suben/inicio.png")

    # --- los canales, con sus logos ---
    tocar("En vivo")
    if not esperar("canales", 45):
        return 1
    time.sleep(4)
    captura("1-canales.png")

    # --- un canal reproduciendo, apaisado y a pantalla completa ---
    tocar("Palomera Uno")
    print("  esperando al primer fotograma…")
    time.sleep(26)
    # Aquí `uiautomator` no ve nada (ver `arbol`), así que un toque al centro
    # del apaisado trae los controles, que se ocultan a los 4 s.
    shell(f"input tap {ALTO // 2} {ANCHO // 2}")
    time.sleep(2)
    captura("2-canal.png")

    shell("input keyevent 4")
    if esperar("canales", 30):
        tocar("Catálogo")
        time.sleep(6)
        captura("no-se-suben/catalogo.png")

    print(f"\n{VERDE}listo{FIN} — en {DESTINO}")
    print("  míralas antes de subirlas: los pasos pueden acertar y el encuadre no")
    return 0


if __name__ == "__main__":
    sys.exit(main())
