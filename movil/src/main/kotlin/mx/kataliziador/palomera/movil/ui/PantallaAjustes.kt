package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.BuildConfig
import mx.kataliziador.palomera.movil.acceso.Sesion
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * Ajustes, y sobre todo **los avisos legales**.
 *
 * ## Esto no es una pantalla opcional
 *
 * `CLAUDE.md` §8 la hace obligatoria y `PLAY.md` §5 explica por qué: es lo que
 * hace **legal** el envío a la tienda. Tres cosas tienen que estar aquí:
 *
 * 1. **«no afiliada al proyecto Jellyfin»** — la app no usa su nombre ni su
 *    logo como marca propia;
 * 2. **las licencias con su dirección** — la GPL-2.0 exige una oferta del
 *    código fuente, y una oferta sin dirección no es una oferta. Sale de
 *    `BuildConfig.CLIENT_REPOSITORY`, no escrita a mano, porque en la caja ya
 *    costó un mes de informes apuntando al repositorio equivocado;
 * 3. **de quién es el contenido** — una sola app sirve a varios ISPs y lo que
 *    se ve en ella es de cada uno. Es lo que un revisor de una app de
 *    televisión busca, y lo que le dice al abonado a quién reclamar.
 *
 * El texto es **el mismo que el de la caja**, palabra por palabra. Dos
 * redacciones del mismo aviso legal son dos cosas que mantener sincronizadas,
 * y la que se quede atrás será la que alguien lea.
 */
@Composable
fun PantallaAjustes(
	sesion: Sesion,
	acento: Color,
	idDeInstalacion: String,
	alCerrarSesion: () -> Unit,
	alVolver: () -> Unit,
) {
	var confirmando by remember { mutableStateOf(false) }

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.verticalScroll(rememberScrollState()),
	) {
		Row(
			Modifier.fillMaxWidth().padding(6.dp, 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				Modifier.size(BLANCO_TACTIL_DP.dp).clickable(onClick = alVolver),
				contentAlignment = Alignment.Center,
			) {
				BasicText("‹", style = Tipografia.titulo.copy(color = Paleta.tinta))
			}
			BasicText("Ajustes", style = Tipografia.titulo.copy(color = Paleta.tinta))
		}

		Column(
			Modifier.padding(horizontal = 20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Spacer(Modifier.height(4.dp))

			Apartado("Tu cuenta")
			Dato("Proveedor", sesion.proveedor ?: "Sin marca configurada")
			Dato("Usuario", sesion.nombre.ifBlank { "—" })
			// La dirección del site: es lo primero que pide quien da soporte.
			Dato("Servidor", sesion.servidor)

			Spacer(Modifier.height(4.dp))

			if (!confirmando) {
				Boton("Cerrar sesión", acento, peligro = true) { confirmando = true }
			} else {
				// **Se confirma, y el aviso dice la consecuencia.** Cerrar
				// sesión en un teléfono es un toque; volver a entrar exige
				// llamar al proveedor, que es bastante más.
				BasicText(
					"Vas a salir de tu cuenta. Para volver a entrar necesitarás " +
						"el código de tu proveedor o tu contraseña.",
					style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
				)
				Boton("Sí, cerrar sesión", acento, peligro = true, alPulsar = alCerrarSesion)
				Boton("Mejor no", acento) { confirmando = false }
			}

			Spacer(Modifier.height(12.dp))
			Apartado("Avisos legales")

			// §8: palabra por palabra lo mismo que la caja.
			Parrafo(
				"Esta aplicación no está afiliada al proyecto Jellyfin ni cuenta con " +
					"su respaldo.",
			)

			Apartado("De quién es el contenido")
			Parrafo(
				"Los canales y las películas los pone tu proveedor de internet, que es " +
					"el responsable de ese contenido y de sus derechos. Esta aplicación " +
					"no lo produce ni lo elige.",
			)

			Apartado("Licencias del software")
			Parrafo(
				"Derivada de jellyfin-androidtv, bajo GPL-2.0. Código fuente: " +
					BuildConfig.CLIENT_REPOSITORY,
			)

			Spacer(Modifier.height(12.dp))
			Apartado("Esta instalación")
			Dato("Versión", BuildConfig.VERSION_NAME)
			// Es lo que se le pide a un abonado para encontrar su caso en los
			// registros, y por eso está a la vista y se puede leer en voz alta.
			Dato("Identificador", idDeInstalacion)

			Spacer(Modifier.height(40.dp))
		}
	}
}

@Composable
private fun Apartado(texto: String) {
	BasicText(texto, style = Tipografia.seccion.copy(color = Paleta.tinta))
}

@Composable
private fun Parrafo(texto: String) {
	BasicText(texto, style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada))
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
	Column {
		BasicText(etiqueta, style = Tipografia.detalle.copy(color = Paleta.tintaSutil))
		Spacer(Modifier.height(2.dp))
		BasicText(valor, style = Tipografia.cuerpo.copy(color = Paleta.tinta))
	}
}

@Composable
private fun Boton(texto: String, acento: Color, peligro: Boolean = false, alPulsar: () -> Unit) {
	Box(
		Modifier
			.fillMaxWidth()
			.heightIn(min = BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if (peligro) Paleta.mosaico else Color.Transparent)
			.clickable(onClick = alPulsar)
			.padding(horizontal = 18.dp, vertical = 13.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(
			texto,
			style = Tipografia.cuerpo.copy(color = if (peligro) Paleta.aviso else acento),
		)
	}
}
