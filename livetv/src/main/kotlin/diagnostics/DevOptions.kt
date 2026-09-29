package org.jellyfin.livetv.diagnostics

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.livetv.LiveTuning

/**
 * Menú de degradación (LIVETV_ENGINE.md §7).
 *
 * La caja de pruebas es el mejor caso posible: trae MPEG-2, MP2 y AC-3 por
 * hardware. Sin poder empeorarla a propósito, los caminos de respaldo llegarían
 * a producción **sin haberse ejercitado nunca**. De ahí que este menú sea
 * obligatorio y no un extra.
 *
 * **Todos los valores por defecto reproducen el comportamiento actual**
 * (CLAUDE.md §3): abrir el menú no cambia nada por sí solo.
 */
class DevOptions(context: Context) {

	private val prefs: SharedPreferences =
		context.getSharedPreferences("livetv.dev", Context.MODE_PRIVATE)

	/** Cambia en cada modificación: la pantalla lo observa para rehacer el plan. */
	private val _revision = MutableStateFlow(0)
	val revision: StateFlow<Int> get() = _revision

	// --- Decoders ----------------------------------------------------------

	/** Decoders vetados por nombre exacto. El motor los excluye al seleccionar. */
	var vetoedDecoders: Set<String>
		get() = prefs.getStringSet(K_VETOED, emptySet()).orEmpty()
		set(v) = put { putStringSet(K_VETOED, v) }

	fun veto(decoder: String) { vetoedDecoders = vetoedDecoders + decoder }
	fun unveto(decoder: String) { vetoedDecoders = vetoedDecoders - decoder }
	fun isVetoed(decoder: String) = decoder in vetoedDecoders

	/**
	 * Fuerza decodificación de video por software descartando los decoders de
	 * hardware. Es el interruptor que ejercita el camino de respaldo.
	 */
	var forceSoftwareVideo: Boolean
		get() = prefs.getBoolean(K_FORCE_SW, false)
		set(v) = put { putBoolean(K_FORCE_SW, v) }

	/** `null` = cascada automática. Si no, `"media3"` o `"libvlc"`. */
	var forcedEngine: String?
		get() = prefs.getString(K_FORCED_ENGINE, null)
		set(v) = put { if (v == null) remove(K_FORCED_ENGINE) else putString(K_FORCED_ENGINE, v) }

	// --- Flags del extractor TS --------------------------------------------
	//
	// Valores base OFF/OFF porque los canales traen AUDs e IDR (medido). Pero
	// la experiencia de campo en Amlogic sugiere lo contrario, y resolver esa
	// contradicción es la fase M4: por eso tienen que poder cambiarse en
	// caliente, sin recompilar. En HLS no se heredan de
	// `DefaultMediaSourceFactory`: van vía `DefaultHlsExtractorFactory`.

	var tsDetectAccessUnits: Boolean
		get() = prefs.getBoolean(K_TS_AUD, false)
		set(v) = put { putBoolean(K_TS_AUD, v) }

	var tsAllowNonIdrKeyframes: Boolean
		get() = prefs.getBoolean(K_TS_NONIDR, false)
		set(v) = put { putBoolean(K_TS_NONIDR, v) }

	/**
	 * Devuelve el umbral de playlist detenido al valor de Media3 (3.5), para
	 * poder comparar en pantalla el comportamiento antiguo con el nuevo.
	 */
	var playlistStuckMedia3Default: Boolean
		get() = prefs.getBoolean(K_STUCK_DEFAULT, false)
		set(v) = put { putBoolean(K_STUCK_DEFAULT, v) }

	val playlistStuckCoefficient: Double
		get() = if (playlistStuckMedia3Default) LiveTuning.PLAYLIST_STUCK_COEFFICIENT_MEDIA3
		else LiveTuning.PLAYLIST_STUCK_COEFFICIENT

	/**
	 * Desactiva la vuelta automática al borde de emisión, para poder comparar
	 * en pantalla con el comportamiento anterior.
	 */
	var liveEdgeResyncDisabled: Boolean
		get() = prefs.getBoolean(K_NO_RESYNC, false)
		set(v) = put { putBoolean(K_NO_RESYNC, v) }

	/**
	 * Reproducción **tunneled**: el decodificador entrega directamente al
	 * hardware de pantalla y el SoC gestiona sincronía, desentrelazado y
	 * cadencia en su propio pipeline.
	 *
	 * Es el camino que usan las apps nativas de TV en cajas Amlogic, y el
	 * decodificador lo declara (`<Feature name="tunneled-playback"/>`).
	 * Explicaría por qué otros reproductores manejan bien el cine con cadencia
	 * 3:2 que a nosotros nos da tirones.
	 *
	 * **Apagado por defecto** (`CLAUDE.md` §6): su fallo produce pantalla negra
	 * silenciosa, así que se valida antes de activarlo por override remoto.
	 */
	var tunnelingEnabled: Boolean
		get() = prefs.getBoolean(K_TUNNELING, false)
		set(v) = put { putBoolean(K_TUNNELING, v) }

	/**
	 * Amplía la ventana de búsqueda de timestamps del extractor TS.
	 *
	 * Es la hipótesis para `estrellashd`: si el extractor no halla referencias
	 * temporales, nunca establece el formato de vídeo.
	 */
	var wideTimestampSearch: Boolean
		get() = prefs.getBoolean(K_WIDE_SEARCH, false)
		set(v) = put { putBoolean(K_WIDE_SEARCH, v) }

	val timestampSearchPackets: Int
		get() = if (wideTimestampSearch) LiveTuning.TS_TIMESTAMP_SEARCH_PACKETS
		else LiveTuning.TS_TIMESTAMP_SEARCH_PACKETS_MEDIA3

	// --- Sintonía en caliente ----------------------------------------------

	/** `null` = el del canal. Sirve también para provocar BEHIND_LIVE_WINDOW a voluntad. */
	var targetOffsetOverrideMs: Long?
		get() = prefs.getLong(K_TARGET_OFFSET, -1L).takeIf { it >= 0 }
		set(v) = put { if (v == null) remove(K_TARGET_OFFSET) else putLong(K_TARGET_OFFSET, v) }

	/** `null` = los de `LiveTuning`. */
	var minBufferOverrideMs: Int?
		get() = prefs.getInt(K_MIN_BUFFER, -1).takeIf { it >= 0 }
		set(v) = put { if (v == null) remove(K_MIN_BUFFER) else putInt(K_MIN_BUFFER, v) }

	var maxBufferOverrideMs: Int?
		get() = prefs.getInt(K_MAX_BUFFER, -1).takeIf { it >= 0 }
		set(v) = put { if (v == null) remove(K_MAX_BUFFER) else putInt(K_MAX_BUFFER, v) }

	val minBufferMs: Int get() = minBufferOverrideMs ?: LiveTuning.MIN_BUFFER_MS
	val maxBufferMs: Int get() = maxBufferOverrideMs ?: LiveTuning.MAX_BUFFER_MS

	// --- Overlay ------------------------------------------------------------

	/**
	 * Overlay de estadísticas: 0 oculto, 1 compacto, 2 completo.
	 *
	 * El compacto existe porque el completo ocupa un tercio de la pantalla y
	 * estorba justo cuando hay que juzgar imagen. Dos líneas bastan para ver
	 * decodificadores y coste en la caja mientras se mira el canal.
	 */
	var overlayMode: Int
		get() = prefs.getInt(K_OVERLAY_MODE, OVERLAY_OCULTO)
		set(v) = put { putInt(K_OVERLAY_MODE, v.coerceIn(OVERLAY_OCULTO, OVERLAY_COMPLETO)) }

	fun siguienteOverlay() {
		overlayMode = if (overlayMode >= OVERLAY_COMPLETO) OVERLAY_OCULTO else overlayMode + 1
	}

	fun describeOverlay(): String = when (overlayMode) {
		OVERLAY_COMPACTO -> "compacto"
		OVERLAY_COMPLETO -> "completo"
		else -> "oculto"
	}

	// --- Estado -------------------------------------------------------------

	/** ¿Hay algo que aleje el comportamiento del de fábrica? Se avisa en el overlay. */
	val isDegraded: Boolean
		get() = vetoedDecoders.isNotEmpty() || forceSoftwareVideo || forcedEngine != null ||
			playlistStuckMedia3Default || liveEdgeResyncDisabled || tunnelingEnabled ||
			wideTimestampSearch ||
			tsDetectAccessUnits || tsAllowNonIdrKeyframes ||
			targetOffsetOverrideMs != null || minBufferOverrideMs != null ||
			maxBufferOverrideMs != null

	fun describe(): String = buildList {
		if (vetoedDecoders.isNotEmpty()) add("vetados=${vetoedDecoders.size}")
		if (forceSoftwareVideo) add("video=software")
		forcedEngine?.let { add("motor=$it") }
		if (playlistStuckMedia3Default) add("stuck=3.5")
		if (liveEdgeResyncDisabled) add("sin resync")
		if (tunnelingEnabled) add("tunneled")
		if (wideTimestampSearch) add("búsqueda amplia")
		if (tsDetectAccessUnits) add("AUD=on")
		if (tsAllowNonIdrKeyframes) add("nonIDR=on")
		targetOffsetOverrideMs?.let { add("offset=${it}ms") }
		minBufferOverrideMs?.let { add("minBuf=${it}ms") }
		maxBufferOverrideMs?.let { add("maxBuf=${it}ms") }
	}.joinToString(" ").ifEmpty { "de fábrica" }

	/**
	 * Devuelve la **degradación** a valores de fábrica.
	 *
	 * El overlay se conserva a propósito: es instrumentación, no degradación, y
	 * apagarle al operador la vista justo cuando restablece para comparar sería
	 * quitarle el instrumento en mitad de la medición.
	 */
	fun resetAll() {
		val overlay = overlayMode
		prefs.edit().clear().putInt(K_OVERLAY_MODE, overlay).apply()
		LiveLog.info("dev.reset", "overlayConservado" to describeOverlay())
		_revision.value++
	}

	private inline fun put(block: SharedPreferences.Editor.() -> Unit) {
		prefs.edit().apply(block).apply()
		LiveLog.info("dev.changed", "opciones" to describe())
		_revision.value++
	}

	companion object {
		@Volatile
		private var instance: DevOptions? = null

		/** Una sola instancia: el menú y el reproductor comparten `revision`. */
		fun of(context: Context): DevOptions =
			instance ?: synchronized(this) {
				instance ?: DevOptions(context.applicationContext).also { instance = it }
			}

		const val K_VETOED = "vetoedDecoders"
		const val K_FORCE_SW = "forceSoftwareVideo"
		const val K_FORCED_ENGINE = "forcedEngine"
		const val K_STUCK_DEFAULT = "playlistStuckMedia3Default"
		const val K_NO_RESYNC = "liveEdgeResyncDisabled"
		const val K_TUNNELING = "tunnelingEnabled"
		const val K_WIDE_SEARCH = "wideTimestampSearch"
		const val K_TS_AUD = "tsDetectAccessUnits"
		const val K_TS_NONIDR = "tsAllowNonIdrKeyframes"
		const val K_TARGET_OFFSET = "targetOffsetOverrideMs"
		const val K_MIN_BUFFER = "minBufferOverrideMs"
		const val K_MAX_BUFFER = "maxBufferOverrideMs"
		const val K_OVERLAY_MODE = "overlayMode"

		const val OVERLAY_OCULTO = 0
		const val OVERLAY_COMPACTO = 1
		const val OVERLAY_COMPLETO = 2
	}
}
