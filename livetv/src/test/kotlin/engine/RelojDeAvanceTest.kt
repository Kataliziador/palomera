package org.jellyfin.livetv.engine

import androidx.media3.common.C
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * El detector de atasco, probado contra las dos sesiones que lo engañaron.
 *
 * Son doce líneas de clase y aun así merecen pruebas: de ellas depende que la
 * escalera de rescate llegue a empezar, y el 2026-10-09 no empezó en 98 minutos
 * de pantalla muerta. La primera versión de este arreglo —«progresar es que la
 * posición suba»— la tumbó la segunda prueba de aquí, que es la que lleva los
 * números de una sesión sana.
 */
class RelojDeAvanceTest : StringSpec({

	val UMBRAL = 10_000L

	"reproducción normal: progresa en cada latido" {
		val r = RelojDeAvance()
		r.reiniciar(0)
		var t = 0L
		var pos = 20_000L
		repeat(30) {
			t += 2_000
			pos += 2_000
			r.observar(pos, reproduciendo = true, ahoraMs = t) shouldBe 0L
		}
	}

	"la posición de un directo sano no sube: oscila, y eso es progresar" {
		// Los números son de la sesión del 09-oct antes del atasco, con la
		// ventana corriendo al mismo ritmo que la reproducción. Si se exigiera
		// que la posición superase su máximo, esto daría 18 s sin progreso y el
		// vigilante despertaría sobre un canal que se ve perfecto.
		val r = RelojDeAvance()
		r.reiniciar(0)
		val posiciones = listOf(23_600L, 20_500, 20_200, 22_200, 22_000, 19_500, 21_500, 21_400, 19_100, 21_100)
		var t = 0L
		var peor = 0L
		for (p in posiciones) {
			t += 2_000
			peor = maxOf(peor, r.observar(p, reproduciendo = true, ahoraMs = t))
		}
		peor shouldBe 0L
	}

	"parado con la posición cayendo sin parar: ESO es el atasco del 09-oct" {
		// `reproduciendo=false` y la posición cambiando cada latido, que es lo
		// que hacía que esto pasara desapercibido.
		val r = RelojDeAvance()
		r.reiniciar(0)
		var t = 0L
		var pos = -37_900L
		repeat(10) {
			t += 2_000
			pos -= 2_100
			r.observar(pos, reproduciendo = false, ahoraMs = t)
		}
		r.observar(pos - 2_100, reproduciendo = false, ahoraMs = t + 2_000) shouldBeGreaterThan UMBRAL
	}

	"reproduciendo pero clavado en la misma posición: el atasco del 30-sep" {
		val r = RelojDeAvance()
		r.reiniciar(0)
		r.observar(5_000, reproduciendo = true, ahoraMs = 0)
		r.observar(5_000, reproduciendo = true, ahoraMs = 12_000) shouldBeGreaterThan UMBRAL
	}

	"un rebuffer corriente no despierta al vigilante" {
		// Lo medido en producción: la mediana de los cortes de `discoveryhd` es
		// de 194 ms y el máximo de 1,7 s. Un umbral de diez segundos tiene que
		// dejarlos pasar o el rescate sería peor que el fallo.
		val r = RelojDeAvance()
		r.reiniciar(0)
		var t = 0L
		var pos = 20_000L
		repeat(5) {
			t += 2_000; pos += 2_000
			r.observar(pos, reproduciendo = true, ahoraMs = t)
		}
		// 1,7 s sin reproducir, el peor corte medido.
		r.observar(pos, reproduciendo = false, ahoraMs = t + 1_700) shouldBe 1_700L
		// Y al volver, a cero.
		r.observar(pos + 1_700, reproduciendo = true, ahoraMs = t + 2_000) shouldBe 0L
	}

	"sin reproductor no se inventa un progreso" {
		val r = RelojDeAvance()
		r.reiniciar(0)
		r.observar(5_000, reproduciendo = true, ahoraMs = 1_000)
		r.observar(C.TIME_UNSET, reproduciendo = false, ahoraMs = 13_000) shouldBeGreaterThan UMBRAL
	}

	"preparar de nuevo pone el reloj a cero" {
		// Si no, el rescate heredaría el atasco del intento anterior y
		// encadenaría escalones sobre un reproductor que acaba de arrancar.
		val r = RelojDeAvance()
		r.reiniciar(0)
		r.observar(5_000, reproduciendo = false, ahoraMs = 20_000) shouldBeGreaterThan UMBRAL
		r.reiniciar(20_000)
		r.observar(0, reproduciendo = false, ahoraMs = 20_000) shouldBe 0L
	}
})
