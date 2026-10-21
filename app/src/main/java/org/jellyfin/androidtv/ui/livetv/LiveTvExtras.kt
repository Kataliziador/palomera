package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.source.ChannelExtra

/**
 * Los pares etiqueta/valor que trae el `config.json`, pintados sin saber qué
 * son (`CLAUDE.md` §6).
 *
 * **La app no entiende estos datos, así que no puede jerarquizarlos.** De ahí
 * las dos decisiones de dibujo:
 *
 * - **Se repite el idioma visual de «Luego …» de la barra**: etiqueta apagada,
 *   valor un paso más claro. No inventa un componente nuevo para algo que el
 *   abonado leerá como una línea más de información del canal.
 * - **Fluye en varias líneas en vez de recortarse.** El parser ya acotó
 *   cuántos son y cuánto miden; lo que llegue aquí cabe, y una etiqueta
 *   cortada a la mitad se lee como un fallo de la app, no como un descuido del
 *   operador.
 *
 * Nunca lleva enlaces, acciones ni formato: en cuanto algo necesita
 * comportamiento deja de ser dato y vuelve a ser código.
 */
@Composable
fun DatosExtra(
	extras: List<ChannelExtra>,
	modifier: Modifier = Modifier,
	tamano: TextUnit = 17.sp,
	colorEtiqueta: Color = LiveTvPaleta.tintaSutil,
	colorValor: Color = LiveTvPaleta.tintaApagada,
) {
	if (extras.isEmpty()) return

	FlowRow(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(26.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		extras.forEach { extra ->
			Row {
				Text(text = extra.label, color = colorEtiqueta, fontSize = tamano)
				Spacer(modifier = Modifier.width(10.dp))
				Text(text = extra.value, color = colorValor, fontSize = tamano)
			}
		}
	}
}
