package org.jellyfin.livetv.epg

import java.util.concurrent.TimeUnit

/**
 * Qué se está emitiendo y qué viene después.
 *
 * El modelo es deliberadamente pobre: título, inicio y fin. Es lo único que la
 * barra necesita, y atarse a más haría que cualquier fuente —XMLTV hoy, la API
 * de Flussonic o lo que venga— tuviera que encajar en un molde inventado antes
 * de conocerla.
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
 * La programación que esta caja tiene en memoria, por canal.
 *
 * ## Por qué es inmutable y se reemplaza entera
 *
 * Una guía a medias es peor que ninguna: la barra enseñaría el programa de
 * antes de ayer con la convicción de siempre. Al refrescar se construye una
 * [Guia] nueva y se sustituye la anterior de golpe, así que nunca hay un
 * instante en el que una mitad sea de hoy y la otra de la semana pasada.
 *
 * ## Qué no es
 *
 * No es el XMLTV entero. Es la ventana que [XmltvParser] decidió conservar:
 * unas horas hacia delante y un margen hacia atrás, acotada por canal. El
 * fichero completo sigue en la caché, y volver a leerlo es lo que mueve la
 * ventana cuando pasa el tiempo.
 */
class Guia(
	private val porCanal: Map<String, List<Programa>>,
	/** Cuándo se leyó el fichero del que salió. Para saber si hay que releerlo. */
	val interpretadaMs: Long,
) {

	val canales: Int get() = porCanal.size

	val programas: Int get() = porCanal.values.sumOf { it.size }

	/**
	 * Qué dan ahora y qué dan después en un canal, o `null` si de ese canal no
	 * se sabe nada.
	 *
	 * **`null` y «no hay nada ahora» son cosas distintas**, y la barra las
	 * trata distinto: de lo primero no se dice nada, de lo segundo se puede
	 * decir que el hueco existe. Por eso [FranjaEpg] admite `ahora` nulo con
	 * `luego` lleno — un canal que empieza a emitir dentro de media hora.
	 */
	fun franja(canalId: String, ahoraMs: Long): FranjaEpg? {
		val lista = porCanal[canalId] ?: return null

		// La lista viene ordenada por hora de inicio, así que basta recorrerla:
		// con 64 entradas como mucho, buscar en binario sería más código para
		// un ahorro que nadie puede medir.
		val ahora = lista.firstOrNull { ahoraMs >= it.inicioMs && ahoraMs < it.finMs }
		// Lo siguiente empieza cuando acaba lo de ahora; si no hay nada ahora
		// —un hueco en la parrilla—, lo siguiente es lo primero que empiece.
		val desde = ahora?.finMs ?: ahoraMs
		val luego = lista.firstOrNull { it.inicioMs >= desde }

		if (ahora == null && luego == null) return null
		return FranjaEpg(ahora = ahora, luego = luego)
	}

	/**
	 * Si lo que hay en memoria sigue cubriendo el presente.
	 *
	 * Se pregunta antes de pintar, no por reloj: lo que importa no es cuánto
	 * hace que se leyó el fichero, sino si la ventana que se guardó todavía
	 * contiene la hora actual. Una caja que se queda encendida toda la noche
	 * sale de la ventana aunque la guía del servidor no haya cambiado.
	 */
	fun cubre(ahoraMs: Long): Boolean =
		porCanal.values.any { lista -> lista.any { it.finMs > ahoraMs } }

	companion object {
		val VACIA = Guia(emptyMap(), 0L)
	}
}
