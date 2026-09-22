package org.jellyfin.livetv.engine

import android.view.SurfaceView
import org.jellyfin.livetv.source.AudioTrackRef
import org.jellyfin.livetv.source.ChannelProfile

/** Cómo se decodifica. Lo elige la cascada de LIVETV_ENGINE.md §4.2. */
enum class DecodePath { HW, SW_PLATFORM, SW_VLC }

enum class DeinterlaceMode { NONE, VPU, SOFTWARE, GPU_SHADER }

enum class AspectMode { FIT, FILL, ZOOM, RATIO_4_3, RATIO_16_9 }

/**
 * El plan de reproducción de un canal en una caja concreta.
 *
 * `reason` no es decorativo: se muestra en el overlay de stats. Sin él, depurar
 * en una caja ajena es imposible (LIVETV_ENGINE.md §4.5).
 */
data class PlaybackPlan(
	val engineId: String,
	val videoPath: DecodePath,
	val audioPath: DecodePath,
	val deinterlace: DeinterlaceMode,
	val targetOffsetMs: Long,
	val tsFlags: Int,
	val reason: String,
)

data class EngineStats(
	val engineId: String,
	val activeVideoDecoder: String? = null,
	val activeAudioDecoder: String? = null,
	val bufferedMs: Long = 0,
	val bitrateBps: Long = 0,
	val droppedFrames: Int = 0,
	val msSinceLastFrame: Long = 0,
	val liveOffsetMs: Long = 0,
	val reason: String? = null,
)

interface EngineListener {
	fun onReady() {}
	fun onPlaying() {}
	fun onBuffering() {}

	/**
	 * Un error que el motor ya está gestionando. **No significa que haya que
	 * cerrar el player**: un canal caído por lluvia es un estado temporal
	 * (CLAUDE.md §6).
	 */
	fun onRecoverableError(event: String, error: Throwable?) {}

	/** El canal no se puede reproducir en esta caja. Agotada la cascada. */
	fun onFatalError(event: String, error: Throwable?) {}

	fun onAudioTracksChanged(tracks: List<AudioTrackRef>) {}
	fun onVideoSizeChanged(width: Int, height: Int, pixelWidthHeightRatio: Float) {}
}

/**
 * El contrato del motor. La interfaz existe desde el primer commit aunque solo
 * haya una implementación (CLAUDE.md regla 10), porque libVLC entra en M6 por
 * esta misma puerta.
 *
 * **La interfaz habla solo con esto.** Nunca con `ExoPlayer` ni con el
 * `MediaPlayer` de libVLC directamente.
 */
interface LiveEngine {
	val id: String

	fun attach(surface: SurfaceView)
	fun prepare(channel: ChannelProfile, plan: PlaybackPlan)
	fun play()
	fun stop()
	fun release()

	/** Por identidad de pista, nunca por PID ni por índice. Ver [AudioTrackRef]. */
	fun selectAudioTrack(track: AudioTrackRef)

	fun setAspectMode(mode: AspectMode)
	fun addListener(listener: EngineListener)
	fun removeListener(listener: EngineListener)
	fun stats(): EngineStats
}
