package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.livetv.config.Brand
import org.jellyfin.livetv.config.ChannelGroup
import org.jellyfin.livetv.config.Acceso
import org.jellyfin.livetv.config.DecisorDeAcceso
import org.jellyfin.livetv.config.RegistryRepository
import org.jellyfin.livetv.config.SiteAuthorization
import org.jellyfin.livetv.config.ConfigOrigin
import org.jellyfin.livetv.config.ConfigRepository
import org.jellyfin.livetv.config.OperatorMessage
import org.jellyfin.livetv.config.EpgPointer
import org.jellyfin.livetv.config.ServiceUrl
import org.jellyfin.livetv.epg.EpgRepository
import org.jellyfin.livetv.epg.Guia
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.ServerDeviceSettings
import org.jellyfin.livetv.source.ChannelExtra
import org.jellyfin.livetv.source.ChannelProfile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Lo que la sección enseña: canales, grupos y de dónde salieron.
 *
 * `origin` no es decorativo. Una caja que lleva días con una lista vieja porque
 * el servidor no responde se comporta igual que una al día, y sin este dato
 * nadie puede distinguirlas cuando un suscriptor llama.
 */
data class LiveCatalog(
	val channels: List<ChannelProfile>,
	val groups: List<ChannelGroup>,
	val origin: ConfigOrigin,
	val loading: Boolean = false,
	/** Aviso del operador, si lo hay y sigue vigente. */
	val message: OperatorMessage? = null,
	/** `id` del canal que abre una caja recién instalada. */
	val defaultChannel: String? = null,
	/** La marca del proveedor. Nunca nula: la neutra es una marca. */
	val brand: Brand = Brand.NEUTRA,
	/** Texto libre del ISP para la sección entera, ya acotado por el parser. */
	val extras: List<ChannelExtra> = emptyList(),
	/**
	 * Si se entra a la sección, según el registro de sites.
	 *
	 * Permitido mientras no se demuestre lo contrario: la sección nunca se
	 * cierra por no haber preguntado todavía.
	 */
	val acceso: Acceso = Acceso.Permitido(motivo = "sin comprobar"),
)

/**
 * El catálogo de canales de la sección, con su respaldo.
 *
 * Une el cliente del servidor de configuración con la pantalla, y garantiza lo
 * único que no es negociable: **la sección nunca se queda vacía**. Si el
 * servidor no responde y tampoco hay caché, se cae a la lista de arranque del
 * `BuildConfig`, que es la que ha venido sosteniendo las fases de motor.
 *
 * Instancia única, como [org.jellyfin.livetv.diagnostics.DevOptions]: la lista
 * y el reproductor tienen que ver exactamente los mismos canales, o abrir un
 * canal desde la lista llevaría a otro.
 */
class LiveTvCatalog private constructor(context: Context) : KoinComponent {

	private val repository = ConfigRepository(context)
	private val epgRepository = EpgRepository(context)
	private val registro = RegistryRepository(context).apply {
		appVersion = BuildConfig.VERSION_NAME
	}

	/** Lo último que el registro dijo de este host. Decide URL, marca y acceso. */
	@Volatile
	private var autorizacion: SiteAuthorization? = null
	private val dev = DevOptions.of(context)
	private val servidores by inject<ServerRepository>()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private val _state = MutableStateFlow(
		LiveCatalog(
			channels = BootstrapChannels.all,
			groups = emptyList(),
			origin = ConfigOrigin.NINGUNO,
			brand = Brand.NEUTRA,
		)
	)
	val state: StateFlow<LiveCatalog> get() = _state

	/**
	 * La última URL servida, para no repetir la descarga en cada entrada a la
	 * sección **pero sí** rehacerla si la caja cambia de servidor.
	 */
	@Volatile
	private var urlCargada: String? = null

	/** Cuándo se bajó por última vez, para no dejar la lista congelada. */
	@Volatile
	private var ultimaCargaMs: Long = 0L

	/**
	 * La guía, **en un flujo aparte del catálogo**.
	 *
	 * No es organización: es evitar un fallo concreto. `LiveCatalog` se compara
	 * consigo mismo para no reemitir cuando nada cambió, porque reemitirlo
	 * rehace las tarjetas de la guía y al rehacerlas el foco salta al canal
	 * inicial. Meter la programación dentro obligaría a que [Guia] participara
	 * en esa comparación, y una guía nueva cada pocas horas haría saltar el
	 * foco de quien estuviera mirando.
	 *
	 * Van por caminos distintos además porque tienen urgencias distintas: la
	 * lista tiene que aparecer ya y pesa 40 kB; la guía puede tardar y pesa
	 * megas. Quien espera a la segunda para enseñar la primera ha elegido mal.
	 */
	private val _guia = MutableStateFlow(Guia.VACIA)
	val guia: StateFlow<Guia> get() = _guia

	/** Lo último que dijo el `config.json` sobre dónde está la guía. */
	@Volatile
	private var punteroEpg: EpgPointer? = null

	@Volatile
	private var claveEpg: String? = null

	@Volatile
	private var canalesParaEpg: Set<String> = emptySet()

	@Volatile
	private var cargandoGuia = false

	/**
	 * Baja la lista si hace falta: porque la caja cambió de servidor, o porque
	 * la que tiene ya tiene sus buenos minutos.
	 *
	 * **Lo segundo no estaba y era un defecto de verdad.** Esto se pedía una
	 * vez por proceso, y una caja de salón puede tener la app abierta días: el
	 * operador quitaba un canal, o ponía un aviso con su caducidad, y no
	 * llegaba a nadie que no reiniciara la app. El aviso podía caducar antes de
	 * que alguien lo viera.
	 */
	fun refreshSiHaceFalta() {
		val (url, _) = destino()
		val cambioDeServidor = urlCargada == null || urlCargada != url
		val caducada = System.currentTimeMillis() - ultimaCargaMs >= LiveTuning.CONFIG_REFRESH_INTERVAL_MS
		if (!cambioDeServidor && !caducada) return

		// Un refresco por tiempo no enseña «actualizando…»: quien está mirando
		// la guía no pidió nada y no tiene por qué ver parpadear un aviso.
		refresh(silencioso = !cambioDeServidor)
	}

	/**
	 * A qué servidor de canales toca mirar, y bajo qué clave guardarlo.
	 *
	 * La URL **se deriva del host de Jellyfin** (`CLAUDE.md` §6): el servicio
	 * de canales vive en la misma máquina, en su propio puerto. El menú puede
	 * sustituirla para apuntar a un servidor de pruebas, y el `BuildConfig`
	 * queda como último recurso para builds de desarrollo sin sesión.
	 *
	 * La clave de caché es el **identificador** del servidor, no su dirección,
	 * para que un ISP que cambie de dominio no pierda su lista.
	 */
	private fun destino(): Pair<String?, String> {
		val servidor = servidores.currentServer.value
		val manual = dev.configUrlOverride
		val url = manual
			// El registro **precede** a la convención del puerto, no la
			// sustituye (`REGISTRY_SCHEMA.md` §5). Es lo que permite mover el
			// servicio de canales de un site editando una fila en vez de
			// publicando una versión de la app.
			?: autorizacion?.takeIf { it.authorized }?.channelsUrl
			?: ServiceUrl.configDesdeServidor(servidor?.address)
			?: BuildConfig.LIVETV_CONFIG_URL.takeIf { it.isNotBlank() }

		// Una lista de pruebas no se guarda en la caché del site real. La clave
		// es el servidor de Jellyfin, pero el override cambia la **fuente** sin
		// cambiar el servidor: sin esto, el técnico que apunta una caja a su
		// portátil la deja con el lineup equivocado cuando se va.
		val clave = when {
			manual != null -> "manual"
			else -> servidor?.id?.toString() ?: "sin-servidor"
		}
		return url to clave
	}

	/**
	 * Pregunta al registro **solo si hace falta**.
	 *
	 * Una consulta por site, no por arranque: mientras la autorización guardada
	 * siga viva no se vuelve a preguntar (`REGISTRY_SCHEMA.md` §4). Eso fija la
	 * carga del registro en unas dos peticiones por minuto con diez ISPs, y es
	 * lo que le permite ser un servicio modesto.
	 */
	private suspend fun comprobarAcceso(): Acceso {
		// El simulador del menú va primero y no toca la red: lo que se quiere
		// mirar es la pantalla, no el camino que lleva a ella (pendiente 46).
		dev.simularDenegacion.takeIf { it > 0 }?.let { cual ->
			val mensaje = if (cual == 2) {
				DecisorDeAcceso.DENEGADO_CADUCADA
			} else {
				DecisorDeAcceso.DENEGADO_GENERICO
			}
			LiveLog.warn("registro.denegacionSimulada", "texto" to cual)
			return Acceso.Denegado(mensaje = mensaje, motivo = "simulada desde el menú")
		}

		val host = ServiceUrl.hostDeServidor(servidores.currentServer.value?.address)
		if (host == null) {
			// Sin sesión no hay host que preguntar. Pasa en builds de
			// desarrollo que apuntan al `BuildConfig`; no es motivo para
			// cerrar nada.
			return Acceso.Permitido(motivo = "sin host de Jellyfin que consultar")
		}

		val guardada = registro.guardada(host)
		autorizacion = guardada

		val ahora = System.currentTimeMillis()
		if (guardada != null && guardada.vigente(ahora)) {
			return DecisorDeAcceso.decidir(guardada, null, ahora)
				.also { LiveLog.info("registro.vigente", "host" to host, "hasta" to guardada.untilEpochMs) }
		}

		val base = dev.registryUrlOverride ?: BuildConfig.REGISTRY_URL.takeIf { it.isNotBlank() }
		val respuesta = registro.resolver(base, host)
		if (respuesta != null) autorizacion = respuesta

		return DecisorDeAcceso.decidir(guardada, respuesta, ahora).also { acceso ->
			LiveLog.info(
				"registro.acceso",
				"host" to host,
				"resultado" to if (acceso is Acceso.Denegado) "DENEGADO" else "permitido",
				"motivo" to when (acceso) {
					is Acceso.Permitido -> acceso.motivo
					is Acceso.Denegado -> acceso.motivo
				},
			)
		}
	}

	fun refresh(silencioso: Boolean = false) {
		scope.launch {
			if (!silencioso) _state.value = _state.value.copy(loading = true)

			// El registro va **antes** de mirar el destino: puede decir dónde
			// está el servicio de canales de este site.
			val acceso = comprobarAcceso()

			val (url, clave) = destino()
			urlCargada = url
			LiveLog.info(
				"catalogo.destino",
				"url" to url,
				"servidor" to clave,
				"procedencia" to when {
					dev.configUrlOverride != null -> "menú"
					autorizacion?.channelsUrl != null -> "registro"
					ServiceUrl.configDesdeServidor(servidores.currentServer.value?.address) != null -> "derivada de Jellyfin"
					else -> "BuildConfig"
				},
			)
			val resultado = repository.load(url, clave)
			val config = resultado.config
			ultimaCargaMs = System.currentTimeMillis()

			// Los ajustes por modelo de caja se aplican **antes** de enseñar la
			// lista, porque gobiernan cómo se reproducirá el primer canal que
			// alguien abra. Es lo que convierte una caja nueva que falla en un
			// cambio de `config.json` en vez de una visita.
			dev.aplicarDelServidor(config?.settingsForThisDevice()?.let { o ->
				ServerDeviceSettings(
					engine = o.engine,
					tunneling = o.tunneling,
					tsAllowNonIdrKeyframes = o.tsAllowNonIdrKeyframes,
					tsDetectAccessUnits = o.tsDetectAccessUnits,
					forceSoftwareVideo = o.forceSoftwareVideo,
					targetOffsetMs = o.targetOffsetMs,
					// **`vpuDeinterlaces` no estaba aquí**, así que el campo
					// viajaba en el `config.json`, se interpretaba y se quedaba
					// en el camino: el nivel 2 de la cascada no podía
					// dispararse en producción ni publicándolo (pendiente 66).
					vpuDeinterlaces = o.vpuDeinterlaces,
					blockedDecoders = o.blockedDecoders,
				)
			})

			val nuevo = if (config == null) {
				// El respaldo de última instancia. Que se registre importa: una
				// sección servida desde el `BuildConfig` está enseñando los
				// canales de prueba, no el lineup del cliente.
				LiveLog.warn(
					"catalogo.desdeArranque",
					"canales" to BootstrapChannels.all.size,
					"motivo" to "sin configuración del servidor ni caché",
				)
				LiveCatalog(
					numerarArranque(BootstrapChannels.all),
					emptyList(),
					ConfigOrigin.NINGUNO,
					acceso = acceso,
				)
			} else {
				// La marca que se registra es **la que se va a pintar**, no la
				// que sirvió el site. Con el registro pisando a la del site, un
				// log que informara de la del site diría «neutra» mientras la
				// pantalla enseña otra cosa, y eso manda a buscar donde no es.
				val marcaEfectiva = autorizacion?.brand ?: config.brand
				LiveLog.info(
					"catalogo.listo",
					"origen" to resultado.origin,
					"canales" to config.channels.size,
					"grupos" to config.groups.size,
					"marca" to marcaEfectiva.displayName,
					"marcaOrigen" to if (autorizacion?.brand != null) "registro" else marcaEfectiva.origen,
				)
				LiveCatalog(
					channels = ordenar(config.channels),
					groups = config.groups,
					origin = resultado.origin,
					message = config.message?.takeIf { it.vigente(System.currentTimeMillis()) },
					defaultChannel = config.defaultChannel,
					// La marca del registro pisa a la del site (§5): es la
					// nuestra, y la del site la pone el ISP.
					brand = marcaEfectiva,
					extras = config.extras,
					acceso = acceso,
				)
			}

			// **Solo se emite si de verdad cambió.**
			//
			// Con el refresco por tiempo, un catálogo idéntico emitido cada
			// cuarto de hora rehace las tarjetas de la guía, y al rehacerlas el
			// foco se pierde y vuelve al canal inicial: a quien estuviera
			// mirando el 214 le saltaría solo, sin tocar nada. Comparar es
			// barato —son `data class` hasta el fondo— y deja el caso normal,
			// que es «no cambió nada», en cero recomposiciones.
			val actual = _state.value
			_state.value = if (nuevo == actual.copy(loading = false)) {
				actual.copy(loading = false)
			} else {
				nuevo
			}

			// La guía va **después** de enseñar los canales y en su propia
			// corrutina: pesa megas y la lista no tiene por qué esperarla.
			punteroEpg = config?.epg
			claveEpg = clave
			canalesParaEpg = nuevo.channels.map { it.id }.toSet()
			cargarGuia()
		}
	}

	/**
	 * Trae la guía, si este site publica alguna.
	 *
	 * No devuelve nada ni falla hacia fuera: un site sin EPG es normal
	 * (`CONFIG_SCHEMA.md` §5) y la barra ya sabe enseñar un canal sin
	 * programación. Lo único que no puede pasar es que esto impida ver
	 * televisión, y por eso no bloquea a nadie.
	 */
	private fun cargarGuia() {
		val puntero = punteroEpg
		val clave = claveEpg
		if (puntero == null || clave == null) {
			_guia.value = Guia.VACIA
			return
		}
		if (cargandoGuia) return
		cargandoGuia = true
		scope.launch {
			try {
				_guia.value = epgRepository.load(
					epgUrl = puntero.url,
					refreshMinutes = puntero.refreshMinutes,
					claveDeCache = clave,
					canalesConocidos = canalesParaEpg,
				).guia
			} finally {
				cargandoGuia = false
			}
		}
	}

	/**
	 * Relee la guía si la que hay en memoria ya no llega al presente.
	 *
	 * **Es un reloj distinto del refresco.** Una guía se vuelve a *descargar*
	 * cuando caduca lo que dice `refreshMinutes`; se vuelve a *leer* cuando el
	 * tiempo sale de la ventana que se guardó, y eso pasa sin que el servidor
	 * publique nada. Una caja encendida toda la noche necesita lo segundo:
	 * a las seis de la mañana, la ventana que se leyó a medianoche ya no tiene
	 * nada que decir, y sin esto la barra se quedaría muda hasta que alguien
	 * reiniciara la app.
	 *
	 * Lo llama quien pinta, no un temporizador: si nadie mira, no hace falta.
	 */
	fun guiaSiHaceFalta(ahoraMs: Long = System.currentTimeMillis()) {
		if (punteroEpg == null) return
		if (_guia.value.cubre(ahoraMs)) return
		cargarGuia()
	}

	/**
	 * Orden de la guía: primero el número, y los que no lo traen al final por
	 * nombre.
	 *
	 * Un lineup sin números es raro pero posible mientras el operador lo va
	 * escribiendo, y dejar esos canales intercalados al azar haría parecer rota
	 * una guía que solo está a medio llenar.
	 */
	private fun ordenar(canales: List<ChannelProfile>): List<ChannelProfile> =
		canales.sortedWith(
			compareBy(
				{ it.number ?: Int.MAX_VALUE },
				{ it.order ?: Int.MAX_VALUE },
				{ it.name.lowercase() },
			)
		)

	/**
	 * Numera la lista de arranque del 1 en adelante.
	 *
	 * Sin números, marcar el canal con el mando no funcionaría hasta que
	 * exista el `config.json` del servidor — y entonces esa parte de la
	 * interfaz no se podría probar en todo ese tiempo. Los números reales
	 * llegan del servidor y pisan a estos.
	 */
	private fun numerarArranque(canales: List<ChannelProfile>): List<ChannelProfile> =
		canales.mapIndexed { i, canal -> canal.copy(number = canal.number ?: (i + 1)) }

	fun byNumber(number: Int): ChannelProfile? =
		_state.value.channels.firstOrNull { it.number == number }

	/**
	 * El canal pedido; si no se pide ninguno, el que abre una caja nueva.
	 *
	 * El respaldo es `defaultChannel` y solo después el primero de la lista. Sin
	 * eso, una caja recién instalada abre lo que haya quedado primero según cómo
	 * esté numerado el lineup, que puede ser cualquier cosa — y es el primer
	 * momento en que el abonado se forma una opinión del servicio.
	 */
	fun byId(id: String?): ChannelProfile? {
		val estado = _state.value
		val canales = estado.channels
		return canales.firstOrNull { it.id == id }
			?: canales.firstOrNull { it.id == estado.defaultChannel }
			?: canales.firstOrNull()
	}

	companion object {
		@Volatile
		private var instancia: LiveTvCatalog? = null

		fun of(context: Context): LiveTvCatalog =
			instancia ?: synchronized(this) {
				instancia ?: LiveTvCatalog(context.applicationContext).also { instancia = it }
			}
	}
}
