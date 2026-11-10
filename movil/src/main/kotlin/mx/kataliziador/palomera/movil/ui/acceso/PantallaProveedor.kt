package mx.kataliziador.palomera.movil.ui.acceso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.launch
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import org.jellyfin.livetv.config.Proveedor
import org.jellyfin.livetv.config.RespuestaProveedor

/**
 * La primera pantalla de toda instalación: **quién te da el servicio**.
 *
 * ## Por qué no pide una dirección
 *
 * Porque lo que habría que escribir es `http://192.168.1.10:8096`, y ningún
 * abonado sabe eso (`docs/selector-de-proveedor.md` §1). La cascada es la
 * misma que en la caja y por el mismo motivo — ninguna de las tres cubre todos
 * los casos y la última nunca puede fallar:
 *
 * | | cuándo entra | a quién sirve |
 * |---|---|---|
 * | **1. por la red** | el registro reconoce desde dónde se llama | el abonado en su casa. Cero teclado |
 * | **2. por código** | no la reconoce | probadores, datos móviles, el ISP bajo CGNAT |
 * | **3. la dirección** | **siempre disponible** | sin internet, y el revisor de Google |
 *
 * **La 3 no se quita nunca.** Es la única que funciona sin llegar a internet,
 * y sin ella la app se queda sin camino de alta el día que el registro calle.
 *
 * ## Y «no te conozco» no es lo mismo que «no pude preguntar»
 *
 * Son dos pantallas distintas a propósito. Decirle «tu proveedor no existe» a
 * quien simplemente está sin internet lo manda a buscar un dato que ya tiene.
 */
@Composable
fun PantallaProveedor(
	acento: Color,
	preguntarAlRegistro: suspend (codigo: String?) -> RespuestaProveedor,
	alElegir: (Proveedor) -> Unit,
	alEscribirDireccion: () -> Unit,
) {
	var estado by remember { mutableStateOf<RespuestaProveedor?>(null) }
	var codigo by remember { mutableStateOf("") }
	var preguntando by remember { mutableStateOf(true) }
	val alcance = rememberCoroutineScope()

	// Al abrir se pregunta **sin código**: si la red del abonado es conocida,
	// no tiene que teclear nada.
	LaunchedEffect(Unit) {
		estado = preguntarAlRegistro(null)
		preguntando = false
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
		Spacer(Modifier.height(48.dp))
		Marca(acento)
		Spacer(Modifier.height(28.dp))

		val encontrado = (estado as? RespuestaProveedor.Encontrado)?.proveedor

		when {
			preguntando -> {
				BasicText(
					"Buscando tu proveedor…",
					style = Tipografia.titulo.copy(color = Paleta.tinta),
				)
			}

			// Ruta 1: la red lo delató. Cero teclado.
			encontrado != null -> {
				BasicText(
					"¿Eres cliente de ${encontrado.nombre}?",
					style = Tipografia.cabecera.copy(color = Paleta.tinta),
				)
				Spacer(Modifier.height(10.dp))
				BasicText(
					"Reconocimos tu red. Si es correcto, continúa.",
					style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
				)
				Spacer(Modifier.height(24.dp))
				BotonPrincipal("Sí, continuar", acento) { alElegir(encontrado) }
				Spacer(Modifier.height(4.dp))
				Salida("No, tengo otro proveedor") { estado = RespuestaProveedor.Ninguno }
			}

			// Ruta 2: el código que le dio su proveedor.
			else -> {
				BasicText("¿Quién te da el servicio?", style = Tipografia.cabecera.copy(color = Paleta.tinta))
				Spacer(Modifier.height(10.dp))
				BasicText(
					textoDeAyuda(estado),
					style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
				)
				Spacer(Modifier.height(24.dp))

				Campo(
					etiqueta = "Código de tu proveedor",
					valor = codigo,
					acento = acento,
					teclado = KeyboardType.Ascii,
					ayuda = "Viene en tu contrato o te lo dicta tu proveedor por teléfono.",
				) { codigo = it.uppercase().trim() }

				Spacer(Modifier.height(16.dp))
				BotonPrincipal("Continuar", acento, habilitado = codigo.isNotBlank()) {
					preguntando = true
					alcance.launch {
						val r = preguntarAlRegistro(codigo)
						// Un código bueno entra directo; uno malo deja la
						// pantalla donde está, con su propio mensaje.
						(r as? RespuestaProveedor.Encontrado)?.let { alElegir(it.proveedor) }
						estado = r
						preguntando = false
					}
				}
				Spacer(Modifier.height(4.dp))
				// **La ruta 3, siempre visible.** No es un último recurso
				// escondido: es la única que funciona sin internet.
				Salida("Escribir la dirección del servidor") { alEscribirDireccion() }
			}
		}

		Spacer(Modifier.height(40.dp))
	}
}

/**
 * El texto cambia con lo que pasó, y los tres casos llevan a acciones
 * distintas. Uno solo —«no se pudo»— los haría indistinguibles.
 */
private fun textoDeAyuda(estado: RespuestaProveedor?): String = when (estado) {
	RespuestaProveedor.NoSeSupo ->
		"No pudimos comprobarlo ahora mismo. Si tienes el código de tu proveedor, " +
			"escríbelo; si no, puedes escribir la dirección del servidor."

	RespuestaProveedor.DemasiadosIntentos ->
		"Demasiados intentos seguidos. Espera un minuto y vuelve a probar: " +
			"el código puede ser correcto."

	else ->
		"No reconocimos tu red, así que escribe el código que te dio tu proveedor."
}

/** La marca neutra: lo que ve quien aún no ha entrado (`CLAUDE.md` §8). */
@Composable
private fun Marca(acento: Color) {
	Box(
		Modifier
			.size(52.dp)
			.clip(RoundedCornerShape(13.dp))
			.background(acento),
		contentAlignment = Alignment.Center,
	) {
		BasicText("P", style = Tipografia.cabecera.copy(color = Paleta.fondo))
	}
	Spacer(Modifier.height(14.dp))
	BasicText("Palomera", style = Tipografia.titulo.copy(color = Paleta.tinta))
}
