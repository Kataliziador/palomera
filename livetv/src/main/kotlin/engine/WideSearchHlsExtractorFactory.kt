package org.jellyfin.livetv.engine

import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.BundledHlsMediaChunkExtractor
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * Factory de HLS que amplía **cuánto busca el extractor TS antes de rendirse**
 * al establecer las referencias temporales.
 *
 * Por qué existe: `estrellashd` almacena datos sin problema, inicializa los
 * decoders y **nunca reporta un formato de vídeo** — se queda en «Almacenando…»
 * para siempre. Si el extractor no encuentra timestamps dentro de su ventana de
 * búsqueda, nunca establece el formato, y entonces el decodificador se queda
 * esperando algo que jamás llega. El síntoma encaja exactamente.
 *
 * El valor por defecto de Media3 son 600 paquetes TS (~113 kB). Un reproductor
 * IPTV comercial analizado el 2026-09-26 usa **3000** (~564 kB), cinco veces
 * más lejos. Este es el único dato que se tomó de ahí: un número, no código.
 *
 * Solo se aplica a segmentos MPEG-TS; cualquier otra cosa se delega a la
 * factory de Media3 sin tocarla.
 */
@UnstableApi
class WideSearchHlsExtractorFactory(
	private val payloadReaderFlags: Int,
	private val timestampSearchPackets: Int,
) : HlsExtractorFactory {

	private val delegate = DefaultHlsExtractorFactory(payloadReaderFlags, false)

	override fun createExtractor(
		uri: Uri,
		format: Format,
		muxedCaptionFormats: MutableList<Format>?,
		timestampAdjuster: TimestampAdjuster,
		responseHeaders: MutableMap<String, MutableList<String>>,
		sniffingExtractorInput: ExtractorInput,
		playerId: PlayerId,
	): HlsMediaChunkExtractor {
		if (!esTransportStream(uri, format)) {
			return delegate.createExtractor(
				uri, format, muxedCaptionFormats, timestampAdjuster,
				responseHeaders, sniffingExtractorInput, playerId,
			)
		}

		val searchBytes = timestampSearchPackets * TsExtractor.TS_PACKET_SIZE
		LiveLog.debug(
			"extractor.ventanaAmpliada",
			"paquetes" to timestampSearchPackets,
			"bytes" to searchBytes,
		)

		val ts = TsExtractor(
			TsExtractor.MODE_HLS,
			timestampAdjuster,
			DefaultTsPayloadReaderFactory(payloadReaderFlags),
			searchBytes,
		)
		return BundledHlsMediaChunkExtractor(ts, format, timestampAdjuster)
	}

	/**
	 * Flussonic sirve los segmentos como `.ts` y declara `video/MP2T`. Si
	 * alguna vez cambiara, se delega en vez de forzar el extractor equivocado.
	 */
	private fun esTransportStream(uri: Uri, format: Format): Boolean {
		val ruta = uri.lastPathSegment.orEmpty().lowercase()
		return ruta.endsWith(".ts") ||
			format.containerMimeType?.contains("mp2t", ignoreCase = true) == true ||
			format.sampleMimeType?.contains("mp2t", ignoreCase = true) == true
	}
}
