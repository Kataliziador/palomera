package org.jellyfin.androidtv.ui.preference.category

import android.os.Build
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.OptionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.livetv.config.RegistryRepository
import org.jellyfin.androidtv.ui.preference.screen.LicensesScreen

fun OptionsScreen.aboutCategory() = category {
	setTitle(R.string.pref_about_title)

	// Se lee una vez, en el ámbito que tiene `context`: el de `link` no lo
	// expone. Solo toca `SharedPreferences`, así que no hace falta sacarlo del
	// hilo principal.
	val identificador = RegistryRepository(context).installId

	// El aviso de no afiliación y la licencia del fork.
	//
	// **No es decoración: es obligación.** El fork es GPL-2.0 y se distribuye
	// con marca de otro, así que decir de dónde viene y que no lo respalda el
	// proyecto original es lo que hace legal el envío a Play (`CLAUDE.md` §8).
	// Va en Información porque es donde alguien lo busca.
	link {
		setTitle(R.string.livetv_legal_title)
		setContent(R.string.livetv_legal_affiliation)
		icon = R.drawable.ic_tv
	}

	link {
		setTitle(R.string.livetv_legal_licenses_title)
		// La dirección se interpola aquí y no se escribe en la cadena: vive
		// en `BuildConfig.CLIENT_REPOSITORY`, que es el mismo valor que
		// viaja en la cabecera de los informes. Una oferta de código y dos
		// informes que apunten a sitios distintos serían peor que ninguno.
		content = context.getString(R.string.livetv_legal_licenses, BuildConfig.CLIENT_REPOSITORY)
		icon = R.drawable.ic_guide
	}

	link {
		// Versión, para soporte. **Sin la marca del proyecto original en el
		// título**: la app no usa el nombre ni el logo de Jellyfin (§8). El
		// identificador técnico del fork sí se conserva, que es lo que hace
		// falta para diagnosticar y lo que exige la GPL-2.0 reconocer.
		setTitle(R.string.livetv_about_version)
		content = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"
		icon = R.drawable.ic_tv
	}

	link {
		// **El identificador de instalación, a la vista.**
		//
		// El aviso de privacidad dice que el abonado puede pedir acceso o
		// borrado de sus datos indicándolo, y es lo único que los identifica:
		// no sabemos su nombre. Sin enseñarlo en algún sitio, ese derecho no
		// se puede ejercer y el aviso sería papel mojado.
		//
		// Va aquí y no en el menú de Diagnóstico porque Información es donde
		// ya viven los avisos legales, y porque mandar a un abonado a un menú
		// lleno de interruptores de degradación es pedirle que rompa algo.
		setTitle(R.string.livetv_about_install_id)
		content = identificador
		icon = R.drawable.ic_guide
	}

	link {
		setTitle(R.string.pref_device_model)
		content = "${Build.MANUFACTURER} ${Build.MODEL}"
		icon = R.drawable.ic_tv
	}

	link {
		// La descripción de upstream nombra a Jellyfin; §8 lo prohíbe en la UI.
		setTitle(R.string.licenses_link)
		setContent(R.string.livetv_licenses_description)
		icon = R.drawable.ic_guide
		withFragment<LicensesScreen>()
	}
}
