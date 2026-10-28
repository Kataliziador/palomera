package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.ServiceSuspendedState
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.FragmentServerBinding
import org.jellyfin.androidtv.ui.ServerButtonView
import org.jellyfin.androidtv.ui.card.UserCardView
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.ListAdapter
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ServerFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ID = "server_id"

		/**
		 * Cada cuánto vuelve a preguntar una caja suspendida.
		 *
		 * Dos minutos: lo que tarda en enterarse de que el abonado ya pagó, y es
		 * lo que convierte la reactivación en «se arregla solo» en vez de «vuelve
		 * a llamar». Sólo corre mientras el aviso está en pantalla, contra el
		 * Jellyfin del propio site, que está en la LAN.
		 */
		private const val REINTENTO_SUSPENDIDO_MS = 2L * 60 * 1000
	}

	private val startupViewModel: StartupViewModel by activityViewModel()
	private val markdownRenderer: MarkdownRenderer by inject()
	private val authenticationRepository: AuthenticationRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private var _binding: FragmentServerBinding? = null
	private var avisoSuspendido: AlertDialog? = null
	private var reintentoSuspendido: Job? = null
	private val binding get() = _binding!!

	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val server = serverIdArgument?.let(startupViewModel::getServer)

		if (server == null) {
			navigateFragment<SelectServerFragment>(keepToolbar = true, keepHistory = false)
			return null
		}

		_binding = FragmentServerBinding.inflate(inflater, container, false)

		val userAdapter = UserAdapter(requireContext(), server, startupViewModel, authenticationRepository, serverUserRepository)
		userAdapter.onItemPressed = { user -> autenticar(server, user) }
		binding.users.adapter = userAdapter

		startupViewModel.users
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.onEach { users ->
				userAdapter.items = users

				binding.users.isFocusable = users.any()
				binding.noUsersWarning.isVisible = users.isEmpty()
				binding.root.requestFocus()
			}.launchIn(viewLifecycleOwner.lifecycleScope)

		startupViewModel.loadUsers(server)

		onServerChange(server)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				val updated = startupViewModel.updateServer(server)
				if (updated) startupViewModel.getServer(server.id)?.let(::onServerChange)
			}
		}

		return binding.root
	}

	/**
	 * Entra con el usuario elegido y reacciona a lo que conteste el servidor.
	 *
	 * Estaba dentro del `onItemPressed`; se saca aquí porque el reintento
	 * automático de [avisarSuspendido] tiene que pasar por exactamente el mismo
	 * camino, y dos copias del `when` se habrían separado a la primera.
	 */
	private fun autenticar(server: Server, user: User) {
		startupViewModel.authenticate(server, user).onEach { state ->
			when (state) {
				// Ignored states
				AuthenticatingState -> Unit
				AuthenticatedState -> Unit
				// Actions
				RequireSignInState -> navigateFragment<UserLoginFragment>(bundleOf(
					UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
					UserLoginFragment.ARG_USERNAME to user.name,
				))
				// El contrato está suspendido: ni es un error de red ni lleva al código
				ServiceSuspendedState -> avisarSuspendido(server, user)
				// Errors
				ServerUnavailableState,
				is ApiClientErrorLoginState -> Toast.makeText(context, R.string.server_connection_failed, Toast.LENGTH_LONG).show()

				is ServerVersionNotSupported -> Toast.makeText(
					context,
					getString(
						R.string.server_issue_outdated_version,
						state.server.version,
						ServerRepository.recommendedServerVersion.toString()
					),
					Toast.LENGTH_LONG
				).show()
			}
		}.launchIn(lifecycleScope)
	}

	/**
	 * Lo que ve un abonado suspendido, que hasta hoy era la pantalla del código.
	 *
	 * Dice qué pasa —y que **no** hay que activar el aparato otra vez—, y vuelve
	 * a probar sola cada [REINTENTO_SUSPENDIDO_MS]. Si para entonces el proveedor
	 * ya reactivó, `setCurrentSession` deja la sesión puesta y `StartupActivity`
	 * entra sin que nadie toque el mando: eso es lo que hace que la suspensión
	 * salga gratis en soporte, y es la mitad que faltaba del 403.
	 */
	private fun avisarSuspendido(server: Server, user: User) {
		if (avisoSuspendido?.isShowing == true) return

		avisoSuspendido = AlertDialog.Builder(requireContext())
			.setTitle(R.string.livetv_acceso_suspendido_titulo)
			.setMessage(R.string.livetv_acceso_suspendido_mensaje)
			.setPositiveButton(R.string.livetv_acceso_suspendido_reintentar) { dialogo, _ ->
				dialogo.dismiss()
				autenticar(server, user)
			}
			.setOnDismissListener {
				reintentoSuspendido?.cancel()
				reintentoSuspendido = null
				avisoSuspendido = null
			}
			.show()

		reintentoSuspendido = viewLifecycleOwner.lifecycleScope.launch {
			while (true) {
				delay(REINTENTO_SUSPENDIDO_MS)
				autenticar(server, user)
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()

		reintentoSuspendido?.cancel()
		reintentoSuspendido = null
		avisoSuspendido?.dismiss()
		avisoSuspendido = null
		_binding = null
	}

	private fun onServerChange(server: Server) {
		binding.loginDisclaimer.text = server.loginDisclaimer?.let { markdownRenderer.toMarkdownSpanned(it) }

		binding.serverButton.apply {
			state = ServerButtonView.State.EDIT
			name = server.name
			address = server.address
			version = server.version
		}

		binding.addUserButton.setOnClickListener {
			navigateFragment<UserLoginFragment>(
				args = bundleOf(
					UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
					UserLoginFragment.ARG_USERNAME to null
				)
			)
		}

		binding.serverButton.setOnClickListener {
			navigateFragment<SelectServerFragment>(keepToolbar = true)
		}

		if (!server.versionSupported) {
			binding.notification.isVisible = true
			binding.notification.text = getString(
				R.string.server_unsupported_notification,
				server.version,
				ServerRepository.recommendedServerVersion.toString(),
			)
		} else if (!server.setupCompleted) {
			binding.notification.isVisible = true
			binding.notification.text = getString(R.string.server_setup_incomplete)
		} else {
			binding.notification.isGone = true
		}
	}

	private inline fun <reified F : Fragment> navigateFragment(
		args: Bundle = bundleOf(),
		keepToolbar: Boolean = false,
		keepHistory: Boolean = true,
	) {
		requireActivity()
			.supportFragmentManager
			.commit {
				if (keepToolbar) {
					replace<StartupToolbarFragment>(R.id.content_view)
					add<F>(R.id.content_view, null, args)
				} else {
					replace<F>(R.id.content_view, null, args)
				}

				if (keepHistory) addToBackStack(null)
			}
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		backgroundService.clearBackgrounds()

		val server = serverIdArgument?.let(startupViewModel::getServer)
		if (server != null) startupViewModel.loadUsers(server)
		else navigateFragment<SelectServerFragment>(keepToolbar = true)
	}

	private class UserAdapter(
		private val context: Context,
		private val server: Server,
		private val startupViewModel: StartupViewModel,
		private val authenticationRepository: AuthenticationRepository,
		private val serverUserRepository: ServerUserRepository,
	) : ListAdapter<User, UserAdapter.ViewHolder>() {
		var onItemPressed: (User) -> Unit = {}

		override fun areItemsTheSame(old: User, new: User): Boolean = old.id == new.id

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val cardView = UserCardView(context)

			return ViewHolder(cardView)
		}

		override fun onBindViewHolder(holder: ViewHolder, user: User) {
			holder.cardView.name = user.name
			holder.cardView.image = startupViewModel.getUserImage(server, user)

			holder.cardView.setPopupMenu {
				// Logout button
				if (user is PrivateUser && user.accessToken != null) {
					item(context.getString(R.string.lbl_sign_out)) {
						authenticationRepository.logout(user)
					}
				}

				// Remove button
				if (user is PrivateUser) {
					item(context.getString(R.string.lbl_remove)) {
						serverUserRepository.deleteStoredUser(user)
						startupViewModel.loadUsers(server)
					}
				}
			}

			holder.cardView.setOnClickListener {
				onItemPressed(user)
			}
		}

		private class ViewHolder(
			val cardView: UserCardView,
		) : RecyclerView.ViewHolder(cardView)
	}
}

