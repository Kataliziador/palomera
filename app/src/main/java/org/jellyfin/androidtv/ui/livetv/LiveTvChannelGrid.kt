package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.livetv.config.ChannelGroup
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Una sección de la guía: su título y los canales que la componen.
 *
 * «Favoritos» es una más, y por eso no lleva tratamiento especial en el
 * dibujado: se construye antes, en [agruparCanales].
 */
data class ChannelSection(
	val id: String,
	val title: String,
	val channels: List<ChannelProfile>,
)

/**
 * Reparte los canales en secciones para la guía.
 *
 * El orden es deliberado: **favoritos primero**, porque son los cuatro canales
 * que alguien ve de verdad; después los grupos del operador en el orden que él
 * fijó; y al final los que no tienen grupo, que existen mientras el lineup se
 * está escribiendo y no deberían intercalarse al azar.
 *
 * Un canal favorito aparece **dos veces**: arriba y en su grupo. Esconderlo de
 * su grupo obligaría a recordar que se marcó, y sorprende más de lo que ahorra.
 */
fun agruparCanales(
	channels: List<ChannelProfile>,
	groups: List<ChannelGroup>,
	favorites: Set<String>,
): List<ChannelSection> = buildList {
	val favoritos = channels.filter { it.id in favorites }
	if (favoritos.isNotEmpty()) {
		add(ChannelSection("__favoritos", "Favoritos", favoritos))
	}

	groups.forEach { grupo ->
		val suyos = channels.filter { it.group == grupo.id }
		if (suyos.isNotEmpty()) add(ChannelSection(grupo.id, grupo.name, suyos))
	}

	val sueltos = channels.filter { it.group == null }
	if (sueltos.isNotEmpty()) {
		// Si no hay grupos en absoluto, no tiene sentido llamarlos «otros»:
		// son, sencillamente, los canales.
		val titulo = if (groups.isEmpty() && favoritos.isEmpty()) "Canales" else "Otros"
		add(ChannelSection("__sueltos", titulo, sueltos))
	}
}

/**
 * La guía: una fila por sección, con desplazamiento vertical y horizontal.
 *
 * Las dos direcciones desplazan a lo que tiene el foco. Con ~120 canales, el
 * foco llega constantemente a tarjetas fuera de pantalla, y una lista que no
 * sigue al foco es una lista donde la mitad del lineup no existe.
 */
@Composable
fun LiveTvChannelGrid(
	sections: List<ChannelSection>,
	favorites: Set<String>,
	onChannelClick: (ChannelProfile) -> Unit,
	onChannelLongClick: (ChannelProfile) -> Unit,
	firstChannelFocus: FocusRequester,
	modifier: Modifier = Modifier,
) {
	val verticalState = rememberLazyListState()
	val scope = rememberCoroutineScope()

	LazyColumn(
		state = verticalState,
		modifier = modifier.focusGroup(),
		verticalArrangement = Arrangement.spacedBy(18.dp),
	) {
		itemsIndexed(sections, key = { _, s -> s.id }) { indiceSeccion, seccion ->
			Column(
				modifier = Modifier.onFocusChanged {
					if (it.hasFocus) scope.launch { verticalState.animateScrollToItem(indiceSeccion) }
				}
			) {
				Text(
					text = seccion.title,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
				)

				val horizontalState = rememberLazyListState()
				LazyRow(
					state = horizontalState,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					itemsIndexed(seccion.channels, key = { _, c -> c.id }) { indice, canal ->
						ChannelCard(
							channel = canal,
							favorite = canal.id in favorites,
							onClick = { onChannelClick(canal) },
							onLongClick = { onChannelLongClick(canal) },
							modifier = Modifier
								.onFocusChanged {
									if (it.isFocused) {
										scope.launch { horizontalState.animateScrollToItem(indice) }
									}
								}
								.then(
									// Solo la primera tarjeta de la primera
									// sección recibe el foco al entrar.
									if (indiceSeccion == 0 && indice == 0) {
										Modifier.focusRequester(firstChannelFocus)
									} else {
										Modifier
									}
								),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ChannelCard(
	channel: ChannelProfile,
	favorite: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Button(
		onClick = onClick,
		modifier = modifier.width(150.dp).height(96.dp),
		contentPadding = PaddingValues(10.dp),
	) {
		Column(modifier = Modifier.fillMaxWidth()) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				channel.number?.let { numero ->
					// El número en monoespaciada para que la columna quede
					// alineada entre tarjetas y se pueda leer de un vistazo.
					Text(
						text = numero.toString(),
						fontFamily = FontFamily.Monospace,
						fontWeight = FontWeight.Bold,
						fontSize = 13.sp,
					)
					Spacer(modifier = Modifier.width(8.dp))
				}
				if (favorite) Text(text = "★", fontSize = 13.sp)
			}
			Spacer(modifier = Modifier.height(4.dp))
			Text(text = channel.name, fontWeight = FontWeight.Medium)
		}
	}
}

/**
 * Lo que se ve al teclear un número con el mando.
 *
 * Aparece sobre la guía y desaparece sola. Sin ella, marcar un número es un
 * acto de fe: no hay forma de saber si la caja está recibiendo las pulsaciones
 * hasta que cambia el canal, o hasta que no cambia.
 */
@Composable
fun NumberEntryOverlay(digits: String, resolved: String?, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier
			.background(Color.Black.copy(alpha = 0.82f))
			.padding(horizontal = 28.dp, vertical = 18.dp)
	) {
		Text(
			text = digits,
			fontFamily = FontFamily.Monospace,
			fontWeight = FontWeight.Bold,
			fontSize = 44.sp,
			color = Color.White,
		)
		Text(
			text = resolved ?: "sin canal con ese número",
			color = if (resolved != null) Color(0xFFBDBDBD) else Color(0xFFFFB300),
			fontSize = 14.sp,
		)
	}
}
