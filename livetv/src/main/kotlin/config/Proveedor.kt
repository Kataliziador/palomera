package org.jellyfin.livetv.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * El proveedor que el registro reconoció, para no tener que escribir la
 * dirección del servidor.
 *
 * Es la pregunta **anterior** a la del registro: `REGISTRY_SCHEMA.md` §2
 * pregunta «¿este host está autorizado?», y para eso hay que saber ya la
 * dirección. Ésta la hace una caja recién estrenada, que no sabe ninguna.
 */
data class Proveedor(
	val id: String,
	/** Lo que se le enseña al abonado: «¿Eres cliente de RedTV?». */
	val nombre: String,
	/**
	 * La dirección del Jellyfin del site, con esquema y puerto.
	 *
	 * Se le pasa tal cual a la pantalla de alta, igual que si el abonado la
	 * hubiera escrito. El selector produce una dirección; no inventa un camino
	 * de alta nuevo.
	 */
	val serverUrl: String,
	/** La marca del ISP, si el registro la manda. Se viste antes de entrar. */
	val brand: Brand? = null,
)

/**
 * Lo que puede pasar al preguntar, y los cuatro casos son distintos en pantalla.
 *
 * **[Ninguno] y [NoSeSupo] no se pueden confundir.** El primero significa «el
 * registro contestó y no te conoce», y lleva al abonado al código o a escribir
 * la dirección. El segundo significa «no pudimos preguntar» —la caja llega a
 * la red del ISP pero no a internet, que `CLAUDE.md` §6 dice que pasa—, y ahí
 * decirle «no existe tu proveedor» sería mentirle y mandarlo a buscar un dato
 * que sí tiene.
 */
sealed interface RespuestaProveedor {
	data class Encontrado(val proveedor: Proveedor) : RespuestaProveedor

	data object Ninguno : RespuestaProveedor

	data object NoSeSupo : RespuestaProveedor

	/**
	 * El registro cortó por demasiados intentos (HTTP 429).
	 *
	 * Contesta distinto que [Ninguno] a propósito: un código bueno rechazado
	 * por el límite tiene que poder distinguirse de uno malo, o el abonado lo
	 * reescribe diez veces y acaba llamando a soporte.
	 */
	data object DemasiadosIntentos : RespuestaProveedor
}

/**
 * Interpreta la respuesta del selector.
 *
 * Misma regla que [RegistryParser]: **prefiere descartar a entregar algo a
 * medias**. Un proveedor sin dirección utilizable no es medio proveedor, es
 * una pantalla que enseña un nombre y no lleva a ninguna parte.
 */
object ProveedorParser {

	private val lector = Json { ignoreUnknownKeys = true; isLenient = false }

	fun parse(json: String): RespuestaProveedor {
		val raiz = runCatching { lector.parseToJsonElement(json).jsonObject }
			.getOrElse {
				LiveLog.warn("proveedor.respuestaIlegible", "motivo" to it.message)
				return RespuestaProveedor.NoSeSupo
			}

		val objeto = raiz["proveedor"] as? JsonObject ?: return RespuestaProveedor.Ninguno

		val id = objeto.str("id")
		val nombre = objeto.str("nombre")
		// Misma validación que `channelsUrl`: lo que no es http ni https no se
		// usa. Una errata aquí mandaría la caja a ninguna parte y el síntoma
		// saldría semanas después, en la sala de un abonado.
		val url = objeto.str("serverUrl")?.takeIf {
			it.startsWith("http://") || it.startsWith("https://")
		}

		if (id == null || nombre == null || url == null) {
			LiveLog.warn(
				"proveedor.incompleto",
				"id" to id, "nombre" to nombre, "serverUrl" to objeto.str("serverUrl"),
			)
			return RespuestaProveedor.Ninguno
		}

		val marca = (raiz["brand"] as? JsonObject)?.let { b ->
			BrandParser.parse(b.str("displayName"), b.str("accent"), b.str("logoUrl"))
		}

		return RespuestaProveedor.Encontrado(Proveedor(id, nombre, url, marca))
	}
}
