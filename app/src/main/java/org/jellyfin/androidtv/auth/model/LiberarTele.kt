package org.jellyfin.androidtv.auth.model

/**
 * Por qué la caja deja de usar una cuenta de Jellyfin.
 *
 * No son todas las salidas imaginables: son las tres que esta app ejecuta y que
 * cambian —o deliberadamente no cambian— el cupo de teles de la cuenta.
 */
enum class SalidaDeCuenta {
	/** El abonado pulsa «Cerrar sesión» sobre un usuario guardado. */
	CERRAR_SESION,

	/** Se borra el servidor entero, con todos sus usuarios guardados. */
	BORRAR_SERVIDOR,

	/**
	 * Se cambia de usuario **conservando el perfil** en la caja.
	 *
	 * Hoy ninguna ruta de la app llama a la liberación desde aquí, así que el
	 * caso está por construcción. Se nombra igualmente porque es la regla que
	 * más fácil se rompe sin querer: la caja sigue siendo una de las teles de
	 * esa cuenta y volverá sin pedir código.
	 */
	CAMBIAR_DE_USUARIO,
}

/** Si esa salida tiene que avisar a Jellyfin para devolver la tele al cupo. */
sealed interface LiberarTele {
	/** Hay que llamar a `POST /Sessions/Logout` antes de olvidar el token. */
	data object Libera : LiberarTele

	/**
	 * No hay nada que avisar, y [motivo] dice cuál de los dos casos es.
	 *
	 * Lleva motivo porque los dos caminos que llegan aquí son opuestos al leer
	 * por qué una caja no se puede reactivar: «la caja conserva el perfil» es
	 * la regla funcionando, y «sin token que liberar» es una sesión que ya
	 * estaba vacía. Sin la columna, una serie de «no liberada» no distingue una
	 * de otra.
	 */
	data class NoLibera(val motivo: String) : LiberarTele
}

/**
 * Qué salidas devuelven la tele al cupo de `MaxActiveSessions`.
 *
 * ## El fallo que esto arregla, medido en producción el 09-nov
 *
 * Al salir de una cuenta, la app olvidaba el token **sólo en la caja** y nunca
 * avisaba al servidor. La sesión seguía viva en Jellyfin ocupando una de las
 * teles que permite la política del usuario —`MaxActiveSessions`, que fija el
 * panel de abonados desde el plan—, y al reactivar **esa misma caja** Jellyfin
 * contestaba **403**: `AuthenticateNewSessionInternal` cuenta las sesiones
 * **antes** de llamar a `GetAuthorizationToken`, que es lo que cerraría la
 * sesión vieja del mismo aparato. La caja chocaba contra su propio fantasma, y
 * el cuerpo del 403 en producción es «Error processing request.», así que el
 * panel lo traducía como «la clave no tiene permiso».
 *
 * Se vio con una Roku en el site de Palomera Probadores, y borrar el
 * dispositivo a mano en Jellyfin la dejó entrar a la primera. En Roku ya está
 * arreglado (`docs/peticion-liberar-tele.md`).
 *
 * ## Por qué es una función pura y no un `if` en el repositorio
 *
 * Equivocarse tiene dos precios distintos y los dos son caros: **liberar de
 * menos** deja al abonado sin poder reactivar su propia caja —el fallo de
 * arriba—, y **liberar de más** echa de Jellyfin a una caja que seguía en uso.
 * Lo que hay que proteger no es el código, es la tabla.
 *
 * @param tieneToken si esa sesión guarda un token. `POST /Sessions/Logout` se
 *   autentica **con el token de esa misma sesión**, así que sin él no hay a
 *   quién avisar ni con qué.
 */
fun decidirLiberarTele(salida: SalidaDeCuenta, tieneToken: Boolean): LiberarTele {
	if (!tieneToken) return LiberarTele.NoLibera("sin token que liberar")

	return when (salida) {
		SalidaDeCuenta.CERRAR_SESION,
		SalidaDeCuenta.BORRAR_SERVIDOR,
		-> LiberarTele.Libera

		SalidaDeCuenta.CAMBIAR_DE_USUARIO -> LiberarTele.NoLibera("la caja conserva el perfil")
	}
}
