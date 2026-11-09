package mx.kataliziador.palomera.movil.arranque

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.content.getSystemService

/**
 * Lee del sistema las tres señales que [decidirAparato] necesita.
 *
 * Es la única parte de la detección que toca Android, y por eso está separada:
 * la regla —que es donde se puede uno equivocar— vive en [decidirAparato], es
 * pura y tiene pruebas.
 *
 * El modo de interfaz cae a `UI_MODE_TYPE_UNDEFINED` si el servicio no está,
 * y eso **no** es un voto por la televisión: la regla lo trata como lo que es,
 * un aparato que no sabe decir qué es.
 */
fun Context.leerAparato(forzadoPorAjustes: Aparato? = null): Aparato = decidirAparato(
	modoDeInterfaz = getSystemService<UiModeManager>()?.currentModeType
		?: Configuration.UI_MODE_TYPE_UNDEFINED,
	declaraLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
	tienePantallaTactil = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
	forzadoPorAjustes = forzadoPorAjustes,
)
