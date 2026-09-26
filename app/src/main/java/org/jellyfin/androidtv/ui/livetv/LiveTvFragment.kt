package org.jellyfin.androidtv.ui.livetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
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
			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(MainToolbarActiveButton.LiveTv)

				ChannelRow(
					channels = BootstrapChannels.all,
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
	channels: List<ChannelProfile>,
	onChannelClick: (ChannelProfile) -> Unit,
	onDiagnosticsClick: () -> Unit,
) {
	// Foco inicial en el primer canal. Es la variante que Edgar validó con el
	// control; las medidas automáticas por `adb` entran a la sección con un
	// toque, que no recorre el mismo camino de foco que el mando, así que no
	// sirven para juzgar esto. Confirmar siempre con el control.
	val firstChannel = remember { FocusRequester() }
	LaunchedEffect(firstChannel) { firstChannel.requestFocus() }

	// Se observa `revision` para que el aviso aparezca y desaparezca al volver
	// del menú de degradación, sin recrear la pantalla.
	val context = LocalContext.current
	val dev = remember { DevOptions.of(context) }
	val revision by dev.revision.collectAsState()
	val degradada = remember(revision) { dev.isDegraded }
	val degradacion = remember(revision) { dev.describe() }

	Column(
		modifier = Modifier
			.padding(horizontal = 48.dp, vertical = 24.dp)
			.focusGroup()
	) {
		Text(text = stringResource(R.string.pref_live_tv_cat), fontWeight = FontWeight.Bold)

		// La degradación persiste en `SharedPreferences`: sobrevive al cierre de
		// la app y al reinicio de la caja. Se avisa aquí, antes de sintonizar,
		// porque una sesión con toggles pegados se mide como si fuera de fábrica
		// y eso ya costó dos conclusiones falsas (LIVETV_LOG, 2026-09-26).
		if (degradada) {
			Spacer(modifier = Modifier.height(8.dp))
			Text(text = "degradación activa: $degradacion", color = Color.Yellow)
		}

		Spacer(modifier = Modifier.height(16.dp))

		// Las tarjetas se dimensionan para que los seis canales de prueba quepan
		// sin desplazamiento. La fila **no** desplaza el foco más allá de lo
		// visible, y pelearse con eso aquí no tiene sentido: esta lista es
		// andamiaje de M0 y B2 la reescribe con grupos, favoritos y entrada por
		// número, que es donde el desplazamiento hay que resolverlo de verdad
		// para ~120 canales.
		LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			// Entrada al menú de degradación. Vive junto a los canales porque es
			// donde hace falta: se veta un decoder y se entra al canal a ver qué
			// cambia, sin salir de la sección.
			item {
				Button(
					onClick = onDiagnosticsClick,
					modifier = Modifier.width(136.dp).height(92.dp),
					contentPadding = PaddingValues(10.dp),
				) {
					Text(text = "Diagnóstico", fontWeight = FontWeight.Medium)
				}
			}

			items(channels, key = { it.id }) { channel ->
				ChannelCard(
					channel = channel,
					onClick = { onChannelClick(channel) },
					modifier = if (channel == channels.firstOrNull()) {
						Modifier.focusRequester(firstChannel)
					} else {
						Modifier
					},
				)
			}
		}
	}
}

@Composable
private fun ChannelCard(
	channel: ChannelProfile,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Button(
		onClick = onClick,
		modifier = modifier.width(136.dp).height(92.dp),
		contentPadding = PaddingValues(10.dp),
	) {
		Text(text = channel.name, fontWeight = FontWeight.Medium)
	}
}
