package mx.kataliziador.palomera.movil.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Un episodio de una serie.
 *
 * ## Por qué esto existe
 *
 * **Medido el 10-nov**: pulsar «Reproducir» en una serie llamaba a
 * `PlaybackInfo` con el id de la serie y el servidor contestaba
 * `Error processing request`. Una serie no es un vídeo — no hay nada que
 * reproducir en ella, sólo en sus episodios.
 */
data class Episodio(
	val id: String,
	val nombre: String,
	val temporada: Int?,
	val numero: Int?,
	val minutos: Int,
	val porcentaje: Int,
	/** La etiqueta de su imagen, si la trae. */
	val cartel: String?,
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Los episodios de una respuesta de `/Shows/{id}/Episodes`.
 *
 * **Uno malo se descarta, no tira la lista.** Perder una temporada entera por
 * una errata en un episodio sería desproporcionado, que es la misma regla que
 * rige para los canales y para el catálogo.
 */
fun leerEpisodios(cuerpo: String): List<Episodio> {
	val items = runCatching {
		json.parseToJsonElement(cuerpo).jsonObject["Items"]?.jsonArray
	}.getOrNull() ?: return emptyList()

	return items.mapNotNull { elemento ->
		val item = elemento as? JsonObject ?: return@mapNotNull null
		val id = item.texto("Id") ?: return@mapNotNull null
		val nombre = item.texto("Name") ?: return@mapNotNull null

		Episodio(
			id = id,
			nombre = nombre,
			temporada = item["ParentIndexNumber"]?.jsonPrimitive?.intOrNull,
			numero = item["IndexNumber"]?.jsonPrimitive?.intOrNull,
			minutos = ((item["RunTimeTicks"]?.jsonPrimitive?.longOrNull ?: 0L) / 600_000_000L).toInt(),
			porcentaje = ((item["UserData"] as? JsonObject)
				?.get("PlayedPercentage")?.jsonPrimitive?.doubleOrNull ?: 0.0).toInt(),
			cartel = (item["ImageTags"] as? JsonObject)?.texto("Primary"),
		)
	}
}

/**
 * Lo que dice el botón de una serie.
 *
 * **Dice a qué episodio lleva.** «Reproducir» a secas, en una serie de ocho
 * temporadas, es pedirle al abonado que lo pulse para averiguar dónde cae.
 *
 * Y distingue empezar de continuar, porque no son lo mismo para quien lleva
 * media temporada vista.
 */
fun etiquetaDeEpisodio(temporada: Int?, numero: Int?, empezado: Boolean): String {
	val verbo = if (empezado) "Continuar" else "Reproducir"
	val donde = rotulo(temporada, numero)
	return if (donde.isEmpty()) verbo else "$verbo $donde"
}

/**
 * «T1:E4», o lo que se pueda.
 *
 * Hay episodios sueltos —especiales, pilotos— sin temporada o sin número.
 * Enseñar «T0:E0» sería peor que no enseñar nada, así que se omite lo que
 * falte.
 */
fun rotulo(temporada: Int?, numero: Int?): String = when {
	temporada != null && numero != null -> "T$temporada:E$numero"
	numero != null -> "E$numero"
	temporada != null -> "T$temporada"
	else -> ""
}

private fun JsonObject.texto(clave: String): String? =
	this[clave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
