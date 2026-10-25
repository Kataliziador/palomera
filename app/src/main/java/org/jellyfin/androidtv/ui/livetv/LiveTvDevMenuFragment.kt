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
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.config.RegistryRepository
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.diagnostics.Diagnostico
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * Menú de degradación y diagnóstico (LIVETV_ENGINE.md §7).
 *
 * Existe porque la caja de pruebas es el **mejor caso posible**: trae MPEG-2,
 * MP2 y AC-3 por hardware. Sin poder empeorarla a propósito, los caminos de
 * respaldo llegarían a producción sin haberse ejercitado nunca.
 *
 * Todo lo de aquí arranca en el valor de fábrica: abrir el menú no cambia nada.
 *
 * **Solo en compilaciones de depuración** (decisión de Edgar, 11-oct). La
 * comprobación está aquí **además** de en el botón que lleva aquí: el botón es
 * lo que se ve, y esto es lo que cierra la puerta si alguien llega al destino
 * por otro camino. Un menú que puede vetar decoders y reapuntar el registro no
 * se protege solo escondiendo su botón.
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
				if (BuildConfig.DEBUG) DevMenu()
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
				label = "Overlay  (oculto → compacto → completo)",
				value = dev.describeOverlay(),
				changed = dev.overlayMode != DevOptions.OVERLAY_OCULTO,
			) { change { dev.siguienteOverlay() } }
		}
		item {
			// Antes de volcar, para que el archivo contenga solo la observación
			// que interesa y no lo que se acumuló cambiando de canal.
			Row(label = "Limpiar el log y empezar de cero", value = "${LiveLog.snapshot().size} entradas") {
				// Por `change` para que el contador de las dos filas se rehaga.
				change { LiveLog.clear() }
				Toast.makeText(context, "Log limpio", Toast.LENGTH_SHORT).show()
			}
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
				value = (if (dev.tunnelingEnabled) "ACTIVA" else "APAGADA") +
					"  (${dev.procedenciaDe(DevOptions.K_TUNNELING)})",
				changed = dev.procedenciaDe(DevOptions.K_TUNNELING) == "menú",
			) { change { dev.tunnelingEnabled = !dev.tunnelingEnabled } }
		}
		item {
			Info("El camino que usan las apps nativas de TV. Su fallo da pantalla negra sin aviso.")
		}

		item {
			Row(
				label = "Rescate: apagar tunneled si no da imagen",
				value = if (dev.rescateTunneledEnabled) "ACTIVO" else "apagado (fábrica)",
				changed = dev.rescateTunneledEnabled,
			) { change { dev.rescateTunneledEnabled = !dev.rescateTunneledEnabled } }
		}
		item {
			Info("Reproduciendo, con la posición avanzando y sin primer fotograma a los 8 s: se apaga el tunneled y se rehace. Pendiente de validación.")
		}

		item {
			Row(
				label = "Zapear desde las pantallas de aviso",
				value = if (dev.zapeoEnAvisoEnabled) "ACTIVO" else "apagado (pendiente 82)",
				changed = dev.procedenciaDe(DevOptions.K_ZAPEO_AVISO) == "menú",
			) { change { dev.zapeoEnAvisoEnabled = !dev.zapeoEnAvisoEnabled } }
		}
		item {
			Info("Con un canal caído en pantalla, ▲▼ siguen cambiando de canal. Apagado, solo se sale con ATRÁS: es el pendiente 82 tal como se midió.")
		}

		item {
			Row(
				label = "Fingir tunneled sin imagen",
				value = if (dev.simularTunneledSinImagen) "FINGIENDO" else "no",
				changed = dev.simularTunneledSinImagen,
			) { change { dev.simularTunneledSinImagen = !dev.simularTunneledSinImagen } }
		}
		item {
			Info("Para ver el rescate en una caja que sí da imagen. Hay que tener el rescate activo.")
		}

		item {
			Row(
				label = "Búsqueda amplia de timestamps TS",
				value = "${dev.timestampSearchPackets} paquetes",
				changed = dev.wideTimestampSearch,
			) { change { dev.wideTimestampSearch = !dev.wideTimestampSearch } }
		}
		item {
			Info("Si el extractor no halla referencias temporales, nunca reporta formato de vídeo.")
		}

		item {
			Row(
				label = "Vigilante de recuperación",
				value = if (dev.watchdogDisabled) "APAGADO" else "activo (fábrica)",
				changed = dev.watchdogDisabled,
			) { change { dev.watchdogDisabled = !dev.watchdogDisabled } }
		}
		item {
			Info("Apagarlo reproduce el fallo del 30-sep: congelado indefinidamente con el buffer lleno.")
		}

		item {
			Row(
				label = "Idioma de audio del config.json",
				value = if (dev.audioDefaultEnabled) "se aplica (fábrica)" else "NO SE APLICA",
				changed = !dev.audioDefaultEnabled,
			) { change { dev.audioDefaultEnabled = !dev.audioDefaultEnabled } }
		}
		item {
			Info("Apagarlo reproduce el comportamiento anterior: arranca la pista que elija Media3, no la que pide el config.json.")
		}

		item {
			Row(
				label = "Dimensionar la superficie (respeta el SAR)",
				value = if (dev.superficieDimensionada) "dimensionada (fábrica)" else "A PANTALLA COMPLETA",
				changed = !dev.superficieDimensionada,
			) { change { dev.superficieDimensionada = !dev.superficieDimensionada } }
		}
		item {
			Info("Apagarlo vuelve al comportamiento anterior: la superficie llena la pantalla y el códec estira el vídeo hasta ella, así que un canal 4:3 sale deformado.")
		}
		item {
			Row(
				label = "Entregar la superficie solo al cambiar",
				value = if (dev.attachSoloAlCambiar) "solo al cambiar (fábrica)" else "EN CADA RECOMPOSICIÓN",
				changed = !dev.attachSoloAlCambiar,
			) { change { dev.attachSoloAlCambiar = !dev.attachSoloAlCambiar } }
		}
		item {
			Info("Apagarlo vuelve al comportamiento anterior: 2 entregas por segundo, que dan la vuelta al logcat en menos de un minuto.")
		}

		item {
			Row(
				label = "Forzar desentrelazado",
				value = if (dev.forzarDesentrelazado) "FORZADO" else "lo dice el config.json",
				changed = dev.forzarDesentrelazado,
			) { change { dev.forzarDesentrelazado = !dev.forzarDesentrelazado } }
		}
		item {
			Info("Para MPEG-2 Flussonic no sabe si va entrelazado y el canal se publica como progresivo. Sin esto, libVLC decodifica pero no desentrelaza.")
		}

		item {
			Row(
				label = "Volver al borde calculando la posición",
				value = if (dev.resyncCalculando) "sí (fábrica)" else "NO, COMO ANTES",
				changed = !dev.resyncCalculando,
			) { change { dev.resyncCalculando = !dev.resyncCalculando } }
		}
		item {
			Info("La vuelta al borde usaba la «posición por defecto» del reproductor y medido una noche entera no devolvía al directo: 547 saltos y el offset seguía en 37 s. Ahora salta a ventana menos retraso objetivo, y medido de día eso bajó a 9 saltos en 3,4 h con el directo en 17-19 s. Apagarlo vuelve al comportamiento anterior.")
		}

		item {
			Row(
				label = "Volver al borde solo si el salto adelanta",
				value = if (dev.resyncSoloSiAdelanta) "ON (refutado)" else "off (fábrica)",
				changed = dev.resyncSoloSiAdelanta,
			) { change { dev.resyncSoloSiAdelanta = !dev.resyncSoloSiAdelanta } }
		}
		item {
			Row(
				label = "Alejar del borde si el buffer no aguanta",
				value = if (dev.resyncPorMargen) "sí (fábrica)" else "NO, COMO ANTES",
				changed = !dev.resyncPorMargen,
			) { change { dev.resyncPorMargen = !dev.resyncPorMargen } }
		}
		item {
			Info("Cuando el origen deja de publicar, el reproductor se queda a 1,5 s del final de los datos y entra en un bucle de un corte cada tres segundos: 25 de los 35 cortes de la sesión del 10-oct. El latido lo detecta en 8 s y mueve la posición hacia atrás. Medido en 7 h 22 el 11-oct: 6 cortes contra 35, y 0,26 % de imagen parada contra 0,88. Lo que compra no es rellenar el buffer en el acto —medido, no lo rellena— sino dejar la posición lejos del borde durante horas: el tramo que salio de un salto dio 4 h 07 sin un corte. Sale «offset.margenCorto» en el log. Apagarlo vuelve al comportamiento anterior.")
		}

		item {
			Info("REFUTADO el 11-oct: déjalo apagado. Se puso creyendo que un salto que se aleja del directo era un corte desperdiciado, y medido en pantalla es lo contrario: ese salto deja la posición 9 s más adentro del buffer y es la única cura cuando el origen publica más despacio que el reloj. Encenderlo costó 12 cortes en 33 s donde el comportamiento de fábrica costaba 1. Se conserva solo para reproducir la medición.")
		}

		item {
			Row(
				label = "Preparar tras tener la superficie",
				value = if (dev.prepararConSuperficie) "sí (fábrica)" else "NO, COMO ANTES",
				changed = !dev.prepararConSuperficie,
			) { change { dev.prepararConSuperficie = !dev.prepararConSuperficie } }
		}
		item {
			Info("Apagarlo vuelve al comportamiento anterior: preparar antes de tener la superficie y aceptarla en caliente, que lanzaba una aserción de Media3 en 9 de cada 11 sintonías y obligaba a preparar otra vez. No cambia el tiempo del primer fotograma.")
		}

		item {
			Row(
				label = "Simular VPU que no desentrelaza",
				value = if (dev.simularVpuSinDeint) "SIMULADO" else "lo dice el config.json",
				changed = dev.simularVpuSinDeint,
			) { change { dev.simularVpuSinDeint = !dev.simularVpuSinDeint } }
		}
		item {
			Info("Hace creer al motor que el chip de esta caja no desentrelaza. Con el canal marcado entrelazado, la cascada lo manda a libVLC por software. Es la única forma de probar aquí lo que en un site publica el config.json por modelo.")
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
				value = (if (dev.tsAllowNonIdrKeyframes) "ON" else "APAGADO") +
					"  (${dev.procedenciaDe(DevOptions.K_TS_NONIDR)})",
				changed = dev.procedenciaDe(DevOptions.K_TS_NONIDR) == "menú",
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
			Row(label = "minBufferMs", value = v?.let { "$it ms" } ?: "5000 (fábrica)", changed = v != null) {
				change { dev.minBufferOverrideMs = nextBuffer(v) }
			}
		}

		item { Header("Registro de sites") }
		item {
			Row(
				label = "Olvidar la autorización guardada",
				value = "y volver a preguntar",
			) {
				change {
					val n = RegistryRepository(context).olvidar()
					Toast.makeText(context, "Olvidadas: $n", Toast.LENGTH_SHORT).show()
				}
			}
		}
		item {
			Info("Un técnico que acaba de dar de alta un site no tiene por qué esperar a que caduque.")
		}

		item {
			Row(
				label = "Fingir que el registro deniega",
				value = when (dev.simularDenegacion) {
					1 -> "TEXTO GENÉRICO"
					2 -> "TEXTO DE CADUCADA"
					else -> "no"
				},
				changed = dev.simularDenegacion > 0,
			) { change { dev.simularDenegacion = (dev.simularDenegacion + 1) % 3 } }
		}
		item {
			Info("Enseña la sección cerrada sin levantar un registro que deniegue. Dos textos porque lo que hay que mirar es la maquetación: el de caducada son dos frases largas. Volver a la sección y salir para que se note.")
		}

		item {
			Row(
				label = "Olvidar el diagnóstico acumulado",
				value = "para medir desde cero",
			) {
				change {
					Diagnostico.of(context).olvidar()
					Toast.makeText(context, "Diagnóstico a cero", Toast.LENGTH_SHORT).show()
				}
			}
		}
		item {
			Info("Se acumula desde la instalación y viaja con la consulta al registro. No lleva historial de lo visto.")
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
				// Un veto del servidor no se puede quitar desde aquí: es
				// conocimiento de un modelo entero, y si hay que volver a
				// probar ese decoder se quita del `config.json`, que es donde
				// se escribió. Enseñarlo aparte importa — «VETADO» y «lo veta
				// el servidor» se arreglan en sitios distintos.
				val delServidor = dev.ajustesDelServidor?.blockedDecoders.orEmpty()
					.any { it.equals(d.name, ignoreCase = true) }
				Row(
					label = "${if (d.hardware) "HW" else "SW"}  ${d.name}",
					value = when {
						delServidor -> "VETADO POR EL SERVIDOR"
						dev.isVetoed(d.name) -> "VETADO"
						else -> "activo"
					},
					changed = delServidor || dev.isVetoed(d.name),
				) {
					if (delServidor) return@Row
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
	// 8 000 y 10 000 añadidos el 09-oct. El barrido saltaba de 5 000 a 30 000 y
	// dejaba sin probar justo el tramo que importa: medido ese día, el cliente
	// se asienta ~10,3 s por detrás del directo en una ventana de 30-38 s, así
	// que por delante de su posición hay ~10 s de material publicado. Pedir
	// 5 000 deja el buffer oscilando entre 3 y 5; pedir 30 000 no cabe. Entre
	// los dos nunca se midió nada.
	5_000 -> 8_000
	8_000 -> 10_000
	10_000 -> 30_000
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
