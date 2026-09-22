package org.jellyfin.androidtv.ui.livetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.engine.AspectMode
import org.jellyfin.livetv.engine.DecodePath
import org.jellyfin.livetv.engine.DeinterlaceMode
import org.jellyfin.livetv.engine.EngineListener
import org.jellyfin.livetv.engine.LiveEngine
import org.jellyfin.livetv.engine.Media3LiveEngine
import org.jellyfin.livetv.engine.PlaybackPlan
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Reproducción de un canal en vivo.
 *
 * Habla **solo** con la interfaz `LiveEngine`, nunca con ExoPlayer directamente
 * (`LIVETV_ENGINE.md` §2). Es lo que permite que libVLC entre en M6 sin tocar
 * esta pantalla.
 *
 * **M0:** el plan de reproducción se construye a mano y el canal llega por
 * `BuildConfig`. La cascada que decide el plan por canal y por caja es M3.
 */
class LiveTvPlayerFragment : Fragment() {

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			LivePlayer()
		}
	}
}

@Composable
private fun LivePlayer() {
	val context = LocalContext.current

	var state by remember { mutableStateOf("Conectando…") }
	var detail by remember { mutableStateOf<String?>(null) }

	val channel = remember {
		ChannelProfile(
			id = "bootstrap",
			name = BuildConfig.LIVETV_BOOTSTRAP_CHANNEL_NAME,
			url = BuildConfig.LIVETV_BOOTSTRAP_CHANNEL_URL,
			videoCodec = "h264",
			audioCodec = "aac",
		)
	}

	// M0: plan fijo. La cascada que lo decide por canal y por caja es M3.
	val plan = remember {
		PlaybackPlan(
			engineId = "media3",
			videoPath = DecodePath.HW,
			audioPath = DecodePath.HW,
			deinterlace = DeinterlaceMode.NONE,
			targetOffsetMs = LiveTuning.TARGET_OFFSET_DEFAULT_MS,
			tsFlags = 0,
			reason = "M0: plan fijo, sin cascada",
		)
	}

	// El motor se crea una sola vez por canal. Ojo con atarlo al SurfaceView:
	// la vista llega en una recomposición posterior, y si el efecto depende de
	// ella se construye un ExoPlayer que se libera acto seguido. Además de
	// desperdiciar ~100 ms, eso se paga en el zapping, que tiene objetivo duro
	// de p50 < 2 s (CLAUDE.md §10).
	val engine: LiveEngine = remember(channel.url) { Media3LiveEngine(context.applicationContext) }

	DisposableEffect(engine) {
		val listener = object : EngineListener {
			override fun onReady() { state = "Listo" }
			override fun onPlaying() { state = "En vivo"; detail = null }
			override fun onBuffering() { state = "Almacenando…" }

			override fun onRecoverableError(event: String, error: Throwable?) {
				// Nunca se cierra el player: es un estado temporal (CLAUDE.md §6).
				state = "Señal débil por condiciones climáticas"
				detail = event
			}

			override fun onFatalError(event: String, error: Throwable?) {
				state = "Este canal no es compatible con tu dispositivo"
				detail = event
			}
		}

		engine.addListener(listener)
		engine.setAspectMode(AspectMode.FIT)
		engine.prepare(channel, plan)
		engine.play()

		onDispose {
			engine.removeListener(listener)
			engine.release()
		}
	}

	Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
		AndroidView(
			modifier = Modifier.fillMaxSize(),
			// SurfaceView, nunca TextureView (CLAUDE.md §6).
			factory = { ctx -> SurfaceView(ctx) },
			// La superficie se entrega cuando existe; `prepare` no la necesita
			// de antemano y Media3 la acepta en caliente.
			update = { view -> engine.attach(view) },
		)

		Text(
			text = buildString {
				append(channel.name).append(" · ").append(state)
				detail?.let { append(" · ").append(it) }
			},
			color = Color.White,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(24.dp),
		)
	}
}
