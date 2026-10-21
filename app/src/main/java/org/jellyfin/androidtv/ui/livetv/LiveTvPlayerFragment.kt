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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
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
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.engine.AspectMode
import org.jellyfin.livetv.engine.CausaDeFallo
import org.jellyfin.livetv.engine.DecodePath
import org.jellyfin.livetv.engine.DeinterlaceMode
import org.jellyfin.livetv.engine.EngineListener
import org.jellyfin.livetv.engine.EngineStats
import org.jellyfin.livetv.engine.LiveEngine
import org.jellyfin.livetv.engine.Media3LiveEngine
import org.jellyfin.livetv.engine.PlanBuilder
import org.jellyfin.livetv.engine.VlcLiveEngine
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
			LivePlayer(
				channelId = arguments?.getString(BootstrapChannels.EXTRA_CHANNEL_ID),
				alSalir = { requireActivity().onBackPressedDispatcher.onBackPressed() },
			)
		}
	}
}

@Composable
private fun LivePlayer(channelId: String?, alSalir: () -> Unit) {
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

	// Declarado aquí y no con el resto de los controles: el oyente del motor,
	// que es quien las escribe, se registra antes.
	var lineasSubtitulo by remember { mutableStateOf<List<String>>(emptyList()) }

	// --- Zapeo con el mando ------------------------------------------------
	//
	// Dos canales y no uno. `enPantalla` cambia en el acto con cada pulsación,
	// porque la barra tiene que responder al instante o el mando se siente
	// roto. `sintonizado` lo sigue tras una pausa, y es el único que toca el
	// motor: mantener ▼ pulsado recorriendo veinte canales no puede reconstruir
	// veinte veces el decodificador.
	val catalogo = remember { LiveTvCatalog.of(context) }
	val canales by catalogo.state.collectAsState()
	val guia by catalogo.guia.collectAsState()

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

	// Un canal marcado como no disponible no se intenta reproducir.
	//
	// Es distinto de no publicarlo: el canal sigue en la guía, con su número y
	// su sitio, pero dice por qué no está. **Un canal que desaparece genera una
	// llamada a soporte**; uno que explica, no. Se comprueba antes de construir
	// plan ni motor: no tiene sentido abrir un decodificador para nada.
	if (channel.unavailable) {
		LiveLog.info(
			"canal.noDisponible",
			"canal" to channel.id,
			"motivo" to channel.unavailableMessage,
		)
		Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
			Column(modifier = Modifier.align(Alignment.Center)) {
				Text(text = channel.name, color = Color.White, fontSize = 26.sp)
				Text(
					text = channel.unavailableMessage ?: "Este canal no está disponible ahora",
					color = LiveTvPaleta.tintaApagada,
				)
			}
		}
		return
	}

	// Se recuerda el canal **sintonizado**, no el previsualizado: zapear con
	// ▲▼ pasea por la lista sin ver nada, y guardar cada paso dejaría de
	// último canal uno por el que solo se pasó.
	LaunchedEffect(channel.id) { LiveTvUltimoCanal.of(context).recordar(channel.id) }

	// El plan lo decide la cascada (M3), por canal y por caja. Se rehace si
	// cambian las capacidades, el canal o lo que diga el servidor.
	// Atado a `revision` porque el veto de decoders del menú cambia lo que la
	// caja «puede», y el plan tiene que rehacerse con la caja ya recortada.
	val caps = remember(revision) { DeviceCapabilities.of(context).excluyendo(dev.decodersDescartados) }
	val plan = remember(channel.id, revision, caps) {
		PlanBuilder.build(
			channel = channel,
			caps = caps,
			// `ajustesEfectivos` y no `ajustesDelServidor`: el menú puede
			// simular un modelo cuyo VPU no desentrelaza, que es la única
			// forma de probar el nivel 2 en esta caja (pendiente 63).
			servidor = dev.ajustesEfectivos,
			tsFlags = dev.tsFlags,
			forzarSoftwareLocal = dev.forceSoftwareVideo,
			motorForzadoLocal = dev.forcedEngine,
			forzarDeintLocal = dev.forzarDesentrelazado,
		).also {
			LiveLog.info(
				"plan.construido",
				"canal" to channel.id,
				"motor" to it.engineId,
				"video" to it.videoPath,
				"deint" to it.deinterlace,
				"offsetMs" to it.targetOffsetMs,
				"motivo" to it.reason,
			)
		}
	}

	// Un canal sin camino de decodificación se dice, no se deja en negro.
	if (plan.unsupported) {
		Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
			Column(modifier = Modifier.align(Alignment.Center)) {
				Text(text = channel.name, color = Color.White, fontSize = 26.sp)
				Text(
					text = "Este canal no se puede reproducir en este dispositivo",
					color = LiveTvPaleta.tintaApagada,
				)
				Text(text = plan.reason, color = LiveTvPaleta.tintaSutil, fontSize = 14.sp)
			}
		}
		return
	}

	// El motor se crea una sola vez por canal. Ojo con atarlo al SurfaceView:
	// la vista llega en una recomposición posterior, y si el efecto depende de
	// ella se construye un ExoPlayer que se libera acto seguido. Además de
	// desperdiciar ~100 ms, eso se paga en el zapping, que tiene objetivo duro
	// de p50 < 2 s (CLAUDE.md §10).
	// Atado al canal **sintonizado**: el de la barra cambia mientras se zapea y
	// reconstruir el motor en cada pulsación es justo lo que hay que evitar.
	val engine: LiveEngine = remember(channel.url, revision, plan.engineId) {
		when (plan.engineId) {
			PlanBuilder.MEDIA3_ENGINE_ID -> Media3LiveEngine(context.applicationContext, dev)
			PlanBuilder.VLC_ENGINE_ID -> VlcLiveEngine(context.applicationContext, dev)
			else -> {
				// Llegar aquí significa que alguien añadió un motor y olvidó
				// este punto: se registra y se sigue reproduciendo con Media3,
				// que es mejor que una pantalla negra.
				LiveLog.error(
					"plan.motorSinImplementacion",
					null,
					"canal" to channel.id,
					"motor" to plan.engineId,
				)
				Media3LiveEngine(context.applicationContext, dev)
			}
		}
	}

	LaunchedEffect(engine) {
		while (true) {
			stats = engine.stats()
			delay(STATS_REFRESH_MS)
		}
	}

	// §4.6: el códec del `config.json` es una pista; el stream es la verdad.
	// Se mira una vez por sintonía, cuando ya hay formato de vídeo: si lo
	// observado desmiente lo declarado, la cascada se rehace y el motor se
	// vuelve a preparar con el plan correcto.
	//
	// Solo se actúa si el plan **cambia de verdad**. En los seis canales del
	// lineup el códec declarado coincide, así que esto no debe dispararse
	// nunca aquí; existe para el lineup de 120, donde nadie garantiza que el
	// JSON siga al headend.
	var planRevisado by remember(channel.id, plan) { mutableStateOf(false) }
	LaunchedEffect(engine, stats.videoMime) {
		val observado = stats.videoMime ?: return@LaunchedEffect
		if (planRevisado) return@LaunchedEffect
		planRevisado = true

		// Se registra siempre, coincida o no. Sin esta línea, «no hubo
		// discrepancia» y «nunca se llegó a comprobar» se ven igual en el log,
		// y son cosas distintas cuando un canal no arranca.
		LiveLog.info(
			"plan.codecObservado",
			"canal" to channel.id,
			"declarado" to channel.videoCodec,
			"observado" to observado,
		)

		val nuevo = PlanBuilder.revisar(
			plan = plan,
			channel = channel,
			mimeObservado = observado,
			caps = caps,
			// `ajustesEfectivos` y no `ajustesDelServidor`: el menú puede
			// simular un modelo cuyo VPU no desentrelaza, que es la única
			// forma de probar el nivel 2 en esta caja (pendiente 63).
			servidor = dev.ajustesEfectivos,
			tsFlags = dev.tsFlags,
			forzarSoftwareLocal = dev.forceSoftwareVideo,
			motorForzadoLocal = dev.forcedEngine,
		) ?: return@LaunchedEffect

		if (nuevo.videoPath == plan.videoPath && nuevo.engineId == plan.engineId) {
			// El códec era otro pero el camino no cambia. Ya quedó registrado
			// por `revisar()`; rearrancar el canal no arreglaría nada y le
			// costaría al abonado un corte de dos segundos.
			return@LaunchedEffect
		}

		LiveLog.warn(
			"plan.rehecho",
			"canal" to channel.id,
			"antes" to plan.reason,
			"ahora" to nuevo.reason,
		)
		if (nuevo.engineId == plan.engineId) {
			engine.prepare(channel, nuevo)
		} else {
			// Cambiar de motor exige reconstruirlo, y eso no se hace desde
			// aquí: el motor está atado a `remember`. Queda registrado para
			// M6, cuando libVLC exista y haya a dónde conmutar.
			LiveLog.error(
				"plan.cambioDeMotorNoAplicado",
				null,
				"canal" to channel.id,
				"motorPedido" to nuevo.engineId,
			)
		}
	}

	DisposableEffect(engine, lifecycleOwner) {
		val listener = object : EngineListener {
			override fun onReady() { state = "Listo" }
			override fun onPlaying() { state = "En vivo"; detail = null }
			override fun onBuffering() { state = "Almacenando…" }

			override fun onCues(lineas: List<String>) { lineasSubtitulo = lineas }

			override fun onRecoverableError(event: String, error: Throwable?, causa: CausaDeFallo) {
				// Nunca se cierra el player: es un estado temporal (CLAUDE.md §6).
				//
				// **El mensaje no inventa la causa.** Hasta el 03-oct cualquier
				// error decía «señal débil por condiciones climáticas», incluida
				// una aserción interna de Media3 — y eso manda a un técnico a
				// revisar una antena que está perfecta.
				//
				// Y hasta el 19-oct lo seguía diciendo el caso de señal, que es
				// **cualquier** error de entrada/salida. Visto en M7 cortando la
				// red entera: el abonado leía «condiciones climáticas» con el
				// cielo despejado y el servidor apagado. La app no puede saber si
				// llueve; sabe que algo del camino falló y que está reintentando,
				// así que eso es lo que dice. El que lee un aviso falso deja de
				// creerse los verdaderos, y el soporte del ISP se acostumbra a
				// descartar como clima lo que puede ser su headend caído.
				//
				// Tiene que ser cierto además **mientras se sigue viendo bien**:
				// esto se pone en cada error recuperable, y muchos se reintentan
				// con éxito sin que la imagen llegue a pararse. El cartel central
				// de «Sin señal» es el que solo sale con el vigilante trabajando.
				state = when (causa) {
					CausaDeFallo.SENAL -> "Recuperando la señal…"
					CausaDeFallo.INTERNO -> "Reconectando…"
				}
				detail = event
			}

			override fun onFatalError(event: String, error: Throwable?) {
				state = "Este canal no es compatible con tu dispositivo"
				detail = event
			}
		}

		engine.addListener(listener)
		// El modo recordado, no `FIT` fijo: esto corre antes de que la
		// composición aplique el suyo, y con un valor distinto el motor recibía
		// primero uno y luego el otro. No se veía —pasa 1,4 s antes del primer
		// fotograma— pero libVLC guarda el último modo y lo reaplica al cambiar
		// de superficie, así que dejarlo mal aquí sí tendría consecuencias.
		engine.setAspectMode(LiveTvAspecto.of(context).modo)

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

	// El reloj y el progreso tienen que avanzar mientras la barra está puesta,
	// o se verían bien en una captura y mal en pantalla. Solo corre mientras se
	// ve: un temporizador latiendo bajo una barra escondida no pinta nada.
	var ahoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
	LaunchedEffect(barraVisible) {
		while (barraVisible) {
			ahoraMs = System.currentTimeMillis()
			delay(BAR_CLOCK_REFRESH_MS)
		}
	}

	// Releer la guía es un efecto, no parte de componer, así que va aquí y no
	// dentro del `remember` que la consulta. Cada minuto es de sobra: lo que se
	// vigila es que la ventana guardada siga llegando al presente, que es cosa
	// de horas, y la llamada no hace nada si ya la cubre.
	LaunchedEffect(ahoraMs / 60_000) { LiveTvCatalog.of(context).guiaSiHaceFalta(ahoraMs) }

	val favoritos = remember { LiveTvFavorites.of(context) }
	val favoritosIds by favoritos.ids.collectAsState()

	// --- Controles ----------------------------------------------------------
	//
	// `enControles` = el foco está en la fila, no en el canal. Se entra con ▶ y
	// se sale con ATRÁS, para no quitarle ▲▼ al zapeo, que es el gesto más
	// usado de todos.
	var enControles by remember { mutableStateOf(false) }
	var controlSeleccionado by remember { mutableIntStateOf(0) }
	var menuAbierto by remember { mutableStateOf<String?>(null) }
	var opcionSeleccionada by remember { mutableIntStateOf(0) }

	// El modo de ajuste se recuerda por caja: con un lineup de ~120 canales,
	// volver a elegirlo cada vez que se entra al reproductor es una vez por
	// sesión y para siempre. De fábrica sigue siendo `FIT` (ver [LiveTvAspecto]).
	val aspectoGuardado = remember { LiveTvAspecto.of(context) }
	var aspecto by remember { mutableStateOf(aspectoGuardado.modo) }

	// El modo se le dice **al motor**, no solo a la vista. libVLC aplica su
	// propia geometría —mantiene la proporción dentro de su ventana— y sin
	// esto «Llenar» dejaba franjas. Media3 lo ignora sin coste.
	LaunchedEffect(engine, aspecto) { engine.setAspectMode(aspecto) }
	var subtitulosActivos by remember { mutableStateOf(false) }
	var apagarEnMin by remember { mutableIntStateOf(0) }
	var reinicios by remember { mutableIntStateOf(0) }

	// Temporizador de apagado. Detiene la reproducción y devuelve a la lista:
	// apagar la caja no está en nuestra mano, y dejar el canal sonando toda la
	// noche es justo lo que se quería evitar.
	LaunchedEffect(apagarEnMin, reinicios) {
		if (apagarEnMin <= 0) return@LaunchedEffect
		delay(apagarEnMin * 60_000L)
		apagarEnMin = 0
		LiveLog.info("temporizador.cumplido")
		// Mismo camino que ATRÁS: apagar la caja no está en nuestra mano, y
		// dejar el canal sonando toda la noche es lo que se quería evitar.
		alSalir()
	}

	// **La barra la saca el abonado, no un parpadeo del reproductor.**
	//
	// Esto estaba atado a `enVivo`, así que cualquier cambio de estado la
	// mostraba. Y un rebuffer corriente son **tres** cambios seguidos
	// —`buffering`, `ready`, `playing`—: medido el 08-oct en `estrellassd`,
	// 371 ms en total. Invisible en pantalla, y aun así sacaba la barra encima
	// del vídeo. Edgar lo describió como «aparece a cada rato, a veces sin que
	// se note ningún microcorte», que es exactamente eso: la barra era más
	// visible que el fallo del que avisaba.
	LaunchedEffect(pulsacion) { barraVisible = true }

	// Se esconde sola **solo si el canal está dando imagen**. Con el canal
	// parado, la barra es lo único que le explica algo al abonado.
	LaunchedEffect(pulsacion, enVivo) {
		if (!enVivo) return@LaunchedEffect
		delay(CHANNEL_BAR_VISIBLE_MS)
		barraVisible = false
	}

	// Y si el parón va en serio, vuelve. El margen es lo que separa «se paró»
	// de «hubo un hipo»: por debajo de él no hay nada que contar, y contarlo
	// molesta más que el hipo.
	LaunchedEffect(enVivo) {
		if (enVivo) return@LaunchedEffect
		delay(CHANNEL_BAR_STALL_MS)
		barraVisible = true
	}

	fun vecino(paso: Int): ChannelProfile? {
		val lista = canales.channels
        if (lista.isEmpty()) return null
		val i = lista.indexOfFirst { it.id == enPantalla?.id }
		// Da la vuelta a propósito: en televisión, bajar desde el último lleva
		// al primero. Toparse con un muro invisible se siente como un fallo.
		return lista[((if (i < 0) 0 else i) + paso + lista.size) % lista.size]
	}

	// --- Qué controles hay, y qué hace cada uno -----------------------------
	//
	// Un control sin nada que ofrecer se enseña apagado, no se esconde: que el
	// botón de subtítulos desaparezca en unos canales y aparezca en otros se
	// lee como un fallo, y verlo en gris dice la verdad.
	val controles = remember(stats, aspecto, subtitulosActivos, favoritosIds, apagarEnMin, enPantalla) {
		listOf(
			Control(
				id = "audio",
				etiqueta = "Audio",
				valor = idiomaCorto(stats) ?: "—",
				disponible = stats.audioTracks.size > 1,
			),
			Control(
				id = "subtitulos",
				etiqueta = "Subtítulos",
				valor = if (subtitulosActivos) "sí" else "no",
				activo = subtitulosActivos,
				disponible = stats.subtitleTracks.isNotEmpty(),
			),
			Control(
				id = "imagen",
				etiqueta = "Imagen",
				valor = AspectoDeVideo.nombre(aspecto),
				activo = aspecto != AspectMode.FIT,
			),
			Control(
				id = "favorito",
				etiqueta = "Favorito",
				activo = (enPantalla ?: channel).id in favoritosIds,
			),
			Control(
				id = "resintonizar",
				etiqueta = "Volver a sintonizar",
			),
			Control(
				id = "apagado",
				etiqueta = "Apagar en",
				valor = if (apagarEnMin > 0) "${apagarEnMin} min" else "no",
				activo = apagarEnMin > 0,
			),
		)
	}

	fun activarControl(control: Control) {
		if (!control.disponible) return
		when (control.id) {
			"favorito" -> favoritos.toggle((enPantalla ?: channel).id)

			"resintonizar" -> {
				LiveLog.info("control.resintonizar", "canal" to channel.id)
				engine.stop()
				engine.prepare(channel, plan)
				engine.play()
				reinicios++
			}

			// Los de varias opciones abren su lista; los de una sola acción no.
			else -> { menuAbierto = control.id; opcionSeleccionada = 0 }
		}
	}

	fun aplicarOpcion(menu: String, indice: Int) {
		when (menu) {
			"audio" -> stats.audioTracks.getOrNull(indice)?.let { engine.selectAudioTrack(it) }

			"subtitulos" -> {
				// La primera opción es siempre «desactivados».
				val pista = if (indice == 0) null else stats.subtitleTracks.getOrNull(indice - 1)
				engine.selectSubtitleTrack(pista)
				subtitulosActivos = pista != null
				if (pista == null) lineasSubtitulo = emptyList()
			}

			"imagen" -> AspectoDeVideo.modos.getOrNull(indice)?.let {
				aspecto = it
				aspectoGuardado.modo = it
			}

			"apagado" -> apagarEnMin = MINUTOS_DE_APAGADO.getOrElse(indice) { 0 }
		}
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
				pulsacion++

				// Un menú abierto se queda con las teclas: ▲▼ recorren sus
				// opciones en vez de cambiar de canal.
				val menu = menuAbierto
				if (menu != null) {
					val n = opcionesDe(menu, stats, aspecto, subtitulosActivos).size
					return@onKeyEvent when (evento.key) {
						Key.DirectionUp -> { opcionSeleccionada = (opcionSeleccionada - 1 + n) % n; true }
						Key.DirectionDown -> { opcionSeleccionada = (opcionSeleccionada + 1) % n; true }
						Key.DirectionCenter, Key.Enter -> {
							aplicarOpcion(menu, opcionSeleccionada)
							menuAbierto = null
							true
						}
						Key.Back, Key.Escape -> { menuAbierto = null; true }
						else -> false
					}
				}

				if (enControles) {
					val lista = controles
					return@onKeyEvent when (evento.key) {
						Key.DirectionLeft -> {
							// Salir por la izquierda devuelve al canal: es el
							// gesto inverso de como se entró.
							if (controlSeleccionado == 0) enControles = false
							else controlSeleccionado--
							true
						}
						Key.DirectionRight -> {
							if (controlSeleccionado < lista.lastIndex) controlSeleccionado++
							true
						}
						Key.DirectionCenter, Key.Enter -> { activarControl(lista[controlSeleccionado]); true }
						Key.Back, Key.Escape -> { enControles = false; true }
						else -> false
					}
				}

				when (evento.key) {
					Key.DirectionUp -> { vecino(-1)?.let { enPantalla = it }; true }
					Key.DirectionDown -> { vecino(1)?.let { enPantalla = it }; true }
					Key.DirectionRight -> { enControles = true; controlSeleccionado = 0; true }
					Key.DirectionCenter, Key.Enter, Key.Info -> true
					// Todo lo demás se deja pasar: ATRÁS tiene que seguir
					// saliendo del canal.
					else -> false
				}
			}
	) {
		// **El `update` de un `AndroidView` corre en cada recomposición**, y las
		// estadísticas del overlay recomponen cada 500 ms. Con `attach` ahí
		// dentro eran 206 llamadas en 115 s, y el logcat se daba la vuelta en
		// menos de un minuto — justo el instrumento que hacen falta para juzgar
		// M4 y M5.
		//
		// `AspectoDeVideo.aplicar` **sí** quiere las estadísticas, porque el
		// tamaño y el SAR del vídeo llegan ahí. `attach` solo quiere la vista,
		// y la vista cambia muy de vez en cuando.
		var vista by remember { mutableStateOf<SurfaceView?>(null) }

		// **La superficie se dimensiona, no se escala.**
		//
		// `scaleX`/`scaleY` sobre un `SurfaceView` no llegan a la imagen: la
		// compone el sistema aparte del árbol de vistas. Lo que sí fija sus
		// límites es el tamaño que se le da al disponerla, que es lo que hace
		// el `AspectRatioFrameLayout` del `PlayerView` de Media3 — la pieza que
		// este módulo no usa.
		BoxWithConstraints(
			modifier = Modifier.fillMaxSize().clipToBounds(),
			contentAlignment = Alignment.Center,
		) {
			// **Con libVLC la superficie no se dimensiona.** Son dos geometrías
			// distintas porque son dos decodificadores distintos: MediaCodec
			// estira hasta llenar la caja que se le da, y libVLC mantiene la
			// proporción dentro de ella. Agrandar la superficie para «Llenar»
			// solo dejaba la imagen centrada con franjas más anchas — visto en
			// pantalla el 09-oct. El modo se lo aplica el propio motor.
			val mandaVlc = plan.engineId == PlanBuilder.VLC_ENGINE_ID
			val tamano = if (dev.superficieDimensionada && !mandaVlc) {
				AspectoDeVideo.tamanoDe(aspecto, stats, constraints.maxWidth, constraints.maxHeight)
			} else {
				null
			}
			val densidad = LocalDensity.current

			AndroidView(
				modifier = if (tamano == null) {
					Modifier.fillMaxSize()
				} else {
					with(densidad) { Modifier.size(tamano.first.toDp(), tamano.second.toDp()) }
				},
				// SurfaceView, nunca TextureView (CLAUDE.md §6).
				factory = { ctx -> SurfaceView(ctx).also { vista = it } },
				update = { view ->
					// El camino anterior, que el interruptor conserva para poder
					// comparar en pantalla si algún modelo se queda sin imagen.
					if (!dev.attachSoloAlCambiar) engine.attach(view)
					// Y el escalado anterior, que solo se aplica cuando no se
					// dimensiona: mezclar los dos escalaría dos veces.
					if (!dev.superficieDimensionada) AspectoDeVideo.aplicar(view, aspecto, stats)
				},
			)
		}

		// La superficie se entrega cuando existe; `prepare` no la necesita de
		// antemano y Media3 la acepta en caliente. Las dos claves importan: el
		// motor se rehace al cambiar de canal y al rescatar el tunneled, y
		// entonces hay que volver a entregársela aunque la vista sea la misma.
		LaunchedEffect(engine, vista, dev.attachSoloAlCambiar) {
			if (dev.attachSoloAlCambiar) vista?.let { engine.attach(it) }
		}

		if (barraVisible) {
			val mostrado = enPantalla ?: channel
			val sintonizando = mostrado.id != channel.id
			LiveTvChannelBar(
				channel = mostrado,
				state = if (sintonizando) "Sintonizando…" else state,
				detail = detail,
				// La guía del site. `null` cuando ese canal no tiene
				// programación o el site no publica EPG, y la barra ya sabe
				// enseñar el canal sin ella: un site sin guía es normal.
				//
				// Se recalcula cada medio minuto, que es la resolución a la que
				// se mueve una barra de progreso de programa, no cada
				// recomposición.
				epg = remember(mostrado.id, guia, ahoraMs / 30_000) {
					guia.franja(mostrado.id, ahoraMs)
				},
				ahoraMs = ahoraMs,
				favorito = mostrado.id in favoritosIds,
				idiomaAudio = idiomaCorto(stats),
				// La resolución medida manda sobre lo que declare el perfil:
				// la lista de arranque no trae `hd` y etiquetaba HBO como SD.
				altaDefinicion = mostrado.hd || stats.videoHeight >= 720,
				enVivo = enVivo && !sintonizando,
				controles = if (barraVisible) controles else emptyList(),
				controlSeleccionado = if (enControles) controlSeleccionado else -1,
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}

		menuAbierto?.let { menu ->
			LiveTvOpciones(
				titulo = tituloDeMenu(menu),
				opciones = opcionesDe(menu, stats, aspecto, subtitulosActivos),
				seleccionada = opcionSeleccionada,
				// Justo encima de la fila de controles, no en el centro: es
				// donde está mirando quien acaba de pulsar OK, y así no tapa
				// el nombre del canal ni el programa.
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(start = 56.dp, bottom = 150.dp),
			)
		}

		LiveTvSubtitulos(
			lineas = lineasSubtitulo,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = if (barraVisible) 300.dp else 72.dp),
		)


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
					color = LiveTvPaleta.tintaApagada,
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

/**
 * Cuánto tiene que llevar parado el canal para que la barra vuelva sola.
 *
 * Dos segundos porque por debajo de eso el abonado no ha llegado a ver nada
 * raro: el rebuffer medido en `estrellassd` duró 371 ms. Avisar de algo que no
 * se nota es empeorar la imagen para contar un no-suceso.
 */
private const val CHANNEL_BAR_STALL_MS = 2_000L

/** Lo que dura la barra en pantalla cuando el canal va bien. */
private const val CHANNEL_BAR_VISIBLE_MS = 6_000L

/** Opciones del temporizador. El 0 apaga el temporizador, no la tele. */
private val MINUTOS_DE_APAGADO = listOf(0, 30, 60, 90, 120)

private fun tituloDeMenu(menu: String): String = when (menu) {
	"audio" -> "Idioma del audio"
	"subtitulos" -> "Subtítulos"
	"imagen" -> "Ajuste de imagen"
	"apagado" -> "Apagar en"
	else -> menu
}

private fun opcionesDe(
	menu: String,
	stats: EngineStats,
	aspecto: AspectMode,
	subtitulosActivos: Boolean,
): List<String> = when (menu) {
	"audio" -> stats.audioTracks.map { it.label ?: it.language ?: "Pista" }
	"subtitulos" -> listOf("Desactivados") +
		stats.subtitleTracks.map { it.label ?: it.language ?: "Subtítulos" }
	"imagen" -> AspectoDeVideo.modos.map { AspectoDeVideo.nombre(it) }
	"apagado" -> MINUTOS_DE_APAGADO.map { if (it == 0) "No apagar" else "$it minutos" }
	else -> emptyList()
}

/** Cada cuánto se refresca el reloj y el progreso mientras la barra se ve. */
private const val BAR_CLOCK_REFRESH_MS = 1_000L

/**
 * «spa» → «ES». Lo que lee el abonado, no el código ISO.
 *
 * Sale de la pista que suena, no de la lista: en `discoveryhd` y `hbohd` hay
 * español e inglés, y lo que importa es en cuál estás.
 */
private fun idiomaCorto(stats: EngineStats): String? {
	val lang = stats.selectedAudio?.language ?: stats.audioTracks.firstOrNull()?.language
	return when (lang?.lowercase()) {
		null -> null
		"spa", "esp", "esl", "es" -> "ES"
		"eng", "en" -> "EN"
		else -> lang.take(2).uppercase()
	}
}


