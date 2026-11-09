package mx.kataliziador.palomera.movil.ui

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import mx.kataliziador.palomera.movil.ui.tema.Paleta

/**
 * Un canal, a pantalla completa y sin controles.
 *
 * **No es el reproductor del producto**: es el instrumento de la fase 0.2, el
 * que contesta si un 1080i de satélite sale con peine en un teléfono (R1 de
 * `MOVIL_PLAN.md`). Por eso enseña la resolución y la cadencia encima: sin esa
 * línea, una captura no dice qué se estaba decodificando.
 *
 * El reproductor de verdad está diseñado en `docs/diseno-movil.md` y es otra
 * cosa — con barra de programa, zapeo y «volver al directo».
 */
@OptIn(UnstableApi::class)
@Composable
fun PantallaCanal(url: String) {
	val contexto = LocalContext.current
	var medida by remember { mutableStateOf("preparando…") }

	val exo = remember {
		ExoPlayer.Builder(contexto).build().apply {
			addListener(object : Player.Listener {
				override fun onVideoSizeChanged(tamano: VideoSize) {
					medida = "${tamano.width}×${tamano.height}  SAR ${tamano.pixelWidthHeightRatio}"
				}

				override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
					medida = "error: ${error.errorCodeName}"
				}
			})
			setMediaItem(MediaItem.fromUri(url))
			prepare()
			playWhenReady = true
		}
	}

	DisposableEffect(Unit) {
		onDispose { exo.release() }
	}

	Box(Modifier.fillMaxSize().background(Paleta.fondo)) {
		AndroidView(
			factory = { ctx -> SurfaceView(ctx).also { exo.setVideoSurfaceView(it) } },
			modifier = Modifier.fillMaxSize(),
		)
		BasicText(
			text = medida,
			modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
			style = TextStyle(color = Paleta.tinta, fontSize = 12.sp),
		)
	}
}
