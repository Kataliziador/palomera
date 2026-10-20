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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.epg.FranjaEpg
import org.jellyfin.livetv.source.ChannelProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
				Brush.verticalGradient(
					0f to Color.Transparent,
					0.45f to Color.Black.copy(alpha = 0.55f),
					1f to Color.Black.copy(alpha = 0.88f),
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

			Column(horizontalAlignment = Alignment.End) {
				Reloj(ahoraMs)
				epg?.ahora?.let { p ->
					Spacer(modifier = Modifier.height(14.dp))
					Text(
						text = "${hora(p.inicioMs)} – ${hora(p.finMs)}",
						color = Color(0xFFA8ADB4),
						fontSize = 17.sp,
					)
					Spacer(modifier = Modifier.height(3.dp))
					Text(
						text = "quedan ${p.restanteMin(ahoraMs)} min",
						color = Color(0xFF7D8187),
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
					.background(Color(0xFFE53935)),
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
			Text(text = "Luego", color = Color(0xFF7D8187), fontSize = 17.sp)
			Spacer(modifier = Modifier.width(14.dp))
			Text(text = siguiente.titulo, color = Color(0xFFA8ADB4), fontSize = 17.sp)
		}
	}

	// Lo que el ISP quiso añadir y la app no entiende. Va al final del bloque
	// del programa porque es lo menos urgente de la barra: debajo de qué están
	// dando y de qué viene luego.
	if (channel.extras.isNotEmpty()) {
		Spacer(modifier = Modifier.height(12.dp))
		DatosExtra(channel.extras)
	}

	// El detalle técnico solo aparece cuando algo va mal: en marcha estorba.
	if (!enVivo && detail != null) {
		Spacer(modifier = Modifier.height(8.dp))
		Text(text = detail, color = Color(0xFF7D8187), fontSize = 15.sp)
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
		text = hora(ahoraMs),
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
		Text(text = que, color = Color(0xFF6E7278), fontSize = 14.sp)
	}
}

private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * `HH:mm` en el huso de la caja.
 *
 * Compartida con la sección —no privada de este archivo— para que la línea de
 * programación de la guía y la barra del canal escriban la hora igual. Dos
 * formateadores para lo mismo acaban divergiendo.
 */
internal fun hora(ms: Long): String = formatoHora.format(Date(ms))
