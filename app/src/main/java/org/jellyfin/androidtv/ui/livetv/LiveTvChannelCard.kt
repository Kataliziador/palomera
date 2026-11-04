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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Una ficha de canal: quién es arriba, qué dan abajo.
 *
 * **Cambió de forma el 04-nov, y el motivo es que la anterior era muda.** Era
 * un mosaico de 168×94 con el número y el nombre **fuera**, debajo; lo que dan
 * se contaba una sola vez, arriba de la guía, para el canal que tuviera el
 * foco. Con 53 canales eso significa que la pantalla enseña cincuenta y tres
 * números y **un** programa: toda la superficie trabaja para decir lo único que
 * el abonado ya sabe —el número que marcó— y nada para lo que no sabe.
 *
 * La ficha mete las dos cosas dentro del mismo recuadro:
 *
 * - **arriba, la identidad** — el logo si lo hay y, si no, el número, que es lo
 *   normal: 52 de los 53 canales del site de los probadores no traen `logoUrl`;
 * - **en medio, la línea de progreso**, que es el único sitio de la sección
 *   donde el color de marca del ISP dice algo en vez de decorar;
 * - **abajo, el nombre y el programa**, los dos dentro del ancho de la ficha.
 *
 * **Y por eso la ficha es más ancha: 198 dp y cuatro por fila, no cinco.** Es
 * el ancho que deja el cálculo —864 dp útiles, cuatro fichas y tres huecos de
 * 24— y es lo que hace que «Discovery Home & Health» quepa en dos líneas sin
 * recortarse. Se pagan unos 20 dp de alto por fila; a cambio, una pantalla de
 * guía dice qué están dando en ocho canales en vez de en uno.
 *
 * **Lo que esto deja desfasado**, y conviene decirlo aquí porque está escrito
 * en otro archivo: el comentario de `QueDanAhora` (`LiveTvFragment`) sostiene
 * que el programa no cabe en la tarjeta. Era verdad con 168 dp y una línea. Esa
 * línea de arriba **se queda**, y no por inercia: en la ficha el título va
 * recortado a una línea y sin horas, y arriba cabe entero, con la hora de
 * inicio y fin y los minutos que quedan. Son dos niveles de detalle, no el
 * mismo dato dos veces.
 *
 * **La ficha no es un `Button`.** `ButtonDefaults.Shape` es `CircleShape`: en
 * este sistema de diseño una píldora significa «acción», y un canal no es una
 * acción, es contenido.
 */
@Composable
fun LiveTvChannelCard(
	channel: ChannelProfile,
	favorite: Boolean,
	/**
	 * Lo que lleva emitido el programa en curso, 0..1, o `null` si de este canal
	 * no hay guía.
	 */
	progreso: Float? = null,
	/**
	 * El título de lo que dan ahora, o `null` si de este canal no se sabe.
	 *
	 * Recortado a una línea: un título real no cabe en 176 dp de texto y
	 * partirlo en dos le quitaría la línea al nombre, que es lo que identifica
	 * el canal. Lo que no cabe se lee entero arriba, en la línea del canal
	 * enfocado.
	 */
	programa: String? = null,
	/**
	 * Si **el site** publica guía, que no es lo mismo que si la publica para
	 * este canal.
	 *
	 * Con las dos cosas en una sola bandera, un site sin EPG —el caso normal
	 * mientras un ISP monta su tubería— saldría con «Sin guía» escrito 53 veces.
	 * Separadas, ese site no reserva la línea y sus fichas son más bajas; y en
	 * un site que sí publica, los 17 canales que no tienen programación dicen
	 * «Sin guía», que ahí sí es información.
	 */
	siteConGuia: Boolean = false,
	/**
	 * El color de marca del ISP, ya validado contra el fondo de la sección.
	 *
	 * Es lo único de la ficha que lo lleva, y es deliberado: el progreso es el
	 * único sitio donde el color dice algo —cuánto va del programa— en vez de
	 * decorar. El sistema de diseño lo pide así.
	 */
	accent: Color,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactions = remember { MutableInteractionSource() }
	val enfocada by interactions.collectIsFocusedAsState()

	// El crecimiento es pequeño a propósito: con las fichas pegadas, un salto
	// grande las solapa y el foco se lee peor, no mejor. Quien dice dónde está
	// el foco es el borde; la escala solo lo acompaña.
	val escala by animateFloatAsState(
		targetValue = if (enfocada) ESCALA_ENFOCADA else 1f,
		animationSpec = tween(durationMillis = MS_FOCO),
		label = "focoCanal",
	)

	// **¿Hay logo, o solo una URL?** No es lo mismo, y la mitad de arriba cambia
	// de forma según la respuesta: con logo, el número va a una esquina; sin él,
	// el número ocupa el hueco entero.
	val url = channel.logoUrl?.takeIf { it.isNotBlank() }
	var falloLogo by remember(url) { mutableStateOf(false) }
	val conLogo = url != null && !falloLogo

	// Un canal que está en la lista pero no se puede ver **se atenúa entero**.
	// No se esconde —el abonado tiene que poder ver que existe y que hoy no— y
	// no se pinta de rojo: no es un error de la app ni algo que él pueda
	// arreglar. Mismo criterio que [SeccionCerrada].
	val atenuada = if (channel.unavailable) Modifier.alpha(OPACIDAD_SIN_SENAL) else Modifier

	Column(
		modifier = modifier
			.width(ANCHO_TARJETA)
			.height(if (siteConGuia) ALTO_TARJETA else ALTO_TARJETA_SIN_GUIA)
			.scale(escala)
			.clip(ShapeDefaults.Medium)
			.background(if (enfocada) LiveTvPaleta.elevado else LiveTvPaleta.mosaico)
			// **Sin borde en reposo.** Cincuenta recuadros de 1 dp son una
			// rejilla, y la rejilla compite con lo único que hay que ver de un
			// vistazo: cuál tiene el foco. El borde queda para eso.
			.then(
				if (enfocada) {
					Modifier.border(BorderStroke(2.dp, Color.White), ShapeDefaults.Medium)
				} else {
					Modifier
				}
			)
			// `combinedClickable` trae el foco consigo, así que no hace falta
			// `focusable` aparte.
			.combinedClickable(
				interactionSource = interactions,
				indication = null,
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		Box(modifier = Modifier.fillMaxWidth().height(ALTO_IDENTIDAD)) {
			LogoDeCanal(
				channel = channel,
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 16.dp, vertical = 10.dp)
					.then(atenuada),
				tamanoTexto = 22.sp,
				fondo = Color.Transparent,
				alFallar = { falloLogo = true },
				respaldo = { NumeroDeCanal(channel) },
			)

			if (conLogo && channel.number != null) {
				// Monoespaciada: con 53 canales la columna de números queda
				// alineada entre fichas y se recorre de un vistazo.
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
				// A plena opacidad sobre la ficha atenuada: es lo que explica
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
		}

		// **La pista se pinta siempre, aunque no haya nada que marcar.** Es lo
		// que separa la identidad del programa y lo que hace que todas las
		// fichas de una fila midan exactamente igual; si apareciera solo cuando
		// hay guía, la mitad de los canales de un grupo tendrían el nombre tres
		// dp más arriba que la otra mitad y la fila se vería desalineada sin
		// que se supiera por qué.
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(ALTO_PROGRESO)
				.background(Color.White.copy(alpha = 0.10f)),
		) {
			// Por debajo de un 1 % no se pinta, igual que en la barra del canal:
			// un relleno de ancho casi cero se lee como un fallo de dibujo y no
			// como «acaba de empezar». Y no se pinta en un canal marcado sin
			// señal, donde prometería algo que no se puede ver.
			if (progreso != null && progreso > 0.01f && !channel.unavailable) {
				Box(
					modifier = Modifier
						.fillMaxWidth(progreso.coerceIn(0f, 1f))
						.fillMaxHeight()
						.background(accent),
				)
			}
		}

		Column(
			modifier = Modifier
				.padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 8.dp)
				.then(atenuada),
		) {
			// **Dos líneas fijas, no «hasta dos».** «Discovery Home & Health»
			// necesita dos y «ESPN» una, y si la caja del nombre creciera solo
			// cuando hace falta, el programa de una ficha quedaría a distinta
			// altura que el de la de al lado. Reservadas las dos siempre, la
			// fila se lee como una rejilla y no como una escalera.
			Text(
				text = channel.name,
				color = if (enfocada) Color.White else LiveTvPaleta.tintaApagada,
				fontWeight = FontWeight.SemiBold,
				fontSize = 15.sp,
				lineHeight = 17.sp,
				minLines = 2,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)

			if (siteConGuia) {
				Spacer(modifier = Modifier.height(2.dp))
				Text(
					text = programa ?: "Sin guía",
					color = when {
						programa == null -> LiveTvPaleta.tintaSutil
						enfocada -> LiveTvPaleta.tintaApagada
						else -> LiveTvPaleta.tintaSutil
					},
					fontSize = 13.sp,
					lineHeight = 15.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
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
 * El número del canal ocupando la mitad de arriba, para cuando no hay logo.
 *
 * **Y ese es el caso normal**, no el raro: un site recién montado sirve su
 * lista antes que sus imágenes, y mientras tanto las iniciales no distinguían
 * nada. «Estrellas HD» y «Estrellas SD» daban las dos `EST`, en dos fichas
 * pegadas, y la única forma de saber cuál era cuál era leer el nombre de
 * abajo — que ahora está dentro de la ficha, pero sigue siendo leer.
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
 * El ancho de una ficha.
 *
 * **Sale de una cuenta, no de un gusto:** la sección deja 864 dp útiles —960 de
 * la pantalla menos 48 de margen a cada lado— y cuatro fichas con tres huecos
 * de 24 dp dan (864 − 72) / 4 = 198. Cambiar uno de los tres números obliga a
 * recalcular los otros dos, o la cuarta ficha se corta por el borde derecho.
 *
 * `internal` porque la fila fantasma de la sección dibuja huecos de este
 * tamaño: si no fuera el mismo número, la lista daría un salto al llegar.
 */
internal val ANCHO_TARJETA = 198.dp

/** La mitad de arriba: el logo, o el número cuando no hay logo. */
private val ALTO_IDENTIDAD = 92.dp

/** La pista de progreso, que además separa la identidad del programa. */
private val ALTO_PROGRESO = 3.dp

/**
 * El alto de una ficha en un site que publica guía.
 *
 * 92 de identidad + 3 de pista + 8 + dos líneas de nombre (17×2) + 2 + una de
 * programa (15) + 8.
 */
internal val ALTO_TARJETA = 162.dp

/** Lo mismo sin la línea del programa, para un site que no publica guía. */
internal val ALTO_TARJETA_SIN_GUIA = 145.dp

/**
 * El número cuando manda, sin logo.
 *
 * Treinta y ocho sp entran en los 198×92 de la mitad de arriba con cuatro
 * cifras y se leen a tres metros, que es para lo que existe el tamaño. Eran 34
 * cuando la tarjeta medía 168 de ancho.
 */
private val TAMANO_NUMERO = 38.sp

/**
 * Lo que crece la ficha enfocada, y en cuánto tiempo.
 *
 * El idioma de foco del sistema de diseño es 1,04 en 160 ms: poco salto, porque
 * con las fichas pegadas un salto grande las solapa, y un tiempo fijo para que
 * el movimiento sea el mismo en toda la sección.
 */
private const val ESCALA_ENFOCADA = 1.04f

private const val MS_FOCO = 160

/**
 * Lo que se atenúa un canal que no se puede ver.
 *
 * La mitad justa: bastante para que se note sin leer nada, poco para que el
 * canal siga siendo reconocible. Por debajo de 0,4 el logo deja de leerse y la
 * ficha parece un hueco en la fila.
 */
private const val OPACIDAD_SIN_SENAL = 0.5f
