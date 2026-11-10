package mx.kataliziador.palomera.movil.acceso

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * La sesión del abonado: lo que la app recuerda entre arranques.
 *
 * **No hay campo para la contraseña, y es a propósito.** El token sirve para
 * todo y el servidor lo puede revocar; una contraseña guardada no se revoca,
 * vale en todos los sites donde el abonado reusó la misma, y convierte un
 * teléfono perdido en un problema del proveedor. No tener dónde ponerla es la
 * única forma de que no acabe ahí dentro por descuido.
 */
data class Sesion(
	val servidor: String,
	val token: String,
	val usuarioId: String,
	val nombre: String = "",
	/** La marca del site, si la hubo. Un adorno: su ausencia no impide entrar. */
	val proveedor: String? = null,
	/**
	 * El identificador del Jellyfin del site.
	 *
	 * Es la **clave de caché** de su lista de canales, y por eso se guarda:
	 * indexar por dirección haría que un site que cambia de dominio perdiera
	 * su lista, y que uno que cambia de site heredara la del anterior
	 * (`CLAUDE.md` §6).
	 */
	val servidorId: String? = null,
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Cómo se escribe en el disco. */
fun aTexto(s: Sesion): String = buildJsonObject {
	put("servidor", JsonPrimitive(s.servidor))
	put("token", JsonPrimitive(s.token))
	put("usuarioId", JsonPrimitive(s.usuarioId))
	put("nombre", JsonPrimitive(s.nombre))
	s.proveedor?.let { put("proveedor", JsonPrimitive(it)) }
	s.servidorId?.let { put("servidorId", JsonPrimitive(it)) }
}.toString()

/**
 * Lo guardado, o `null`.
 *
 * **Una sesión a medias no se usa.** Un token sin servidor no lleva a ninguna
 * parte y dejaría a la app pidiéndole cosas a `null`; vale más volver a la
 * puerta, que es una pantalla diseñada, que entrar a una pantalla rota.
 */
fun leerSesion(texto: String?): Sesion? {
	if (texto.isNullOrBlank()) return null
	val raiz = runCatching { json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null

	fun campo(clave: String) = raiz[clave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

	val servidor = campo("servidor") ?: return null
	val token = campo("token") ?: return null
	val usuarioId = campo("usuarioId") ?: return null

	return Sesion(
		servidor = servidor,
		token = token,
		usuarioId = usuarioId,
		nombre = campo("nombre").orEmpty(),
		proveedor = campo("proveedor"),
		servidorId = campo("servidorId"),
	)
}

/** Dónde vive. Mismo archivo que el identificador de instalación. */
private const val CLAVE = "sesion"

fun Context.sesionGuardada(): Sesion? =
	leerSesion(getSharedPreferences("palomera", Context.MODE_PRIVATE).getString(CLAVE, null))

fun Context.guardarSesion(s: Sesion) {
	getSharedPreferences("palomera", Context.MODE_PRIVATE).edit().putString(CLAVE, aTexto(s)).apply()
}

fun Context.olvidarSesion() {
	getSharedPreferences("palomera", Context.MODE_PRIVATE).edit().remove(CLAVE).apply()
}
