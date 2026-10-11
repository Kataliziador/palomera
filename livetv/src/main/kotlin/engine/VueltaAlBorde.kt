package org.jellyfin.livetv.engine

import androidx.media3.common.C
import org.jellyfin.livetv.LiveTuning

/**
 * Si volver al borde de emisión sirve de algo, y a dónde.
 *
 * ## Por qué es una clase y no cuatro líneas en el motor
 *
 * Fue cuatro líneas dentro de `onTimelineChanged`, y ahí no se podía probar.
 * Lleva dos pendientes equivocados —el 70 y el 71— que se diagnosticaron mirando
 * una noche entera de log, cuando los nueve casos que hacían falta caben en una
 * tabla. Mismo motivo por el que [RelojDeAvance] se extrajo, y la prueba de
 * aquella tumbó la primera versión de su arreglo.
 *
 * ## El offset no dice si hay a dónde saltar
 *
 * `currentLiveOffset` es **reloj de pared contra `EXT-X-PROGRAM-DATE-TIME`**: mide
 * cuánto se ha retrasado la reproducción respecto a la hora en que se emitió lo
 * que se está viendo. Mientras el origen publica, eso coincide con «lo que me
 * falta para el directo». Cuando el playlist deja de crecer, **no**: el offset
 * sube sin límite —medido el 10-oct en `mileniosd`: 39 → 53 → 72 → 81 s— aunque
 * el reproductor esté pegado al final de todos los datos que existen.
 *
 * Los nueve saltos de esa sesión, con la posición a la vista:
 *
 * | pos antes | ventana | destino | ganancia | offset después |
 * |---|---|---|---|---|
 * | 26 843 | 33 267 | 19 267 | **−7 576** | 80 s — falló |
 * | −41 844 | 33 600 | 19 600 | +61 444 | 19,3 s ✓ |
 * | 326 | 33 600 | 19 600 | +19 274 | 18,5 s ✓ |
 *
 * De ahí salió el pendiente 73 —«los tres que saltan hacia atrás no recuperan
 * nada»— y **el 11-oct se midió lo contrario**: ese salto hacia atrás es la
 * cura. Deja la posición 9 s más adentro del buffer, y con el origen publicando
 * más despacio que el reloj eso es exactamente lo que hace falta; declinarlo
 * costó 12 cortes en 33 s (pendiente 75). El salto de la primera fila compró,
 * por un corte, treinta segundos seguidos con 10-13 s de buffer.
 *
 * Así que [Decision.NoAdelanta] **describe** el salto, no lo juzga: quien la
 * recibe decide, y de fábrica se salta igual
 * ([LiveTuning.RESYNC_SOLO_SI_ADELANTA_DEFAULT] en `false`). Lo que debería
 * decidir —y es el pendiente 75— no es el signo de la ganancia sino el margen
 * de buffer que deja el destino.
 */
internal object VueltaAlBorde {

	sealed interface Decision {
		/** Hay material por delante: saltar a [destinoMs]. */
		data class Salta(val destinoMs: Long, val gananciaMs: Long) : Decision

		/**
		 * El salto iría hacia atrás o casi no movería: el offset está inflado
		 * porque la lista no avanza.
		 *
		 * **No significa «no sirve».** Medido el 11-oct, ir hacia atrás dentro
		 * de la ventana es la única cura del bucle de inanición del 75. Esto
		 * dice qué clase de salto es; si se da o no lo decide quien llama.
		 */
		data class NoAdelanta(val destinoMs: Long, val gananciaMs: Long) : Decision

		/** El offset está dentro de lo tolerable. */
		data object NoHaceFalta : Decision
	}

	/**
	 * @param offsetRealMs lo que dice `currentLiveOffset`, o [C.TIME_UNSET].
	 * @param ventanaMs duración de la ventana DVR conocida por el reproductor.
	 * @param posMs posición actual, que **puede ser negativa** si la ventana ya
	 *   dejó atrás al reproductor, o [C.TIME_UNSET] si no se sabe.
	 * @param objetivoMs a cuánto del final se quiere estar.
	 */
	fun decidir(
		offsetRealMs: Long,
		ventanaMs: Long,
		posMs: Long,
		objetivoMs: Long,
	): Decision {
		if (offsetRealMs == C.TIME_UNSET || ventanaMs == C.TIME_UNSET || ventanaMs <= 0L) {
			return Decision.NoHaceFalta
		}

		// El disparador de siempre: por encima de media ventana, con margen para
		// no reaccionar al ruido. Queda apuntado en el pendiente 72 que está mal
		// elegido —el directo sano se asienta a 300 ms de este umbral— pero
		// cambiarlo es otra fase.
		val limite = (ventanaMs * LiveTuning.LIVE_EDGE_RESYNC_WINDOW_FRACTION).toLong()
		if (offsetRealMs - limite <= LiveTuning.LIVE_EDGE_RESYNC_MIN_EXCESS_MS) {
			return Decision.NoHaceFalta
		}

		val destinoMs = (ventanaMs - objetivoMs).coerceAtLeast(0L)

		// Sin posición no se inventa un bloqueo: se conserva lo que hacía antes.
		// Es la misma regla que [RelojDeAvance] con su caso ciego — un
		// instrumento que no mide no autoriza a decidir en ninguno de los dos
		// sentidos, y de los dos errores este es el barato.
		if (posMs == C.TIME_UNSET) return Decision.Salta(destinoMs, C.TIME_UNSET)

		val gananciaMs = destinoMs - posMs
		return if (gananciaMs > LiveTuning.LIVE_EDGE_RESYNC_MIN_GAIN_MS) {
			Decision.Salta(destinoMs, gananciaMs)
		} else {
			Decision.NoAdelanta(destinoMs, gananciaMs)
		}
	}
}
