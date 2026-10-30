package org.jellyfin.livetv.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.diagnostics.Diagnostico
import org.jellyfin.livetv.diagnostics.LiveLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Cliente del registro de sites.
 *
 * **Lo que de verdad compra no es el control de acceso, es quitar la convención
 * del puerto** (`REGISTRY_SCHEMA.md` §1): cada site dice dónde está su servicio
 * de canales, y moverlo pasa de exigir una versión de la app a editar una fila.
 *
 * Tres reglas que gobiernan el diseño, todas en la misma dirección —**un fallo
 * nuestro no puede apagar televisiones**:
 *
 * 1. **No reintenta.** Un fallo es un fallo hasta la próxima vez que alguien
 *    entre a la sección. Esperar más solo retrasa la entrada a TV en vivo.
 * 2. **No vuelve a preguntar mientras la autorización esté viva.** Una consulta
 *    por site, no por arranque. Eso fija la carga del registro en unas dos
 *    peticiones por minuto con diez ISPs.
 * 3. **La respuesta se guarda íntegra o no se guarda.** El parser ya descarta
 *    lo que venga a medias; aquí solo se persiste lo que pasó por él.
 *
 * Sin dependencias nuevas (regla 11): `HttpURLConnection` y la plataforma.
 */
class RegistryRepository(context: Context) {

	private val contexto = context.applicationContext

	private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private val directorio = File(context.filesDir, "livetv")

	/**
	 * Un archivo por host, por la misma razón que la caché del `config.json`:
	 * una caja puede cambiar de site y heredar la autorización del anterior
	 * sería autorizar lo que nadie autorizó.
	 */
	private fun cacheDe(host: String) = File(directorio, "registro-${sanear(host)}.json")

	private fun sanear(host: String) = host.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

	/**
	 * Identificador opaco **por instalación**, no del abonado ni de la caja.
	 *
	 * Sirve para contar cajas, no para identificar a nadie. Aun así sale del
	 * dispositivo hacia un servidor nuestro, así que entra en el aviso de
	 * privacidad y en el formulario de Seguridad de los datos de Play: es la
	 * capa de **diagnóstico técnico** de `CLAUDE.md` §8, nunca la de datos de
	 * abonado.
	 *
	 * Se borra al desinstalar, que es lo que lo hace «por instalación».
	 */
	val installId: String
		get() = prefs.getString(K_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
			prefs.edit().putString(K_INSTALL_ID, it).apply()
		}

	/** Lo último que el registro dijo de este host, si alguna vez dijo algo. */
	fun guardada(host: String): SiteAuthorization? {
		val archivo = cacheDe(host)
		if (!archivo.exists()) return null
		// La fecha del archivo es cuándo contestó el registro: así una
		// autorización guardada sabe lo vieja que es aunque la app se haya
		// reinstalado el reloj por medio.
		return runCatching { RegistryParser.parse(host, archivo.readText(), archivo.lastModified()) }
			.getOrElse {
				LiveLog.warn("registro.cacheIlegible", "host" to host, "motivo" to it.message)
				null
			}
	}

	/**
	 * Tira lo que el registro haya dicho, para volver a preguntar.
	 *
	 * Hace falta para probar —una autorización vigente impide ver qué pasa
	 * cuando se deniega— y también en operación: un técnico que acaba de dar de
	 * alta un site no tiene por qué esperar semanas a que caduque lo guardado.
	 *
	 * Devuelve cuántas olvidó, para poder decirlo en pantalla.
	 */
	fun olvidar(): Int {
		val archivos = directorio.listFiles { f -> f.name.startsWith("registro-") } ?: return 0
		val borrados = archivos.count { it.delete() }
		LiveLog.warn("registro.olvidado", "autorizaciones" to borrados)
		return borrados
	}

	/**
	 * Pregunta al registro, o devuelve `null` si no se pudo.
	 *
	 * `null` **no es un error que haya que propagar**: es la entrada normal de
	 * la escalera de degradación. Quien llama tiene la guardada y
	 * [DecisorDeAcceso].
	 */
	suspend fun resolver(baseUrl: String?, host: String): SiteAuthorization? =
		withContext(Dispatchers.IO) {
			if (baseUrl.isNullOrBlank()) {
				LiveLog.info("registro.sinDireccion", "detalle" to "no hay REGISTRY_URL compilada")
				return@withContext null
			}

			val url = baseUrl.trimEnd('/') + RUTA
			val cuerpo = peticion(host)

			runCatching { postear(url, cuerpo) }
				.mapCatching { texto ->
					RegistryParser.parse(host, texto, System.currentTimeMillis()).also {
						guardar(host, texto)
						LiveLog.info(
							"registro.respuesta",
							"host" to host,
							"autorizado" to it.authorized,
							"hasta" to it.untilEpochMs,
							"channelsUrl" to it.channelsUrl,
							"marca" to it.brand?.displayName,
						)
					}
				}
				.getOrElse {
					// Que no conteste es lo esperado en un despliegue donde la
					// caja llega a la red del ISP pero no a internet. Se avisa
					// y se sigue: la decisión la toma el decisor.
					LiveLog.warn("registro.sinRespuesta", "host" to host, "motivo" to it.message)
					null
				}
		}

	/**
	 * «¿De quién soy cliente?», para no tener que escribir la dirección.
	 *
	 * Sin [codigo] la contesta la **IP de origen**: el registro mira desde qué
	 * red llega la petición. Con él, la IP ni se mira — quien teclea un código
	 * está diciendo explícitamente de quién es cliente, y puede estar en
	 * cualquier red.
	 *
	 * **No manda `device` ni `diagnostico`**, a diferencia de [resolver]: en
	 * este punto no hay site al que apuntarlos, y mandarlos sería entregar el
	 * inventario de la caja antes de saber siquiera si el registro nos conoce.
	 *
	 * El diseño entero está en `docs/selector-de-proveedor.md`.
	 */
	suspend fun proveedor(baseUrl: String?, codigo: String? = null): RespuestaProveedor =
		withContext(Dispatchers.IO) {
			if (baseUrl.isNullOrBlank()) {
				LiveLog.info("proveedor.sinDireccion", "detalle" to "no hay REGISTRY_URL compilada")
				return@withContext RespuestaProveedor.NoSeSupo
			}

			val cuerpo = buildJsonObject {
				put("appVersion", appVersion)
				put("installId", installId)
				codigo?.trim()?.takeIf { it.isNotEmpty() }?.let { put("codigo", it) }
			}.toString()

			runCatching { postearProveedor(baseUrl.trimEnd('/') + RUTA_PROVEEDOR, cuerpo) }
				.map { (estado, texto) ->
					when {
						estado == 429 -> RespuestaProveedor.DemasiadosIntentos
						estado !in 200..299 -> {
							LiveLog.warn("proveedor.sinRespuesta", "estado" to estado)
							RespuestaProveedor.NoSeSupo
						}

						else -> ProveedorParser.parse(texto).also {
							LiveLog.info(
								"proveedor.respuesta",
								"porCodigo" to (codigo != null),
								"encontrado" to (it is RespuestaProveedor.Encontrado),
								"proveedor" to (it as? RespuestaProveedor.Encontrado)?.proveedor?.id,
							)
						}
					}
				}
				.getOrElse {
					// Que no conteste es lo esperado en una caja que llega a la
					// red del ISP pero no a internet. **No es «no existe tu
					// proveedor»**: eso mandaría al abonado a buscar un dato
					// que sí tiene.
					LiveLog.warn("proveedor.sinRespuesta", "motivo" to it.message)
					RespuestaProveedor.NoSeSupo
				}
		}

	/**
	 * Como [postear], pero **devuelve el estado en vez de fallar con él**.
	 *
	 * El selector necesita distinguir el 429 del resto: un código bueno
	 * rechazado por el límite de intentos no es un código malo, y tratarlos
	 * igual hace que el abonado lo reescriba diez veces.
	 */
	private fun postearProveedor(url: String, cuerpo: String): Pair<Int, String> {
		val conexion = abrirConexion(url)
		try {
			conexion.outputStream.bufferedWriter().use { it.write(cuerpo) }
			val codigo = conexion.responseCode
			val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
			val texto = flujo?.bufferedReader()?.use { it.readText() }.orEmpty()
			if (texto.length > MAX_RESPUESTA) error("respuesta de ${texto.length} caracteres")
			return codigo to texto
		} finally {
			conexion.disconnect()
		}
	}

	/**
	 * El cuerpo de la pregunta (`REGISTRY_SCHEMA.md` §2).
	 *
	 * `device` existe para soporte: saber qué cajas hay en cada ISP sin tener
	 * que preguntárselo. Es, además, el único camino que tendremos para conocer
	 * la caja mayoritaria del cliente, que solo se puede instalar desde la
	 * ficha de Play.
	 */
	private fun peticion(host: String): String = buildJsonObject {
		put("host", host)
		put("appVersion", appVersion)
		put("installId", installId)
		put("device", dispositivo())
		// El diagnóstico va **montado aquí** y no en un servicio nuevo: es un
		// campo más en una conversación que ya existía, así que no añade nada
		// de lo que el registro pase a depender. Si se pierde, no pasa nada.
		Diagnostico.of(contexto).resumen()?.let { put("diagnostico", it) }
	}.toString()

	/**
	 * Lo que esta caja es, para soporte.
	 *
	 * **`decoders` es el campo que justifica todo B6.** La caja mayoritaria del
	 * cliente solo se instala desde la ficha de Play, así que la primera vez
	 * que sepamos qué decoders tiene será cuando una nos lo cuente por aquí. De
	 * ahí sale el `deviceOverrides` que el generador del `config.json` publica
	 * a todos los sites sin tocar la APK.
	 *
	 * Se mandan **sólo los de vídeo**: son los que deciden la cascada, y la
	 * lista entera de una caja moderna pasa de cien entradas.
	 */
	private fun dispositivo(): JsonObject = buildJsonObject {
		put("manufacturer", android.os.Build.MANUFACTURER)
		put("model", android.os.Build.MODEL)
		put("device", android.os.Build.DEVICE)
		put("sdkInt", JsonPrimitive(android.os.Build.VERSION.SDK_INT))
		runCatching {
			val caps = DeviceCapabilities.of(contexto)
			put("abis", kotlinx.serialization.json.JsonArray(caps.abis.map { JsonPrimitive(it) }))
			put("ramMb", JsonPrimitive(caps.totalRamMb))
			put(
				"decoders",
				kotlinx.serialization.json.JsonArray(
					caps.decoders.asSequence()
						.filter { it.mime.startsWith("video/") }
						.map { JsonPrimitive("${it.name}|${it.mime}|${if (it.software) "sw" else "hw"}") }
						.toList()
				),
			)
		}
	}

	/** La pone quien construye el repositorio; el módulo no conoce `BuildConfig`. */
	var appVersion: String = "desconocida"

	/**
	 * La conexión, con la confianza puesta.
	 *
	 * La comparten las dos preguntas que se le hacen al registro —la
	 * autorización y el selector de proveedor— **a propósito**: la
	 * comprobación `e)` de `herramientas/comprobar.py` exige que toda conexión
	 * propia pase por [RegistryTrust], y dos sitios donde ponerla son dos
	 * sitios donde olvidarla.
	 */
	private fun abrirConexion(url: String): HttpURLConnection =
		(URL(url).openConnection() as HttpURLConnection).apply {
			// Sin esto, una caja con Android anterior a 7.1.1 no valida el
			// certificado del registro y nunca entra a TV en vivo, sin dar
			// error visible. En claro no hace nada (`RegistryTrust.aplicarA`).
			RegistryTrust.aplicarA(this, contexto)
			connectTimeout = LiveTuning.REGISTRY_CONNECT_TIMEOUT_MS
			readTimeout = LiveTuning.REGISTRY_READ_TIMEOUT_MS
			requestMethod = "POST"
			doOutput = true
			setRequestProperty("Content-Type", "application/json")
			setRequestProperty("Accept", "application/json")
			instanceFollowRedirects = true
		}

	private fun postear(url: String, cuerpo: String): String {
		val conexion = abrirConexion(url)

		try {
			conexion.outputStream.bufferedWriter().use { it.write(cuerpo) }

			val codigo = conexion.responseCode
			if (codigo !in 200..299) error("HTTP $codigo ${conexion.responseMessage}")

			// La respuesta del registro son cuatro campos. Un cuerpo grande
			// significa que la URL no apunta a lo que creemos.
			val texto = conexion.inputStream.bufferedReader().use { it.readText() }
			if (texto.length > MAX_RESPUESTA) error("respuesta de ${texto.length} caracteres")
			return texto
		} finally {
			conexion.disconnect()
		}
	}

	private fun guardar(host: String, texto: String) {
		runCatching {
			directorio.mkdirs()
			val temporal = File(directorio, "registro-${sanear(host)}.tmp")
			temporal.writeText(texto)
			if (!temporal.renameTo(cacheDe(host))) {
				temporal.delete()
				error("no se pudo renombrar")
			}
		}.onFailure {
			// Perder la caché no impide entrar ahora, pero sí la próxima vez
			// sin red: se avisa en vez de callarlo.
			LiveLog.warn("registro.cacheNoGuardada", "host" to host, "motivo" to it.message)
		}
	}

	private companion object {
		const val PREFS = "livetv.registro"
		const val K_INSTALL_ID = "installId"
		const val RUTA = "/v1/resolve"
		const val RUTA_PROVEEDOR = "/v1/proveedor"

		/** Holgado para cuatro campos y un mensaje largo; lejos de una página de error. */
		const val MAX_RESPUESTA = 64 * 1024
	}
}
