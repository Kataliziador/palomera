package org.jellyfin.androidtv.ui.livetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject

/**
 * La sección de canales en vivo, reescrita desde cero.
 *
 * Reemplaza la sección LiveTV de Jellyfin (`CLAUDE.md` §4, regla 5): esta se
 * alimenta del servidor de configuración propio y de Flussonic, no del servidor
 * Jellyfin, y funciona sin sesión iniciada.
 *
 * Usa `JellyfinTheme` y los componentes de `ui/base/` a propósito: Inicio,
 * Buscar y TV en vivo tienen que verse como una sola aplicación.
 *
 * **M0:** un solo canal, el de arranque, que llega por `BuildConfig` porque la
 * regla 12 prohíbe codificar URLs y nombres de canal. La lista real, con grupos
 * y favoritos para ~120 canales, es B2.
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
					onChannelClick = { navigationRepository.navigate(Destinations.liveTvPlayer) },
				)
			}
		}
	}
}

@Composable
private fun ChannelRow(onChannelClick: () -> Unit) {
	val firstChannel = remember { FocusRequester() }
	LaunchedEffect(firstChannel) { firstChannel.requestFocus() }

	Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)) {
		Text(text = stringResource(R.string.pref_live_tv_cat), fontWeight = FontWeight.Bold)

		Spacer(modifier = Modifier.height(16.dp))

		LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			item {
				ChannelCard(
					name = BuildConfig.LIVETV_BOOTSTRAP_CHANNEL_NAME,
					onClick = onChannelClick,
					modifier = Modifier.focusRequester(firstChannel),
				)
			}
		}
	}
}

@Composable
private fun ChannelCard(
	name: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Button(
		onClick = onClick,
		modifier = modifier.width(220.dp).height(124.dp),
		contentPadding = PaddingValues(16.dp),
	) {
		Text(text = name, fontWeight = FontWeight.Medium)
	}
}
