package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text

/**
 * La búsqueda de canales de la sección.
 *
 * **El problema real no es filtrar, es teclear con un mando.** De ahí las
 * decisiones:
 *
 * - **Una sola línea, siempre visible mientras se busca.** Nada de pantalla
 *   aparte: el abonado tiene que ver cómo la guía se reduce mientras escribe, o
 *   no sabe si va bien encaminado.
 * - **Se abre con el teclado del sistema ya levantado.** En una caja de TV ese
 *   teclado es también el camino al micrófono del mando, que es como se busca
 *   de verdad en un sofá. Pedirlo a mano sería un paso más en la única parte
 *   incómoda de la interacción.
 * - **ABAJO lleva a los resultados.** Es el gesto natural y hay que forzarlo:
 *   el campo de texto se queda las teclas de dirección aunque sea de una sola
 *   línea.
 * - **ATRÁS baja el teclado la primera vez y cierra la búsqueda la segunda.**
 *   Nunca sale de la sección. El teclado del sistema tapa media guía, así que
 *   si la primera pulsación cerrara la búsqueda entera **no habría forma de
 *   llegar a los resultados**: se verían y no se podrían tocar.
 * - **El acento del ISP marca el foco**, no el relleno: sobre el negro de la
 *   sección un recuadro de color compite con las tarjetas, y aquí el color solo
 *   tiene que decir «estás escribiendo aquí».
 */
@Composable
fun BarraDeBusqueda(
	consulta: String,
	onConsultaChange: (String) -> Unit,
	onCerrar: () -> Unit,
	/** ABAJO: el campo cede el foco a los resultados. */
	onBajar: () -> Unit,
	acento: Color,
	modifier: Modifier = Modifier,
) {
	val foco = remember { FocusRequester() }
	val teclado = LocalSoftwareKeyboardController.current
	var enfocado by remember { mutableStateOf(false) }

	// Se lleva a mano y no se consulta al sistema: lo que hace falta saber no
	// es si el teclado está en pantalla, sino **si esta pantalla lo pidió**, que
	// es lo que decide qué hace ATRÁS.
	var tecladoPedido by remember { mutableStateOf(true) }

	LaunchedEffect(Unit) {
		foco.requestFocus()
		teclado?.show()
	}

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(ShapeDefaults.Small)
			.background(Color.White.copy(alpha = 0.06f))
			.border(
				width = 2.dp,
				color = if (enfocado) acento else Color.Transparent,
				shape = ShapeDefaults.Small,
			)
			.padding(horizontal = 18.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_search),
			contentDescription = null,
			tint = Color(0xFF9E9E9E),
			modifier = Modifier.size(22.dp),
		)

		Spacer(modifier = Modifier.width(14.dp))

		Box(modifier = Modifier.fillMaxWidth()) {
			if (consulta.isEmpty()) {
				// La pista dice **por qué** sirve el campo, no «Buscar…». Que
				// los alias funcionen es invisible si nadie los menciona, y es
				// justo lo que hace que «canal 2» encuentre «Estrellas HD».
				Text(
					text = "Nombre o número del canal",
					color = Color(0xFF6E7278),
					fontSize = 20.sp,
				)
			}

			BasicTextField(
				value = consulta,
				onValueChange = onConsultaChange,
				singleLine = true,
				textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
				cursorBrush = SolidColor(acento),
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				keyboardActions = KeyboardActions(onSearch = {
					teclado?.hide()
					tecladoPedido = false
				}),
				modifier = Modifier
					.fillMaxWidth()
					.focusRequester(foco)
					.onFocusChanged { enfocado = it.isFocused }
					// `onPreviewKeyEvent` y no `onKeyEvent`: el campo de texto
					// consume ATRÁS para cerrar el teclado, y sin interceptarlo
					// antes la primera pulsación no llegaría nunca aquí.
					.onPreviewKeyEvent { evento ->
						if (evento.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
						when (evento.key) {
							Key.Back -> {
								if (tecladoPedido) {
									teclado?.hide()
									tecladoPedido = false
								} else {
									onCerrar()
								}
								true
							}
							// Con el teclado bajado, el campo sigue enfocado y
							// OK tiene que devolverlo: es la tecla que cualquiera
							// pulsa sobre un campo de texto para escribir en él.
							Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
								if (!tecladoPedido) {
									teclado?.show()
									tecladoPedido = true
									true
								} else {
									false
								}
							}
							// **ABAJO tiene que salir del campo, y hay que
							// hacerlo a mano.** `BasicTextField` se queda las
							// teclas de dirección para mover el cursor, incluso
							// siendo de una sola línea: sin esto los resultados
							// se ven y no se pueden tocar, que es peor que no
							// tener búsqueda.
							Key.DirectionDown -> {
								if (tecladoPedido) {
									teclado?.hide()
									tecladoPedido = false
								}
								onBajar()
								true
							}
							else -> false
						}
					},
			)
		}
	}
}
