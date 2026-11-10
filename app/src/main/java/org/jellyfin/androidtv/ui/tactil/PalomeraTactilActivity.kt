package org.jellyfin.androidtv.ui.tactil

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.kataliziador.palomera.movil.Identidad
import mx.kataliziador.palomera.movil.PalomeraTactil
import mx.kataliziador.palomera.movil.arranque.Aparato
import mx.kataliziador.palomera.movil.arranque.leerAparato
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.ui.startup.StartupActivity

/**
 * La puerta de entrada **en un teléfono**.
 *
 * ## Una sola APK, dos interfaces
 *
 * Hasta el 10-nov eran dos apps con dos `applicationId`, o sea dos fichas en
 * Play. Se unificaron antes del primer envío del móvil porque Play fija el
 * nombre de paquete en esa subida: publicar `…​.movil` una vez habría cerrado
 * la puerta para siempre.
 *
 * **Quién abre qué lo decide el lanzador, no el código.** Esta actividad
 * declara `LAUNCHER` y [StartupActivity] declara `LEANBACK_LAUNCHER`, así que
 * en una caja aparece la de Leanback y en un teléfono ésta. Sin ramas, sin
 * estado, y funcionando incluso si algo de abajo falla.
 *
 * ## Y aun así verifica
 *
 * Porque hay cajas que ignoran la categoría de Leanback y enseñan todo lo que
 * tenga `LAUNCHER`. Si esta actividad se abre en un aparato de televisión,
 * **cede** a la de siempre en vez de enseñar una interfaz de dedo a quien
 * tiene un mando. Es el mismo principio de `CLAUDE.md` §6: el servidor
 * sugiere, la caja verifica.
 *
 * La decisión vive en `decidirAparato`, que es pura y está probada desde el
 * 08-nov con sus ocho casos.
 */
class PalomeraTactilActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		// **Obligatorio, no decorativo.** Con `targetSdk 36` Android dibuja de
		// borde a borde y deja de reservar sitio para la barra de estado y la
		// de navegación; declararlo es lo que hace que el sistema nos dé los
		// insets en vez de recortarnos.
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)

		if (leerAparato() == Aparato.TELEVISOR) {
			// Un mando no sabe qué hacer con esto. Se cede y no se vuelve.
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return
		}

		// **El vertical se fija aquí y no en el manifiesto**, y es la otra
		// mitad de la unificación: un `screenOrientation="portrait"` declarado
		// implica `android.hardware.screen.portrait`, que **filtra el catálogo
		// de Play** y dejaría la ficha invisible para las cajas de ISP — el
		// mismo fallo que §4.7 cuenta que ya ocurrió con `ACCESS_WIFI_STATE`.
		// Puesto desde el código, el efecto es el mismo y no se declara nada.
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

		setContent {
			PalomeraTactil(
				// Los cuatro salen de `:app`, que es la única que se publica y
				// por tanto la única que tiene versión y nombre de paquete.
				identidad = Identidad(
					cliente = BuildConfig.CLIENT_NAME,
					repositorio = BuildConfig.CLIENT_REPOSITORY,
					version = BuildConfig.VERSION_NAME,
					registroUrl = BuildConfig.REGISTRY_URL,
				),
				extra = { clave -> intent?.getStringExtra(clave) },
			)
		}
	}
}
