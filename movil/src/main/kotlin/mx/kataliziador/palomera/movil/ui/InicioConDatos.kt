package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.catalogo.EnProgreso
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.Titulo
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * Inicio con el catálogo del ISP de verdad.
 *
 * Separado de [PantallaInicio] a propósito: aquélla **no sabe de red**, recibe
 * listas y las pinta, y por eso se puede mirar sin servidor. Esto es la mitad
 * que habla con Jellyfin.
 */
@Composable
fun InicioConDatos(
	proveedor: String,
	acento: Color,
	baseUrl: String,
	token: String,
	usuarioId: String,
) {
	val datos by produceState<Datos?>(initialValue = null, baseUrl, token, usuarioId) {
		val jf = Jellyfin(baseUrl, token, usuarioId)
		// Las bibliotecas primero: son las que deciden qué carriles hay, y el
		// ISP ya eligió por nosotros al crearlas. El site de pruebas sirve 21.
		val bibliotecas = jf.bibliotecas().take(CARRILES_MAXIMOS)
		value = coroutineScope {
			// En paralelo: en serie, seis peticiones sobre una red de abonado
			// son seis esperas encadenadas en la primera pantalla.
			val progreso = async { jf.enProgreso() }
			val rieles = bibliotecas.map { b -> b.nombre to async { jf.ultimosDe(b.id) } }
			Datos(
				enProgreso = progreso.await(),
				carriles = rieles.mapNotNull { (nombre, pendiente) -> carril(nombre, pendiente.await()) },
			)
		}
	}

	val d = datos
	if (d == null) {
		Box(
			Modifier.fillMaxSize().background(Paleta.fondo).padding(32.dp),
			contentAlignment = Alignment.Center,
		) {
			BasicText(
				"Buscando tu catálogo…",
				style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
			)
		}
		return
	}

	PantallaInicio(
		proveedor = proveedor,
		acento = acento,
		enProgreso = d.enProgreso,
		carriles = d.carriles,
	)
}

private data class Datos(
	val enProgreso: List<EnProgreso>,
	val carriles: List<Carril>,
)

/**
 * Cuántas bibliotecas se traen a Inicio.
 *
 * El site de pruebas sirve **21** —«Películas», «Series» y diecinueve
 * colecciones «Top …» del ISP—, y una petición por cada una es una primera
 * pantalla que tarda. Seis llenan de sobra el alto de un teléfono, y el resto
 * vive en Catálogo, que es donde se va a buscar algo concreto.
 */
private const val CARRILES_MAXIMOS = 6

/** Un carril, o nada si no hay qué poner: una fila vacía es peor que ninguna. */
private fun carril(nombre: String, titulos: List<Titulo>): Carril? =
	titulos.takeIf { it.isNotEmpty() }?.let { lista ->
		Carril(nombre, lista.map { Portada(it.nombre, tonoDe(it.id)) })
	}

/**
 * Un color estable para el hueco del cartel, mientras no haya imágenes.
 *
 * Se deriva del id, así que el mismo título tiene siempre el mismo tono y la
 * rejilla no parpadea entre dos aperturas. Son cuatro grises templados de la
 * familia de [Paleta.mosaico]: lo bastante distintos para separar tarjetas, lo
 * bastante apagados para no competir con el texto.
 */
private fun tonoDe(id: String): Color = TONOS[Math.floorMod(id.hashCode(), TONOS.size)]

private val TONOS = listOf(
	Color(0xFF2A2D34),
	Color(0xFF332B2B),
	Color(0xFF26302B),
	Color(0xFF2E2A33),
)
