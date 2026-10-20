package org.jellyfin.androidtv.ui.livetv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Una tarjeta de canal.
 *
 * **El logo manda, no el nombre.** A tres metros, con 120 canales y el pulgar
 * en una flecha, un canal se reconoce por su imagen mucho antes que leyendo
 * «Estrellas HD». Por eso la tarjeta es un mosaico con el logo y el nombre va
 * **debajo**, igual que las filas de Inicio: así la sección se lee como el
 * resto de la app y no como una lista aparte.
 *
 * **Y por eso dejó de ser un `Button`.** `ButtonDefaults.Shape` es
 * `CircleShape`: en este sistema de diseño una píldora significa «acción», y
 * un canal no es una acción, es contenido. Las tarjetas salían ovaladas
 * precisamente porque se estaba usando el componente equivocado.
 */
@Composable
fun LiveTvChannelCard(
	channel: ChannelProfile,
	favorite: Boolean,
	/**
	 * Lo que lleva emitido el programa en curso, 0..1, o `null` si de este canal
	 * no hay guía.
	 *
	 * **Va como línea en el borde y no como texto.** La tarjeta mide 168 dp: a
	 * tres metros un título real se corta a media palabra, y 120 títulos
	 * cortados son ruido. Una línea dice «va por la mitad» de un vistazo y no
	 * compite con el logo, que es lo que identifica el canal. El título entero
	 * se dice una sola vez, arriba de la guía, para el canal que tiene el foco.
	 */
	progreso: Float? = null,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactions = remember { MutableInteractionSource() }
	val enfocada by interactions.collectIsFocusedAsState()

	// El crecimiento es pequeño a propósito: con las tarjetas pegadas, un salto
	// grande las solapa y el foco se lee peor, no mejor. Quien dice dónde está
	// el foco es el borde; la escala solo lo acompaña.
	val escala by animateFloatAsState(if (enfocada) 1.06f else 1f, label = "focoCanal")

	Column(modifier = modifier.width(ANCHO)) {
		Box(
			modifier = Modifier
				.scale(escala)
				.fillMaxWidth()
				.height(ALTO)
				.clip(ShapeDefaults.Medium)
				.background(if (enfocada) FONDO_ENFOCADO else FONDO)
				.border(
					BorderStroke(if (enfocada) 2.dp else 1.dp, if (enfocada) Color.White else BORDE),
					ShapeDefaults.Medium,
				)
				// `combinedClickable` trae el foco consigo, así que no hace
				// falta `focusable` aparte.
				//
				// **La pulsación larga va aquí de verdad.** La tarjeta anterior
				// recibía `onLongClick` y nunca se lo pasaba al botón: marcar
				// un favorito manteniendo pulsado no hacía nada, y nadie lo
				// notó porque no hay forma de distinguir «no funciona» de «no
				// lo mantuve bastante».
				.combinedClickable(
					interactionSource = interactions,
					indication = null,
					onClick = onClick,
					onLongClick = onLongClick,
				),
		) {
			// El logo ocupa el mosaico entero menos el aire de las esquinas,
			// donde van número e insignias.
			LogoDeCanal(
				channel = channel,
				modifier = Modifier.fillMaxWidth().height(ALTO).padding(horizontal = 16.dp, vertical = 20.dp),
				tamanoTexto = 22.sp,
				fondo = Color.Transparent,
			)

			if (channel.number != null) {
				// Monoespaciada: con 120 canales la columna de números queda
				// alineada entre tarjetas y se recorre de un vistazo.
				Etiqueta(
					text = channel.number.toString(),
					modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
					fontFamily = FontFamily.Monospace,
				)
			}

			if (favorite) {
				Etiqueta(
					text = "★",
					modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
					color = Color(0xFFFFC74A),
				)
			}

			if (channel.hd && !channel.unavailable) {
				Etiqueta(
					text = "HD",
					modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
				)
			}

			if (channel.unavailable) {
				// Un canal que está pero no se puede ver se dice **aquí**, no
				// al entrar: enterarse después de pulsar es enterarse tarde.
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(8.dp)
						.clip(ShapeDefaults.ExtraSmall)
						.background(Color(0xCC8A1C1C))
						.padding(horizontal = 6.dp, vertical = 2.dp),
				) {
					Text(text = "sin señal", color = Color.White, fontSize = 11.sp)
				}
			}

			// Pegada al borde de abajo y dentro del recorte del mosaico: separarla
			// la convertiría en un subrayado y se leería como decoración, no como
			// «cuánto lleva».
			//
			// Por debajo de un 1 % no se pinta, igual que en la barra del canal: un
			// relleno de ancho casi cero se lee como un fallo de dibujo y no como
			// «acaba de empezar». Y no se pinta en un canal marcado sin señal,
			// donde una barra de progreso prometería algo que no se puede ver.
			if (progreso != null && progreso > 0.01f && !channel.unavailable) {
				Box(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.fillMaxWidth()
						.height(ALTO_PROGRESO)
						.background(Color.Black.copy(alpha = 0.35f)),
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth(progreso.coerceIn(0f, 1f))
							.height(ALTO_PROGRESO)
							.background(Color.White.copy(alpha = 0.85f)),
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(8.dp))
		Text(
			text = channel.name,
			color = if (enfocada) Color.White else Color(0xFFBFBFBF),
			fontWeight = if (enfocada) FontWeight.SemiBold else FontWeight.Normal,
			fontSize = 15.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 2.dp),
		)
	}
}

/**
 * Las marcas de la esquina: número, favorito, HD.
 *
 * Van sobre un velo oscuro porque caen encima del logo, y un logo claro deja
 * un número blanco ilegible. El velo cuesta menos que recortar el logo.
 */
@Composable
private fun Etiqueta(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = Color(0xFFE8E8E8),
	fontFamily: FontFamily? = null,
) {
	Box(
		modifier = modifier
			.clip(ShapeDefaults.ExtraSmall)
			.background(Color.Black.copy(alpha = 0.55f))
			.padding(horizontal = 6.dp, vertical = 2.dp),
	) {
		Text(
			text = text,
			color = color,
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			fontFamily = fontFamily,
		)
	}
}

private val ANCHO = 168.dp

/** 16:9, la proporción de todo lo demás en una tele. */
private val ALTO = 94.dp

/** La línea de progreso del borde. Cuatro dp se ven a tres metros; dos no. */
private val ALTO_PROGRESO = 4.dp

private val FONDO = Color(0xFF1C1C1E)
private val FONDO_ENFOCADO = Color(0xFF2E2E31)
private val BORDE = Color(0xFF2E2E31)
