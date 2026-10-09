package org.jellyfin.livetv.engine

import android.content.Context
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.livetv.diagnostics.DeviceLoad
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.source.AudioTrackRef
import org.jellyfin.livetv.source.ChannelProfile
import org.jellyfin.livetv.source.LanguageNormalizer
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * El respaldo de vídeo: libVLC.
 *
 * ## Para qué existe, con un número detrás
 *
 * No es un motor alternativo «por si acaso». Entra por **un caso medido**: el
 * 08-oct, `estrellassd` —MPEG-2 480i— se presentaba a **15 fps con peine** en
 * la caja de pruebas. Acotado hasta el final: no era el render, no era la app
 * y no era el stream (600 fotogramas en 20 s medidos con `ffprobe`). Era el
 * único decodificador MPEG-2 que tiene esa caja, y no hay otro al que caer
 * dentro de Media3.
 *
 * Es literalmente lo que `CLAUDE.md` §6 le reserva a libVLC: «MPEG-2 sin
 * decoder en la caja, y **desentrelazado por software de SD**».
 *
 * ## La trampa que gobierna toda la clase
 *
 * `LIVETV_ENGINE.md` §4.4 lo dice y conviene repetirlo aquí, porque es lo que
 * hace que esto funcione o no sirva para nada: **un backend que decodifica por
 * MediaCodec y pinta directo a la superficie no aplica filtros.** Si libVLC
 * usa el decodificador por hardware, el desentrelazado por software no llega a
 * ejecutarse y habríamos cambiado de motor para obtener exactamente el mismo
 * fotograma con peine.
 *
 * Por eso aquí la decodificación por hardware se **desactiva explícitamente**.
 * Cuesta CPU, y ese es el precio del único camino que desentrelaza.
 *
 * ## Lo que esta clase no hace
 *
 * No es un `Media3LiveEngine` con otro nombre. Media3 sigue siendo el motor de
 * todos los canales (§6); esto se elige solo cuando la cascada lo pide. Las
 * métricas que libVLC no expone se devuelven en cero **y se dice cuáles**, en
 * vez de inventarlas: un número inventado en el overlay es peor que un hueco,
 * porque se lee igual que uno medido.
 */
class VlcLiveEngine(
	context: Context,
	private val dev: DevOptions,
) : LiveEngine {

	override val id: String = PlanBuilder.VLC_ENGINE_ID

	private val contexto = context.applicationContext
	private val listeners = mutableListOf<EngineListener>()

	private var libVlc: LibVLC? = null
	private var player: MediaPlayer? = null
	private var surface: SurfaceView? = null
	private var channel: ChannelProfile? = null
	private var plan: PlaybackPlan? = null

	private var prepareAtMs = 0L
	private var firstFrameAtMs = 0L
	private var rebuffers = 0
	private var sesionDesdeMs = 0L
	private var anchoVideo = 0
	private var altoVideo = 0
	private var sarVideo = 1f
	private var pistas: List<AudioTrackRef> = emptyList()
	private var pistaElegida: AudioTrackRef? = null
	private var ultimaCausa: CausaDeFallo = CausaDeFallo.SENAL

	/** Para contar flancos y no los 26 avisos de llenado que manda libVLC. */
	private var enRebuffer = false

	/** Lo último que midió el latido. El overlay lo lee entre latido y latido. */
	private var ultimaCpu = 0f

	private val medidor = DeviceLoad(context)
	private var latido: Job? = null
	private val alcance = CoroutineScope(Dispatchers.Main.immediate)

	override fun attach(surface: SurfaceView) {
		this.surface = surface
		player?.vlcVout?.let { vout ->
			if (vout.areViewsAttached()) vout.detachViews()
			vout.setVideoView(surface)
			vout.attachViews()
			dimensionar(surface)
		}
		LiveLog.debug("vlc.attach", "ancho" to surface.width, "alto" to surface.height)
	}

	/**
	 * Decirle a libVLC cuánto mide su ventana.
	 *
	 * **Sin esto el vídeo sale en un rectángulo pequeño en una esquina**, no
	 * centrado y no escalado — visto en la caja el 08-oct en la primera
	 * reproducción que funcionó. Media3 toma el tamaño de la superficie solo;
	 * libVLC no: hay que dárselo, y hay que volver a dárselo cada vez que la
	 * superficie cambia de tamaño, que es lo que hace `AspectoDeVideo` al
	 * aplicar un modo de imagen.
	 */
	private fun dimensionar(vista: SurfaceView) {
		val ancho = vista.width
		val alto = vista.height
		if (ancho <= 0 || alto <= 0) {
			// La vista todavía no está medida. Se reintenta cuando lo esté, en
			// vez de fijar un tamaño inventado que luego nadie corrige.
			vista.post { if (vista.width > 0) dimensionar(vista) }
			return
		}
		runCatching { player?.vlcVout?.setWindowSize(ancho, alto) }
			.onFailure { LiveLog.warn("vlc.sinTamano", "motivo" to it.message) }
	}

	override fun prepare(channel: ChannelProfile, plan: PlaybackPlan) {
		this.channel = channel
		this.plan = plan
		prepareAtMs = System.currentTimeMillis()
		firstFrameAtMs = 0L
		sesionDesdeMs = prepareAtMs

		LiveLog.startSession(channel.id, channel.url, channel.videoCodec, channel.audioCodec)
		LiveLog.info(
			"engine.prepare",
			"engine" to id,
			"videoPath" to plan.videoPath,
			"deinterlace" to plan.deinterlace,
			"targetOffsetMs" to plan.targetOffsetMs,
			"reason" to plan.reason,
			"degradacion" to dev.describe(),
		)

		soltarReproductor()

		val vlc = libVlc ?: LibVLC(contexto, opcionesDeArranque()).also { libVlc = it }
		val media = Media(vlc, android.net.Uri.parse(channel.url)).apply {
			// **Sin esto, el desentrelazado no se ejecuta.** Ver la nota de la
			// clase: con decodificación por hardware el filtro no corre.
			setHWDecoderEnabled(false, false)
			addOption(":network-caching=${LiveTuning.VLC_NETWORK_CACHING_MS}")
			if (plan.deinterlace == DeinterlaceMode.SOFTWARE) {
				addOption(":deinterlace=1")
				addOption(":deinterlace-mode=${LiveTuning.VLC_DEINTERLACE_MODE}")
			}
		}

		val mp = MediaPlayer(vlc).apply {
			setEventListener(::alEvento)
			this.media = media
		}
		media.release()
		player = mp

		surface?.let { attach(it) }
		arrancarLatido(alcance)
	}

	/**
	 * Lo que se le pasa a libVLC al arrancar, y por qué cada cosa.
	 *
	 * Se queda corto a propósito. Cada opción que se añade aquí es
	 * comportamiento que luego hay que explicar cuando una caja se porta
	 * distinto, y libVLC trae cientos.
	 */
	private fun opcionesDeArranque(): ArrayList<String> = arrayListOf(
		// El log de libVLC va al nuestro y no a logcat a secas: en una caja
		// ajena, lo que no está en el volcado no existe.
		"-v",
		// Sin esto, libVLC intenta su propio reescalado y se pelea con el
		// tamaño que le da la vista.
		"--android-display-chroma", "RV32",
	)

	override fun play() {
		player?.play()
	}

	override fun stop() {
		player?.stop()
	}

	override fun release() {
		latido?.cancel()
		latido = null
		soltarReproductor()
		libVlc?.release()
		libVlc = null
		LiveLog.debug("vlc.release")
	}

	private fun soltarReproductor() {
		player?.let { mp ->
			runCatching {
				mp.setEventListener(null)
				if (mp.vlcVout.areViewsAttached()) mp.vlcVout.detachViews()
				mp.stop()
				mp.release()
			}.onFailure { LiveLog.warn("vlc.releaseSucio", "motivo" to it.message) }
		}
		player = null
	}

	/**
	 * Selección de audio **por identidad de pista**, nunca por índice.
	 *
	 * Misma regla que en Media3 (`CLAUDE.md` §4.6): el idioma normalizado es la
	 * clave y el índice de libVLC solo sirve para pedírselo. La etiqueta puede
	 * mentir —hay satélites que marcan `spa` una pista en inglés— así que esto
	 * es un valor por defecto, no una imposición.
	 */
	override fun selectAudioTrack(track: AudioTrackRef) {
		val mp = player ?: return
		val objetivo = LanguageNormalizer.normalize(track.language)
		val encontrada = mp.audioTracks?.filter { it.id >= 0 }?.firstOrNull {
			LanguageNormalizer.normalize(it.name) == objetivo ||
				it.name.orEmpty().contains(track.language.orEmpty(), ignoreCase = true)
		}
		if (encontrada == null) {
			LiveLog.warn("vlc.audioNoEncontrado", "idioma" to track.language)
			return
		}
		mp.audioTrack = encontrada.id
		pistaElegida = track
		LiveLog.info("audio.elegida", "idioma" to track.language, "motor" to id)
	}

	/**
	 * No hay subtítulos que elegir en este lineup.
	 *
	 * Los *closed captions* de `estrellashd` van **incrustados en el vídeo**
	 * como CEA-608 (`CLAUDE.md` §7), y eso lo expone el extractor HLS de
	 * Media3 pidiéndoselo. libVLC no entra por ese camino, y los subtítulos de
	 * pista aparte están fuera de alcance. Se registra para que quien llegue
	 * aquí sepa que el silencio es deliberado.
	 */
	override fun selectSubtitleTrack(track: AudioTrackRef?) {
		LiveLog.debug("vlc.subtitulosNoSoportados", "pedida" to track?.language)
	}

	/**
	 * El aspecto lo resuelve la vista, no el motor.
	 *
	 * `AspectoDeVideo` dimensiona la superficie, que es lo único que llega a la
	 * imagen —medido el 06-oct—, y eso vale igual con libVLC. Pedirle además a
	 * libVLC que escale sería escalar dos veces.
	 */
	override fun setAspectMode(mode: AspectMode) {
		// La vista cambia de tamaño al aplicar el modo, y libVLC no se entera
		// solo: hay que volver a decírselo.
		surface?.let { dimensionar(it) }
		LiveLog.debug("vlc.setAspectMode", "mode" to mode, "detalle" to "lo dimensiona la vista")
	}

	override fun addListener(listener: EngineListener) { listeners += listener }
	override fun removeListener(listener: EngineListener) { listeners -= listener }

	private fun notificar(bloque: (EngineListener) -> Unit) = listeners.forEach(bloque)

	private fun alEvento(evento: MediaPlayer.Event) {
		when (evento.type) {
			// **`Buffering` no significa lo mismo que en Media3.**
			//
			// libVLC lo emite de continuo mientras reproduce, con el porcentaje
			// de llenado: medido el 09-oct, 26 veces al 100 % en una sesión
			// normal. Traducirlo a `onReady()` —que es lo que hacía— dejaba el
			// estado en «Listo» en vez de «En vivo» una y otra vez, y la barra
			// de canal no se escondía nunca. El síntoma no se parecía en nada a
			// la causa.
			//
			// Lo que de verdad informa es el **flanco**: caer por debajo de 100
			// con imagen ya en pantalla es un rebuffer, y volver a 100 después
			// es haber salido de él.
			MediaPlayer.Event.Buffering -> {
				val lleno = evento.buffering >= 100f
				when {
					!lleno && firstFrameAtMs > 0L && !enRebuffer -> {
						enRebuffer = true
						rebuffers++
						LiveLog.warn("player.rebuffer", "numero" to rebuffers, "motor" to id)
						notificar { it.onBuffering() }
					}
					lleno && enRebuffer -> {
						enRebuffer = false
						LiveLog.debug("vlc.rebufferSuperado", "numero" to rebuffers)
						notificar { it.onPlaying() }
					}
				}
			}

			MediaPlayer.Event.Playing -> {
				LiveLog.debug("vlc.evento", "tipo" to "Playing")
				notificar { it.onPlaying() }
			}

			MediaPlayer.Event.Vout -> {
				if (evento.voutCount > 0 && firstFrameAtMs == 0L) {
					firstFrameAtMs = System.currentTimeMillis()
					LiveLog.info(
						"frames.first",
						"msDesdePrepare" to (firstFrameAtMs - prepareAtMs),
						"motor" to id,
					)
					leerFormato()
				}
			}

			MediaPlayer.Event.EncounteredError -> {
				// **No se cierra el reproductor** (`CLAUDE.md` §6): un canal
				// caído por lluvia es un estado temporal. libVLC no distingue
				// la causa, así que se declara SENAL, que es lo que acierta
				// casi siempre y lo que no manda a nadie a revisar la antena
				// por un fallo interno... al revés de lo que haría INTERNO.
				ultimaCausa = CausaDeFallo.SENAL
                LiveLog.error("player.error", null, "motor" to id, "evento" to "EncounteredError")
				notificar { it.onRecoverableError("vlcEncounteredError", null, ultimaCausa) }
			}

			MediaPlayer.Event.EndReached -> {
				// En directo no hay final. Si llega, la fuente se cortó.
				LiveLog.warn("vlc.finDeMedio", "detalle" to "en directo esto es un corte de la fuente")
				notificar { it.onRecoverableError("vlcEndReached", null, CausaDeFallo.SENAL) }
			}
		}
	}

	/**
	 * El latido, también aquí.
	 *
	 * **Vivía solo en `Media3LiveEngine`**, así que una sesión con libVLC no
	 * dejaba ni una línea de diagnóstico: cero latidos en el volcado del
	 * 09-oct. Todo el método de trabajo de este proyecto se apoya en ese
	 * rastro —se mide leyendo el volcado, no mirando— y un motor que no lo
	 * escribe es un motor que no se puede depurar en casa de un abonado.
	 *
	 * Lleva menos columnas que el de Media3 y eso es deliberado: solo las que
	 * libVLC sabe de verdad. Las que no, no se escriben, en vez de escribirse
	 * en cero y leerse igual que una medida.
	 */
	private fun arrancarLatido(alcance: CoroutineScope) {
		latido?.cancel()
		latido = alcance.launch {
			var n = 0
			while (isActive) {
				delay(LiveTuning.HEARTBEAT_INTERVAL_MS)
				val mp = player ?: continue
				n++
				val carga = runCatching { medidor.sample() }.getOrNull()
				carga?.let { ultimaCpu = it.cpuPercent }
				LiveLog.debug(
					"latido",
					"n" to n,
					"motor" to id,
					"estado" to if (mp.isPlaying) "reproduciendo" else "parado",
					"posS" to "%.1f".format(mp.time / 1000.0),
					"rebuffers" to rebuffers,
					"cpu" to carga?.let { "%.0f%%".format(it.cpuPercent) },
					"ramLibreMb" to carga?.ramAvailableMb,
				)
			}
		}
	}

	/** Tamaño, SAR y pistas de audio, en cuanto hay imagen. */
	private fun leerFormato() {
		val mp = player ?: return
		mp.currentVideoTrack?.let { v ->
			anchoVideo = v.width
			altoVideo = v.height
			// libVLC da el SAR como una fracción; 0 significa «no lo sé».
			sarVideo = if (v.sarDen > 0 && v.sarNum > 0) v.sarNum.toFloat() / v.sarDen else 1f
			LiveLog.info(
				"player.videoSize",
				"width" to anchoVideo,
				"height" to altoVideo,
				"sar" to sarVideo,
			)
			notificar { it.onVideoSizeChanged(anchoVideo, altoVideo, sarVideo) }
		}

		// **El idioma sale del medio, no del nombre de la pista.**
		//
		// `audioTracks` devuelve descripciones para pintar un menú, y la
		// primera es la pseudo-pista «Disable» de libVLC, con id negativo. Sin
		// filtrarla, la barra enseñaba «DI» como si fuera un idioma — visto en
		// la caja el 08-oct. El medio sí trae un campo `language` de verdad.
		val delMedio = runCatching {
			val media = mp.media ?: return@runCatching emptyList<IMedia.AudioTrack>()
			val lista = (0 until media.trackCount).mapNotNull { media.getTrack(it) }
				.filterIsInstance<IMedia.AudioTrack>()
			media.release()
			lista
		}.getOrDefault(emptyList())

		pistas = if (delMedio.isNotEmpty()) {
			delMedio.map { t ->
				AudioTrackRef(
					language = t.language,
					// **No hay PID.** libVLC no lo expone, y el PID tampoco
					// identificaba nada: Flussonic los renumera al remuxar y
					// entrega todos los canales con los mismos (§4.6). Se deja
					// en cero en vez de inventar un índice que se leería como
					// PID.
					pid = 0,
					channels = t.channels,
					label = t.description,
				)
			}
		} else {
			mp.audioTracks.orEmpty().filter { it.id >= 0 }.map { t ->
				AudioTrackRef(language = null, pid = 0, channels = 0, label = t.name)
			}
		}
		if (pistas.isNotEmpty()) {
			LiveLog.info("audio.pistas", "cuantas" to pistas.size, "motor" to id)
			notificar { it.onAudioTracksChanged(pistas) }
		}
	}

	/**
	 * Lo que se puede medir, y **solo** lo que se puede medir.
	 *
	 * libVLC no expone el nombre del decodificador activo, ni fotogramas
	 * perdidos, ni la ventana DVR, ni el offset al directo. Eso se queda en
	 * cero: el overlay enseñará huecos, que es honesto. Rellenarlos con
	 * estimaciones los haría indistinguibles de los de Media3, que sí están
	 * medidos, y la primera vez que alguien comparase los dos motores sacaría
	 * una conclusión falsa.
	 */
	override fun stats(): EngineStats {
		val mp = player
		val ahora = System.currentTimeMillis()
		return EngineStats(
			engineId = id,
			activeVideoDecoder = if (mp != null) "libvlc (software)" else null,
			activeAudioDecoder = if (mp != null) "libvlc" else null,
			videoWidth = anchoVideo,
			videoHeight = altoVideo,
			pixelAspectRatio = sarVideo,
			videoMime = channel?.videoCodec,
			// **`null`, no el códec declarado.** Este campo alimenta el aviso
			// de audio sin normalizar de §2, y ese aviso solo vale si dice lo
			// que **se está decodificando**. Poniendo aquí lo que el
			// `config.json` declara, el aviso compararía el dato consigo mismo
			// y dejaría de detectar justo el caso para el que existe: un site
			// que declara AAC y entrega MP2. libVLC no nos dice el MIME, así
			// que no se dice nada — visto en pantalla el 09-oct, donde ponerlo
			// mal encendía un «⚠AAC» sobre un canal correctamente normalizado.
			audioMime = null,
			cpuPercent = ultimaCpu,
			msToFirstFrame = if (firstFrameAtMs > 0) firstFrameAtMs - prepareAtMs else 0,
			rebufferCount = rebuffers,
			sessionUptimeMs = if (sesionDesdeMs > 0) ahora - sesionDesdeMs else 0,
			targetOffsetMs = plan?.targetOffsetMs ?: 0,
			reason = plan?.reason,
			degradation = dev.describe(),
			audioTracks = pistas,
			selectedAudio = pistaElegida,
		)
	}
}
