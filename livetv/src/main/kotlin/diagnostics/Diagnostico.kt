package org.jellyfin.livetv.diagnostics

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Cómo se ha reproducido en **esta caja**, acumulado desde que se instaló.
 *
 * ## Por qué existe, y por qué ahora
 *
 * La caja mayoritaria del cliente —la ZTE B866V2— **solo admite instalaciones
 * desde la ficha pública de Play**, así que la primera vez que la veamos será
 * con la app ya publicada. Si ese primer APK no sabe informar, lo que nos llega
 * es una llamada diciendo «no funciona» y seguimos igual de ciegos: habremos
 * gastado el envío sin aprender nada.
 *
 * Con esto, la primera ZTE que abra la app nos dice qué decoders tiene, si el
 * tunneled le dio imagen, cuánto tarda en el primer fotograma y qué canales le
 * fallan. Eso se convierte en un `deviceOverrides` publicado desde el generador
 * del `config.json`, que llega a todos los sites **sin tocar la APK**.
 *
 * ## Qué es y qué no es
 *
 * Es la capa de **diagnóstico técnico** de `CLAUDE.md` §8: anónima y necesaria
 * para operar. **No es la capa de datos de abonado**, y las dos no se mezclan:
 *
 * - Se cuenta **cuántas sesiones fallaron en un canal**, no qué vio nadie ni
 *   cuándo. Un canal que nunca falla y nunca tarda no aporta ninguna línea.
 * - No hay marcas de tiempo por sesión, ni orden, ni duración individual. De
 *   aquí no se reconstruye un historial de visionado, que es justo lo que lo
 *   dejaría fuera del diagnóstico técnico y bajo la LFPDPPP.
 * - El identificador que lo acompaña es el `installId` del registro: opaco y
 *   por instalación, no del abonado ni de la caja.
 *
 * ## Dónde viaja
 *
 * Montado en la petición que la caja ya le hace al registro, **no en un
 * servicio nuevo**. Eso respeta la regla 6 del autorizador —nada más puede
 * depender de él— porque no añade una dependencia: es un campo más en una
 * conversación que ya existía, y si se pierde no pasa nada.
 *
 * La cadencia encaja con lo que hace falta: **no necesitamos telemetría
 * continua, necesitamos un informe por modelo de caja.**
 */
class Diagnostico private constructor(context: Context) {

	private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	/**
	 * Anota cómo acabó una sesión de reproducción.
	 *
	 * Acumulativo desde la instalación y **nunca se vacía al enviarlo**: el
	 * registro recibe el total cada vez y la diferencia la calcula él. Vaciar
	 * aquí significaría perder lo acumulado cada vez que un envío falla, que es
	 * precisamente el caso que importa —una caja con mala conexión.
	 */
	@Synchronized
	fun anotarSesion(
		canal: String?,
		huboImagen: Boolean,
		msPrimerFotograma: Long,
		rebuffers: Int,
		msReproducidos: Long,
		tunneledPedido: Boolean,
		tunneledRescatado: Boolean,
		causaDeFallo: String?,
	) {
		val e = prefs.edit()
		e.putInt(K_SESIONES, prefs.getInt(K_SESIONES, 0) + 1)
		if (huboImagen) {
			e.putInt(K_CON_IMAGEN, prefs.getInt(K_CON_IMAGEN, 0) + 1)
			if (msPrimerFotograma > 0) {
				e.putLong(K_PRIMER_SUMA, prefs.getLong(K_PRIMER_SUMA, 0) + msPrimerFotograma)
				e.putInt(K_PRIMER_N, prefs.getInt(K_PRIMER_N, 0) + 1)
				e.putLong(K_PRIMER_MAX, maxOf(prefs.getLong(K_PRIMER_MAX, 0), msPrimerFotograma))
			}
		}
		e.putInt(K_REBUFFERS, prefs.getInt(K_REBUFFERS, 0) + rebuffers)
		e.putLong(K_MINUTOS, prefs.getLong(K_MINUTOS, 0) + msReproducidos / 60_000)
		if (tunneledPedido) e.putInt(K_TUN_PEDIDO, prefs.getInt(K_TUN_PEDIDO, 0) + 1)
		if (tunneledRescatado) e.putInt(K_TUN_RESCATE, prefs.getInt(K_TUN_RESCATE, 0) + 1)

		if (causaDeFallo != null) {
			e.putString(K_FALLOS, sumarAcotado(prefs.getString(K_FALLOS, null), causaDeFallo, MAX_CAUSAS))
			if (canal != null) {
				e.putString(K_CANALES, sumarAcotado(prefs.getString(K_CANALES, null), canal, MAX_CANALES))
			}
		}
		e.apply()
	}

	/**
	 * El resumen que viaja al registro, o `null` si no hay nada que contar.
	 *
	 * Devolver `null` con cero sesiones es deliberado: una caja recién
	 * instalada no tiene nada que decir, y mandar un objeto de ceros solo
	 * ensucia la lectura del otro lado.
	 */
	fun resumen(): JsonObject? {
		val sesiones = prefs.getInt(K_SESIONES, 0)
		if (sesiones == 0) return null

		val n = prefs.getInt(K_PRIMER_N, 0)
		return buildJsonObject {
			put("sesiones", sesiones)
			put("conImagen", prefs.getInt(K_CON_IMAGEN, 0))
			if (n > 0) {
				// Media y no mediana: guardar la mediana exacta exigiría
				// conservar todas las muestras, y lo que hace falta responder
				// —«¿esta caja tarda más que las demás?»— la media lo responde.
				put("msPrimerFotogramaMedia", prefs.getLong(K_PRIMER_SUMA, 0) / n)
				put("msPrimerFotogramaMax", prefs.getLong(K_PRIMER_MAX, 0))
			}
			put("rebuffers", prefs.getInt(K_REBUFFERS, 0))
			put("minutosReproducidos", prefs.getLong(K_MINUTOS, 0))
			put("tunneledPedido", prefs.getInt(K_TUN_PEDIDO, 0))
			put("tunneledRescatado", prefs.getInt(K_TUN_RESCATE, 0))
			mapaDe(prefs.getString(K_FALLOS, null))?.let { put("fallosPorCausa", it) }
			mapaDe(prefs.getString(K_CANALES, null))?.let { put("fallosPorCanal", it) }
		}
	}

	/** Para el menú de diagnóstico: empezar de cero al medir algo a propósito. */
	fun olvidar() {
		prefs.edit().clear().apply()
		LiveLog.warn("diagnostico.olvidado")
	}

	/**
	 * Suma uno a una clave de un mapa guardado como JSON, **con tope**.
	 *
	 * El tope importa: con 120 canales y causas de fallo que podrían incluir
	 * mensajes de excepción, sin él una caja con un headend roto acabaría
	 * mandando kilobytes. Pasado el tope se deja de añadir claves nuevas, pero
	 * se siguen contando las que ya están —que son las que más fallan, y por
	 * tanto las que interesan.
	 */
	private fun sumarAcotado(json: String?, clave: String, tope: Int): String {
		val actual = mapaDe(json)?.toMutableMap() ?: mutableMapOf()
		val previo = actual[clave]?.jsonPrimitive?.int ?: 0
		if (previo == 0 && actual.size >= tope) return json ?: "{}"
		actual[clave] = JsonPrimitive(previo + 1)
		return JsonObject(actual).toString()
	}

	private fun mapaDe(json: String?): JsonObject? =
		json?.let { runCatching { lector.parseToJsonElement(it).jsonObject }.getOrNull() }
			?.takeIf { it.isNotEmpty() }

	companion object {
		private const val PREFS = "livetv.diagnostico"
		private const val K_SESIONES = "sesiones"
		private const val K_CON_IMAGEN = "conImagen"
		private const val K_PRIMER_SUMA = "primerSuma"
		private const val K_PRIMER_N = "primerN"
		private const val K_PRIMER_MAX = "primerMax"
		private const val K_REBUFFERS = "rebuffers"
		private const val K_MINUTOS = "minutos"
		private const val K_TUN_PEDIDO = "tunPedido"
		private const val K_TUN_RESCATE = "tunRescate"
		private const val K_FALLOS = "fallos"
		private const val K_CANALES = "canales"

		/** Causas distintas que se guardan. Son pocas por diseño: son enums. */
		private const val MAX_CAUSAS = 12

		/** Canales con fallo que se guardan. Un lineup ronda los 120. */
		private const val MAX_CANALES = 40

		private val lector = Json { ignoreUnknownKeys = true }

		@Volatile
		private var instancia: Diagnostico? = null

		fun of(context: Context): Diagnostico =
			instancia ?: synchronized(this) {
				instancia ?: Diagnostico(context.applicationContext).also { instancia = it }
			}
	}
}
