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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.catalogo.EnProgreso
import mx.kataliziador.palomera.movil.catalogo.sinDuplicados
import mx.kataliziador.palomera.movil.catalogo.tiempoRestante
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * Un título del catálogo.
 *
 * [tono] no es un respaldo de segunda: es lo que se ve mientras la imagen
 * descarga y lo que queda si el título no tiene cartel, así que la pantalla
 * nunca enseña un hueco.
 */
data class Portada(
	val id: String,
	val titulo: String,
	val tono: Color,
	val url: String? = null,
)

/** Un carril con nombre. */
data class Carril(val nombre: String, val portadas: List<Portada>)

/**
 * Inicio: lo primero que ve el abonado, y **el catálogo manda**.
 *
 * Es al revés que en la caja, donde el canal es el centro. Decisión de Edgar;
 * el porqué está en `docs/diseno-movil.md` §1.
 *
 * Las medidas salen de ahí: margen de 16 dp —no los 48 de la caja—, carteles
 * de 112×168, y **44 dp de blanco en todo lo que se toca**, que manda sobre la
 * tipografía.
 */
@Composable
fun PantallaInicio(
	proveedor: String,
	acento: Color,
	enProgreso: List<EnProgreso>,
	carriles: List<Carril>,
	/** Cómo se pide el fondo apaisado de lo que está a medias. */
	urlDeProgreso: (String) -> String? = { null },
	alAbrir: (String) -> Unit = {},
) {
	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo),
	) {
		Cabecera(proveedor, acento)

		Column(
			Modifier
				.weight(1f)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(22.dp),
		) {
			// **Sin duplicados**, que es el defecto medido el 09-nov: el mismo
			// título vive en varias bibliotecas y el servidor lo devuelve una
			// vez por copia.
			CarrilDeProgreso(sinDuplicados(enProgreso), acento, urlDeProgreso, alAbrir)

			for (carril in carriles) CarrilDePortadas(carril, alAbrir)

			Spacer(Modifier.height(8.dp))
		}

	}
}

@Composable
private fun Cabecera(proveedor: String, acento: Color) {
	Row(
		Modifier
			.fillMaxWidth()
			.height(56.dp)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Box(
			Modifier
				.size(26.dp)
				.clip(RoundedCornerShape(6.dp))
				.background(acento),
			contentAlignment = Alignment.Center,
		) {
			BasicText(
				proveedor.take(1).uppercase(),
				style = Tipografia.tarjeta.copy(color = Paleta.fondo),
			)
		}
		BasicText(proveedor, style = Tipografia.seccion.copy(color = Paleta.tinta))
	}
}

@Composable
private fun CarrilDeProgreso(
	items: List<EnProgreso>,
	acento: Color,
	urlDe: (String) -> String?,
	alAbrir: (String) -> Unit,
) {
	if (items.isEmpty()) return

	Column {
		BasicText(
			"Seguir viendo",
			modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
			style = Tipografia.seccion.copy(color = Paleta.tinta),
		)
		LazyRow(
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			items(items, key = { it.id }) { item ->
				Column(Modifier.width(158.dp).clickable { alAbrir(item.id) }) {
					Box(
						Modifier
							.fillMaxWidth()
							.height(89.dp)
							.clip(RoundedCornerShape(8.dp)),
					) {
						// **El fondo apaisado, no el cartel.** Es la única
						// tarjeta horizontal de la app —158×89, o sea 16:9— y
						// un cartel vertical aquí recortaría la cara de quien
						// salga en él.
						Cartel(
							url = urlDe(item.id),
							id = item.id,
							descripcion = item.titulo,
							modifier = Modifier.fillMaxSize(),
						)

						// La marca sólo donde dice algo: aquí, cuánto llevas.
						Box(
							Modifier
								.align(Alignment.BottomStart)
								.fillMaxWidth()
								.height(3.dp)
								.background(Paleta.elevado),
						) {
							Box(
								Modifier
									.fillMaxWidth(item.porcentaje / 100f)
									.height(3.dp)
									.background(acento),
							)
						}
					}
					Spacer(Modifier.height(7.dp))
					BasicText(
						item.titulo,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						style = Tipografia.tarjeta.copy(color = Paleta.tintaApagada),
					)
					// **Minutos, no porcentaje.** La primera versión decía
					// «69 % por ver»: cierto, e inútil. Nadie decide si le da
					// tiempo a terminar una película en porcentajes. Se vio
					// mirando el teléfono, no la maqueta — que ya lo decía bien.
					BasicText(
						tiempoRestante(item.minutosRestantes),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						style = Tipografia.detalle.copy(color = Paleta.tintaSutil),
					)
				}
			}
		}
	}
}

@Composable
private fun CarrilDePortadas(carril: Carril, alAbrir: (String) -> Unit) {
	Column {
		BasicText(
			carril.nombre,
			modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
			style = Tipografia.seccion.copy(color = Paleta.tinta),
		)
		LazyRow(
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			items(carril.portadas) { portada ->
				Column(Modifier.width(112.dp).clickable { alAbrir(portada.id) }) {
					Cartel(
						url = portada.url,
						tono = portada.tono,
						descripcion = portada.titulo,
						modifier = Modifier
							.fillMaxWidth()
							.height(168.dp)
							.clip(RoundedCornerShape(8.dp)),
					)
					// Sin texto dentro: ahí va la imagen que sirve el servidor.
					// Pintarlo además del renglón de abajo se lee como un fallo,
					// y así se vio en el teléfono el 09-nov.
					Spacer(Modifier.height(7.dp))
					// Dos líneas, no una: con una, «Transformers: El despertar
					// de las bestias» y «Transformers: El último caballero» son
					// la misma palabra.
					BasicText(
						portada.titulo,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						style = Tipografia.tarjeta.copy(color = Paleta.tintaApagada),
					)
				}
			}
		}
	}
}

