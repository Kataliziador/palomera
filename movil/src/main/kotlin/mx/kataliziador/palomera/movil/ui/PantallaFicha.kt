package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.catalogo.Detalle
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.tiempoRestante
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * La ficha de un título.
 *
 * **Abre en lo que toca hacer**, no en «Reproducir»: si el abonado lo dejó a
 * medias, el botón dice cuánto falta. Es la misma regla que la ficha de serie
 * (`docs/diseno-movil.md` §9), y sale de que lo que decide si lo pones ahora es
 * el tiempo, no el porcentaje.
 *
 * **No lleva barra inferior.** Una ficha es un destino, no una sección: se
 * abre encima y se sale con atrás.
 */
@Composable
fun PantallaFicha(jf: Jellyfin, id: String, acento: Color, alVolver: () -> Unit) {
	val detalle by produceState<Detalle?>(initialValue = null, id) { value = jf.detalle(id) }

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			// **La ficha pone sus propios márgenes del sistema.** Se dibuja
			// *antes* que el contenedor de secciones —una ficha es un destino
			// y se abre encima—, así que no hereda los suyos. Sin esto, la
			// flecha de volver se pinta sobre el reloj; se vio en el teléfono
			// el 09-nov, y es el mismo defecto de borde a borde que ya mordió
			// en la pantalla de capacidades.
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.verticalScroll(rememberScrollState()),
	) {
		// El arte iría aquí. Sin él, una superficie lisa: el sistema de diseño
		// es explícito en que un hueco sin arte no se rellena con ilustración.
		Box(Modifier.fillMaxWidth().height(200.dp).background(Paleta.mosaico)) {
			Box(
				Modifier
					.padding(6.dp, 8.dp)
					.size(BLANCO_TACTIL_DP.dp)
					.clickable(onClick = alVolver),
				contentAlignment = Alignment.Center,
			) {
				BasicText("‹", style = Tipografia.titulo.copy(color = Paleta.tinta))
			}
		}

		val d = detalle
		if (d == null) {
			BasicText(
				"Cargando…",
				modifier = Modifier.padding(16.dp),
				style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
			)
			return@Column
		}

		Column(
			Modifier.padding(16.dp, 14.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			BasicText(d.nombre, style = Tipografia.cabecera.copy(color = Paleta.tinta))

			BasicText(
				listOfNotNull(
					d.anio?.toString(),
					d.minutos.takeIf { it > 0 }?.let { duracion(it) },
					d.clasificacion,
					d.generos.firstOrNull(),
				).joinToString("  ·  "),
				style = Tipografia.detalle.copy(color = Paleta.tintaSutil),
			)

			// El botón dice qué pasa al tocarlo, no cómo se llama la acción.
			val empezado = d.minutosVistos > 0
			Box(
				Modifier
					.fillMaxWidth()
					.heightIn(min = 48.dp)
					.clip(RoundedCornerShape(24.dp))
					.background(acento)
					.clickable { }
					.padding(vertical = 14.dp),
				contentAlignment = Alignment.Center,
			) {
				BasicText(
					if (empezado) tiempoRestante(d.minutos - d.minutosVistos).replace("Quedan", "Reanudar ·")
					else "Reproducir",
					style = Tipografia.cuerpo.copy(color = Paleta.fondo),
				)
			}

			d.sinopsis?.let {
				BasicText(it, style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada))
			}

			// Lo que un abonado consulta antes de darle: en qué idioma lo va a oír.
			Dato("Audio", d.audios.joinToString(", ").ifBlank { "Una sola pista" })
			Dato("Subtítulos", d.subtitulos.joinToString(", ").ifBlank { "No trae" })
		}
	}
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		Box(Modifier.width(84.dp)) {
			BasicText(etiqueta, style = Tipografia.tarjeta.copy(color = Paleta.tintaSutil))
		}
		BasicText(valor, style = Tipografia.tarjeta.copy(color = Paleta.tintaApagada))
	}
	Spacer(Modifier.height(0.dp))
}

private fun duracion(minutos: Int): String =
	if (minutos < 60) "$minutos min" else "${minutos / 60} h ${minutos % 60} min"
