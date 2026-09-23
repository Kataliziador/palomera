package org.jellyfin.livetv.engine

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.source.AudioTrackRef
import org.jellyfin.livetv.source.ChannelProfile
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlin.math.pow

/**
 * El motor principal: Media3/ExoPlayer sobre HLS con segmentos TS.
 *
 * Lo que este motor **no** hace, a propósito:
 * - **No hace ABR.** Una sola calidad por canal (CLAUDE.md §6). El endpoint es
 *   `mono.m3u8`, un media playlist directo, así que no hay variantes que elegir.
 * - **No cierra el player ante un error de red.** Un canal caído por lluvia es
 *   un estado temporal: se reintenta indefinidamente con backoff.
 * - **No usa TextureView.** SurfaceView siempre.
 */
@UnstableApi
class Media3LiveEngine(
	private val context: Context,
	private val dev: DevOptions,
) : LiveEngine {

	override val id: String = ENGINE_ID

	private var player: ExoPlayer? = null
	private var surface: SurfaceView? = null
	private var channel: ChannelProfile? = null
	private var plan: PlaybackPlan? = null

	private val listeners = CopyOnWriteArrayList<EngineListener>()
	private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

	// Estado observado, para el overlay de stats
	private var videoDecoder: String? = null
	private var audioDecoder: String? = null
	private var droppedFrames = 0
	private var bytesLoaded = 0L
	private var loadingSinceMs = 0L
	private var lastPositionMs = C.TIME_UNSET
	private var lastProgressAtMs = 0L
	private var windowDurationMs = C.TIME_UNSET
	private var activeTargetOffsetMs = 0L

	override fun attach(surface: SurfaceView) {
		this.surface = surface
		player?.setVideoSurfaceView(surface)
		LiveLog.debug("engine.attach")
	}

	override fun prepare(channel: ChannelProfile, plan: PlaybackPlan) {
		this.channel = channel
		this.plan = plan

		LiveLog.startSession(channel.id, channel.url, channel.videoCodec, channel.audioCodec)
		LiveLog.info(
			"engine.prepare",
			"engine" to id,
			"videoPath" to plan.videoPath,
			"targetOffsetMs" to (dev.targetOffsetOverrideMs ?: plan.targetOffsetMs),
			"reason" to plan.reason,
			"degradacion" to dev.describe(),
		)

		release()
		bytesLoaded = 0
		loadingSinceMs = System.currentTimeMillis()
		lastPositionMs = C.TIME_UNSET
		lastProgressAtMs = System.currentTimeMillis()

		val httpFactory = DefaultHttpDataSource.Factory()
			.setConnectTimeoutMs(LiveTuning.CONNECT_TIMEOUT_MS)
			.setReadTimeoutMs(LiveTuning.READ_TIMEOUT_MS)
			.setAllowCrossProtocolRedirects(true)

		// En HLS los flags del extractor **no** se heredan de
		// `DefaultMediaSourceFactory`: hay que pasarlos por aquí
		// (LIVETV_ENGINE.md §5). Configurables en caliente desde el menú, que
		// es lo que M4 necesita para resolver la contradicción en Amlogic.
		var tsFlags = 0
		if (dev.tsDetectAccessUnits) tsFlags = tsFlags or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
		if (dev.tsAllowNonIdrKeyframes) tsFlags = tsFlags or DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES

		val mediaSourceFactory = HlsMediaSource.Factory(httpFactory)
			.setLoadErrorHandlingPolicy(LiveErrorPolicy())
			.setExtractorFactory(DefaultHlsExtractorFactory(tsFlags, false))
			// El playlist se reconstruye tras una caída de la fuente; que el
			// parser no se detenga en el primer hueco.
			.setAllowChunklessPreparation(false)

		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				dev.minBufferMs,
				dev.maxBufferMs,
				LiveTuning.BUFFER_FOR_PLAYBACK_MS,
				LiveTuning.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
			)
			.setPrioritizeTimeOverSizeThresholds(true)
			.build()

		// El menú de degradación entra aquí: veta decoders por nombre y, si se
		// pide, descarta los de hardware para ejercitar el camino de respaldo.
		val renderersFactory = DefaultRenderersFactory(context)
			.setEnableDecoderFallback(true)
			.setMediaCodecSelector(DegradableCodecSelector())

		val targetOffsetMs = dev.targetOffsetOverrideMs ?: plan.targetOffsetMs
		activeTargetOffsetMs = targetOffsetMs

		val mediaItem = MediaItem.Builder()
			.setUri(channel.url)
			.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
			.setLiveConfiguration(
				MediaItem.LiveConfiguration.Builder()
					.setTargetOffsetMs(targetOffsetMs)
					.build()
			)
			.build()

		player = ExoPlayer.Builder(context, renderersFactory)
			.setBandwidthMeter(bandwidthMeter)
			.setLoadControl(loadControl)
			.setMediaSourceFactory(mediaSourceFactory)
			.build()
			.also { exo ->
				exo.addListener(PlayerEvents())
				exo.addAnalyticsListener(DecoderEvents())
				surface?.let(exo::setVideoSurfaceView)
				exo.setMediaItem(mediaItem)
				exo.prepare()
			}
	}

	override fun play() {
		player?.playWhenReady = true
		LiveLog.debug("engine.play")
	}

	override fun stop() {
		player?.stop()
		LiveLog.info("engine.stop")
	}

	override fun release() {
		player?.release()
		player = null
		videoDecoder = null
		audioDecoder = null
		droppedFrames = 0
		windowDurationMs = C.TIME_UNSET
	}

	override fun selectAudioTrack(track: AudioTrackRef) {
		// M3. La selección va por idioma normalizado, no por PID (CHANNELS.md §2.bis).
		LiveLog.warn("engine.selectAudioTrack.notImplemented", "language" to track.language, "pid" to track.pid)
	}

	override fun setAspectMode(mode: AspectMode) {
		// M5: el modo de ajuste lo aplica la vista, no el player.
		LiveLog.debug("engine.setAspectMode", "mode" to mode)
	}

	override fun addListener(listener: EngineListener) { listeners.add(listener) }
	override fun removeListener(listener: EngineListener) { listeners.remove(listener) }

	override fun stats(): EngineStats {
		val exo = player

		// El avance de la posición es la señal fiable de que sigue saliendo
		// imagen. Los callbacks de frame de Media3 no dan un latido regular, y
		// apoyarse en ellos producía un reloj que medía desde el arranque —
		// exactamente el dato que el watchdog de M7 no puede permitirse falso.
		val pos = exo?.currentPosition ?: C.TIME_UNSET
		val ahora = System.currentTimeMillis()
		if (pos != lastPositionMs) {
			lastPositionMs = pos
			lastProgressAtMs = ahora
		}

		val segundos = (ahora - loadingSinceMs).coerceAtLeast(1L) / 1000.0

		return EngineStats(
			engineId = id,
			activeVideoDecoder = videoDecoder,
			activeAudioDecoder = audioDecoder,
			bufferedMs = exo?.totalBufferedDuration ?: 0,
			// El TS no declara bitrate en el `Format`, así que se informa la
			// estimación de ancho de banda: además es el dato útil en vivo.
			bitrateBps = if (bytesLoaded > 0) (bytesLoaded * 8 / segundos).toLong() else 0,
			bandwidthBps = bandwidthMeter.bitrateEstimate,
			droppedFrames = droppedFrames,
			msSinceProgress = ahora - lastProgressAtMs,
			liveOffsetMs = exo?.currentLiveOffset?.takeIf { it != C.TIME_UNSET } ?: 0,
			windowMs = windowDurationMs.takeIf { it != C.TIME_UNSET } ?: 0,
			targetOffsetMs = activeTargetOffsetMs,
			reason = plan?.reason,
			degradation = dev.describe(),
		)
	}

	private fun notify(block: (EngineListener) -> Unit) = listeners.forEach(block)

	// -- Eventos del player ---------------------------------------------------

	private inner class PlayerEvents : Player.Listener {
		override fun onPlaybackStateChanged(state: Int) {
			when (state) {
				Player.STATE_READY -> { LiveLog.info("player.ready"); notify { it.onReady() } }
				Player.STATE_BUFFERING -> { LiveLog.debug("player.buffering"); notify { it.onBuffering() } }
				Player.STATE_ENDED -> LiveLog.warn("player.ended")
				Player.STATE_IDLE -> LiveLog.debug("player.idle")
			}
		}

		override fun onIsPlayingChanged(isPlaying: Boolean) {
			if (isPlaying) {
				LiveLog.info("player.playing", "liveOffsetMs" to player?.currentLiveOffset)
				notify { it.onPlaying() }
			}
		}

		override fun onVideoSizeChanged(videoSize: VideoSize) {
			LiveLog.info(
				"player.videoSize",
				"width" to videoSize.width,
				"height" to videoSize.height,
				"sar" to videoSize.pixelWidthHeightRatio,
			)
			notify { it.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio) }
		}

		override fun onTimelineChanged(timeline: Timeline, reason: Int) {
			if (timeline.windowCount == 0) return
			val window = timeline.getWindow(0, Timeline.Window())
			val durationMs = window.durationMs
			if (durationMs == C.TIME_UNSET || durationMs <= 0) return
			windowDurationMs = durationMs

			// La ventana DVR es lo que gobierna el offset. Un offset mayor que la
			// mitad de la ventana acaba en BEHIND_LIVE_WINDOW en cuanto hay un
			// hipo de red. Medido el 2026-09-22 en hbohd-70w: ver CHANNELS.md §2.bis.
			val target = plan?.targetOffsetMs ?: return
			val ceiling = (durationMs * LiveTuning.TARGET_OFFSET_MAX_WINDOW_FRACTION).toLong()
			if (target > ceiling) {
				LiveLog.warn(
					"player.targetOffsetExceedsWindow",
					"targetOffsetMs" to target,
					"windowMs" to durationMs,
					"ceilingMs" to ceiling,
				)
			}
		}

		override fun onPlayerError(error: PlaybackException) {
			// Prohibido cerrar el player ante un error de red (CLAUDE.md §6).
			when (error.errorCode) {
				PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
					LiveLog.warn(
						"player.behindLiveWindow",
						"windowMs" to windowDurationMs,
						"targetOffsetMs" to plan?.targetOffsetMs,
					)
					player?.seekToDefaultPosition()
					player?.prepare()
					notify { it.onRecoverableError("behindLiveWindow", error) }
				}

				else -> {
					LiveLog.error(
						"player.error",
						error,
						"errorCode" to error.errorCodeName,
						"videoDecoder" to videoDecoder,
						"bufferedMs" to player?.totalBufferedDuration,
					)
					// La escalera de recuperación completa es M7. Aquí, lo mínimo
					// honesto: reintentar sin soltar el player.
					player?.prepare()
					notify { it.onRecoverableError(error.errorCodeName, error) }
				}
			}
		}
	}

	private inner class DecoderEvents : AnalyticsListener {
		override fun onVideoDecoderInitialized(
			eventTime: AnalyticsListener.EventTime,
			decoderName: String,
			initializedTimestampMs: Long,
			initializationDurationMs: Long,
		) {
			videoDecoder = decoderName
			LiveLog.info("decoder.video", "name" to decoderName, "initMs" to initializationDurationMs)
		}

		override fun onAudioDecoderInitialized(
			eventTime: AnalyticsListener.EventTime,
			decoderName: String,
			initializedTimestampMs: Long,
			initializationDurationMs: Long,
		) {
			audioDecoder = decoderName
			LiveLog.info("decoder.audio", "name" to decoderName, "initMs" to initializationDurationMs)
		}

		override fun onDroppedVideoFrames(
			eventTime: AnalyticsListener.EventTime,
			droppedFrames: Int,
			elapsedMs: Long,
		) {
			this@Media3LiveEngine.droppedFrames += droppedFrames
			LiveLog.warn("frames.dropped", "count" to droppedFrames, "total" to this@Media3LiveEngine.droppedFrames)
		}

		override fun onRenderedFirstFrame(
			eventTime: AnalyticsListener.EventTime,
			output: Any,
			renderTimeMs: Long,
		) {
			LiveLog.info("frames.first", "renderTimeMs" to renderTimeMs)
		}

		/** Bytes reales descargados: es de donde sale el bitrate del canal. */
		override fun onLoadCompleted(
			eventTime: AnalyticsListener.EventTime,
			loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
			mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
		) {
			bytesLoaded += loadEventInfo.bytesLoaded
		}
	}

	/**
	 * Política de error propia: backoff exponencial y reintentos efectivamente
	 * infinitos (CLAUDE.md §6).
	 *
	 * El caso que la justifica está medido: cuando la fuente cae, Flussonic
	 * devuelve un **playlist vacío**, no un 404, y deja las peticiones colgadas.
	 * Tratar eso como error terminal dejaría al suscriptor con la pantalla
	 * muerta ante un chubasco de treinta segundos.
	 */
	private inner class LiveErrorPolicy : DefaultLoadErrorHandlingPolicy() {
		override fun getMinimumLoadableRetryCount(dataType: Int): Int = LiveTuning.MAX_RETRIES

		override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
			val attempt = loadErrorInfo.errorCount.coerceAtMost(MAX_BACKOFF_EXPONENT)
			val delay = min(
				LiveTuning.RETRY_BACKOFF_BASE_MS * 2.0.pow(attempt.toDouble()).toLong(),
				LiveTuning.RETRY_BACKOFF_MAX_MS,
			)
			LiveLog.warn(
				"load.retry",
				"attempt" to loadErrorInfo.errorCount,
				"delayMs" to delay,
				"cause" to loadErrorInfo.exception::class.java.simpleName,
			)
			return delay
		}
	}

	/**
	 * Selector de decoders que obedece al menú de degradación.
	 *
	 * Es la pieza que permite simular cajas peores. Sin ella, la caja de
	 * pruebas —que trae MPEG-2 por hardware— nunca recorrería los pasos bajos
	 * de la cascada, y esos pasos llegarían a producción sin probarse.
	 */
	private inner class DegradableCodecSelector : MediaCodecSelector {
		override fun getDecoderInfos(
			mimeType: String,
			requiresSecureDecoder: Boolean,
			requiresTunnelingDecoder: Boolean,
		): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
			val all = MediaCodecSelector.DEFAULT
				.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)

			val isVideo = mimeType.startsWith("video/")
			val kept = all.filterNot { info ->
				val vetado = dev.isVetoed(info.name)
				val porSoftware = isVideo && dev.forceSoftwareVideo && info.hardwareAccelerated
				vetado || porSoftware
			}

			if (kept.size != all.size) {
				LiveLog.warn(
					"codec.descartados",
					"mime" to mimeType,
					"antes" to all.map { it.name },
					"despues" to kept.map { it.name },
				)
			}

			if (kept.isEmpty() && all.isNotEmpty()) {
				// Que se vea por qué el canal va a fallar, en vez de una
				// pantalla negra sin explicación (CLAUDE.md regla 8).
				LiveLog.error(
					"codec.sinCandidatos",
					null,
					"mime" to mimeType,
					"degradacion" to dev.describe(),
				)
			}

			return kept
		}
	}

	private companion object {
		const val ENGINE_ID = "media3"
		const val MAX_BACKOFF_EXPONENT = 4
	}
}
