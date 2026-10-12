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
 * —19,6 s— cuesta un solo corte.
 *
 * **Lo que ese salto compra no es el rescate inmediato.** Este KDoc decía que
 * pone «12,5 s de datos ya publicados delante», y la sesión de validación del
 * 11-oct lo desmintió: el salto tira el buffer y los segmentos de atrás hay que
 * volver a pedirlos, así que con el origen callado tampoco llegan. En tres de
 * los cuatro saltos medidos el buffer se quedó en 1,5 / 1,4 / 2,7 s y cayó a
 * cero en el latido siguiente; solo llenó el cuarto (4,4 → 12,4 s), que fue el
 * único con el origen ya sirviendo.
 *
 * Lo que compra es **la meseta**: a qué distancia del directo se reproduce
 * durante las horas siguientes. Un `seekTo` dentro de una ventana en directo
 * redefine el objetivo de latencia de Media3 —`ExoPlayerImplInternal` llama a
 * `setTargetLiveOffsetOverrideUs` con el offset del destino— y el reproductor se
 * queda ahí hasta el siguiente salto. Medido: el salto de las 13:02:21 dejó la
 * meseta en 18,6 s de directo y 14,8 s de buffer, y de ahí salieron **4 h 07 min
 * sin un corte**; el escalón 1 del vigilante, que usa `seekToDefaultPosition`,
 * dejó mesetas de 10,7 y 8,5 s, y la segunda murió en 26 segundos.
 *
 * Y el reproductor aguanta tantos segundos de silencio del origen como segundos
 * de buffer tenga. Por eso la meseta es la métrica, y no el corte.
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
		/**
		 * Hay sitio detrás: moverse a [destinoMs] aparta la posición del borde.
		 * No rellena el buffer por sí solo —eso depende de que el origen
		 * sirva—, fija la distancia al directo de ahí en adelante.
		 */
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
