package mx.kataliziador.palomera.movil.acceso

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * La puerta: lo único de la app que habla con el Jellyfin del ISP **sin** tener
 * todavía un token.
 *
 * Dos caminos, y los dos se quedan:
 *
 * - **el código**, que el abonado le dicta por teléfono a su proveedor y éste
 *   autoriza desde el panel de abonados. Es el camino normal;
 * - **usuario y contraseña**, que es el único que funciona en un site sin
 *   panel instalado y **es por donde entra el revisor de Google**
 *   (`PLAY.md` §6). No se quita.
 *
 * Nada de lo que pasa por aquí se guarda salvo lo que [Sesion] admite, que no
 * incluye la contraseña.
 */
class Puerta(
	private val servidor: String,
	private val cabecera: String,
) {
	/** Usuario y contraseña. La contraseña vive en memoria y nada más. */
	suspend fun entrar(usuario: String, clave: String): Entrada {
		val (codigo, cuerpo) = enviar(
			"/Users/AuthenticateByName",
			"""{"Username":${comillas(usuario)},"Pw":${comillas(clave)}}""",
		)
		return leerEntrada(codigo, cuerpo)
	}

	/** Pide un código nuevo para que el proveedor lo autorice. */
	suspend fun pedirCodigo(): CodigoDeActivacion? {
		val (codigo, cuerpo) = pedir("/QuickConnect/Initiate")
		if (codigo == null || codigo !in 200..299) {
			Timber.w("acceso.codigoNoDisponible codigo=%s", codigo)
			return null
		}
		return leerCodigoDeActivacion(cuerpo.orEmpty())
	}

	/** ¿Ya lo autorizaron? Se llama en bucle mientras la pantalla está puesta. */
	suspend fun yaAutorizado(secreto: String): Boolean {
		val (codigo, cuerpo) = pedir("/QuickConnect/Connect?secret=$secreto")
		if (codigo == null || codigo !in 200..299) return false
		return leerCodigoDeActivacion(cuerpo.orEmpty())?.autorizado == true
	}

	/** Cambia un código ya autorizado por una sesión. */
	suspend fun entrarConCodigo(secreto: String): Entrada {
		val (codigo, cuerpo) = enviar(
			"/Users/AuthenticateWithQuickConnect",
			"""{"Secret":${comillas(secreto)}}""",
		)
		return leerEntrada(codigo, cuerpo)
	}

	/**
	 * ¿Hay un Jellyfin en esa dirección?
	 *
	 * Se pregunta **antes** de pedirle la contraseña a nadie: una dirección
	 * mal escrita tiene que decirlo ahí, no después de que el abonado teclee
	 * sus datos en un teléfono.
	 */
	suspend fun hayServidor(): String? {
		val (codigo, cuerpo) = pedir("/System/Info/Public")
		if (codigo == null || codigo !in 200..299) return null
		return runCatching {
			kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
				.parseToJsonElement(cuerpo.orEmpty())
				.let { (it as kotlinx.serialization.json.JsonObject)["ServerName"] }
				?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
		}.getOrNull()
	}

	/** ¿Este site ofrece el camino del código? Si no, sólo queda la contraseña. */
	suspend fun admiteCodigo(): Boolean {
		val (codigo, cuerpo) = pedir("/QuickConnect/Enabled")
		return codigo in 200..299 && cuerpo?.trim()?.lowercase() == "true"
	}

	// ---- lo de debajo no decide nada; sólo mueve bytes ----

	private suspend fun pedir(ruta: String): Pair<Int?, String?> = llamar(ruta, null)

	private suspend fun enviar(ruta: String, cuerpo: String): Pair<Int?, String?> =
		llamar(ruta, cuerpo)

	/**
	 * **Devuelve el código HTTP aunque sea de error**, que es justo lo que
	 * distingue un 403 de un 401 y de un «no contesta». Un `catch` que lo
	 * aplastara todo a `null` haría indistinguibles los tres casos de
	 * [Entrada], que es el defecto que esta clase existe para no repetir.
	 */
	private suspend fun llamar(ruta: String, cuerpo: String?): Pair<Int?, String?> =
		withContext(Dispatchers.IO) {
			var conexion: HttpURLConnection? = null
			try {
				conexion = (URL(servidor.trimEnd('/') + ruta).openConnection() as HttpURLConnection)
					.apply {
						requestMethod = if (cuerpo == null) "GET" else "POST"
						connectTimeout = 8_000
						readTimeout = 15_000
						setRequestProperty("Content-Type", "application/json")
						setRequestProperty("Authorization", cabecera)
						if (cuerpo != null) doOutput = true
					}
				cuerpo?.let { c -> conexion.outputStream.use { it.write(c.toByteArray()) } }

				val codigo = conexion.responseCode
				val texto = runCatching {
					if (codigo in 200..299) conexion.inputStream.bufferedReader().use { it.readText() }
					else conexion.errorStream?.bufferedReader()?.use { it.readText() }
				}.getOrNull()

				codigo to texto
			} catch (err: Exception) {
				// Sin código: ni siquiera hubo respuesta. Es un caso propio.
				Timber.w(err, "acceso.sinRespuesta ruta=%s motivo=%s", ruta, err::class.simpleName)
				null to null
			} finally {
				conexion?.disconnect()
			}
		}

	private fun comillas(v: String) =
		"\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
