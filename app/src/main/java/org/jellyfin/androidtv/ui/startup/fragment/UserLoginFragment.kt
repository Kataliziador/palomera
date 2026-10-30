package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.UnavailableQuickConnectState
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.FragmentUserLoginBinding
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class UserLoginFragment : Fragment() {
	companion object {
		const val ARG_USERNAME = "user_name"
		const val ARG_SERVER_ID = "server_id"
		const val ARG_SKIP_QUICKCONNECT = "skip_quickconnect"
		const val TAG_LOGIN_METHOD = "login_method"
	}

	private val userLoginViewModel: UserLoginViewModel by activityViewModel()
	private val backgroundService: BackgroundService by inject()
	private var _binding: FragmentUserLoginBinding? = null
	private val binding get() = _binding!!

	private val usernameArgument get() = arguments?.getString(ARG_USERNAME)?.ifBlank { null }
	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }
	private val skipQuickConnect get() = arguments?.getBoolean(ARG_SKIP_QUICKCONNECT) ?: false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		userLoginViewModel.forcedUsername = usernameArgument
		userLoginViewModel.setServer(serverIdArgument?.toUUIDOrNull())
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentUserLoginBinding.inflate(inflater, container, false)

		binding.cancel.setOnClickListener { parentFragmentManager.popBackStack() }
		binding.useCredentials.setOnClickListener { setLoginMethod<UserLoginCredentialsFragment>() }
		binding.useQuickconnect.setOnClickListener { setLoginMethod<UserLoginQuickConnectFragment>() }

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		userLoginViewModel.clearLoginState()

		// Open initial fragment
		if (skipQuickConnect) setLoginMethod<UserLoginCredentialsFragment>()
		else setLoginMethod<UserLoginQuickConnectFragment>()

		lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				// Update "connecting to ..." text and background
				userLoginViewModel.server.onEach { server ->
					// **El respaldo decía «Jellyfin»** —§8: la app llamándose
					// Jellyfin, y en la primera pantalla de toda instalación—.
					// `name` no es nulo pero sí puede venir vacío, y `server`
					// es nulo mientras se resuelve, así que los dos casos
					// pintaban ese texto aunque fuera un fotograma.
					// Cae a la dirección que el abonado acaba de escribir, que
					// además le dice más que cualquier nombre genérico.
					val name = server?.name?.takeIf(String::isNotBlank)
						?: server?.address
						?: getString(R.string.livetv_acceso_servidor_sin_nombre)
					binding.subtitle.text = getString(R.string.login_connect_to, name)

					if (server != null) backgroundService.setBackground(server)
					else backgroundService.clearBackgrounds()
				}.launchIn(this)

				// Disable QuickConnect when unavailable
				userLoginViewModel.quickConnectState.onEach { state ->
					binding.useQuickconnect.isEnabled = state != UnavailableQuickConnectState
					if (state == UnavailableQuickConnectState) setLoginMethod<UserLoginCredentialsFragment>()
				}.launchIn(this)
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()

		_binding = null
	}

	private inline fun <reified T : Fragment> setLoginMethod() {
		val currentFragment = childFragmentManager.findFragmentByTag(TAG_LOGIN_METHOD)
		if (currentFragment is T) return

		childFragmentManager.commit {
			setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
			replace<T>(binding.loginMethod.id, TAG_LOGIN_METHOD)
		}

		// Hide button for active fragment
		binding.useCredentials.isVisible = T::class != UserLoginCredentialsFragment::class
		binding.useQuickconnect.isVisible = T::class != UserLoginQuickConnectFragment::class
	}
}
