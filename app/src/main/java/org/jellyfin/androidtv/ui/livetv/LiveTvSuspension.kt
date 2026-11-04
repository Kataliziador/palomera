package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.SessionFailure
import org.jellyfin.androidtv.auth.model.asSessionFailure
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Vigila que el contrato del abonado siga vivo mientras la sección está
 * abierta. Es el **pendiente 84**.
 *
 * ## Qué arregla
 *
 * La sección de canales sólo usa la sesión de Jellyfin para sacar el host del
 * servidor de canales (`LiveTvCatalog`), así que una caja **encendida** seguía
 * dando televisión después de que el operador suspendiera al abonado: el corte
 * esperaba al siguiente arranque, y una caja de salón pasa días sin
 * reiniciarse. Para el ISP, eso es que el corte por impago no sirva de nada.
 *
 * ## Por qué preguntar basta, y qué se pregunta
 *
 * No hace falta inventar ningún protocolo: **el propio Jellyfin del ISP ya
 * contesta 403 a cualquier petición de un abonado suspendido**, y eso está
 * medido —no supuesto— contra 10.11.11 y 12.0.0 el 28-oct, reutilizando el
 * token de una caja ya autenticada. La clasificación de los tres casos vive en
 * [SessionFailure] y aquí sólo se consume.
 *
 * Se pregunta por `GET /Users/Me`, que es la llamada más barata que evalúa la
 * política del usuario.
 *
 * ## Las tres respuestas, y por qué sólo una cierra
 *
 * | | qué significa | qué hace |
 * |---|---|---|
 * | **403** | contrato suspendido, el token sigue vivo | cierra la sección |
 * | **no contesta** | el site apagado, sin red, reiniciándose | **nada**, y reintenta antes |
 * | 401 y lo demás | la credencial ya no sirve | **nada**: de eso ya se encarga la puerta de la sesión al arrancar |
 *
 * Que un «no contesta» no cierre nada no es prudencia: es que en el despliegue
 * normal la caja llega a la LAN del ISP antes que a cualquier otra cosa, y
 * cortar la televisión porque un servidor tardó en responder sería convertir
 * cada hipo del site en una llamada a soporte. Es la misma regla que §6 ya
 * aplica al registro: *si no contesta y la autorización sigue viva, se entra*.
 *
 * ## Lo que **no** hace
 *
 * No toca el VOD. La suspensión real afecta a todo, pero películas y series
 * son el invariante de §4 y su camino no se modifica desde aquí; quien corta
 * ahí es la puerta de la sesión, al arrancar. Esto cubre lo que el pendiente
 * 84 pedía: que la **sección de canales** se entere sin esperar a un reinicio.
 *
 * Y no decide cuándo se corta la imagen: eso lo deciden las pantallas. Aquí
 * sólo se sabe si el contrato está vivo.
 */
class LiveTvSuspension private constructor(context: Context) : KoinComponent {

	private val api by inject<ApiClient>()
	private val dev = DevOptions.of(context)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private val _suspendido = MutableStateFlow(false)

	/** `true` sólo cuando el servidor del ISP ha contestado 403. */
	val suspendido: StateFlow<Boolean> get() = _suspendido

	@Volatile
	private var ultimaMs: Long = 0L

	@Volatile
	private var preguntando = false

	/**
	 * Pregunta si toca: al entrar a la sección y cada cuarto de hora.
	 *
	 * Idempotente y sin bloquear a nadie. Si el interruptor está apagado no
	 * hace nada **y además olvida lo que supiera**, para que apagarlo en el
	 * menú devuelva la sección al instante en vez de dejarla cerrada hasta el
	 * siguiente arranque.
	 */
	fun comprobarSiHaceFalta(ahoraMs: Long = System.currentTimeMillis()) {
		// El simulador manda y no pregunta a nadie: sirve para ver las dos
		// pantallas sin suspender de verdad a un abonado de un site vivo.
		if (dev.simularSuspension) {
			if (!_suspendido.value) _suspendido.value = true
			return
		}
		if (!dev.vigilarSuspensionEnabled) {
			if (_suspendido.value) _suspendido.value = false
			return
		}
		if (preguntando) return
		// El mismo ritmo esté suspendido o no: preguntar igual cuando lo está
		// es lo que hace que la sección vuelva sola en cuanto el abonado pague,
		// sin que nadie reinicie la caja.
		if (ultimaMs != 0L && ahoraMs - ultimaMs < LiveTuning.VIGILAR_SUSPENSION_CADA_MS) return
		preguntar()
	}

	private fun preguntar() {
		preguntando = true
		scope.launch {
			val fallo = withContext(Dispatchers.IO) {
				runCatching { api.userApi.getCurrentUser() }
					.fold(onSuccess = { null }, onFailure = { causa ->
						(causa as? ApiClientException)?.asSessionFailure() ?: SessionFailure.UNREACHABLE
					})
			}
			val decision = decidir(fallo)
			when (decision) {
				Decision.ABRIR -> {
					if (_suspendido.value) LiveLog.info("abonado.reactivado")
					_suspendido.value = false
				}

				Decision.CERRAR -> {
					if (!_suspendido.value) {
						LiveLog.warn("abonado.suspendido", "detalle" to "403 del servidor del ISP")
					}
					_suspendido.value = true
				}

				Decision.DEJARLO_COMO_ESTA, Decision.REINTENTAR_PRONTO -> {
					if (fallo == SessionFailure.UNREACHABLE) LiveLog.debug("abonado.sinRespuesta")
					else LiveLog.warn("abonado.credencialInvalida")
				}
			}
			// Un «no contesta» no gasta el cuarto de hora: se vuelve a
			// preguntar en un minuto. Ver [Decision.REINTENTAR_PRONTO].
			ultimaMs = if (decision == Decision.REINTENTAR_PRONTO) {
				System.currentTimeMillis() -
					LiveTuning.VIGILAR_SUSPENSION_CADA_MS +
					LiveTuning.VIGILAR_SUSPENSION_REINTENTO_MS
			} else {
				System.currentTimeMillis()
			}
			preguntando = false
		}
	}

	companion object {
		@Volatile
		private var instancia: LiveTvSuspension? = null

		/** Instancia única: la guía y el reproductor tienen que ver lo mismo. */
		fun of(context: Context): LiveTvSuspension =
			instancia ?: synchronized(this) {
				instancia ?: LiveTvSuspension(context.applicationContext).also { instancia = it }
			}
	}
}

/**
 * Qué hacer con la sección según lo que contestó el servidor del ISP.
 *
 * Está fuera de la clase **para poder probarla sin un Android delante**: lo
 * que de verdad hay que proteger de un descuido futuro no es la petición, es
 * la tabla de decisiones —qué respuesta cierra la televisión y cuál no—.
 */
internal enum class Decision {
	/** Contrato vivo: la sección se abre, y si estaba cerrada se reabre sola. */
	ABRIR,

	/** 403: contrato suspendido. Es la **única** respuesta que cierra. */
	CERRAR,

	/** No contesta: no se toca nada y se pregunta en un minuto, no en quince. */
	REINTENTAR_PRONTO,

	/** 401 y lo demás: tampoco se toca. De eso se encarga la puerta de la sesión. */
	DEJARLO_COMO_ESTA,
}

internal fun decidir(fallo: SessionFailure?): Decision = when (fallo) {
	null -> Decision.ABRIR
	SessionFailure.SUSPENDED -> Decision.CERRAR
	SessionFailure.UNREACHABLE -> Decision.REINTENTAR_PRONTO
	SessionFailure.INVALID -> Decision.DEJARLO_COMO_ESTA
}
