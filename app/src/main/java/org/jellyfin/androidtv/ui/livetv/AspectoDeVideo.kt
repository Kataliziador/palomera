package org.jellyfin.androidtv.ui.livetv

import android.view.SurfaceView
import org.jellyfin.livetv.engine.AspectMode
import org.jellyfin.livetv.engine.EngineStats

/**
 * Aplica el modo de ajuste escalando la superficie de vídeo.
 *
 * Se hace **en la vista y no en el motor**: el decodificador entrega la imagen
 * como viene y el escalado es una preferencia de quien mira, no una propiedad
 * del flujo. Mezclarlo con la decodificación obligaría a rehacer el motor cada
 * vez que alguien toca el botón.
 *
 * **El punto de partida ya es correcto.** Con el SAR respetado, los seis
 * canales salen con su proporción real en modo `FIT`, incluidos los dos
 * anamórficos. Esto existe para quien no quiere franjas negras, no para
 * arreglar nada.
 */
object AspectoDeVideo {

	fun nombre(mode: AspectMode): String = when (mode) {
		AspectMode.FIT -> "Ajustar"
		AspectMode.FILL -> "Llenar"
		AspectMode.ZOOM -> "Zoom"
		AspectMode.RATIO_4_3 -> "4:3"
		AspectMode.RATIO_16_9 -> "16:9"
	}

	val modos = listOf(
		AspectMode.FIT,
		AspectMode.FILL,
		AspectMode.ZOOM,
		AspectMode.RATIO_4_3,
		AspectMode.RATIO_16_9,
	)

	/**
	 * Escala la superficie para el modo pedido.
	 *
	 * `FIT` no toca nada: la superficie ya ocupa la pantalla y Media3 entrega
	 * la imagen con su proporción. Los demás parten de ahí.
	 */
	fun aplicar(view: SurfaceView, mode: AspectMode, stats: EngineStats) {
		val anchoVista = view.width.toFloat()
		val altoVista = view.height.toFloat()
		if (anchoVista <= 0f || altoVista <= 0f) return

		val anchoVideo = stats.videoWidth * stats.pixelAspectRatio
		val altoVideo = stats.videoHeight.toFloat()
		if (anchoVideo <= 0f || altoVideo <= 0f) return

		val proporcionVista = anchoVista / altoVista
		val proporcionVideo = anchoVideo / altoVideo

		val objetivo = when (mode) {
			AspectMode.FIT, AspectMode.FILL, AspectMode.ZOOM -> proporcionVideo
			AspectMode.RATIO_4_3 -> 4f / 3f
			AspectMode.RATIO_16_9 -> 16f / 9f
		}

		// `SurfaceView` ya pinta la imagen ajustada a la vista, así que lo que
		// se calcula aquí es cuánto hay que corregir **respecto a eso**.
		var escalaX = 1f
		var escalaY = 1f

		when (mode) {
			AspectMode.FIT -> Unit

			// Llena la pantalla recortando lo que sobra por el lado largo.
			AspectMode.FILL, AspectMode.ZOOM -> {
				val factor = if (proporcionVideo > proporcionVista) {
					proporcionVideo / proporcionVista
				} else {
					proporcionVista / proporcionVideo
				}
				// El zoom aprieta un poco más: es el modo para quitar las
				// franjas de un 4:3 emitido dentro de un 16:9.
				val extra = if (mode == AspectMode.ZOOM) 1.12f else 1f
				escalaX = factor * extra
				escalaY = factor * extra
			}

			AspectMode.RATIO_4_3, AspectMode.RATIO_16_9 -> {
				// Fuerza la proporción pedida deformando respecto a la real.
				if (objetivo > proporcionVideo) escalaX = objetivo / proporcionVideo
				else escalaY = proporcionVideo / objetivo
			}
		}

		view.scaleX = escalaX
		view.scaleY = escalaY
	}
}
