package mx.kataliziador.palomera.movil

import android.app.Application
import timber.log.Timber

/**
 * El `Application` de la app de teléfono.
 *
 * Nace casi vacío a propósito. Lo que `:app` hace aquí —Koin, ACRA, el SDK de
 * Jellyfin, la sesión— se irá trayendo fase por fase, y traerlo antes de tener
 * dónde usarlo sólo pondría a fallar un arranque que todavía no arranca nada.
 */
class PalomeraMovilApp : Application() {
	override fun onCreate() {
		super.onCreate()
		if (BuildConfig.DEVELOPMENT) Timber.plant(Timber.DebugTree())
	}
}
