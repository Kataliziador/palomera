package mx.kataliziador.palomera.movil.catalogo

/**
 * Cómo se presenta la app al Jellyfin del ISP.
 *
 * ## Qué pasa si esto está mal
 *
 * Jellyfin guarda **cliente, aparato e identificador de instalación** por
 * sesión, y es lo que el panel de abonados le enseña al operador cuando
 * confirma un alta (`CLAUDE.md` §8). Sin esos campos no hay sesión a la que
 * atar una reproducción, así que el latido de [tocaLatido] se envía, contesta
 * 200 y **no guarda nada**.
 *
 * El nombre es **«Palomera»** por §8: identifica el aparato, no al proveedor.
 * Las ranuras de marca visten la app por dentro; quien pregunta «qué cliente es
 * esta sesión» necesita saber que es la nuestra. No se traduce.
 */

/**
 * La cabecera `Authorization` del esquema de Jellyfin.
 *
 * **Las comillas se escapan.** Hay fabricantes que las meten en `Build.MODEL`,
 * y una comilla suelta parte la cabecera por la mitad: el servidor contesta
 * 200 y no asocia nada, que es el fallo más caro porque parece un acierto.
 */
fun cabeceraDeAutorizacion(
	cliente: String,
	aparato: String,
	idDeInstalacion: String,
	version: String,
	token: String,
): String {
	fun limpio(valor: String) = valor.replace("\"", "").replace("\n", " ").trim()

	return "MediaBrowser " + listOf(
		"Client" to cliente,
		"Device" to aparato,
		"DeviceId" to idDeInstalacion,
		"Version" to version,
		"Token" to token,
	).joinToString(", ") { (clave, valor) -> """$clave="${limpio(valor)}"""" }
}

/**
 * El nombre con el que el operador va a reconocer el aparato en su panel.
 *
 * Muchos fabricantes ya meten su nombre en el modelo y otros no, así que
 * concatenar sin mirar da «Xiaomi Xiaomi 2511FPC34G». Y un nombre vacío deja al
 * operador ante una fila en blanco justo cuando confirma un alta, así que hay
 * un último recurso.
 */
fun nombreDeAparato(fabricante: String, modelo: String): String {
	val fab = fabricante.trim()
	val mod = modelo.trim()

	return when {
		mod.isBlank() && fab.isBlank() -> "Teléfono"
		mod.isBlank() -> fab
		fab.isBlank() -> mod
		mod.startsWith(fab, ignoreCase = true) -> mod
		else -> "$fab $mod"
	}
}
