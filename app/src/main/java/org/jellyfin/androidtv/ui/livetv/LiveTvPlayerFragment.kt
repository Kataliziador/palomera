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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.ScreensaverLock
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
 * **M0:** el plan de reproducción se construye a mano y el canal llega de la
 * lista de `BuildConfig`. La cascada que lo decide por canal y por caja es M3.
 */
class LiveTvPlayerFragment : Fragment() {

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			LivePlayer(channelId = arguments?.getString(BootstrapChannels.EXTRA_CHANNEL_ID))
		}
	}
}

@Composable
private fun LivePlayer(channelId: String?) {
	val context = LocalContext.current

	val lifecycleOwner = LocalLifecycleOwner.current

	var state by remember { mutableStateOf("Conectando…") }
	var detail by remember { mutableStateOf<String?>(null) }

	val channel = remember(channelId) { BootstrapChannels.byId(channelId) }

	if (channel == null) {
		// Con la lista de BuildConfig no debería ocurrir, pero callarlo sería
		// un error silencioso (CLAUDE.md regla 8).
		Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
			Text(
				text = "Canal no encontrado: $channelId",
				color = Color.White,
				modifier = Modifier.align(Alignment.Center),
			)
		}
		return
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

	DisposableEffect(engine, lifecycleOwner) {
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

		// La reproducción se ata al **ciclo de vida**, no solo a la composición.
		// Con ATRÁS el reproductor sale de la composición y `onDispose` limpia,
		// pero al pulsar HOME el fragmento solo se detiene: sin esto, el canal
		// se seguía oyendo por encima del lanzador de la caja, gastando ancho
		// de banda de un stream que nadie está viendo.
		//
		// `addObserver` sincroniza al observador hasta el estado actual, así que
		// ON_START llega solo en la primera composición y arranca la
		// reproducción. No hace falta prepararla aquí aparte.
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_START -> {
					engine.prepare(channel, plan)
					engine.play()
				}

				Lifecycle.Event.ON_STOP -> {
					// En vivo no tiene sentido seguir almacenando en segundo
					// plano: al volver se vuelve al borde de emisión, no a lo
					// que se perdió.
					engine.stop()
					state = "Conectando…"
				}

				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)

		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			engine.removeListener(listener)
			engine.release()
		}
	}

	// Sin esto, el salvapantallas de la app tapa el canal a los 5 minutos: el
	// suscriptor no toca el control mientras ve televisión, que es justo la
	// condición que el temporizador interpreta como inactividad.
	//
	// A diferencia del VOD, que pasa `enabled = playing` para dejar entrar el
	// salvapantallas en pausa, aquí el bloqueo dura lo que dure la pantalla.
	// En vivo no hay pausa, y durante un rebuffer por lluvia lo último que
	// debe pasar es que la imagen se sustituya por un carrusel de carátulas
	// mientras el usuario espera a que vuelva la señal.
	ScreensaverLock(enabled = true)

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
