package mx.kataliziador.palomera.movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import mx.kataliziador.palomera.movil.ui.PantallaCapacidades

/**
 * La única actividad de la app, como manda el diseño de una sola actividad con
 * Compose. Hoy enseña la pantalla de capacidades de la fase 0.
 */
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent { PantallaCapacidades() }
	}
}
