package org.jellyfin.androidtv.ui.livetv
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.ui.ScreensaverLock
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.engine.AspectMode
import org.jellyfin.livetv.engine.DecodePath
import org.jellyfin.livetv.engine.DeinterlaceMode
import org.jellyfin.livetv.engine.EngineListener
import org.jellyfin.livetv.engine.EngineStats
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
	val dev = remember { DevOptions.of(context) }

	// El volcado automático se activa aquí porque es donde ocurren los errores.
	// Es idempotente.
	remember(context) { LiveLog.enableAutoExport(context); Unit }

	// Cambiar una opción del menú rehace el plan sin salir del canal: eso es lo
	// que significa «en caliente», y es lo que M4 necesita para barrer las
	// cuatro combinaciones de flags sin recompilar.
	val revision by dev.revision.collectAsState()

	var stats by remember { mutableStateOf(EngineStats(engineId = "media3")) }
	var state by remember { mutableStateOf("Conectando…") }
	var detail by remember { mutableStateOf<String?>(null) }

	// --- Zapeo con el mando ------------------------------------------------
	//
	// Dos canales y no uno. `enPantalla` cambia en el acto con cada pulsación,
	// porque la barra tiene que responder al instante o el mando se siente
	// roto. `sintonizado` lo sigue tras una pausa, y es el único que toca el
	// motor: mantener ▼ pulsado recorriendo veinte canales no puede reconstruir
	// veinte veces el decodificador.
	val catalogo = remember { LiveTvCatalog.of(context) }
	val canales by catalogo.state.collectAsState()

	var enPantalla by remember(channelId) { mutableStateOf(catalogo.byId(channelId)) }
	var sintonizado by remember(channelId) { mutableStateOf(catalogo.byId(channelId)) }

	LaunchedEffect(enPantalla) {
		if (enPantalla == null || enPantalla == sintonizado) return@LaunchedEffect
		delay(ZAP_SETTLE_MS)
		sintonizado = enPantalla
	}

	val channel = sintonizado

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
	// Atado al canal **sintonizado**: el de la barra cambia mientras se zapea y
	// reconstruir el motor en cada pulsación es justo lo que hay que evitar.
	val engine: LiveEngine = remember(channel.url, revision) {
		Media3LiveEngine(context.applicationContext, dev)
	}

	LaunchedEffect(engine) {
		while (true) {
			stats = engine.stats()
			delay(STATS_REFRESH_MS)
		}
	}

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

	// --- Barra de canal -----------------------------------------------------
	//
	// Se enseña al sintonizar, al zapear y al pulsar OK, y se esconde sola.
	// **Mientras algo no vaya bien se queda fija**: esconder la única pista de
	// lo que ocurre dejaría al suscriptor mirando una pantalla muda.
	var barraVisible by remember { mutableStateOf(true) }
	var pulsacion by remember { mutableIntStateOf(0) }
	val enVivo = state == "En vivo"

	LaunchedEffect(pulsacion, enVivo) {
		barraVisible = true
		if (!enVivo) return@LaunchedEffect
		delay(CHANNEL_BAR_VISIBLE_MS)
		barraVisible = false
	}

	// Marcado por número, igual que en la lista: con ~120 canales nadie llega
	// al 87 con la flecha.
	var digitos by remember { mutableStateOf("") }
	val marcado = remember(digitos, canales) {
		digitos.toIntOrNull()?.let { n -> canales.channels.firstOrNull { it.number == n } }
	}
	LaunchedEffect(digitos) {
		if (digitos.isEmpty()) return@LaunchedEffect
		delay(NUMBER_ENTRY_TIMEOUT_MS)
		val destino = marcado
		digitos = ""
		if (destino != null) { enPantalla = destino; pulsacion++ }
	}

	fun vecino(paso: Int): ChannelProfile? {
		val lista = canales.channels
        if (lista.isEmpty()) return null
		val i = lista.indexOfFirst { it.id == enPantalla?.id }
		// Da la vuelta a propósito: en televisión, bajar desde el último lleva
		// al primero. Toparse con un muro invisible se siente como un fallo.
		return lista[((if (i < 0) 0 else i) + paso + lista.size) % lista.size]
	}

	val foco = remember { FocusRequester() }
	LaunchedEffect(foco) { runCatching { foco.requestFocus() } }

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black)
			.focusRequester(foco)
			.focusable()
			.onKeyEvent { evento ->
				if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
				when (evento.key) {
					Key.DirectionUp -> { vecino(-1)?.let { enPantalla = it }; pulsacion++; true }
					Key.DirectionDown -> { vecino(1)?.let { enPantalla = it }; pulsacion++; true }
					Key.DirectionCenter, Key.Enter, Key.Info -> { pulsacion++; true }
					else -> {
						val d = digitoDePlayer(evento.key)
						if (d != null) {
							if (digitos.length < 4) digitos += d
							true
						} else {
							// Todo lo demás se deja pasar: ATRÁS tiene que
							// seguir saliendo del canal.
							false
						}
					}
				}
			}
	) {
		AndroidView(
			modifier = Modifier.fillMaxSize(),
			// SurfaceView, nunca TextureView (CLAUDE.md §6).
			factory = { ctx -> SurfaceView(ctx) },
			// La superficie se entrega cuando existe; `prepare` no la necesita
			// de antemano y Media3 la acepta en caliente.
			update = { view -> engine.attach(view) },
		)

		if (barraVisible) {
			LiveTvChannelBar(
				channel = enPantalla ?: channel,
				state = if (enPantalla?.id != channel.id) "Sintonizando…" else state,
				detail = detail,
				resolution = stats.videoWidth.takeIf { it > 0 }?.let { "${it}x${stats.videoHeight}" },
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}

		if (digitos.isNotEmpty()) {
			NumberEntryOverlay(
				digits = digitos,
				resolved = marcado?.name,
				modifier = Modifier.align(Alignment.Center),
			)
		}

		// Lo que el abonado ve mientras el vigilante trabaja.
		//
		// Es el sustituto del letrero del headend, y es mejor sitio para él:
		// no depende de Flussonic, no puede romper el flujo, no hay que
		// mantenerlo en 120 canales y lleva la marca de cada ISP. El letrero
		// en el servidor convirtió un corte temporal en un canal detenido
		// (LIVETV_LOG, 01-oct); esto no puede hacer eso.
		if (stats.recovering) {
			Column(
				modifier = Modifier
					.align(Alignment.Center)
					.background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
					.padding(horizontal = 36.dp, vertical = 24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text(text = "Sin señal", color = Color.White, fontSize = 30.sp)
				Text(
					text = "Reconectando…",
					color = Color(0xFFBDBDBD),
					fontSize = 15.sp,
				)
			}
		}

		when (dev.overlayMode) {
			DevOptions.OVERLAY_COMPACTO -> LiveTvCompactOverlay(
				channel = channel,
				stats = stats,
				modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
			)

			DevOptions.OVERLAY_COMPLETO -> LiveTvStatsOverlay(
				channel = channel,
				stats = stats,
				state = state,
				modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
			)
		}

		if (dev.overlayMode == DevOptions.OVERLAY_OCULTO && dev.isDegraded) {
			// La degradación vive en `SharedPreferences` y sobrevive al cierre de
			// la app y al reinicio de la caja. Con el overlay oculto no había
			// forma de enterarse, y el 2026-09-26 eso costó dos conclusiones
			// falsas: se midió una sesión con `video=software` y `tunneled`
			// pegados como si fuera de fábrica. El aviso es el que impide
			// repetirlo, y solo aparece cuando algo se aparta de fábrica.
			Text(
				text = "degradación: ${dev.describe()}",
				color = Color.Yellow,
				modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
			)
		}
	}
}

/** Medio segundo: suficiente para seguir un problema, sin coste apreciable. */
private const val STATS_REFRESH_MS = 500L

/**
 * Lo que se espera antes de sintonizar de verdad tras una pulsación.
 *
 * Mantener ▼ recorriendo veinte canales no puede reconstruir veinte veces el
 * decodificador. La barra cambia en el acto; el motor espera a que pares.
 */
private const val ZAP_SETTLE_MS = 450L

/** Lo que dura la barra en pantalla cuando el canal va bien. */
private const val CHANNEL_BAR_VISIBLE_MS = 4_000L

/** Segundo y medio: da tiempo a marcar tres cifras sin hacer esperar. */
private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L

private fun digitoDePlayer(key: Key): Char? = when (key) {
	Key.Zero, Key.NumPad0 -> '0'
	Key.One, Key.NumPad1 -> '1'
	Key.Two, Key.NumPad2 -> '2'
	Key.Three, Key.NumPad3 -> '3'
	Key.Four, Key.NumPad4 -> '4'
	Key.Five, Key.NumPad5 -> '5'
	Key.Six, Key.NumPad6 -> '6'
	Key.Seven, Key.NumPad7 -> '7'
	Key.Eight, Key.NumPad8 -> '8'
	Key.Nine, Key.NumPad9 -> '9'
	else -> null
}
