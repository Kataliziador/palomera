package mx.kataliziador.palomera.movil.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Un título de la biblioteca del ISP. */
data class Titulo(
	val id: String,
	val nombre: String,
	val tipo: String,
	val anio: Int?,
)

/**
 * Lo que la app entiende de lo que contesta Jellyfin.
 *
 * ## Por qué se navega el árbol a mano
 *
 * Igual que `LiveConfigParser` con el `config.json`, y por el mismo motivo
 * (`CLAUDE.md` §6): hace falta **descartar elemento por elemento** y seguir.
 * Un catálogo de 16 233 elementos trae de todo, y la regla es la misma que con
 * los canales — **uno mal formado se descarta; la lista no se cae**. Perder un
 * título por una errata del servidor es desproporcionado; perder la pantalla
 * entera lo es mucho más.
 *
 * Tampoco se usa el SDK de Jellyfin aquí: sus modelos generados obligan a que
 * **todo** encaje, que es justo lo contrario de lo que hace falta.
 */
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Una biblioteca del site: «Películas», «Top Series Netflix»… */
data class BibliotecaDelSite(val id: String, val nombre: String)

/**
 * Las bibliotecas que valen como carril de catálogo.
 *
 * **La de TV en vivo se descarta**: el site la sirve como una vista más, pero
 * abrirla desde Inicio llevaría al abonado a la guía por una puerta que no es
 * la suya — la sección de canales tiene la suya propia.
 */
fun leerBibliotecas(cuerpo: String): List<BibliotecaDelSite> = elementos(cuerpo).mapNotNull { item ->
	val id = item.texto("Id") ?: return@mapNotNull null
	val nombre = item.texto("Name") ?: return@mapNotNull null
	if (item.texto("CollectionType") == "livetv") return@mapNotNull null
	BibliotecaDelSite(id, nombre)
}

/**
 * Cuántos hay **en total**, no cuántos vinieron.
 *
 * La primera versión de Catálogo enseñaba el tamaño de la lista recibida —«60
 * títulos»— cuando la biblioteca tiene 248. Un recuento que cuenta la página y
 * no el catálogo es un texto que miente, y se vio en el teléfono.
 */
fun leerTotal(cuerpo: String): Int = runCatching {
	json.parseToJsonElement(cuerpo).jsonObject["TotalRecordCount"]?.jsonPrimitive?.intOrNull
}.getOrNull() ?: 0

/** Los títulos de una respuesta de `/Items`. */
fun leerTitulos(cuerpo: String): List<Titulo> = elementos(cuerpo).mapNotNull { item ->
	val id = item.texto("Id") ?: return@mapNotNull null
	val nombre = item.texto("Name") ?: return@mapNotNull null
	Titulo(
		id = id,
		nombre = nombre,
		tipo = item.texto("Type") ?: "Desconocido",
		anio = item["ProductionYear"]?.jsonPrimitive?.intOrNull,
	)
}

/** Lo dejado a medias, de una respuesta de `/Items/Resume`. */
fun leerEnProgreso(cuerpo: String): List<EnProgreso> = elementos(cuerpo).mapNotNull { item ->
	val id = item.texto("Id") ?: return@mapNotNull null
	val nombre = item.texto("Name") ?: return@mapNotNull null

	val porcentaje = (item["UserData"] as? JsonObject)
		?.get("PlayedPercentage")?.jsonPrimitive?.doubleOrNull
		?.coerceIn(0.0, 100.0)
		?: 0.0

	// `RunTimeTicks` son unidades de 100 ns, que es como las manda Jellyfin.
	val minutosTotales = (item["RunTimeTicks"]?.jsonPrimitive?.longOrNull ?: 0L) / 600_000_000L

	EnProgreso(
		id = id,
		titulo = nombre,
		porcentaje = porcentaje.toInt(),
		minutosRestantes = (minutosTotales * (100 - porcentaje) / 100).toInt(),
	)
}

/**
 * Los elementos de una respuesta, o ninguno.
 *
 * **Un cuerpo ilegible devuelve una lista vacía y no lanza.** La pantalla
 * enseña entonces su estado vacío, que está diseñado; una excepción aquí
 * dejaría a un abonado mirando una pantalla en blanco sin saber por qué.
 */
private fun elementos(cuerpo: String): List<JsonObject> = runCatching {
	json.parseToJsonElement(cuerpo).jsonObject["Items"]?.jsonArray.orEmpty().map { it.jsonObject }
}.getOrDefault(emptyList())

private fun JsonObject.texto(clave: String): String? =
	this[clave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
