package mx.kataliziador.palomera.movil.catalogo

/**
 * Algo que el abonado dejó a medias.
 *
 * @param id el de Jellyfin, que es por **copia** y no por título: el mismo
 *   título en dos bibliotecas son dos ids distintos.
 * @param porcentaje lo que lleva visto, de 0 a 100.
 */
data class EnProgreso(
	val id: String,
	val titulo: String,
	val porcentaje: Int,
	/** Lo que falta para el final. Es lo que se enseña; ver [tiempoRestante]. */
	val minutosRestantes: Int = 0,
)

/**
 * Lo que queda, dicho como lo piensa una persona.
 *
 * **La primera versión enseñaba «69 % por ver».** Es cierto y no sirve: nadie
 * decide si le da tiempo a terminar una película en porcentajes. Se vio al
 * mirarlo en el teléfono, no en la maqueta —que ya decía «Quedan 1 h 38 min»—.
 *
 * «Casi terminada» en vez de «Quedan 0 min» porque lo segundo se lee como un
 * fallo de la app, y cubre de paso cualquier duración imposible que llegue del
 * servidor: perder una frase es mejor que perder el renglón entero.
 */
fun tiempoRestante(minutos: Int): String = when {
	minutos <= 0 -> "Casi terminada"
	minutos < 60 -> "Quedan $minutos min"
	minutos % 60 == 0 -> "Quedan ${minutos / 60} h"
	else -> "Quedan ${minutos / 60} h ${minutos % 60} min"
}

/**
 * La misma lista sin el mismo título dos veces.
 *
 * ## Por qué hace falta
 *
 * Medido el 09-nov contra el servidor: `Items/Resume` devolvió **11 elementos
 * para 8 títulos**. No es un error suyo — el mismo título vive en varias
 * bibliotecas («Películas» y «Top Películas Netflix»), y cada copia es un
 * elemento con su id y su progreso. En el catálogo general «Top Gun: Maverick»
 * aparece **tres** veces.
 *
 * En una tele con doce carriles no se nota. En la primera pantalla de un
 * teléfono, donde caben tres tarjetas, dos iguales **parecen un error de la
 * app**.
 *
 * ## Las dos reglas, y por qué cada una
 *
 * **Se queda el más avanzado.** Las copias llevan progreso por separado, así
 * que el abonado vio una de ellas; la otra está atrás, y reanudar por ahí le
 * haría repetir lo que ya vio.
 *
 * **Y se queda donde apareció el primero.** Si el superviviente saltara al
 * final, la fila se reordenaría sola entre dos aperturas de la app y el
 * abonado perdería de vista lo que estaba viendo.
 */
fun sinDuplicados(items: List<EnProgreso>): List<EnProgreso> {
	// El título puede venir de dos catalogaciones a mano, así que se compara
	// sin espacios de sobra ni diferencias de caja. Lo que se **enseña** sigue
	// siendo el texto tal cual llegó.
	fun clave(it: EnProgreso) = it.titulo.trim().lowercase()

	val mejorPorTitulo = items
		.groupBy(::clave)
		.mapValues { (_, copias) -> copias.maxBy { it.porcentaje } }

	val yaPuestos = mutableSetOf<String>()
	return items.mapNotNull { item ->
		val k = clave(item)
		if (yaPuestos.add(k)) mejorPorTitulo.getValue(k) else null
	}
}
