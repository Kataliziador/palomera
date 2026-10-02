package org.jellyfin.livetv.capabilities

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import org.jellyfin.livetv.diagnostics.LiveLog

/** Un decoder declarado por la plataforma. */
data class DecoderInfo(
	val name: String,
	val mime: String,
	val hardware: Boolean,
	val maxInstances: Int,
) {
	/**
	 * Declarado no es funcionando. El XML del fabricante puede listar un decoder
	 * que falla con streams reales, así que la cascada tiene que poder
	 * reaccionar en caliente (LIVETV_ENGINE.md §3).
	 */
	val software: Boolean get() = !hardware
}

/**
 * Qué puede esta caja.
 *
 * Se calcula **una vez** al arranque y se cachea con `Build.FINGERPRINT` como
 * clave: si la caja recibe una actualización de firmware, se recalcula solo.
 */
class DeviceCapabilities private constructor(
	val decoders: List<DecoderInfo>,
	val abis: List<String>,
	val sdkInt: Int,
	val totalRamMb: Int,
	val manufacturer: String,
	val model: String,
	val device: String,
	val hasVlcBackend: Boolean,
) {
	fun decodersFor(mime: String): List<DecoderInfo> =
		decoders.filter { it.mime.equals(mime, ignoreCase = true) }

	fun decoderFor(mime: String): DecoderInfo? =
		decodersFor(mime).sortedByDescending { it.hardware }.firstOrNull()

	fun hasHardwareFor(mime: String): Boolean = decodersFor(mime).any { it.hardware }

	fun hasAnyFor(mime: String): Boolean = decodersFor(mime).isNotEmpty()

	companion object {
		/**
		 * Fabrica unas capacidades a mano.
		 *
		 * Existe para las pruebas de la cascada, y no es un atajo: **la
		 * cascada se construye para cajas que no tenemos**. La de pruebas
		 * resuelve los seis canales en el primer paso, así que probar contra
		 * ella no ejercita ni uno de los caminos de respaldo. Sin poder
		 * inventar cajas, el código que decide qué hacer en una ZTE sin MPEG-2
		 * llegaría a producción sin haberse ejecutado nunca.
		 */
		fun paraPruebas(
			decoders: List<DecoderInfo>,
			abis: List<String> = listOf("armeabi-v7a"),
			sdkInt: Int = 30,
			totalRamMb: Int = 2048,
			manufacturer: String = "Prueba",
			model: String = "CajaDePrueba",
			device: String = "prueba",
			hasVlcBackend: Boolean = false,
		) = DeviceCapabilities(
			decoders, abis, sdkInt, totalRamMb, manufacturer, model, device, hasVlcBackend,
		)

		private const val PREFS = "livetv.capabilities"
		private const val KEY_FINGERPRINT = "fingerprint"

		@Volatile
		private var cached: DeviceCapabilities? = null

		fun of(context: Context, hasVlcBackend: Boolean = false): DeviceCapabilities =
			cached ?: synchronized(this) {
				cached ?: build(context, hasVlcBackend).also { cached = it }
			}

		private fun build(context: Context, hasVlcBackend: Boolean): DeviceCapabilities {
			val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			val known = prefs.getString(KEY_FINGERPRINT, null)
			if (known != Build.FINGERPRINT) {
				LiveLog.info(
					"caps.firmwareChanged",
					"anterior" to (known ?: "ninguno"),
					"actual" to Build.FINGERPRINT,
				)
				prefs.edit().putString(KEY_FINGERPRINT, Build.FINGERPRINT).apply()
			}

			val caps = DeviceCapabilities(
				decoders = enumerate(),
				abis = Build.SUPPORTED_ABIS.toList(),
				sdkInt = Build.VERSION.SDK_INT,
				totalRamMb = totalRamMb(context),
				manufacturer = Build.MANUFACTURER,
				model = Build.MODEL,
				device = Build.DEVICE,
				hasVlcBackend = hasVlcBackend,
			)

			LiveLog.info(
				"caps.built",
				"decoders" to caps.decoders.size,
				"abis" to caps.abis.joinToString(","),
				"ramMb" to caps.totalRamMb,
			)
			return caps
		}

		/**
		 * Enumerar con `ALL_CODECS`, no `REGULAR_CODECS`: los decoders que solo
		 * existen en modo tunneled no aparecen en el segundo.
		 */
		private fun enumerate(): List<DecoderInfo> =
			MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
				.filterNot { it.isEncoder }
				.flatMap { info ->
					info.supportedTypes.map { mime ->
						DecoderInfo(
							name = info.name,
							mime = mime,
							hardware = isHardware(info),
							maxInstances = maxInstances(info, mime),
						)
					}
				}

		/**
		 * `isHardwareAccelerated()` existe desde API 29. Por debajo hay que
		 * inferir por prefijo: `c2.android.*` y `OMX.google.*` son software.
		 */
		private fun isHardware(info: MediaCodecInfo): Boolean =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				info.isHardwareAccelerated
			} else {
				val n = info.name.lowercase()
				!(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.sec.") && n.contains(".sw."))
			}

		private fun maxInstances(info: MediaCodecInfo, mime: String): Int =
			runCatching { info.getCapabilitiesForType(mime).maxSupportedInstances }
				.getOrElse {
					// Cero try/catch silenciosos (CLAUDE.md regla 8).
					LiveLog.warn("caps.maxInstancesDesconocido", "codec" to info.name, "mime" to mime)
					-1
				}

		private fun totalRamMb(context: Context): Int {
			val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
				?: return -1
			val mi = ActivityManager.MemoryInfo()
			am.getMemoryInfo(mi)
			return (mi.totalMem / (1024 * 1024)).toInt()
		}
	}
}
