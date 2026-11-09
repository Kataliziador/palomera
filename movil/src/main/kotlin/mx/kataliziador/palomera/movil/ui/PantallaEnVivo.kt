package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import org.jellyfin.livetv.config.ConfigRepository
import org.jellyfin.livetv.config.LiveConfig
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Los canales del site, de verdad.
 *
 * **Usa el parser de la caja**, no uno propio: `ConfigRepository` y
 * `LiveConfigParser` viven en `:livetv` y los dos aparatos leen el mismo
 * `config.json` con el mismo código. Es para lo que se compartió el módulo
 * (`MOVIL_PLAN.md` §3), y significa que un canal mal formado se descarta igual
 * aquí que en la sala del abonado.
 *
 * **Lista vertical y no rejilla**, y sin arte: el XMLTV de los sites llega sin
 * imágenes, así que diseñar con carátula de programa sería diseñar para un caso
 * que no ocurre (`docs/diseno-movil.md` §5).
 */
@Composable
fun PantallaEnVivo(configUrl: String) {
	val contexto = LocalContext.current
	var grupoElegido by remember { mutableStateOf<String?>(null) }

	val carga by produceState<Carga>(initialValue = Carga.Cargando, configUrl) {
		val resultado = ConfigRepository(contexto).load(configUrl, claveDeCache = "demo")
		value = resultado.config?.let(Carga::Lista) ?: Carga.Vacia
	}

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			.windowInsetsPadding(WindowInsets.safeDrawing),
	) {
		BasicText(
			"TV en vivo",
			modifier = Modifier.padding(16.dp, 14.dp),
			style = Tipografia.titulo.copy(color = Paleta.tinta),
		)

		when (val estado = carga) {
			Carga.Cargando -> Aviso("Buscando los canales de tu proveedor…")

			Carga.Vacia -> Aviso(
				"Tu equipo no responde. Puede estar apagado o reiniciándose.",
			)

			is Carga.Lista -> {
				val config = estado.config
				val acento = Color(config.brand.accent)
				val canales = config.channels.filter {
					grupoElegido == null || it.group == grupoElegido
				}

				Grupos(config, grupoElegido, acento) { grupoElegido = it }

				BasicText(
					"${canales.size} canales",
					modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp),
					style = Tipografia.detalle.copy(color = Paleta.tintaSutil),
				)

				LazyColumn(Modifier.weight(1f)) {
					items(canales, key = { it.id }) { canal -> Renglon(canal) }
				}
			}
		}
	}
}

private sealed interface Carga {
	data object Cargando : Carga
	data object Vacia : Carga
	data class Lista(val config: LiveConfig) : Carga
}

@Composable
private fun Aviso(texto: String) {
	Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
		BasicText(texto, style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada))
	}
}

@Composable
private fun Grupos(
	config: LiveConfig,
	elegido: String?,
	acento: Color,
	alElegir: (String?) -> Unit,
) {
	LazyRow(
		contentPadding = PaddingValues(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.padding(bottom = 12.dp),
	) {
		item {
			Pastilla("Todos", elegido == null, acento) { alElegir(null) }
		}
		items(config.groups, key = { it.id }) { grupo ->
			Pastilla(grupo.name, elegido == grupo.id, acento) { alElegir(grupo.id) }
		}
	}
}

@Composable
private fun Pastilla(texto: String, activa: Boolean, acento: Color, alPulsar: () -> Unit) {
	Box(
		Modifier
			.heightIn(min = 36.dp)
			.clip(RoundedCornerShape(18.dp))
			.background(if (activa) acento else Paleta.mosaico)
			.clickable(onClick = alPulsar)
			.padding(horizontal = 14.dp, vertical = 9.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(
			texto,
			maxLines = 1,
			style = Tipografia.tarjeta.copy(
				color = if (activa) Paleta.fondo else Paleta.tintaApagada,
			),
		)
	}
}

@Composable
private fun Renglon(canal: ChannelProfile) {
	Row(
		Modifier
			.fillMaxWidth()
			.heightIn(min = BLANCO_TACTIL_DP.dp + 20.dp)
			.clickable { }
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		// **El número, no las iniciales.** Los 53 canales del demo vienen sin
		// logo, y el primer intento puso tres letras del nombre: «Azteca Uno»
		// y «Azteca 7» salían las dos como «AZT», que no distingue nada. El
		// número es único y además es lo que el abonado se sabe de memoria.
		// Cuando el site sirva `logoUrl`, aquí va el logo y esto es el
		// respaldo.
		Box(
			Modifier
				.size(48.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(Paleta.mosaico),
			contentAlignment = Alignment.Center,
		) {
			BasicText(
				canal.number?.toString() ?: "—",
				style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
			)
		}

		Column(Modifier.weight(1f)) {
			BasicText(
				canal.name,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				style = Tipografia.cuerpo.copy(color = Paleta.tinta),
			)
			Spacer(Modifier.height(4.dp))
			// Todavía sin guía: cuando la haya, aquí va el programa de ahora,
			// que es lo que de verdad decide si te quedas en este canal.
			BasicText(
				if (canal.hd) "HD" else "SD",
				style = Tipografia.etiqueta.copy(color = Paleta.tintaSutil),
			)
		}
	}
}
