package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.engine.EngineStats
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Overlay compacto: dos líneas para mirar el canal, no para auditarlo.
 *
 * Lleva lo que Edgar pidió poder ver **mientras** ve televisión: qué
 * decodificadores están activos y qué le cuesta a la caja. Lo demás vive en el
 * overlay completo, que ocupa un tercio de la pantalla y estorba para juzgar
 * imagen.
 *
 * La segunda línea se pone ámbar sola cuando hay algo que mirar, para que no
 * haya que leerla: si está gris, no está pasando nada.
 */
@Composable
fun LiveTvCompactOverlay(
	channel: ChannelProfile,
	stats: EngineStats,
	modifier: Modifier = Modifier,
) {
	val alarma = stats.rebufferCount > 0 ||
		stats.msSinceProgress > 3000 ||
		stats.cpuPercent > 85f ||
		stats.droppedFrames > 0

	Column(
		modifier = modifier
			.clip(RoundedCornerShape(6.dp))
			// 0,55 dejaba el fondo en 115 sobre vídeo blanco, y ahí ninguna de
			// las dos tintas llega a 4,5:1. Mismo defecto que la barra de canal
			// (30-oct); esto se lee en una tele durante una medición.
			.background(Color.Black.copy(alpha = 0.80f))
			.padding(horizontal = 10.dp, vertical = 6.dp)
	) {
		Linea(
			buildString {
				append(channel.id)
				append("  v:").append(corto(stats.activeVideoDecoder))
				append("  a:").append(corto(stats.activeAudioDecoder))
				// El códec solo se escribe cuando **no** es AAC. Si todo está
				// bien no gasta sitio, y si el site está mal normalizado el
				// dato está en pantalla sin tener que pedir el log.
				audioSinNormalizar(stats.audioMime)?.let { append(" ⚠").append(it) }
				if (stats.videoWidth > 0) {
					append("  ").append(stats.videoWidth).append('x').append(stats.videoHeight)
					append('→').append(aspectoCorto(stats))
				}
				if (stats.measuredFps > 0f) append("  %.1ffps".format(stats.measuredFps))
			},
			LiveTvPaleta.tintaApagadaSobreVideo,
		)

		Linea(
			buildString {
				append("cpu ").append("%.0f%%".format(stats.cpuPercent))
				stats.loadAvg1?.let { append("  carga ").append("%.1f".format(it)) }
				if (stats.ramAvailableMb > 0) append("  ram ").append(stats.ramAvailableMb).append('M')
				// **Lo que no se mide no se escribe.** libVLC no expone ni el
				// buffer ni la distancia al directo, y escribirlos en cero los
				// hace indistinguibles de una medida de cero — que es justo el
				// valor alarmante. Un hueco se lee como «no lo sé»; un 0.0s se
				// lee como «se acabó el buffer».
				if (stats.bufferedMs > 0) append("  buf ").append("%.1fs".format(stats.bufferedMs / 1000.0))
				if (stats.liveOffsetMs > 0) append("  dir ").append("%.1fs".format(stats.liveOffsetMs / 1000.0))
				append("  tard ").append(stats.frameGapCount)
				if (stats.maxFrameGapMs > 0) append('/').append(stats.maxFrameGapMs).append("ms")
				append("  perd ").append(stats.droppedFrames)
				append("  reb ").append(stats.rebufferCount)
				if (stats.loadRetries > 0) append("  ret ").append(stats.loadRetries)
			},
			if (alarma) LiveTvPaleta.aviso else LiveTvPaleta.tintaSutilSobreVideo,
		)
	}
}

/**
 * `OMX.amlogic.avc.decoder.awesome2` → `amlogic.avc`.
 *
 * El nombre completo no cabe en una línea y su parte informativa son dos
 * palabras: quién lo fabrica y qué códec decodifica.
 */
/**
 * El códec de audio, abreviado, **solo si no es AAC**.
 *
 * `CLAUDE.md` §2: todo el audio debe llegar normalizado a AAC desde el
 * headend. Cualquier otra cosa es un site mal configurado, y aunque esta caja
 * lo decodifique por hardware, otra no lo hará.
 */
private fun audioSinNormalizar(mime: String?): String? {
	if (mime.isNullOrBlank() || mime == "audio/mp4a-latm") return null
	return mime.substringAfterLast('/').uppercase()
}

private fun corto(nombre: String?): String {
	if (nombre.isNullOrBlank()) return "—"
	val partes = nombre
		.removePrefix("OMX.")
		.removePrefix("c2.")
		.removeSuffix(".awesome2")
		.removeSuffix(".decoder")
		.split('.')
		.filter { it.isNotBlank() && it != "decoder" }
	return if (partes.size >= 2) "${partes[0]}.${partes[1]}" else partes.joinToString(".")
}

private fun aspectoCorto(stats: EngineStats): String {
	if (stats.videoWidth <= 0 || stats.videoHeight <= 0) return "?"
	val ratio = (stats.videoWidth * stats.pixelAspectRatio) / stats.videoHeight
	return when {
		kotlin.math.abs(ratio - 16f / 9f) < 0.02f -> "16:9"
		kotlin.math.abs(ratio - 4f / 3f) < 0.02f -> "4:3"
		else -> "%.2f".format(ratio)
	}
}

@Composable
private fun Linea(texto: String, color: Color) = Text(
	text = texto,
	color = color,
	fontFamily = FontFamily.Monospace,
	fontSize = 12.sp,
)
