package mx.kataliziador.palomera.movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.kataliziador.palomera.movil.ui.PantallaCapacidades

/**
 * La única actividad de la app, como manda el diseño de una sola actividad con
 * Compose. Hoy enseña la pantalla de capacidades de la fase 0.
 */
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		// **Obligatorio, no decorativo.** Con `targetSdk 36` Android dibuja de
		// borde a borde y **deja de reservar sitio** para la barra de estado y
		// la de navegación: declararlo aquí es lo que hace que el sistema nos
		// dé los insets en vez de recortarnos. Medido el 08-nov en el Xiaomi de
		// pruebas: sin esto, el título de la pantalla se pintaba encima del
		// reloj. Quien escriba las pantallas de la fase 1 tiene que contar con
		// ello en todas.
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent { PantallaCapacidades() }
	}
}
