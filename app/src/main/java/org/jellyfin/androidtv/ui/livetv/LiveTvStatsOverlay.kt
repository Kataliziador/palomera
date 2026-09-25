package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.engine.EngineStats
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Overlay de diagnóstico (fase M1).
 *
 * El objetivo de la fase es poder **describir una falla con datos, no con
 * impresiones**. Por eso el dato que más importa no es el buffer ni el bitrate,
 * sino el **motivo** de la decisión: sin él, depurar en la caja de un
 * suscriptor a 2000 km es imposible.
 */
@Composable
fun LiveTvStatsOverlay(
	channel: ChannelProfile,
	stats: EngineStats,
	state: String,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.width(440.dp)
			.clip(RoundedCornerShape(8.dp))
			.background(Color.Black.copy(alpha = 0.72f))
			.padding(14.dp)
	) {
		Line("canal", "${channel.name}  (${channel.id})", strong = true)
		Line("estado", state)
		Line("motor", stats.engineId)
		Line("motivo", stats.reason ?: "—", strong = true)

		Spacer()

		Line("decoder video", stats.activeVideoDecoder ?: "—")
		Line("decoder audio", stats.activeAudioDecoder ?: "—")
		Line("códecs decl.", "${channel.videoCodec ?: "?"} / ${channel.audioCodec ?: "?"}")

		Spacer()

		// Render: es lo que M5 tiene que juzgar. El aspecto resultante ya
		// incorpora el SAR, que es el dato que deforma los canales anamórficos
		// si se ignora.
		Line(
			"resolución",
			if (stats.videoWidth > 0) "${stats.videoWidth}x${stats.videoHeight}" else "—",
		)
		Line("SAR", "%.3f".format(stats.pixelAspectRatio))
		Line("aspecto resultante", aspectoDe(stats))
		Line("fps medido", if (stats.measuredFps > 0f) "%.2f".format(stats.measuredFps) else "—")

		Spacer()

		// Audio: la clave de selección de M3 es el idioma, no el PID. Si una
		// pista desaparece a mitad de emisión, se ve aquí.
		Line(
			"pistas de audio",
			if (stats.audioTracks.isEmpty()) "ninguna" else stats.audioTracks.size.toString(),
			warn = stats.audioTracks.isEmpty(),
		)
		Line(
			"audio activo",
			stats.selectedAudio?.let { t ->
				buildString {
					append(t.language ?: "sin idioma")
					append("  ").append(t.channels).append("ch")
					// El PID solo se enseña si de verdad lo hay.
					if (t.pid >= 0) append("  pid=0x%x".format(t.pid))
				}
			} ?: "—",
		)

		Spacer()

		Line("buffer", "${stats.bufferedMs} ms")
		Line("bitrate canal", if (stats.bitrateBps > 0) "${stats.bitrateBps / 1000} kbps" else "—")
		Line("ancho de banda", if (stats.bandwidthBps > 0) "${stats.bandwidthBps / 1000} kbps" else "—")
		Line("frames perdidos", stats.droppedFrames.toString())
		// Distinto de «perdidos»: estos llegan, pero fuera de tiempo. Es lo que
		// se ve como tirón cuando todo lo demás marca cero.
		Line(
			"entregas tardías",
			"${stats.frameGapCount}  (máx ${stats.maxFrameGapMs} ms)",
			warn = stats.frameGapCount > 0,
		)
		// Si esto crece, la imagen está congelada aunque el player diga «En vivo».
		Line("sin avanzar", "${stats.msSinceProgress} ms", warn = stats.msSinceProgress > 3000)

		Spacer()

		// La relación entre estas dos es la que rompió hbohd: un offset por
		// encima de la mitad de la ventana acaba en BEHIND_LIVE_WINDOW.
		Line("live offset", "${stats.liveOffsetMs} ms")
		Line(
			"ventana DVR",
			if (stats.windowMs > 0) "${stats.windowMs} ms" else "—",
			warn = stats.windowMs > 0 && stats.targetOffsetMs > stats.windowMs / 2,
		)
		// Si el real supera al pedido, es que el suelo de HLS manda sobre
		// nosotros. Se resalta para que nadie lo dé por aplicado.
		val offsetIgnorado = stats.liveOffsetMs > 0 &&
			stats.liveOffsetMs - stats.targetOffsetMs > 2000
		Line(
			"target offset",
			"${stats.targetOffsetMs} ms" + if (offsetIgnorado) "  (no se aplica)" else "",
			warn = offsetIgnorado,
		)

		Spacer()

		val degraded = stats.degradation != null && stats.degradation != "de fábrica"
		Line("degradación", stats.degradation ?: "de fábrica", warn = degraded, strong = degraded)

		Spacer()

		// Los objetivos duros de CLAUDE.md §10. Se resaltan al incumplirse,
		// para no tener que recordar el umbral de memoria.
		Line(
			"1er frame (zapping)",
			if (stats.msToFirstFrame > 0) "${stats.msToFirstFrame} ms" else "—",
			warn = stats.msToFirstFrame > 3000,
		)
		Line("rebuffers", stats.rebufferCount.toString(), warn = stats.rebufferCount > 0)
		Line(
			"ratio de rebuffer",
			"%.3f %%".format(stats.rebufferRatio * 100),
			warn = stats.rebufferRatio > 0.003f,
		)
		Line("reintentos de carga", stats.loadRetries.toString(), warn = stats.loadRetries > 0)
		Line("tiempo en el canal", formatoDuracion(stats.sessionUptimeMs))
	}
}

/** Aspecto que se verá en pantalla, ya con el SAR aplicado. */
private fun aspectoDe(stats: EngineStats): String {
	if (stats.videoWidth <= 0 || stats.videoHeight <= 0) return "—"
	val ancho = stats.videoWidth * stats.pixelAspectRatio
	val ratio = ancho / stats.videoHeight
	val nombre = when {
		kotlin.math.abs(ratio - 16f / 9f) < 0.02f -> "16:9"
		kotlin.math.abs(ratio - 4f / 3f) < 0.02f -> "4:3"
		else -> ""
	}
	return "%.3f".format(ratio) + if (nombre.isEmpty()) "" else "  ($nombre)"
}

private fun formatoDuracion(ms: Long): String {
	val s = ms / 1000
	return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun Spacer() = Text(text = " ", fontSize = 5.sp, color = Color.Transparent)

@Composable
private fun Line(label: String, value: String, strong: Boolean = false, warn: Boolean = false) {
	Text(
		text = "${label.padEnd(19)} $value",
		color = when {
			warn -> Color(0xFFFFB300)
			strong -> Color.White
			else -> Color(0xFFBDBDBD)
		},
		fontFamily = FontFamily.Monospace,
		fontWeight = if (strong || warn) FontWeight.Bold else FontWeight.Normal,
		fontSize = 13.sp,
	)
}
