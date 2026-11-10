package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.aTicks
import mx.kataliziador.palomera.movil.catalogo.seReporta
import mx.kataliziador.palomera.movil.catalogo.tocaLatido

/**
 * Lo que la app le cuenta a Jellyfin mientras el abonado ve algo.
 *
 * ## Por qué esto existe, y es un defecto medido
 *
 * **Medido el 10-nov**: con una película reproduciéndose cinco minutos en el
 * teléfono, `GET /Sessions` del site de los probadores devolvía dos sesiones y
 * **ninguna era el teléfono**. La app reproducía y el servidor no se enteraba.
 *
 * Tres cosas se rompen con eso, y la primera es de producto:
 *
 * 1. **«Seguir viendo» no avanza.** Es el primer carril del inicio y se
 *    alimenta de `PlaybackPositionTicks`. Quien ve media película en el móvil
 *    la encuentra sin empezar — en el móvil **y en la caja**.
 * 2. **El proveedor no ve la sesión** en el panel de abonados.
 * 3. **No cuenta contra `MaxActiveSessions`**, así que la política del plan no
 *    se aplica al teléfono.
 */
class LatidoTest : FunSpec({

	test("un milisegundo son diez mil ticks") {
		// El factor es lo único de todo esto que falla en silencio: con uno
		// equivocado «Seguir viendo» no se queda vacío, se queda **mintiendo**,
		// y nadie mira un número que parece plausible.
		aTicks(0) shouldBe 0L
		aTicks(1) shouldBe 10_000L
		aTicks(60_000) shouldBe 600_000_000L // un minuto, el mismo que lee Ficha.kt
	}

	test("el latido va cada diez segundos") {
		tocaLatido(ultimoMs = 0, ahoraMs = 9_999, pausaAnterior = false, pausaAhora = false) shouldBe false
		tocaLatido(ultimoMs = 0, ahoraMs = 10_000, pausaAnterior = false, pausaAhora = false) shouldBe true
	}

	test("pero una pausa se avisa en el momento, sin esperar al latido") {
		// Si no, el panel del proveedor enseña «reproduciendo» hasta diez
		// segundos después de que el abonado pulsara pausa. Es el dato que el
		// operador está mirando mientras habla por teléfono con él.
		tocaLatido(ultimoMs = 0, ahoraMs = 200, pausaAnterior = false, pausaAhora = true) shouldBe true
		tocaLatido(ultimoMs = 0, ahoraMs = 200, pausaAnterior = true, pausaAhora = false) shouldBe true
	}

	test("un canal en vivo no se reporta") {
		// Un canal no es un título de la biblioteca de Jellyfin: no tiene
		// posición que guardar, y meterlo en el historial ensucia «Seguir
		// viendo» con algo que nadie puede reanudar.
		seReporta(enDirecto = true, itemId = "abc") shouldBe false
		seReporta(enDirecto = false, itemId = "abc") shouldBe true
	}

	test("sin item no hay nada que reportar") {
		seReporta(enDirecto = false, itemId = null) shouldBe false
		seReporta(enDirecto = false, itemId = "") shouldBe false
	}
})
