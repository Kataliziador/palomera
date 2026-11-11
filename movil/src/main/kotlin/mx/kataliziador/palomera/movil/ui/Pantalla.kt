package mx.kataliziador.palomera.movil.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Pone el teléfono en modo cine mientras haya vídeo, y lo deja como estaba al
 * salir.
 *
 * ## Por qué el resto de la app va en vertical y esto no
 *
 * Las pantallas de catálogo se pensaron en vertical, que es como se sostiene
 * un teléfono para elegir qué ver, y al girarlas la barra inferior se come el
 * primer carril. **Pero el vídeo es lo contrario**: un canal 16:9 dentro de
 * una pantalla 9:20 ocupa menos de un tercio del alto y deja dos franjas
 * negras enormes. Nadie ve una película así.
 *
 * El diseño ya lo decía —«el apaisado se queda para el reproductor, que lo
 * pedirá para sí mismo cuando exista»— y hasta el 11-nov no se había hecho.
 *
 * ## Dos cosas, no una
 *
 * **Girar** y **esconder las barras del sistema**. Sin lo segundo, el vídeo
 * apaisado sigue perdiendo una franja por el reloj y otra por los botones de
 * navegación, que es justo el ancho que se acaba de ganar.
 *
 * Las barras vuelven con un deslizamiento, que es lo que todo el mundo espera
 * en un reproductor, y **no se bloquea la rotación**: se pide apaisado, pero
 * si el abonado gira el teléfono a vertical, se le deja. Forzar una
 * orientación contra el gesto de alguien es de las cosas que más molestan de
 * una app de vídeo.
 */
@Composable
fun ModoCine() {
	val contexto = LocalContext.current
	val actividad = contexto as? Activity ?: return

	DisposableEffect(Unit) {
		val ventana = actividad.window
		val controles = WindowInsetsControllerCompat(ventana, ventana.decorView)
		val orientacionAnterior = actividad.requestedOrientation

		actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
		WindowCompat.setDecorFitsSystemWindows(ventana, false)
		controles.hide(WindowInsetsCompat.Type.systemBars())
		controles.systemBarsBehavior =
			WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

		onDispose {
			// **Se deja como estaba, no como creemos que estaba.** Si algún
			// día otra pantalla pide otra orientación, restaurar un valor fijo
			// se la llevaría por delante.
			actividad.requestedOrientation = orientacionAnterior
			controles.show(WindowInsetsCompat.Type.systemBars())
			WindowCompat.setDecorFitsSystemWindows(ventana, false)
		}
	}
}
