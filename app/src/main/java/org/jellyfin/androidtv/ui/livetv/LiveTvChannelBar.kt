package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.epg.FranjaEpg
import org.jellyfin.livetv.source.ChannelProfile
import java.util.Date

/**
 * La barra de canal.
 *
 * **El condicionante no es el contenido: es que vive encima de vídeo que no
 * controlamos** —nieve, un partido, una película oscura— y de eso salen las
 * decisiones:
 *
 * - **Monocroma, con un solo punto rojo para «en vivo».** Cualquier color se
 *   lee como dominante sobre imagen ajena. El rojo del directo es convención
 *   universal y no hace falta explicarlo. Deja además el hueco limpio para que
 *   el color de marca de cada ISP entre después como relleno del progreso.
 * - **El programa es el protagonista, no el canal.** Al pulsar OK el abonado
 *   ya sabe qué canal ve; lo que no sabe es qué están dando.
 * - **El tiempo es el eje.** Progreso, lo que queda, lo que viene y el reloj:
 *   todo se ordena respecto a «ahora».
 * - **Velo degradado, nunca un bloque opaco.** Pesado bajo el texto y
 *   desaparecido arriba, para no recortar la imagen con un borde duro.
 *
 *   **Corregido el 30-oct: el desvanecido caía encima del texto.** La idea era
 *   ésta, pero el degradado repartía la subida por el 45 % de la barra, que es
 *   justo donde van el número, el nombre y las horas. Medido sobre la captura
 *   de la ficha —banda limpia sin letras, a 1910 px— y proyectado a vídeo
 *   blanco a pantalla completa, que es el peor caso real: el nombre del canal
 *   salía a **2,03:1** y las horas a **1,66** y **1,58**. Ahora el
 *   desvanecido se agota dentro del relleno superior y por debajo el velo se
 *   queda plano; las mismas filas dan 9,2, 6,3 y 4,7.
 *
 *   Lo segundo que hizo falta es que **las tintas secundarias dejen de ser
 *   grises fijos sobre vídeo** ([LiveTvPaleta.tintaSutilSobreVideo]): ningún
 *   velo razonable sube un `#9DA1A6` a 4,5:1 contra blanco.
 * - **Monoespaciada solo en el número**, porque al zapear los dígitos tienen
 *   que caer siempre en el mismo sitio.
 *
 * Tamaños pensados para tres metros de distancia: nada por debajo de 16 sp.
 */
@Composable
fun LiveTvChannelBar(
	channel: ChannelProfile,
	state: String,
	detail: String?,
	epg: FranjaEpg?,
	ahoraMs: Long,
	favorito: Boolean,
	idiomaAudio: String?,
	/** Alta definición **medida**, no la que declare el `config.json`. */
	altaDefinicion: Boolean,
	enVivo: Boolean,
	controles: List<Control> = emptyList(),
	/** −1 = el foco está en el canal, no en la fila. */
	controlSeleccionado: Int = -1,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(
				// El desvanecido se agota dentro del relleno de arriba —56 dp
				// de 650 px de barra, de ahí el 0,18— y por debajo el velo ya
				// no sube: así el degradado no cae encima de ninguna letra.
				Brush.verticalGradient(
					0f to Color.Transparent,
					0.18f to Color.Black.copy(alpha = 0.72f),
					1f to Color.Black.copy(alpha = 0.90f),
				)
			)
			.padding(start = 56.dp, end = 56.dp, top = 56.dp, bottom = 32.dp)
	) {
		Row(verticalAlignment = Alignment.Top) {
			LogoDeCanal(channel, modifier = Modifier.size(84.dp))

			Spacer(modifier = Modifier.width(22.dp))

			Column(modifier = Modifier.weight(1f)) {
				IdentidadDelCanal(channel, favorito, idiomaAudio, altaDefinicion)
				Spacer(modifier = Modifier.height(14.dp))
				QueEstanDando(channel, state, detail, epg, ahoraMs, enVivo)
			}

			Spacer(modifier = Modifier.width(28.dp))

			val contexto = LocalContext.current
			Column(horizontalAlignment = Alignment.End) {
				Reloj(ahoraMs)
				epg?.ahora?.let { p ->
					Spacer(modifier = Modifier.height(14.dp))
					Text(
						text = "${hora(contexto, p.inicioMs)} – ${hora(contexto, p.finMs)}",
						color = LiveTvPaleta.tintaApagadaSobreVideo,
						fontSize = 17.sp,
					)
					Spacer(modifier = Modifier.height(3.dp))
					Text(
						text = "quedan ${p.restanteMin(ahoraMs)} min",
						color = LiveTvPaleta.tintaSutilSobreVideo,
						fontSize = 16.sp,
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(22.dp))

		if (controles.isEmpty()) {
			Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
				Pista("▲ ▼", "cambiar de canal")
				Pista("OK", "ver esta barra")
			}
		} else {
			LiveTvControles(controles = controles, seleccionado = controlSeleccionado)
			Spacer(modifier = Modifier.height(12.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
				if (controlSeleccionado < 0) {
					Pista("▲ ▼", "cambiar de canal")
					Pista("▶", "controles")
				} else {
					Pista("◀ ▶", "elegir")
					Pista("OK", "abrir")
				}
			}
		}
	}
}

@Composable
private fun IdentidadDelCanal(
	channel: ChannelProfile,
	favorito: Boolean,
	idiomaAudio: String?,
	altaDefinicion: Boolean,
) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		channel.number?.let { numero ->
			Text(
				text = numero.toString(),
				color = Color.White,
				fontFamily = FontFamily.Monospace,
				fontWeight = FontWeight.Medium,
				fontSize = 40.sp,
			)
			Spacer(modifier = Modifier.width(18.dp))
		}

		Text(
			text = channel.name,
			color = Color.White,
			fontWeight = FontWeight.SemiBold,
			fontSize = 26.sp,
		)

		Spacer(modifier = Modifier.width(16.dp))

		// «HD» dice más que «1920x1080» a quien está en el sofá.
		Distintivo(if (altaDefinicion) "HD" else "SD")
		idiomaAudio?.let { Spacer(modifier = Modifier.width(8.dp)); Distintivo(it) }

		if (favorito) {
			Spacer(modifier = Modifier.width(12.dp))
			Text(text = "★", color = Color.White, fontSize = 20.sp)
		}
	}
}

@Composable
private fun QueEstanDando(
	channel: ChannelProfile,
	state: String,
	detail: String?,
	epg: FranjaEpg?,
	ahoraMs: Long,
	enVivo: Boolean,
) {
	val ahora = epg?.ahora

	Row(verticalAlignment = Alignment.CenterVertically) {
		if (enVivo) {
			Box(
				modifier = Modifier
					.size(11.dp)
					.clip(CircleShape)
					.background(LiveTvPaleta.enVivo),
			)
			Spacer(modifier = Modifier.width(12.dp))
		}

		Text(
			// Sin programación, el estado ocupa el sitio del título: es lo más
			// importante que se puede decir en ese momento.
			text = ahora?.titulo ?: state,
			color = Color.White,
			fontWeight = FontWeight.Bold,
			fontSize = 32.sp,
		)
	}

	if (ahora != null) {
		Spacer(modifier = Modifier.height(12.dp))
		BarraDeProgreso(ahora.progreso(ahoraMs))
	}

	val siguiente = epg?.luego
	if (siguiente != null) {
		Spacer(modifier = Modifier.height(12.dp))
		Row {
			Text(text = "Luego", color = LiveTvPaleta.tintaSutilSobreVideo, fontSize = 17.sp)
			Spacer(modifier = Modifier.width(14.dp))
			Text(text = siguiente.titulo, color = LiveTvPaleta.tintaApagadaSobreVideo, fontSize = 17.sp)
		}
	}

	// Lo que el ISP quiso añadir y la app no entiende. Va al final del bloque
	// del programa porque es lo menos urgente de la barra: debajo de qué están
	// dando y de qué viene luego.
	if (channel.extras.isNotEmpty()) {
		Spacer(modifier = Modifier.height(12.dp))
		DatosExtra(
			channel.extras,
			colorEtiqueta = LiveTvPaleta.tintaSutilSobreVideo,
			colorValor = LiveTvPaleta.tintaApagadaSobreVideo,
		)
	}

	// El detalle técnico solo aparece cuando algo va mal: en marcha estorba.
	if (!enVivo && detail != null) {
		Spacer(modifier = Modifier.height(8.dp))
		Text(text = detail, color = LiveTvPaleta.tintaSutilSobreVideo, fontSize = 15.sp)
	}
}

/**
 * El único elemento gráfico de la barra, y el que lleva «cuánto llevas».
 *
 * Blanco sobre blanco al 22 %: monocromo a propósito, para que el color de
 * marca del ISP pueda sustituir el relleno sin rediseñar nada.
 */
@Composable
private fun BarraDeProgreso(progreso: Float) {
	Box(
		modifier = Modifier
			.fillMaxWidth(0.72f)
			.height(5.dp)
			.clip(RoundedCornerShape(3.dp))
			.background(Color.White.copy(alpha = 0.22f)),
	) {
		// Por debajo de un 1 % no se pinta: el relleno redondeado a ancho cero
		// deja un punto suelto que se lee como un fallo de dibujo, no como
		// «acaba de empezar».
		if (progreso > 0.01f) {
			Box(
				modifier = Modifier
					.fillMaxWidth(progreso.coerceIn(0f, 1f))
					.height(5.dp)
					.clip(RoundedCornerShape(3.dp))
					.background(Color.White),
			)
		}
	}
}

@Composable
private fun Reloj(ahoraMs: Long) {
	Text(
		text = hora(LocalContext.current, ahoraMs),
		color = Color.White,
		fontWeight = FontWeight.Medium,
		fontSize = 30.sp,
	)
}

@Composable
private fun Distintivo(texto: String) {
	Text(
		text = texto,
		color = Color.White.copy(alpha = 0.92f),
		fontSize = 15.sp,
		fontWeight = FontWeight.Medium,
		modifier = Modifier
			.clip(RoundedCornerShape(4.dp))
			.background(Color.White.copy(alpha = 0.16f))
			.padding(horizontal = 9.dp, vertical = 3.dp),
	)
}

@Composable
private fun Pista(tecla: String, que: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = tecla,
			color = Color.White.copy(alpha = 0.7f),
			fontFamily = FontFamily.Monospace,
			fontSize = 14.sp,
			modifier = Modifier
				.clip(RoundedCornerShape(3.dp))
				.background(Color.White.copy(alpha = 0.12f))
				.padding(horizontal = 8.dp, vertical = 3.dp),
		)
		Spacer(modifier = Modifier.width(8.dp))
		Text(text = que, color = LiveTvPaleta.tintaSutilSobreVideo, fontSize = 14.sp)
	}
}

/**
 * La hora como la escribe el resto de la caja.
 *
 * **Era `HH:mm` fijo, y se veía.** En una caja puesta en 12 h, la barra del
 * canal marcaba «16:01» mientras la barra superior de la app —que es del
 * upstream y sí respeta el ajuste del sistema— marcaba «4:00» en la misma
 * pantalla. El abonado no sabe que son dos barras de dos sitios: lee un fallo.
 *
 * `DateFormat.getTimeFormat` devuelve el formato del sistema, así que sigue el
 * ajuste de 12/24 h y el idioma sin obligar a nadie a elegir por el abonado.
 *
 * Compartida con la sección —no privada de este archivo— para que la línea de
 * programación de la guía y la barra del canal escriban la hora igual. Dos
 * formateadores para lo mismo acaban divergiendo.
 */
internal fun hora(context: Context, ms: Long): String =
	DateFormat.getTimeFormat(context).format(Date(ms))
