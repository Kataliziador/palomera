package org.jellyfin.livetv.engine

import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.diagnostics.ServerDeviceSettings
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Decide **cómo** se va a reproducir un canal en **esta** caja.
 *
 * Tres fuentes, en el orden de `CLAUDE.md` §6:
 *
 * ```
 * 1. deviceOverrides del servidor   ← lo más específico, gana siempre
 * 2. preferencia del canal          ← si la caja puede cumplirla
 * 3. detección propia de la caja    ← el respaldo que nunca falla
 * ```
 *
 * **El servidor sugiere, la caja verifica.** Una orden que el hardware no
 * puede cumplir se ignora, se registra y se sigue con la detección propia.
 * Obedecer a ciegas convertiría una errata en el `config.json` en un canal
 * muerto en miles de casas.
 *
 * **`reason` no es decorativo.** Va al overlay y al log, y es lo único que
 * permite entender por qué una caja ajena eligió lo que eligió.
 */
object PlanBuilder {

	/** Los MIME que la cascada sabe mirar. `null` = el canal no lo declara. */
	fun mimeDe(codec: String?): String? {
		val c = codec?.lowercase()?.trim()
		// Ya viene como MIME. Pasa cuando el plan se rehace desde lo observado
		// en el stream, y también si el operador escribe «video/avc» en el
		// `config.json`, que es una forma razonable de decirlo.
		if (c != null && c.startsWith("video/")) return c
		return when (c) {
			null, "" -> null
			"h264", "avc", "avc1" -> "video/avc"
			"hevc", "h265" -> "video/hevc"
			"mpeg2video", "mpeg2", "mp2v" -> "video/mpeg2"
			"mpeg4", "mp4v" -> "video/mp4v-es"
			"vp9" -> "video/x-vnd.on2.vp9"
			"av1" -> "video/av01"
			else -> null
		}
	}

	fun build(
		channel: ChannelProfile,
		caps: DeviceCapabilities,
		servidor: ServerDeviceSettings?,
		tsFlags: Int,
		forzarSoftwareLocal: Boolean,
		motorForzadoLocal: String?,
		forzarDeintLocal: Boolean = false,
	): PlaybackPlan {
		val motivos = mutableListOf<String>()

		// --- Filtro duro del canal -----------------------------------------
		//
		// `enginesAllowed` no es una preferencia: un motor ausente de esa lista
		// no se elige **nunca**, ni como último recurso. Es la forma de decir
		// «este canal con este motor no, pase lo que pase».
		val permitidos = channel.enginesAllowed
		fun permitido(motor: String) = permitidos == null || motor in permitidos

		// --- 1. Lo que manda el operador -----------------------------------
		val motorPedido = motorForzadoLocal ?: servidor?.engine
		val motorElegido = when {
			motorPedido == null -> null

			!permitido(motorPedido) -> {
				LiveLog.warn(
					"plan.motorNoPermitido",
					"canal" to channel.id,
					"pedido" to motorPedido,
					"permitidos" to permitidos,
				)
				null
			}

			motorPedido == VLC_ENGINE_ID && !caps.hasVlcBackend -> {
				// El servidor sugiere, la caja verifica: pedir libVLC donde no
				// está compilado dejaría el canal sin reproducir.
				LiveLog.warn("plan.motorNoDisponible", "canal" to channel.id, "pedido" to motorPedido)
				null
			}

			else -> motorPedido.also {
				motivos += if (motorForzadoLocal != null) "motor=$it (menú)" else "motor=$it (servidor)"
			}
		}

		// --- 2. El camino de decodificación de vídeo ------------------------
		val mime = mimeDe(channel.videoCodec)
		val forzarSoftware = forzarSoftwareLocal || servidor?.forceSoftwareVideo == true

		val videoPath = when {
			// Forzar software es una herramienta de diagnóstico y de rescate:
			// manda sobre la detección, pero no sobre la realidad.
			forzarSoftware && mime != null && caps.decodersFor(mime).any { it.software } -> {
				motivos += "software forzado"
				DecodePath.SW_PLATFORM
			}

			forzarSoftware && mime != null && caps.hasVlcBackend && permitido(VLC_ENGINE_ID) -> {
				motivos += "software forzado, sin decoder de plataforma"
				DecodePath.SW_VLC
			}

			// El canal no declara códec: no se puede decidir de antemano, así
			// que se empieza por hardware y el plan se rehace al descubrir las
			// pistas de verdad (§4.6).
			mime == null -> {
				motivos += "códec sin declarar, se asume hardware"
				DecodePath.HW
			}

			caps.hasHardwareFor(mime) -> {
				motivos += "hw para $mime"
				DecodePath.HW
			}

			caps.decodersFor(mime).any { it.software } -> {
				motivos += "sin hw para $mime, software de plataforma"
				DecodePath.SW_PLATFORM
			}

			caps.hasVlcBackend && permitido(VLC_ENGINE_ID) -> {
				motivos += "sin decoder de plataforma para $mime, libVLC"
				DecodePath.SW_VLC
			}

			else -> {
				LiveLog.error(
					"plan.sinCamino",
					null,
					"canal" to channel.id,
					"mime" to mime,
					"decodersConocidos" to caps.decoders.size,
				)
				return PlaybackPlan(
					engineId = MEDIA3_ENGINE_ID,
					videoPath = DecodePath.HW,
					audioPath = DecodePath.HW,
					deinterlace = DeinterlaceMode.NONE,
					targetOffsetMs = offsetDe(channel, servidor),
					tsFlags = tsFlags,
					reason = "sin decoder para $mime en esta caja",
					unsupported = true,
				)
			}
		}

		// El camino de vídeo decide el motor cuando nadie lo impuso.
		val engineId = motorElegido
			?: if (videoPath == DecodePath.SW_VLC) VLC_ENGINE_ID else MEDIA3_ENGINE_ID

		// --- 3. Entrelazado -------------------------------------------------
		//
		// No se puede preguntar por API si el VPU desentrelaza (§4.4): se mira
		// en pantalla una vez por modelo y el resultado se publica en
		// `deviceOverrides`. Sin ese dato se asume que sí, porque es lo que
		// hace cualquier caja de televisión moderna y porque asumir que no
		// llevaría a desentrelazar dos veces.
		// `entrelazado` y no `channel.interlaced` a secas: para MPEG-2 la API de
		// Flussonic devuelve `field_order=unknown` y el generador publica
		// `interlaced: false` en un canal que está medido 480i (pendiente 53).
		// Sin esta salida, el respaldo de libVLC se elige y luego no
		// desentrelaza, que es la mitad inútil del camino.
		val entrelazado = channel.interlaced || forzarDeintLocal
		val deinterlace = when {
			!entrelazado -> DeinterlaceMode.NONE
			engineId == VLC_ENGINE_ID -> DeinterlaceMode.SOFTWARE.also { motivos += "deint por software" }
			servidor?.vpuDeinterlaces == false -> {
				// La caja no desentrelaza y no hay libVLC: queda documentado
				// como limitación del modelo en vez de enseñarse con peine
				// callando.
				LiveLog.warn("plan.sinDesentrelazado", "canal" to channel.id, "modelo" to caps.model)
				motivos += "el VPU no desentrelaza en este modelo"
				DeinterlaceMode.NONE
			}
			else -> DeinterlaceMode.VPU
		}

		return PlaybackPlan(
			engineId = engineId,
			videoPath = videoPath,
			// Sin cascada de audio: todo llega en AAC desde el headend (§4.3).
			audioPath = DecodePath.HW,
			deinterlace = deinterlace,
			targetOffsetMs = offsetDe(channel, servidor),
			tsFlags = tsFlags,
			reason = motivos.joinToString(" · ").ifEmpty { "detección propia" },
		)
	}

	/**
	 * Rehace el plan cuando el stream desmiente al `config.json` (§4.6).
	 *
	 * El códec declarado es una pista; el stream es la verdad. Si el headend
	 * recodifica un canal y nadie actualiza el JSON, el plan se habría
	 * construido sobre un dato falso.
	 */
	fun revisar(
		plan: PlaybackPlan,
		channel: ChannelProfile,
		mimeObservado: String?,
		caps: DeviceCapabilities,
		servidor: ServerDeviceSettings?,
		tsFlags: Int,
		forzarSoftwareLocal: Boolean,
		motorForzadoLocal: String?,
	): PlaybackPlan? {
		val declarado = mimeDe(channel.videoCodec)
		if (mimeObservado == null || mimeObservado == declarado) return null

		LiveLog.warn(
			"plan.codecDiscrepante",
			"canal" to channel.id,
			"declarado" to (declarado ?: channel.videoCodec),
			"observado" to mimeObservado,
			"detalle" to "manda el stream",
		)

		return build(
			channel = channel.copy(videoCodec = mimeObservado),
			caps = caps,
			servidor = servidor,
			tsFlags = tsFlags,
			forzarSoftwareLocal = forzarSoftwareLocal,
			motorForzadoLocal = motorForzadoLocal,
		)
	}

	private fun offsetDe(channel: ChannelProfile, servidor: ServerDeviceSettings?): Long =
		servidor?.targetOffsetMs
			?: channel.targetOffsetMs
			?: LiveTuning.TARGET_OFFSET_DEFAULT_MS

	const val MEDIA3_ENGINE_ID = "media3"
	const val VLC_ENGINE_ID = "libvlc"
}
