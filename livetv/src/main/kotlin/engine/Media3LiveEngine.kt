package org.jellyfin.livetv.engine

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlin.math.pow
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.DeviceLoad
import org.jellyfin.livetv.diagnostics.Diagnostico
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.source.AudioTrackRef
import org.jellyfin.livetv.source.ChannelProfile
import org.jellyfin.livetv.source.LanguageNormalizer

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
	private var trackSelector: DefaultTrackSelector? = null
	private var surface: SurfaceView? = null
	private var channel: ChannelProfile? = null
	private var plan: PlaybackPlan? = null

	/** El aviso de audio sin normalizar se da una vez por sintonía, no por evento. */
	private var audioYaRevisado = false

	/**
	 * El códec de audio recibido cuando **no** era AAC, para la telemetría.
	 *
	 * Se guarda aquí y se entrega al cerrar la sesión en vez de informarlo en
	 * el momento, porque `Diagnostico` cuenta por sesión y no por evento.
	 */
	private var audioSinNormalizar: String? = null

	/**
	 * El idioma por defecto se aplica **una vez por sintonía**.
	 *
	 * Si se reaplicara en cada `onTracksChanged`, a quien cambiara el audio a
	 * inglés a mano se lo devolveríamos a español en cuanto el canal cambiara
	 * de pistas —que en este lineup pasa al cambiar de programa—. Elegir por él
	 * una vez es ayuda; hacerlo dos es pelearse con él.
	 */
	private var audioPorDefectoAplicado = false

	/** El aviso de «no está la pista pedida» se da una vez, el intento no. */
	private var audioPorDefectoAvisado = false

	/** Un master con varias calidades se avisa una vez, no en cada evento. */
	private var abrYaAvisado = false

	private val listeners = CopyOnWriteArrayList<EngineListener>()
	private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

	// --- Latido de diagnóstico ---------------------------------------------
	//
	// El log registra bien lo que se anuncia solo: rebuffers, errores, huecos
	// de entrega. Un tramo sano no deja rastro, y entonces «vi un corte a los
	// doce minutos» no se puede situar en nada. El latido escribe una línea
	// cada dos segundos para que exista esa línea de tiempo.
	//
	// El estado del player se lee en su propio hilo —ExoPlayer no es seguro
	// fuera de él— y el muestreo de `/proc` se hace aparte, para no pagarlo en
	// el hilo que suelta los frames.

	private val carga = DeviceLoad(context)
	/**
	 * `prepare()` construyó el reproductor y **dejó para después** el
	 * `exo.prepare()`, esperando la superficie (pendiente 68).
	 */
	private var esperandoSuperficie = false

	private val latido = Handler(Looper.getMainLooper())
	private var muestreo: HandlerThread? = null
	private var muestreoHandler: Handler? = null

	@Volatile private var ultimaCarga: DeviceLoad.Muestra? = null
	private var latidos = 0L
	private var framesEnElLatidoAnterior = 0L
	private var huecosEnElLatidoAnterior = 0

	// --- Vigilante de recuperación -----------------------------------------
	//
	// El 30-sep la fuente calló 40 s. El cliente se bebió su buffer, quedó 45 s
	// atrás, y a partir de ahí pidió segmentos que el servidor ya había
	// borrado: 404 eternos y siete horas y cuarto de imagen congelada **con el
	// buffer lleno**. La vuelta al borde de emisión, que existe justo para
	// esto, no llegó a dispararse ni una vez porque está condicionada a
	// `STATE_READY` — y el reproductor nunca salió de `STATE_BUFFERING`.
	//
	// Esa guarda es correcta para un rebuffer de dos segundos y exactamente
	// contraria para uno de siete horas. Lo que faltaba era distinguir por
	// duración, y eso necesita un reloj: el latido.

	/**
	 * Generación del latido.
	 *
	 * El escalón 3 del vigilante reconstruye el reproductor **desde dentro del
	 * propio latido**: al volver, el `Runnable` viejo se re-encolaba además del
	 * nuevo que arranca `play()`, y cada reconstrucción añadía otra copia.
	 * Medido el 01-oct: los latidos salían por duplicado en el log.
	 */
	private var generacionLatido = 0

	private var atascadoDesdeMs = 0L
	private var ultimoEscalonMs = 0L
	private var escalon = 0
	private var ciclosSinExito = 0
	private var recuperaciones = 0

	// Estado observado, para el overlay de stats
	private var videoDecoder: String? = null
	private var audioDecoder: String? = null
	private var droppedFrames = 0
	private var bytesLoaded = 0L
	private var loadingSinceMs = 0L
	/**
	 * El avance de la posición, que es de lo que vive el vigilante.
	 *
	 * Va en su propia clase porque la definición de «avanzar» tiene miga y se
	 * prueba sin emulador: ver [RelojDeAvance].
	 */
	private val avance = RelojDeAvance()

	/**
	 * Si la posición aguanta donde está. Pendiente 75, y se mira **aquí y no en
	 * `onTimelineChanged`** porque con la lista del origen congelada ese método
	 * no se llama: medido, 38 segundos de bucle sin una sola evaluación.
	 */
	private val margen = MargenDeBuffer()

	// Métricas de los objetivos de CLAUDE.md §10
	private var prepareAtMs = 0L
	private var firstFrameAtMs = 0L

	/**
	 * El tunneled **efectivo de esta sesión**, que no siempre es el ajustado.
	 *
	 * El rescate puede apagarlo a mitad de sintonía al demostrarse que no da
	 * imagen, y a partir de ahí es esto —no `dev.tunnelingEnabled`— lo que
	 * describe cómo se está reproduciendo de verdad.
	 */
	private var tunnelingSesion = false

	/** El rescate se hace una vez por sesión: si no bastó, no bastará repetirlo. */
	private var rescateTunneledHecho = false

	/** La última causa de fallo de esta sesión, para el diagnóstico de B6. */
	private var ultimaCausa: CausaDeFallo? = null
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

	/**
	 * De la identidad de pista al grupo real de Media3.
	 *
	 * La clave es el idioma normalizado, con el PID de desempate (CLAUDE.md
	 * regla 6): nunca un índice, porque el índice cambia entre sesiones y
	 * entre segmentos, y seleccionar por índice acaba poniendo inglés donde el
	 * abonado pidió español.
	 */
	private var gruposDeAudio: Map<String, Pair<androidx.media3.common.TrackGroup, Int>> = emptyMap()
	private var subtitleTracks: List<AudioTrackRef> = emptyList()
	private var gruposDeSubtitulo: Map<String, Pair<androidx.media3.common.TrackGroup, Int>> = emptyMap()
	private var selectedSubtitle: AudioTrackRef? = null
	private var windowDurationMs = C.TIME_UNSET
	private var activeTargetOffsetMs = 0L
	private var offsetMismatchReported = false
	private var lastResyncAtMs = 0L
	private var resyncCount = 0
	private var resyncDeclinadas = 0

	/** Cuántas veces se ha alejado la posición del borde. Pendiente 75. */
	private var margenCorto = 0
	private var sinSitioAvisado = false

	/** Cuántas veces ha llegado un playlist nuevo. Pendiente 76. */
	private var cambiosDeTimeline = 0

	/** Para no repetir el mismo `offset.noSeEvalua` en cada playlist. */
	private var ultimoMotivoSinEvaluar: String? = null
	private var ultimoNoSeEvaluaAtMs = 0L

	override fun attach(surface: SurfaceView) {
		this.surface = surface
		player?.setVideoSurfaceView(surface)
		LiveLog.debug("engine.attach")

		// Lo que `prepare()` dejó pendiente. Entregar la superficie **antes**
		// de preparar es lo que evita el error de cada sintonía: Media3 la
		// acepta en caliente, pero reconfigurar el renderer de vídeo con la
		// reproducción ya pedida cuesta un `FAILED_RUNTIME_CHECK` y una
		// preparación entera de más.
		if (esperandoSuperficie) {
			esperandoSuperficie = false
			latido.removeCallbacks(prepararSinSuperficie)
			LiveLog.debug("engine.preparaTrasSuperficie")
			player?.prepare()
		}
	}

	/**
	 * La red por si la superficie no llega nunca: se prepara igual.
	 *
	 * Sin esto, un cambio en la interfaz que dejara de entregar la superficie
	 * daría pantalla negra permanente en vez del arranque de siempre, y nadie
	 * lo relacionaría con esta espera.
	 */
	private val prepararSinSuperficie = Runnable {
		if (!esperandoSuperficie) return@Runnable
		esperandoSuperficie = false
		LiveLog.warn("engine.superficieNoLlego", "esperaMs" to LiveTuning.ESPERA_SUPERFICIE_MS)
		player?.prepare()
	}

	override fun prepare(channel: ChannelProfile, plan: PlaybackPlan) {
		this.channel = channel
		this.plan = plan

		val stuckCoefficient = dev.playlistStuckCoefficient
		tunnelingSesion = dev.tunnelingEnabled

		LiveLog.startSession(channel.id, channel.url, channel.videoCodec, channel.audioCodec)
		LiveLog.info(
			"engine.prepare",
			"engine" to id,
			"videoPath" to plan.videoPath,
			"targetOffsetMs" to (dev.targetOffsetOverrideMs ?: plan.targetOffsetMs),
			"reason" to plan.reason,
			"playlistStuckCoef" to stuckCoefficient,
			"tunneled" to tunnelingSesion,
			"tsSearchPaquetes" to dev.timestampSearchPackets,
			"degradacion" to dev.describe(),
		)

		release()
		val inicio = System.currentTimeMillis()
		bytesLoaded = 0
		loadingSinceMs = inicio
		avance.reiniciar(inicio)
		offsetMismatchReported = false
		lastResyncAtMs = 0
		resyncCount = 0
		resyncDeclinadas = 0
		margenCorto = 0
		sinSitioAvisado = false
		// `cambiosDeTimeline` se reinicia con la sesión a propósito: lo que se
		// quiere leer en el latido es «cuántos playlists ha visto **esta**
		// sintonía», no un acumulado de la app.
		cambiosDeTimeline = 0
		margen.reiniciar()
		ultimoMotivoSinEvaluar = null
		ultimoNoSeEvaluaAtMs = 0L
		prepareAtMs = inicio
		firstFrameAtMs = 0
		rescateTunneledHecho = false
		ultimaCausa = null
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
		audioYaRevisado = false
		audioSinNormalizar = null
		audioPorDefectoAplicado = false
		audioPorDefectoAvisado = false
		abrYaAvisado = false

		val httpFactory = DefaultHttpDataSource.Factory()
			.setConnectTimeoutMs(LiveTuning.CONNECT_TIMEOUT_MS)
			.setReadTimeoutMs(LiveTuning.READ_TIMEOUT_MS)
			.setAllowCrossProtocolRedirects(true)

		// En HLS los flags del extractor **no** se heredan de
		// `DefaultMediaSourceFactory`: hay que pasarlos por aquí
		// (LIVETV_ENGINE.md §5). Vienen **en el plan** y no se leen del menú
		// aquí: el motor reproduce lo que la cascada decidió, y así el motivo
		// que se registra describe de verdad cómo se está reproduciendo.
		val tsFlags = plan.tsFlags

		// Umbral de playlist detenido. Con el valor de Media3 (3.5) el error
		// salta a los ~14 s, justo antes de que Flussonic termine de
		// reconstruir el playlist tras una caída de fuente (~15 s medidos).
		// Es decir: el defecto convierte un chubasco en un error de
		// reproducción, que es lo que CLAUDE.md §6 prohíbe.
		val mediaSourceFactory = HlsMediaSource.Factory(httpFactory)
			.setLoadErrorHandlingPolicy(LiveErrorPolicy())
			.setExtractorFactory(
				if (dev.wideTimestampSearch) {
					WideSearchHlsExtractorFactory(tsFlags, dev.timestampSearchPackets)
				} else {
					// `true`: los subtítulos de este lineup van **incrustados en
					// el vídeo** como CEA-608 y el flujo no los declara. Sin
					// esto Media3 no los expone y el canal parece no tenerlos.
					// Medido el 01-oct en `estrellashd`: 22 entradas con
					// diálogo real en 40 s.
					DefaultHlsExtractorFactory(tsFlags, true)
				}
			)
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

		// La reproducción tunneled se pide por el selector de pistas: solo se
		// elegirán decoders capaces de entregar directamente al hardware de
		// pantalla.
		val selector = DefaultTrackSelector(context).apply {
			setParameters(buildUponParameters().setTunnelingEnabled(tunnelingSesion))
		}
		trackSelector = selector

		player = ExoPlayer.Builder(context, renderersFactory)
			.setBandwidthMeter(bandwidthMeter)
			.setTrackSelector(selector)
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

				// En la primera sintonía la superficie todavía no existe: la
				// crea la vista de Compose unos milisegundos después. Preparar
				// ahora y aceptarla en caliente es lo que deja el error del
				// pendiente 68 en cada canal.
				if (dev.prepararConSuperficie && surface == null) {
					esperandoSuperficie = true
					LiveLog.debug("engine.esperaSuperficie")
					latido.postDelayed(prepararSinSuperficie, LiveTuning.ESPERA_SUPERFICIE_MS)
				} else {
					exo.prepare()
				}
			}
	}

	override fun play() {
		player?.playWhenReady = true
		LiveLog.debug("engine.play")
		arrancarLatido()
	}

	override fun stop() {
		player?.stop()
		LiveLog.info("engine.stop")
	}

	override fun release() {
		esperandoSuperficie = false
		latido.removeCallbacks(prepararSinSuperficie)
		// Se registra porque una fuga de reproductores sería invisible sin esto:
		// un `ExoPlayer` por zapeo que nadie suelta no da error, solo se come la
		// memoria y pelea por recursos del decodificador.
		//
		// **Salen dos por sintonía y eso es correcto:** uno lo hace Compose al
		// soltar el motor anterior (`habiaPlayer=true`) y el otro lo hace
		// `prepare()` antes de construir el nuevo (`habiaPlayer=false`). Lo que
		// delataría una fuga es un `prepare` **sin** su `release` previo con
		// `habiaPlayer=true`.
		LiveLog.info("engine.release", "canal" to channel?.id, "habiaPlayer" to (player != null))

		// Lo que esta sesión deja para el diagnóstico (B6). Solo si hubo
		// sesión: `release()` se llama también sobre un motor que nunca
		// preparó nada, y contar eso como una sesión fallida mentiría.
		if (prepareAtMs > 0L) {
			Diagnostico.of(context).anotarSesion(
				canal = channel?.id,
				huboImagen = firstFrameAtMs > 0L,
				msPrimerFotograma = msToFirstFrame,
				rebuffers = rebufferCount,
				msReproducidos = if (firstFrameAtMs > 0L) System.currentTimeMillis() - firstFrameAtMs else 0L,
				tunneledPedido = dev.tunnelingEnabled,
				tunneledRescatado = rescateTunneledHecho,
				causaDeFallo = when {
					firstFrameAtMs == 0L -> "sinImagen"
					else -> ultimaCausa?.name
				},
				audioSinNormalizar = audioSinNormalizar,
			)
			prepareAtMs = 0L
		}

		pararLatido()
		player?.release()
		player = null
		trackSelector = null
		gruposDeAudio = emptyMap()
		gruposDeSubtitulo = emptyMap()
		subtitleTracks = emptyList()
		selectedSubtitle = null
		videoDecoder = null
		audioDecoder = null
		droppedFrames = 0
		windowDurationMs = C.TIME_UNSET
	}

	// --- Latido -------------------------------------------------------------

	private fun arrancarLatido() {
		if (muestreo != null) return
		val generacion = ++generacionLatido

		muestreo = HandlerThread("livetv-carga").also { hilo ->
			hilo.start()
			muestreoHandler = Handler(hilo.looper).also { h ->
				h.post(object : Runnable {
					override fun run() {
						if (generacionLatido != generacion) return
						ultimaCarga = carga.sample()
						h.postDelayed(this, LiveTuning.HEARTBEAT_INTERVAL_MS)
					}
				})
			}
		}

		latido.postDelayed(object : Runnable {
			override fun run() {
				// Solo el latido de la generación vigente se re-encola. Sin
				// esto, reconstruir desde dentro del latido lo duplica.
				if (generacionLatido != generacion || player == null) return
				escribirLatido()
				if (generacionLatido != generacion) return
				latido.postDelayed(this, LiveTuning.HEARTBEAT_INTERVAL_MS)
			}
		}, LiveTuning.HEARTBEAT_INTERVAL_MS)
	}

	private fun pararLatido() {
		generacionLatido++
		latido.removeCallbacksAndMessages(null)
		muestreoHandler?.removeCallbacksAndMessages(null)
		muestreo?.quitSafely()
		muestreo = null
		muestreoHandler = null
		ultimaCarga = null
		latidos = 0
		framesEnElLatidoAnterior = 0
		huecosEnElLatidoAnterior = 0
	}

	/**
	 * Una línea cada dos segundos con lo que hace falta para situar un corte
	 * que el propio motor no vio: si la posición avanzó, cuánto buffer queda,
	 * cuántos frames salieron en el intervalo y qué le costó a la caja.
	 */
	private fun escribirLatido() {
		val exo = player ?: return
		val s = stats()
		latidos++

		val framesAhora = renderedFrames
		val entregados = framesAhora - framesEnElLatidoAnterior
		framesEnElLatidoAnterior = framesAhora

		val huecosAhora = releaseGapCount
		val huecosNuevos = huecosAhora - huecosEnElLatidoAnterior
		huecosEnElLatidoAnterior = huecosAhora

		vigilar(exo, s)
		vigilarImagen(exo)
		vigilarMargen(exo, s)

		val c = ultimaCarga
		// La carga del sistema solo se anota donde el kernel la deja leer: en
		// Android 11 `/proc/loadavg` da EACCES, y repetir `carga1=null` en cada
		// una de las 20 000 líneas del anillo solo estorba al leerlo.
		val extra = c?.loadAvg1?.let { arrayOf("carga1" to "%.2f".format(it) as Any?) } ?: emptyArray()
		LiveLog.debug(
			"latido",
			"n" to latidos,
			"estado" to estadoLegible(exo.playbackState),
			"reproduciendo" to exo.isPlaying,
			"posS" to "%.1f".format(exo.currentPosition / 1000.0),
			"sinAvanzarMs" to s.msSinceProgress,
			"bufferS" to "%.1f".format(s.bufferedMs / 1000.0),
			"directoS" to "%.1f".format(s.liveOffsetMs / 1000.0),
			"frames" to entregados,
			"fps" to "%.1f".format(s.measuredFps),
			"huecosNuevos" to huecosNuevos,
			"huecoMaxMs" to s.maxFrameGapMs,
			"perdidos" to s.droppedFrames,
			"rebuffers" to s.rebufferCount,
			"reintentos" to s.loadRetries,
			"timelines" to cambiosDeTimeline,
			"cpu" to c?.let { "%.0f%%".format(it.cpuPercent) },
			*extra,
			"ramLibreMb" to c?.ramAvailableMb,
			"anchoBandaKbps" to s.bandwidthBps / 1000,
		)
	}

	/**
	 * Decide si hay que rescatar la reproducción, y con qué dureza.
	 *
	 * Sube de escalón solo si el anterior no sirvió, y **no se rinde nunca**:
	 * un canal caído por lluvia vuelve solo cuando vuelve la señal, sin que el
	 * abonado toque nada (CLAUDE.md §6).
	 */
	/**
	 * El fallo que el vigilante de progreso no puede ver: **tunneled sin
	 * imagen**.
	 *
	 * `vigilar` declara atascado lo que no avanza de posición. Cuando el
	 * tunneled de una caja no entrega fotogramas, el audio suena, la posición
	 * avanza y la pantalla está negra: desde dentro todo va bien, y el abonado
	 * se queda mirando un negro hasta que llama por teléfono.
	 *
	 * **Esto importa porque la caja mayoritaria del cliente —la ZTE B866V2—
	 * solo admite instalaciones desde la ficha de Play**, así que la primera
	 * vez que la veamos será con la app ya publicada. El tunneled está activo
	 * de fábrica y se excluye por modelo, o sea por conocimiento que todavía no
	 * tenemos de ella. Si lo implementa mal, el síntoma es ese negro, en todos
	 * los canales y en la caja que más abonados tiene.
	 *
	 * El remedio es barato y se cura solo: apagar el tunneled **para esta
	 * sesión** y rehacer la preparación. Convierte un fallo permanente y mudo
	 * en un hipo de unos segundos, sin esperar a que alguien nos cuente qué
	 * modelo falló. Y lo deja escrito en el log, que es como lo aprendemos.
	 */
	private fun vigilarImagen(exo: ExoPlayer) {
		if (!dev.rescateTunneledEnabled) return
		if (!tunnelingSesion || rescateTunneledHecho) return
		if (prepareAtMs == 0L) return

		// Con la simulación encendida se ignora que sí hubo imagen: es la única
		// forma de ver el rescate en una caja que funciona bien.
		val huboImagen = firstFrameAtMs > 0L && !dev.simularTunneledSinImagen
		if (huboImagen) return

		// Si ni siquiera está reproduciendo, el atasco es de otro tipo y de
		// eso se encarga `vigilar`: apagar el tunneled no arreglaría nada y
		// perdería la única función que quita los microcortes del cine.
		if (!exo.isPlaying) return
		if (System.currentTimeMillis() - prepareAtMs < LiveTuning.TUNNELED_NO_IMAGE_MS) return

		rescateTunneledHecho = true
		tunnelingSesion = false
		LiveLog.warn(
			"tunneled.sinImagen",
			"modelo" to "${Build.MANUFACTURER} ${Build.MODEL}",
			"sdk" to Build.VERSION.SDK_INT,
			"msSinImagen" to (System.currentTimeMillis() - prepareAtMs),
			"simulado" to dev.simularTunneledSinImagen,
			"accion" to "se apaga el tunneled y se rehace la preparación",
		)

		trackSelector?.let { sel ->
			runCatching { sel.setParameters(sel.buildUponParameters().setTunnelingEnabled(false)) }
				.onFailure { LiveLog.error("tunneled.rescateFallido", it) }
		}
		runCatching {
			exo.seekToDefaultPosition()
			exo.prepare()
		}.onFailure { LiveLog.error("tunneled.rescateFallido", it) }
	}

	/**
	 * Alejar la posición del borde cuando el buffer no sobrevive (pendiente 75).
	 *
	 * Vive en el latido porque es el único reloj que no depende del origen. Las
	 * guardas son las mínimas y **ninguna de ellas es «estar reproduciendo»**:
	 * los cinco momentos medidos en que esto debía actuar tenían
	 * `reproduciendo=false`, que es justo cuando hay datos detrás sin consumir.
	 *
	 * El enfriamiento es **el mismo** que el de la vuelta al borde, y a
	 * propósito: son dos mecanismos sobre la misma posición y si cada uno
	 * llevara su reloj se pelearían.
	 */
	private fun vigilarMargen(exo: ExoPlayer, s: EngineStats) {
		if (!dev.resyncPorMargen || dev.liveEdgeResyncDisabled) return
		// Durante el arranque el buffer está corto por definición. Sin esta
		// guarda, mover la posición ahí reinicia el almacenamiento y vuelve a
		// leer corto: el bucle que costó que `hbohd` tardara cuatro minutos en
		// dar el primer fotograma.
		if (firstFrameAtMs <= 0L || exo.playbackState == Player.STATE_IDLE) return

		val decision = margen.observar(
			bufferMs = s.bufferedMs,
			ventanaMs = windowDurationMs,
			posMs = exo.currentPosition,
			objetivoMs = activeTargetOffsetMs,
		)

		val ahoraMs = System.currentTimeMillis()
		when (decision) {
			is MargenDeBuffer.Decision.Alejarse -> {
				if (ahoraMs - lastResyncAtMs <= LiveTuning.LIVE_EDGE_RESYNC_COOLDOWN_MS) return
				lastResyncAtMs = ahoraMs
				margenCorto++
				LiveLog.warn(
					"offset.margenCorto",
					"bufferMs" to s.bufferedMs,
					"ventanaMs" to windowDurationMs,
					"posAntesMs" to exo.currentPosition,
					"destinoMs" to decision.destinoMs,
					"colchonMs" to decision.colchonMs,
					"numero" to margenCorto,
				)
				exo.seekTo(decision.destinoMs)
				LiveLog.warn(
					"offset.trasElSalto",
					"posMs" to exo.currentPosition,
					"offsetMs" to exo.currentLiveOffset,
				)
			}

			is MargenDeBuffer.Decision.SinSitioDetras -> {
				// No es un fallo nuestro y el cliente no puede arreglarlo: la
				// ventana no da para apartarse. Se dice una vez por sesión
				// porque lo que hay que tocar es el `segment_count` del site.
				if (sinSitioAvisado) return
				sinSitioAvisado = true
				LiveLog.warn(
					"offset.sinSitioDetras",
					"bufferMs" to s.bufferedMs,
					"ventanaMs" to windowDurationMs,
					"posMs" to exo.currentPosition,
					"destinoMs" to decision.destinoMs,
					"colchonMs" to decision.colchonMs,
					"causa" to "la ventana del origen no da para apartarse del borde",
				)
			}

			MargenDeBuffer.Decision.HayMargen,
			MargenDeBuffer.Decision.NoSeSabe,
			-> Unit
		}
	}

	private fun vigilar(exo: ExoPlayer, s: EngineStats) {
		if (dev.watchdogDisabled) return

		// Atascado = no avanza la posición. Vale tanto si está almacenando como
		// si dice estar reproduciendo: el 30-sep decía `reproduciendo=true` con
		// la imagen muerta, así que el estado del player no basta por sí solo.
		val atascado = s.msSinceProgress > LiveTuning.WATCHDOG_STALL_MS &&
			exo.playbackState != Player.STATE_IDLE

		// Y recuperado **no** es «la posición cambió»: al reconstruir, la
		// posición vuelve a cero y eso lee como avance. Medido el 01-oct: el
		// vigilante cantaba victoria con el buffer a cero y sin reproducir.
		// Recuperado es estar reproduciendo y con datos delante.
		val recuperado = exo.isPlaying && s.bufferedMs > 0 &&
			s.msSinceProgress <= LiveTuning.WATCHDOG_STALL_MS

		val ahora = System.currentTimeMillis()

		if (recuperado) {
			if (atascadoDesdeMs != 0L) {
				LiveLog.info(
					"vigilante.recuperado",
					"segundosAtascado" to (ahora - atascadoDesdeMs) / 1000,
					"escalonAlcanzado" to escalon,
				)
			}
			atascadoDesdeMs = 0L
			escalon = 0
			ciclosSinExito = 0
			return
		}

		if (!atascado) return

		if (atascadoDesdeMs == 0L) {
			atascadoDesdeMs = ahora
            LiveLog.warn(
				"vigilante.atascado",
				"estado" to estadoLegible(exo.playbackState),
				// La posición, porque en negativo dice algo que ninguna otra
				// columna dice: el reproductor se quedó en un punto que la
				// ventana ya dejó atrás.
				"posS" to "%.1f".format(exo.currentPosition / 1000.0),
				"bufferS" to "%.1f".format(s.bufferedMs / 1000.0),
				"directoS" to "%.1f".format(s.liveOffsetMs / 1000.0),
				"sinAvanzarMs" to s.msSinceProgress,
			)
		}

		// La espera crece mientras no haya una recuperación de verdad. Saltar
		// al borde cada doce segundos con la fuente muerta no adelanta nada:
		// no hay a dónde saltar, y cada intento cuesta trabajo a la caja y
		// peticiones al servidor. Se topa a tres veces la base para que, en
		// cuanto la señal vuelva, el rescate siga llegando pronto.
		val espera = LiveTuning.WATCHDOG_STEP_COOLDOWN_MS *
			(1 + ciclosSinExito.coerceAtMost(2))
		if (ahora - ultimoEscalonMs < espera) return
		ultimoEscalonMs = ahora
		escalon++
		recuperaciones++
		if (escalon % 3 == 0) ciclosSinExito++

		// Tres escalones y vuelta a empezar. Reconstruir el reproductor es lo
		// más caro y lo que más tarda en verse, así que es el último.
		when (((escalon - 1) % 3) + 1) {
			1 -> {
				LiveLog.warn("vigilante.saltaAlBorde", "intento" to escalon)
				runCatching { exo.seekToDefaultPosition() }
					.onFailure { LiveLog.error("vigilante.saltoFallido", it) }
			}

			2 -> {
				LiveLog.warn("vigilante.rehacePreparacion", "intento" to escalon)
				// Relee el playlist desde cero: garantiza caer dentro de la
				// ventana actual, que es de lo que el salto no puede fiarse si
				// la posición quedó fuera de ella.
				runCatching {
					exo.seekToDefaultPosition()
					exo.prepare()
				}.onFailure { LiveLog.error("vigilante.preparacionFallida", it) }
			}

			else -> {
				LiveLog.warn("vigilante.reconstruye", "intento" to escalon)
				val canal = channel
				val plan = plan
				if (canal == null || plan == null) {
					LiveLog.error("vigilante.sinCanal", null, "intento" to escalon)
					return
				}
				runCatching {
					pararLatido()
					player?.release()
					player = null
					prepare(canal, plan)
					play()
				}.onFailure { LiveLog.error("vigilante.reconstruccionFallida", it) }
			}
		}

		listeners.forEach {
			it.onRecoverableError("vigilante.escalon$escalon", null)
		}
	}

	private fun estadoLegible(state: Int): String = when (state) {
		Player.STATE_IDLE -> "idle"
		Player.STATE_BUFFERING -> "almacenando"
		Player.STATE_READY -> "listo"
		Player.STATE_ENDED -> "terminado"
		else -> "?$state"
	}

	/**
	 * Cambia la pista de audio **sin cortar el vídeo**.
	 *
	 * Se hace con una anulación sobre el grupo de audio y nada más: el
	 * renderizador de vídeo ni se entera, que es justo lo que `CLAUDE.md` §3
	 * pide del lado de motor de este botón.
	 *
	 * La pista se busca por **identidad** —idioma normalizado, PID de
	 * desempate— y nunca por índice (regla 6).
	 */
	override fun selectAudioTrack(track: AudioTrackRef) {
		// Quien elige, manda. A partir de aquí el idioma por defecto no vuelve
		// a tocar nada en esta sintonía: si alguien se pasó a inglés a mano, no
		// se le devuelve a español porque el canal cambie de programa.
		audioPorDefectoAplicado = true
		aplicarPista(track, gruposDeAudio, C.TRACK_TYPE_AUDIO, "audio")
	}

	override fun selectSubtitleTrack(track: AudioTrackRef?) {
		val selector = trackSelector ?: return
		if (track == null) {
			selector.setParameters(
				selector.buildUponParameters()
					.clearOverridesOfType(C.TRACK_TYPE_TEXT)
					.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
					.setPreferredTextLanguage(null)
			)
			selectedSubtitle = null
			LiveLog.info("subtitulos.apagados")
			return
		}
		aplicarPista(track, gruposDeSubtitulo, C.TRACK_TYPE_TEXT, "subtitulo")
	}

	private fun aplicarPista(
		track: AudioTrackRef,
		mapa: Map<String, Pair<androidx.media3.common.TrackGroup, Int>>,
		tipo: Int,
		que: String,
	) {
		val selector = trackSelector
		if (selector == null) {
			LiveLog.warn("pista.sinSelector", "que" to que)
			return
		}
		val destino = mapa[claveDePista(track)]
		if (destino == null) {
			// No se calla: pedir una pista que no está es un síntoma de que la
			// identidad con que la guardamos no sobrevivió al cambio de
			// segmento, y eso hay que verlo (CLAUDE.md regla 8).
			LiveLog.warn(
				"pista.noEncontrada",
				"que" to que,
				"idioma" to track.language,
				"disponibles" to mapa.keys,
			)
			return
		}

		selector.setParameters(
			selector.buildUponParameters()
				.setOverrideForType(
					androidx.media3.common.TrackSelectionOverride(destino.first, destino.second)
				)
				.setTrackTypeDisabled(tipo, false)
		)
		LiveLog.info("pista.seleccionada", "que" to que, "idioma" to track.language)
	}

	/** Idioma normalizado + PID de desempate. Nunca el índice. */
	private fun claveDePista(ref: AudioTrackRef): String =
		"${ref.language ?: "?"}#${ref.pid}"



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
		val sinAvanzarMs = avance.observar(pos, exo?.isPlaying == true, ahora)

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
			msSinceProgress = sinAvanzarMs,
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
			videoMime = exo?.videoFormat?.sampleMimeType,
			audioMime = exo?.audioFormat?.sampleMimeType,
			measuredFps = measuredFps(ahora),
			frameGapCount = releaseGapCount,
			maxFrameGapMs = maxReleaseGapMs,

			recovering = atascadoDesdeMs != 0L,
			recoveryAttempts = recuperaciones,

			cpuPercent = ultimaCarga?.cpuPercent ?: 0f,
			loadAvg1 = ultimaCarga?.loadAvg1,
			ramAvailableMb = ultimaCarga?.ramAvailableMb ?: 0,
			ramTotalMb = ultimaCarga?.ramTotalMb ?: 0,
			cores = ultimaCarga?.cores ?: 0,

			audioTracks = audioTracks,
			selectedAudio = selectedAudio,
			subtitleTracks = subtitleTracks,
			selectedSubtitle = selectedSubtitle,
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

		override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
			val lineas = cueGroup.cues.mapNotNull { it.text?.toString()?.trim() }.filter { it.isNotEmpty() }
			notify { it.onCues(lineas) }
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
			// Pendiente 76. Toda la decisión de volver al borde vive dentro de
			// este método, así que **si el playlist se congela el método no se
			// llama y el mecanismo entero queda mudo** sin dejar rastro. Eso
			// costó el 11-oct no poder explicar por qué en 38 segundos de bucle
			// de inanición no se evaluó ni una vez. El contador va en el latido:
			// si deja de subir mientras el buffer baja, ya no hay que suponerlo.
			cambiosDeTimeline++
			if (timeline.windowCount == 0) return
			val window = timeline.getWindow(0, Timeline.Window())
			val durationMs = window.durationMs
			if (durationMs == C.TIME_UNSET || durationMs <= 0) return
			windowDurationMs = durationMs

			// El offset pedido se compara contra dos referencias, y **ninguna
			// de las dos lo recorta aquí**: las dos se registran.
			//
			// Por arriba: más de la mitad de la ventana acaba en
			// BEHIND_LIVE_WINDOW en cuanto hay un hipo de red (medido en
			// hbohd, CHANNELS.md §2.bis). Quien de verdad protege de eso es la
			// vuelta al borde, más abajo, porque actúa sobre el offset **real**
			// y no sobre lo que alguien pidió. Este aviso dice de quién es la
			// culpa: un `config.json` mal generado para ese stream.
			//
			// Suelo: sin `EXT-X-SERVER-CONTROL` la especificación HLS fija el
			// margen seguro en 3 × TARGETDURATION y Media3 no baja de ahí. Lo
			// pedido puede ser sencillamente inalcanzable, y callarlo haría que
			// alguien perdiera un día preguntándose por qué el config.json no
			// hace nada.
			val target = activeTargetOffsetMs
			val umbral = (durationMs * LiveTuning.TARGET_OFFSET_WINDOW_WARN_FRACTION).toLong()
			if (target > umbral) {
				LiveLog.warn(
					"offset.superaLaVentana",
					"pedidoMs" to target,
					"ventanaMs" to durationMs,
					"umbralMs" to umbral,
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

			val antesMs = player?.currentPosition ?: C.TIME_UNSET
			// A dónde se salta. `seekToDefaultPosition()` era lo anterior y
			// **no devolvía al directo**: medido la noche del 10-oct, 547
			// saltos y el offset seguía en 37 s tras cada uno. Calcularlo
			// de la ventana da una posición comprobable, y además permite
			// preguntar lo que importa de verdad: si el salto adelanta.
			val decision = if (dev.resyncCalculando) {
				VueltaAlBorde.decidir(real, durationMs, antesMs, activeTargetOffsetMs)
			} else {
				null
			}
			val limite = (durationMs * LiveTuning.LIVE_EDGE_RESYNC_WINDOW_FRACTION).toLong()

			// Sin el cálculo activo no hay destino, así que tampoco hay ganancia
			// que medir: se queda el disparador de siempre y el salto a ciegas.
			val disparaSinCalculo = decision == null && real != C.TIME_UNSET &&
				real - limite > LiveTuning.LIVE_EDGE_RESYNC_MIN_EXCESS_MS
			val noAdelanta = decision as? VueltaAlBorde.Decision.NoAdelanta
			// Y con el interruptor apagado, un salto que no adelanta se da
			// igual: el valor de fábrica tiene que ser el comportamiento
			// anterior (`CLAUDE.md` §3). Lo que se gana de todas formas es la
			// columna `gananciaMs`, que lo deja ver en el log.
			val dispara = disparaSinCalculo ||
				decision is VueltaAlBorde.Decision.Salta ||
				(noAdelanta != null && !dev.resyncSoloSiAdelanta)

			// Pendiente 76: por qué **no** se salta.
			//
			// Hasta el 11-oct el log solo decía lo que el mecanismo hizo, nunca
			// lo que no hizo. Con el offset leyendo 70 s sobre una ventana de
			// 33,6 —o sea con la condición cumplida de sobra— hubo 38 segundos
			// sin una sola línea, y las cuatro guardas eran candidatas por igual.
			//
			// Solo se registra cuando el disparador **habría** disparado, que es
			// lo que acota esto a los momentos interesantes, y una vez por
			// motivo y por enfriamiento para no inundar el log.
			if (!puedeResincronizar && dispara) {
				val motivo = when {
					dev.liveEdgeResyncDisabled -> "apagado en el menú"
					firstFrameAtMs <= 0L -> "aún sin imagen en pantalla"
					player?.playbackState != Player.STATE_READY -> "el reproductor no está listo"
					else -> "enfriamiento, faltan " +
						"${(LiveTuning.LIVE_EDGE_RESYNC_COOLDOWN_MS - (ahoraMs - lastResyncAtMs)) / 1000} s"
				}
				if (motivo != ultimoMotivoSinEvaluar ||
					ahoraMs - ultimoNoSeEvaluaAtMs > LiveTuning.LIVE_EDGE_RESYNC_COOLDOWN_MS
				) {
					ultimoMotivoSinEvaluar = motivo
					ultimoNoSeEvaluaAtMs = ahoraMs
					LiveLog.warn(
						"offset.noSeEvalua",
						"offsetMs" to real,
						"ventanaMs" to durationMs,
						"posMs" to antesMs,
						"motivo" to motivo,
					)
				}
				return
			}

			if (puedeResincronizar && noAdelanta != null && dev.resyncSoloSiAdelanta) {
				// Aquí no hay nada que recuperar: la lista del origen no avanza y
				// el destino está detrás de donde ya estamos. Se gasta el
				// enfriamiento igual, que es lo que limita esto a una línea cada
				// 30 s y lo que hace que se vuelva a mirar justo cuando llegaba
				// el salto que sí servía.
				lastResyncAtMs = ahoraMs
				resyncDeclinadas++
				LiveLog.warn(
					"offset.saltoNoAdelanta",
					"offsetMs" to real,
					"ventanaMs" to durationMs,
					"posMs" to antesMs,
					"destinoMs" to noAdelanta.destinoMs,
					"gananciaMs" to noAdelanta.gananciaMs,
					"numero" to resyncDeclinadas,
				)
				return
			}

			if (puedeResincronizar && dispara) {
				lastResyncAtMs = ahoraMs
				resyncCount++
				val salta = decision as? VueltaAlBorde.Decision.Salta
				val destinoMs = salta?.destinoMs ?: noAdelanta?.destinoMs ?: C.TIME_UNSET
				LiveLog.warn(
					"offset.vuelveAlBorde",
					"offsetMs" to real,
					"ventanaMs" to durationMs,
					"limiteMs" to limite,
					// Las cuatro columnas que faltaban para saber si el salto
					// sirve de algo: de dónde, a dónde, cuánto adelanta y qué
					// pasó después.
					"posAntesMs" to antesMs,
					"destinoMs" to destinoMs.takeIf { it != C.TIME_UNSET },
					"gananciaMs" to (salta?.gananciaMs ?: noAdelanta?.gananciaMs),
					"numero" to resyncCount,
				)
				if (destinoMs != C.TIME_UNSET) {
					player?.seekTo(destinoMs)
				} else {
					player?.seekToDefaultPosition()
				}
				// Y el resultado, medido en el mismo sitio: sin esto, «el salto
				// no sirvió» se deduce de que el aviso vuelve a salir treinta
				// segundos después, que es una conclusión a la que se llega
				// después de una noche entera.
				LiveLog.warn(
					"offset.trasElSalto",
					"posMs" to (player?.currentPosition ?: C.TIME_UNSET),
					"offsetMs" to (player?.currentLiveOffset ?: C.TIME_UNSET),
				)
				return
			}

			if (real != C.TIME_UNSET && !offsetMismatchReported &&
				real - target > OFFSET_MISMATCH_TOLERANCE_MS
			) {
				offsetMismatchReported = true
				// Dónde puso Media3 la posición por defecto, medido desde el
				// final de la ventana. **No es un suelo**, y llamarlo así duró
				// una hora: con los 14 000 pedidos esto sale 14 000 clavados.
				//
				// Leído en la fuente de Media3 1.8.0
				// (`HlsMediaSource.getLiveWindowDefaultStartPositionUs`), el
				// arranque es `duración − objetivo` **redondeado hacia atrás al
				// principio del segmento que lo contiene**. De ahí sale todo el
				// desajuste: pedir 14 000 con segmentos de 2,8 s da hasta 2,8 s
				// de más, y lo medido fue 16 674 y 16 707.
				//
				// El `3 × TARGETDURATION` de la especificación **no recorta lo
				// que pedimos**: es el objetivo de reserva que Media3 usa cuando
				// el `MediaItem` no trae ninguno.
				val objetivoAplicadoMs = (durationMs - window.defaultPositionMs).takeIf { it > 0 }
				LiveLog.warn(
					"offset.noSeAplica",
					"pedidoMs" to target,
					"realMs" to real,
					"objetivoAplicadoMs" to objetivoAplicadoMs,
					"causa" to "el arranque se redondea al principio de un segmento",
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
			// Diagnóstico: qué grupos entrega Media3 de verdad, antes de que
			// nuestro filtrado toque nada.
			LiveLog.debug(
				"tracks.crudo",
				"grupos" to tracks.groups.size,
				"detalle" to tracks.groups.joinToString(" | ") { g ->
					val tipo = when (g.type) {
						C.TRACK_TYPE_AUDIO -> "audio"
						C.TRACK_TYPE_VIDEO -> "video"
						C.TRACK_TYPE_TEXT -> "texto"
						else -> "t${g.type}"
					}
					val pistas = (0 until g.length).joinToString(",") { i ->
						val f = g.getTrackFormat(i)
						"${f.language ?: "?"}/${f.sampleMimeType?.substringAfterLast('/')}" +
							(if (g.isTrackSupported(i)) "" else "!nosoportada") +
							(if (g.isTrackSelected(i)) "*" else "")
					}
					"$tipo[$pistas]"
				},
			)

			// `CLAUDE.md` §6 prohíbe ABR: una sola calidad por canal. Con
			// `index.m3u8` eso lo sostiene el headend —un solo
			// `EXT-X-STREAM-INF`— y aquí no hay forma de impedirlo, pero sí de
			// **verlo**. Un master con varias calidades pondría a Media3 a
			// conmutar sin que nadie se enterara.
			val calidadesDeVideo = tracks.groups
				.filter { it.type == C.TRACK_TYPE_VIDEO }
				.sumOf { it.length }
			if (calidadesDeVideo > 1 && !abrYaAvisado) {
				abrYaAvisado = true
				LiveLog.warn(
					"abr.variasVariantes",
					"canal" to channel?.id,
					"calidades" to calidadesDeVideo,
					"detalle" to "el master declara más de una calidad; §6 prohíbe ABR",
				)
			}

			val encontradas = mutableListOf<AudioTrackRef>()
			var seleccionada: AudioTrackRef? = null
			var mimeAudio: String? = null
			val mapaAudio = mutableMapOf<String, Pair<androidx.media3.common.TrackGroup, Int>>()

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
						mapaAudio[claveDePista(ref)] = group.mediaTrackGroup to i
						if (group.isTrackSelected(i)) seleccionada = ref
						// La que suena manda; si no hay ninguna elegida todavía,
						// vale la primera: en este lineup todas comparten códec.
						if (mimeAudio == null || group.isTrackSelected(i)) {
							mimeAudio = f.sampleMimeType
						}
					}
				}

			// Subtítulos. En este lineup van incrustados en el vídeo como
			// CEA-608 y Media3 los expone como pistas de texto una vez se le
			// pide que los busque (medido el 01-oct en `estrellashd`: 22
			// entradas con diálogo real en 40 s).
			val textos = mutableListOf<AudioTrackRef>()
			val mapaTexto = mutableMapOf<String, Pair<androidx.media3.common.TrackGroup, Int>>()
			var textoSeleccionado: AudioTrackRef? = null

			tracks.groups
				.filter { it.type == C.TRACK_TYPE_TEXT }
				.forEach { group ->
					for (i in 0 until group.length) {
						val f = group.getTrackFormat(i)
						val ref = AudioTrackRef(
							language = LanguageNormalizer.normalize(f.language),
							pid = -1,
							channels = 0,
							label = f.label ?: f.language ?: "Subtítulos",
						)
						textos += ref
						mapaTexto[claveDePista(ref)] = group.mediaTrackGroup to i
						if (group.isTrackSelected(i)) textoSeleccionado = ref
					}
				}

			if (textos.size != subtitleTracks.size) {
				LiveLog.info("subtitulos.pistas", "cuantas" to textos.size)
			}
			subtitleTracks = textos
			gruposDeSubtitulo = mapaTexto
			selectedSubtitle = textoSeleccionado

			if (encontradas.map { it.language } != audioTracks.map { it.language }) {
				LiveLog.info(
					"audio.pistas",
					"cuantas" to encontradas.size,
					"idiomas" to encontradas.mapNotNull { it.language },
				)
			}

			audioTracks = encontradas
			gruposDeAudio = mapaAudio
			selectedAudio = seleccionada
			revisarNormalizacionDeAudio(mimeAudio)
			aplicarAudioPorDefecto(encontradas, seleccionada)
			notify { it.onAudioTracksChanged(encontradas) }
		}

		/**
		 * Si el problema viene de la señal o de dentro del reproductor.
		 *
		 * Los códigos de entrada/salida y los de analizado son lo que provoca
		 * un chubasco: segmentos que no llegan o llegan rotos. Un fallo del
		 * decodificador o una aserción de Media3 **no son el satélite**, y
		 * decir que sí manda a buscar donde no es.
		 */
		private fun causaDe(error: PlaybackException): CausaDeFallo = when (error.errorCode) {
			in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
			in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
			-> CausaDeFallo.SENAL

			else -> CausaDeFallo.INTERNO
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
					notify {
						ultimaCausa = causaDe(error)
						it.onRecoverableError(error.errorCodeName, error, causaDe(error))
					}
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

		/**
		 * El formato cambia **a mitad de canal** en el conjunto real: un canal
		 * generalista alterna cine a 23.976 con pulldown y vídeo a 29.97, y con
		 * ello cambia la cadencia de presentación sin que cambie la resolución.
		 * `onVideoSizeChanged` no lo ve. Sin esta línea, un tirón que empieza
		 * al cambiar de programa parece salido de la nada.
		 */
		override fun onVideoInputFormatChanged(
			eventTime: AnalyticsListener.EventTime,
			format: androidx.media3.common.Format,
			decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
		) {
			LiveLog.info(
				"video.formato",
				"codec" to format.sampleMimeType,
				"ancho" to format.width,
				"alto" to format.height,
				"fpsDeclarado" to format.frameRate,
				"sar" to format.pixelWidthHeightRatio,
				"bitrate" to format.bitrate,
				"reuso" to decoderReuseEvaluation?.result,
			)
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
				// Que el rescate funcionara es tan importante como que saltara:
				// sin esta línea sabríamos que una caja da negro con tunneled,
				// pero no si la salida que le dimos la arregla.
				if (rescateTunneledHecho) {
					LiveLog.info(
						"tunneled.rescatado",
						"modelo" to "${Build.MANUFACTURER} ${Build.MODEL}",
						"msHastaImagen" to (firstFrameAtMs - prepareAtMs),
					)
				}
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
	/**
	 * Selecciona la pista de audio que pide el `config.json`.
	 *
	 * **No se delega en el emparejamiento de idioma de Media3**, y la razón es
	 * concreta: `estrellashd` y `estrellassd` etiquetan el español como `esl`,
	 * que no es un código ISO. Media3 no lo casaría con `spa` y la preferencia
	 * se quedaría sin efecto justo en los canales donde el dato existe. La
	 * normalización es nuestra (`LanguageNormalizer`), así que la comparación
	 * también tiene que serlo.
	 *
	 * **No toca nada si ya suena lo pedido**, que es el caso de cuatro de los
	 * seis canales —una sola pista— y evita una conmutación inútil al arrancar.
	 */
	private fun aplicarAudioPorDefecto(pistas: List<AudioTrackRef>, actual: AudioTrackRef?) {
		if (audioPorDefectoAplicado) return
		if (!dev.audioDefaultEnabled) return

		val pedido = LanguageNormalizer.normalize(channel?.audioDefault) ?: return
		if (pistas.isEmpty()) return

		if (LanguageNormalizer.normalize(actual?.language) == pedido) {
			audioPorDefectoAplicado = true
			LiveLog.debug("audio.porDefectoYaActivo", "canal" to channel?.id, "idioma" to pedido)
			return
		}

		val destino = pistas.firstOrNull { LanguageNormalizer.normalize(it.language) == pedido }
		if (destino == null) {
			// **No se da por resuelto.** El segundo audio de un canal de cine
			// aparece y desaparece con el programa (medido el 01-oct), así que
			// rendirse en el primer `onTracksChanged` sería rendirse justo
			// cuando todavía no había llegado. Se reintenta en cada cambio de
			// pistas; lo que se da una sola vez es el aviso.
			if (!audioPorDefectoAvisado) {
				audioPorDefectoAvisado = true
				LiveLog.info(
					"audio.porDefectoNoDisponible",
					"canal" to channel?.id,
					"pedido" to pedido,
					"hay" to pistas.mapNotNull { LanguageNormalizer.normalize(it.language) },
				)
			}
			return
		}

		LiveLog.info(
			"audio.porDefectoAplicado",
			"canal" to channel?.id,
			"pedido" to pedido,
			"antes" to LanguageNormalizer.normalize(actual?.language),
		)
		selectAudioTrack(destino)
	}

	/**
	 * Avisa cuando el audio de un canal **no llegó normalizado a AAC**.
	 *
	 * `CLAUDE.md` §2 permite una sola excepción al principio rector —el audio
	 * se normaliza a AAC en Flussonic— y §6 cuelga de ella la decisión de no
	 * llevar decoders de MP2, MP3 ni AC-3. Con un headend por ISP eso dejó de
	 * ser una garantía y pasó a ser un requisito de despliegue.
	 *
	 * **Existe por cómo falla, no por cómo suena.** La caja de pruebas
	 * decodifica `audio/mpeg` y `audio/ac3` por hardware, así que un canal mal
	 * normalizado se oye perfecto aquí y es silencio en una caja barata. Sin
	 * esta línea, el síntoma que llega es «un abonado no tiene sonido» y hay
	 * que ir al site a buscarlo.
	 *
	 * El aviso dice además **si esta caja puede decodificarlo**, que es lo que
	 * separa «este site está mal configurado, arréglalo cuando puedas» de «este
	 * canal está mudo ahora mismo en este modelo».
	 */
	private fun revisarNormalizacionDeAudio(mimeObservado: String?) {
		if (audioYaRevisado || mimeObservado == null) return
		audioYaRevisado = true

		val canal = channel
		if (mimeObservado == MimeTypes.AUDIO_AAC) {
			LiveLog.debug("audio.normalizado", "canal" to canal?.id, "codec" to mimeObservado)
			return
		}

		val caps = DeviceCapabilities.of(context)
		val puede = caps.decodersFor(mimeObservado).isNotEmpty()
		val datos = arrayOf(
			"canal" to canal?.id,
			"declarado" to canal?.audioCodec,
			"observado" to mimeObservado,
			"modelo" to caps.model,
			"puedeDecodificar" to puede,
		)

		// Lo que viaja al registro, para que esto no se quede en el log de una
		// caja a la que habría que ir (§2, defensa 2). Se anota suene o no:
		// que suene aquí es un accidente del hardware de esta caja, no que el
		// site esté bien. Sin el prefijo `audio/`, que no aporta y ocupa.
		audioSinNormalizar = mimeObservado.substringAfter("audio/")

		if (puede) {
			// Suena, pero solo porque esta caja trae el decoder. En otra no.
			LiveLog.warn("audio.noNormalizado", *datos)
		} else {
			// Mudo aquí y ahora. Se eleva a error para que salga en el volcado
			// automático: es una visita al site, no una curiosidad.
			LiveLog.error("audio.sinDecoder", null, *datos)
		}
	}

	private inner class DegradableCodecSelector : MediaCodecSelector {
		override fun getDecoderInfos(
			mimeType: String,
			requiresSecureDecoder: Boolean,
			requiresTunnelingDecoder: Boolean,
		): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
			val all = MediaCodecSelector.DEFAULT
				.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)

			val isVideo = mimeType.startsWith("video/")
			// El camino de vídeo lo decide **el plan**, no este selector. Son
			// la misma respuesta hoy —la cascada lee el mismo menú y el mismo
			// servidor—, pero con dos lugares donde se decide lo mismo basta
			// un descuido para que el log diga una cosa y la caja haga otra.
			val porPlan = plan?.videoPath
			val soloSoftware = porPlan == DecodePath.SW_PLATFORM || porPlan == DecodePath.SW_VLC
			val kept = all.filterNot { info ->
				val vetado = dev.isVetoed(info.name)
				val porSoftware = isVideo && soloSoftware && info.hardwareAccelerated
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
