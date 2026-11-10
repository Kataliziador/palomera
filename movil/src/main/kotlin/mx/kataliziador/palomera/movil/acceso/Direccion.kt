package mx.kataliziador.palomera.movil.acceso

/**
 * Convierte lo que el abonado escribió en una dirección que funciona, o `null`.
 *
 * ## Por qué perdona tanto
 *
 * Es la **ruta 3** del selector: la que usa el revisor de Google y la única
 * que funciona sin llegar a internet. Lo que la gente teclea de verdad no
 * lleva esquema ni puerto —`192.168.1.10`, que es lo que les dictaron por
 * teléfono—, y rechazarlo con «dirección no válida» sería culpar al abonado de
 * que falte algo que nosotros sabemos poner.
 *
 * Perdona cuatro cosas y **no inventa una quinta**: devuelve `null` en cuanto
 * lo escrito no puede ser una dirección, para que la pantalla lo diga **antes**
 * de pedir la contraseña.
 */
fun normalizarDireccion(escrito: String): String? {
	val limpio = escrito.trim().trimEnd('/')
	if (limpio.isBlank()) return null
	if (limpio.contains(' ')) return null

	val conEsquema = when {
		limpio.startsWith("http://") || limpio.startsWith("https://") -> limpio
		// Un esquema que no es ninguno de los dos no se arregla añadiéndole otro.
		limpio.contains("://") -> return null
		// **http y no https**: el Jellyfin de un site vive en la LAN del ISP y
		// casi nunca tiene certificado. Asumir TLS dejaría esta ruta
		// inservible justo para quien la necesita.
		else -> "http://$limpio"
	}

	val sinEsquema = conEsquema.substringAfter("://")
	val autoridad = sinEsquema.substringBefore('/')
	if (autoridad.isBlank()) return null

	// Un anfitrión tiene que parecerlo: letras, cifras, puntos, guiones, y el
	// puerto detrás de dos puntos. Nada más.
	if (!AUTORIDAD.matches(autoridad)) return null

	val tienePuerto = autoridad.substringAfterLast(':', "").toIntOrNull() != null
	val esSeguro = conEsquema.startsWith("https://")

	// **Un https sin puerto se deja en el 443.** Un site con TLS está detrás
	// de un proxy; meterle el 8096 lo mandaría a un puerto cerrado.
	return if (tienePuerto || esSeguro) conEsquema else "$conEsquema:$PUERTO_JELLYFIN"
}

/** El puerto de fábrica de Jellyfin. */
private const val PUERTO_JELLYFIN = 8096

private val AUTORIDAD = Regex("""[A-Za-z0-9.\-]+(:\d{1,5})?""")
