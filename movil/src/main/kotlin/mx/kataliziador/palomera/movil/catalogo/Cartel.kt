package mx.kataliziador.palomera.movil.catalogo

/** Qué imagen de un título se pide. */
enum class Forma {
	/** El cartel vertical, 2:3. Es el que llena la rejilla y los carriles. */
	CARTEL,

	/** El fondo apaisado, 16:9. Sólo en la ficha. */
	FONDO,
}

/**
 * La dirección de la imagen de un título, o `null` si no la hay.
 *
 * ## Lo medido el 10-nov contra el site de los probadores
 *
 * **300 de 300 títulos tienen cartel**, el aspecto que declara el servidor es
 * 0,667 —2:3, el mismo que ya usaba la rejilla— y **las imágenes se sirven sin
 * token**, así que esta URL no lleva credenciales y se puede cachear sin
 * cuidado.
 *
 * ## Dos detalles que no son decorativos
 *
 * **La etiqueta va dentro.** Sin ella la URL de un título nunca cambia, así
 * que el día que el ISP corrige un cartel equivocado el teléfono sigue
 * enseñando el viejo hasta que alguien borre la caché.
 *
 * **Y se pide por el eje que manda en cada forma**: el cartel por alto y el
 * fondo por ancho. Pedir por el otro trae una imagen que luego hay que
 * estirar, y eso no da ningún error — sólo se ve mal.
 */
fun urlDeCartel(
	baseUrl: String,
	itemId: String,
	etiqueta: String?,
	forma: Forma,
	tamanoPx: Int,
): String? {
	// Sin etiqueta no hay imagen. Devolver una URL igualmente daría un 404 por
	// tarjeta: sesenta peticiones perdidas al servidor del ISP por pantalla.
	if (etiqueta.isNullOrBlank()) return null

	val base = baseUrl.trimEnd('/')
	return when (forma) {
		Forma.CARTEL -> "$base/Items/$itemId/Images/Primary?maxHeight=$tamanoPx&tag=$etiqueta"
		Forma.FONDO -> "$base/Items/$itemId/Images/Backdrop?maxWidth=$tamanoPx&tag=$etiqueta"
	}
}
