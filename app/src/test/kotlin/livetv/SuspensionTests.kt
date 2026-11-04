package org.jellyfin.androidtv.livetv

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.model.SessionFailure
import org.jellyfin.androidtv.ui.livetv.Decision
import org.jellyfin.androidtv.ui.livetv.decidir

/**
 * Qué respuesta del servidor del ISP cierra la televisión, y cuál no.
 *
 * Es el pendiente 84, y lo que se prueba aquí es **la tabla de decisiones**,
 * no la petición. La petición es trivial; lo que no puede cambiar por
 * descuido es que un servidor que tarda en responder apague la televisión de
 * un abonado que está al día.
 *
 * Los códigos salen de `SessionFailure`, medidos el 28-oct contra Jellyfin
 * 10.11.11 y 12.0.0.
 */
class SuspensionTests : FunSpec({
	test("sólo un 403 cierra la sección") {
		decidir(SessionFailure.SUSPENDED) shouldBe Decision.CERRAR
	}

	test("que el servidor no conteste no cierra nada: puede estar reiniciándose") {
		decidir(SessionFailure.UNREACHABLE) shouldBe Decision.REINTENTAR_PRONTO
	}

	test("una credencial inválida tampoco cierra con el texto de suspendido") {
		// 401 quiere decir «hay que activar la caja otra vez», no «paga». De
		// eso se encarga la puerta de la sesión al arrancar; enseñar aquí
		// «Servicio suspendido» mandaría al abonado a pagar lo que ya pagó.
		decidir(SessionFailure.INVALID) shouldBe Decision.DEJARLO_COMO_ESTA
	}

	test("si el servidor contesta bien, la sección se abre") {
		decidir(null) shouldBe Decision.ABRIR
	}

	test("el contrato vivo reabre la sección sola, sin reiniciar la caja") {
		// Es la otra mitad de la promesa del texto —«en cuanto tu servicio se
		// reanude, esta televisión entrará sola»—: si esto dejara de devolver
		// ABRIR, el abonado que paga se quedaría cerrado hasta el próximo
		// arranque y la pantalla le estaría mintiendo.
		decidir(null) shouldBe Decision.ABRIR
	}

	test("ninguna respuesta distinta de 403 cierra") {
		val cierran = SessionFailure.entries.filter { decidir(it) == Decision.CERRAR }
		cierran shouldBe listOf(SessionFailure.SUSPENDED)
	}
})
