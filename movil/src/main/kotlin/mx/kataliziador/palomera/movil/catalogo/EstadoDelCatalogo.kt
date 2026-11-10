package mx.kataliziador.palomera.movil.catalogo

/**
 * Qué se le enseña al abonado cuando la pantalla no tiene contenido.
 *
 * **Medido el 10-nov**: con el token revocado la app enseñaba negro entre la
 * cabecera y la barra. El registro lo sabía —`codigo=401`— y el abonado no, y
 * lo único que puede hacer con una pantalla muda es llamar a soporte.
 *
 * Es la misma tabla que la de la puerta, y está aquí porque **entrar y seguir
 * dentro son momentos distintos**: la de la puerta no se aplicaba después de
 * entrar, que es justo donde falló.
 */
enum class EstadoDelCatalogo {
	/** Hay contenido. No se enseña nada. */
	Bien,

	/** El token dejó de valer. Se vuelve a activar el teléfono. */
	SesionCaducada,

	/** El contrato está suspendido. **No** lleva a reactivar: lleva a pagar. */
	Suspendido,

	/** Sin red o el site caído. No se acusa a nadie y se puede reintentar. */
	SinConexion,

	/** El servidor contestó bien y no hay nada. Pasa en un site recién montado. */
	Vacio,
}

/**
 * [ultimoCodigo] es el código HTTP de la última petición, o `null` si no hubo
 * ni respuesta — que es un caso propio y no un error más.
 */
fun estadoDelCatalogo(hayContenido: Boolean, ultimoCodigo: Int?): EstadoDelCatalogo = when {
	// Un código de error suelto no tapa un catálogo que sí llegó: puede ser
	// una petición secundaria que falló mientras el resto funcionaba.
	hayContenido -> EstadoDelCatalogo.Bien
	ultimoCodigo == 401 -> EstadoDelCatalogo.SesionCaducada
	ultimoCodigo == 403 -> EstadoDelCatalogo.Suspendido
	ultimoCodigo != null && ultimoCodigo in 200..299 -> EstadoDelCatalogo.Vacio
	else -> EstadoDelCatalogo.SinConexion
}
