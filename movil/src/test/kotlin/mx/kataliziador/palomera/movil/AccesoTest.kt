package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.acceso.Entrada
import mx.kataliziador.palomera.movil.acceso.leerEntrada
import mx.kataliziador.palomera.movil.acceso.leerCodigoDeActivacion
import mx.kataliziador.palomera.movil.acceso.CodigoDeActivacion

/**
 * Qué pasa cuando el abonado intenta entrar, y **los cuatro casos son
 * distintos en pantalla**.
 *
 * ## Por qué esto es lo primero que se escribe
 *
 * Es la misma tabla que la caja aprendió a golpes el 28-oct (`CLAUDE.md`
 * §4.7). El Jellyfin del ISP distingue tres cosas y una app ingenua las trata
 * como una sola:
 *
 * | del servidor | qué significa de verdad | qué hace el abonado |
 * |---|---|---|
 * | **401** | la clave no es ésa | la reescribe |
 * | **403** | el contrato está suspendido | **llama a pagar, no a pedir un código** |
 * | **no contesta** | la casa está sin internet, o el site caído | espera |
 *
 * Confundir el 403 con el 401 manda a un abonado suspendido a pedir un código
 * nuevo, y con él **entra otra vez**. Confundir «no contesta» con «te echamos»
 * apaga la tele de alguien que está al día porque su router tardó en arrancar.
 *
 * Y es asimétrico: equivocarse hacia «no entras» molesta a quien paga;
 * equivocarse hacia «entras» regala el servicio. Por eso ninguna rama es el
 * `else` de otra.
 */
class AccesoTest : FunSpec({

	test("con usuario y clave buenos, se entra") {
		val json = """
			{"User":{"Id":"abc123","Name":"probador"},
			 "AccessToken":"tok-999","ServerId":"srv-1"}
		""".trimIndent()

		val e = leerEntrada(200, json) as Entrada.Adentro

		e.token shouldBe "tok-999"
		e.usuarioId shouldBe "abc123"
		e.nombre shouldBe "probador"
		// El id del servidor es la clave con la que se guarda su lista de
		// canales, así que se recoge aquí o se pierde.
		e.servidorId shouldBe "srv-1"
	}

	test("401 es la clave equivocada, y se puede reintentar") {
		leerEntrada(401, "") shouldBe Entrada.ClaveEquivocada
	}

	test("403 es el servicio suspendido, y NO manda a pedir un codigo") {
		// Éste es el caro. Un abonado suspendido al que se le enseña la
		// pantalla del código pide uno nuevo y entra: el proveedor deja de
		// cobrar y nadie se entera.
		leerEntrada(403, "") shouldBe Entrada.Suspendido
	}

	test("si no contesta, no se le cierra la puerta a nadie") {
		// `codigo = null` es «ni siquiera hubo respuesta». No puede caer en el
		// mismo sitio que un 401: una caja encendida antes que su router no
		// está dada de baja.
		leerEntrada(null, null) shouldBe Entrada.NoContesta
		leerEntrada(500, "") shouldBe Entrada.NoContesta
		leerEntrada(503, "") shouldBe Entrada.NoContesta
	}

	test("un 200 sin token no es entrar") {
		// Pasa con un proxy mal puesto que devuelve una página de cortesía con
		// 200. Tratarlo como éxito deja la app dentro sin token y el fallo
		// aparece tres pantallas después, sin relación aparente.
		leerEntrada(200, """{"User":{"Id":"a"}}""") shouldBe Entrada.NoContesta
		leerEntrada(200, "<html>vaya</html>") shouldBe Entrada.NoContesta
	}

	test("el codigo de activacion se lee con su secreto") {
		val json = """{"Authenticated":false,"Secret":"s3cr","Code":"472913"}"""

		leerCodigoDeActivacion(json) shouldBe CodigoDeActivacion("472913", "s3cr", false)
	}

	test("un codigo sin secreto no sirve de nada y no se entrega a medias") {
		// Sin secreto no hay forma de preguntar si ya lo autorizaron, así que
		// la pantalla enseñaría un número que nunca va a avanzar.
		leerCodigoDeActivacion("""{"Code":"472913"}""") shouldBe null
		leerCodigoDeActivacion("no es json") shouldBe null
	}
})
