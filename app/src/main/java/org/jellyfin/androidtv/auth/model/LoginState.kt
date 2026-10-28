package org.jellyfin.androidtv.auth.model

import org.jellyfin.sdk.api.client.exception.ApiClientException

sealed class LoginState
data object AuthenticatingState : LoginState()
data object RequireSignInState : LoginState()
data object ServerUnavailableState : LoginState()

/**
 * El servidor contestó **403**: el contrato del abonado está suspendido.
 *
 * No es un fallo de credenciales y no lleva a la pantalla del código: el token
 * guardado sigue siendo válido y vuelve a servir en cuanto el proveedor
 * reactive la cuenta, sin que nadie teclee nada. Ver [SessionFailure].
 */
data object ServiceSuspendedState : LoginState()
data class ServerVersionNotSupported(val server: Server) : LoginState()
data class ApiClientErrorLoginState(val error: ApiClientException) : LoginState()
data object AuthenticatedState : LoginState()
