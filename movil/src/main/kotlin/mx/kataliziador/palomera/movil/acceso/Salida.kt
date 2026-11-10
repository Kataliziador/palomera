package mx.kataliziador.palomera.movil.acceso

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Salir de la cuenta, devolviendo la tele.
 *
 * ## Por qué no basta con olvidar el token
 *
 * Es el mismo defecto que la caja arregló el 09-nov. Olvidarlo **sólo aquí**
 * deja la sesión viva en Jellyfin ocupando una de las teles que permite
 * `MaxActiveSessions`, y al reactivar **este mismo aparato** el servidor
 * contesta 403: cuenta las sesiones antes de cerrar la vieja del mismo
 * dispositivo. El teléfono choca contra su propio fantasma.
 *
 * ## Y al abonado no se le retiene nunca
 *
 * Si el servidor no contesta en [ESPERA_MS], se registra y se sale igual. Un
 * teléfono que no deja cerrar sesión no lo desbloquea nadie; un cupo atascado
 * sí lo desbloquea el proveedor desde su panel. El daño es asimétrico y la
 * decisión también.
 */
const val ESPERA_MS = 5_000L

suspend fun liberarLaTele(servidor: String, cabecera: String) {
	val url = servidor.trimEnd('/') + "/Sessions/Logout"
	try {
		withTimeout(ESPERA_MS) {
			val codigo = postSinCuerpo(url, cabecera)
			if (codigo in 200..299) {
				Timber.i("tele.liberada servidor=%s codigo=%d", servidor, codigo)
			} else {
				// **Se registra con su código, no en silencio.** «No se pudo»
				// no distingue un 401 —el token ya no valía— de un 500, y son
				// cosas distintas para quien lo lea dentro de un mes.
				Timber.w("tele.noLiberada servidor=%s codigo=%d", servidor, codigo)
			}
		}
	} catch (err: TimeoutCancellationException) {
		Timber.w("tele.noLiberada servidor=%s motivo=plazo-agotado", servidor)
	} catch (err: Exception) {
		Timber.w(err, "tele.noLiberada servidor=%s motivo=%s", servidor, err::class.simpleName)
	}
}

private suspend fun postSinCuerpo(url: String, cabecera: String): Int =
	kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
		val conexion = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
			requestMethod = "POST"
			connectTimeout = 4_000
			readTimeout = 4_000
			setRequestProperty("Authorization", cabecera)
			setRequestProperty("Content-Length", "0")
			doOutput = true
		}
		try {
			conexion.outputStream.use { }
			conexion.responseCode
		} finally {
			conexion.disconnect()
		}
	}
