package mx.kataliziador.palomera.movil.ui

import android.os.SystemClock
import android.view.SurfaceView
import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.kataliziador.palomera.movil.catalogo.Aviso
import mx.kataliziador.palomera.movil.catalogo.tocaLatido
import mx.kataliziador.palomera.movil.ui.tema.BLANCO_TACTIL_DP
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.heightIn
import androidx.media3.common.C
import androidx.media3.common.Tracks

/**
 * El reproductor.
 *
 * **Las tintas de aquí no son las del resto de la app.** Encima del vídeo son
 * blanco con alfa y no grises fijos, porque lo que haya detrás lo pone el ISP y
 * puede ser blanco a pantalla completa — está medido en la caja, donde
 * `#BDBDBD` daba 1,66:1 sobre una carta de ajuste.
 *
 * **Sin barra de avance en directo.** Detrás de un canal no hay una película
 * sino una ventana DVR de segundos: arrastrarla no tendría dónde ir
 * (`docs/diseno-movil.md` §9).
 */
@OptIn(UnstableApi::class)
@Composable
fun PantallaVideo(
	url: String,
	titulo: String,
	enDirecto: Boolean,
	/**
	 * Qué contarle al servidor del ISP mientras esto corre.
	 *
	 * Por defecto, nada: el reproductor funciona igual sin servidor, y los
	 * canales en vivo no se reportan ([seReporta]). Cuando sí hay a quién
	 * avisar, es esto lo que hace avanzar «Seguir viendo».
	 */
	avisar: suspend (Aviso, Long, Boolean) -> Unit = { _, _, _ -> },
	alVolver: () -> Unit,
) {
	val contexto = LocalContext.current

	// Apaisado y sin barras del sistema mientras esto esté puesto. Ver
	// [ModoCine]: un 16:9 en una pantalla 9:20 ocupa menos de un tercio.
	ModoCine()

	var controlesVisibles by remember { mutableStateOf(true) }
	var reproduciendo by remember { mutableStateOf(true) }
	var posicion by remember { mutableLongStateOf(0L) }
	var duracion by remember { mutableLongStateOf(0L) }
	// La forma del vídeo. Llega con el primer fotograma, no antes.
	var aspecto by remember { mutableDoubleStateOf(0.0) }
	// Dónde está el dedo mientras arrastra la barra, de 0 a 1. `null` = no
	// está arrastrando, y entonces manda la posición del reproductor.
	var arrastre by remember { mutableStateOf<Float?>(null) }
	// Las pistas de audio que trae lo que se está viendo, y cuál suena.
	var pistas by remember { mutableStateOf(emptyList<Pista>()) }
	var pistaActiva by remember { mutableStateOf<String?>(null) }
	var eligiendoPista by remember { mutableStateOf(false) }

	val exo = remember {
		ExoPlayer.Builder(contexto).build().apply {
			setMediaItem(MediaItem.fromUri(url))
			prepare()
			playWhenReady = true
		}
	}

	// **Se pausa al salir de la pantalla, y es un requisito de teléfono.**
	// Medido el 10-nov en el emulador: con HOME pulsado la película seguía —el
	// servidor marcaba `pausa: False` y el audio del aparato `state:started`—,
	// así que al abonado se le gastaban datos y batería en el bolsillo y
	// «Seguir viendo» avanzaba sin que nadie mirase. En una caja esto no
	// existe; una tele no se guarda en un bolsillo.
	//
	// **No se reanuda solo al volver.** Quien vuelve a la app puede estar
	// volviendo a otra cosa, y arrancar sonido sin que lo pidan es peor que
	// un botón de más. La barra aparece con «Reproducir».
	val duenoDelCiclo = LocalLifecycleOwner.current
	DisposableEffect(duenoDelCiclo) {
		val vigia = LifecycleEventObserver { _, evento ->
			if (evento == Lifecycle.Event.ON_PAUSE || evento == Lifecycle.Event.ON_STOP) {
				exo.pause()
			}
		}
		duenoDelCiclo.lifecycle.addObserver(vigia)
		onDispose { duenoDelCiclo.lifecycle.removeObserver(vigia) }
	}

	// **El aviso de fin necesita sobrevivir a la pantalla que lo manda.** Al
	// salir, la corrutina del latido ya está cancelada y el `exo` a punto de
	// soltarse, así que la posición se lee **antes** de soltar nada y el envío
	// va por un alcance propio: `rememberCoroutineScope()` se cancela justo en
	// ese instante, que es cuando hace falta.
	val alcanceDeSalida = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
	DisposableEffect(exo) {
		val oyente = object : Player.Listener {
			override fun onVideoSizeChanged(tamano: VideoSize) {
				aspecto = aspectoDeVideo(tamano.width, tamano.height, tamano.pixelWidthHeightRatio)
			}

			override fun onTracksChanged(tracks: Tracks) {
				// §6: **la etiqueta de idioma puede mentir**, así que lo que
				// importa no es acertar la pista sino poder cambiarla. Aquí
				// sólo se lee qué hay.
				val deAudio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
				pistas = deAudio.mapIndexedNotNull { indice, grupo ->
					val formato = grupo.getTrackFormat(0)
					Pista(
						id = formato.id ?: "${indice + 1}",
						idioma = formato.language,
						canales = formato.channelCount.coerceAtLeast(0),
						etiqueta = formato.label,
					)
				}
				pistaActiva = deAudio.firstOrNull { it.isSelected }
					?.getTrackFormat(0)?.id
			}
		}
		exo.addListener(oyente)
		onDispose { exo.removeListener(oyente) }
	}

	DisposableEffect(Unit) {
		onDispose {
			val ultima = exo.currentPosition.coerceAtLeast(0L)
			exo.release()
			alcanceDeSalida.launch { avisar(Aviso.PARO, ultima, false) }
		}
	}

	// El reloj de la barra, y con él el latido al servidor. Un segundo basta
	// para la barra —nadie lee décimas— y es la resolución justa para que una
	// pausa salga al momento en el panel del proveedor.
	LaunchedEffect(exo) {
		var ultimoAvisoMs = 0L
		var pausaAnterior = true // arranca «en pausa» para que el primer aviso salga solo
		var empezado = false

		while (true) {
			posicion = exo.currentPosition.coerceAtLeast(0L)
			duracion = exo.duration.coerceAtLeast(0L)
			reproduciendo = exo.isPlaying

			val ahora = SystemClock.elapsedRealtime()
			val enPausa = !exo.isPlaying
			if (tocaLatido(ultimoAvisoMs, ahora, pausaAnterior, enPausa)) {
				avisar(if (empezado) Aviso.AVANZA else Aviso.EMPEZO, posicion, enPausa)
				empezado = true
				ultimoAvisoMs = ahora
				pausaAnterior = enPausa
			}
			delay(1_000)
		}
	}

	// Los controles se van solos: lo que se ve es la película, no los botones.
	//
	// **Salvo con un menú abierto o el dedo en la barra.** Elegir pista de
	// audio es leer dos o tres nombres y decidir, y cuatro segundos no dan: se
	// cerraba la lista mientras el abonado la miraba. Lo mismo arrastrando —
	// que la barra desaparezca bajo el dedo es de las cosas más molestas que
	// puede hacer un reproductor.
	LaunchedEffect(controlesVisibles, reproduciendo, eligiendoPista, arrastre) {
		if (controlesVisibles && reproduciendo && !eligiendoPista && arrastre == null) {
			delay(4_000)
			controlesVisibles = false
		}
	}

	Box(
		Modifier
			.fillMaxSize()
			.background(Color.Black),
	) {
		AndroidViewDelVideo(exo, aspecto)

		// **La capa del toque va encima del vídeo, no debajo.** Medido el
		// 10-nov: con el `clickable` en el contenedor, el `SurfaceView` del
		// `AndroidView` se comía el toque y los controles, una vez ocultos a
		// los 4 s, **no volvían nunca**. Sin ellos no hay pausa, ni retroceso,
		// ni título — y antes de que existiera el `BackHandler`, tampoco
		// salida.
		//
		// Los botones se dibujan después de esta capa, así que reciben el
		// toque antes que ella: pulsar «Pausa» pausa, y no esconde la barra.
		Box(
			Modifier
				.fillMaxSize()
				.clickable(
					// Sin onda ni resalte: esto es la pantalla entera, y un
					// destello al tocar un vídeo se lee como un fallo.
					indication = null,
					interactionSource = remember { MutableInteractionSource() },
				) { controlesVisibles = !controlesVisibles },
		)

		if (!controlesVisibles) return@Box

		// Velo arriba y abajo: el texto nunca cae sobre la imagen desnuda.
		Box(
			Modifier
				.fillMaxWidth()
				.height(110.dp)
				.align(Alignment.TopCenter)
				.background(Color.Black.copy(alpha = 0.55f)),
		)
		Box(
			Modifier
				.fillMaxWidth()
				.height(190.dp)
				.align(Alignment.BottomCenter)
				.background(Color.Black.copy(alpha = 0.65f)),
		)

		// **Los controles respetan las zonas del sistema; el vídeo no.**
		// Medido el 10-nov: sin esto, «Pausa», «−10 s» y «+10 s» caían detrás
		// de la barra de navegación de Android y no se podían pulsar, y el
		// título se pintaba donde va el reloj. El vídeo sí ocupa la pantalla
		// entera a propósito — es lo que se ha venido a ver.
		Row(
			Modifier
				.align(Alignment.TopStart)
				.windowInsetsPadding(WindowInsets.safeDrawing)
				.padding(6.dp, 10.dp)
				.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				Modifier.size(BLANCO_TACTIL_DP.dp).clickable(onClick = alVolver),
				contentAlignment = Alignment.Center,
			) {
				BasicText("‹", style = Tipografia.titulo.copy(color = Paleta.tinta))
			}
			BasicText(
				titulo,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				style = Tipografia.cuerpo.copy(color = Paleta.tinta),
			)
		}

		Column(
			Modifier
				.align(Alignment.BottomStart)
				.windowInsetsPadding(WindowInsets.safeDrawing)
				.padding(16.dp, 14.dp)
				.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			if (!enDirecto && duracion > 0) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					BasicText(
						reloj(arrastre?.let { (it * duracion).toLong() } ?: posicion),
						style = Tipografia.detalle.copy(color = Color.White.copy(alpha = 0.78f)),
					)
					// **La barra se arrastra.** Hasta el 11-nov sólo pintaba
					// el avance, así que en una película de dos horas lo único
					// para moverse eran saltos de diez segundos: setecientos
					// toques para llegar al final. Es el control que más se usa
					// de un reproductor y era el único que no hacía nada.
					//
					// Mientras el dedo está puesto se pinta `arrastre` y no la
					// posición real: si se siguiera al reproductor, la barra
					// daría tirones hacia atrás cada vez que el reloj la
					// adelanta.
					val fraccion = arrastre ?: (posicion.toFloat() / duracion).coerceIn(0f, 1f)
					Box(
						Modifier
							.weight(1f)
							// Alto de toque de 28 dp aunque la línea mida 3:
							// un objetivo de 3 dp no se acierta con el pulgar.
							.height(28.dp)
							.pointerInput(duracion) {
								detectHorizontalDragGestures(
									onDragStart = { posicionDelDedo ->
										arrastre = (posicionDelDedo.x / size.width)
											.coerceIn(0f, 1f)
									},
									onDragEnd = {
										arrastre?.let { exo.seekTo((it * duracion).toLong()) }
										arrastre = null
									},
									onDragCancel = { arrastre = null },
								) { cambio, delta ->
									cambio.consume()
									arrastre = ((arrastre ?: 0f) + delta / size.width)
										.coerceIn(0f, 1f)
								}
							}
							.pointerInput(duracion) {
								// Un toque suelto también salta, que es lo que
								// se espera al picar en mitad de la barra.
								detectTapGestures { donde ->
									exo.seekTo(
										((donde.x / size.width).coerceIn(0f, 1f) * duracion)
											.toLong(),
									)
								}
							},
						contentAlignment = Alignment.CenterStart,
					) {
						Box(
							Modifier
								.fillMaxWidth()
								.height(3.dp)
								.clip(RoundedCornerShape(2.dp))
								.background(Color.White.copy(alpha = 0.26f)),
						)
						Box(
							Modifier
								.fillMaxWidth(fraccion)
								.height(3.dp)
								.clip(RoundedCornerShape(2.dp))
								.background(Color.White),
						)
						// El tirador sólo aparece al arrastrar: en reposo la
						// barra es una línea fina y no una pieza de interfaz
						// encima de la película.
						if (arrastre != null) {
							Box(
								Modifier
									.fillMaxWidth(fraccion)
									.height(3.dp),
								contentAlignment = Alignment.CenterEnd,
							) {
								Box(
									Modifier
										.size(13.dp)
										.clip(RoundedCornerShape(7.dp))
										.background(Color.White),
								)
							}
						}
					}
					BasicText(
						"−" + reloj(duracion - posicion),
						style = Tipografia.detalle.copy(color = Color.White.copy(alpha = 0.78f)),
					)
				}
			}

			if (eligiendoPista) {
				ListaDePistas(pistas, pistaActiva) { elegida ->
					exo.trackSelectionParameters = exo.trackSelectionParameters
						.buildUpon()
						.setPreferredAudioLanguage(
							pistas.firstOrNull { it.id == elegida }?.idioma,
						)
						.build()
					pistaActiva = elegida
					eligiendoPista = false
				}
			}

			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				Boton(if (reproduciendo) "Pausa" else "Reproducir") {
					if (exo.isPlaying) exo.pause() else exo.play()
					reproduciendo = exo.isPlaying
					controlesVisibles = true
				}
				if (!enDirecto) {
					Boton("−10 s") { exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0)) }
					Boton("+10 s") { exo.seekTo(exo.currentPosition + 10_000) }
				}
				// Sólo cuando hay entre qué elegir: un botón que abre una
				// lista de una fila es un botón que miente.
				if (pistas.size > 1) {
					Boton("Audio") { eligiendoPista = !eligiendoPista; controlesVisibles = true }
				}
			}
		}
	}
}

/**
 * El vídeo, **con su forma**.
 *
 * Hasta el 10-nov era un `SurfaceView` a `fillMaxSize()` sin corrección
 * ninguna, así que un canal 16:9 se estiraba a los 1080×2400 de un teléfono
 * en vertical. Se vio en Las Estrellas: el logo del anuncio salía alargado.
 *
 * Se centra y se le da su proporción —incluido el SAR, que §6 exige por los
 * canales anamórficos— dejando banda negra donde sobre. `SurfaceView` y no
 * `TextureView`, que es lo que §6 fija también.
 */
@OptIn(UnstableApi::class)
@Composable
private fun AndroidViewDelVideo(exo: ExoPlayer, aspecto: Double) {
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		androidx.compose.ui.viewinterop.AndroidView(
			factory = { ctx -> SurfaceView(ctx).also { exo.setVideoSurfaceView(it) } },
			modifier = if (aspecto > 0.0) {
				Modifier.fillMaxWidth().aspectRatio(aspecto.toFloat())
			} else {
				// Todavía sin tamaño: ocupa todo y se recoloca al llegar el
				// primer fotograma. Un salto en el primer segundo es mejor que
				// una forma inventada que puede no ser la buena.
				Modifier.fillMaxSize()
			},
		)
	}
}

@Composable
private fun Boton(texto: String, alPulsar: () -> Unit) {
	Box(
		Modifier
			.height(BLANCO_TACTIL_DP.dp)
			.clip(RoundedCornerShape(22.dp))
			.background(Color.White.copy(alpha = 0.16f))
			.clickable(onClick = alPulsar)
			.padding(horizontal = 18.dp),
		contentAlignment = Alignment.Center,
	) {
		BasicText(texto, style = Tipografia.tarjeta.copy(color = Paleta.tinta))
	}
}

/** `h:mm:ss` o `m:ss`, que es como se lee una barra de tiempo. */
private fun reloj(ms: Long): String {
	val s = (ms / 1000).coerceAtLeast(0)
	val h = s / 3600
	val m = (s % 3600) / 60
	val seg = s % 60
	return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
}

/**
 * Las pistas de audio, para elegir.
 *
 * Va encima de los botones y no en un diálogo aparte: son dos o tres filas y
 * un diálogo a pantalla completa sobre un vídeo en marcha tapa justo lo que se
 * está intentando juzgar — si esta pista es la del idioma correcto.
 */
@Composable
private fun ListaDePistas(
	pistas: List<Pista>,
	activa: String?,
	alElegir: (String) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
		for (pista in pistas) {
			val esLaQueSuena = pista.id == activa
			Box(
				Modifier
					.heightIn(min = BLANCO_TACTIL_DP.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(
						if (esLaQueSuena) Color.White.copy(alpha = 0.22f) else Color.Transparent,
					)
					.clickable { alElegir(pista.id) }
					.padding(horizontal = 14.dp, vertical = 11.dp),
				contentAlignment = Alignment.CenterStart,
			) {
				BasicText(
					nombreDePista(pista),
					style = Tipografia.cuerpo.copy(
						color = if (esLaQueSuena) {
							Color.White
						} else {
							Color.White.copy(alpha = 0.78f)
						},
					),
				)
			}
		}
	}
}
