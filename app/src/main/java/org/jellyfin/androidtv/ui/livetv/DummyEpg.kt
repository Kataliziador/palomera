package org.jellyfin.androidtv.ui.livetv

import org.jellyfin.livetv.source.ChannelProfile
import java.util.concurrent.TimeUnit

/**
 * Qué se está emitiendo y qué viene después.
 *
 * El modelo es deliberadamente pobre: título, inicio y fin. Es lo único que la
 * barra necesita, y atarse a más ahora haría que cualquier fuente real —XMLTV,
 * la API de Flussonic, el servidor de configuración— tuviera que encajar en un
 * molde inventado antes de conocerla.
 */
data class Programa(
	val titulo: String,
	val inicioMs: Long,
	val finMs: Long,
) {
	val duracionMs: Long get() = (finMs - inicioMs).coerceAtLeast(1)

	fun progreso(ahoraMs: Long): Float =
		((ahoraMs - inicioMs).toFloat() / duracionMs).coerceIn(0f, 1f)

	/**
	 * Redondeado **hacia arriba**: decir «quedan 0 min» de algo que se sigue
	 * viendo es información falsa, y en el último minuto es justo cuando
	 * alguien mira para decidir si se queda.
	 */
	fun restanteMin(ahoraMs: Long): Int {
		val restante = (finMs - ahoraMs).coerceAtLeast(0)
		if (restante == 0L) return 0
		return TimeUnit.MILLISECONDS.toMinutes(restante + 59_999).toInt()
	}
}

data class FranjaEpg(val ahora: Programa?, val luego: Programa?)

/**
 * **Andamiaje: datos inventados para poder juzgar el diseño de la barra.**
 *
 * Desaparece en cuanto haya una fuente real. Está aquí y no en el módulo
 * `livetv` a propósito: el motor no debe aprender a fabricar programación
 * falsa, ni siquiera temporalmente.
 *
 * Los horarios se anclan al reloj de verdad y las franjas duran de 30 a 90
 * minutos según el canal, para que la barra de progreso **avance mientras se
 * mira** y se pueda juzgar cómo se comporta. Una franja fija a mitad de camino
 * se vería bien en una captura y mal en pantalla.
 */
object DummyEpg {

	private val parrillas: Map<String, List<String>> = mapOf(
		"hbohd" to listOf("Los Soprano", "Succession", "The Last of Us", "Chernobyl"),
		"estrellashd" to listOf("Noticiero", "Como dice el dicho", "La Rosa de Guadalupe", "Cine de la tarde"),
		"estrellassd" to listOf("Hoy", "Vecinos", "Noticiero Meridiano", "Teleserie"),
		"discoveryhd" to listOf("Mythbusters", "Deadliest Catch", "Gold Rush", "How It's Made"),
		"beisbol" to listOf("Diablos Rojos vs Sultanes", "Previa del partido", "Resumen de la jornada"),
		"mileniosd" to listOf("Milenio Noticias", "A las Tres", "Milenio Televisión Nocturno"),
	)

	private val genericos = listOf("Programación continua", "En emisión", "Transmisión en vivo")

	/** Minutos que dura cada franja, por canal. Un canal de noticias rota más. */
	private fun franjaMin(channelId: String): Int = when {
		channelId.contains("milenio") -> 30
		channelId.contains("beisbol") -> 90
		channelId.contains("hbo") -> 60
		else -> 45
	}

	fun para(channel: ChannelProfile, ahoraMs: Long): FranjaEpg {
		val titulos = parrillas[channel.id] ?: genericos
		val franja = TimeUnit.MINUTES.toMillis(franjaMin(channel.id).toLong())

		// El índice sale del reloj, así que la parrilla avanza sola y dos
		// canales distintos no enseñan lo mismo a la misma hora.
		val indice = (ahoraMs / franja).toInt()
		val inicio = (ahoraMs / franja) * franja

		return FranjaEpg(
			ahora = Programa(
				titulo = titulos[Math.floorMod(indice, titulos.size)],
				inicioMs = inicio,
				finMs = inicio + franja,
			),
			luego = Programa(
				titulo = titulos[Math.floorMod(indice + 1, titulos.size)],
				inicioMs = inicio + franja,
				finMs = inicio + franja * 2,
			),
		)
	}
}
