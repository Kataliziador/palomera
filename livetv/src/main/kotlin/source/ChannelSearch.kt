package org.jellyfin.livetv.source

import java.text.Normalizer

/**
 * Busca canales por lo que el abonado teclea.
 *
 * **Está aquí y no en la pantalla a propósito:** es la única pieza de la
 * búsqueda que se puede equivocar en silencio —un orden mal elegido deja el
 * canal correcto en tercer lugar y nadie lo nota— y aquí se prueba sin caja.
 *
 * Tres decisiones que vienen de cómo se usa esto en un sofá:
 *
 * - **Se ignoran acentos, mayúsculas y signos.** El teclado de una caja de TV
 *   no pone tildes con comodidad, y «estrellas» tiene que encontrar «Estrellas
 *   HD». Quien busca «TNT» no debería tener que saber si el operador lo
 *   escribió «TNT.» o «T.N.T».
 * - **Los alias pesan igual que el nombre.** Existen justamente porque el
 *   abonado llama al canal por otra cosa (`CONFIG_SCHEMA.md`): «canal 2» tiene
 *   que encontrar «Estrellas HD» o el campo no sirve de nada.
 * - **Los dígitos buscan por número.** Teclear «101» en la búsqueda es pedir
 *   el canal 101, no los canales que llevan «101» en el nombre. El marcado
 *   directo ya existe, pero quien abrió la búsqueda está en la búsqueda.
 *
 * Lo que **no** hace: corregir erratas ni buscar por parecido. Un resultado
 * aproximado en una lista de 120 canales esconde el que sí casaba.
 */
object ChannelSearch {

	/**
	 * Los canales que casan con [consulta], del más probable al menos.
	 *
	 * Una consulta en blanco devuelve la lista vacía, no todos: «todos» ya es
	 * la guía, y enseñar el lineup entero como «resultados» haría parecer que
	 * la búsqueda no hizo nada.
	 */
	fun buscar(canales: List<ChannelProfile>, consulta: String): List<ChannelProfile> {
		val q = plegar(consulta)
		if (q.isEmpty()) return emptyList()

		return canales
			.mapNotNull { canal -> puntuar(canal, q)?.let { canal to it } }
			// `sortedBy` es estable, así que a igual puntuación se conserva el
			// orden de la guía — número primero. Es el desempate que el abonado
			// espera sin saber que lo espera.
			.sortedBy { (_, punto) -> punto }
			.map { (canal, _) -> canal }
	}

	/**
	 * Cuánto casa un canal: **menos es mejor**, y `null` es no casar.
	 *
	 * La escala la decide una pregunta: si alguien teclea «dis», ¿qué espera
	 * arriba? El canal que **empieza** por «dis», no el que lo lleva en medio.
	 *
	 * **Y lo exacto gana a lo que empieza igual, aunque sea un alias.** Medido
	 * en la caja con un lineup real: quien teclea «canal 2» veía primero
	 * «Canal 22», que empieza por esas letras, en vez de «Estrellas HD», cuyo
	 * alias **es** «canal 2». Si alguien escribió el nombre entero de algo, es
	 * eso lo que quiere.
	 */
	private fun puntuar(canal: ChannelProfile, q: String): Int? {
		if (q.all { it.isDigit() } && canal.number?.toString() == q) return 0

		val nombre = plegar(canal.name)
		val alias = canal.aliases.map(::plegar)

		if (nombre == q) return 1
		if (alias.any { it == q }) return 2
		if (nombre.startsWith(q)) return 3
		if (alias.any { it.startsWith(q) }) return 4
		if (nombre.contains(q)) return 5
		if (alias.any { it.contains(q) }) return 6

		return null
	}

	/**
	 * Deja el texto en minúsculas sin acentos ni nada que no sea letra o
	 * número.
	 *
	 * Quitar los signos y no solo normalizarlos es lo que hace que «TNT»
	 * encuentre «T.N.T.» y que «canal 5» encuentre «Canal-5».
	 */
	private fun plegar(texto: String): String =
		Normalizer.normalize(texto, Normalizer.Form.NFD)
			.filter { it.isLetterOrDigit() }
			.lowercase()
}
