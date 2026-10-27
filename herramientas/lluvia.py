#!/usr/bin/env python3
"""Un camino de red degradado entre la caja y el headend, para probar la lluvia.

## Por qué existe

`LIVETV_PLAN.md` cierra M7 pidiendo `tc netem loss 5% delay 100ms 20ms`, un
corte de 5 minutos y un golden sample de lluvia en bucle. Las tres estaban
bloqueadas por la misma razón: **el único sitio donde se podía aplicar netem era
el headend de pruebas, que es compartido y de SOLO LECTURA** (`CLAUDE.md` §9).
Degradarlo ahí rompe el trabajo de otros, y la caja no es nuestra para rootear.

La salida es ponerse **en medio**: este proceso corre en nuestra máquina, sirve
un `config.json` cuyos canales apuntan aquí, y reenvía al headend aplicando el
destrozo que se le pida. El headend no se toca, la caja no se toca, y lo único
que hay que cambiar en la caja es `configUrlOverride`, que ya existe en el menú
de desarrollador justo para esto.

## Por qué no es netem, y en qué es mejor y peor

netem tira paquetes IP; esto tira **peticiones HTTP**. Son cosas distintas y
conviene no confundirlas al escribir lo que se midió:

- netem es más fiel a un enlace malo: fragmenta, reordena y deja a TCP
  retransmitir. Esto no reproduce eso.
- a cambio, esto es **reproducible y queda registrado**: cada decisión se
  imprime con su causa, así que un corte de la app se puede casar con la
  petición exacta que lo provocó. netem no deja ese rastro.

Para lo que M7 quiere saber —si el motor aguanta sin cerrarse, reintenta y se
recupera solo— la capa HTTP es la que importa, porque es donde Media3 y libVLC
ven el fallo. Si algún día hace falta el destrozo a nivel de paquete, la forma
limpia es un `netns` con netem en el veth, **nunca** en el headend.

## Y una distinción que no hay que perder

La lluvia de banda Ku no es una red mala: es la **señal** que llega rota al
headend, y Flussonic publica entonces segmentos corruptos o deja de publicar.
Esa mitad la cubre `herramientas/corromper-ts.py`, que fabrica el golden sample
y se sirve en bucle; aquí no hay un modo para ella a propósito, porque mezclar
las dos en un solo mando haría imposible decir cuál de los dos fallos rompió
algo. Los modos de este archivo —`--perdida`, `--retardo`, `--truncar`,
`--corte`— son el otro fallo, el del tramo IP, que en casa del abonado existe
igual aunque no llueva.

## Uso

    # de pasarela limpia, para comprobar que no estorba
    python3 herramientas/lluvia.py

    # lluvia: una de cada veinte peticiones se cae, y todas llegan tarde
    python3 herramientas/lluvia.py --perdida 5 --retardo 100 --jitter 20

    # corte de 5 minutos, sin reiniciar nada
    curl 'http://127.0.0.1:8099/_lluvia?corte=300'

    # volver a la normalidad
    curl 'http://127.0.0.1:8099/_lluvia?perdida=0&retardo=0&corte=0'

Y en la caja, una sola vez:

    herramientas/lluvia.py --apuntar-caja 192.168.20.132:5555
"""

from __future__ import annotations

import argparse
import random
import shlex
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# --- estado, cambiable en caliente -----------------------------------------


class Tormenta:
	"""Cuánto se destroza ahora mismo. Se lee en cada petición, no al arrancar."""

	def __init__(self) -> None:
		self.perdida = 0.0       # % de peticiones que no se contestan bien
		self.retardo = 0         # ms añadidos a cada respuesta
		self.jitter = 0          # ms de variación uniforme sobre el retardo
		self.truncar = 0.0       # % de segmentos que se cortan a la mitad
		self.modo_perdida = "reset"   # reset | 500 | timeout
		self.corte_hasta = 0.0   # epoch en el que termina el corte total
		self.lock = threading.Lock()

	def en_corte(self) -> bool:
		return time.time() < self.corte_hasta

	def describe(self) -> str:
		trozos = [f"perdida={self.perdida}%", f"retardo={self.retardo}±{self.jitter}ms",
		          f"truncar={self.truncar}%", f"modoPerdida={self.modo_perdida}"]
		if self.en_corte():
			trozos.append(f"CORTE restan={self.corte_hasta - time.time():.0f}s")
		return " ".join(trozos)


TORMENTA = Tormenta()
CONTADOR = {"peticiones": 0, "caidas": 0, "truncadas": 0, "enCorte": 0}


def registra(evento: str, **kv: object) -> None:
	"""Una línea por decisión, con el mismo aire que el log de la app."""
	campos = " ".join(f"{k}={v}" for k, v in kv.items())
	print(f"{time.strftime('%H:%M:%S')} {evento} {campos}", flush=True)


# --- el servidor ------------------------------------------------------------


class Pasarela(BaseHTTPRequestHandler):
	protocol_version = "HTTP/1.1"
	servidor_canales = ""   # http://host:8097
	headend = ""            # http://host:8181
	mi_base = ""            # http://ip:8099

	def log_message(self, *_a: object) -> None:
		pass  # el nuestro es mejor: dice qué se decidió, no solo qué se pidió

	# -- utilidades

	def _responde(self, codigo: int, cuerpo: bytes, tipo: str) -> None:
		self.send_response(codigo)
		self.send_header("Content-Type", tipo)
		self.send_header("Content-Length", str(len(cuerpo)))
		self.send_header("Cache-Control", "no-cache")
		self.end_headers()
		self.wfile.write(cuerpo)

	def _espera_el_retardo(self) -> int:
		with TORMENTA.lock:
			base, jit = TORMENTA.retardo, TORMENTA.jitter
		if base <= 0 and jit <= 0:
			return 0
		ms = max(0, base + random.randint(-jit, jit) if jit else base)
		time.sleep(ms / 1000.0)
		return ms

	# -- rutas

	def do_GET(self) -> None:  # noqa: N802 — lo exige BaseHTTPRequestHandler
		ruta = urllib.parse.urlsplit(self.path)
		if ruta.path == "/_lluvia":
			return self._control(urllib.parse.parse_qs(ruta.query))
		if ruta.path == "/config.json":
			return self._config()
		return self._reenvia()

	def _control(self, q: dict[str, list[str]]) -> None:
		with TORMENTA.lock:
			if "perdida" in q:
				TORMENTA.perdida = float(q["perdida"][0])
			if "retardo" in q:
				TORMENTA.retardo = int(q["retardo"][0])
			if "jitter" in q:
				TORMENTA.jitter = int(q["jitter"][0])
			if "truncar" in q:
				TORMENTA.truncar = float(q["truncar"][0])
			if "modo" in q:
				TORMENTA.modo_perdida = q["modo"][0]
			if "corte" in q:
				s = float(q["corte"][0])
				TORMENTA.corte_hasta = time.time() + s if s > 0 else 0.0
		estado = TORMENTA.describe()
		registra("lluvia.ajustada", estado=estado, **CONTADOR)
		self._responde(200, (estado + "\n" + str(CONTADOR) + "\n").encode(), "text/plain; charset=utf-8")

	def _config(self) -> None:
		"""El `config.json` del site, con cada canal apuntando aquí.

		Se reescribe en vez de copiarse para que **no haya una segunda lista
		que se quede atrás**: lo que la caja ve por aquí es el lineup real del
		site, canal a canal, con el destino cambiado y nada más.
		"""
		import json

		try:
			with urllib.request.urlopen(f"{self.servidor_canales}/config.json", timeout=10) as r:
				cfg = json.load(r)
		except OSError as e:
			registra("config.noSePudo", error=e)
			return self._responde(502, b"no se pudo leer el config.json del site\n", "text/plain")

		cambiados = 0
		for canal in cfg.get("channels", []):
			url = canal.get("url")
			if not url or not url.startswith(self.headend):
				continue
			canal["url"] = self.mi_base + url[len(self.headend):]
			cambiados += 1
		cuerpo = json.dumps(cfg, ensure_ascii=False).encode()
		registra("config.reescrito", canales=len(cfg.get("channels", [])), redirigidos=cambiados)
		self._responde(200, cuerpo, "application/json; charset=utf-8")

	def _reenvia(self) -> None:
		CONTADOR["peticiones"] += 1
		es_playlist = self.path.split("?")[0].endswith(".m3u8")

		if TORMENTA.en_corte():
			CONTADOR["enCorte"] += 1
			registra("corte.tragada", ruta=self.path[:70])
			return self._cae("corte")

		ms = self._espera_el_retardo()

		with TORMENTA.lock:
			perdida, truncar, modo = TORMENTA.perdida, TORMENTA.truncar, TORMENTA.modo_perdida

		# El playlist nunca se pierde a propósito: si se pierde, el cliente no
		# tiene de dónde sacar el siguiente segmento y lo que se mide deja de
		# ser «aguanta la lluvia» para ser «aguanta sin lista», que es el corte
		# limpio que ya está probado (pendiente 69).
		if not es_playlist and perdida > 0 and random.random() * 100 < perdida:
			CONTADOR["caidas"] += 1
			registra("perdida.aplicada", ruta=self.path.split("/")[-1][:50], modo=modo, retardoMs=ms)
			return self._cae(modo)

		try:
			req = urllib.request.Request(self.headend + self.path,
			                             headers={"User-Agent": self.headers.get("User-Agent", "lluvia")})
			with urllib.request.urlopen(req, timeout=20) as r:
				datos = r.read()
				tipo = r.headers.get("Content-Type", "application/octet-stream")
		except Exception as e:  # noqa: BLE001 — cualquier fallo del origen se reenvía como 502
			registra("origen.falla", ruta=self.path[:70], error=type(e).__name__)
			return self._cae("500")

		if not es_playlist and truncar > 0 and random.random() * 100 < truncar:
			CONTADOR["truncadas"] += 1
			corte = len(datos) // 2
			registra("truncado.aplicado", ruta=self.path.split("/")[-1][:50],
			         bytesDe=len(datos), bytesA=corte)
			# Se anuncia el tamaño entero y se manda la mitad: el cliente ve un
			# segmento incompleto, que es lo que deja un camino con pérdidas.
			self.send_response(200)
			self.send_header("Content-Type", tipo)
			self.send_header("Content-Length", str(len(datos)))
			self.end_headers()
			self.wfile.write(datos[:corte])
			self.close_connection = True
			return

		self._responde(200, datos, tipo)

	def _cae(self, modo: str) -> None:
		if modo == "500":
			try:
				self._responde(500, b"lluvia\n", "text/plain")
			except OSError:
				pass
		elif modo == "timeout":
			time.sleep(35)  # por encima del readTimeout de 30 s del motor
			self.close_connection = True
		else:  # reset
			try:
				self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER,
				                           b"\x01\x00\x00\x00\x00\x00\x00\x00")
				self.connection.close()
			except OSError:
				pass
		self.close_connection = True


# --- apuntar la caja --------------------------------------------------------


def ip_hacia(destino: str) -> str:
	"""La IP de esta máquina por la que la caja nos ve."""
	s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	try:
		s.connect((destino, 9))
		return s.getsockname()[0]
	finally:
		s.close()


def apuntar_caja(serie: str, base: str) -> None:
	"""Deja `configUrlOverride` puesto en la caja, y dice cómo deshacerlo."""
	destino = f"{base}/config.json"
	pref = "/data/data/mx.kataliziador.palomera.debug/shared_prefs/livetv.dev.xml"
	print(f"apuntando {serie} a {destino}")
	leer = subprocess.run(["adb", "-s", serie, "shell", "run-as",
	                       "mx.kataliziador.palomera.debug", "cat", pref],
	                      capture_output=True, text=True)
	xml = leer.stdout if leer.returncode == 0 and "<map" in leer.stdout else \
		'<?xml version="1.0" encoding="utf-8"?>\n<map />\n'
	linea = f'    <string name="configUrlOverride">{destino}</string>\n'
	import re
	xml = re.sub(r'\s*<string name="configUrlOverride">.*?</string>\n?', "\n", xml)
	xml = xml.replace("<map />", "<map>\n</map>").replace("</map>", linea + "</map>")
	# El entrecomillado no es cosmético. `adb shell` junta sus argumentos y
	# **el shell de la caja vuelve a interpretarlos**, así que un `>` sin
	# proteger lo ejecuta el shell de fuera —uid `shell`, 2000— antes de que
	# `run-as` cambie de identidad: el redirector escribe el que no tiene
	# permiso y sale «Permission denied» aunque `run-as` funcione. Pasando la
	# orden entera como una sola palabra, el redirector lo hace el `sh` de
	# dentro, que ya corre como la app.
	subprocess.run(["adb", "-s", serie, "shell", "run-as", "mx.kataliziador.palomera.debug",
	                "sh", "-c", shlex.quote(f"cat > {pref}")],
	               input=xml, text=True, check=True)
	subprocess.run(["adb", "-s", serie, "shell", "am", "force-stop",
	                "mx.kataliziador.palomera.debug"], check=True)
	print("puesto. para quitarlo cuando se termine:")
	# Mismo motivo que arriba: la orden va entre comillas simples para que
	# llegue entera al `sh` de dentro.
	print(f"  adb -s {serie} shell run-as mx.kataliziador.palomera.debug "
	      f"\"sh -c 'sed -i /configUrlOverride/d {pref}'\"")
	print(f"  adb -s {serie} shell am force-stop mx.kataliziador.palomera.debug")


def main() -> int:
	p = argparse.ArgumentParser(description=__doc__,
	                            formatter_class=argparse.RawDescriptionHelpFormatter)
	p.add_argument("--puerto", type=int, default=8099)
	p.add_argument("--headend", default="http://192.168.11.250:8181")
	p.add_argument("--site", default="http://192.168.20.249:8097",
	               help="el servidor de canales del que se copia el lineup")
	p.add_argument("--perdida", type=float, default=0.0, help="%% de segmentos que no llegan")
	p.add_argument("--retardo", type=int, default=0, help="ms añadidos a cada respuesta")
	p.add_argument("--jitter", type=int, default=0, help="ms de variación del retardo")
	p.add_argument("--truncar", type=float, default=0.0, help="%% de segmentos que llegan a medias")
	p.add_argument("--modo", default="reset", choices=("reset", "500", "timeout"))
	p.add_argument("--apuntar-caja", metavar="SERIE",
	               help="deja configUrlOverride puesto en esa caja y sale")
	a = p.parse_args()

	mi_ip = ip_hacia(a.headend.split("//")[1].split(":")[0])
	base = f"http://{mi_ip}:{a.puerto}"

	if a.apuntar_caja:
		apuntar_caja(a.apuntar_caja, base)
		return 0

	TORMENTA.perdida, TORMENTA.retardo = a.perdida, a.retardo
	TORMENTA.jitter, TORMENTA.truncar = a.jitter, a.truncar
	TORMENTA.modo_perdida = a.modo

	Pasarela.servidor_canales = a.site
	Pasarela.headend = a.headend
	Pasarela.mi_base = base

	srv = ThreadingHTTPServer(("0.0.0.0", a.puerto), Pasarela)
	srv.daemon_threads = True
	registra("lluvia.arranca", base=base, headend=a.headend, site=a.site)
	registra("lluvia.estado", estado=TORMENTA.describe())
	print(f"\n  config para la caja:  {base}/config.json")
	print(f"  control:              curl '{base}/_lluvia?perdida=5&retardo=100&jitter=20'\n")
	try:
		srv.serve_forever()
	except KeyboardInterrupt:
		registra("lluvia.para", **CONTADOR)
	return 0


if __name__ == "__main__":
	sys.exit(main())
