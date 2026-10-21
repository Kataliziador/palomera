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
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.ButtonColors
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults

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
	// Aquí la píldora **sí** es lo correcto, y por el mismo motivo por el que
	// no lo era en la guía: estos son actos —cambiar audio, ajustar imagen— y
	// en este sistema de diseño una píldora significa acción. Un canal es
	// contenido y por eso su tarjeta es un rectángulo.
	//
	// La selección no es el foco real de Compose: se recorre con ◀ ▶ dentro de
	// la barra, así que los colores se pasan a mano en vez de dejar que el
	// botón reaccione solo.
	val esquema = JellyfinTheme.colorScheme
	val colores = ButtonColors(
		containerColor = when {
			!control.disponible -> esquema.buttonDisabled
			control.activo -> esquema.buttonActive
			else -> esquema.button
		},
		contentColor = when {
			!control.disponible -> esquema.onButtonDisabled
			control.activo -> esquema.onButtonActive
			else -> esquema.onButton
		},
		focusedContainerColor = esquema.buttonFocused,
		focusedContentColor = esquema.onButtonFocused,
		disabledContainerColor = esquema.buttonDisabled,
		disabledContentColor = esquema.onButtonDisabled,
	)

	// Un control deshabilitado **y** seleccionado tiene el fondo claro del
	// foco, así que su texto no puede ser el gris de deshabilitado: quedaría
	// invisible. Primero se decide sobre qué fondo se está.
	val fondo = if (enfocado) colores.focusedContainerColor else colores.containerColor
	val tinta = if (enfocado) {
		if (control.disponible) colores.focusedContentColor else colores.focusedContentColor.copy(alpha = 0.45f)
	} else {
		colores.contentColor
	}

	Row(
		modifier = Modifier
			.clip(ButtonDefaults.Shape)
			.background(fondo, ButtonDefaults.Shape)
			.then(
				// El borde marca lo que está encendido cuando no está
				// seleccionado: sin él, «activo» y «disponible» se ven igual
				// de lejos.
				if (control.activo && !enfocado) {
					Modifier.border(1.dp, esquema.onButtonActive.copy(alpha = 0.55f), ButtonDefaults.Shape)
				} else {
					Modifier
				}
			)
			.padding(horizontal = 16.dp, vertical = 9.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = control.etiqueta,
			color = tinta,
			fontSize = 15.sp,
			fontWeight = if (enfocado) FontWeight.SemiBold else FontWeight.Normal,
		)
		control.valor?.let {
			Spacer(modifier = Modifier.width(8.dp))
			Text(text = it, color = tinta.copy(alpha = 0.66f), fontSize = 14.sp)
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
			.clip(ShapeDefaults.Medium)
			// Opaco del todo y con borde: sobre el velo de la barra, un panel
			// traslúcido deja ver el texto de debajo y las dos capas se leen
			// como una sola cosa rota.
			.background(LiveTvPaleta.fondo)
			.border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
			.padding(horizontal = 24.dp, vertical = 18.dp),
	) {
		Text(text = titulo, color = LiveTvPaleta.tintaSutil, fontSize = 14.sp)
		Spacer(modifier = Modifier.height(12.dp))

		opciones.forEachIndexed { i, opcion ->
			Row(
				modifier = Modifier
					.clip(ShapeDefaults.ExtraSmall)
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
					.clip(ShapeDefaults.ExtraSmall)
					.background(Color.Black.copy(alpha = 0.72f))
					.padding(horizontal = 12.dp, vertical = 4.dp),
			)
		}
	}
}
