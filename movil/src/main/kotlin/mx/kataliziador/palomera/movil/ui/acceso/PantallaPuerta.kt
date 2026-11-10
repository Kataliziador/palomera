package mx.kataliziador.palomera.movil.ui.acceso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.kataliziador.palomera.movil.acceso.Puerta
import mx.kataliziador.palomera.movil.acceso.Sesion
import mx.kataliziador.palomera.movil.acceso.normalizarDireccion
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import org.jellyfin.livetv.config.RespuestaProveedor

/**
 * La puerta entera: de no saber nada a tener una sesión.
 *
 * Tres paradas y **ninguna es obligatoria para todos**: quien es reconocido
 * por su red pasa de la primera a la tercera sin teclear nada, y quien sabe su
 * dirección puede saltarse el registro por completo.
 *
 * ```
 * Proveedor ──┬── (reconocido o código) ──→ Activación ──→ Sesión
 *             └── «escribir la dirección» ──→ Dirección ──┘
 * ```
 */
@Composable
fun PantallaPuerta(
	acento: Color,
	preguntarAlRegistro: suspend (codigo: String?) -> RespuestaProveedor,
	construirPuerta: (servidor: String) -> Puerta,
	alEntrar: (Sesion) -> Unit,
) {
	// `null` = todavía en el selector. Con valor = ya hay a dónde ir.
	var destino by remember { mutableStateOf<Pair<String, String>?>(null) }
	var escribiendoDireccion by remember { mutableStateOf(false) }

	val d = destino
	when {
		d != null -> PantallaActivacion(
			proveedor = d.second,
			servidor = d.first,
			acento = acento,
			puerta = construirPuerta(d.first),
			alEntrar = { dentro ->
				alEntrar(
					Sesion(
						servidor = d.first,
						token = dentro.token,
						usuarioId = dentro.usuarioId,
						nombre = dentro.nombre,
						proveedor = d.second,
						servidorId = dentro.servidorId.takeIf { it.isNotBlank() },
					),
				)
			},
			alVolver = { destino = null; escribiendoDireccion = false },
		)

		escribiendoDireccion -> PantallaDireccion(
			acento = acento,
			construirPuerta = construirPuerta,
			alAceptar = { url, nombre -> destino = url to nombre },
			alVolver = { escribiendoDireccion = false },
		)

		else -> PantallaProveedor(
			acento = acento,
			preguntarAlRegistro = preguntarAlRegistro,
			alElegir = { p -> destino = p.serverUrl to p.nombre },
			alEscribirDireccion = { escribiendoDireccion = true },
		)
	}
}

/**
 * La ruta 3: escribir la dirección.
 *
 * **Comprueba que hay un servidor antes de seguir.** Una dirección mal escrita
 * tiene que decirlo aquí, no después de que el abonado teclee su usuario y su
 * contraseña en un teléfono — ahí el fallo parece de sus datos y no de la
 * dirección, y vuelve a escribirlos.
 */
@Composable
private fun PantallaDireccion(
	acento: Color,
	construirPuerta: (String) -> Puerta,
	alAceptar: (url: String, nombre: String) -> Unit,
	alVolver: () -> Unit,
) {
	var escrito by remember { mutableStateOf("") }
	var aviso by remember { mutableStateOf<String?>(null) }
	var comprobando by remember { mutableStateOf(false) }
	val alcance = rememberCoroutineScope()

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.imePadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp),
	) {
		Spacer(Modifier.height(56.dp))
		BasicText(
			"Escribe la dirección",
			style = Tipografia.cabecera.copy(color = Paleta.tinta),
		)
		Spacer(Modifier.height(10.dp))
		BasicText(
			"Si tu proveedor te dio la dirección de su servidor, escríbela aquí. " +
				"Es la opción para quien ya la conoce.",
			style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
		)
		Spacer(Modifier.height(24.dp))

		Campo(
			etiqueta = "Dirección del servidor",
			valor = escrito,
			acento = acento,
			teclado = KeyboardType.Uri,
			ayuda = "Por ejemplo: 192.168.1.10 o tv.miproveedor.mx",
		) { escrito = it; aviso = null }

		Spacer(Modifier.height(16.dp))
		aviso?.let { Aviso(it); Spacer(Modifier.height(16.dp)) }

		BotonPrincipal(
			if (comprobando) "Comprobando…" else "Continuar",
			acento,
			habilitado = escrito.isNotBlank() && !comprobando,
		) {
			val url = normalizarDireccion(escrito)
			if (url == null) {
				aviso = "Eso no parece una dirección. Prueba con algo como 192.168.1.10"
				return@BotonPrincipal
			}
			comprobando = true
			alcance.launch {
				val nombre = construirPuerta(url).hayServidor()
				if (nombre == null) {
					aviso = "No encontramos un servidor en $url. Comprueba la dirección " +
						"y que estás en la red de tu proveedor."
				} else {
					alAceptar(url, nombre)
				}
				comprobando = false
			}
		}
		Spacer(Modifier.height(4.dp))
		Salida("Volver") { alVolver() }
		Spacer(Modifier.height(40.dp))
	}
}

/** Lo que se enseña mientras se entra con una sesión ya guardada. */
@Composable
fun PantallaEntrando() {
	Column(
		Modifier.fillMaxSize().background(Paleta.fondo).padding(32.dp),
	) {
		Spacer(Modifier.height(120.dp))
		BasicText("Palomera", style = Tipografia.titulo.copy(color = Paleta.tintaSutil))
	}
}
