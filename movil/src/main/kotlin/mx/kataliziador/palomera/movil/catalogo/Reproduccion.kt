package mx.kataliziador.palomera.movil.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.livetv.capabilities.DeviceCapabilities

/** Cómo se puede ver un título. */
sealed interface Reproduccion {
	/** Tal cual está en el disco del ISP: su servidor no gasta CPU. */
	data class Directo(val url: String) : Reproduccion

	/**
	 * El servidor lo convierte al vuelo.
	 *
	 * Funciona, **y cuesta**: con veinte casas haciendo esto a la vez, la CPU
	 * del site llega al techo. [motivo] es lo que Jellyfin dice que se lo
	 * impidió, y es el dato con el que se arregla.
	 */
	data class Transcodificado(val url: String, val motivo: String?) : Reproduccion

	/** Ni directo ni convertido. El abonado ve el estado vacío, no una pantalla negra. */
	data object NoSePuede : Reproduccion
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Qué hacer con lo que contestó `/Items/{id}/PlaybackInfo`.
 *
 * **La `TranscodingUrl` llega relativa y sin llave** —medido el 09-nov—, así
 * que hay que componerla y pegarle la `api_key`. Sin eso el reproductor recibe
 * un 401 y el abonado una pantalla negra sin explicación.
 */
fun leerReproduccion(
	cuerpo: String,
	baseUrl: String,
	token: String,
	itemId: String,
): Reproduccion {
	val fuente = runCatching {
		json.parseToJsonElement(cuerpo).jsonObject["MediaSources"]?.jsonArray?.firstOrNull()?.jsonObject
	}.getOrNull() ?: return Reproduccion.NoSePuede

	val base = baseUrl.trimEnd('/')
	val directo = fuente["SupportsDirectPlay"]?.jsonPrimitive?.booleanOrNull == true ||
		fuente["SupportsDirectStream"]?.jsonPrimitive?.booleanOrNull == true

	if (directo) {
		val fuenteId = fuente["Id"]?.jsonPrimitive?.contentOrNull.orEmpty()
		return Reproduccion.Directo(
			"$base/Videos/$itemId/stream?static=true&mediaSourceId=$fuenteId&api_key=$token",
		)
	}

	val urlTranscodificada = fuente["TranscodingUrl"]?.jsonPrimitive?.contentOrNull
	if (!urlTranscodificada.isNullOrBlank()) {
		val separador = if (urlTranscodificada.contains('?')) "&" else "?"
		return Reproduccion.Transcodificado(
			url = base + urlTranscodificada + separador + "api_key=" + token,
			motivo = fuente["TranscodeReasons"]?.jsonPrimitive?.contentOrNull,
		)
	}

	return Reproduccion.NoSePuede
}

/**
 * El perfil que la app le declara al servidor del ISP: qué sabe reproducir.
 *
 * ## Las dos formas de equivocarse, y las dos son caras
 *
 * **Prometer de más** deja al abonado con una película en negro a media noche.
 * **Quedarse corto** pone a transcodificar al servidor del ISP para todas las
 * casas a la vez, y eso no se nota hasta que su CPU llega al techo. Es el
 * riesgo R2 de `MOVIL_PLAN.md`.
 *
 * Por eso lo que se declara sale de [DeviceCapabilities] —lo que el aparato
 * **tiene**, leído de `MediaCodecList`— y no de una lista escrita a mano. Ese
 * inventario viene del módulo `:livetv`, el mismo que usa la caja.
 *
 * ## Y una línea que no se quita
 *
 * `SubtitleProfiles` con `Method: External`. **Medido el 09-nov**: sin ella, el
 * mismo título con el mismo perfil pasa de `SupportsDirectPlay: true` a
 * `false`, porque el servidor decide incrustar los subtítulos y para eso tiene
 * que convertir el vídeo entero. Este catálogo trae `.srt` al lado de las
 * películas, así que afecta a casi todas.
 */
fun perfilDeDispositivo(capacidades: DeviceCapabilities): String {
	fun tiene(mime: String) = capacidades.hasAnyFor(mime)

	val videos = buildList {
		if (tiene("video/avc")) add("h264")
		if (tiene("video/hevc")) add("hevc")
		if (tiene("video/x-vnd.on2.vp9")) add("vp9")
		if (tiene("video/av01")) add("av1")
		if (tiene("video/mpeg2")) add("mpeg2video")
	}.joinToString(",")

	val audios = buildList {
		if (tiene("audio/mp4a-latm")) add("aac")
		if (tiene("audio/mpeg")) addAll(listOf("mp3", "mp2"))
		if (tiene("audio/ac3")) add("ac3")
		if (tiene("audio/eac3")) add("eac3")
		add("flac")
	}.joinToString(",")

	return """
		{
		  "DirectPlayProfiles":[
		    {"Container":"mp4,mkv,webm","Type":"Video","VideoCodec":"$videos","AudioCodec":"$audios"}
		  ],
		  "TranscodingProfiles":[
		    {"Container":"ts","Type":"Video","VideoCodec":"h264","AudioCodec":"aac",
		     "Protocol":"hls","Context":"Streaming","MaxAudioChannels":"2"}
		  ],
		  "SubtitleProfiles":[
		    {"Format":"subrip","Method":"External"},
		    {"Format":"srt","Method":"External"},
		    {"Format":"vtt","Method":"External"},
		    {"Format":"ass","Method":"External"},
		    {"Format":"ssa","Method":"External"}
		  ]
		}
	""".trimIndent().replace(Regex("\\s*\\n\\s*"), "")
}
