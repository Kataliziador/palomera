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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
	/**
	 * El acento de la marca del proveedor, ya validado contra el fondo.
	 *
	 * Los títulos de grupo son el único sitio de la guía donde el color del ISP
	 * cabe sin competir con los canales, que son lo que la gente viene a ver.
	 */
	accent: Color,
	onChannelClick: (ChannelProfile) -> Unit,
	onChannelLongClick: (ChannelProfile) -> Unit,
	/**
	 * El canal que debe recibir el foco al entrar: el último visto, o el que el
	 * ISP puso por defecto. `null` lo deja en la primera tarjeta.
	 */
	idConFoco: String?,
	/**
	 * Si la guía puede quedarse el foco al componerse.
	 *
	 * Es `false` mientras se busca: las secciones cambian **en cada tecla**, y
	 * sin esto la guía reclamaría el foco a media palabra y el abonado acabaría
	 * escribiendo en ninguna parte.
	 */
	tomarFoco: Boolean = true,
	/**
	 * Un contador que, al subir, manda el foco a [idConFoco] aunque
	 * [tomarFoco] sea `false`.
	 *
	 * Es lo que permite que la búsqueda ceda el foco mientras se escribe y lo
	 * recupere **cuando el abonado pulsa ABAJO**, y en la primera tarjeta: la
	 * navegación geométrica de Compose aterriza en la segunda, porque el campo
	 * de texto ocupa el ancho entero y su centro cae ahí.
	 */
	enfocar: Int = 0,
	modifier: Modifier = Modifier,
) {
	val verticalState = rememberLazyListState()
	val scope = rememberCoroutineScope()

	// Un canal favorito aparece dos veces —arriba y en su grupo— así que se
	// toma la **primera** aparición: es la de más arriba y la que menos hace
	// desplazarse a la guía.
	val posicionConFoco = remember(sections, idConFoco) {
		if (idConFoco == null) null
		else sections.asSequence()
			.mapIndexedNotNull { iSec, sec ->
				sec.channels.indexOfFirst { it.id == idConFoco }
					.takeIf { it >= 0 }
					?.let { iSec to it }
			}
			.firstOrNull()
	}

	// El foco lo pide **la guía**, no quien la dibuja, y después de desplazarse.
	//
	// `LazyColumn` no compone lo que está fuera de pantalla: si el canal que
	// debe recibir el foco está en una sección de más abajo, su tarjeta todavía
	// no existe y el `FocusRequester` no está atado a nada. Pedir el foco antes
	// de desplazarse no falla ruidosamente — simplemente no pasa nada, y el
	// foco se queda en la primera tarjeta como si no hubiera memoria.
	val focoInicial = remember { FocusRequester() }

	// La fila del canal con foco se maneja desde aquí. Las demás llevan su
	// propio estado, que no hace falta tocar.
	//
	// Hay **dos** desplazamientos, no uno: `LazyColumn` compone la sección y
	// `LazyRow` compone la tarjeta. Con seis canales las filas cabían enteras
	// en pantalla y el segundo nunca hizo falta; con 120 el canal 214 está a
	// quince tarjetas a la derecha, su tarjeta no existe, y pedir el foco no
	// hace nada — ni falla.
	val filaConFoco = rememberLazyListState()
	// **Se vuelve a pedir el foco en cada cambio de `sections`, y tiene que ser
	// así.** El catálogo llega en dos pasos —el de arranque o la caché primero,
	// el del servidor después— y al sustituirse las tarjetas el foco se pierde.
	// Se intentó tomarlo una sola vez y la sección se abría con el foco en la
	// barra de arriba. Lo que evita que un refresco por tiempo lo mueva no está
	// aquí: es que el catálogo no se re-emite si no cambió (`LiveTvCatalog`).
	LaunchedEffect(posicionConFoco, sections, enfocar) {
		if (sections.isEmpty()) return@LaunchedEffect
		posicionConFoco?.let { (seccion, indice) ->
			verticalState.scrollToItem(seccion)
			filaConFoco.scrollToItem(indice)
		}
		if (tomarFoco || enfocar > 0) runCatching { focoInicial.requestFocus() }
	}

	LazyColumn(
		state = verticalState,
		modifier = modifier.focusGroup(),
		verticalArrangement = Arrangement.spacedBy(26.dp),
	) {
		itemsIndexed(sections, key = { _, s -> s.id }) { indiceSeccion, seccion ->
			Column(
				modifier = Modifier.onFocusChanged {
					if (it.hasFocus) scope.launch { verticalState.animateScrollToItem(indiceSeccion) }
				}
			) {
				// Color explícito: el del tema está pensado para el fondo claro
				// de la app y sobre el negro de esta sección queda casi
				// invisible. Un título de grupo que no se lee convierte la
				// guía en una lista suelta. El acento llega ya validado contra
				// este fondo, así que no puede volver a pasar.
				Text(
					text = seccion.title,
					color = accent,
					fontWeight = FontWeight.Bold,
					fontSize = 17.sp,
					modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
				)

				val horizontalState =
					if (indiceSeccion == posicionConFoco?.first) filaConFoco
					else rememberLazyListState()
				LazyRow(
					state = horizontalState,
					horizontalArrangement = Arrangement.spacedBy(16.dp),
				) {
					itemsIndexed(seccion.channels, key = { _, c -> c.id }) { indice, canal ->
						LiveTvChannelCard(
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
									// El foco va al último canal visto; si no
									// hay o ya no está en el lineup, a la
									// primera tarjeta.
									if ((posicionConFoco ?: (0 to 0)) == (indiceSeccion to indice)) {
										Modifier.focusRequester(focoInicial)
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
