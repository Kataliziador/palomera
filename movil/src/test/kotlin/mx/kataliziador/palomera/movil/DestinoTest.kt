package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.destinoDeCanales

/**
 * A qué servidor de canales mira este teléfono, y bajo qué clave lo guarda.
 *
 * ## Por qué esto se escribe con pruebas y en la caja no las tiene
 *
 * Es la misma cascada que `LiveTvCatalog.destino()`, que vive en `app/` y por
 * eso **no tiene ni una prueba**. Su fallo no se ve al ocurrir: se manifiesta
 * como **«este ISP ve el lineup de otro»**, semanas después y en casa de un
 * abonado. Como función pura son cuatro entradas y dos salidas, así que aquí
 * se escribe al revés.
 *
 * ## Y la clave de caché es la mitad del asunto
 *
 * Se indexa por el **identificador** del servidor y no por su dirección
 * (`CLAUDE.md` §6): cambiar de dominio no debe tirar la caché, y cambiar de
 * site no debe heredar su lineup.
 */
class DestinoTest : FunSpec({

	test("sin nada configurado, se deriva del host de Jellyfin") {
		// Es el caso normal: el abonado escribió una dirección y el servicio
		// de canales vive en la misma máquina, en su puerto.
		val (url, clave) = destinoDeCanales(
			servidor = "http://192.168.1.10:8096",
			idServidor = "srv-1",
			delRegistro = null,
			manual = null,
			deFabrica = "",
		)

		url shouldBe "http://192.168.1.10:8097/config.json"
		clave shouldBe "srv-1"
	}

	test("el registro manda sobre la convencion") {
		// Es lo que permite mover el servicio de canales de un site editando
		// una fila en vez de publicando una versión (`REGISTRY_SCHEMA.md` §5).
		val (url, _) = destinoDeCanales(
			servidor = "http://192.168.1.10:8096",
			idServidor = "srv-1",
			delRegistro = "https://canales.miproveedor.mx/config.json",
			manual = null,
			deFabrica = "",
		)

		url shouldBe "https://canales.miproveedor.mx/config.json"
	}

	test("y el ajuste manual manda sobre todo") {
		val (url, _) = destinoDeCanales(
			servidor = "http://192.168.1.10:8096",
			idServidor = "srv-1",
			delRegistro = "https://canales.miproveedor.mx/config.json",
			manual = "http://portatil:9000/config.json",
			deFabrica = "",
		)

		url shouldBe "http://portatil:9000/config.json"
	}

	test("una lista de pruebas NO se guarda bajo la clave del site real") {
		// Éste es el caro y es el que la caja no prueba. El ajuste manual
		// cambia la **fuente** sin cambiar el servidor: sin esta regla, el
		// técnico que apunta un teléfono a su portátil lo deja con el lineup
		// equivocado guardado como si fuera el del ISP, y se va.
		val (_, clave) = destinoDeCanales(
			servidor = "http://192.168.1.10:8096",
			idServidor = "srv-1",
			delRegistro = null,
			manual = "http://portatil:9000/config.json",
			deFabrica = "",
		)

		clave shouldBe "manual"
	}

	test("sin servidor no se inventa una clave que colisione") {
		// Dos teléfonos sin sesión no pueden compartir archivo de caché con
		// un site real. «sin-servidor» es un nombre que ningún id de Jellyfin
		// puede tener.
		val (url, clave) = destinoDeCanales(
			servidor = null,
			idServidor = null,
			delRegistro = null,
			manual = null,
			deFabrica = "",
		)

		url shouldBe null
		clave shouldBe "sin-servidor"
	}

	test("el esquema del site se conserva: un site con TLS no se degrada") {
		val (url, _) = destinoDeCanales(
			servidor = "https://tv.miproveedor.mx/jellyfin",
			idServidor = "srv-2",
			delRegistro = null,
			manual = null,
			deFabrica = "",
		)

		url shouldBe "https://tv.miproveedor.mx:8097/config.json"
	}
})
