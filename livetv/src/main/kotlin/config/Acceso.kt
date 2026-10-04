package org.jellyfin.livetv.config

import org.jellyfin.livetv.LiveTuning

/**
 * Si se entra a la sección de canales, y con qué aviso.
 *
 * **La puerta cubre solo la sección de canales** (`REGISTRY_SCHEMA.md` §4). El
 * VOD no se toca: bloquear el inicio de sesión de Jellyfin dejaría a un ISP sin
 * las películas que Katalog le sirve, y convertiría cualquier fallo nuestro en
 * un cliente entero sin nada.
 */
sealed interface Acceso {
	/** Se entra. [aviso] no nulo se enseña discreto, sin cerrar nada. */
	data class Permitido(val aviso: String? = null, val motivo: String) : Acceso

	/** No se entra. [mensaje] se le enseña al abonado tal cual. */
	data class Denegado(val mensaje: String, val motivo: String) : Acceso
}

/**
 * Decide el acceso a partir de lo que el registro dijo —o no dijo.
 *
 * Está aparte del cliente de red a propósito: es la única parte de B9 que se
 * puede equivocar en silencio, y así se prueba entera sin levantar un servidor.
 */
object DecisorDeAcceso {

	/**
	 * El texto que ve el abonado cuando el registro deniega y no manda
	 * `message`. Genérico a la fuerza, y por eso el contrato insiste en que el
	 * registro mande el suyo.
	 */
	const val DENEGADO_GENERICO =
		"Este servidor no está disponible para TV en vivo. Contacta a tu proveedor."

	fun decidir(
		guardada: SiteAuthorization?,
		respuesta: SiteAuthorization?,
		ahoraMs: Long,
	): Acceso {
		// 1. Contestó.
		if (respuesta != null) {
			if (!respuesta.authorized) {
				return Acceso.Denegado(
					mensaje = respuesta.message ?: DENEGADO_GENERICO,
					motivo = "el registro deniega este host",
				)
			}
			return Acceso.Permitido(motivo = "autorizado por el registro")
		}

		// 2. No contestó, pero hay una autorización viva.
		if (guardada != null && guardada.vigente(ahoraMs)) {
			return Acceso.Permitido(motivo = "sin respuesta, autorización vigente")
		}

		// 3. No contestó y la que había caducó.
		if (guardada != null && guardada.authorized && guardada.untilEpochMs != null) {
			val vencidaHaceMs = ahoraMs - guardada.untilEpochMs
			if (vencidaHaceMs < LiveTuning.REGISTRY_GRACE_MS) {
				return Acceso.Permitido(
					aviso = "No se ha podido verificar el servicio. Sigue funcionando por ahora.",
					motivo = "caducada hace ${vencidaHaceMs / 86_400_000} d, dentro de la gracia",
				)
			}
			return Acceso.Denegado(
				mensaje = "No se ha podido verificar este servidor en varios días. " +
					"Comprueba la conexión a internet de tu caja o contacta a tu proveedor.",
				motivo = "caducada hace ${vencidaHaceMs / 86_400_000} d, fuera de la gracia",
			)
		}

		// 4. No contestó y nunca hubo respuesta.
		//
		// **Este caso no estaba en el contrato**, y había que decidirlo: una
		// caja recién instalada que no alcanza internet. `REGISTRY_SCHEMA.md`
		// §6 nombra ese despliegue —la caja llega a la red del ISP pero no a la
		// nube— como el escenario que algún día exigiría firmar la
		// autorización dentro del `config.json`. Si aquí se cerrara, ese
		// escenario estaría muerto desde el primer día y un corte de internet
		// del ISP dejaría sin televisión a cajas que nunca llegaron a preguntar.
		//
		// Se entra, se avisa y se registra. Y encaja con lo que esto es: un
		// interruptor operativo sobre gente que coopera, no un candado.
		return Acceso.Permitido(
			aviso = "No se ha podido verificar el servicio. Sigue funcionando por ahora.",
			motivo = "sin respuesta y sin autorización previa",
		)
	}
}
