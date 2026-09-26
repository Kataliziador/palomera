package org.jellyfin.androidtv.ui.livetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * Menú de degradación y diagnóstico (LIVETV_ENGINE.md §7).
 *
 * Existe porque la caja de pruebas es el **mejor caso posible**: trae MPEG-2,
 * MP2 y AC-3 por hardware. Sin poder empeorarla a propósito, los caminos de
 * respaldo llegarían a producción sin haberse ejercitado nunca.
 *
 * Todo lo de aquí arranca en el valor de fábrica: abrir el menú no cambia nada.
 */
class LiveTvDevMenuFragment : Fragment() {

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(MainToolbarActiveButton.LiveTv)
				DevMenu()
			}
		}
	}
}

@Composable
private fun DevMenu() {
	val context = LocalContext.current
	val dev = remember { DevOptions.of(context) }
	val caps = remember { DeviceCapabilities.of(context) }

	// Fuerza el redibujado tras cada cambio: las opciones viven en preferencias,
	// no en estado de Compose.
	var tick by remember { mutableIntStateOf(0) }
	var verDecoders by remember { mutableStateOf(false) }
	fun change(block: () -> Unit) { block(); tick++ }

	val videoDecoders = remember(tick) {
		caps.decoders
			.filter { it.mime.startsWith("video/") }
			.distinctBy { it.name }
			.sortedWith(compareByDescending<org.jellyfin.livetv.capabilities.DecoderInfo> { it.hardware }.thenBy { it.name })
	}

	LazyColumn(
		modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		// El orden importa: con un mando, lo que está a sesenta pulsaciones no
		// existe. Primero lo que se usa, y la lista de 54 decoders al final y
		// plegada.
		item { Header("Estado") }
		item {
			Info(
				"${caps.manufacturer} ${caps.model} · ${caps.device} · API ${caps.sdkInt} · " +
					"${caps.totalRamMb} MB · ${caps.abis.firstOrNull() ?: "?"}"
			)
		}
		item { Info("degradación activa: ${dev.describe()}", warn = dev.isDegraded) }

		item { Header("Diagnóstico") }
		item {
			Row(
				label = "Overlay de estadísticas en el reproductor",
				value = if (dev.statsOverlayVisible) "visible" else "oculto",
				changed = dev.statsOverlayVisible,
			) { change { dev.statsOverlayVisible = !dev.statsOverlayVisible } }
		}
		item {
			Row(label = "Volcar el log ahora a Descargas", value = "${LiveLog.snapshot().size} entradas") {
				val destino = LiveLog.export(context)
				Toast.makeText(context, destino, Toast.LENGTH_LONG).show()
			}
		}
		item {
			Info("Se vuelca por sí solo en cuanto ocurre un error, sin pulsar nada.")
		}
		item {
			val volcados = remember(tick) { LiveLog.exportedFiles(context) }
			Info(
				if (volcados.isEmpty()) "en Descargas: todavía ninguno"
				else "en Descargas: ${volcados.size} · último ${volcados.first()}"
			)
		}

        item { Header("Degradación — simular cajas peores") }
		item {
			Row(
				label = "Forzar video por software",
				value = if (dev.forceSoftwareVideo) "SÍ" else "no",
				changed = dev.forceSoftwareVideo,
			) { change { dev.forceSoftwareVideo = !dev.forceSoftwareVideo } }
		}

		item {
			Row(
				label = "Umbral de playlist detenido",
				value = if (dev.playlistStuckMedia3Default) "3.5 (Media3)" else "10 (nuestro)",
				changed = dev.playlistStuckMedia3Default,
			) { change { dev.playlistStuckMedia3Default = !dev.playlistStuckMedia3Default } }
		}
		item {
			Info("Con 3.5 el error salta a los ~14 s, antes de que Flussonic reconstruya el playlist (~15 s).")
		}

		item {
			Row(
				label = "Volver al borde de emisión al derivar",
				value = if (dev.liveEdgeResyncDisabled) "desactivado" else "activo",
				changed = dev.liveEdgeResyncDisabled,
			) { change { dev.liveEdgeResyncDisabled = !dev.liveEdgeResyncDisabled } }
		}
		item {
			Info("Sin esto, cada corte de red deja el canal más atrás hasta salirse de la ventana.")
		}

		item {
			Row(
				label = "Reproducción tunneled",
				value = if (dev.tunnelingEnabled) "ACTIVA" else "apagada",
				changed = dev.tunnelingEnabled,
			) { change { dev.tunnelingEnabled = !dev.tunnelingEnabled } }
		}
		item {
			Info("El camino que usan las apps nativas de TV. Su fallo da pantalla negra sin aviso.")
		}

		item { Header("Flags del extractor TS  (fase M4)") }
		item {
			Row(
				label = "FLAG_DETECT_ACCESS_UNITS",
				value = if (dev.tsDetectAccessUnits) "ON" else "off",
				changed = dev.tsDetectAccessUnits,
			) { change { dev.tsDetectAccessUnits = !dev.tsDetectAccessUnits } }
		}
		item {
			Row(
				label = "FLAG_ALLOW_NON_IDR_KEYFRAMES",
				value = if (dev.tsAllowNonIdrKeyframes) "ON" else "off",
				changed = dev.tsAllowNonIdrKeyframes,
			) { change { dev.tsAllowNonIdrKeyframes = !dev.tsAllowNonIdrKeyframes } }
		}

		item { Header("Sintonía en caliente") }
		item {
			val v = dev.targetOffsetOverrideMs
			Row(
				label = "targetOffsetMs",
				value = v?.let { "$it ms" } ?: "el del canal",
				changed = v != null,
			) { change { dev.targetOffsetOverrideMs = nextOffset(v) } }
		}
		item {
			Info("Por encima de la mitad de la ventana provoca BEHIND_LIVE_WINDOW a voluntad.")
		}
		item {
			val v = dev.minBufferOverrideMs
			Row(label = "minBufferMs", value = v?.let { "$it ms" } ?: "15000 (fábrica)", changed = v != null) {
				change { dev.minBufferOverrideMs = nextBuffer(v) }
			}
		}

		item { Header("Restablecer") }
		item {
			Row(label = "Todo a valores de fábrica", value = "") { change { dev.resetAll() } }
		}

		item { Header("Vetar decoders por nombre") }
		item {
			Row(
				label = if (verDecoders) "Ocultar la lista" else "Mostrar los ${videoDecoders.size} decoders de video",
				value = if (dev.vetoedDecoders.isEmpty()) "" else "${dev.vetoedDecoders.size} vetados",
				changed = dev.vetoedDecoders.isNotEmpty(),
			) { verDecoders = !verDecoders }
		}
		if (verDecoders) {
			item { Info("El de MPEG-2 es el que ejercita el camino de respaldo.") }
			items(videoDecoders, key = { it.name }) { d ->
				Row(
					label = "${if (d.hardware) "HW" else "SW"}  ${d.name}",
					value = if (dev.isVetoed(d.name)) "VETADO" else "activo",
					changed = dev.isVetoed(d.name),
				) {
					change { if (dev.isVetoed(d.name)) dev.unveto(d.name) else dev.veto(d.name) }
				}
			}
		}
	}
}

/** Barrido de valores útiles, incluido uno absurdo para forzar el fallo. */
private fun nextOffset(current: Long?): Long? = when (current) {
	null -> 4_000L
	4_000L -> 8_000L
	8_000L -> 12_000L
	12_000L -> 20_000L
	20_000L -> 60_000L
	else -> null
}

private fun nextBuffer(current: Int?): Int? = when (current) {
	null -> 2_000
	2_000 -> 5_000
	5_000 -> 30_000
	else -> null
}

@Composable
private fun Header(text: String) = Text(
	text = text,
	fontWeight = FontWeight.Bold,
	modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
)

@Composable
private fun Info(text: String, warn: Boolean = false) = Text(
	text = text,
	fontSize = 13.sp,
	fontFamily = FontFamily.Monospace,
	fontWeight = if (warn) FontWeight.Bold else FontWeight.Normal,
)

@Composable
private fun Row(
	label: String,
	value: String,
	changed: Boolean = false,
	onClick: () -> Unit,
) {
	Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
		Text(
			text = if (value.isEmpty()) label else "${label.padEnd(44)} $value",
			fontFamily = FontFamily.Monospace,
			fontSize = 13.sp,
			fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
		)
	}
}
