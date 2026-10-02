package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.livetv.config.ChannelGroup
import org.jellyfin.livetv.config.ConfigOrigin
import org.jellyfin.livetv.config.ConfigRepository
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.diagnostics.ServerDeviceSettings
import org.jellyfin.livetv.source.ChannelProfile

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
class LiveTvCatalog private constructor(context: Context) {

	private val repository = ConfigRepository(context, BuildConfig.LIVETV_CONFIG_URL)
	private val dev = DevOptions.of(context)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private val _state = MutableStateFlow(
		LiveCatalog(
			channels = BootstrapChannels.all,
			groups = emptyList(),
			origin = ConfigOrigin.NINGUNO,
		)
	)
	val state: StateFlow<LiveCatalog> get() = _state

	/** Para no repetir la descarga cada vez que se entra a la sección. */
	@Volatile
	private var yaCargado = false

	fun refreshOnce() {
		if (yaCargado) return
		yaCargado = true
		refresh()
	}

	fun refresh() {
		scope.launch {
			_state.value = _state.value.copy(loading = true)
			val resultado = repository.load()
			val config = resultado.config

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
				)
			})

			_state.value = if (config == null) {
				// El respaldo de última instancia. Que se registre importa: una
				// sección servida desde el `BuildConfig` está enseñando los
				// canales de prueba, no el lineup del cliente.
				LiveLog.warn(
					"catalogo.desdeArranque",
					"canales" to BootstrapChannels.all.size,
					"motivo" to "sin configuración del servidor ni caché",
				)
				LiveCatalog(numerarArranque(BootstrapChannels.all), emptyList(), ConfigOrigin.NINGUNO)
			} else {
				LiveLog.info(
					"catalogo.listo",
					"origen" to resultado.origin,
					"canales" to config.channels.size,
					"grupos" to config.groups.size,
				)
				LiveCatalog(ordenar(config.channels), config.groups, resultado.origin)
			}
		}
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

	fun byId(id: String?): ChannelProfile? {
		val canales = _state.value.channels
		return canales.firstOrNull { it.id == id } ?: canales.firstOrNull()
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
