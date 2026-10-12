package org.jellyfin.livetv.engine

import androidx.media3.common.C
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * El margen de buffer, probado contra los latidos que lo midieron.
 *
 * Los números son del volcado del 10-oct (`mileniosd`, 298 min, ventana de
 * 33 600 ms y objetivo de 14 000). Dos ráfagas de inanición de dos minutos se
 * comieron **25 de los 35 cortes** de la sesión, y entre ellas hay 4 h 10 min
 * limpias que esta clase no debe tocar: ese es el guardarraíl del pendiente 72,
 * y si alguien afloja las constantes falla aquí y no en la tele de Edgar.
 */
class MargenDeBufferTest : StringSpec({

	val VENTANA = 33_600L
	val OBJETIVO = 14_000L

	fun cuatroVeces(m: MargenDeBuffer, bufferMs: Long, posMs: Long) =
		(1..4).map { m.observar(bufferMs, VENTANA, posMs, OBJETIVO) }.last()

	"el latido que empezó la ráfaga: pegado al final, con 12,5 s detrás" {
		// 19:56:46 estado=almacenando reproduciendo=false posS=32.1 bufferS=1.5
		//
		// La ventana todavía tenía 33,6 s de datos. Lo que no tenía el
		// reproductor era nada por delante, porque solo almacena hacia adelante
		// y estaba al final. Moverse a 19 600 lo aparta 12,5 s del borde — que
		// es lo que fija la meseta, no lo que rellena el buffer en el acto (ver
		// el KDoc de `MargenDeBuffer`).
		val m = MargenDeBuffer()
		cuatroVeces(m, bufferMs = 1_500, posMs = 32_100) shouldBe
			MargenDeBuffer.Decision.Alejarse(destinoMs = 19_600, colchonMs = 12_500)
	}

	"las cuatro horas limpias no disparan nada" {
		// Mediana 10,7 s y p5 8,4 en 7 500 latidos. Ni uno por debajo de 6
		// cuatro veces seguidas.
		val m = MargenDeBuffer()
		listOf(10_700L, 8_400L, 15_000L, 8_500L, 11_300L).forEach {
			m.observar(it, VENTANA, 19_000, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
		}
	}

	"un bache suelto no cuenta: la racha se reinicia" {
		// Tres latidos cortos y uno sano. Pasa en toda sesión al cambiar de
		// segmento, y reaccionar a eso sería el pendiente 72 otra vez.
		val m = MargenDeBuffer()
		repeat(3) {
			m.observar(2_000, VENTANA, 32_100, OBJETIVO) shouldBe
				MargenDeBuffer.Decision.HayMargen
		}
		m.observar(9_000, VENTANA, 32_100, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
		// Y ahora tres cortos más tampoco bastan, porque empezó de cero.
		repeat(3) {
			m.observar(2_000, VENTANA, 32_100, OBJETIVO) shouldBe
				MargenDeBuffer.Decision.HayMargen
		}
	}

	"la oscilación del bucle dispara, con los números del bucle" {
		// 19:57:42 → 19:57:52, los cuatro latidos tal cual: 3,3 / 4,4 / 2,7 / 3,7
		// con la posición entre 28,2 y 29,8. Uno de cada tres cortaba.
		val m = MargenDeBuffer()
		m.observar(3_300, VENTANA, 29_300, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
		m.observar(4_400, VENTANA, 28_200, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
		m.observar(2_700, VENTANA, 29_800, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
		m.observar(3_700, VENTANA, 28_800, OBJETIVO) shouldBe
			MargenDeBuffer.Decision.Alejarse(destinoMs = 19_600, colchonMs = 9_200)
	}

	"tras actuar no se encadena: la racha se consume" {
		// El salto vacía el buffer y lo vuelve a llenar, y contar eso como
		// inanición daría un salto cada dos segundos. Es la misma causa por la
		// que el analizador descarta las caídas de los 30 s posteriores a un
		// salto.
		val m = MargenDeBuffer()
		cuatroVeces(m, 1_500, 32_100).shouldBeAlejarse()
		m.observar(1_500, VENTANA, 19_600, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
	}

	"con la ventana corta no hay sitio detrás, y eso se dice en vez de saltar" {
		// `oncesd` y `canal22sd` sirven 4 segmentos de 5,6 s: ventana de 22 400 y
		// objetivo recortado a 11 000, o sea destino 11 400. Con la posición en
		// 13 000 el salto ganaría 1,6 s, menos que un segmento. El arreglo no es
		// del cliente: es el `segment_count` del site, y el servicio ya lo grita.
		val m = MargenDeBuffer()
		(1..4).map { m.observar(2_000, 22_400, 13_000, 11_000) }.last() shouldBe
			MargenDeBuffer.Decision.SinSitioDetras(destinoMs = 11_400, colchonMs = 1_600)
	}

	"sin medida del buffer no se decide, y la racha no se hereda" {
		val m = MargenDeBuffer()
		repeat(3) { m.observar(2_000, VENTANA, 32_100, OBJETIVO) }
		m.observar(C.TIME_UNSET, VENTANA, 32_100, OBJETIVO) shouldBe
			MargenDeBuffer.Decision.NoSeSabe
		// Un latido corto más: si la racha se hubiera heredado, aquí dispararía.
		m.observar(2_000, VENTANA, 32_100, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
	}

	"sin ventana o sin posición no se inventa un destino" {
		MargenDeBuffer().let { m ->
			(1..4).map { m.observar(1_500, C.TIME_UNSET, 32_100, OBJETIVO) }.last() shouldBe
				MargenDeBuffer.Decision.NoSeSabe
		}
		MargenDeBuffer().let { m ->
			(1..4).map { m.observar(1_500, VENTANA, C.TIME_UNSET, OBJETIVO) }.last() shouldBe
				MargenDeBuffer.Decision.NoSeSabe
		}
	}

	"reiniciar borra la racha, que es lo que pasa entre canales" {
		val m = MargenDeBuffer()
		repeat(3) { m.observar(1_500, VENTANA, 32_100, OBJETIVO) }
		m.reiniciar()
		m.observar(1_500, VENTANA, 32_100, OBJETIVO) shouldBe MargenDeBuffer.Decision.HayMargen
	}
})

private fun MargenDeBuffer.Decision.shouldBeAlejarse() {
	(this is MargenDeBuffer.Decision.Alejarse) shouldBe true
}
