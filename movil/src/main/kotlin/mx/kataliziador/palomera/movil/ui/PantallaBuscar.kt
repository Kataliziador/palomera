package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.kataliziador.palomera.movil.catalogo.Forma
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.Titulo
import mx.kataliziador.palomera.movil.catalogo.sePreguntaAlServidor
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import org.jellyfin.livetv.source.ChannelProfile
import org.jellyfin.livetv.source.ChannelSearch

/**
 * Buscar: **una sola caja para las dos cosas**.
 *
 * ## Por qué no son dos búsquedas
 *
 * Porque el abonado no piensa en «catálogo» y «canales»: piensa en lo que
 * quiere ver. Obligarle a elegir primero dónde buscar le pide que sepa de
 * antemano la respuesta.
 *
 * Los dos lados se comportan distinto por necesidad, no por diseño:
 *
 * - **los canales están en memoria**, así que se filtran desde la primera
 *   letra y sin pedirle nada a nadie — y los dígitos buscan por número, que
 *   es lo que ya hace [ChannelSearch] en la caja;
 * - **el catálogo vive en el servidor del ISP**, así que se espera a dos
 *   letras y a que el abonado deje de teclear. Sin eso, cada tecla sería una
 *   petición a una máquina que atiende a todas las casas a la vez.
 */
@Composable
fun PantallaBuscar(
	jf: Jellyfin,
	acento: Color,
	canales: List<ChannelProfile>,
	alAbrirTitulo: (String) -> Unit,
	alAbrirCanal: (ChannelProfile) -> Unit,
) {
	var consulta by rememberSaveable { mutableStateOf("") }
	var titulos by remember { mutableStateOf(emptyList<Titulo>()) }
	var buscando by remember { mutableStateOf(false) }

	val canalesQueCasan = remember(consulta, canales) { ChannelSearch.buscar(canales, consulta) }

	// **Se espera a que deje de teclear.** 350 ms es lo que separa escribir de
	// haber escrito: por debajo se dispara a media palabra, por encima la
	// pantalla parece dormida.
	LaunchedEffect(consulta) {
		if (!sePreguntaAlServidor(consulta)) {
			titulos = emptyList()
			buscando = false
			return@LaunchedEffect
		}
		buscando = true
		delay(350)
		titulos = jf.buscar(consulta.trim())
		buscando = false
	}

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			.imePadding(),
	) {
		CajaDeBusqueda(consulta, acento) { consulta = it }

		val vacio = canalesQueCasan.isEmpty() && titulos.isEmpty()

		when {
			consulta.isBlank() -> Mensaje("Busca una película, una serie o un canal.")

			buscando && vacio -> Mensaje("Buscando…")

			vacio -> Mensaje(
				"No encontramos nada para «${consulta.trim()}». " +
					"Prueba con menos palabras o con el número del canal.",
			)

			else -> LazyColumn(
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				// Los canales primero: son los que ya están y los que
				// contestan al instante. Hacer esperar a quien tecleó «207»
				// por una consulta al servidor sería absurdo.
				if (canalesQueCasan.isNotEmpty()) {
					item { Encabezado("Canales") }
					items(canalesQueCasan, key = { "c" + it.id }) { canal ->
						FilaDeCanal(canal, acento) { alAbrirCanal(canal) }
					}
				}
				if (titulos.isNotEmpty()) {
					item { Encabezado("Catálogo") }
					items(titulos, key = { "t" + it.id }) { titulo ->
						FilaDeTitulo(titulo, jf) { alAbrirTitulo(titulo.id) }
					}
				}
			}
		}
	}
}

@Composable
private fun CajaDeBusqueda(valor: String, acento: Color, alCambiar: (String) -> Unit) {
	Box(
		Modifier
			.fillMaxWidth()
			.padding(16.dp, 12.dp)
			.heightIn(min = BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(Paleta.mosaico)
			.border(1.dp, Paleta.elevado, RoundedCornerShape(10.dp))
			.padding(horizontal = 14.dp, vertical = 12.dp),
		contentAlignment = Alignment.CenterStart,
	) {
		if (valor.isEmpty()) {
			BasicText(
				"Buscar",
				style = Tipografia.cuerpo.copy(color = Paleta.tintaSutil),
			)
		}
		BasicTextField(
			value = valor,
			onValueChange = alCambiar,
			singleLine = true,
			cursorBrush = SolidColor(acento),
			textStyle = Tipografia.cuerpo.copy(color = Paleta.tinta),
			modifier = Modifier.fillMaxWidth(),
		)
	}
}

@Composable
private fun Encabezado(texto: String) {
	BasicText(
		texto,
		modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 8.dp),
		style = Tipografia.seccion.copy(color = Paleta.tinta),
	)
}

@Composable
private fun FilaDeCanal(canal: ChannelProfile, acento: Color, alPulsar: () -> Unit) {
	Row(
		Modifier
			.fillMaxWidth()
			.heightIn(min = BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(8.dp))
			.clickable(onClick = alPulsar)
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		// El número y no las iniciales: «Azteca Uno» y «Azteca 7» daban las
		// mismas tres letras, y entonces la insignia no distinguía nada.
		Box(
			Modifier.width(44.dp).height(32.dp).clip(RoundedCornerShape(6.dp))
				.background(Paleta.mosaico),
			contentAlignment = Alignment.Center,
		) {
			BasicText(
				canal.number?.toString() ?: "–",
				style = Tipografia.tarjeta.copy(color = acento),
			)
		}
		BasicText(
			canal.name,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			style = Tipografia.cuerpo.copy(color = Paleta.tinta),
		)
	}
}

@Composable
private fun FilaDeTitulo(titulo: Titulo, jf: Jellyfin, alPulsar: () -> Unit) {
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.clickable(onClick = alPulsar)
			.padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Cartel(
			url = jf.cartel(titulo.id, titulo.cartel, Forma.CARTEL, 132),
			id = titulo.id,
			descripcion = titulo.nombre,
			modifier = Modifier.size(44.dp, 66.dp).clip(RoundedCornerShape(6.dp)),
		)
		Column {
			BasicText(
				titulo.nombre,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				style = Tipografia.cuerpo.copy(color = Paleta.tinta),
			)
			Spacer(Modifier.height(3.dp))
			BasicText(
				listOfNotNull(
					if (titulo.tipo == "Series") "Serie" else "Película",
					titulo.anio?.toString(),
				).joinToString(" · "),
				style = Tipografia.etiqueta.copy(color = Paleta.tintaSutil),
			)
		}
	}
}

@Composable
private fun Mensaje(texto: String) {
	Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
		BasicText(texto, style = Tipografia.cuerpo.copy(color = Paleta.tintaSutil))
	}
}
