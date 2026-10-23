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
	/**
	 * Si el decoder **declara** `feature-tunneled-playback` para este mime.
	 *
	 * No es una curiosidad del inventario: es la condición que Media3 evalúa
	 * en silencio. Si se pide tunneled y el decoder elegido no lo declara,
	 * `DefaultTrackSelector.maybeConfigureRenderersForTunneling` **lo apaga
	 * sin decir nada** y la sesión corre sin él (pendiente 78).
	 *
	 * Por defecto `false` para las capacidades fabricadas a mano de las
	 * pruebas: una caja inventada no declara nada mientras no se diga.
	 */
	val tunneled: Boolean = false,
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
	/**
	 * Las mismas capacidades **sin** los decoders vetados por nombre.
	 *
	 * El menú de degradación permite vetar un decoder para simular una caja
	 * peor, y hasta el 08-oct ese veto **no hacía nada**: se guardaba, se
	 * contaba en la línea de degradación y se enseñaba en el menú, y ninguna
	 * línea lo leía. Se descubrió al intentar probar M6 con él, que es
	 * exactamente para lo que existe —«con el MPEG-2 de hardware vetado, el
	 * canal mp2v reproduce por libVLC» (`LIVETV_PLAN.md` M6)—.
	 *
	 * Se aplica aquí y no dentro de la cascada porque la cascada ya tiene una
	 * sola fuente de verdad sobre lo que la caja puede: esta clase. Un filtro
	 * paralelo daría dos respuestas distintas a «¿hay decoder para esto?».
	 *
	 * Devuelve `this` si no hay nada que quitar, para no construir un objeto
	 * por recomposición en el caso normal, que es el de fábrica.
	 */
	fun excluyendo(nombres: Set<String>): DeviceCapabilities {
		if (nombres.isEmpty()) return this
		// Insensible a mayúsculas, como todo lo que se compara contra un
		// nombre que viene del `config.json`: un veto que no casa por una
		// mayúscula no hace nada y no se nota hasta que alguien mira la tele.
		val vetados = nombres.map { it.trim().lowercase() }.toSet()
		val quedan = decoders.filterNot { it.name.lowercase() in vetados }
		if (quedan.size == decoders.size) return this
		LiveLog.warn(
			"caps.decodersVetados",
			"cuantos" to (decoders.size - quedan.size),
			"nombres" to nombres.joinToString(","),
		)
		return DeviceCapabilities(
			quedan, abis, sdkInt, totalRamMb, manufacturer, model, device, hasVlcBackend,
		)
	}

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

		fun of(context: Context): DeviceCapabilities =
			cached ?: synchronized(this) {
				cached ?: build(context).also { cached = it }
			}

		/**
		 * Si libVLC viaja en este APK.
		 *
		 * **Era un parámetro de `of()` y eso estaba mal.** La caché devuelve lo
		 * primero que se construyó e ignora los argumentos de las llamadas
		 * siguientes, así que el valor acababa dependiendo de **quién llamara
		 * primero**: `RegistryRepository` consulta las capacidades al arrancar,
		 * antes que el reproductor, y fijaba `false` para toda la vida del
		 * proceso. Costó un rato el 08-oct, con el motor ya escrito y la
		 * cascada negándose a elegirlo.
		 *
		 * Y el fondo del error es que **no es una propiedad de la caja**: es
		 * una propiedad del APK. O libVLC está compilado dentro o no está, y
		 * eso no lo decide quien pregunta. Se mira una vez, por reflexión, para
		 * que quitar la dependencia no deje la cascada apuntando a un motor
		 * que no existe.
		 */
		private val vlcEnElApk: Boolean by lazy {
			runCatching { Class.forName("org.videolan.libvlc.LibVLC") }.isSuccess
		}

		private fun build(context: Context): DeviceCapabilities {
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
				hasVlcBackend = vlcEnElApk,
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
							tunneled = declaraTunneled(info, mime),
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

		/**
		 * Medido el 14-oct sobre los ficheros de códecs de la propia Skyworth:
		 * `OMX.amlogic.avc.decoder.awesome2` y
		 * `OMX.amlogic.mpeg2.decoder.awesome2` **sí** declaran
		 * `feature-tunneled-playback`, mientras que el fichero entero de los de
		 * software —`/apex/com.android.media.swcodec/etc/media_codecs.xml`— no
		 * menciona `tunneled` ni una vez. O sea que degradar a software con
		 * `forceSoftwareVideo` reproduce la condición del pendiente 78 en la
		 * caja que tenemos.
		 */
		private fun declaraTunneled(info: MediaCodecInfo, mime: String): Boolean =
			runCatching {
				info.getCapabilitiesForType(mime)
					.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)
			}.getOrElse {
				// Cero try/catch silenciosos (CLAUDE.md regla 8).
				LiveLog.warn("caps.tunneledDesconocido", "codec" to info.name, "mime" to mime)
				false
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
