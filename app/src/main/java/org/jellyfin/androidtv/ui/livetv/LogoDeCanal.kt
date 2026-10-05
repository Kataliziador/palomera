package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.config.Brand
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
	/**
	 * El recuadro propio del logo.
	 *
	 * Tiene sentido suelto —en la barra del canal, donde flota sobre el vídeo—
	 * y **estorba dentro de una tarjeta**, que ya tiene su fondo: se ve una
	 * caja dentro de otra. Ahí se pasa transparente.
	 */
	fondo: Color = Color.White.copy(alpha = 0.10f),
) {
	// Atado a la URL: al zapear, un canal sin logo no debe heredar el fallo del
	// anterior, ni uno con logo quedarse con las iniciales del que falló.
	var fallo by remember(channel.logoUrl) { mutableStateOf(false) }
	val url = channel.logoUrl?.takeIf { it.isNotBlank() }

	Box(
		modifier = modifier
			.clip(RoundedCornerShape(10.dp))
			.background(fondo),
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

/**
 * La marca del proveedor en la cabecera de la sección.
 *
 * **El logo sustituye al nombre, no lo acompaña.** Un logo con su nombre escrito
 * al lado es lo que hace una cabecera de ISP; aquí la cabecera es de la sección,
 * y el nombre solo aparece cuando no hay logo que lo diga.
 *
 * Si el logo no carga, cae al nombre. Y si no hay marca, el nombre es el de la
 * marca neutra, que está diseñada para eso: nunca un hueco.
 */
@Composable
fun LogoDeMarca(brand: Brand, modifier: Modifier = Modifier) {
	var fallo by remember(brand.logoUrl) { mutableStateOf(false) }
	val url = brand.logoUrl?.takeIf { it.isNotBlank() }

	if (url != null && !fallo) {
		AsyncImage(
			model = url,
			contentDescription = brand.displayName,
			contentScale = ContentScale.Fit,
			onError = { fallo = true },
			// Cargador propio, no el global: el logo puede venir del registro
			// por TLS y una caja anterior a Android 7.1.1 no trae esa raíz.
			// Ver [LogoLoader] para por qué no se amplía el global.
			imageLoader = LogoLoader.of(LocalContext.current),
			// Alto fijo y ancho libre: los logos de marca son apaisados y
			// encajarlos en un cuadrado los deja diminutos.
			modifier = modifier.height(28.dp),
		)
	} else {
		Text(
			text = brand.displayName,
			color = Color(brand.accent),
			fontWeight = FontWeight.Bold,
			fontSize = 20.sp,
			modifier = modifier,
		)
	}
}

/**
 * Un estado de la cabecera, en una pastilla discreta.
 *
 * Son avisos que importan pero que nadie viene a leer: «la lista es vieja»,
 * «esta caja está degradada». Como texto suelto competían con la marca; como
 * pastillas se leen cuando se buscan y desaparecen del camino cuando no.
 */
@Composable
fun Pastilla(texto: String, color: Color, modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.clip(ShapeDefaults.Small)
			.background(color.copy(alpha = 0.14f))
			.padding(horizontal = 10.dp, vertical = 5.dp),
	) {
		Text(text = texto, color = color, fontSize = 12.sp)
	}
}
