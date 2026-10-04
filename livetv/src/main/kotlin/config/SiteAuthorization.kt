package org.jellyfin.livetv.config

import org.jellyfin.livetv.diagnostics.LiveLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Lo que el registro de sites contestó sobre un host, ya interpretado.
 *
 * Se guarda **indexada por host** y, mientras no caduque, no se vuelve a
 * preguntar (`REGISTRY_SCHEMA.md` §4).
 */
data class SiteAuthorization(
	/** El host normalizado por el que se preguntó. Es la clave. */
	val host: String,
	val authorized: Boolean,
	/**
	 * Cuándo deja de valer. **Obligatoria si [authorized]**, y por eso aquí
	 * nunca es nula en una respuesta autorizada: el parser descarta la
	 * respuesta entera antes que guardar una autorización sin caducidad.
	 */
	val untilEpochMs: Long?,
	/** Pisa a la convención del puerto 8097. */
	val channelsUrl: String? = null,
	/** Pisa a la marca que sirva el site. `null` = el registro no la manda. */
	val brand: Brand? = null,
	/** Para enseñar tal cual al abonado cuando se deniega. */
	val message: String? = null,
	/** Cuándo se obtuvo, para saber lo vieja que es la respuesta guardada. */
	val obtenidaEnMs: Long = 0L,
) {
	fun vigente(ahoraMs: Long): Boolean =
		authorized && untilEpochMs != null && ahoraMs < untilEpochMs
}

/**
 * Interpreta la respuesta del registro.
 *
 * **Prefiere descartar a guardar algo a medias.** Una autorización sin
 * caducidad es eterna: bastaría un campo que el registro se deja para que una
 * caja quedara autorizada para siempre sin forma de retirarlo
 * (`REGISTRY_SCHEMA.md` §3). Descartarla es recuperable —la app se degrada y
 * vuelve a preguntar—; guardarla, no.
 */
object RegistryParser {

	class InvalidResponse(message: String) : Exception(message)

	private val lector = Json { ignoreUnknownKeys = true; isLenient = false }

	fun parse(host: String, json: String, ahoraMs: Long): SiteAuthorization {
		val raiz = runCatching { lector.parseToJsonElement(json).jsonObject }
			.getOrElse { throw InvalidResponse("no es un objeto JSON válido: ${it.message}") }

		val autorizado = raiz.bool("authorized")
			?: throw InvalidResponse("falta «authorized»")

		if (!autorizado) {
			// Denegar no necesita `until`: no hay nada que caducar.
			return SiteAuthorization(
				host = host,
				authorized = false,
				untilEpochMs = null,
				message = raiz.str("message"),
				obtenidaEnMs = ahoraMs,
			)
		}

		val hasta = fechaDe(raiz)
			?: throw InvalidResponse("autorizada sin «until» utilizable")

		val site = raiz["site"] as? JsonObject
		// Misma regla que para la URL de un canal: lo que no es http ni https
		// no se usa. Una errata aquí mandaría la caja a ninguna parte y el
		// síntoma saldría semanas después, en la sala de un abonado.
		val canales = site?.str("channelsUrl")?.takeIf { url ->
			(url.startsWith("http://") || url.startsWith("https://")).also {
				if (!it) LiveLog.warn("registro.channelsUrlIlegible", "url" to url)
			}
		}

		val marca = (raiz["brand"] as? JsonObject)?.let { b ->
			BrandParser.parse(b.str("displayName"), b.str("accent"), b.str("logoUrl"))
		}

		return SiteAuthorization(
			host = host,
			authorized = true,
			untilEpochMs = hasta,
			channelsUrl = canales,
			brand = marca,
			message = raiz.str("message"),
			obtenidaEnMs = ahoraMs,
		)
	}

	/**
	 * Acepta la fecha en ISO-8601 con zona o en milisegundos epoch.
	 *
	 * A diferencia del aviso del operador —donde una fecha ilegible se trata
	 * como «sin caducidad» porque perder el aviso es peor—, aquí una fecha que
	 * no se entiende **invalida la respuesta**. Es la diferencia entre un texto
	 * y una llave.
	 */
	private fun fechaDe(raiz: JsonObject): Long? {
		raiz.long("until")?.let { return it }
		val crudo = raiz.str("until") ?: return null
		return runCatching { java.time.Instant.parse(crudo).toEpochMilli() }
			.getOrElse {
				LiveLog.warn("registro.fechaIlegible", "until" to crudo)
				null
			}
	}
}
