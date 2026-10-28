package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.SessionFailure
import org.jellyfin.androidtv.auth.model.asSessionFailure
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.DISABLED
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.LAST_USER
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.SPECIFIC_USER
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

data class Session(
	val userId: UUID,
	val serverId: UUID,
	val accessToken: String,
)

/**
 * Cómo acabó el intento de usar una sesión.
 *
 * Antes era un `Boolean`, y ahí se perdía el único dato que distingue a un
 * abonado suspendido de uno dado de baja y de un site que no contesta: el
 * motivo. Quien llama necesita los tres para enseñar cosas distintas.
 */
sealed interface SessionOutcome {
	data object Ok : SessionOutcome

	/** No se llegó a preguntarle al servidor: no existe o su versión no vale. */
	data object NotAttempted : SessionOutcome

	/** Se preguntó —o se intentó— y la sesión no sirve. */
	data class Rejected(val failure: SessionFailure) : SessionOutcome
}

enum class SessionRepositoryState {
	READY,
	RESTORING_SESSION,
	SWITCHING_SESSION,
}

interface SessionRepository {
	val currentSession: StateFlow<Session?>
	val state: StateFlow<SessionRepositoryState>

	suspend fun restoreSession(destroyOnly: Boolean)
	suspend fun switchCurrentSession(serverId: UUID, userId: UUID): SessionOutcome
	fun destroyCurrentSession()
}

class SessionRepositoryImpl(
	private val authenticationPreferences: AuthenticationPreferences,
	private val authenticationStore: AuthenticationStore,
	private val userApiClient: ApiClient,
	private val preferencesRepository: PreferencesRepository,
	private val defaultDeviceInfo: DeviceInfo,
	private val userRepository: UserRepository,
	private val serverRepository: ServerRepository,
	private val telemetryPreferences: TelemetryPreferences,
) : SessionRepository {
	private val currentSessionMutex = Mutex()
	private val _currentSession = MutableStateFlow<Session?>(null)
	override val currentSession = _currentSession.asStateFlow()
	private val _state = MutableStateFlow(SessionRepositoryState.READY)
	override val state = _state.asStateFlow()

	override suspend fun restoreSession(destroyOnly: Boolean): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			Timber.i("Restoring session")

			_state.value = SessionRepositoryState.RESTORING_SESSION

			val alwaysAuthenticate = authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate]
			val autoLoginBehavior = authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]

			when {
				alwaysAuthenticate -> destroyCurrentSession()
				autoLoginBehavior == DISABLED -> destroyCurrentSession()
				autoLoginBehavior == LAST_USER && !destroyOnly -> setCurrentSession(createLastUserSession())
				autoLoginBehavior == SPECIFIC_USER && !destroyOnly -> {
					val serverId = authenticationPreferences[AuthenticationPreferences.autoLoginServerId].toUUIDOrNull()
					val userId = authenticationPreferences[AuthenticationPreferences.autoLoginUserId].toUUIDOrNull()
					if (serverId != null && userId != null) setCurrentSession(createUserSession(serverId, userId))
				}
			}

			_state.value = SessionRepositoryState.READY
		}
	}

	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID): SessionOutcome {
		// No change in user - don't switch
		if (currentSession.value?.userId == userId) {
			Timber.d("Current session user is the same as the requested user")
			return SessionOutcome.NotAttempted
		}

		_state.value = SessionRepositoryState.SWITCHING_SESSION
		Timber.i("Switching current session to user $userId")

		val session = createUserSession(serverId, userId)
		if (session == null) {
			Timber.w("Could not switch to non-existing session for user $userId")
			_state.value = SessionRepositoryState.READY
			return SessionOutcome.NotAttempted
		}

		val switched = setCurrentSession(session)
		_state.value = SessionRepositoryState.READY
		return switched
	}

	override fun destroyCurrentSession() {
		Timber.i("Destroying current session")

		userRepository.setCurrentUser(null)
		serverRepository.setCurrentServer(null)
		_currentSession.value = null
		_state.value = SessionRepositoryState.READY
	}

	private suspend fun setCurrentSession(session: Session?): SessionOutcome {
		var server: Server? = null

		if (session != null) {
			// No change in session - don't switch
			if (currentSession.value?.userId == session.userId) return SessionOutcome.Ok

			// Update last active user
			authenticationPreferences[AuthenticationPreferences.lastServerId] = session.serverId.toString()
			authenticationPreferences[AuthenticationPreferences.lastUserId] = session.userId.toString()

			// Check if server version is supported
			server = serverRepository.getServer(session.serverId, true)
			if (server == null || !server.versionSupported) return SessionOutcome.NotAttempted
		}

		// Update session after binding the apiclient settings
		val deviceInfo = session?.let { defaultDeviceInfo.forUser(it.userId) } ?: defaultDeviceInfo
		Timber.i("Updating current session. userId=${session?.userId} server=${server?.serverVersion}")

		val applied = userApiClient.applySession(session, deviceInfo)
		if (applied && session != null) {
			try {
				val user = withContext(Dispatchers.IO) {
					userApiClient.userApi.getCurrentUser().content
				}
				userRepository.setCurrentUser(user)
				serverRepository.setCurrentServer(server)
			} catch (err: ApiClientException) {
				// **El motivo no se tira.** Esta llamada es la misma que hace
				// `AuthenticationRepository.authenticateToken`, y como se hacía aquí
				// primero, su `catch` —que sí separa el tiempo de espera— no llegaba a
				// ejecutarse nunca: los tres casos salían por este `return false` y
				// acababan en la pantalla del código.
				val failure = err.asSessionFailure()
				Timber.e(err, "Unable to authenticate: bad response when getting user info ($failure)")
				// Se destruye la sesión en los tres casos, también en el 403: con un
				// token que el servidor rechaza no se puede entrar a nada. Lo que esto
				// **no** hace es borrar el token guardado —sólo «Cerrar sesión» lo
				// borra—, y por eso un abonado reactivado vuelve a entrar sin código.
				destroyCurrentSession()
				return SessionOutcome.Rejected(failure)
			}

			// Update crash reporting URL
			val crashReportUrl = userApiClient.clientLogApi.logFileUrl()
			telemetryPreferences[TelemetryPreferences.crashReportUrl] = crashReportUrl
			telemetryPreferences[TelemetryPreferences.crashReportToken] = session.accessToken
		} else {
			userRepository.setCurrentUser(null)
			serverRepository.setCurrentServer(null)
		}
		preferencesRepository.onSessionChanged()
		_currentSession.value = session

		return SessionOutcome.Ok
	}

	private fun createLastUserSession(): Session? {
		val lastUserId = authenticationPreferences[AuthenticationPreferences.lastUserId].toUUIDOrNull()
		val lastServerId = authenticationPreferences[AuthenticationPreferences.lastServerId].toUUIDOrNull()

		return if (lastUserId != null && lastServerId != null) createUserSession(lastServerId, lastUserId)
		else null
	}

	private fun createUserSession(serverId: UUID, userId: UUID): Session? {
		val account = authenticationStore.getUser(serverId, userId)
		if (account?.accessToken == null) return null

		return Session(
			userId = userId,
			serverId = serverId,
			accessToken = account.accessToken
		)
	}

	private fun ApiClient.applySession(session: Session?, newDeviceInfo: DeviceInfo = defaultDeviceInfo): Boolean {
		if (session == null) {
			update(
				baseUrl = null,
				accessToken = null,
				deviceInfo = newDeviceInfo,
			)
		} else {
			val server = authenticationStore.getServer(session.serverId)
				?: return false

			update(
				baseUrl = server.address,
				accessToken = session.accessToken,
				deviceInfo = newDeviceInfo,
			)
		}

		return true
	}
}
