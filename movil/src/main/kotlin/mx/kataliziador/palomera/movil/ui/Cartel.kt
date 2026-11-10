package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * El cartel de un título.
 *
 * **Siempre pinta algo.** Debajo de la imagen hay un bloque de color derivado
 * del id, que es lo que se ve mientras descarga, lo que queda si el título no
 * tiene cartel y lo que queda si el servidor no contesta. Una rejilla con
 * huecos blancos parece rota; una con bloques de color parece una rejilla.
 *
 * El color sale del id y no es aleatorio: el mismo título tiene siempre el
 * mismo, así que al volver a una pantalla no cambia de sitio ni de tono.
 */
@Composable
fun Cartel(
	url: String?,
	id: String,
	descripcion: String,
	modifier: Modifier = Modifier,
	recorte: ContentScale = ContentScale.Crop,
) {
	Box(modifier.background(tonoDeCartel(id))) {
		if (url != null) {
			AsyncImage(
				model = url,
				contentDescription = descripcion,
				contentScale = recorte,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

/** Para los sitios donde el tono ya se calculó fuera. */
@Composable
fun Cartel(
	url: String?,
	tono: Color,
	descripcion: String,
	modifier: Modifier = Modifier,
	recorte: ContentScale = ContentScale.Crop,
) {
	Box(modifier.background(tono)) {
		if (url != null) {
			AsyncImage(
				model = url,
				contentDescription = descripcion,
				contentScale = recorte,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}
