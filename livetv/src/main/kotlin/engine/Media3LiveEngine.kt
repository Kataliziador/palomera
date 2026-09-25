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
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
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
import org.jellyfin.livetv.source.LanguageNormalizer
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

	// Métricas de los objetivos de CLAUDE.md §10
	private var prepareAtMs = 0L
	private var firstFrameAtMs = 0L
	private var msToFirstFrame = 0L
	private var rebufferCount = 0
	private var bufferingSinceMs = 0L
	private var bufferingTotalMs = 0L
	private var loadRetries = 0

	// Render, para M5
	private var videoWidth = 0
	private var videoHeight = 0
	private var pixelAspectRatio = 1f
	private var renderedFrames = 0L
	private var fpsSampleAtMs = 0L
	private var fpsSampleFrames = 0L
	private var fpsActual = 0f
	private var lastReleaseNs = 0L
	private var maxReleaseGapMs = 0L
	private var releaseGapCount = 0

	// Audio, para M3
	private var audioTracks: List<AudioTrackRef> = emptyList()
	private var selectedAudio: AudioTrackRef? = null
	private var windowDurationMs = C.TIME_UNSET
	private var activeTargetOffsetMs = 0L
	private var offsetMismatchReported = false
	private var lastResyncAtMs = 0L
	private var resyncCount = 0

	override fun attach(surface: SurfaceView) {
		this.surface = surface
		player?.setVideoSurfaceView(surface)
		LiveLog.debug("engine.attach")
	}

	override fun prepare(channel: ChannelProfile, plan: PlaybackPlan) {
		this.channel = channel
		this.plan = plan

		val stuckCoefficient = dev.playlistStuckCoefficient

		LiveLog.startSession(channel.id, channel.url, channel.videoCodec, channel.audioCodec)
		LiveLog.info(
			"engine.prepare",
			"engine" to id,
			"videoPath" to plan.videoPath,
			"targetOffsetMs" to (dev.targetOffsetOverrideMs ?: plan.targetOffsetMs),
			"reason" to plan.reason,
			"playlistStuckCoef" to stuckCoefficient,
			"degradacion" to dev.describe(),
		)

		release()
		val inicio = System.currentTimeMillis()
		bytesLoaded = 0
		loadingSinceMs = inicio
		lastPositionMs = C.TIME_UNSET
		lastProgressAtMs = inicio
		offsetMismatchReported = false
		lastResyncAtMs = 0
		resyncCount = 0
		prepareAtMs = inicio
		firstFrameAtMs = 0
		msToFirstFrame = 0
		rebufferCount = 0
		bufferingSinceMs = 0
		bufferingTotalMs = 0
		loadRetries = 0
		renderedFrames = 0
		fpsSampleAtMs = 0
		fpsSampleFrames = 0
		fpsActual = 0f
		lastReleaseNs = 0
		maxReleaseGapMs = 0
		releaseGapCount = 0
		audioTracks = emptyList()
		selectedAudio = null

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

		// Umbral de playlist detenido. Con el valor de Media3 (3.5) el error
		// salta a los ~14 s, justo antes de que Flussonic termine de
		// reconstruir el playlist tras una caída de fuente (~15 s medidos).
		// Es decir: el defecto convierte un chubasco en un error de
		// reproducción, que es lo que CLAUDE.md §6 prohíbe.
		val mediaSourceFactory = HlsMediaSource.Factory(httpFactory)
			.setLoadErrorHandlingPolicy(LiveErrorPolicy())
			.setExtractorFactory(DefaultHlsExtractorFactory(tsFlags, false))
			.setPlaylistTrackerFactory(
				HlsPlaylistTracker.Factory { dataSourceFactory, loadErrorPolicy, parserFactory, cmcd ->
					DefaultHlsPlaylistTracker(
						dataSourceFactory,
						loadErrorPolicy,
						parserFactory,
						cmcd,
						stuckCoefficient,
					)
				}
			)
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
				// `onVideoFrameProcessingOffset` no da un latido fiable; este
				// listener sí dispara una vez por frame presentado.
				exo.setVideoFrameMetadataListener { presentationUs, releaseNs, _, _ ->
					renderedFrames++
					registrarEntregaDeFrame(presentationUs, releaseNs)
				}
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

			msToFirstFrame = msToFirstFrame,
			rebufferCount = rebufferCount,
			rebufferRatio = rebufferRatio(ahora),
			sessionUptimeMs = if (prepareAtMs == 0L) 0 else ahora - prepareAtMs,
			loadRetries = loadRetries,

			videoWidth = videoWidth,
			videoHeight = videoHeight,
			pixelAspectRatio = pixelAspectRatio,
			measuredFps = measuredFps(ahora),
			frameGapCount = releaseGapCount,
			maxFrameGapMs = maxReleaseGapMs,

			audioTracks = audioTracks,
			selectedAudio = selectedAudio,
		)
	}

	/**
	 * Mide **cuándo sale cada frame a pantalla**, no cuándo se decodifica.
	 *
	 * Es lo único que delata un tirón cuando el stream está limpio, el refresco
	 * es el correcto y los contadores de rebuffer y frames perdidos están a
	 * cero — que es exactamente el caso de `hbohd`. Un hueco de entrega no es
	 * un frame perdido: el frame llega, pero tarde.
	 */
	private fun registrarEntregaDeFrame(presentationUs: Long, releaseNs: Long) {
		val anterior = lastReleaseNs
		lastReleaseNs = releaseNs
		if (anterior == 0L) return

		val huecoMs = (releaseNs - anterior) / 1_000_000
		if (huecoMs > maxReleaseGapMs) maxReleaseGapMs = huecoMs

		// 50 ms es el intervalo legítimo más largo de una cadencia 3:2. Por
		// encima del doble, es un tirón de verdad.
		if (huecoMs > FRAME_GAP_WARN_MS) {
			releaseGapCount++
			LiveLog.warn(
				"frames.entregaTardia",
				"huecoMs" to huecoMs,
				"presentacionS" to "%.2f".format(presentationUs / 1_000_000.0),
				"total" to releaseGapCount,
			)
		}
	}

	/** Fracción del tiempo con imagen que se pasó almacenando. Objetivo: < 0.3 %. */
	private fun rebufferRatio(ahora: Long): Float {
		if (firstFrameAtMs == 0L) return 0f
		val conImagen = (ahora - firstFrameAtMs).coerceAtLeast(1L)
		val almacenando = bufferingTotalMs +
			if (bufferingSinceMs > 0L) ahora - bufferingSinceMs else 0L
		return (almacenando.toFloat() / conImagen).coerceIn(0f, 1f)
	}

	/**
	 * fps instantáneo, sobre una ventana deslizante.
	 *
	 * La media acumulada desde el primer frame engaña: tras un rebuffer arrastra
	 * el hueco para siempre y nunca vuelve al valor real. Con ventana corta se
	 * ve lo que está pasando ahora, que es lo que sirve para juzgar si un canal
	 * va a 29.97 o a 59.94 — la disputa abierta de `estrellashd`.
	 */
	private fun measuredFps(ahora: Long): Float {
		if (firstFrameAtMs == 0L) return 0f
		if (fpsSampleAtMs == 0L) {
			fpsSampleAtMs = ahora
			fpsSampleFrames = renderedFrames
			return 0f
		}
		val transcurrido = ahora - fpsSampleAtMs
		if (transcurrido >= FPS_WINDOW_MS) {
			fpsActual = (renderedFrames - fpsSampleFrames) * 1000f / transcurrido
			fpsSampleAtMs = ahora
			fpsSampleFrames = renderedFrames
		}
		return fpsActual
	}

	private fun notify(block: (EngineListener) -> Unit) = listeners.forEach(block)

	// -- Eventos del player ---------------------------------------------------

	private inner class PlayerEvents : Player.Listener {
		override fun onPlaybackStateChanged(state: Int) {
			when (state) {
				Player.STATE_READY -> {
					if (bufferingSinceMs > 0L) {
						bufferingTotalMs += System.currentTimeMillis() - bufferingSinceMs
						bufferingSinceMs = 0L
					}
					LiveLog.info("player.ready")
					notify { it.onReady() }
				}
				Player.STATE_BUFFERING -> {
					// Solo cuenta como rebuffer si ya había imagen: el
					// almacenamiento inicial es arranque, no interrupción.
					if (firstFrameAtMs > 0L && bufferingSinceMs == 0L) {
						bufferingSinceMs = System.currentTimeMillis()
						rebufferCount++
						LiveLog.warn("player.rebuffer", "numero" to rebufferCount)
					}
					LiveLog.debug("player.buffering")
					notify { it.onBuffering() }
				}
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
			videoWidth = videoSize.width
			videoHeight = videoSize.height
			pixelAspectRatio = videoSize.pixelWidthHeightRatio
			notify { it.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio) }
		}

		override fun onTimelineChanged(timeline: Timeline, reason: Int) {
			if (timeline.windowCount == 0) return
			val window = timeline.getWindow(0, Timeline.Window())
			val durationMs = window.durationMs
			if (durationMs == C.TIME_UNSET || durationMs <= 0) return
			windowDurationMs = durationMs

			// El offset tiene un techo y un suelo, y el que manda es el suelo.
			//
			// Techo: más de la mitad de la ventana acaba en BEHIND_LIVE_WINDOW
			// en cuanto hay un hipo de red (medido en hbohd, CHANNELS.md §2.bis).
			//
			// Suelo: sin `EXT-X-SERVER-CONTROL` la especificación HLS fija el
			// margen seguro en 3 × TARGETDURATION y Media3 no baja de ahí. Lo
			// pedido puede ser sencillamente inalcanzable, y callarlo haría que
			// alguien perdiera un día preguntándose por qué el config.json no
			// hace nada.
			val target = activeTargetOffsetMs
			val ceiling = (durationMs * LiveTuning.TARGET_OFFSET_MAX_WINDOW_FRACTION).toLong()
			if (target > ceiling) {
				LiveLog.warn(
					"offset.superaLaVentana",
					"pedidoMs" to target,
					"ventanaMs" to durationMs,
					"techoMs" to ceiling,
				)
			}

			val real = player?.currentLiveOffset ?: C.TIME_UNSET

			// Vuelta al borde de emisión.
			//
			// Tras un corte, el reproductor retoma donde estaba y no donde está
			// el directo, así que el offset crece y **no vuelve solo**. Cada
			// corte se come margen hasta que la posición cae fuera de la
			// ventana. Reaccionar a BEHIND_LIVE_WINDOW cuando ya pasó llega
			// tarde: hay que volver antes de llegar ahí.
			// Tres guardas, y las tres se ganaron a base de romper algo:
			//
			// 1. **Solo con imagen ya en pantalla.** Durante el arranque el
			//    offset lee alto, dispara un salto, el salto reinicia el
			//    almacenamiento y el offset vuelve a leer alto: un bucle que
			//    impide arrancar. Costó que `hbohd` tardara cuatro minutos en
			//    dar el primer frame.
			// 2. **Solo reproduciendo.** Saltar mientras se almacena no
			//    adelanta nada y reinicia el trabajo hecho.
			// 3. **Con enfriamiento.** Un salto tarda en surtir efecto; medir
			//    otra vez antes de que se note lleva a encadenar saltos.
			val ahoraMs = System.currentTimeMillis()
			val puedeResincronizar = !dev.liveEdgeResyncDisabled &&
				firstFrameAtMs > 0L &&
				player?.playbackState == Player.STATE_READY &&
				ahoraMs - lastResyncAtMs > LiveTuning.LIVE_EDGE_RESYNC_COOLDOWN_MS

			val limite = (durationMs * LiveTuning.LIVE_EDGE_RESYNC_WINDOW_FRACTION).toLong()
			if (puedeResincronizar && real != C.TIME_UNSET &&
				real - limite > LiveTuning.LIVE_EDGE_RESYNC_MIN_EXCESS_MS
			) {
				lastResyncAtMs = ahoraMs
				resyncCount++
				LiveLog.warn(
					"offset.vuelveAlBorde",
					"offsetMs" to real,
					"ventanaMs" to durationMs,
					"limiteMs" to limite,
					"numero" to resyncCount,
				)
				player?.seekToDefaultPosition()
				return
			}

			if (real != C.TIME_UNSET && !offsetMismatchReported &&
				real - target > OFFSET_MISMATCH_TOLERANCE_MS
			) {
				offsetMismatchReported = true
				LiveLog.warn(
					"offset.noSeAplica",
					"pedidoMs" to target,
					"realMs" to real,
					"causa" to "sin EXT-X-SERVER-CONTROL el suelo es 3xTARGETDURATION",
				)
			}
		}

		/**
		 * Inventario de pistas de audio.
		 *
		 * Además de servir a la selección por idioma de M3, hace **visible** el
		 * audio intermitente de `estrellashd`: si la pista desaparece a mitad
		 * de emisión, aquí se ve desaparecer en vez de deducirlo del silencio.
		 */
		override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
			val encontradas = mutableListOf<AudioTrackRef>()
			var seleccionada: AudioTrackRef? = null

			tracks.groups
				.filter { it.type == C.TRACK_TYPE_AUDIO }
				.forEach { group ->
					for (i in 0 until group.length) {
						val f = group.getTrackFormat(i)
						// El `id` que expone Media3 en HLS es un índice de pista,
						// no el PID del TS. Enseñarlo como PID sería inventarse
						// un dato — y además el PID no identifica nada, porque
						// Flussonic los renumera (CHANNELS.md §2.bis). Solo se
						// acepta si cae en el rango válido de PID.
						val posiblePid = f.id?.substringAfterLast('/')?.toIntOrNull()
						val ref = AudioTrackRef(
							language = LanguageNormalizer.normalize(f.language),
							pid = posiblePid?.takeIf { it in 0x0010..0x1FFE } ?: -1,
							channels = f.channelCount,
							label = f.label ?: f.language,
						)
						encontradas += ref
						if (group.isTrackSelected(i)) seleccionada = ref
					}
				}

			if (encontradas.map { it.language } != audioTracks.map { it.language }) {
				LiveLog.info(
					"audio.pistas",
					"cuantas" to encontradas.size,
					"idiomas" to encontradas.mapNotNull { it.language },
				)
			}

			audioTracks = encontradas
			selectedAudio = seleccionada
			notify { it.onAudioTracksChanged(encontradas) }
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
			if (firstFrameAtMs == 0L) {
				firstFrameAtMs = System.currentTimeMillis()
				msToFirstFrame = firstFrameAtMs - prepareAtMs
				LiveLog.info("frames.first", "msDesdePrepare" to msToFirstFrame)
			}
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
			loadRetries++
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
		const val FPS_WINDOW_MS = 2_000L
		/** Por debajo de esto, la diferencia es ruido de convergencia. */
		const val OFFSET_MISMATCH_TOLERANCE_MS = 2_000L
		/** El doble del intervalo legítimo más largo de una cadencia 3:2. */
		const val FRAME_GAP_WARN_MS = 100L
	}
}
