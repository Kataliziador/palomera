package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.databinding.FragmentServerAddBinding
import org.jellyfin.androidtv.ui.startup.ServerAddViewModel
import org.jellyfin.androidtv.util.getSummary
import org.jellyfin.livetv.config.RegistryRepository
import org.jellyfin.livetv.config.RespuestaProveedor
import org.jellyfin.livetv.diagnostics.DevOptions
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.launch

/**
 * La pantalla de alta, con el **selector de proveedor** encima.
 *
 * Lo que había antes era «escribe la dirección del servidor», y lo que hay
 * que escribir es algo como `http://192.168.20.249:8096`. Ningún abonado sabe
 * eso, y el técnico que lo teclea por él se equivoca.
 *
 * Las tres rutas de `docs/selector-de-proveedor.md`, por orden:
 *
 * 1. **por IP**: se pregunta al registro sin más, y si reconoce la red desde
 *    la que se llama, ofrece ese proveedor. Cero teclado;
 * 2. **por código**: si no la reconoce, el abonado escribe el código que le
 *    dio su ISP. Es la ruta de los probadores de la prueba cerrada, del
 *    técnico que aprovisiona desde la oficina y de quien está bajo CGNAT;
 * 3. **la dirección a mano**, que **no se esconde nunca**: es la única que
 *    funciona sin internet —`CLAUDE.md` §6 dice que hay cajas que llegan a la
 *    red del ISP pero no a internet— y es por donde entra el revisor de
 *    Google (`PLAY.md` §6).
 *
 * **El selector no inventa un camino de alta.** Lo único que hace es producir
 * una dirección y dársela a `ServerAddViewModel.addServer`, que es exactamente
 * lo que hace el campo de texto. Por eso el camino viejo queda intacto.
 */
class ServerAddFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ADDRESS = "server_address"
	}

	private val registro by lazy { RegistryRepository(requireContext()) }

	private val direccionDelRegistro: String?
		get() = DevOptions(requireContext()).registryUrlOverride
			?: BuildConfig.REGISTRY_URL.takeIf { it.isNotBlank() }

	private val startupViewModel: ServerAddViewModel by viewModel()
	private var _binding: FragmentServerAddBinding? = null
	private val binding get() = _binding!!

	private val serverAddressArgument get() = arguments?.getString(ARG_SERVER_ADDRESS)?.ifBlank { null }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentServerAddBinding.inflate(inflater, container, false)

		with(binding.address) {
			setOnEditorActionListener { _, actionId, _ ->
				when (actionId) {
					EditorInfo.IME_ACTION_DONE -> {
						submitAddress()
						true
					}

					else -> false
				}
			}
		}

		with(binding.confirm) {
			setOnClickListener { submitAddress() }
		}

		with(binding.codigo) {
			setOnEditorActionListener { _, actionId, _ ->
				when (actionId) {
					EditorInfo.IME_ACTION_DONE -> {
						enviarCodigo()
						true
					}

					else -> false
				}
			}
		}

		binding.codigoConfirmar.setOnClickListener { enviarCodigo() }

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (serverAddressArgument != null) {
			// Llegó una dirección ya elegida: no hay nada que seleccionar.
			ocultarSelector()
			binding.address.setText(serverAddressArgument)
			binding.address.isEnabled = false
			submitAddress()
		} else {
			preguntarPorLaRed()
		}

		startupViewModel.state.onEach { state ->
			when (state) {
				is ConnectingState -> {
					// Disable form
					binding.address.isEnabled = false
					binding.confirm.isEnabled = false
					// Update state text
					binding.error.text = getString(R.string.server_connecting, state.address)
				}

				is UnableToConnectState -> {
					// Enable form
					binding.address.isEnabled = true
					binding.confirm.isEnabled = true
					// Update state text
					binding.error.text = getString(
						R.string.server_connection_failed_candidates,
						state.addressCandidates
							.map { "${it.key} - ${it.value.getSummary(requireContext())}" }
							.joinToString(prefix = "\n", separator = "\n")
					)
				}

				is ConnectedState -> parentFragmentManager.commit {
					// Open server view
					replace<StartupToolbarFragment>(R.id.content_view)
					add<ServerFragment>(
						R.id.content_view,
						null,
						bundleOf(
							ServerFragment.ARG_SERVER_ID to state.id.toString()
						)
					)
				}

				null -> Unit
			}
		}.launchIn(lifecycleScope)
	}

	override fun onDestroyView() {
		super.onDestroyView()

		_binding = null
	}

	/**
	 * Ruta 1: que el registro mire desde qué red llamamos.
	 *
	 * Mientras tanto la pantalla dice «buscando», y **no se le quita el foco
	 * al abonado**: si tiene la dirección a mano puede ir escribiéndola, que
	 * es lo que hacía antes de que esto existiera.
	 */
	private fun preguntarPorLaRed() {
		binding.proveedorEstado.setText(R.string.livetv_proveedor_buscando)
		binding.address.requestFocus()
		lifecycleScope.launch {
			when (val r = registro.proveedor(direccionDelRegistro)) {
				is RespuestaProveedor.Encontrado -> ofrecer(r.proveedor.nombre, r.proveedor.serverUrl)
				// «No te conozco» lleva al código. «No pude preguntar» no:
				// decirle que su proveedor no existe a alguien cuya caja no
				// llega a internet lo manda a buscar un dato que sí tiene.
				RespuestaProveedor.Ninguno -> pedirCodigo(R.string.livetv_proveedor_codigo_pide)
				RespuestaProveedor.NoSeSupo -> pedirCodigo(R.string.livetv_proveedor_sin_red)
				RespuestaProveedor.DemasiadosIntentos ->
					pedirCodigo(R.string.livetv_proveedor_demasiados)
			}
		}
	}

	/** Ruta 2: el código que le dio su ISP. */
	private fun enviarCodigo() {
		val codigo = binding.codigo.text.toString().trim()
		if (codigo.isEmpty()) return
		binding.codigoConfirmar.isEnabled = false
		binding.proveedorEstado.setText(R.string.livetv_proveedor_buscando)
		lifecycleScope.launch {
			val r = registro.proveedor(direccionDelRegistro, codigo)
			binding.codigoConfirmar.isEnabled = true
			when (r) {
				is RespuestaProveedor.Encontrado -> ofrecer(r.proveedor.nombre, r.proveedor.serverUrl)
				RespuestaProveedor.Ninguno ->
					binding.proveedorEstado.setText(R.string.livetv_proveedor_codigo_malo)
				// Un código bueno rechazado por el límite no es un código
				// malo, y si se le dice lo mismo lo reescribe diez veces.
				RespuestaProveedor.DemasiadosIntentos ->
					binding.proveedorEstado.setText(R.string.livetv_proveedor_demasiados)
				RespuestaProveedor.NoSeSupo ->
					binding.proveedorEstado.setText(R.string.livetv_proveedor_sin_red)
			}
		}
	}

	/** Hay proveedor: se enseña su nombre y el botón que lo confirma. */
	private fun ofrecer(nombre: String, serverUrl: String) {
		// **El teclado de Leanback no se va solo.** Visto en el emulador: tras
		// aceptar el código se quedaba puesto tapando media pantalla, y
		// seguía ahí en la pantalla siguiente. En una tele eso no es un
		// detalle — es el botón de confirmar escondido detrás de una `Ñ`.
		esconderTeclado()
		binding.proveedorEstado.text = getString(R.string.livetv_proveedor_encontrado, nombre)
		binding.codigo.visibility = View.GONE
		binding.codigoConfirmar.visibility = View.GONE
		with(binding.proveedorConfirmar) {
			visibility = View.VISIBLE
			// La dirección se mete en el campo de siempre y se envía por el
			// camino de siempre: así el abonado ve qué se guardó, y si algo
			// falla el mensaje de error es el que ya conocemos.
			setOnClickListener {
				binding.address.setText(serverUrl)
				submitAddress()
			}
			requestFocus()
		}
	}

	private fun pedirCodigo(mensaje: Int) {
		binding.proveedorEstado.setText(mensaje)
		binding.proveedorConfirmar.visibility = View.GONE
		binding.codigo.visibility = View.VISIBLE
		binding.codigoConfirmar.visibility = View.VISIBLE
		// El código pasa a ser la acción principal, así que el foco va ahí
		// —medido en el emulador: se quedaba abajo, en la dirección, y con un
		// mando eso son cuatro pulsaciones para llegar a lo que la pantalla
		// acaba de pedir—. **Sólo si el abonado no ha empezado a escribir
		// otra cosa**: quitarle el cursor a mitad de una dirección sería peor
		// que dejarlo donde está.
		if (binding.address.text.isEmpty()) binding.codigo.requestFocus()
	}

	private fun esconderTeclado() {
		val gestor = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
			as? InputMethodManager
		gestor?.hideSoftInputFromWindow(binding.root.windowToken, 0)
	}

	private fun ocultarSelector() {
		binding.proveedorEstado.visibility = View.GONE
		binding.proveedorConfirmar.visibility = View.GONE
		binding.codigo.visibility = View.GONE
		binding.codigoConfirmar.visibility = View.GONE
	}

	private fun submitAddress() = when {
		binding.address.text.isNotBlank() -> startupViewModel.addServer(binding.address.text.toString())
		else -> binding.error.setText(R.string.server_field_empty)
	}
}
