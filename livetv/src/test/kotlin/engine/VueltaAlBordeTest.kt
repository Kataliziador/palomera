package org.jellyfin.livetv.engine

import androidx.media3.common.C
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.livetv.LiveTuning

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

	// --- El escalón 1 del vigilante (pendiente 77) ---------------------------
	//
	// Las seis de abajo son el cable entero de `rescateDelVigilante`, en los dos
	// sentidos. Importan porque el valor de fábrica es **el comportamiento
	// anterior**, así que un fallo aquí no se vería en ninguna sesión normal: se
	// vería el día que alguien enciende el interruptor para medir, y entonces la
	// medición saldría mal sin que nadie supiera por qué.

	"de fábrica no cambia nada: el vigilante sigue saltando como siempre" {
		VueltaAlBorde.rescateDelVigilante(
			calculado = false,
			ventanaMs = 33_600,
			objetivoMs = OBJETIVO,
		) shouldBe VueltaAlBorde.Rescate.PosicionPorDefecto("interruptor apagado")
	}

	"encendido, salta al mismo destino que los otros dos mecanismos" {
		// El mismo número que `offset.margenCorto` y `offset.vuelveAlBorde`
		// calculan con `destino()`, que es justo lo que el 77 propone igualar.
		VueltaAlBorde.rescateDelVigilante(true, 33_600, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.Calculado(destinoMs = 19_600)
		VueltaAlBorde.destino(33_600, OBJETIVO) shouldBe 19_600L
	}

	"sin ventana no se inventa un destino" {
		// Es el caso ciego de siempre: el vigilante rescata igual, por el
		// camino de antes. Y el motivo viaja al log, porque una meseta por
		// defecto con el interruptor encendido no dice por sí sola si fue una
		// decisión o un instrumento que no medía.
		VueltaAlBorde.rescateDelVigilante(true, C.TIME_UNSET, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.PosicionPorDefecto("ventana desconocida")
		VueltaAlBorde.rescateDelVigilante(true, 0, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.PosicionPorDefecto("ventana desconocida")
	}

	"sin objetivo tampoco" {
		// `activeTargetOffsetMs` vale 0 hasta la primera preparación, y el
		// vigilante puede disparar antes de que el plan llegue.
		VueltaAlBorde.rescateDelVigilante(true, 33_600, 0) shouldBe
			VueltaAlBorde.Rescate.PosicionPorDefecto("objetivo desconocido")
	}

	"si la ventana no da para apartarse, no se salta al segmento que caduca" {
		// `destino()` corta en cero, y cero es el primer segmento de la ventana
		// —el que está a punto de desaparecer—. Saltar ahí cambiaría un buffer
		// fino por un `BEHIND_LIVE_WINDOW`. Es el mismo caso que
		// `MargenDeBuffer` avisa como `SinSitioDetras`.
		VueltaAlBorde.rescateDelVigilante(true, 14_000, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.PosicionPorDefecto("la ventana no da para apartarse")
		VueltaAlBorde.rescateDelVigilante(true, 9_000, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.PosicionPorDefecto("la ventana no da para apartarse")
		// Un milisegundo de margen ya es un destino, y vale: el límite está
		// escrito, no en el aire.
		VueltaAlBorde.rescateDelVigilante(true, 14_001, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.Calculado(destinoMs = 1)
	}

	"el valor de fábrica es el comportamiento anterior, y eso lo manda el Modo A" {
		// `CLAUDE.md` §3: todo cambio de comportamiento de motor va detrás de un
		// interruptor con el valor por defecto igual al comportamiento anterior,
		// hasta que Edgar lo valide. Esta línea es lo único que vigila esa
		// regla para el 77: sin ella, encender el experimento de fábrica «de
		// paso» no rompería ninguna prueba y la primera noticia sería una
		// sesión de campo comportándose distinto a la medida.
		//
		// Cuando el experimento se valide, esta prueba se invierte **en el
		// mismo commit** que el valor, y el commit explica con qué medida.
		LiveTuning.VIGILANTE_DESTINO_CALCULADO_DEFAULT shouldBe false
		VueltaAlBorde.rescateDelVigilante(
			calculado = LiveTuning.VIGILANTE_DESTINO_CALCULADO_DEFAULT,
			ventanaMs = 33_600,
			objetivoMs = OBJETIVO,
		) shouldBe VueltaAlBorde.Rescate.PosicionPorDefecto("interruptor apagado")
	}

	"la ventana de los seis canales de desarrollo da de sobra" {
		// 29-36 s medidos desde que se subió `segment_count` a 12 (pendiente
		// 17). Con el objetivo de 14 s, el destino cae a 15-22 s del principio.
		VueltaAlBorde.rescateDelVigilante(true, 29_000, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.Calculado(destinoMs = 15_000)
		VueltaAlBorde.rescateDelVigilante(true, 36_000, OBJETIVO) shouldBe
			VueltaAlBorde.Rescate.Calculado(destinoMs = 22_000)
	}
})
