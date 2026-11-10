package mx.kataliziador.palomera.movil.acceso

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Qué pasó al intentar entrar al Jellyfin del ISP.
 *
 * **Los cuatro casos son distintos en pantalla**, y la tabla es la misma que
 * la caja aprendió a golpes el 28-oct (`CLAUDE.md` §4.7):
 *
 * | del servidor | qué significa | qué hace el abonado |
 * |---|---|---|
 * | 401 | la clave no es ésa | la reescribe |
 * | 403 | el contrato está suspendido | **llama a pagar, no a pedir un código** |
 * | no contesta | sin internet, o el site caído | espera |
 *
 * Y es **asimétrico**: equivocarse hacia «no entras» molesta a quien paga;
 * equivocarse hacia «entras» regala el servicio. Por eso ninguna rama es el
 * `else` de otra y todas están probadas.
 */
sealed interface Entrada {
	data class Adentro(
		val token: String,
		val usuarioId: String,
		val nombre: String,
		/** Con qué nombre se guarda la lista de canales de este site. */
		val servidorId: String = "",
	) : Entrada

	/** Se puede reintentar sin llamar a nadie. */
	data object ClaveEquivocada : Entrada

	/**
	 * El contrato está suspendido. El token seguiría sirviendo; lo que no hay
	 * es servicio.
	 *
	 * **No lleva a la pantalla del código.** Un abonado suspendido que pide un
	 * código nuevo entra otra vez, y el proveedor deja de cobrar sin enterarse.
	 */
	data object Suspendido : Entrada

	/** Ni sí ni no. No se le cierra la puerta a nadie por esto. */
	data object NoContesta : Entrada
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Interpreta la respuesta de `POST /Users/AuthenticateByName`.
 *
 * [codigo] es `null` cuando no hubo ni respuesta —la conexión no llegó—, que
 * es un caso distinto de cualquier código HTTP y se trata como [Entrada.NoContesta].
 */
fun leerEntrada(codigo: Int?, cuerpo: String?): Entrada {
	when (codigo) {
		null -> return Entrada.NoContesta
		401 -> return Entrada.ClaveEquivocada
		403 -> return Entrada.Suspendido
	}
	if (codigo !in 200..299) return Entrada.NoContesta

	// **Un 200 sin token no es entrar.** Pasa con un proxy mal puesto que
	// devuelve su página de cortesía: tratarlo como éxito deja la app dentro
	// sin token, y el fallo sale tres pantallas después sin relación aparente.
	val raiz = runCatching { json.parseToJsonElement(cuerpo.orEmpty()).jsonObject }.getOrNull()
		?: return Entrada.NoContesta

	val token = raiz["AccessToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		?: return Entrada.NoContesta
	val usuario = raiz["User"]?.jsonObject ?: return Entrada.NoContesta
	val id = usuario["Id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		?: return Entrada.NoContesta

	return Entrada.Adentro(
		token = token,
		usuarioId = id,
		nombre = usuario["Name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
		servidorId = raiz["ServerId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
	)
}

/**
 * El código que el abonado le dicta por teléfono a su proveedor.
 *
 * El [secreto] no se enseña: es con lo que la app pregunta si ya lo
 * autorizaron.
 */
data class CodigoDeActivacion(
	val codigo: String,
	val secreto: String,
	val autorizado: Boolean,
)

/**
 * Interpreta `GET /QuickConnect/Initiate` y `/Connect`.
 *
 * Devuelve `null` antes que medio código: **sin secreto no hay forma de
 * preguntar si lo autorizaron**, así que la pantalla enseñaría un número que
 * nunca va a avanzar y el abonado esperaría delante de él.
 */
fun leerCodigoDeActivacion(cuerpo: String): CodigoDeActivacion? {
	val raiz = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull() ?: return null

	val codigo = raiz["Code"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val secreto = raiz["Secret"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null

	return CodigoDeActivacion(
		codigo = codigo,
		secreto = secreto,
		autorizado = raiz["Authenticated"]?.jsonPrimitive?.booleanOrNull == true,
	)
}
