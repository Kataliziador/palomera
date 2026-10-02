package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.source.ChannelProfile

/**
 * El logo de un canal, con las iniciales como respaldo.
 *
 * **El respaldo no es decorativo: es el estado normal.** El `config.json` puede
 * no traer `logoUrl`, el servicio del site puede no tener los archivos todavía,
 * y la URL puede estar mal escrita. En los tres casos hay que enseñar algo que
 * se lea como diseño y no como imagen rota, porque un hueco vacío en la barra
 * parece un fallo de la app y no del lineup.
 *
 * **Las iniciales no se pintan detrás de la imagen.** Un logo con transparencia
 * las dejaría asomar y se leería como suciedad. Se pintan solo cuando no hay
 * logo o cuando el logo no cargó, y para saber lo segundo hace falta el estado
 * de carga — por eso esto usa Coil directamente en vez del `AsyncImage` común
 * de la app, que no lo expone.
 */
@Composable
fun LogoDeCanal(
	channel: ChannelProfile,
	modifier: Modifier = Modifier,
	tamano: Dp = 84.dp,
	tamanoTexto: TextUnit = 26.sp,
) {
	// Atado a la URL: al zapear, un canal sin logo no debe heredar el fallo del
	// anterior, ni uno con logo quedarse con las iniciales del que falló.
	var fallo by remember(channel.logoUrl) { mutableStateOf(false) }
	val url = channel.logoUrl?.takeIf { it.isNotBlank() }

	Box(
		modifier = modifier
			.clip(RoundedCornerShape(10.dp))
			.background(Color.White.copy(alpha = 0.10f)),
		contentAlignment = Alignment.Center,
	) {
		if (url != null && !fallo) {
			AsyncImage(
				model = url,
				contentDescription = channel.name,
				// `Fit`: los logos de canal vienen en proporciones imposibles de
				// prever y recortarlos cortaría nombres. El relleno evita que
				// un logo cuadrado toque los bordes del recuadro.
				contentScale = ContentScale.Fit,
				onError = { fallo = true },
				modifier = Modifier.fillMaxSize().padding(8.dp),
			)
		} else {
			Text(
				text = inicialesDeCanal(channel.name),
				color = Color.White.copy(alpha = 0.82f),
				fontWeight = FontWeight.Bold,
				fontSize = tamanoTexto,
			)
		}
	}
}

/**
 * «HBO HD» → `HBO`, «Estrellas HD» → `EST`.
 *
 * Las iniciales de cada palabra daban «HH», porque la segunda palabra de medio
 * lineup es la calidad. Se descartan esos sufijos y se toman tres letras del
 * nombre de verdad, que es lo que alguien reconocería.
 */
fun inicialesDeCanal(nombre: String): String {
	val sufijos = setOf("hd", "sd", "4k", "uhd", "tv")
	val palabras = nombre.split(' ').filter { it.isNotBlank() && it.lowercase() !in sufijos }
	val base = palabras.firstOrNull() ?: nombre
	return base.take(3).uppercase()
}
