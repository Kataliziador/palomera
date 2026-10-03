package org.jellyfin.androidtv.ui.preference.category

import android.os.Build
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.OptionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.screen.LicensesScreen

fun OptionsScreen.aboutCategory() = category {
	setTitle(R.string.pref_about_title)

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
		setContent(R.string.livetv_legal_licenses)
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
