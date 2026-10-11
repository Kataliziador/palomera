package org.jellyfin.livetv.engine

import androidx.media3.common.C
import org.jellyfin.livetv.LiveTuning

/**
 * Cuándo la posición está demasiado pegada al borde para sobrevivir, y hay sitio
 * detrás al que moverla. Pendiente 75.
 *
 * ## El fallo que mide
 *
 * El 10-oct, dos veces en cinco horas, el origen dejó de publicar y el
 * reproductor acabó parado a 1,5 s del final de todos los datos que existían:
 *
 * ```
 * 19:56:46  estado=almacenando reproduciendo=false posS=32.1 bufferS=1.5  (ventana 33,6 s)
 * ```
 *
 * Y de ahí salió un **bucle de inanición**: dos segundos de imagen, un corte,
 * dos segundos, un corte, **uno cada tres segundos durante 38 s**. Trece cortes
 * en una ráfaga y doce en la otra: **25 de los 35 cortes de la sesión**.
 *
 * ## Por qué ninguno de los dos mecanismos que ya existen lo ve
 *
 * - **El vigilante** ([RelojDeAvance]) pregunta si la posición avanza, y en el
 *   bucle avanza: se reproducen dos segundos cada tres. No hay atasco que ver.
 * - **La vuelta al borde** ([VueltaAlBorde]) vive dentro de
 *   `onTimelineChanged`, y con la lista del origen congelada ese método no se
 *   llama. Medido: 38 segundos sin una sola evaluación.
 *
 * Por eso esto se mira **en el latido**, que corre cada dos segundos pase lo que
 * pase con la lista.
 *
 * ## La cura, y por qué es ir hacia atrás
 *
 * En ese latido de las 19:56:46 la ventana seguía teniendo 33,6 s de datos: lo
 * que no tenía el reproductor era **nada por delante**, porque solo almacena
 * hacia adelante y estaba al final. Mover la posición a `ventana − objetivo`
 * —19,6 s— le pone **12,5 s de datos ya publicados delante**, al precio de un
 * solo corte.
 *
 * Eso es lo que el comportamiento viejo hacía por accidente y lo que el
 * pendiente 73 quitó creyendo que era desperdicio: medido el 11-oct, un salto
 * así llevó el buffer de 3,9 a 12,9 s y compró **treinta segundos seguidos sin
 * un corte**.
 *
 * ## Dos cosas que las medidas dejaron claras, y conviene no deshacer
 *
 * **No se exige estar reproduciendo.** La guarda «solo reproduciendo» de la
 * vuelta al borde existe porque saltar *hacia adelante* mientras se almacena no
 * adelanta nada. Aquí es al revés: los cinco momentos en que esto debía actuar
 * tenían `reproduciendo=false`, y es justo cuando hay datos detrás sin consumir.
 *
 * **El umbral no es delicado**, al contrario que el del pendiente 72. Probado
 * contra los dos volcados del 10-oct con 5, 6 y 7 s y con 3, 4 y 5 latidos: las
 * nueve combinaciones dan **exactamente los mismos disparos**. El buffer de esta
 * señal o está sano por encima de 7 s o está hundido por debajo de 5; no hay
 * zona intermedia que afinar. Y en las 4 h 10 min limpias de esa sesión no
 * dispara ninguna de las nueve.
 */
internal class MargenDeBuffer {

	sealed interface Decision {
		/** Hay datos detrás sin consumir: moverse a [destinoMs] los pone delante. */
		data class Alejarse(val destinoMs: Long, val colchonMs: Long) : Decision

		/** El buffer aguanta, o la racha es demasiado corta para creérsela. */
		data object HayMargen : Decision

		/**
		 * El buffer está corto pero moverse no lo arregla: el destino queda
		 * donde ya estamos o delante. Pasa cuando la ventana es tan pequeña que
		 * no hay sitio detrás — `oncesd` y `canal22sd` sirven 22 s— y entonces
		 * el arreglo no es del cliente sino del `segment_count` del headend.
		 */
		data class SinSitioDetras(val destinoMs: Long, val colchonMs: Long) : Decision

		/** Falta un dato para decidir. */
		data object NoSeSabe : Decision
	}

	private var latidosCortos = 0

	fun reiniciar() {
		latidosCortos = 0
	}

	/**
	 * Anota el latido de ahora y dice qué hacer.
	 *
	 * @param bufferMs datos por delante, o [C.TIME_UNSET] si no se sabe.
	 * @param ventanaMs duración de la ventana DVR conocida por el reproductor.
	 * @param posMs posición actual, que puede ser negativa si la ventana ya dejó
	 *   atrás al reproductor, o [C.TIME_UNSET].
	 * @param objetivoMs a cuánto del final se quiere estar.
	 */
	fun observar(bufferMs: Long, ventanaMs: Long, posMs: Long, objetivoMs: Long): Decision {
		if (bufferMs == C.TIME_UNSET) {
			latidosCortos = 0
			return Decision.NoSeSabe
		}

		if (bufferMs >= LiveTuning.MARGEN_MINIMO_MS) {
			latidosCortos = 0
			return Decision.HayMargen
		}

		latidosCortos++
		if (latidosCortos < LiveTuning.MARGEN_LATIDOS_SEGUIDOS) return Decision.HayMargen

		if (ventanaMs == C.TIME_UNSET || ventanaMs <= 0L || posMs == C.TIME_UNSET) {
			return Decision.NoSeSabe
		}

		// El mismo destino que la vuelta al borde, por la misma aritmética: una
		// sola definición para que no haya dos versiones del mismo número.
		val destinoMs = VueltaAlBorde.destino(ventanaMs, objetivoMs)
		val colchonMs = posMs - destinoMs
		return if (colchonMs > LiveTuning.LIVE_EDGE_RESYNC_MIN_GAIN_MS) {
			// Se consume la racha: tras el salto el buffer se vacía y se vuelve
			// a llenar, y contar eso como inanición encadenaría saltos.
			latidosCortos = 0
			Decision.Alejarse(destinoMs, colchonMs)
		} else {
			Decision.SinSitioDetras(destinoMs, colchonMs)
		}
	}
}
