package org.jellyfin.livetv.config

import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * De dónde saca la app el servidor de canales.
 *
 * **Se deriva de la dirección de Jellyfin, no se configura.** El abonado ya
 * tiene que escribir esa dirección; pedirle una segunda sería pedirle algo que
 * no sabe. El servicio de canales vive en la misma máquina (`CLAUDE.md` §6),
 * así que basta con quedarse con el esquema y el host y cambiar el puerto.
 *
 * **Por qué el puerto y no una ruta:** un puerto solo lo puede ocupar un
 * proceso, así que el servicio no puede escuchar en el de Jellyfin. O va
 * detrás de un proxy en una ruta —lo que obliga a montar nginx en cada site—
 * o va en el suyo. El puerto funciona haya proxy o no.
 *
 * **Lo que se descarta a propósito de la dirección de Jellyfin:** la ruta, la
 * consulta y el fragmento. Jellyfin puede estar en `https://tv.isp.mx/jellyfin`
 * y el servicio de canales sigue estando en `https://tv.isp.mx:8097`, porque es
 * la misma máquina pero otro proceso.
 */
object ServiceUrl {

	/**
	 * Devuelve la URL del `config.json` del site, o `null` si la dirección no
	 * sirve para derivarla.
	 *
	 * Devolver `null` no es un fallo que deba propagarse: el llamante tiene
	 * caché y lista de arranque. Pero sí se registra, porque una sección
	 * servida desde el `BuildConfig` está enseñando canales de prueba.
	 */
	fun configDesdeServidor(
		direccionJellyfin: String?,
		puerto: Int = LiveTuning.CHANNEL_SERVICE_PORT,
		ruta: String = LiveTuning.CHANNEL_SERVICE_CONFIG_PATH,
	): String? {
		val base = baseDesdeServidor(direccionJellyfin, puerto) ?: return null
		return base + ruta
	}

	/** `esquema://host:puerto`, sin barra final. Visible para poder probarlo. */
	fun baseDesdeServidor(direccionJellyfin: String?, puerto: Int): String? {
		val cruda = direccionJellyfin?.trim()
		if (cruda.isNullOrEmpty()) return null

		// Sin esquema no hay forma de saber si el site va por TLS, y asumirlo
		// mal deja la sección muerta en un sitio y abierta en el otro.
		val esquema = when {
			cruda.startsWith("https://", ignoreCase = true) -> "https"
			cruda.startsWith("http://", ignoreCase = true) -> "http"
			else -> {
				LiveLog.warn("url.sinEsquema", "direccion" to cruda)
				return null
			}
		}

		val resto = cruda.substringAfter("://")
		// La autoridad termina donde empieza la ruta, la consulta o el ancla.
		val autoridad = resto.takeWhile { it != '/' && it != '?' && it != '#' }
		if (autoridad.isEmpty()) {
			LiveLog.warn("url.sinHost", "direccion" to cruda)
			return null
		}

		// Credenciales en la dirección: raro, pero legal, y si no se quitan
		// acaban dentro de la URL del servicio de canales.
		val sinCredenciales = autoridad.substringAfterLast('@')

		val host = if (sinCredenciales.startsWith("[")) {
			// IPv6 literal: el puerto va detrás del corchete de cierre.
			sinCredenciales.substringBefore(']', missingDelimiterValue = "").plus(']')
				.takeIf { it.length > 1 } ?: return nulo(cruda)
		} else {
			sinCredenciales.substringBefore(':')
		}

		if (host.isEmpty()) return nulo(cruda)
		return "$esquema://$host:$puerto"
	}

	/**
	 * El host tal como se le pregunta al registro: **la clave de un site**.
	 *
	 * Es más tolerante que [baseDesdeServidor] y tiene que serlo. Aquélla
	 * deriva la URL del servicio de canales y por eso exige esquema —sin él no
	 * se puede saber si el site va por TLS, y asumirlo mal deja la sección
	 * muerta—. Aquí no hay nada que adivinar: solo hace falta el nombre.
	 *
	 * Se baja a minúsculas y **se quita el punto final** del FQDN absoluto. Lo
	 * segundo lo avisó el proyecto del autorizador: `tv.isp.mx.` y `tv.isp.mx`
	 * son el mismo nombre para el DNS, y si cada lado normaliza distinto hay
	 * hosts que la app pregunta y el registro no encuentra. El reporte que eso
	 * produce es «a este cliente no le funciona y a los demás sí», que es el
	 * más caro de diagnosticar.
	 *
	 * Un IPv6 literal **conserva sus corchetes**, que es como se escribe y como
	 * se compara.
	 */
	fun hostDeServidor(direccionJellyfin: String?): String? {
		val cruda = direccionJellyfin?.trim()
		if (cruda.isNullOrEmpty()) return null

		// El esquema es opcional aquí, a diferencia de `baseDesdeServidor`.
		val resto = if ("://" in cruda) cruda.substringAfter("://") else cruda
		val autoridad = resto.takeWhile { it != '/' && it != '?' && it != '#' }
		val sinCredenciales = autoridad.substringAfterLast('@')

		val host = if (sinCredenciales.startsWith("[")) {
			sinCredenciales.substringBefore(']', missingDelimiterValue = "").plus(']')
				.takeIf { it.length > 1 } ?: return nuloHost(cruda)
		} else {
			sinCredenciales.substringBefore(':')
		}

		val limpio = host.lowercase().trimEnd('.')
		if (limpio.isEmpty()) return nuloHost(cruda)
		return limpio
	}

	private fun nuloHost(direccion: String): String? {
		LiveLog.warn("registro.hostIlegible", "direccion" to direccion)
		return null
	}

	private fun nulo(direccion: String): String? {
		LiveLog.warn("url.hostIlegible", "direccion" to direccion)
		return null
	}
}
