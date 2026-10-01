package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text

/**
 * Un control de la barra: qué hace y en qué estado está.
 *
 * `valor` no es decorativo. En televisión, un botón que no dice su estado
 * obliga a abrirlo para saber en qué quedó, y eso es un viaje de ida y vuelta
 * por cada duda.
 */
data class Control(
	val id: String,
	val etiqueta: String,
	val valor: String? = null,
	val activo: Boolean = false,
	val disponible: Boolean = true,
)

/**
 * La fila de controles, dentro de la barra.
 *
 * Se entra con ▶, se recorre con ◀ ▶ y se activa con OK, porque ▲ ▼ ya son el
 * zapeo y quitárselos para un menú sería cambiar el gesto más usado por el
 * menos usado.
 *
 * **Un control sin nada que ofrecer se enseña apagado, no se esconde.** Que el
 * botón de subtítulos desaparezca en unos canales y aparezca en otros se lee
 * como un fallo; verlo en gris dice la verdad: este canal no trae.
 */
@Composable
fun LiveTvControles(
	controles: List<Control>,
	seleccionado: Int,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		controles.forEachIndexed { i, control ->
			BotonDeControl(control, enfocado = i == seleccionado)
		}
	}
}

@Composable
private fun BotonDeControl(control: Control, enfocado: Boolean) {
	// El orden importa: un control deshabilitado **y enfocado** tiene fondo
	// blanco, así que pintarlo del gris de deshabilitado lo dejaba invisible.
	// Primero se decide sobre qué fondo se está, y luego cuánto se apaga.
	val color = when {
		enfocado && !control.disponible -> Color.Black.copy(alpha = 0.38f)
		enfocado -> Color.Black
		!control.disponible -> Color.White.copy(alpha = 0.30f)
		else -> Color.White.copy(alpha = 0.92f)
	}

	Row(
		modifier = Modifier
			.clip(RoundedCornerShape(6.dp))
			.background(
				when {
					enfocado -> Color.White
					control.activo -> Color.White.copy(alpha = 0.26f)
					else -> Color.White.copy(alpha = 0.12f)
				}
			)
			.then(
				// El borde marca lo que está encendido cuando no tiene el foco:
				// sin él, «activo» y «disponible» se ven igual de lejos.
				if (control.activo && !enfocado) {
					Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
				} else {
					Modifier
				}
			)
			.padding(horizontal = 14.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = control.etiqueta,
			color = color,
			fontSize = 15.sp,
			fontWeight = if (enfocado) FontWeight.SemiBold else FontWeight.Normal,
		)
		control.valor?.let {
			Spacer(modifier = Modifier.width(8.dp))
			Text(
				text = it,
				color = when {
					enfocado && !control.disponible -> Color.Black.copy(alpha = 0.30f)
					enfocado -> Color.Black.copy(alpha = 0.62f)
					!control.disponible -> Color.White.copy(alpha = 0.22f)
					else -> Color.White.copy(alpha = 0.58f)
				},
				fontSize = 14.sp,
			)
		}
	}
}

/**
 * La lista que se abre al activar un control con varias opciones.
 *
 * Se dibuja encima de la barra y no en un diálogo aparte: el abonado está
 * eligiendo sobre lo que está viendo, y sacarlo a otra pantalla le haría
 * perder de vista el canal.
 */
@Composable
fun LiveTvOpciones(
	titulo: String,
	opciones: List<String>,
    seleccionada: Int,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.clip(RoundedCornerShape(10.dp))
			// Opaco del todo y con borde: sobre el velo de la barra, un panel
			// traslúcido deja ver el texto de debajo y las dos capas se leen
			// como una sola cosa rota.
			.background(Color(0xFF0D0D0D))
			.border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
			.padding(horizontal = 24.dp, vertical = 18.dp),
	) {
		Text(text = titulo, color = Color(0xFF8A8A8A), fontSize = 14.sp)
		Spacer(modifier = Modifier.height(12.dp))

		opciones.forEachIndexed { i, opcion ->
			Row(
				modifier = Modifier
					.clip(RoundedCornerShape(5.dp))
					.background(if (i == seleccionada) Color.White else Color.Transparent)
					.padding(horizontal = 14.dp, vertical = 8.dp),
			) {
				Text(
					text = opcion,
					color = if (i == seleccionada) Color.Black else Color.White.copy(alpha = 0.88f),
					fontSize = 18.sp,
					fontWeight = if (i == seleccionada) FontWeight.SemiBold else FontWeight.Normal,
				)
			}
			if (i < opciones.lastIndex) Spacer(modifier = Modifier.height(4.dp))
		}
	}
}

/**
 * Los subtítulos, sobre el vídeo.
 *
 * Fondo semitransparente y no texto suelto: encima de una escena clara, el
 * texto blanco sin caja desaparece, y es justo cuando hace falta leerlo.
 */
@Composable
fun LiveTvSubtitulos(lineas: List<String>, modifier: Modifier = Modifier) {
	if (lineas.isEmpty()) return
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(3.dp),
	) {
		lineas.forEach { linea ->
			Text(
				text = linea,
				color = Color.White,
				fontSize = 24.sp,
				modifier = Modifier
					.clip(RoundedCornerShape(4.dp))
					.background(Color.Black.copy(alpha = 0.72f))
					.padding(horizontal = 12.dp, vertical = 4.dp),
			)
		}
	}
}
