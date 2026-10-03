package org.jellyfin.androidtv.ui.livetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.livetv.config.ConfigOrigin
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.source.ChannelProfile
import org.koin.android.ext.android.inject

/**
 * La sección de canales en vivo, reescrita desde cero.
 *
 * Reemplaza la sección LiveTV de Jellyfin (`CLAUDE.md` §4, regla 5): esta se
 * alimenta del servidor de configuración propio y de Flussonic, no del servidor
 * Jellyfin.
 *
 * Queda **detrás del inicio de sesión**, como Inicio y Buscar (`CLAUDE.md` §6).
 * Los canales en sí no piden credenciales; lo que la sesión controla es quién
 * llega a la sección.
 *
 * Usa `JellyfinTheme` y los componentes de `ui/base/` a propósito: Inicio,
 * Buscar y TV en vivo tienen que verse como una sola aplicación.
 *
 * **M0:** una fila plana con los canales de prueba de `BuildConfig`. La lista
 * real —grupos, favoritos, último canal, entrada por número y búsqueda, pensada
 * para ~120 canales— es B2.
 */
class LiveTvFragment : Fragment() {
	private val navigationRepository by inject<NavigationRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			// El catálogo se pide una sola vez por proceso: entrar y salir de la
			// sección no puede convertirse en una descarga por visita.
			val catalogo by remember { LiveTvCatalog.of(requireContext()).also { it.refreshOnce() }.state }
				.collectAsState()

			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(MainToolbarActiveButton.LiveTv)

				ChannelRow(
					catalog = catalogo,
					onChannelClick = { channel ->
						navigationRepository.navigate(Destinations.liveTvPlayer(channel.id))
					},
					onDiagnosticsClick = {
						navigationRepository.navigate(Destinations.liveTvDevMenu)
					},
				)
			}
		}
	}
}

@Composable
private fun ChannelRow(
	catalog: LiveCatalog,
	onChannelClick: (ChannelProfile) -> Unit,
	onDiagnosticsClick: () -> Unit,
) {
	// El foco inicial lo pide la guía, que es quien sabe cuándo existe la
	// tarjeta a la que debe ir (ver `LiveTvChannelGrid`).
	//
	// Las medidas automáticas por `adb` entran a la sección con un toque, que
	// no recorre el mismo camino de foco que el mando: **confirmar siempre con
	// el control**.
	val context = LocalContext.current

	// A qué canal mirar al entrar: lo último que vio esta persona, lo que el
	// ISP puso por defecto, y si no, el primero. Con ~120 canales, volver a la
	// sección y tener que buscar otra vez el canal de siempre es la diferencia
	// entre una guía usable y una lista.
	val ultimo = remember { LiveTvUltimoCanal.of(context) }
	val idInicial = remember(catalog.channels, catalog.defaultChannel) {
		ultimo.inicial(catalog.channels.map { it.id }, catalog.defaultChannel)
	}
	val dev = remember { DevOptions.of(context) }
	val revision by dev.revision.collectAsState()
	val degradada = remember(revision) { dev.isDegraded }
	val degradacion = remember(revision) { dev.describe() }

	val favoritos = remember { LiveTvFavorites.of(context) }
	val favoritosIds by favoritos.ids.collectAsState()

	val secciones = remember(catalog, favoritosIds) {
		agruparCanales(catalog.channels, catalog.groups, favoritosIds)
	}

	// --- Marcado por número -------------------------------------------------
	//
	// Con ~120 canales nadie navega con flechas hasta el 87: teclea 8 y 7. Los
	// dígitos se acumulan y, tras una pausa, se sintoniza. La pausa es lo que
	// permite marcar números de dos y tres cifras sin pedir confirmación.
	var digitos by remember { mutableStateOf("") }
	val marcado = remember(digitos, catalog) {
		digitos.toIntOrNull()?.let { n -> catalog.channels.firstOrNull { it.number == n } }
	}

	LaunchedEffect(digitos) {
		if (digitos.isEmpty()) return@LaunchedEffect
		delay(NUMBER_ENTRY_TIMEOUT_MS)
		val destino = marcado
		digitos = ""
		if (destino != null) onChannelClick(destino)
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.padding(horizontal = 48.dp, vertical = 24.dp)
				.focusGroup()
				.onKeyEvent { evento ->
					if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
					val digito = digitoDe(evento.key) ?: return@onKeyEvent false
					// Se acota a cuatro cifras: más no existe en ningún lineup y
					// evita que una pulsación pegada del mando acumule sin fin.
					if (digitos.length < 4) digitos += digito
					true
				}
		) {
			// La cabecera: marca a la izquierda, estado a la derecha.
			//
			// Antes todo se apilaba —marca, aviso, degradación y un botón de
			// Diagnóstico del tamaño de una tarjeta— y empujaba los canales
			// media pantalla hacia abajo. **Lo que la gente viene a ver son los
			// canales**, así que el resto se aparta a una sola línea.
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				LogoDeMarca(catalog.brand)

				Spacer(modifier = Modifier.weight(1f))

				// De dónde salió la lista. Una caja sirviendo una lista vieja
				// porque el servidor no responde se comporta igual que una al
				// día, y sin este aviso nadie las distingue.
				when {
					catalog.loading -> Pastilla("actualizando…", Color(0xFF9E9E9E))
					catalog.origin == ConfigOrigin.CACHE ->
						Pastilla("lista guardada — el servidor no responde", Color(0xFFE2B33C))
					catalog.origin == ConfigOrigin.NINGUNO ->
						Pastilla("canales de prueba", Color(0xFF9E9E9E))
					else -> Unit
				}

				// La degradación persiste en `SharedPreferences`: sobrevive al
				// cierre de la app y al reinicio de la caja. Se avisa antes de
				// sintonizar, porque una sesión con toggles pegados se mide
				// como si fuera de fábrica y eso ya costó dos conclusiones
				// falsas.
				if (degradada) {
					Spacer(modifier = Modifier.width(8.dp))
					Pastilla(degradacion, Color(0xFFE2B33C))
				}

				Spacer(modifier = Modifier.width(12.dp))

				// Diagnóstico sigue a mano —se veta un decoder y se entra al
				// canal sin salir de la sección— pero deja de ocupar el sitio
				// del primer canal.
				Button(
					onClick = onDiagnosticsClick,
					contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
				) {
					Text(text = "Diagnóstico", fontSize = 13.sp)
				}
			}

			// El aviso del operador. Va aquí y no sobre el vídeo: es
			// información del ISP, no del canal, y tapar la imagen con ella
			// sería cobrarle al abonado el recado de su proveedor.
			catalog.message?.let { aviso ->
				Spacer(modifier = Modifier.height(14.dp))
				Row(
					modifier = Modifier
						.clip(ShapeDefaults.Small)
						.background(Color.White.copy(alpha = 0.06f))
						.padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(text = "ⓘ", color = Color(catalog.brand.accent))
					Spacer(modifier = Modifier.width(10.dp))
					Text(text = aviso.text, color = Color(0xFFD5D5D5), fontSize = 14.sp)
				}
			}

			Spacer(modifier = Modifier.height(26.dp))

			if (secciones.isEmpty()) {
				// Sin canales hay dos situaciones distintas y conviene no
				// confundirlas: el servidor contestó una lista vacía —raro,
				// pero suyo— o no se pudo hablar con él. Lo segundo es lo que
				// va a pasar en un site mal configurado, y decir «no hay
				// canales» ahí manda a buscar donde no es.
				Text(
					text = when (catalog.origin) {
						ConfigOrigin.NINGUNO ->
							"No se pudo cargar la lista de canales de este servidor."
						else -> "No hay canales que mostrar."
					},
					color = Color(0xFF9E9E9E),
				)
			} else {
				LiveTvChannelGrid(
					sections = secciones,
					favorites = favoritosIds,
					accent = Color(catalog.brand.accent),
					onChannelClick = onChannelClick,
					onChannelLongClick = { favoritos.toggle(it.id) },
					idConFoco = idInicial,
				)
			}
		}

		if (digitos.isNotEmpty()) {
			NumberEntryOverlay(
				digits = digitos,
				resolved = marcado?.name,
				modifier = Modifier.align(Alignment.Center),
			)
		}
	}
}

/** Segundo y medio: da tiempo a marcar tres cifras sin hacer esperar. */
private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L

/** Acepta tanto la fila superior como el teclado numérico del mando. */
private fun digitoDe(key: Key): Char? = when (key) {
	Key.Zero, Key.NumPad0 -> '0'
	Key.One, Key.NumPad1 -> '1'
	Key.Two, Key.NumPad2 -> '2'
	Key.Three, Key.NumPad3 -> '3'
	Key.Four, Key.NumPad4 -> '4'
	Key.Five, Key.NumPad5 -> '5'
	Key.Six, Key.NumPad6 -> '6'
	Key.Seven, Key.NumPad7 -> '7'
	Key.Eight, Key.NumPad8 -> '8'
	Key.Nine, Key.NumPad9 -> '9'
	else -> null
}
