package mx.kataliziador.palomera.movil.ui.acceso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.kataliziador.palomera.movil.acceso.CodigoDeActivacion
import mx.kataliziador.palomera.movil.acceso.Entrada
import mx.kataliziador.palomera.movil.acceso.Puerta
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * Donde el abonado entra de verdad.
 *
 * ## Dos caminos, y los dos se quedan
 *
 * **El código** es el normal: el abonado llama a su proveedor, le dicta seis
 * cifras y el proveedor las autoriza desde el panel de abonados. No tiene que
 * saber que existe un usuario ni una contraseña.
 *
 * **La contraseña no se quita** (`CLAUDE.md` §8). Mientras un site no tenga el
 * panel instalado es el único camino de alta que existe, y es también por
 * donde entra el revisor de Google (`PLAY.md` §6).
 *
 * ## Y la pantalla no dice «inicia sesión»
 *
 * Quien estrena esto no está iniciando sesión: está estrenando un aparato y no
 * sabe que tiene una cuenta. El mismo cambio se hizo en la caja el 27-oct.
 */
@Composable
fun PantallaActivacion(
	proveedor: String,
	servidor: String,
	acento: Color,
	puerta: Puerta,
	alEntrar: (Entrada.Adentro) -> Unit,
	alVolver: () -> Unit,
) {
	var conClave by remember { mutableStateOf(false) }
	var codigo by remember { mutableStateOf<CodigoDeActivacion?>(null) }
	var admiteCodigo by remember { mutableStateOf<Boolean?>(null) }
	var usuario by remember { mutableStateOf("") }
	var clave by remember { mutableStateOf("") }
	var aviso by remember { mutableStateOf<String?>(null) }
	var entrando by remember { mutableStateOf(false) }
	val alcance = rememberCoroutineScope()

	// ¿Este site ofrece el camino del código? Si no, se va directo al otro:
	// enseñar un código que nadie puede autorizar es peor que no ofrecerlo.
	LaunchedEffect(servidor) {
		val hay = puerta.admiteCodigo()
		admiteCodigo = hay
		if (!hay) conClave = true else codigo = puerta.pedirCodigo()
		if (hay && codigo == null) {
			conClave = true
			aviso = "No pudimos pedir un código de activación. Entra con tu usuario y contraseña."
		}
	}

	// Mientras el código está en pantalla, se pregunta si ya lo autorizaron.
	// Cada 3 s: más a menudo castiga al servidor del ISP sin que nadie teclee
	// más rápido, y menos deja al abonado mirando una pantalla quieta después
	// de que su proveedor ya le dijo «listo».
	LaunchedEffect(codigo?.secreto, conClave) {
		val secreto = codigo?.secreto ?: return@LaunchedEffect
		if (conClave) return@LaunchedEffect
		while (true) {
			delay(3_000)
			if (puerta.yaAutorizado(secreto)) {
				when (val e = puerta.entrarConCodigo(secreto)) {
					is Entrada.Adentro -> { alEntrar(e); return@LaunchedEffect }
					else -> aviso = mensaje(e)
				}
			}
		}
	}

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.imePadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp),
	) {
		Spacer(Modifier.height(40.dp))
		BasicText(proveedor, style = Tipografia.detalle.copy(color = acento))
		Spacer(Modifier.height(6.dp))
		BasicText(
			if (conClave) "Entra con tu cuenta" else "Activa este teléfono",
			style = Tipografia.cabecera.copy(color = Paleta.tinta),
		)
		Spacer(Modifier.height(10.dp))

		if (conClave) {
			BasicText(
				"Si tu proveedor te dio un usuario y una contraseña, escríbelos aquí.",
				style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
			)
			Spacer(Modifier.height(24.dp))

			Campo("Usuario", usuario, acento) { usuario = it.trim(); aviso = null }
			Spacer(Modifier.height(16.dp))
			Campo("Contraseña", clave, acento, esClave = true) { clave = it; aviso = null }
			Spacer(Modifier.height(16.dp))

			aviso?.let { Aviso(it); Spacer(Modifier.height(16.dp)) }

			BotonPrincipal(
				if (entrando) "Entrando…" else "Entrar",
				acento,
				habilitado = usuario.isNotBlank() && !entrando,
			) {
				entrando = true
				aviso = null
				alcance.launch {
					when (val e = puerta.entrar(usuario, clave)) {
						is Entrada.Adentro -> alEntrar(e)
						else -> aviso = mensaje(e)
					}
					entrando = false
				}
			}
			Spacer(Modifier.height(4.dp))
			if (admiteCodigo == true) {
				Salida("Prefiero activar con un código") { conClave = false; aviso = null }
			}
		} else {
			BasicText(
				"Llama a $proveedor, dile que quieres activar este teléfono y dale este número.",
				style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
			)
			Spacer(Modifier.height(28.dp))

			CodigoGrande(codigo?.codigo, acento)

			Spacer(Modifier.height(20.dp))
			BasicText(
				"Esta pantalla continúa sola en cuanto lo autoricen.",
				style = Tipografia.detalle.copy(color = Paleta.tintaSutil),
			)

			Spacer(Modifier.height(24.dp))
			aviso?.let { Aviso(it); Spacer(Modifier.height(16.dp)) }

			// §8: no se quita. Es el único camino en un site sin panel, y es
			// por donde entra el revisor de Google.
			Salida("Usar usuario y contraseña") { conClave = true; aviso = null }
		}

		Salida("Cambiar de proveedor") { alVolver() }
		Spacer(Modifier.height(40.dp))
	}
}

/**
 * El código, en grande y espaciado.
 *
 * Se va a leer en voz alta por teléfono, así que lo que importa no es que
 * quepa sino que **no se confunda una cifra**: de ahí el tamaño y el aire
 * entre caracteres.
 */
@Composable
private fun CodigoGrande(codigo: String?, acento: Color) {
	Box(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.background(Paleta.mosaico)
			.padding(vertical = 26.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(
			codigo?.chunked(3)?.joinToString(" ") ?: "· · · · · ·",
			style = Tipografia.cabecera.copy(
				color = if (codigo == null) Paleta.tintaSutil else acento,
				fontSize = 40.sp,
				letterSpacing = 4.sp,
			),
		)
	}
}

/**
 * Qué se le dice al abonado, y **cada caso lleva a una acción distinta**.
 *
 * Es la tabla de [Entrada] hecha texto. Un solo mensaje —«no se pudo entrar»—
 * mandaría al suspendido a pedir un código y al que está sin internet a
 * reescribir una contraseña que estaba bien.
 */
private fun mensaje(e: Entrada): String = when (e) {
	Entrada.ClaveEquivocada ->
		"Ese usuario o esa contraseña no son correctos. Revísalos y vuelve a intentarlo."

	Entrada.Suspendido ->
		"Tu servicio está suspendido. Llama a tu proveedor para reactivarlo: " +
			"un código nuevo no lo soluciona."

	Entrada.NoContesta ->
		"No pudimos contactar con tu proveedor. Comprueba que tienes conexión " +
			"y vuelve a intentarlo."

	is Entrada.Adentro -> ""
}
