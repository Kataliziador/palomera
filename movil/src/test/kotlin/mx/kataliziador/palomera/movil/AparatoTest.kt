package mx.kataliziador.palomera.movil

import android.content.res.Configuration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.arranque.Aparato
import mx.kataliziador.palomera.movil.arranque.decidirAparato

/**
 * Qué interfaz enseña la app: la del dedo o la del mando.
 *
 * ## Por qué no se reusa `isTvDevice()` de upstream
 *
 * La app de TV ya trae una detección en `util/ContextExtensions.kt:27`, y
 * **está bien para lo que se escribió**: enseñar un aviso de «esto no parece
 * una tele». Ahí equivocarse hacia «es TV» no cuesta nada.
 *
 * Para decidir la interfaz entera el sesgo está al revés, y la regla de
 * upstream tiene dos cosas que no sirven aquí:
 *
 * - **cuenta `UI_MODE_TYPE_UNDEFINED` como televisión.** Un aparato que no
 *   sepa decir qué es se toma por tele, y acaba con una interfaz de cruceta
 *   sobre una pantalla táctil, que es inservible;
 * - une sus tres señales con `or`, así que basta una para decidir.
 *
 * **La regla de aquí se equivoca hacia el teléfono**, y el motivo es
 * asimétrico, no estético: una tele de verdad **casi siempre se declara bien**
 * —`UI_MODE_TYPE_TELEVISION` o `leanback`, que es como funciona Android TV y
 * lo que Play exige—, mientras que el aparato raro que no se declara es casi
 * siempre un teléfono. Dudar hacia el teléfono arregla el caso realista y no
 * cuesta nada en el otro.
 */
class AparatoTest : FunSpec({

	val TV = Configuration.UI_MODE_TYPE_TELEVISION
	val NORMAL = Configuration.UI_MODE_TYPE_NORMAL
	val SIN_DECLARAR = Configuration.UI_MODE_TYPE_UNDEFINED

	test("una caja de Android TV es televisor por las tres señales a la vez") {
		decidirAparato(
			modoDeInterfaz = TV,
			declaraLeanback = true,
			tienePantallaTactil = false,
			forzadoPorAjustes = null,
		) shouldBe Aparato.TELEVISOR
	}

	test("un telefono es telefono") {
		decidirAparato(
			modoDeInterfaz = NORMAL,
			declaraLeanback = false,
			tienePantallaTactil = true,
			forzadoPorAjustes = null,
		) shouldBe Aparato.TELEFONO
	}

	test("una tablet tambien, que es el mismo caso con otra pantalla") {
		decidirAparato(
			modoDeInterfaz = NORMAL,
			declaraLeanback = false,
			tienePantallaTactil = true,
			forzadoPorAjustes = null,
		) shouldBe Aparato.TELEFONO
	}

	test("un aparato que no declara su modo pero tiene pantalla tactil es TELEFONO") {
		// **Éste es el caso que upstream resuelve al revés**, y el que hace
		// falta cambiar para colgarle la app entera a esta decisión.
		decidirAparato(
			modoDeInterfaz = SIN_DECLARAR,
			declaraLeanback = false,
			tienePantallaTactil = true,
			forzadoPorAjustes = null,
		) shouldBe Aparato.TELEFONO
	}

	test("sin pantalla tactil es televisor aunque no declare nada: no hay nada que tocar") {
		decidirAparato(
			modoDeInterfaz = SIN_DECLARAR,
			declaraLeanback = false,
			tienePantallaTactil = false,
			forzadoPorAjustes = null,
		) shouldBe Aparato.TELEVISOR
	}

	test("leanback basta, aunque el modo diga otra cosa") {
		// Una caja que arranca en modo normal pero trae el lanzador de TV.
		decidirAparato(
			modoDeInterfaz = NORMAL,
			declaraLeanback = true,
			tienePantallaTactil = false,
			forzadoPorAjustes = null,
		) shouldBe Aparato.TELEVISOR
	}

	test("el interruptor de Ajustes manda sobre todo, en los dos sentidos") {
		// Es la palanca para el aparato que miente, y tiene que poder forzar
		// las dos respuestas: si sólo pudiera forzar una, la mitad de los
		// aparatos raros se quedaría sin arreglo.
		decidirAparato(
			modoDeInterfaz = TV,
			declaraLeanback = true,
			tienePantallaTactil = false,
			forzadoPorAjustes = Aparato.TELEFONO,
		) shouldBe Aparato.TELEFONO

		decidirAparato(
			modoDeInterfaz = NORMAL,
			declaraLeanback = false,
			tienePantallaTactil = true,
			forzadoPorAjustes = Aparato.TELEVISOR,
		) shouldBe Aparato.TELEVISOR
	}

	test("un telefono con pantalla tactil nunca sale televisor por accidente") {
		// La combinación completa de lo que puede declarar un teléfono: en
		// ninguna de ellas debe salir la interfaz de cruceta.
		for (modo in listOf(NORMAL, SIN_DECLARAR, Configuration.UI_MODE_TYPE_DESK)) {
			decidirAparato(
				modoDeInterfaz = modo,
				declaraLeanback = false,
				tienePantallaTactil = true,
				forzadoPorAjustes = null,
			) shouldBe Aparato.TELEFONO
		}
	}
})
