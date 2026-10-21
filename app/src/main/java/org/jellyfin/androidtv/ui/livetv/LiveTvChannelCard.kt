package org.jellyfin.androidtv.ui.livetv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
	/**
	 * El color de marca del ISP, ya validado contra el fondo de la sección.
	 *
	 * Es lo único de la tarjeta que lo lleva, y es deliberado: el progreso es
	 * el único sitio donde el color dice algo —cuánto va del programa— en vez
	 * de decorar. El sistema de diseño lo pide así.
	 */
	accent: Color,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactions = remember { MutableInteractionSource() }
	val enfocada by interactions.collectIsFocusedAsState()

	// El crecimiento es pequeño a propósito: con las tarjetas pegadas, un salto
	// grande las solapa y el foco se lee peor, no mejor. Quien dice dónde está
	// el foco es el borde; la escala solo lo acompaña.
	val escala by animateFloatAsState(
		targetValue = if (enfocada) ESCALA_ENFOCADA else 1f,
		animationSpec = tween(durationMillis = MS_FOCO),
		label = "focoCanal",
	)

	// **¿Hay logo, o solo una URL?** No es lo mismo, y la tarjeta cambia de
	// forma según la respuesta: con logo, el número va a una esquina; sin él,
	// el número es la tarjeta.
	val url = channel.logoUrl?.takeIf { it.isNotBlank() }
	var falloLogo by remember(url) { mutableStateOf(false) }
	val conLogo = url != null && !falloLogo

	// Un canal que está en la lista pero no se puede ver **se atenúa entero**.
	// No se esconde —el abonado tiene que poder ver que existe y que hoy no— y
	// no se pinta de rojo: no es un error de la app ni algo que él pueda
	// arreglar. Mismo criterio que [SeccionCerrada].
	val atenuada = if (channel.unavailable) Modifier.alpha(OPACIDAD_SIN_SENAL) else Modifier

	Column(modifier = modifier.width(ANCHO_TARJETA)) {
		Box(
			modifier = Modifier
				.scale(escala)
				.fillMaxWidth()
				.height(ALTO_TARJETA)
				.clip(ShapeDefaults.Medium)
				.background(if (enfocada) LiveTvPaleta.elevado else LiveTvPaleta.mosaico)
				// **Sin borde en reposo.** Ciento veinte recuadros de 1 dp son
				// una rejilla, y la rejilla compite con lo único que hay que ver
				// de un vistazo: cuál tiene el foco. El borde queda para eso.
				.then(
					if (enfocada) {
						Modifier.border(BorderStroke(2.dp, Color.White), ShapeDefaults.Medium)
					} else {
						Modifier
					}
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
			// donde van número e insignias. Y cuando no hay logo, lo ocupa el
			// número.
			LogoDeCanal(
				channel = channel,
				modifier = Modifier
					.fillMaxWidth()
					.height(ALTO_TARJETA)
					.padding(horizontal = 16.dp, vertical = 14.dp)
					.then(atenuada),
				tamanoTexto = 22.sp,
				fondo = Color.Transparent,
				alFallar = { falloLogo = true },
				respaldo = { NumeroDeCanal(channel) },
			)

			if (conLogo && channel.number != null) {
				// Monoespaciada: con 120 canales la columna de números queda
				// alineada entre tarjetas y se recorre de un vistazo.
				Etiqueta(
					text = channel.number.toString(),
					modifier = Modifier.align(Alignment.TopStart).padding(8.dp).then(atenuada),
					fontFamily = FontFamily.Monospace,
				)
			}

			if (favorite) {
				Etiqueta(
					text = "★",
					modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).then(atenuada),
					color = LiveTvPaleta.tinta,
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
				//
				// **Y se dice en gris.** Era un distintivo rojo de error, que
				// contradecía a la propia sección: cuando el registro cierra la
				// sección entera se explica sin un solo rojo, porque no es un
				// fallo del aparato y el abonado no puede arreglarlo. Un canal
				// caído es exactamente el mismo caso.
				//
				// A plena opacidad sobre la tarjeta atenuada: es lo que explica
				// la atenuación, así que no puede atenuarse con ella.
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(8.dp)
						.clip(ShapeDefaults.ExtraSmall)
						.background(LiveTvPaleta.velo)
						.padding(horizontal = 6.dp, vertical = 2.dp),
				) {
					Text(text = "sin señal", color = LiveTvPaleta.tintaApagada, fontSize = 11.sp)
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
							.background(accent),
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(8.dp))
		Text(
			text = channel.name,
			color = if (enfocada) Color.White else LiveTvPaleta.tintaApagada,
			fontWeight = if (enfocada) FontWeight.SemiBold else FontWeight.Normal,
			fontSize = 15.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 2.dp).then(atenuada),
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
	color: Color = LiveTvPaleta.tintaApagada,
	fontFamily: FontFamily? = null,
) {
	Box(
		modifier = modifier
			.clip(ShapeDefaults.ExtraSmall)
			.background(LiveTvPaleta.velo)
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

/**
 * El número del canal ocupando el mosaico, para cuando no hay logo.
 *
 * **Y ese es el caso normal**, no el raro: un site recién montado sirve su
 * lista antes que sus imágenes, y mientras tanto las iniciales no distinguían
 * nada. «Estrellas HD» y «Estrellas SD» daban las dos `EST`, en dos tarjetas
 * pegadas, y la única forma de saber cuál era cuál era leer el nombre de
 * abajo — que es justo lo que la tarjeta existe para evitar.
 *
 * El número sí distingue: es único por definición, es lo que el abonado marca
 * con el mando y es lo que está impreso en la guía de papel de su proveedor.
 *
 * Monoespaciado para que 101 y 102 ocupen lo mismo y la columna de números se
 * recorra de un vistazo.
 */
@Composable
private fun NumeroDeCanal(channel: ChannelProfile) {
	val numero = channel.number
	if (numero == null) {
		// Sin logo y sin número solo quedan las iniciales, que es lo que había.
		Text(
			text = inicialesDeCanal(channel.name),
			color = LiveTvPaleta.tinta.copy(alpha = 0.82f),
			fontWeight = FontWeight.Bold,
			fontSize = 22.sp,
		)
		return
	}

	Text(
		text = numero.toString(),
		color = LiveTvPaleta.tinta,
		fontFamily = FontFamily.Monospace,
		fontWeight = FontWeight.Bold,
		fontSize = TAMANO_NUMERO,
	)
}

/**
 * El ancho de una tarjeta.
 *
 * `internal` porque la fila fantasma de la sección dibuja huecos de este
 * tamaño: si no fuera el mismo número, la lista daría un salto al llegar.
 */
internal val ANCHO_TARJETA = 168.dp

/** 16:9, la proporción de todo lo demás en una tele. */
internal val ALTO_TARJETA = 94.dp

/** La línea de progreso del borde. Cuatro dp se ven a tres metros; dos no. */
private val ALTO_PROGRESO = 4.dp

/**
 * El número cuando manda, sin logo.
 *
 * Treinta y cuatro sp entran en el mosaico de 168×94 dp con cuatro cifras y
 * se leen a tres metros, que es para lo que existe el tamaño.
 */
private val TAMANO_NUMERO = 34.sp

/**
 * Lo que crece la tarjeta enfocada, y en cuánto tiempo.
 *
 * Eran 1,06 sin duración declarada. El idioma de foco del sistema de diseño es
 * 1,04 en 160 ms: menos salto, porque con las tarjetas pegadas un salto grande
 * las solapa, y un tiempo fijo para que el movimiento sea el mismo en toda la
 * sección.
 */
private const val ESCALA_ENFOCADA = 1.04f

private const val MS_FOCO = 160

/**
 * Lo que se atenúa un canal que no se puede ver.
 *
 * La mitad justa: bastante para que se note sin leer nada, poco para que el
 * canal siga siendo reconocible. Por debajo de 0,4 el logo deja de leerse y la
 * tarjeta parece un hueco en la fila.
 */
private const val OPACIDAD_SIN_SENAL = 0.5f
