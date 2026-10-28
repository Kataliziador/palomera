package org.jellyfin.livetv.engine

import android.view.SurfaceView
import org.jellyfin.livetv.source.AudioTrackRef
import org.jellyfin.livetv.source.ChannelProfile

/** Cómo se decodifica. Lo elige la cascada de LIVETV_ENGINE.md §4.2. */
enum class DecodePath { HW, SW_PLATFORM, SW_VLC }

/**
 * Quién desentrelaza, si alguien.
 *
 * - [NONE] — nadie, porque el canal no lo necesita.
 * - [VPU] — se espera que lo haga el chip de la caja. **No se puede verificar**
 *   (`LIVETV_ENGINE.md` §4.4): no hay API que lo diga, se mira en pantalla una
 *   vez por modelo y el resultado se publica en `deviceOverrides`. Leer
 *   `deint=VPU` en el log significa «la app no ha hecho nada y confía», no «hay
 *   desentrelazado».
 * - [SOFTWARE] — lo hace libVLC con yadif. Es el único valor en el que la app
 *   ejecuta algo, y existe desde M6.
 *
 * **Había un cuarto, `GPU_SHADER`, retirado el 09-oct** (pendiente 54). Era el
 * nivel 3 de la cascada —un shader en la GPU para el 1080i sin VPU— y
 * `PlanBuilder` no lo producía en ninguna rama: un valor que nadie podía
 * devolver y que, puesto en un `enum`, se leía como una capacidad que esta app
 * tiene. Si algún día se escribe, se añade entonces.
 */
enum class DeinterlaceMode { NONE, VPU, SOFTWARE }

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
	/** No hay forma de reproducir este canal en esta caja. Se avisa, no se calla. */
	val unsupported: Boolean = false,
)

data class EngineStats(
	val engineId: String,
	val activeVideoDecoder: String? = null,
	val activeAudioDecoder: String? = null,
	val bufferedMs: Long = 0,
	/** Bitrate real del canal, medido sobre los bytes descargados. */
	val bitrateBps: Long = 0,
	/** Estimación de ancho de banda de la red. Otra cosa distinta. */
	val bandwidthBps: Long = 0,
	val droppedFrames: Int = 0,
	/**
	 * Bloques que el demuxer recibió corruptos, o `null` si el motor no lo mide.
	 *
	 * **Es la señal de lluvia**, y es distinta de un frame perdido: perder un
	 * frame puede ser que la caja no dé abasto; recibir un bloque corrupto solo
	 * puede venir del transporte. Sin separarlos, «se ve a saltos» tiene dos
	 * causas que en pantalla se parecen y se arreglan en sitios opuestos.
	 *
	 * **`null` y no `0` a propósito.** Media3 no publica nada equivalente, y un
	 * cero diría «no hubo corrupción» donde la verdad es «nadie miró». Es la
	 * misma regla que ya rige para `videoMime` y `loadAvg1`: un hueco se lee
	 * «no lo sé», un cero se lee como una medida.
	 *
	 * **Este contador no se ha visto moverse nunca.** El 27-oct se ejercitó el
	 * camino degradado con el 60 % de los segmentos truncados y se quedó en 0
	 * mientras `demuxDiscontinuity` y `lostAudioBuffers` sí subían: truncar
	 * produce discontinuidad, no bloques corruptos. Para moverlo hace falta el
	 * golden sample con el bit de error de transporte puesto
	 * (`herramientas/corromper-ts.py --tei`) servido en bucle;
	 * `herramientas/lluvia.py` no corrompe bits **a propósito**, y su propio
	 * encabezado dice por qué. Hasta que eso se haga, un 0 aquí significa «no
	 * he visto corrupción», **no** «sé que este contador funciona».
	 */
	val demuxCorrupted: Int? = null,
	/**
	 * Discontinuidades vistas por el demuxer, o `null` si el motor no lo mide.
	 *
	 * **Se mueve, y todavía no se sabe con qué.** Tres corridas del 27-oct
	 * sobre `estrellashd` con libVLC forzado: la primera por el camino normal
	 * dio **0**, la segunda con el 60 % de los segmentos truncados dio **22-27**
	 * y la tercera, **otra vez por el camino normal, dio 15-17**. O sea que el
	 * contador no está muerto —que es lo que había que demostrar— pero la
	 * tercera corrida **refuta** la lectura fácil de las dos primeras: esto
	 * todavía no distingue un canal sano de uno degradado. Hace falta repetirlo
	 * con varias corridas por condición antes de apoyar nada en el número.
	 */
	val demuxDiscontinuity: Int? = null,
	/** Buffers de audio perdidos, o `null` si el motor no lo mide. El equivalente
	 *  sonoro de `droppedFrames`: es lo que hay detrás de «se oye entrecortado». */
	val lostAudioBuffers: Int? = null,
	/** Tiempo sin que avance la posición: es lo que delata imagen congelada. */
	val msSinceProgress: Long = 0,
	val liveOffsetMs: Long = 0,
	/** Ventana DVR medida del playlist. Es lo que acota el `targetOffsetMs`. */
	val windowMs: Long = 0,
	val targetOffsetMs: Long = 0,
	val reason: String? = null,
	/** Resumen de la degradación activa, o «de fábrica». */
	val degradation: String? = null,

	// --- Métricas de los objetivos de CLAUDE.md §10 ------------------------

	/** `prepare` → primer frame. Es la medida de zapping (objetivo p50 < 2 s). */
	val msToFirstFrame: Long = 0,
	/** Rebuffers desde que arrancó la imagen (objetivo: < 1 por hora). */
	val rebufferCount: Int = 0,
	/** Fracción del tiempo de reproducción pasada almacenando (objetivo < 0.3 %). */
	val rebufferRatio: Float = 0f,
	/** Tiempo del canal en pantalla, para la maratón de 48 h. */
	val sessionUptimeMs: Long = 0,
	/** Reintentos de carga acumulados: delata lluvia antes de que se vea. */
	val loadRetries: Int = 0,

	// --- Render, para M5 ---------------------------------------------------

	val videoWidth: Int = 0,
	val videoHeight: Int = 0,
	/** SAR. Ignorarlo deforma los dos canales anamórficos. */
	val pixelAspectRatio: Float = 1f,
	/**
	 * El MIME del vídeo **observado en el stream**, no el declarado.
	 *
	 * Lo necesita la cascada: el códec del `config.json` es una pista y el
	 * stream es la verdad. Si el headend recodifica un canal y nadie actualiza
	 * el JSON, el plan se habría construido sobre un dato falso (§4.6).
	 */
	val videoMime: String? = null,
	/**
	 * El MIME del audio que suena.
	 *
	 * Está aquí para que se vea en pantalla: el nombre del decoder no basta
	 * —`OMX.amlogic.audio.decoder.mp2` se resume como «amlogic.audio» y pierde
	 * el códec— y es justo el dato que distingue un site bien normalizado de
	 * uno que va a dejar mudas las cajas baratas (`CLAUDE.md` §2).
	 */
	val audioMime: String? = null,
	/** fps medido sobre los frames realmente renderizados, no el declarado. */
	val measuredFps: Float = 0f,
	/** Frames que salieron a pantalla tarde. No son frames perdidos: llegan, pero tarde. */
	val frameGapCount: Int = 0,
	val maxFrameGapMs: Long = 0,

	// --- Recuperación ------------------------------------------------------

	/** El vigilante está intentando rescatar la reproducción ahora mismo. */
	val recovering: Boolean = false,
	/** Escalones de recuperación acumulados en la sesión. */
	val recoveryAttempts: Int = 0,

	// --- Lo que le cuesta a la caja ---------------------------------------
	//
	// Un microcorte tiene dos familias de causa que en pantalla se parecen: el
	// stream llega mal, o la caja no da abasto. Sin estos tres números no se
	// distinguen.

	/** CPU de nuestro proceso como porcentaje de la caja entera. */
	val cpuPercent: Float = 0f,
	/** Carga media del sistema a 1 minuto, o `null` si el kernel no la expone. */
	val loadAvg1: Float? = null,
	val ramAvailableMb: Int = 0,
	val ramTotalMb: Int = 0,
	val cores: Int = 0,

	// --- Audio, para M3 ----------------------------------------------------

	val audioTracks: List<AudioTrackRef> = emptyList(),
	val selectedAudio: AudioTrackRef? = null,

	// --- Subtítulos --------------------------------------------------------
	//
	// En este lineup van **incrustados en el vídeo** como CEA-608, no como
	// pista aparte: solo aparecen si se le pide al extractor que los busque.

	val subtitleTracks: List<AudioTrackRef> = emptyList(),
	val selectedSubtitle: AudioTrackRef? = null,
)

/**
 * Qué clase de problema es, para que la pantalla no mienta sobre la causa.
 *
 * **Importa porque el mensaje dirige a quien lo lee.** Decir «señal débil por
 * condiciones climáticas» ante una aserción interna de Media3 manda a un
 * técnico a revisar una antena que está perfecta, y le quita media hora.
 *
 * Esa frase ya no se usa —desde el 19-oct el aviso de señal dice «Recuperando
 * la señal…», porque la app tampoco puede saber si llueve—, pero la distinción
 * sigue haciendo falta: lo que el reproductor rompe por dentro y lo que llega
 * roto de fuera no se arreglan en el mismo sitio.
 */
enum class CausaDeFallo {
	/** Red, segmentos, playlist: lo que la lluvia provoca de verdad. */
	SENAL,

	/** Del reproductor o del decodificador. No es el satélite. */
	INTERNO,
}

interface EngineListener {
	fun onReady() {}
	fun onPlaying() {}
	fun onBuffering() {}

	/**
	 * Un error que el motor ya está gestionando. **No significa que haya que
	 * cerrar el player**: un canal caído por lluvia es un estado temporal
	 * (CLAUDE.md §6).
	 */
	fun onRecoverableError(event: String, error: Throwable?, causa: CausaDeFallo = CausaDeFallo.SENAL) {}

	/** El canal no se puede reproducir en esta caja. Agotada la cascada. */
	fun onFatalError(event: String, error: Throwable?) {}

	fun onAudioTracksChanged(tracks: List<AudioTrackRef>) {}

	/**
	 * Las líneas de subtítulo que deben verse **ahora**.
	 *
	 * Llega por aviso y no por sondeo: los subtítulos cambian cada pocos
	 * segundos y leerlos cada medio segundo los dejaría llegar tarde o
	 * duplicados.
	 */
	fun onCues(lineas: List<String>) {}
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

	/** `null` apaga los subtítulos. Misma regla de identidad que el audio. */
	fun selectSubtitleTrack(track: AudioTrackRef?)

	fun setAspectMode(mode: AspectMode)
	fun addListener(listener: EngineListener)
	fun removeListener(listener: EngineListener)
	fun stats(): EngineStats
}
