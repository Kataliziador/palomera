package org.jellyfin.livetv.engine

import androidx.media3.common.C
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * La vuelta al borde, probada contra los nueve saltos que la midieron.
 *
 * Los números son del volcado del 10-oct (`mileniosd`, 203 min, tres ráfagas de
 * cortes con tres saltos cada una). Importan porque la decisión estuvo cuatro
 * líneas dentro de `onTimelineChanged` y para juzgarla hubo que leer una noche
 * entera de log dos veces, con dos conclusiones equivocadas de camino: los
 * pendientes 70 y 71.
 *
 * El objetivo pedido en esa sesión era 14 000 ms, y de ahí salen los destinos.
 */
class VueltaAlBordeTest : StringSpec({

	val OBJETIVO = 14_000L

	"el primer salto de cada ráfaga va hacia atrás, y eso es bueno" {
		// Offset de 72,8 s con la posición a 6,4 s del final de la ventana. El
		// offset está inflado porque la lista del origen no avanza; el destino
		// calculado queda 7,6 s DETRÁS de donde ya está el reproductor.
		//
		// Medido el 11-oct: **ese salto es la cura**, no el desperdicio. Lleva
		// la posición 9 s más adentro del buffer, que es lo que saca al
		// reproductor del bucle de inanición del 75. Declinarlo costó 12 cortes
		// en 33 s. La clasificación no cambia —el salto efectivamente no
		// adelanta— pero quien la lee ya no debe entenderla como «no sirve»; de
		// fábrica se salta igual. Pendiente 73, cerrado y refutado.
		val d = VueltaAlBorde.decidir(
			offsetRealMs = 72_837,
			ventanaMs = 33_267,
			posMs = 26_843,
			objetivoMs = OBJETIVO,
		)
		d shouldBe VueltaAlBorde.Decision.NoAdelanta(destinoMs = 19_267, gananciaMs = -7_576)
	}

	"el segundo, desde fuera de la ventana, sí: adelanta un minuto" {
		// Posición negativa: la ventana ya había dejado atrás al reproductor.
		// Medido, este salto dejó el directo en 19,3 s.
		val d = VueltaAlBorde.decidir(80_780, 33_600, -41_844, OBJETIVO)
		d shouldBe VueltaAlBorde.Decision.Salta(destinoMs = 19_600, gananciaMs = 61_444)
	}

	"y el tercero, desde el principio de la ventana, también" {
		// Dejó el directo en 18,5 s.
		val d = VueltaAlBorde.decidir(37_821, 33_600, 326, OBJETIVO)
		d shouldBe VueltaAlBorde.Decision.Salta(destinoMs = 19_600, gananciaMs = 19_274)
	}

	"una sesión sana no dispara nada, y por los pelos" {
		// Los 6 078 latidos de la sesión dan un directo de 19,0 a 19,5 s sobre
		// una ventana de 33,6: el límite está en 16,8 y el margen mínimo son 3,
		// o sea que dispararía a partir de 19,8. **Son 300 ms.** Está apuntado
		// como pendiente 72 y es la razón de que esta prueba exista: si alguien
		// toca la fracción o el margen, aquí se ve a quién rompe.
		VueltaAlBorde.decidir(19_000, 33_600, 19_000, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoHaceFalta
		VueltaAlBorde.decidir(19_500, 33_600, 19_500, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoHaceFalta
		// Un pelo más y sí dispara — y entonces la ganancia es de 100 ms, así
		// que la guarda lo para igual.
		VueltaAlBorde.decidir(19_900, 33_600, 19_500, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoAdelanta(destinoMs = 19_600, gananciaMs = 100)
	}

	"sin posición se salta, como antes: un instrumento ciego no decide" {
		val d = VueltaAlBorde.decidir(72_837, 33_267, C.TIME_UNSET, OBJETIVO)
		d shouldBe VueltaAlBorde.Decision.Salta(destinoMs = 19_267, gananciaMs = C.TIME_UNSET)
	}

	"sin ventana no se decide nada" {
		VueltaAlBorde.decidir(72_837, C.TIME_UNSET, 26_843, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoHaceFalta
		VueltaAlBorde.decidir(72_837, 0, 26_843, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoHaceFalta
	}

	"sin offset tampoco" {
		VueltaAlBorde.decidir(C.TIME_UNSET, 33_600, 19_000, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoHaceFalta
	}

	"ganar menos de un segmento no vale el corte que cuesta" {
		// Ventana de 40 s, posición a 24: el salto adelantaría 2 s. Un rebuffer
		// para ganar menos de lo que tira el propio rebuffer.
		VueltaAlBorde.decidir(30_000, 40_000, 24_000, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.NoAdelanta(destinoMs = 26_000, gananciaMs = 2_000)
		// Con 3,5 s sí: pasa del mínimo.
		VueltaAlBorde.decidir(30_000, 40_000, 22_500, OBJETIVO) shouldBe
			VueltaAlBorde.Decision.Salta(destinoMs = 26_000, gananciaMs = 3_500)
	}

	"un objetivo mayor que la ventana no manda el salto a una posición negativa" {
		// Un `config.json` mal generado puede pedir más offset que ventana. El
		// motor ya lo avisa aparte (`offset.superaLaVentana`); aquí lo que
		// importa es que el destino no se vaya por debajo de cero.
		val d = VueltaAlBorde.decidir(60_000, 20_000, 1_000, 40_000)
		d shouldBe VueltaAlBorde.Decision.NoAdelanta(destinoMs = 0, gananciaMs = -1_000)
	}
})
