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

		Line("buffer", "${stats.bufferedMs} ms")
		Line("bitrate canal", if (stats.bitrateBps > 0) "${stats.bitrateBps / 1000} kbps" else "—")
		Line("ancho de banda", if (stats.bandwidthBps > 0) "${stats.bandwidthBps / 1000} kbps" else "—")
		Line("frames perdidos", stats.droppedFrames.toString())
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
		Line("target offset", "${stats.targetOffsetMs} ms")

		Spacer()

		val degraded = stats.degradation != null && stats.degradation != "de fábrica"
		Line("degradación", stats.degradation ?: "de fábrica", warn = degraded, strong = degraded)
	}
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
