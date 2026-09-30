package org.jellyfin.livetv.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.LiveLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * De dónde salió la configuración que se está usando.
 *
 * No es decorativo: sin esto, una caja que lleva días con una lista vieja
 * porque el servidor no responde es indistinguible de una al día, y el
 * operador no tiene forma de saberlo.
 */
enum class ConfigOrigin { RED, CACHE, NINGUNO }

data class ConfigResult(
	val config: LiveConfig?,
	val origin: ConfigOrigin,
	/** Antigüedad de la caché cuando se sirvió desde ella. */
	val cacheAgeMs: Long? = null,
)

/**
 * Cliente del servidor de configuración propio.
 *
 * **Tres reglas que gobiernan el diseño:**
 *
 * 1. **El suscriptor nunca se queda sin televisión por culpa del servidor.**
 *    Si la descarga falla, se sirve la última configuración buena desde disco.
 *    Un `config.json` caído no puede apagar un lineup entero.
 * 2. **Una configuración malformada se rechaza entera.** Nunca se mezcla con
 *    la anterior: media lista es peor que una lista vieja, porque el canal que
 *    falta parece averiado.
 * 3. **El servidor no entrega código** (CLAUDE.md regla 13). Solo datos y
 *    elecciones entre motores que ya vienen en el APK.
 *
 * No tiene dependencias nuevas a propósito (regla 11): `HttpURLConnection` y
 * `org.json` vienen en la plataforma.
 */
class ConfigRepository(
	context: Context,
	private val configUrl: String,
) {

	private val cacheFile = File(context.filesDir, "livetv/config.json")

	/**
	 * Devuelve la mejor configuración disponible, por este orden: la del
	 * servidor, la de la caché, ninguna.
	 *
	 * Que devuelva `null` no es un fallo que deba propagarse como excepción: el
	 * llamante tiene un último recurso —la lista de arranque del `BuildConfig`—
	 * y romper ahí dejaría la sección en blanco.
	 */
	suspend fun load(): ConfigResult = withContext(Dispatchers.IO) {
		if (configUrl.isBlank()) {
			LiveLog.warn("config.sinUrl", "detalle" to "CONFIG_URL vacía en este flavor")
			return@withContext desdeCache(ConfigOrigin.NINGUNO)
		}

		val descargada = runCatching { descargar() }
			.getOrElse { fallo ->
				LiveLog.warn(
					"config.descargaFallida",
					"url" to configUrl,
					"causa" to "${fallo::class.java.simpleName}: ${fallo.message}",
				)
				null
			}

		if (descargada != null) {
			val config = runCatching { LiveConfigParser.parse(descargada) }
				.getOrElse { fallo ->
					// Se conserva la caché: una lista vieja es mejor que ninguna.
					LiveLog.error("config.malformada", fallo, "url" to configUrl)
					null
				}

			if (config != null) {
				guardarCache(descargada)
				LiveLog.info("config.desdeRed", "canales" to config.channels.size)
				return@withContext ConfigResult(config, ConfigOrigin.RED)
			}
		}

		desdeCache(ConfigOrigin.NINGUNO)
	}

	private fun desdeCache(siNoHay: ConfigOrigin): ConfigResult {
		if (!cacheFile.exists()) {
			LiveLog.warn("config.sinCache")
			return ConfigResult(null, siNoHay)
		}

		val edad = System.currentTimeMillis() - cacheFile.lastModified()
		val texto = runCatching { cacheFile.readText() }
			.getOrElse {
				LiveLog.error("config.cacheIlegible", it, "ruta" to cacheFile.path)
				return ConfigResult(null, siNoHay)
			}

		val config = runCatching { LiveConfigParser.parse(texto) }
			.getOrElse {
				// Si hasta la caché está corrupta, se borra: guardarla solo
				// garantiza repetir el mismo fallo en cada arranque.
				LiveLog.error("config.cacheCorrupta", it, "ruta" to cacheFile.path)
				cacheFile.delete()
				return ConfigResult(null, siNoHay)
			}

		LiveLog.warn(
			"config.desdeCache",
			"canales" to config.channels.size,
			"antiguedadHoras" to edad / 3_600_000,
		)
		return ConfigResult(config, ConfigOrigin.CACHE, edad)
	}

	private fun descargar(): String {
		val conexion = (URL(configUrl).openConnection() as HttpURLConnection).apply {
			connectTimeout = LiveTuning.CONFIG_CONNECT_TIMEOUT_MS
			readTimeout = LiveTuning.CONFIG_READ_TIMEOUT_MS
			requestMethod = "GET"
			setRequestProperty("Accept", "application/json")
			instanceFollowRedirects = true
		}

		try {
			val codigo = conexion.responseCode
			if (codigo !in 200..299) {
				error("HTTP $codigo ${conexion.responseMessage}")
			}

			// Un `config.json` de 120 canales ronda los 40 kB. Un cuerpo
			// desmesurado significa que la URL no apunta a lo que creemos —una
			// página de error, un portal cautivo— y leerlo entero en memoria en
			// una caja de 2 GB no es aceptable.
			val longitud = conexion.contentLength
			if (longitud > LiveTuning.CONFIG_MAX_BYTES) {
				error("el cuerpo declara $longitud bytes, máximo ${LiveTuning.CONFIG_MAX_BYTES}")
			}

			return conexion.inputStream.bufferedReader().use { it.readText() }
		} finally {
			conexion.disconnect()
		}
	}

	private fun guardarCache(texto: String) {
		runCatching {
			cacheFile.parentFile?.mkdirs()
			// Se escribe aparte y se renombra: un corte de luz a mitad de
			// escritura dejaría la caché truncada, y entonces la próxima vez no
			// habría ni red ni respaldo.
			val temporal = File(cacheFile.parentFile, "config.json.tmp")
			temporal.writeText(texto)
			if (!temporal.renameTo(cacheFile)) {
				temporal.copyTo(cacheFile, overwrite = true)
				temporal.delete()
			}
		}.onFailure {
			LiveLog.error("config.cacheNoGuardada", it, "ruta" to cacheFile.path)
		}
	}
}
