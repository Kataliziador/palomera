package org.jellyfin.livetv.source

/**
 * Un canal, tal como lo describe el `config.json` del servidor de configuración.
 *
 * Nada de esto se codifica en la app (CLAUDE.md regla 12): ni nombre, ni URL,
 * ni logo. El módulo recibe el perfil y obedece.
 */
/**
 * Un par etiqueta/valor que la app pinta sin saber qué significa.
 *
 * Es la válvula de escape del contrato: lo que un ISP quiera añadir mañana
 * —«Calidad: HD», «Región: Norte»— **si es solo texto**, llega sin una versión
 * nueva de la APK. En cuanto algo necesita comportamiento deja de ser dato y
 * vuelve a ser código, que es una versión — una, para todos (`CLAUDE.md` §6).
 */
data class ChannelExtra(
	val label: String,
	val value: String,
)

data class ChannelProfile(
	val id: String,
	val name: String,
	val url: String,

	/**
	 * Número de canal para marcarlo con el mando.
	 *
	 * Con ~120 canales nadie navega con flechas: teclea el número. Sin esto la
	 * lista es inusable en un mando de verdad, por bien que se vea.
	 */
	val number: Int? = null,

	/** Posición dentro de su grupo. A igualdad, manda el número. */
	val order: Int? = null,

	val group: String? = null,
	val logoUrl: String? = null,

	/**
	 * Códecs declarados. La cascada los necesita **antes** de abrir el stream,
	 * cuando todavía no hay pistas que inspeccionar (LIVETV_ENGINE.md §4.6).
	 *
	 * Son una pista, no la verdad: si el stream real los desmiente, manda el
	 * stream y la discrepancia se registra.
	 */
	val videoCodec: String? = null,
	val audioCodec: String? = null,

	val targetOffsetMs: Long? = null,
	val audioDefault: String? = null,
	val interlaced: Boolean = false,
	val hd: Boolean = false,

	/** Filtro duro: un motor ausente de esta lista no se elige nunca. */
	val enginesAllowed: Set<String>? = null,

	/**
	 * Nombres alternativos para la búsqueda.
	 *
	 * Con ~120 canales nadie navega con flechas: busca. Y busca por lo que
	 * llama al canal —«canal 2»— no por su nombre comercial.
	 */
	val aliases: List<String> = emptyList(),

	/**
	 * El canal está en la guía pero no se puede ver ahora.
	 *
	 * Distinto de no publicarlo. **Un canal que desaparece genera una llamada a
	 * soporte**; uno que dice por qué no está, no. El `id` además sostiene los
	 * favoritos del abonado, así que ocultar un canal borrándolo se los rompe.
	 */
	val unavailable: Boolean = false,
	val unavailableMessage: String? = null,

	/** Clasificación de contenido. Sin comportamiento todavía: futuro PIN. */
	val rating: String? = null,

	/** Segunda fuente del canal. Sin comportamiento todavía: exige motor (M-x). */
	val backupUrl: String? = null,

	/** Texto libre del ISP, acotado y sin significado para la app. */
	val extras: List<ChannelExtra> = emptyList(),
)

/**
 * Referencia a una pista de audio.
 *
 * **No es un PID.** Flussonic renumera los PIDs al remuxar y entrega todos los
 * canales con audio en `0xdd` (y `0xde` el segundo), así que el PID es un alias
 * de la posición y no identifica nada. La clave real es el idioma; el PID sólo
 * desempata dentro de una misma sesión. Ver CHANNELS.md §2.bis.
 */
data class AudioTrackRef(
	val language: String?,
	val pid: Int,
	val channels: Int,
	val label: String? = null,
)

/**
 * Normaliza códigos de idioma a uno canónico.
 *
 * Hace falta porque el lineup no usa ISO: `estrellashd-mp4` y `estrellassd-mp2v`
 * etiquetan su audio como `esl`, que no es un código válido, mientras que los
 * canales de 70W sí usan `spa`. Sin esto no hay forma de distinguir el español
 * del inglés en los canales con doble pista.
 */
object LanguageNormalizer {
	private val spanish = setOf("spa", "esp", "esl", "es", "spanish", "castellano")
	private val english = setOf("eng", "en", "english")

	fun normalize(raw: String?): String? {
		val key = raw?.trim()?.lowercase() ?: return null
		if (key.isEmpty() || key == "und") return null
		return when (key) {
			in spanish -> "spa"
			in english -> "eng"
			else -> key
		}
	}
}
