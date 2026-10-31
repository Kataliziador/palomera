#!/usr/bin/env python3
"""Comprueba, desde fuera, que el site de demostración está listo para la
prueba cerrada de Google Play.

**Por qué existe este archivo.** El 31-oct comprobé a mano los catorce datos
que hacen falta para que un probador pueda instalar la app y llegar a ver un
canal, y encontré cinco huecos. Comprobarlos a mano cuesta veinte minutos,
se hace distinto cada vez y el resultado es un mensaje mío que hay que creer.
Esto lo deja en una orden que cualquiera corre en veinte segundos y que
**puede salir en rojo**, que es la única forma de que salir en verde
signifique algo (`.claude/skills/medir-antes-de-afirmar`).

Se corre después de cada paso del despliegue. El plan está en
`docs/prueba-cerrada.md`.

    ./herramientas/comprobar-demo.py

Dos comprobaciones necesitan datos que **no viven en el repositorio** y que
se leen del entorno si están; sin ellas el resto corre igual y esas dos
salen omitidas:

    DEMO_CODIGO=DEMO-1234 DEMO_USUARIO=probador DEMO_CLAVE=… \\
        ./herramientas/comprobar-demo.py

La clave **nunca** se imprime, ni en los errores.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import socket
import ssl
import sys
import urllib.error
import urllib.request
from urllib.parse import urlparse

VERDE, ROJO, AMARILLO, GRIS, FIN_COLOR = (
    "\033[32m", "\033[31m", "\033[33m", "\033[90m", "\033[0m"
)

ESPERA = 20

# El fondo contra el que `livetv/.../Brand.kt` mide el acento, y su umbral.
# Si cambian ahí, cambian aquí: esta comprobación existe para avisar antes de
# que el abonado vea el acento caído al gris neutro, no para repetir el número.
FONDO_SECCION = "#101010"
CONTRASTE_MINIMO = 3.0

fallos: list[str] = []
roces: list[str] = []
avisos: list[str] = []
ESTRICTO = False


def bien(texto: str) -> None:
    print(f"{VERDE}  ✓ {texto}{FIN_COLOR}")


def mal(clave: str, texto: str) -> None:
    print(f"{ROJO}  ✗ {texto}{FIN_COLOR}")
    fallos.append(clave)


def roce(clave: str, texto: str) -> None:
    """Hace tropezar a un probador, pero **no le impide llegar**.

    La diferencia importa y la aprendí equivocándome: a `/Users/Public`
    vacío lo marqué como bloqueo, y resulta que el Jellyfin de la oficina
    —el que usamos todos los días y donde `test` entra sin problema—
    devuelve `[]` igual. Es el estado por defecto, no un descuido del demo.

    Ninguno de los roces deja al probador sin salida: la dirección a mano
    nunca se esconde, «Usar contraseña» nunca se quita y la marca neutra es
    una marca. Lo que hacen es gastar la paciencia de alguien que no cobra
    por esto. Con `--estricto` cuentan como fallo, que es lo que conviene
    antes de mandar las invitaciones.
    """
    print(f"{AMARILLO}  ~ {texto}{FIN_COLOR}")
    roces.append(clave)


def ojo(texto: str) -> None:
    print(f"{AMARILLO}  ! {texto}{FIN_COLOR}")
    avisos.append(texto)


def nota(texto: str) -> None:
    print(f"{GRIS}    · {texto}{FIN_COLOR}")


def omitida(texto: str) -> None:
    print(f"{GRIS}  · {texto}{FIN_COLOR}")


def pedir(url: str, cuerpo: bytes | None = None, espera: int = ESPERA):
    """Devuelve (estado, texto). Un fallo de red es estado 0, nunca excepción."""
    pet = urllib.request.Request(url, data=cuerpo)
    if cuerpo is not None:
        pet.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(pet, timeout=espera) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001 — aquí cualquier fallo es «no contestó»
        return 0, f"{type(e).__name__}: {e}"


def luminancia(hexa: str) -> float:
    r, g, b = (int(hexa[i:i + 2], 16) / 255 for i in (1, 3, 5))

    def canal(c: float) -> float:
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b)


def contraste(a: str, b: str) -> float:
    la, lb = luminancia(a), luminancia(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


# ---------------------------------------------------------------- el Jellyfin

def comprobar_jellyfin(base: str) -> str | None:
    """Devuelve el `ServerName`, o None si no contestó."""
    print("\nEl Jellyfin del site")
    estado, texto = pedir(f"{base}/System/Info/Public")
    if estado != 200:
        mal("jellyfin", f"{base}/System/Info/Public → {estado or texto}")
        return None
    try:
        inf = json.loads(texto)
    except json.JSONDecodeError:
        mal("jellyfin", "contestó 200 pero no era JSON")
        return None
    bien(f"contesta: «{inf.get('ServerName')}», Jellyfin {inf.get('Version')}")
    if not inf.get("StartupWizardCompleted"):
        mal("jellyfin", "el asistente inicial no está terminado: nadie puede entrar")
    return inf.get("ServerName")


def comprobar_cuentas(base: str) -> None:
    """La pantalla de acceso tiene que ofrecer una cuenta que elegir.

    Con la lista vacía el probador lee «¡No se han encontrado cuentas!» y
    tiene que teclear el usuario con el mando. Los nombres son públicos por
    construcción —ése es el endpoint que los publica—, así que imprimirlos
    no revela nada que el servidor no esté dando ya.
    """
    estado, texto = pedir(f"{base}/Users/Public")
    if estado != 200:
        mal("cuentas", f"/Users/Public → {estado or texto}")
        return
    try:
        usuarios = json.loads(texto)
    except json.JSONDecodeError:
        mal("cuentas", "/Users/Public no era JSON")
        return
    if not usuarios:
        roce("cuentas", "ninguna cuenta que elegir: habrá que pulsar «Agregar cuenta» "
                        "y teclear el usuario con el mando")
        nota("el Jellyfin de la oficina devuelve «[]» igual: es el valor por defecto")
        nota("si quieres que la elija con el mando, hay que hacerla visible desde el panel")
        return
    nombres = ", ".join(str(u.get("Name")) for u in usuarios)
    bien(f"{len(usuarios)} cuenta(s) visible(s) para elegir con el mando: {nombres}")


def comprobar_quickconnect(base: str) -> None:
    """Tiene que estar **apagado** en el demo.

    No porque sobre, sino porque autorizar un código exige el panel de
    abonados (`CLAUDE.md` §8) y el demo no lo tiene: encendido, la app
    ofrece un código de activación que no lleva a ninguna parte. Apagado,
    `initiateQuickConnect()` falla, la app emite `UnavailableQuickConnectState`
    y se va sola a usuario y contraseña (`UserLoginFragment:96-97`).
    """
    estado, texto = pedir(f"{base}/QuickConnect/Enabled")
    if estado != 200:
        ojo(f"/QuickConnect/Enabled → {estado or texto}; no se pudo saber")
        return
    if texto.strip().lower() == "false":
        bien("QuickConnect apagado: la app irá directa a usuario y contraseña")
    else:
        roce("quickconnect", "QuickConnect encendido y sin panel que autorice el código")
        nota("la app ofrecerá primero un código que nadie puede aprobar; «Usar")
        nota("contraseña» sigue ahí, así que el probador llega — después de dudar")


def comprobar_biblioteca(base: str) -> None:
    """Opcional: exige credenciales, y sólo mira que haya algo que ver.

    Un site con la biblioteca vacía deja al probador —y al revisor de
    Google— entrando a una casa sin muebles.
    """
    usuario, clave = os.environ.get("DEMO_USUARIO"), os.environ.get("DEMO_CLAVE")
    if not usuario or not clave:
        omitida("biblioteca VOD: omitida (sin DEMO_USUARIO / DEMO_CLAVE)")
        return
    cabecera = (
        'MediaBrowser Client="comprobar-demo", Device="herramientas", '
        'DeviceId="comprobar-demo", Version="0.0.0"'
    )
    pet = urllib.request.Request(
        f"{base}/Users/AuthenticateByName",
        data=json.dumps({"Username": usuario, "Pw": clave}).encode(),
        headers={"Content-Type": "application/json", "Authorization": cabecera},
    )
    try:
        with urllib.request.urlopen(pet, timeout=ESPERA) as r:
            sesion = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        # Sin cuerpo y sin clave en el mensaje: un 401 aquí ya lo dice todo.
        mal("biblioteca", f"la cuenta «{usuario}» no entró: HTTP {e.code}")
        return
    except Exception as e:  # noqa: BLE001
        mal("biblioteca", f"no se pudo entrar: {type(e).__name__}")
        return

    token, uid = sesion.get("AccessToken"), (sesion.get("User") or {}).get("Id")
    bien(f"la cuenta «{usuario}» entra")
    estado, texto = pedir(f"{base}/Users/{uid}/Items?Recursive=true&Limit=1&api_key={token}")
    if estado != 200:
        ojo(f"no se pudo contar la biblioteca: HTTP {estado}")
        return
    total = (json.loads(texto) or {}).get("TotalRecordCount", 0)
    if total:
        bien(f"la biblioteca tiene {total} elementos")
    else:
        mal("biblioteca", "la biblioteca está vacía: el probador entra y no ve nada")


# ----------------------------------------------------------------- el registro

def comprobar_tls(registro: str) -> None:
    print("\nEl registro")
    host = urlparse(registro).hostname or registro
    try:
        ctx = ssl.create_default_context()
        with socket.create_connection((host, 443), timeout=ESPERA) as s:
            with ctx.wrap_socket(s, server_hostname=host) as ts:
                cert = ts.getpeercert()
    except Exception as e:  # noqa: BLE001
        mal("tls", f"no se pudo leer el certificado de {host}: {type(e).__name__}")
        return
    fin = dt.datetime.strptime(cert["notAfter"], "%b %d %H:%M:%S %Y %Z").replace(
        tzinfo=dt.timezone.utc
    )
    dias = (fin - dt.datetime.now(dt.timezone.utc)).days
    # La app exige TLS hacia el registro (`RegistryTrust` y el
    # `network_security_config`). Un certificado caducado a mitad de los 14
    # días no degrada: corta el selector entero.
    if dias < 21:
        mal("tls", f"el certificado caduca en {dias} días, dentro de la prueba")
    else:
        bien(f"certificado válido {dias} días más ({fin:%Y-%m-%d})")


def comprobar_resolve(registro: str, host: str) -> None:
    cuerpo = json.dumps(
        {"host": host, "installId": "comprobar-demo", "appVersion": "0.0.0"}
    ).encode()
    estado, texto = pedir(f"{registro}/v1/resolve", cuerpo)
    if estado != 200:
        mal("resolve", f"/v1/resolve → {estado or texto}")
        return
    r = json.loads(texto)
    if not r.get("authorized"):
        mal("resolve", f"el registro no autoriza «{host}»: {texto}")
        return
    site = (r.get("site") or {}).get("id")
    canales = (r.get("site") or {}).get("channelsUrl")
    bien(f"autoriza «{host}» → site «{site}»")
    nota(f"channelsUrl: {canales}")

    until = r.get("until")
    if until:
        fin = dt.datetime.fromisoformat(until.replace("Z", "+00:00"))
        dias = round((fin - dt.datetime.now(dt.timezone.utc)).total_seconds() / 86400)
        # Durante la prueba cerrada la ventana baja a 7 días, que es lo que
        # hace que el diagnóstico de reproducción llegue **dentro** de los 14
        # y no una semana después (docs/DESPLIEGUE_SELECTOR.md §1).
        if dias > 14:
            ojo(f"la ventana es de {dias} días: el diagnóstico de reproducción "
                f"llegaría después de acabar la prueba")
            nota("AUTORIZADOR_VENTANA_DIAS=7 mientras dure la prueba")
        else:
            bien(f"ventana de {dias} días: el diagnóstico llegará dentro de la prueba")


def comprobar_selector(registro: str, base_jellyfin: str) -> None:
    estado, texto = pedir(f"{registro}/v1/proveedor", json.dumps(
        {"installId": "comprobar-demo", "appVersion": "0.0.0"}
    ).encode())
    if estado == 404:
        roce("selector", "/v1/proveedor → 404: la rama del selector no está desplegada")
        nota("el probador tendrá que teclear la dirección entera con el mando, que es")
        nota("justo lo que el selector existe para evitar. No le cierra la puerta")
        return
    if estado != 200:
        mal("selector", f"/v1/proveedor → {estado or texto}")
        return
    bien("/v1/proveedor contesta")

    codigo = os.environ.get("DEMO_CODIGO")
    if not codigo:
        omitida("el código del demo: omitido (sin DEMO_CODIGO)")
        return
    estado, texto = pedir(f"{registro}/v1/proveedor", json.dumps(
        {"installId": "comprobar-demo", "appVersion": "0.0.0", "codigo": codigo}
    ).encode())
    if estado != 200:
        mal("codigo", f"el código del demo → HTTP {estado}")
        return
    prov = (json.loads(texto) or {}).get("proveedor")
    if not prov:
        mal("codigo", "el código del demo no resuelve a ningún proveedor")
        return
    url = prov.get("serverUrl") or ""
    bien(f"el código resuelve a «{prov.get('nombre')}» → {url}")
    # Una errata aquí manda la caja a ninguna parte y el síntoma sale semanas
    # después, en la sala de un abonado (docs/selector-de-proveedor.md §4).
    if urlparse(url).hostname != urlparse(base_jellyfin).hostname:
        mal("codigo", f"ese serverUrl no es el Jellyfin que acabo de comprobar")
    if not url.startswith(("http://", "https://")):
        mal("codigo", "el serverUrl no lleva esquema: la app lo descartará")


def comprobar_sin_enumeracion(registro: str) -> None:
    """Lo que no se puede enumerar no se puede filtrar.

    Hay una prueba de unidad que lo defiende en el autorizador; esto lo
    comprueba contra lo que de verdad está desplegado, que es otra cosa.
    """
    abiertos = []
    for ruta in ("/v1/proveedores", "/v1/sites", "/v1/proveedor/lista"):
        estado, _ = pedir(f"{registro}{ruta}")
        if estado not in (0, 404, 405):
            abiertos.append(f"{ruta} → {estado}")
    if abiertos:
        mal("enumeracion", "hay rutas que podrían listar la cartera: " + ", ".join(abiertos))
    else:
        bien("ninguna ruta lista proveedores")


# --------------------------------------------------------- servicio de canales

def comprobar_config(canales_base: str) -> dict | None:
    print("\nEl servicio de canales")
    estado, texto = pedir(f"{canales_base}/config.json")
    if estado != 200:
        mal("config", f"config.json → {estado or texto}")
        return None
    try:
        cfg = json.loads(texto)
    except json.JSONDecodeError:
        mal("config", "config.json no era JSON")
        return None
    if "version" not in cfg:
        mal("config", "sin «version»: la app rechaza el documento entero")
    grupos = cfg.get("groups") or []
    canales = cfg.get("channels") or []
    bien(f"config.json: {len(grupos)} grupos, {len(canales)} canales, version {cfg.get('version')}")
    if not canales:
        mal("config", "ni un canal: la sección se queda con la caché")

    marca = cfg.get("brand")
    if not marca:
        roce("marca", "«brand» nulo: el probador verá la marca neutra")
        nota("la marca neutra está diseñada para esto, así que no se ve roto;")
        nota("lo que se pierde es probar las tres ranuras de §8 en los 14 días")
    else:
        nombre, acento = marca.get("displayName"), (marca.get("accent") or "")
        bien(f"marca: «{nombre}», acento {acento or '(ninguno)'}")
        if len(acento) == 7 and acento.startswith("#"):
            try:
                c = contraste(acento, FONDO_SECCION)
            except ValueError:
                mal("marca", f"el acento {acento} no es un color legible")
            else:
                if c < CONTRASTE_MINIMO:
                    mal("marca", f"el acento da {c:.2f}:1 contra {FONDO_SECCION}; "
                                 f"cae al neutro (mínimo {CONTRASTE_MINIMO})")
                else:
                    bien(f"el acento contrasta {c:.2f}:1 contra {FONDO_SECCION}")
        elif acento:
            mal("marca", f"el acento «{acento}» no tiene forma #RRGGBB")

    if not cfg.get("deviceOverrides"):
        ojo("sin «deviceOverrides»: el generador no está inyectando nuestra tabla de modelos")
    return cfg


def comprobar_canales(cfg: dict) -> None:
    canales = cfg.get("channels") or []
    malos, variantes = [], []
    for ch in canales:
        url = ch.get("url") or ""
        estado, texto = pedir(url, espera=15)
        if estado != 200:
            malos.append(f"{ch.get('id')} → {estado or 'sin respuesta'}")
            continue
        n = texto.count("EXT-X-STREAM-INF")
        if n > 1:
            variantes.append(f"{ch.get('id')} ({n})")
    if malos:
        mal("canales", f"{len(malos)} de {len(canales)} canales no sirven: " + ", ".join(malos))
    else:
        bien(f"los {len(canales)} canales contestan 200")
    # La app no puede impedir el ABR, sólo verlo (`CLAUDE.md` §6). Aquí sí se
    # puede impedir, que es más barato que un aviso en un registro.
    if variantes:
        mal("abr", "masters con varias calidades: " + ", ".join(variantes))
        nota("el generador debe publicar index.m3u8?filter=tracks:v2a1a2")
    else:
        bien("una sola calidad por canal: nada de ABR")

    por_defecto = cfg.get("defaultChannel")
    if por_defecto and por_defecto not in {c.get("id") for c in canales}:
        mal("canales", f"defaultChannel «{por_defecto}» no está en la lista")


def comprobar_epg(cfg: dict) -> None:
    epg = cfg.get("epg") or {}
    url = epg.get("url")
    if not url:
        ojo("el config.json no apunta a ningún EPG: la guía saldrá vacía")
        return
    estado, texto = pedir(url, espera=60)
    if estado != 200:
        mal("epg", f"el EPG → {estado or texto}")
        return
    programas = texto.count("<programme ")
    if not programas:
        mal("epg", "el EPG no trae ni un programa")
        return
    # Un EPG que existe pero no cubre «ahora» es peor que ninguno: la guía
    # sale en blanco y nadie sabe si el fallo es de la app o del dato.
    marcas = sorted(
        t.split('start="', 1)[1][:14]
        for t in texto.split("<programme ")[1:] if 'start="' in t
    )
    ahora = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M%S")
    legible = lambda m: f"{m[0:4]}-{m[4:6]}-{m[6:8]}"
    bien(f"el EPG trae {programas} programas, "
         f"de {legible(marcas[0])} a {legible(marcas[-1])}")
    if not (marcas[0] <= ahora <= marcas[-1]):
        mal("epg", "el EPG no cubre este momento: la guía saldrá en blanco")
    else:
        bien("y cubre este momento")


# ----------------------------------------------------------------------- mando

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", default="xtsrt01.ddns.net",
                   help="el alias público del site (por defecto, el del demo)")
    p.add_argument("--jellyfin-puerto", type=int, default=8096)
    p.add_argument("--canales-puerto", type=int, default=8097)
    p.add_argument("--registro", default="https://authjelly.satmx.net")
    p.add_argument("--estricto", action="store_true",
                   help="los roces también fallan; para usar antes de invitar a nadie")
    a = p.parse_args()

    global ESTRICTO
    ESTRICTO = a.estricto

    base = f"http://{a.host}:{a.jellyfin_puerto}"
    canales = f"http://{a.host}:{a.canales_puerto}"
    registro = a.registro.rstrip("/")

    print(f"Site «{a.host}» · registro {registro} · "
          f"{dt.datetime.now(dt.timezone.utc):%Y-%m-%d %H:%M} UTC")

    if comprobar_jellyfin(base):
        comprobar_cuentas(base)
        comprobar_quickconnect(base)
        comprobar_biblioteca(base)

    comprobar_tls(registro)
    comprobar_resolve(registro, a.host)
    comprobar_selector(registro, base)
    comprobar_sin_enumeracion(registro)

    cfg = comprobar_config(canales)
    if cfg:
        comprobar_canales(cfg)
        comprobar_epg(cfg)

    print()
    if fallos:
        print(f"{ROJO}{len(fallos)} cosa(s) impiden que un probador use la app: "
              f"{', '.join(dict.fromkeys(fallos))}{FIN_COLOR}")
    if roces:
        print(f"{AMARILLO}{len(roces)} roce(s) que gastan la paciencia del probador "
              f"sin cerrarle la puerta: {', '.join(dict.fromkeys(roces))}{FIN_COLOR}")
    if avisos and not fallos and not roces:
        print(f"{AMARILLO}listo, con {len(avisos)} aviso(s){FIN_COLOR}")
    if not fallos and not roces and not avisos:
        print(f"{VERDE}el site está listo para la prueba cerrada{FIN_COLOR}")
    if fallos:
        return 1
    # Antes de mandar doce invitaciones, un roce sí cuenta: cada uno es una
    # llamada de alguien que está haciendo un favor.
    return 1 if (ESTRICTO and roces) else 0


if __name__ == "__main__":
    sys.exit(main())
