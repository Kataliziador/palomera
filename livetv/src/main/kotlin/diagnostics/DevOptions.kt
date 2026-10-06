package org.jellyfin.livetv.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
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
/**
 * Lo que el servidor de configuración dice para esta caja.
 *
 * Tipo propio y no el `DeviceOverride` del esquema: el diagnóstico no tiene
 * por qué saber cómo está escrito el `config.json`, y si mañana cambia el
 * formato, esto no se entera. `null` en un campo = el servidor no opina.
 */
data class ServerDeviceSettings(
	val engine: String? = null,
	val tunneling: Boolean? = null,
	val tsAllowNonIdrKeyframes: Boolean? = null,
	val tsDetectAccessUnits: Boolean? = null,
	val forceSoftwareVideo: Boolean? = null,
	val targetOffsetMs: Long? = null,
	/**
	 * Si el VPU de este modelo desentrelaza.
	 *
	 * No se puede consultar por API (`LIVETV_ENGINE.md` §4.4): se valida
	 * mirando la pantalla una vez por modelo y el resultado se publica aquí.
	 */
	val vpuDeinterlaces: Boolean? = null,
)

class DevOptions(context: Context) {

	private val prefs: SharedPreferences =
		context.getSharedPreferences("livetv.dev", Context.MODE_PRIVATE)

	/**
	 * Lo que el servidor de configuración dice para **esta** caja.
	 *
	 * Capa intermedia de tres: **fábrica → servidor → menú local**. El menú
	 * gana porque es la herramienta con la que se depura una caja concreta; si
	 * el servidor pudiera pisarlo, no habría forma de probar nada en campo.
	 * Pero gana **solo si alguien lo tocó**: un valor sin tocar no es una
	 * opinión.
	 *
	 * Esto es lo que hace real la promesa de `CLAUDE.md` §6 —excluir tunneled
	 * en los modelos que no lo soporten sin recompilar— y lo que convierte una
	 * caja nueva que falla en un cambio de `config.json` en vez de una visita.
	 */
	@Volatile
	private var delServidor: ServerDeviceSettings? = null

	val ajustesDelServidor: ServerDeviceSettings? get() = delServidor

	fun aplicarDelServidor(ajustes: ServerDeviceSettings?) {
		if (ajustes == delServidor) return
		delServidor = ajustes
		LiveLog.info(
			"dev.ajustesDelServidor",
			"tunneling" to ajustes?.tunneling,
			"nonIDR" to ajustes?.tsAllowNonIdrKeyframes,
			"AUD" to ajustes?.tsDetectAccessUnits,
			"software" to ajustes?.forceSoftwareVideo,
			"motor" to ajustes?.engine,
		)
		_revision.value++
	}

	/** De dónde salió cada valor. Se enseña en el menú y se registra. */
	fun procedenciaDe(clave: String): String = when {
		prefs.contains(clave) -> "menú"
		delServidor != null && servidorDefine(clave) -> "servidor"
		else -> "fábrica"
	}

	private fun servidorDefine(clave: String): Boolean = when (clave) {
		K_TUNNELING -> delServidor?.tunneling != null
		K_TS_NONIDR -> delServidor?.tsAllowNonIdrKeyframes != null
		K_TS_AUD -> delServidor?.tsDetectAccessUnits != null
		K_FORCE_SW -> delServidor?.forceSoftwareVideo != null
		else -> false
	}

	private fun tresCapas(clave: String, delServidor: Boolean?, deFabrica: Boolean): Boolean =
		if (prefs.contains(clave)) prefs.getBoolean(clave, deFabrica)
		else delServidor ?: deFabrica

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
		get() = tresCapas(K_FORCE_SW, delServidor?.forceSoftwareVideo, LiveTuning.FORCE_SOFTWARE_VIDEO_DEFAULT)
		set(v) = put { putBoolean(K_FORCE_SW, v) }

	/**
	 * Sustituye la URL del `config.json` derivada del host de Jellyfin.
	 *
	 * Existe para poder apuntar una caja a un servidor de canales de pruebas
	 * sin tocar su Jellyfin. **Degrada**, y a propósito: una caja que mira a
	 * otro lineup no está midiendo lo que parece.
	 */
	var configUrlOverride: String?
		get() = prefs.getString(K_CONFIG_URL, null)?.takeIf { it.isNotBlank() }
		set(v) = put { if (v.isNullOrBlank()) remove(K_CONFIG_URL) else putString(K_CONFIG_URL, v) }

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
		get() = tresCapas(K_TS_AUD, delServidor?.tsDetectAccessUnits, LiveTuning.TS_DETECT_ACCESS_UNITS_DEFAULT)
		set(v) = put { putBoolean(K_TS_AUD, v) }

	/**
	 * **Activo de fábrica desde el 01-oct**, con el visto bueno de Edgar.
	 *
	 * `estrellashd` no emite ni un solo fotograma IDR —medido el 26-sep: 277
	 * slices no-IDR, 0 IDR, 4 Recovery Point— y sin este flag el canal no
	 * arranca. Barrido sobre los seis canales el 26-sep y A/B de tres sintonías
	 * el mismo día: no perjudica a ninguno, porque el flag solo lo consume el
	 * lector de H.264 cuando no halla IDR.
	 *
	 * Que esté apagado es ahora la desviación, no al revés: dejarlo así rompe
	 * un canal y además impide que el vigilante lo recupere tras un corte, ya
	 * que no hay IDR donde reengancharse.
	 */
	var tsAllowNonIdrKeyframes: Boolean
		get() = tresCapas(K_TS_NONIDR, delServidor?.tsAllowNonIdrKeyframes, LiveTuning.TS_ALLOW_NON_IDR_DEFAULT)
		set(v) = put { putBoolean(K_TS_NONIDR, v) }

	/**
	 * Vigilante de recuperación. **Activo de fábrica.**
	 *
	 * El interruptor existe para poder reproducir el comportamiento anterior,
	 * que era quedarse congelado indefinidamente: el 30-sep fueron siete horas
	 * y cuarto de imagen muerta con el buffer lleno.
	 */
	/**
	 * Aplica el idioma de audio que pide el `config.json` al sintonizar.
	 *
	 * **Activo de fábrica** desde el 02-oct. Apagarlo reproduce el
	 * comportamiento anterior: arrancar con la pista que elija Media3.
	 */
	var audioDefaultEnabled: Boolean
		get() = prefs.getBoolean(K_AUDIO_DEFAULT, LiveTuning.AUDIO_DEFAULT_ENABLED)
		set(v) = put { putBoolean(K_AUDIO_DEFAULT, v) }

	var watchdogDisabled: Boolean
		get() = prefs.getBoolean(K_WATCHDOG_OFF, false)
		set(v) = put { putBoolean(K_WATCHDOG_OFF, v) }

	/**
	 * Apagar el tunneled solo cuando se demuestra que no da imagen.
	 *
	 * Nace apagado porque es un cambio de comportamiento del motor y el valor
	 * de fábrica tiene que ser el anterior hasta que se valide en pantalla.
	 */
	/**
	 * Apuntar el registro a otro sitio, para probar sin tocar el de producción.
	 *
	 * Se pone como el override del `config.json`: empujando el archivo de
	 * preferencias con `adb`. No tiene fila propia en el menú porque no es algo
	 * que se cambie con el mando, pero sí se **enseña** cuál está en uso: una
	 * caja apuntada a un registro de pruebas y olvidada es exactamente el tipo
	 * de estado pegado que ya costó dos conclusiones falsas.
	 */
	val registryUrlOverride: String?
		get() = prefs.getString(K_REGISTRY_URL, null)?.trim()?.takeIf { it.isNotEmpty() }

	var rescateTunneledEnabled: Boolean
		get() = prefs.getBoolean(K_RESCATE_TUNNELED, LiveTuning.RESCATE_TUNNELED_DEFAULT)
		set(v) = put { putBoolean(K_RESCATE_TUNNELED, v) }

	/**
	 * Fingir que no llegó el primer fotograma, para poder ver el rescate.
	 *
	 * Sin esto no hay forma de probarlo: la caja de pruebas entrega imagen con
	 * tunneled, que es justo lo que queremos. Es el menú de degradación de §9
	 * —simular cajas peores— aplicado al único fallo que no podemos reproducir
	 * con el hardware que tenemos delante.
	 */
	var simularTunneledSinImagen: Boolean
		get() = prefs.getBoolean(K_SIM_SIN_IMAGEN, false)
		set(v) = put { putBoolean(K_SIM_SIN_IMAGEN, v) }

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
	 * Entregar la superficie al motor **solo cuando cambia**, en vez de en cada
	 * recomposición.
	 *
	 * El `update` de un `AndroidView` corre cada vez que se recompone, y las
	 * estadísticas del overlay recomponen cada 500 ms: medido, 206 llamadas a
	 * `engine.attach` en 115 s. Eso **rota el buffer de logcat en menos de un
	 * minuto**, y M4 y M5 se juzgan mirando la pantalla y el log a la vez.
	 *
	 * **Activo de fábrica desde el 06-oct**, validado en pantalla por Edgar.
	 * El interruptor se queda para reproducir el comportamiento anterior, que
	 * es lo que lo hace útil si algún modelo se queda sin imagen.
	 *
	 * **Por qué merece un interruptor y no es un arreglo evidente:** `attach`
	 * vuelve a entregar la superficie al reproductor, y una caja que recree su
	 * `Surface` por detrás podría estar dependiendo, sin que nadie lo supiera,
	 * de que eso ocurriera dos veces por segundo. Si al apagar las llamadas
	 * repetidas algún modelo se queda sin imagen, este interruptor lo demuestra
	 * en diez segundos en vez de costar una tarde.
	 */
	/**
	 * Dar a la superficie **el tamaño** que pide el modo de imagen, en vez de
	 * escalar la vista.
	 *
	 * Escalar la vista no llega a la superficie: medido el 06-oct sobre
	 * `estrellassd`, las escalas salían bien y distintas por modo y la pantalla
	 * no cambiaba. Un `SurfaceView` lo compone el sistema aparte del árbol de
	 * vistas; lo que sí fija sus límites es su tamaño de disposición.
	 *
	 * **Lo que arregla no son los modos manuales, es el modo normal.** Nadie
	 * fija el modo de escalado del códec, así que MediaCodec estira el vídeo
	 * hasta las dimensiones de la superficie sin respetar la proporción. Con la
	 * superficie siempre a pantalla completa, los dos canales anamórficos
	 * llevaban saliendo deformados y «Ajustar» no corregía nada.
	 *
	 * **Activo de fábrica desde el 06-oct**, validado en pantalla por Edgar:
	 * `estrellassd` sale con sus franjas y sin deformar, `estrellashd` —que es
	 * 1440×1080 con SAR 4:3— llena la pantalla sin franjas, y los canales 16:9
	 * no cambian. El interruptor se queda para reproducir el comportamiento
	 * anterior.
	 */
	var superficieDimensionada: Boolean
		get() = prefs.getBoolean(K_SUPERFICIE_DIM, true)
		set(v) = put { putBoolean(K_SUPERFICIE_DIM, v) }

	var attachSoloAlCambiar: Boolean
		get() = prefs.getBoolean(K_ATTACH_AL_CAMBIAR, true)
		set(v) = put { putBoolean(K_ATTACH_AL_CAMBIAR, v) }

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
	/**
	 * **Activo de fábrica desde el 01-oct**, por decisión de Edgar.
	 *
	 * Es lo único medido que quita los microcortes del cine en H.264. Validado
	 * en pantalla dos veces, sin penalización de zapping.
	 *
	 * El modelo es de **exclusión**: se excluye por `deviceOverrides` del
	 * `config.json` a los modelos que no deban usarlo, no al revés. Ver
	 * `CLAUDE.md` §6.
	 */
	var tunnelingEnabled: Boolean
		get() = tresCapas(K_TUNNELING, delServidor?.tunneling, LiveTuning.TUNNELING_DEFAULT)
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

	/**
	 * Los flags del extractor TS, ya resueltos por las tres capas.
	 *
	 * Vive aquí y no en el motor porque es quien conoce las tres capas, y
	 * porque el plan de reproducción tiene que poder llevarlos: así el motivo
	 * que se ve en el overlay describe de verdad cómo se está reproduciendo.
	 */
	val tsFlags: Int
		get() {
			var f = 0
			// Las constantes se importan, nunca se copian: `1 shl 2` no es
			// `ALLOW_NON_IDR_KEYFRAMES` sino `IGNORE_H264_STREAM`, y copiarlas
			// mal dejó los cinco canales H.264 con audio y pantalla negra.
			if (tsDetectAccessUnits) f = f or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
			if (tsAllowNonIdrKeyframes) f = f or DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
			return f
		}
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
	/**
	 * ¿Hay algo que el **menú local** esté pisando?
	 *
	 * Se mide por las claves escritas en preferencias, no comparando valores
	 * contra constantes: con tres capas, un valor puede coincidir con el de
	 * fábrica y venir del servidor, y eso no es una degradación. Lo que importa
	 * es si alguien tocó esta caja a mano.
	 */
	val isDegraded: Boolean
		get() = CLAVES_DEL_MENU.any { prefs.contains(it) }

	fun describe(): String = buildList {
		if (vetoedDecoders.isNotEmpty()) add("vetados=${vetoedDecoders.size}")
		if (prefs.contains(K_FORCE_SW) && forceSoftwareVideo) add("video=software")
		forcedEngine?.let { add("motor=$it") }
		if (configUrlOverride != null) add("config=manual")
		if (playlistStuckMedia3Default) add("stuck=3.5")
		if (!superficieDimensionada) add("superficie a pantalla completa")
		if (!attachSoloAlCambiar) add("attach en cada recomposición")
		if (liveEdgeResyncDisabled) add("sin resync")
		if (watchdogDisabled) add("sin vigilante")
		if (simularTunneledSinImagen) add("FINGE SIN IMAGEN")
		if (registryUrlOverride != null) add("registro=manual")
		if (prefs.contains(K_RESCATE_TUNNELED)) add("rescateTunneled=${if (rescateTunneledEnabled) "ON" else "OFF"}")
		if (prefs.contains(K_AUDIO_DEFAULT)) add("audioPorDefecto=${if (audioDefaultEnabled) "ON" else "OFF"}")
		if (wideTimestampSearch) add("búsqueda amplia")
		if (prefs.contains(K_TS_AUD)) add("AUD=${if (tsDetectAccessUnits) "on" else "off"}")
		if (prefs.contains(K_TS_NONIDR)) add("nonIDR=${if (tsAllowNonIdrKeyframes) "ON" else "OFF"}")
		if (prefs.contains(K_TUNNELING)) add("tunneled=${if (tunnelingEnabled) "ON" else "OFF"}")
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
		const val K_CONFIG_URL = "configUrlOverride"
		const val K_STUCK_DEFAULT = "playlistStuckMedia3Default"
		const val K_NO_RESYNC = "liveEdgeResyncDisabled"
		const val K_TUNNELING = "tunnelingEnabled"
		const val K_WIDE_SEARCH = "wideTimestampSearch"
		const val K_TS_AUD = "tsDetectAccessUnits"
		const val K_TS_NONIDR = "tsAllowNonIdrKeyframes"
		const val K_WATCHDOG_OFF = "watchdogDisabled"
		const val K_AUDIO_DEFAULT = "audioDefaultEnabled"
		const val K_TARGET_OFFSET = "targetOffsetOverrideMs"
		const val K_MIN_BUFFER = "minBufferOverrideMs"
		const val K_MAX_BUFFER = "maxBufferOverrideMs"
		const val K_OVERLAY_MODE = "overlayMode"
		const val K_RESCATE_TUNNELED = "rescateTunneledEnabled"
		const val K_SIM_SIN_IMAGEN = "simularTunneledSinImagen"
		const val K_REGISTRY_URL = "registryUrlOverride"
		const val K_ATTACH_AL_CAMBIAR = "attachSoloAlCambiar"
		const val K_SUPERFICIE_DIM = "superficieDimensionada"

		/** Las que el menú local puede pisar. El overlay no cuenta: es instrumento. */
		private val CLAVES_DEL_MENU = listOf(
			K_VETOED, K_FORCE_SW, K_FORCED_ENGINE, K_CONFIG_URL, K_STUCK_DEFAULT, K_NO_RESYNC,
			K_TUNNELING, K_WATCHDOG_OFF, K_WIDE_SEARCH, K_TS_AUD, K_TS_NONIDR,
			K_TARGET_OFFSET, K_MIN_BUFFER, K_MAX_BUFFER, K_AUDIO_DEFAULT,
			K_RESCATE_TUNNELED, K_SIM_SIN_IMAGEN, K_REGISTRY_URL, K_ATTACH_AL_CAMBIAR,
			K_SUPERFICIE_DIM,
		)

		const val OVERLAY_OCULTO = 0
		const val OVERLAY_COMPACTO = 1
		const val OVERLAY_COMPLETO = 2
	}
}
