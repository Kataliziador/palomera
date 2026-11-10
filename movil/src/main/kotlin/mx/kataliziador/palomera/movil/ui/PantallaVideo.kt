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
	var controlesVisibles by remember { mutableStateOf(true) }
	var reproduciendo by remember { mutableStateOf(true) }
	var posicion by remember { mutableLongStateOf(0L) }
	var duracion by remember { mutableLongStateOf(0L) }
	// La forma del vídeo. Llega con el primer fotograma, no antes.
	var aspecto by remember { mutableDoubleStateOf(0.0) }

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
	LaunchedEffect(controlesVisibles, reproduciendo) {
		if (controlesVisibles && reproduciendo) {
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
						reloj(posicion),
						style = Tipografia.detalle.copy(color = Color.White.copy(alpha = 0.78f)),
					)
					Box(
						Modifier
							.weight(1f)
							.height(3.dp)
							.clip(RoundedCornerShape(2.dp))
							.background(Color.White.copy(alpha = 0.26f)),
					) {
						Box(
							Modifier
								.fillMaxWidth((posicion.toFloat() / duracion).coerceIn(0f, 1f))
								.height(3.dp)
								.background(Color.White),
						)
					}
					BasicText(
						"−" + reloj(duracion - posicion),
						style = Tipografia.detalle.copy(color = Color.White.copy(alpha = 0.78f)),
					)
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
