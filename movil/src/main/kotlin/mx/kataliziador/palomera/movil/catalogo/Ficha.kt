package mx.kataliziador.palomera.movil.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.livetv.source.LanguageNormalizer

/** Lo que la ficha de un título necesita enseñar. */
data class Detalle(
	val id: String,
	val nombre: String,
	val anio: Int?,
	val minutos: Int,
	val clasificacion: String?,
	val generos: List<String>,
	val sinopsis: String?,
	val audios: List<String>,
	val subtitulos: List<String>,
	val minutosVistos: Int,
	/** La etiqueta del fondo apaisado, para la cabecera. */
	val fondo: String? = null,
	/**
	 * `Movie`, `Series`…
	 *
	 * No es decorativo: **una serie no se puede reproducir**. Pedirle
	 * `PlaybackInfo` al servidor con el id de la serie devuelve
	 * `Error processing request` —medido el 10-nov—, así que la ficha tiene
	 * que saber qué tiene delante antes de ofrecer un botón.
	 */
	val tipo: String = "Movie",
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * La ficha, o `null` si la respuesta no sirve.
 *
 * **Media ficha pintada es peor que el estado vacío**, que sí está diseñado: un
 * título sin nombre ni id no se puede ni abrir ni nombrar, así que no se
 * devuelve a medias.
 */
fun leerDetalle(cuerpo: String): Detalle? {
	val item = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull() ?: return null
	val id = item.texto("Id") ?: return null
	val nombre = item.texto("Name") ?: return null

	val pistas = (item["MediaStreams"]?.jsonArray ?: emptyList()).map { it.jsonObject }
	val usuario = item["UserData"] as? JsonObject

	return Detalle(
		id = id,
		nombre = nombre,
		anio = item["ProductionYear"]?.jsonPrimitive?.intOrNull,
		minutos = enMinutos(item["RunTimeTicks"]?.jsonPrimitive?.longOrNull),
		clasificacion = item.texto("OfficialRating"),
		generos = (item["Genres"]?.jsonArray ?: emptyList()).mapNotNull { it.jsonPrimitive.contentOrNull },
		sinopsis = item.texto("Overview"),
		audios = idiomasDe(pistas, "Audio"),
		subtitulos = idiomasDe(pistas, "Subtitle"),
		minutosVistos = enMinutos(usuario?.get("PlaybackPositionTicks")?.jsonPrimitive?.longOrNull),
		fondo = (item["BackdropImageTags"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull,
		tipo = item.texto("Type") ?: "Movie",
	)
}

/**
 * De qué proveedor es una biblioteca del site.
 *
 * El ISP nombra sus colecciones «Top Películas Netflix», «Top Series HBO
 * Max»… y esa última parte es la que el abonado reconoce. «Películas» y
 * «Series» a secas no son de nadie: son el catálogo entero.
 *
 * Devuelve `null` para todo lo que no siga el patrón, porque inventar un
 * proveedor a partir de un nombre que no lo lleva llenaría la fila de filtros
 * de palabras sueltas.
 */
fun proveedorDe(nombreDeBiblioteca: String): String? {
	val limpio = nombreDeBiblioteca.trim()
	for (prefijo in PREFIJOS) {
		if (limpio.startsWith(prefijo, ignoreCase = true)) {
			return limpio.removePrefix(prefijo).trim().takeIf { it.isNotBlank() }
		}
	}
	return null
}

private val PREFIJOS = listOf("Top Películas ", "Top Peliculas ", "Top Series ")

/**
 * Los idiomas de un tipo de pista, en español y sin repetir.
 *
 * Usa el [LanguageNormalizer] del módulo del motor, que ya sabe que `spa`,
 * `esp`, `esl` y `es` son lo mismo —`CLAUDE.md` §4.6, y está así porque en
 * algunos satélites la etiqueta llega de cualquiera de esas formas—.
 *
 * Lo que se enseña es el nombre, no el código: un abonado elige «Español», no
 * «spa». Un idioma que no esté en la tabla se enseña tal cual vino, que es
 * menos malo que esconderlo.
 */
private fun idiomasDe(pistas: List<JsonObject>, tipo: String): List<String> = pistas
	.filter { it.texto("Type") == tipo }
	.mapNotNull { LanguageNormalizer.normalize(it.texto("Language")) }
	.distinct()
	.map { NOMBRES[it] ?: it.uppercase() }

private val NOMBRES = mapOf(
	"spa" to "Español",
	"eng" to "Inglés",
	"por" to "Portugués",
	"fra" to "Francés",
	"deu" to "Alemán",
	"ita" to "Italiano",
	"jpn" to "Japonés",
)

/** Los ticks de Jellyfin son unidades de 100 ns. */
private fun enMinutos(ticks: Long?): Int = ((ticks ?: 0L) / 600_000_000L).toInt()

private fun JsonObject.texto(clave: String): String? =
	this[clave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
