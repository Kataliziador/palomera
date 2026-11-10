package mx.kataliziador.palomera.movil.ui.acceso

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * Las piezas de la puerta.
 *
 * Están aparte porque las tres pantallas de acceso usan las mismas y porque
 * **son las únicas de toda la app que alguien va a usar con el pulgar y con
 * prisa**, estrenando el servicio. Todo lo que se toca mide [BLANCO_TACTIL_DP].
 */

/** El botón que hace la acción principal de la pantalla. Uno por pantalla. */
@Composable
fun BotonPrincipal(
	texto: String,
	acento: Color,
	habilitado: Boolean = true,
	alPulsar: () -> Unit,
) {
	Box(
		Modifier
			.fillMaxWidth()
			.heightIn(min = BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if (habilitado) acento else Paleta.mosaico)
			.then(if (habilitado) Modifier.clickable(onClick = alPulsar) else Modifier)
			.padding(horizontal = 18.dp, vertical = 13.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(
			texto,
			style = Tipografia.seccion.copy(
				color = if (habilitado) Paleta.fondo else Paleta.tintaSutil,
			),
		)
	}
}

/**
 * La salida secundaria.
 *
 * Nunca es un botón de verdad: en cada pantalla de la puerta hay **una** acción
 * principal, y lo demás son caminos de escape que no deben competir con ella.
 */
@Composable
fun Salida(texto: String, alPulsar: () -> Unit) {
	Box(
		Modifier
			.fillMaxWidth()
			.heightIn(min = BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(10.dp))
			.clickable(onClick = alPulsar)
			.padding(horizontal = 18.dp, vertical = 13.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(texto, style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada))
	}
}

/**
 * Un campo de texto.
 *
 * Lleva su propia etiqueta encima y **no usa un marcador de posición como
 * etiqueta**: al escribir desaparece, y quien se distrae a media frase ya no
 * sabe qué le estaban pidiendo.
 */
@Composable
fun Campo(
	etiqueta: String,
	valor: String,
	acento: Color,
	esClave: Boolean = false,
	teclado: KeyboardType = KeyboardType.Text,
	ayuda: String? = null,
	alCambiar: (String) -> Unit,
) {
	BasicText(
		etiqueta,
		modifier = Modifier.padding(bottom = 6.dp),
		style = Tipografia.detalle.copy(color = Paleta.tintaSutil),
	)
	Box(
		Modifier
			.fillMaxWidth()
			.heightIn(min = BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(Paleta.mosaico)
			.border(1.dp, Paleta.elevado, RoundedCornerShape(10.dp))
			.padding(horizontal = 14.dp, vertical = 12.dp),
		contentAlignment = Alignment.CenterStart,
	) {
		BasicTextField(
			value = valor,
			onValueChange = alCambiar,
			singleLine = true,
			cursorBrush = SolidColor(acento),
			textStyle = Tipografia.cuerpo.copy(color = Paleta.tinta),
			keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = ImeAction.Done),
			visualTransformation =
				if (esClave) PasswordVisualTransformation() else VisualTransformation.None,
			modifier = Modifier.fillMaxWidth(),
		)
	}
	ayuda?.let {
		BasicText(
			it,
			modifier = Modifier.padding(top = 6.dp),
			style = Tipografia.etiqueta.copy(color = Paleta.tintaSutil),
		)
	}
}

/**
 * Lo que ha salido mal, dicho para quien lo lee.
 *
 * **Cada mensaje dice qué hacer**, no sólo qué pasó. Un error que no lleva a
 * una acción es una llamada a soporte (`CLAUDE.md` §2).
 */
@Composable
fun Aviso(texto: String) {
	Box(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.background(Paleta.mosaico)
			.padding(14.dp),
	) {
		BasicText(texto, style = Tipografia.cuerpo.copy(color = Paleta.aviso))
	}
}
