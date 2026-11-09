package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.model.LiberarTele
import org.jellyfin.androidtv.auth.model.SalidaDeCuenta
import org.jellyfin.androidtv.auth.model.decidirLiberarTele
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.util.UUID

/**
 * Le dice a Jellyfin que esta caja deja de usar una cuenta.
 *
 * La decisión de **si** hay que avisar es de [decidirLiberarTele], que es pura
 * y tiene pruebas. Esto es la otra mitad: hacerlo, acotarlo en el tiempo y
 * dejar escrito qué pasó.
 *
 * ## La regla que manda sobre todas las demás
 *
 * **Nunca retener al abonado.** Si el servidor no contesta, contesta tarde o
 * contesta mal, se registra y se sigue: el token se olvida igual. El cupo
 * atascado lo puede desbloquear el proveedor desde el panel de abonados; una
 * caja que no deja cerrar sesión no la desbloquea nadie.
 *
 * Por eso aquí no se propaga ninguna excepción salvo la cancelación real de la
 * corrutina, que sí tiene que subir — tragársela dejaría viva una tarea que el
 * sistema dio por muerta.
 */
class LiberadorDeTele(
	private val jellyfin: Jellyfin,
	private val defaultDeviceInfo: DeviceInfo,
) {
	/**
	 * @param direccion la del servidor Jellyfin, para el log y para la llamada.
	 * @param usuario el id del usuario, que es lo que construye el `deviceId`
	 *   con el que esa sesión se autenticó ([forUser]).
	 * @param token el de esa sesión. `POST /Sessions/Logout` se autentica con
	 *   él, así que sin token no hay llamada posible.
	 */
	suspend fun liberar(
		salida: SalidaDeCuenta,
		direccion: String,
		usuario: UUID,
		token: String?,
	) {
		val decision = decidirLiberarTele(salida, tieneToken = !token.isNullOrBlank())

		if (decision is LiberarTele.NoLibera) {
			Timber.i(
				"tele.noLiberada salida=%s servidor=%s usuario=%s motivo=%s",
				salida, direccion, usuario, decision.motivo,
			)
			return
		}

		val api = jellyfin.createApi(
			baseUrl = direccion,
			accessToken = token,
			deviceInfo = defaultDeviceInfo.forUser(usuario),
		)

		try {
			withTimeout(ESPERA_MS) { api.sessionApi.reportSessionEnded() }
			Timber.i(
				"tele.liberada salida=%s servidor=%s usuario=%s",
				salida, direccion, usuario,
			)
		} catch (err: TimeoutCancellationException) {
			// Tiene que ir **antes** que `CancellationException`, de la que
			// hereda: si no, el agotamiento del plazo se confundiría con que
			// alguien canceló la pantalla, y se relanzaría.
			Timber.w(
				err, "tele.noLiberada salida=%s servidor=%s usuario=%s motivo=agotó los %d ms",
				salida, direccion, usuario, ESPERA_MS,
			)
		} catch (err: CancellationException) {
			throw err
		} catch (err: InvalidStatusException) {
			// El código es el dato que faltaba el 09-nov. En producción el
			// cuerpo del 403 es «Error processing request.», así que sin el
			// número no se distingue una cuenta suspendida —que probablemente
			// tampoco libera, y es lo que avisa `peticion-liberar-tele.md` §5—
			// de un token ya caducado.
			Timber.w(
				err, "tele.noLiberada salida=%s servidor=%s usuario=%s codigo=%d",
				salida, direccion, usuario, err.status,
			)
		} catch (err: Exception) {
			Timber.w(
				err, "tele.noLiberada salida=%s servidor=%s usuario=%s motivo=%s",
				salida, direccion, usuario, err::class.simpleName,
			)
		}
	}

	companion object {
		/**
		 * Lo que se espera a que Jellyfin conteste, y ni un milisegundo más.
		 *
		 * Cinco segundos, los mismos que usa Roku (`Sintonia.LIBERAR_TELE_ESPERA_MS`),
		 * para que las dos apps se comporten igual ante el mismo site.
		 *
		 * **No vive en `LiveTuning` pese a §5.7**, y es deliberado: `LiveTuning`
		 * es del módulo `livetv`, que por §4.1 **no habla con el servidor
		 * Jellyfin para nada**. Meter ahí un plazo de una llamada a Jellyfin
		 * sería la primera grieta en ese aislamiento.
		 */
		const val ESPERA_MS = 5_000L
	}
}
