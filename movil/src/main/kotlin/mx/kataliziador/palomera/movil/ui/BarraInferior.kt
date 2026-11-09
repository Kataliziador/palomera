package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/** Las cuatro secciones de la app. */
enum class Seccion(val etiqueta: String) {
	INICIO("Inicio"),
	CATALOGO("Catálogo"),
	EN_VIVO("En vivo"),
	BUSCAR("Buscar"),
}

/**
 * La barra inferior, compartida por las cuatro secciones.
 *
 * Sustituye a la cruceta de la caja. Cada destino ocupa un cuarto del ancho,
 * que a 390 dp son ~97: muy por encima de los 44 dp de blanco mínimo, y eso es
 * a propósito — es lo que más se toca de toda la app.
 */
@Composable
fun BarraInferior(activa: Seccion, acento: Color, alCambiar: (Seccion) -> Unit) {
	Row(
		Modifier
			.fillMaxWidth()
			.height(64.dp)
			.background(Paleta.fondo),
	) {
		for (seccion in Seccion.entries) {
			val esActiva = seccion == activa
			Column(
				Modifier
					.weight(1f)
					.fillMaxWidth()
					.clickable { alCambiar(seccion) },
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Box(
					Modifier
						.size(20.dp)
						.clip(RoundedCornerShape(5.dp))
						.background(if (esActiva) acento else Paleta.elevado),
				)
				Spacer(Modifier.height(5.dp))
				BasicText(
					seccion.etiqueta,
					style = Tipografia.etiqueta.copy(
						color = if (esActiva) Paleta.tinta else Paleta.tintaSutil,
					),
				)
			}
		}
	}
}
