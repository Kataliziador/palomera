package mx.kataliziador.palomera.movil.catalogo

import org.jellyfin.livetv.config.ServiceUrl

/**
 * A qué servidor de canales mira este teléfono, y bajo qué clave lo guarda.
 *
 * ## La cascada, y por qué tiene cuatro escalones
 *
 * | | cuándo entra | para qué |
 * |---|---|---|
 * | **1. el ajuste manual** | sólo por `adb` | apuntar a un servidor de pruebas |
 * | **2. el registro** | si contestó y autoriza | mover el servicio de un site sin publicar una versión |
 * | **3. la convención** | siempre que haya servidor | el caso normal: misma máquina, otro puerto |
 * | **4. el de fábrica** | compilaciones de desarrollo | último recurso |
 *
 * **El registro precede a la convención, no la sustituye**
 * (`REGISTRY_SCHEMA.md` §5). Ésa es toda la razón de que el registro exista:
 * que cambiar dónde vive el servicio de canales de un ISP sea editar una fila
 * y no publicar una APK.
 *
 * ## La clave de caché es la otra mitad
 *
 * Se indexa por el **identificador** del servidor y no por su dirección
 * (`CLAUDE.md` §6): cambiar de dominio no tira la caché, y cambiar de site no
 * hereda su lineup.
 *
 * Y **una lista de pruebas no se guarda bajo la clave del site real**. El
 * ajuste manual cambia la *fuente* sin cambiar el *servidor*: sin esta regla,
 * quien apunta un teléfono a su portátil lo deja con el lineup equivocado
 * guardado como si fuera el del ISP, y se va.
 */
fun destinoDeCanales(
	servidor: String?,
	idServidor: String?,
	delRegistro: String?,
	manual: String?,
	deFabrica: String,
): Pair<String?, String> {
	val url = manual
		?: delRegistro
		?: ServiceUrl.configDesdeServidor(servidor)
		?: deFabrica.takeIf { it.isNotBlank() }

	val clave = when {
		manual != null -> "manual"
		// Un nombre que ningún identificador de Jellyfin puede tener, para que
		// dos teléfonos sin sesión no compartan archivo con un site real.
		else -> idServidor ?: "sin-servidor"
	}

	return url to clave
}
