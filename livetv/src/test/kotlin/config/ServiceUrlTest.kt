package org.jellyfin.livetv.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * La dirección de Jellyfin la escribe una persona, así que llega como llegue.
 *
 * Estas pruebas existen porque un fallo aquí no se ve: la app se cae a la
 * caché o a la lista de arranque y **parece que funciona**, enseñando los
 * canales de prueba en vez del lineup del ISP. Es el mismo modo de fallo
 * silencioso que ya nos costó horas con el `config.json` congelado.
 */
class ServiceUrlTest : StringSpec({

	fun url(direccion: String?) = ServiceUrl.configDesdeServidor(direccion, 8097, "/config.json")

	"una dirección con puerto cambia de puerto" {
		url("http://192.168.20.249:8096") shouldBe "http://192.168.20.249:8097/config.json"
	}

	"una dirección sin puerto también" {
		url("http://192.168.20.249") shouldBe "http://192.168.20.249:8097/config.json"
	}

	"la barra final no ensucia la URL" {
		url("http://192.168.20.249:8096/") shouldBe "http://192.168.20.249:8097/config.json"
	}

	"el esquema se conserva: un site con TLS no se degrada a http" {
		url("https://tv.isp.mx:8920") shouldBe "https://tv.isp.mx:8097/config.json"
	}

	"Jellyfin bajo una ruta sigue siendo la misma máquina" {
		// Es otro proceso en el mismo host, así que la ruta de Jellyfin no
		// tiene nada que ver con la del servicio de canales.
		url("https://tv.isp.mx/jellyfin") shouldBe "https://tv.isp.mx:8097/config.json"
	}

	"la consulta y el ancla se descartan" {
		url("http://host:8096/web/index.html?foo=1#x") shouldBe "http://host:8097/config.json"
	}

	"las credenciales no se arrastran a la URL del servicio" {
		url("http://usuario:clave@host:8096") shouldBe "http://host:8097/config.json"
	}

	"IPv6 literal conserva los corchetes" {
		url("http://[fd00::1]:8096") shouldBe "http://[fd00::1]:8097/config.json"
	}

	"sin esquema no se adivina" {
		// Asumir http dejaría la sección muerta en un site con TLS, y asumir
		// https la dejaría muerta en todos los demás.
		url("192.168.20.249:8096") shouldBe null
	}

	"una dirección vacía o nula no es un error, es ausencia" {
		url(null) shouldBe null
		url("") shouldBe null
		url("   ") shouldBe null
	}

	"una dirección sin host se rechaza" {
		url("http://") shouldBe null
	}

	"el espacio alrededor no estorba" {
		url("  http://host:8096  ") shouldBe "http://host:8097/config.json"
	}
})
