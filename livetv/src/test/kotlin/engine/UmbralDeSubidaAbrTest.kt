package org.jellyfin.livetv.engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.livetv.LiveTuning

/**
 * El umbral que Media3 aplica para **subir** de calidad en directo.
 *
 * `LiveTuning.umbralSubidaMs` reproduce
 * `AdaptiveTrackSelection.minDurationForQualityIncreaseUs` (Media3 1.8.0, con
 * `availableDurationUs` distinto de `TIME_UNSET`, que es el caso de un
 * directo): descuenta un segmento de lo que queda hasta el borde, toma esa
 * fracción, y acota el resultado por el mínimo de VOD.
 *
 * **Existe porque el número viaja al diagnóstico.** Si la fórmula de aquí y la
 * de Media3 dejaran de coincidir, `umbralSubirS` en el latido sería mentira, y
 * una mentira en el instrumento es peor que no tener instrumento. Esta clase es
 * lo que lo nota: si alguien cambia las constantes, falla aquí y no delante de
 * una tele.
 */
class UmbralDeSubidaAbrTest : StringSpec({

	// Lo medido el 02-nov en la Skyworth con `estrellasabr` por internet.
	val HASTA_EL_DIRECTO = 11_800L
	val SEGMENTO = 2_800L

	"con los valores de fábrica el umbral queda en 6,75 s, encima del valle del buffer" {
		// El buffer medido iba en sierra de 5,0 a 9,0 s: cruza esta línea en
		// los dos sentidos, que es exactamente el defecto.
		LiveTuning.umbralSubidaMs(
			HASTA_EL_DIRECTO, SEGMENTO, LiveTuning.ABR_FRACCION_AL_DIRECTO_DE_FABRICA,
		) shouldBe 6_750L
	}

	"afinado, el umbral baja a 3,15 s y el valle del buffer (5,0 s) lo supera" {
		val umbral = LiveTuning.umbralSubidaMs(
			HASTA_EL_DIRECTO, SEGMENTO, LiveTuning.ABR_FRACCION_AL_DIRECTO_PARA_SUBIR,
		)
		umbral shouldBe 3_150L
		(umbral < 5_000L) shouldBe true
	}

	"la cota del mínimo de VOD manda cuando la ventana es grande" {
		// Con 60 s hasta el directo, 0,75 daría 42,9 s: Media3 lo corta en
		// 10 000, y el techo del diagnóstico tiene que cortarlo igual.
		LiveTuning.umbralSubidaMs(60_000L, 2_800L, 0.75f) shouldBe
			LiveTuning.ABR_MIN_BUFFER_PARA_SUBIR_MS.toLong()
	}

	"un segmento más largo que lo que queda hasta el directo no da un umbral negativo" {
		LiveTuning.umbralSubidaMs(2_000L, 6_000L, 0.75f) shouldBe 0L
	}

	"sin dato de directo se cae al mínimo de VOD, no a cero" {
		// Cero o negativo es «no lo sé». Devolver 0 diría «siempre se puede
		// subir», que es la respuesta optimista y la equivocada.
		LiveTuning.umbralSubidaMs(0L, 2_800L, 0.35f) shouldBe
			LiveTuning.ABR_MIN_BUFFER_PARA_SUBIR_MS.toLong()
	}

	"el techo del latido se calcula sin segmento y por eso es techo, no umbral" {
		val techo = LiveTuning.umbralSubidaMs(HASTA_EL_DIRECTO, 0L, LiveTuning.ABR_FRACCION_AL_DIRECTO_PARA_SUBIR)
		val real = LiveTuning.umbralSubidaMs(HASTA_EL_DIRECTO, SEGMENTO, LiveTuning.ABR_FRACCION_AL_DIRECTO_PARA_SUBIR)
		(techo >= real) shouldBe true
	}
})
