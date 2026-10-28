package org.jellyfin.livetv.epg

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.config.RegistryTrust
import org.jellyfin.livetv.diagnostics.LiveLog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** De dónde salió la guía que está en memoria. */
enum class EpgOrigin { RED, CACHE, SIN_GUIA }

data class EpgResult(val guia: Guia, val origen: EpgOrigin)

/**
 * La guía del site: descarga, caché y relectura.
 *
 * ## Las tres cosas que lo gobiernan
 *
 * 1. **Un site sin EPG es normal, no un error** (`CONFIG_SCHEMA.md` §5). Es el
 *    estado esperado mientras la tubería de guía de ese site no esté lista, y
 *    la barra se degrada a enseñar el canal sin programación. Nada de esto
 *    puede impedir ver televisión.
 * 2. **El fichero no se lee entero a memoria.** Se descarga a disco y se
 *    recorre con SAX. Esa es la diferencia con el `config.json` y es lo que
 *    permite que un lineup de 120 canales quepa en una caja de 2 GB.
 * 3. **La caché se indexa por servidor**, como la del `config.json`: una caja
 *    que cambia de site heredaría si no la parrilla del anterior, que es peor
 *    que no tener ninguna porque parece correcta.
 *
 * ## Dos relojes, y no son el mismo
 *
 * Una guía se vuelve a **descargar** cuando pasa `refreshMinutes` desde la
 * última vez. Una guía se vuelve a **leer** cuando la ventana que hay en
 * memoria deja de cubrir el presente — y eso pasa sin que el servidor publique
 * nada, simplemente porque el tiempo avanza. Una caja encendida toda la noche
 * necesita lo segundo y no lo primero.
 */
class EpgRepository(context: Context) {

	/** Para las raíces incluidas de [RegistryTrust]; ver [descargar]. */
	private val contexto = context.applicationContext

	private val directorio = File(context.filesDir, "livetv")

	private fun cacheDe(clave: String) = File(directorio, "epg-${sanear(clave)}.xml")

	private fun sanear(clave: String) =
		clave.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

	/**
	 * La guía para estos canales, de la red o de la caché.
	 *
	 * @param epgUrl lo que dice el `config.json`; `null` si ese site no sirve
	 *   guía todavía.
	 * @param claveDeCache el servidor, para no mezclar sites.
	 */
	suspend fun load(
		epgUrl: String?,
		refreshMinutes: Int,
		claveDeCache: String,
		canalesConocidos: Set<String>,
		ahoraMs: Long = System.currentTimeMillis(),
	): EpgResult = withContext(Dispatchers.IO) {
		if (epgUrl.isNullOrBlank()) {
			LiveLog.info("epg.sinPuntero", "detalle" to "este site no publica guía")
			return@withContext EpgResult(Guia.VACIA, EpgOrigin.SIN_GUIA)
		}

		val cache = cacheDe(claveDeCache)
		val edadMs = if (cache.exists()) ahoraMs - cache.lastModified() else Long.MAX_VALUE
		val caduca = refreshMinutes.coerceAtLeast(1) * 60_000L

		if (edadMs < caduca) {
			// Vigente: se lee lo que ya está en disco y no se toca la red. Con
			// una guía diaria esto es lo que pasa casi siempre.
			interpretar(cache, canalesConocidos, ahoraMs, EpgOrigin.CACHE)?.let { return@withContext it }
		}

		val descargada = runCatching { descargar(epgUrl, cache) }
			.onFailure {
				// Que no se pueda bajar la guía no es motivo para quedarse sin
				// la de ayer: una parrilla de ayer a estas horas acierta más
				// que un hueco.
				LiveLog.warn("epg.noSeDescargo", "url" to epgUrl, "motivo" to it.message)
			}
			.isSuccess

		interpretar(cache, canalesConocidos, ahoraMs, if (descargada) EpgOrigin.RED else EpgOrigin.CACHE)
			?: EpgResult(Guia.VACIA, EpgOrigin.SIN_GUIA)
	}

	private fun interpretar(
		cache: File,
		canalesConocidos: Set<String>,
		ahoraMs: Long,
		origen: EpgOrigin,
	): EpgResult? {
		if (!cache.exists()) return null
		return runCatching {
			val guia = cache.inputStream().buffered().use {
				XmltvParser.parse(it, canalesConocidos, ahoraMs)
			}
			LiveLog.info(
				"epg.lista",
				"origen" to origen,
				"canales" to guia.canales,
				"programas" to guia.programas,
				"antiguedadMin" to (ahoraMs - cache.lastModified()) / 60_000,
			)
			EpgResult(guia, origen)
		}.getOrElse {
			// Un XMLTV ilegible en disco es basura: se borra para que el
			// siguiente intento vaya a la red en vez de volver a tropezar.
			LiveLog.error("epg.cacheIlegible", it, "ruta" to cache.path)
			cache.delete()
			null
		}
	}

	/**
	 * Baja el XMLTV **a disco**, por un temporal.
	 *
	 * Se escribe aparte y se renombra por lo mismo que la caché del
	 * `config.json`: un corte a mitad de escritura dejaría un XML truncado, y
	 * entonces la próxima vez no habría ni red ni respaldo.
	 */
	private fun descargar(epgUrl: String, destino: File) {
		val conexion = (URL(epgUrl).openConnection() as HttpURLConnection).apply {
			// La cuarta conexión, y la que nadie había contado: el puntero del
			// XMLTV lo pone el `config.json` del site (§6), así que puede ser
			// un dominio público por HTTPS aunque los canales vayan por la LAN.
			// Sin esto, en una caja anterior a 7.1.1 la sección funciona y la
			// guía sale vacía para siempre.
			RegistryTrust.aplicarA(this, contexto)
			connectTimeout = LiveTuning.EPG_CONNECT_TIMEOUT_MS
			readTimeout = LiveTuning.EPG_READ_TIMEOUT_MS
			requestMethod = "GET"
			setRequestProperty("Accept", "application/xml, text/xml")
			setRequestProperty("Accept-Encoding", "gzip")
			instanceFollowRedirects = true
		}

		try {
			val codigo = conexion.responseCode
			if (codigo !in 200..299) error("HTTP $codigo ${conexion.responseMessage}")

			val declarado = conexion.contentLength
			if (declarado > LiveTuning.EPG_MAX_BYTES) {
				error("el cuerpo declara $declarado bytes, máximo ${LiveTuning.EPG_MAX_BYTES}")
			}

			destino.parentFile?.mkdirs()
			val temporal = File(destino.parentFile, destino.name + ".tmp")

			// `HttpURLConnection` descomprime gzip solo cuando él puso la
			// cabecera; aquí la ponemos nosotros, así que puede llegar
			// comprimido y hay que deshacerlo. Importa: un XMLTV comprime a
			// una décima parte y pedirlo sin gzip multiplica por diez lo que
			// cruza la LAN del ISP en cada refresco de cada caja.
			val comprimido = conexion.contentEncoding?.contains("gzip", ignoreCase = true) == true
			val flujo = if (comprimido) {
				java.util.zip.GZIPInputStream(conexion.inputStream)
			} else {
				conexion.inputStream
			}

			var escritos = 0L
			try {
			flujo.use { entrada ->
				FileOutputStream(temporal).use { salida ->
					val trozo = ByteArray(32 * 1024)
					while (true) {
						val leidos = entrada.read(trozo)
						if (leidos < 0) break
						escritos += leidos
						// El tope se comprueba sobre lo **descomprimido**: una
						// respuesta troceada no declara longitud, y un gzip
						// pequeño puede expandirse hasta llenar el disco.
						if (escritos > LiveTuning.EPG_MAX_BYTES) {
							error("el cuerpo supera ${LiveTuning.EPG_MAX_BYTES} bytes")
						}
						salida.write(trozo, 0, leidos)
					}
				}
			}

			if (!temporal.renameTo(destino)) {
				temporal.copyTo(destino, overwrite = true)
				temporal.delete()
			}
			} catch (fallo: Throwable) {
				// Un temporal a medias ocupa disco y no sirve para nada. Se
				// borra aquí y no en un `finally` porque en el camino bueno ya
				// no existe: lo consumió el renombrado.
				temporal.delete()
				throw fallo
			}
			LiveLog.info(
				"epg.descargada",
				"bytes" to escritos,
				"comprimido" to comprimido,
			)
		} finally {
			conexion.disconnect()
		}
	}
}
