package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.catalogo.BibliotecaDelSite
import mx.kataliziador.palomera.movil.catalogo.Forma
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.Titulo
import mx.kataliziador.palomera.movil.catalogo.proveedorDe
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * El catálogo entero, doblado en dos ejes.
 *
 * **Las 21 bibliotecas del site no caben como lista.** El ISP las nombra «Top
 * Películas Netflix», «Top Series HBO Max»…, así que el mismo nombre lleva
 * dentro las dos cosas que el abonado quiere elegir: **qué tipo** y **de
 * quién**. Separarlas convierte veintiuna filas en dos toques
 * (`docs/diseno-movil.md` §4.1).
 */
@Composable
fun PantallaCatalogo(jf: Jellyfin, acento: Color, alAbrir: (String) -> Unit) {
	var esSerie by rememberSaveable { mutableStateOf(false) }
	var proveedor by rememberSaveable { mutableStateOf<String?>(null) }

	val bibliotecas by produceState(initialValue = emptyList<BibliotecaDelSite>()) {
		value = jf.bibliotecas()
	}

	val proveedores = remember(bibliotecas) {
		bibliotecas.mapNotNull { proveedorDe(it.nombre) }.distinct().sorted()
	}

	// Qué biblioteca mirar: la del proveedor elegido, o el catálogo general.
	val elegida = remember(bibliotecas, esSerie, proveedor) {
		val tipo = if (esSerie) "Series" else "Películas"
		bibliotecas.firstOrNull { b ->
			if (proveedor == null) b.nombre == tipo
			else proveedorDe(b.nombre) == proveedor && b.nombre.contains(tipo, ignoreCase = true)
		}
	}

	val pagina by produceState(initialValue = emptyList<Titulo>() to 0, elegida) {
		value = elegida?.let { jf.paginaDe(it.id, limite = 60) } ?: (emptyList<Titulo>() to 0)
	}
	val titulos = pagina.first
	val total = pagina.second

	Column(Modifier.fillMaxSize().background(Paleta.fondo)) {
		BasicText(
			"Catálogo",
			modifier = Modifier.padding(16.dp, 14.dp),
			style = Tipografia.titulo.copy(color = Paleta.tinta),
		)

		// Eje 1: qué tipo.
		LazyRow(
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.padding(bottom = 10.dp),
		) {
			item { Chip("Películas", !esSerie, acento) { esSerie = false } }
			item { Chip("Series", esSerie, acento) { esSerie = true } }
		}

		// Eje 2: de quién.
		LazyRow(
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.padding(bottom = 12.dp),
		) {
			item { Chip("Todos", proveedor == null, acento, tenue = true) { proveedor = null } }
			items(proveedores) { p ->
				Chip(p, proveedor == p, acento, tenue = true) { proveedor = p }
			}
		}

		if (elegida == null) {
			Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
				BasicText(
					"Tu proveedor no tiene ${if (esSerie) "series" else "películas"} en esta colección.",
					style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
				)
			}
			return@Column
		}

		// **El total de la biblioteca, no el de la página.** Decía «60 títulos»
		// con 248 detrás: ése es el número que se pidió, no el que hay. Un
		// recuento que cuenta la página miente, y se vio en el teléfono.
		BasicText(
			"$total títulos",
			modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp),
			style = Tipografia.detalle.copy(color = Paleta.tintaSutil),
		)

		LazyVerticalGrid(
			columns = GridCells.Fixed(3),
			contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			items(titulos, key = { it.id }) { titulo ->
				Column(Modifier.clickable { alAbrir(titulo.id) }) {
					Cartel(
						url = jf.cartel(titulo.id, titulo.cartel, Forma.CARTEL, 336),
						id = titulo.id,
						descripcion = titulo.nombre,
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(2f / 3f)
							.clip(RoundedCornerShape(8.dp)),
					)
					Spacer(Modifier.height(7.dp))
					BasicText(
						titulo.nombre,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						style = Tipografia.tarjeta.copy(color = Paleta.tintaApagada),
					)
					titulo.anio?.let {
						BasicText(
							it.toString(),
							style = Tipografia.etiqueta.copy(color = Paleta.tintaSutil),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun Chip(
	texto: String,
	activo: Boolean,
	acento: Color,
	tenue: Boolean = false,
	alPulsar: () -> Unit,
) {
	Box(
		Modifier
			.heightIn(min = if (tenue) 32.dp else 36.dp)
			.clip(RoundedCornerShape(18.dp))
			.background(if (activo) acento else Paleta.mosaico)
			.clickable(onClick = alPulsar)
			.padding(horizontal = 14.dp, vertical = if (tenue) 7.dp else 9.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(
			texto,
			maxLines = 1,
			style = Tipografia.tarjeta.copy(
				color = if (activo) Paleta.fondo else Paleta.tintaApagada,
			),
		)
	}
}
