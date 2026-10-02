package org.jellyfin.livetv.config

import android.os.Build
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.source.ChannelProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Un grupo de la guía: «Nacionales», «Películas», «Deportes». */
data class ChannelGroup(
	val id: String,
	val name: String,
	val order: Int = 0,
)

/**
 * A qué cajas aplica un ajuste. Los campos ausentes no filtran.
 *
 * La comparación es **insensible a mayúsculas** y por igualdad exacta: nada de
 * comodines. Un patrón que casa de más es peor que uno que no casa, porque
 * rompe cajas que nadie estaba mirando.
 */
data class DeviceMatch(
	val manufacturer: String? = null,
	val model: String? = null,
	val device: String? = null,
	val sdkInt: Int? = null,
) {
	/** Cuántos criterios impone. Gana el más específico (CLAUDE.md §6). */
	val specificity: Int =
		listOfNotNull(manufacturer, model, device, sdkInt?.toString()).size

	fun matches(): Boolean {
		if (specificity == 0) return false
		manufacturer?.let { if (!it.equals(Build.MANUFACTURER, true)) return false }
		model?.let { if (!it.equals(Build.MODEL, true)) return false }
		device?.let { if (!it.equals(Build.DEVICE, true)) return false }
		sdkInt?.let { if (it != Build.VERSION.SDK_INT) return false }
		return true
	}
}

/**
 * Ajuste por modelo de caja.
 *
 * **El servidor sugiere, la caja verifica** (CLAUDE.md §6). Nada de esto se
 * obedece a ciegas: `engine` solo puede nombrar un motor que ya viene en el
 * APK, y la cascada sigue pudiendo descartarlo si el hardware no cumple.
 * El servidor **nunca entrega código** (regla 13).
 */
data class DeviceOverride(
	val match: DeviceMatch,
	val engine: String? = null,
	val tunneling: Boolean? = null,
	val tsAllowNonIdrKeyframes: Boolean? = null,
	val tsDetectAccessUnits: Boolean? = null,
	val forceSoftwareVideo: Boolean? = null,
	val targetOffsetMs: Long? = null,
	/** Si el VPU de este modelo desentrelaza. Se valida en pantalla, no por API. */
	val vpuDeinterlaces: Boolean? = null,
)

/**
 * La configuración completa que sirve el servidor propio.
 *
 * Ni un nombre de canal, ni una URL, ni un logo viven en el APK (regla 12):
 * todo sale de aquí o de `BuildConfig`.
 */
data class LiveConfig(
	val version: Int,
	val generatedAt: String?,
	val groups: List<ChannelGroup>,
	val channels: List<ChannelProfile>,
	val deviceOverrides: List<DeviceOverride>,
) {
	/**
	 * Los ajustes que aplican a **esta** caja, del menos al más específico.
	 *
	 * Se devuelven en ese orden para que quien los aplique pueda hacerlo en
	 * secuencia y quedarse con el último: así «lo más específico gana» sin que
	 * nadie tenga que razonar sobre precedencias.
	 */
	fun overridesForThisDevice(): List<DeviceOverride> =
		deviceOverrides
			.filter { it.match.matches() }
			.sortedBy { it.match.specificity }

	/**
	 * Funde todos los ajustes que casan con esta caja en uno solo.
	 *
	 * Campo a campo gana el más específico, no el ajuste entero: así se puede
	 * decir «todas las cajas de este fabricante sin tunneled» y además «y este
	 * modelo concreto tampoco con el flag nonIDR», sin tener que repetir en el
	 * segundo todo lo que ya decía el primero.
	 */
	fun settingsForThisDevice(): DeviceOverride? {
		val aplicables = overridesForThisDevice()
		if (aplicables.isEmpty()) return null
		return aplicables.reduce { menos, mas ->
			DeviceOverride(
				match = mas.match,
				engine = mas.engine ?: menos.engine,
				tunneling = mas.tunneling ?: menos.tunneling,
				tsAllowNonIdrKeyframes = mas.tsAllowNonIdrKeyframes ?: menos.tsAllowNonIdrKeyframes,
				tsDetectAccessUnits = mas.tsDetectAccessUnits ?: menos.tsDetectAccessUnits,
				forceSoftwareVideo = mas.forceSoftwareVideo ?: menos.forceSoftwareVideo,
				targetOffsetMs = mas.targetOffsetMs ?: menos.targetOffsetMs,
				vpuDeinterlaces = mas.vpuDeinterlaces ?: menos.vpuDeinterlaces,
			)
		}
	}

	companion object {
		/** Versión de esquema que esta app entiende. */
		const val SUPPORTED_VERSION = 1
	}
}

/**
 * Convierte el `config.json` en un [LiveConfig], o explica por qué no puede.
 *
 * **Tolerante hacia adelante, estricto hacia atrás.** Los campos desconocidos
 * se ignoran —un servidor más nuevo no puede romper una app vieja instalada en
 * casa de un suscriptor— pero un canal sin `id`, `name` o `url` usable se
 * descarta con su motivo en el log, y una configuración sin ningún canal se
 * rechaza entera.
 */
object LiveConfigParser {

	class InvalidConfig(message: String) : Exception(message)

	private val lector = Json { ignoreUnknownKeys = true; isLenient = false }

	fun parse(json: String): LiveConfig {
		val raiz = runCatching { lector.parseToJsonElement(json).jsonObject }
			.getOrElse { throw InvalidConfig("no es un objeto JSON válido: ${it.message}") }

		val version = raiz.int("version") ?: throw InvalidConfig("falta «version»")
		if (version > LiveConfig.SUPPORTED_VERSION) {
			// No se rechaza: el suscriptor no puede quedarse sin televisión
			// porque el servidor vaya por delante de su app.
			LiveLog.warn(
				"config.versionMasNueva",
				"delServidor" to version,
				"queEntiendo" to LiveConfig.SUPPORTED_VERSION,
			)
		}

		val grupos = parseGroups(raiz.array("groups"))
		val canales = parseChannels(raiz.array("channels"), grupos.map { it.id }.toSet())
		if (canales.isEmpty()) throw InvalidConfig("no hay ni un canal utilizable")

		val ajustes = parseOverrides(raiz.array("deviceOverrides"))

		LiveLog.info(
			"config.interpretada",
			"version" to version,
			"canales" to canales.size,
			"grupos" to grupos.size,
			"ajustesPorCaja" to ajustes.size,
		)

		return LiveConfig(
			version = version,
			generatedAt = raiz.str("generatedAt"),
			groups = grupos,
			channels = canales,
			deviceOverrides = ajustes,
		)
	}

	private fun parseGroups(array: JsonArray?): List<ChannelGroup> {
		if (array == null) return emptyList()
		return array.mapNotNull { elemento ->
			val o = elemento as? JsonObject ?: return@mapNotNull null
			val id = o.str("id") ?: return@mapNotNull null
			ChannelGroup(id = id, name = o.str("name") ?: id, order = o.int("order") ?: 0)
		}.sortedBy { it.order }
	}

	private fun parseChannels(array: JsonArray?, gruposConocidos: Set<String>): List<ChannelProfile> {
		if (array == null) return emptyList()
		val vistos = mutableSetOf<String>()

		return array.mapIndexedNotNull { i, elemento ->
			val o = elemento as? JsonObject ?: return@mapIndexedNotNull descartar(i, "no es un objeto")
			val id = o.str("id") ?: return@mapIndexedNotNull descartar(i, "sin «id»")
			val name = o.str("name") ?: return@mapIndexedNotNull descartar(i, "sin «name»", id)
			val url = o.str("url") ?: return@mapIndexedNotNull descartar(i, "sin «url»", id)

			if (!url.startsWith("http://") && !url.startsWith("https://")) {
				return@mapIndexedNotNull descartar(i, "la URL no es http ni https", id)
			}
			if (!vistos.add(id)) return@mapIndexedNotNull descartar(i, "«id» repetido", id)

			// Un grupo desconocido no tira el canal: se queda suelto y se avisa.
			// Perder un canal por una errata en la guía sería desproporcionado.
			val grupo = o.str("group")?.takeIf { g ->
				(g in gruposConocidos).also {
					if (!it) LiveLog.warn("config.grupoDesconocido", "canal" to id, "grupo" to g)
				}
			}

			ChannelProfile(
				id = id,
				name = name,
				url = url,
				number = o.int("number"),
				order = o.int("order"),
				group = grupo,
				logoUrl = o.str("logoUrl"),
				videoCodec = o.str("videoCodec"),
				audioCodec = o.str("audioCodec"),
				targetOffsetMs = o.long("targetOffsetMs"),
				audioDefault = o.str("audioDefault"),
				interlaced = o.bool("interlaced") ?: false,
				hd = o.bool("hd") ?: false,
				enginesAllowed = o.array("enginesAllowed")
					?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
					?.toSet(),
			)
		}
	}

	private fun descartar(indice: Int, motivo: String, id: String? = null): ChannelProfile? {
		LiveLog.warn("config.canalDescartado", "indice" to indice, "id" to id, "motivo" to motivo)
		return null
	}

	private fun parseOverrides(array: JsonArray?): List<DeviceOverride> {
		if (array == null) return emptyList()
		return array.mapIndexedNotNull { i, elemento ->
			val o = elemento as? JsonObject ?: return@mapIndexedNotNull null
			val m = o["match"] as? JsonObject
				?: return@mapIndexedNotNull null.also {
					LiveLog.warn("config.ajusteSinMatch", "indice" to i)
				}

			val match = DeviceMatch(
				manufacturer = m.str("manufacturer"),
				model = m.str("model"),
				device = m.str("device"),
				sdkInt = m.int("sdkInt"),
			)
			if (match.specificity == 0) {
				// Un `match` vacío casaría con todas las cajas del mundo. Eso no
				// es un ajuste por modelo, es un cambio global disfrazado.
				LiveLog.warn("config.ajusteSinCriterios", "indice" to i)
				return@mapIndexedNotNull null
			}

			DeviceOverride(
				match = match,
				engine = o.str("engine"),
				tunneling = o.bool("tunneling"),
				tsAllowNonIdrKeyframes = o.bool("tsAllowNonIdrKeyframes"),
				tsDetectAccessUnits = o.bool("tsDetectAccessUnits"),
				forceSoftwareVideo = o.bool("forceSoftwareVideo"),
				targetOffsetMs = o.long("targetOffsetMs"),
				vpuDeinterlaces = o.bool("vpuDeinterlaces"),
			)
		}
	}
}

// Un campo ausente tiene que distinguirse de uno puesto a cero o a cadena
// vacía: `targetOffsetMs: 0` es una orden, y su ausencia es «usa el del canal».
private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonObject.str(key: String): String? =
	prim(key)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull

private fun JsonObject.long(key: String): Long? = prim(key)?.longOrNull

private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull

private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
