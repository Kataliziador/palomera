package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.epg.FranjaEpg
import org.jellyfin.livetv.source.ChannelProfile
import java.util.Date

/**
 * La barra de canal: lo que se ve al zapear y al pulsar OK sobre el vídeo.
 *
 * **El condicionante no es el contenido: es que vive encima de vídeo que no
 * controlamos** —nieve, un partido, una película oscura— y de eso salen las
 * decisiones:
 *
 * - **Casi monocroma.** Cualquier color se lee como dominante sobre imagen
 *   ajena. Quedan dos: el punto rojo del directo, que es convención universal
 *   y no hace falta explicar, y el relleno del progreso, que lleva el acento
 *   del ISP porque es el único sitio donde el color de marca dice algo —cuánto
 *   lleva el programa— en vez de decorar. Es la misma regla que en la ficha de
 *   la guía, y es lo que hace que las dos pantallas se lean como una.
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
 *
 * ## Lo que cambió el 04-nov
 *
 * La barra anterior ponía el logo del canal en un cuadro de 84 dp a la
 * izquierda y amontonaba a la derecha el reloj, las horas del programa y los
 * minutos que quedaban, uno debajo de otro. Dos problemas, los dos medidos en
 * el site de los probadores:
 *
 * - **ese cuadro estaba casi siempre vacío de información**: 52 de 53 canales
 *   no traen `logoUrl`, así que lo que enseñaba eran tres iniciales que no
 *   distinguen nada —«Estrellas HD» y «Estrellas SD» dan las dos `EST`—;
 * - **la columna de la derecha mezclaba dos relojes distintos**: la hora que
 *   es y las horas del programa. Al zapear, los dos números cambian a la vez y
 *   cuesta un segundo saber cuál es cuál.
 *
 * Ahora la izquierda es **el número**, que es la identidad real del canal y lo
 * que el abonado acaba de marcar; el logo entra en su sitio cuando lo hay y el
 * número se va al lado del nombre. La derecha es **solo el reloj**. Las horas
 * del programa bajan a la línea del programa, que es a lo que pertenecen, y el
 * progreso cruza la barra entera en lugar del 72 %.
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
	/**
	 * El acento del ISP, ya validado contra el fondo.
	 *
	 * Un site sin marca configurada manda [org.jellyfin.livetv.config.ACENTO_NEUTRO],
	 * que es el gris de la sección: la barra queda monocroma y no medio teñida.
	 */
	accent: Color,
	controles: List<Control> = emptyList(),
	/** −1 = el foco está en el canal, no en la fila. */
	controlSeleccionado: Int = -1,
	modifier: Modifier = Modifier,
) {
	val contexto = LocalContext.current
	val ahora = epg?.ahora

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
			.padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 34.dp)
	) {
		IdentidadDelCanal(
			channel = channel,
			favorito = favorito,
			idiomaAudio = idiomaAudio,
			altaDefinicion = altaDefinicion,
			ahoraMs = ahoraMs,
		)

		Spacer(modifier = Modifier.height(22.dp))

		// La línea del programa: qué dan, desde cuándo hasta cuándo, y cuánto
		// queda. Las tres cosas son el mismo dato visto de tres formas, así que
		// van en la misma línea y no repartidas por la barra.
		Row(verticalAlignment = Alignment.CenterVertically) {
			// El título y las horas van juntos y se reparten el ancho que
			// sobra; «quedan X min» se ancla a la derecha. Si los tres
			// compitieran por el mismo peso, un título largo empujaría la hora
			// fuera de la barra, que es el dato que menos se puede perder.
			Row(
				modifier = Modifier.weight(1f),
				verticalAlignment = Alignment.CenterVertically,
			) {
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
					// Sin programación, el estado ocupa el sitio del título: es
					// lo más importante que se puede decir en ese momento.
					text = ahora?.titulo ?: state,
					color = Color.White,
					fontWeight = FontWeight.Bold,
					fontSize = 26.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)

				if (ahora != null) {
					Spacer(modifier = Modifier.width(18.dp))
					Text(
						text = "${hora(contexto, ahora.inicioMs)} – ${hora(contexto, ahora.finMs)}",
						color = LiveTvPaleta.tintaApagadaSobreVideo,
						fontSize = 17.sp,
						maxLines = 1,
					)
				}
			}

			if (ahora != null) {
				Spacer(modifier = Modifier.width(18.dp))
				Text(
					text = "quedan ${ahora.restanteMin(ahoraMs)} min",
					color = LiveTvPaleta.tintaSutilSobreVideo,
					fontSize = 17.sp,
					maxLines = 1,
				)
			}
		}

		if (ahora != null) {
			Spacer(modifier = Modifier.height(14.dp))
			BarraDeProgreso(ahora.progreso(ahoraMs), accent)
		}

		epg?.luego?.let { siguiente ->
			Spacer(modifier = Modifier.height(16.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(text = "Luego", color = LiveTvPaleta.tintaSutilSobreVideo, fontSize = 16.sp)
				Spacer(modifier = Modifier.width(12.dp))
				Text(
					text = siguiente.titulo,
					color = LiveTvPaleta.tintaApagadaSobreVideo,
					fontSize = 16.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)
				Spacer(modifier = Modifier.width(10.dp))
				// **La hora de lo que viene, que antes no estaba.** «Luego
				// Piensa rápido» no dice si eso es dentro de dos minutos o
				// dentro de hora y media, que es justo lo que alguien decide
				// cuando mira esta línea.
				Text(
					text = hora(contexto, siguiente.inicioMs),
					color = LiveTvPaleta.tintaSutilSobreVideo,
					fontSize = 16.sp,
					maxLines = 1,
				)
			}
		}

		// Lo que el ISP quiso añadir y la app no entiende. Va después del
		// programa porque es lo menos urgente de la barra.
		if (channel.extras.isNotEmpty()) {
			Spacer(modifier = Modifier.height(14.dp))
			DatosExtra(
				channel.extras,
				colorEtiqueta = LiveTvPaleta.tintaSutilSobreVideo,
				colorValor = LiveTvPaleta.tintaApagadaSobreVideo,
			)
		}

		// El detalle técnico solo aparece cuando algo va mal: en marcha estorba.
		if (!enVivo && detail != null) {
			Spacer(modifier = Modifier.height(10.dp))
			Text(text = detail, color = LiveTvPaleta.tintaSutilSobreVideo, fontSize = 15.sp)
		}

		Spacer(modifier = Modifier.height(20.dp))

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

/**
 * Quién es este canal: el número, el nombre, lo que trae y qué hora es.
 *
 * **El número vive en un bloque y no suelto**, y es para que al zapear caiga
 * siempre en el mismo sitio con el mismo tamaño: un «9» y un «207» sueltos
 * mueven el nombre entero de posición en cada pulsación. El bloque es fijo,
 * el número va centrado y monoespaciado, y el nombre no se entera.
 */
@Composable
private fun IdentidadDelCanal(
	channel: ChannelProfile,
	favorito: Boolean,
	idiomaAudio: String?,
	altaDefinicion: Boolean,
	ahoraMs: Long,
) {
	val numero = channel.number
	val conLogo = !channel.logoUrl.isNullOrBlank()

	Row(verticalAlignment = Alignment.CenterVertically) {
		// El bloque: el logo cuando lo hay, y si no, el número. Es la misma
		// regla que la ficha de la guía, para que el canal se vea igual en las
		// dos pantallas.
		Box(
			modifier = Modifier
				.width(ANCHO_BLOQUE)
				.height(ALTO_BLOQUE)
				.clip(RoundedCornerShape(10.dp))
				.background(Color.White.copy(alpha = 0.08f)),
			contentAlignment = Alignment.Center,
		) {
			LogoDeCanal(
				channel = channel,
				modifier = Modifier.fillMaxSize().padding(8.dp),
				fondo = Color.Transparent,
				respaldo = {
					Text(
						text = numero?.toString() ?: inicialesDeCanal(channel.name),
						color = Color.White,
						fontFamily = FontFamily.Monospace,
						fontWeight = FontWeight.Bold,
						fontSize = 30.sp,
					)
				},
			)
		}

		Spacer(modifier = Modifier.width(18.dp))

		Column(modifier = Modifier.weight(1f)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				// Con logo, el número no cabe en el bloque y se dice aquí: el
				// abonado acaba de marcarlo y tiene que poder confirmarlo sin
				// buscarlo.
				if (conLogo && numero != null) {
					Text(
						text = numero.toString(),
						color = LiveTvPaleta.tintaApagadaSobreVideo,
						fontFamily = FontFamily.Monospace,
						fontWeight = FontWeight.Bold,
						fontSize = 21.sp,
					)
					Spacer(modifier = Modifier.width(12.dp))
				}

				Text(
					text = channel.name,
					color = Color.White,
					fontWeight = FontWeight.Bold,
					fontSize = 23.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)

				if (favorito) {
					Spacer(modifier = Modifier.width(12.dp))
					Text(text = "★", color = Color.White, fontSize = 19.sp)
				}
			}

			Spacer(modifier = Modifier.height(9.dp))

			Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
				// «HD» dice más que «1920x1080» a quien está en el sofá.
				Distintivo(if (altaDefinicion) "HD" else "SD")
				idiomaAudio?.let { Distintivo(it) }
			}
		}

		Spacer(modifier = Modifier.width(24.dp))

		// Solo la hora que es. Las del programa están en su línea.
		Text(
			text = hora(LocalContext.current, ahoraMs),
			color = Color.White,
			fontWeight = FontWeight.Medium,
			fontSize = 26.sp,
		)
	}
}

/**
 * Cuánto lleva el programa.
 *
 * **Cruza la barra entera**, antes el 72 %: una barra que se para a tres
 * cuartos invita a leer ese final como el final del programa, y no lo es.
 *
 * **Y el relleno lleva el acento del ISP**, que era blanco. Es el único
 * elemento gráfico de la barra y el único sitio donde el color de marca
 * significa algo; en la guía ya se usa así en cada ficha, y usarlo aquí
 * también es lo que hace que las dos pantallas parezcan la misma app.
 */
@Composable
private fun BarraDeProgreso(progreso: Float, accent: Color) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
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
					.background(accent),
			)
		}
	}
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

/** El bloque del número: fijo, para que al zapear nada se mueva. */
private val ANCHO_BLOQUE = 96.dp

private val ALTO_BLOQUE = 58.dp

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
