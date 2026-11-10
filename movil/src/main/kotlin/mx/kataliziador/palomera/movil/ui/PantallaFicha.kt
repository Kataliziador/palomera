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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.catalogo.Detalle
import kotlinx.coroutines.launch
import mx.kataliziador.palomera.movil.catalogo.Forma
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.Reproduccion
import timber.log.Timber
import mx.kataliziador.palomera.movil.catalogo.tiempoRestante
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import androidx.compose.ui.text.style.TextOverflow
import mx.kataliziador.palomera.movil.catalogo.Episodio
import mx.kataliziador.palomera.movil.catalogo.etiquetaDeEpisodio
import mx.kataliziador.palomera.movil.catalogo.rotulo

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
fun PantallaFicha(
	jf: Jellyfin,
	id: String,
	acento: Color,
	perfil: String,
	alVolver: () -> Unit,
	alReproducir: (url: String, titulo: String) -> Unit,
) {
	val alcance = rememberCoroutineScope()
	var aviso by remember { mutableStateOf<String?>(null) }
	val detalle by produceState<Detalle?>(initialValue = null, id) { value = jf.detalle(id) }

	// **Una serie no se puede reproducir, sus episodios sí.** Esta pantalla
	// servía las dos cosas con el mismo botón y el servidor contestaba
	// `Error processing request` (medido el 10-nov).
	val esSerie = detalle?.tipo == "Series"
	val siguiente by produceState<Episodio?>(initialValue = null, id, esSerie) {
		value = if (esSerie) jf.siguienteEpisodio(id) else null
	}
	val episodios by produceState(initialValue = emptyList<Episodio>(), id, esSerie) {
		value = if (esSerie) jf.episodios(id) else emptyList()
	}


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
		Box(Modifier.fillMaxWidth().height(220.dp)) {
			Cartel(
				url = detalle?.let { jf.cartel(it.id, it.fondo, Forma.FONDO, 1080) },
				id = id,
				descripcion = detalle?.nombre.orEmpty(),
				modifier = Modifier.fillMaxSize(),
			)

			// **El velo no es estética.** Detrás puede haber un fotograma
			// blanco —nieve, un plató, unos créditos—, y sin él la flecha de
			// volver desaparece justo en la única pantalla sin otra salida.
			// Baja desde arriba, donde está la flecha, y vuelve a cerrar
			// abajo, donde empieza el texto.
			Box(
				Modifier
					.fillMaxSize()
					.background(
						Brush.verticalGradient(
							0f to Color.Black.copy(alpha = 0.55f),
							0.35f to Color.Black.copy(alpha = 0.10f),
							1f to Paleta.fondo,
						),
					),
			)

			Box(
				Modifier
					.padding(6.dp, 8.dp)
					.size(BLANCO_TACTIL_DP.dp)
					.clickable(onClick = alVolver),
				contentAlignment = Alignment.Center,
			) {
				BasicText("‹", style = Tipografia.titulo.copy(color = Color.White))
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

			// **Lo que suena no siempre es lo que se está mirando.** En una
			// serie es un episodio, y el botón dice cuál antes de que lo
			// pulsen: «Reproducir» a secas, en ocho temporadas, es pedirle al
			// abonado que pulse para averiguar dónde cae.
			val etiqueta = when {
				esSerie && siguiente == null -> "Esta serie aún no tiene episodios"
				esSerie -> etiquetaDeEpisodio(
					siguiente?.temporada,
					siguiente?.numero,
					empezado = (siguiente?.porcentaje ?: 0) > 0,
				)
				empezado -> tiempoRestante(d.minutos - d.minutosVistos).replace("Quedan", "Reanudar ·")
				else -> "Reproducir"
			}
			val sePuede = !esSerie || siguiente != null

			Box(
				Modifier
					.fillMaxWidth()
					.heightIn(min = 48.dp)
					.clip(RoundedCornerShape(24.dp))
					.background(if (sePuede) acento else Paleta.mosaico)
					.then(
						if (!sePuede) {
							Modifier
						} else {
							Modifier.clickable {
								alcance.launch {
									reproducir(
										jf, perfil,
										if (esSerie) siguiente!!.id else d.id,
										nombreDeLoQueSuena(d, if (esSerie) siguiente else null),
										alReproducir,
									) { aviso = it }
								}
							}
						},
					)
					.padding(vertical = 14.dp),
				contentAlignment = Alignment.Center,
			) {
				BasicText(
					etiqueta,
					style = Tipografia.cuerpo.copy(
						color = if (sePuede) Paleta.fondo else Paleta.tintaSutil,
					),
				)
			}

			aviso?.let {
				BasicText(it, style = Tipografia.tarjeta.copy(color = Paleta.aviso))
			}

			d.sinopsis?.let {
				BasicText(it, style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada))
			}

			// Lo que un abonado consulta antes de darle: en qué idioma lo va a oír.
			Dato("Audio", d.audios.joinToString(", ").ifBlank { "Una sola pista" })
			Dato("Subtítulos", d.subtitulos.joinToString(", ").ifBlank { "No trae" })

			if (esSerie && episodios.isNotEmpty()) {
				Episodios(
					episodios = episodios,
					acento = acento,
					jf = jf,
				) { episodio ->
					alcance.launch {
						reproducir(
							jf, perfil, episodio.id,
							nombreDeLoQueSuena(d, episodio),
							alReproducir,
						) { aviso = it }
					}
				}
			}
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

/**
 * Cómo se llama lo que va a sonar, para la barra del reproductor.
 *
 * Un episodio se llama «Episodio 1» en casi todas las series del catálogo, y
 * eso solo no dice de qué serie es. Con la serie delante, sí.
 */
private fun nombreDeLoQueSuena(d: Detalle, episodio: Episodio?): String = when (episodio) {
	null -> d.nombre
	else -> listOfNotNull(
		d.nombre,
		rotulo(episodio.temporada, episodio.numero).takeIf { it.isNotEmpty() },
	).joinToString(" · ")
}

/**
 * Preguntar al servidor cómo ver algo, y llevarlo al reproductor.
 *
 * Está suelto porque lo usan los dos botones —el principal y cada episodio— y
 * duplicarlo dejaría dos sitios donde olvidar el registro de la
 * transcodificación o el aviso de que no se puede.
 */
private suspend fun reproducir(
	jf: Jellyfin,
	perfil: String,
	itemId: String,
	comoSeLlama: String,
	alReproducir: (String, String) -> Unit,
	alFallar: (String) -> Unit,
) {
	when (val r = jf.comoReproducir(itemId, perfil)) {
		is Reproduccion.Directo -> alReproducir(r.url, comoSeLlama)
		// Funciona igual, y se registra: transcodificar gasta CPU del site, así
		// que conviene saber cuántas veces pasa y por qué.
		is Reproduccion.Transcodificado -> {
			Timber.i("vod.transcodifica titulo=%s motivo=%s", comoSeLlama, r.motivo)
			alReproducir(r.url, comoSeLlama)
		}
		Reproduccion.NoSePuede ->
			alFallar("Tu proveedor no puede enviarte este título ahora mismo.")
	}
}

/**
 * Los episodios, agrupados por temporada.
 *
 * **Sin selector de temporada.** Con una o dos temporadas, un selector es un
 * toque de más para ver lo que ya cabía; con muchas, el encabezado pegado a su
 * grupo dice lo mismo mientras se desplaza. El catálogo de un ISP no tiene
 * series de veinte temporadas.
 */
@Composable
private fun Episodios(
	episodios: List<Episodio>,
	acento: Color,
	jf: Jellyfin,
	alPulsar: (Episodio) -> Unit,
) {
	// `groupBy` conserva el orden de llegada, que es el del servidor: por
	// temporada y número. No hay que reordenar nada.
	for ((temporada, delGrupo) in episodios.groupBy { it.temporada }) {
		BasicText(
			temporada?.let { "Temporada $it" } ?: "Otros episodios",
			modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
			style = Tipografia.seccion.copy(color = Paleta.tinta),
		)
		for (episodio in delGrupo) {
			FilaDeEpisodio(episodio, acento, jf, alPulsar)
		}
	}
}

@Composable
private fun FilaDeEpisodio(
	episodio: Episodio,
	acento: Color,
	jf: Jellyfin,
	alPulsar: (Episodio) -> Unit,
) {
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.clickable { alPulsar(episodio) }
			.padding(vertical = 7.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(Modifier.width(104.dp)) {
			Cartel(
				url = jf.cartel(episodio.id, episodio.cartel, Forma.CARTEL, 180),
				id = episodio.id,
				descripcion = episodio.nombre,
				modifier = Modifier
					.fillMaxWidth()
					.height(59.dp)
					.clip(RoundedCornerShape(6.dp)),
			)
			// Lo visto, donde ya está la imagen: una barra y no un porcentaje,
			// que es lo mismo que hace «Seguir viendo».
			if (episodio.porcentaje > 0) {
				Box(
					Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth(episodio.porcentaje / 100f)
						.height(3.dp)
						.background(acento),
				)
			}
		}
		Column {
			BasicText(
				listOfNotNull(
					rotulo(episodio.temporada, episodio.numero).takeIf { it.isNotEmpty() },
					episodio.nombre,
				).joinToString("  "),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				style = Tipografia.cuerpo.copy(color = Paleta.tinta),
			)
			episodio.minutos.takeIf { it > 0 }?.let {
				BasicText(
					duracion(it),
					style = Tipografia.etiqueta.copy(color = Paleta.tintaSutil),
				)
			}
		}
	}
}
