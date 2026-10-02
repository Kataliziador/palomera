package org.jellyfin.livetv.engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jellyfin.livetv.capabilities.DecoderInfo
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.diagnostics.ServerDeviceSettings
import org.jellyfin.livetv.source.ChannelProfile

/**
 * La cascada existe **para cajas que no tenemos**.
 *
 * Todo lo que sabemos del motor lo sabemos contra una Skyworth que trae MPEG-2,
 * MP2 y AC-3 por hardware — «el mejor caso posible», según `CLAUDE.md` §9. Esa
 * caja resuelve los seis canales en el primer paso de la cascada, así que
 * probar contra ella **no ejercita nada**.
 *
 * De ahí que estas pruebas fabriquen cajas: una sin MPEG-2, una sin nada, una
 * con libVLC y otra sin él. Es la única forma de saber que el día que llegue la
 * ZTE el código ya sabe qué hacer.
 */
class PlanBuilderTest : StringSpec({

	fun caja(
		decoders: List<DecoderInfo>,
		vlc: Boolean = false,
		modelo: String = "CajaDePrueba",
	) = DeviceCapabilities.paraPruebas(
		decoders = decoders,
		model = modelo,
		hasVlcBackend = vlc,
	)

	fun dec(mime: String, hardware: Boolean, nombre: String = "dec.$mime") =
		DecoderInfo(name = nombre, mime = mime, hardware = hardware, maxInstances = 1)

	val avcHw = dec("video/avc", hardware = true, nombre = "OMX.amlogic.avc.decoder")
	val avcSw = dec("video/avc", hardware = false, nombre = "c2.android.avc.decoder")
	val mpeg2Hw = dec("video/mpeg2", hardware = true, nombre = "OMX.amlogic.mpeg2.decoder")
	val mpeg2Sw = dec("video/mpeg2", hardware = false, nombre = "c2.android.mpeg2.decoder")

	val h264 = ChannelProfile(id = "c1", name = "HD", url = "http://e/1.m3u8", videoCodec = "h264")
	val mpeg2 = ChannelProfile(id = "c2", name = "SD", url = "http://e/2.m3u8", videoCodec = "mpeg2video")

	fun plan(
		canal: ChannelProfile,
		caps: DeviceCapabilities,
		servidor: ServerDeviceSettings? = null,
		software: Boolean = false,
		motor: String? = null,
	) = PlanBuilder.build(canal, caps, servidor, tsFlags = 0, forzarSoftwareLocal = software, motorForzadoLocal = motor)

	// --- Paso 1: hardware ---------------------------------------------------

	"con decoder de hardware se elige hardware y Media3" {
		val p = plan(h264, caja(listOf(avcHw)))
		p.videoPath shouldBe DecodePath.HW
		p.engineId shouldBe PlanBuilder.MEDIA3_ENGINE_ID
		p.reason shouldContain "hw para video/avc"
	}

	// --- Paso 2: software de plataforma -------------------------------------

	"sin hardware pero con software de plataforma, se baja a software" {
		val p = plan(mpeg2, caja(listOf(avcHw, mpeg2Sw)))
		p.videoPath shouldBe DecodePath.SW_PLATFORM
		p.engineId shouldBe PlanBuilder.MEDIA3_ENGINE_ID
		p.reason shouldContain "sin hw"
	}

	// --- Paso 3: libVLC -----------------------------------------------------

	"sin decoder de plataforma se cae a libVLC si está compilado" {
		val p = plan(mpeg2, caja(listOf(avcHw), vlc = true))
		p.videoPath shouldBe DecodePath.SW_VLC
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
	}

	// --- Paso 4: no hay forma -----------------------------------------------

	"sin decoder y sin libVLC, el canal se marca como no reproducible" {
		val p = plan(mpeg2, caja(listOf(avcHw), vlc = false))
		p.unsupported shouldBe true
		p.reason shouldContain "sin decoder"
	}

	// --- El servidor sugiere, la caja verifica ------------------------------

	"el servidor no puede imponer libVLC donde no está compilado" {
		val p = plan(h264, caja(listOf(avcHw), vlc = false), ServerDeviceSettings(engine = "libvlc"))
		p.engineId shouldBe PlanBuilder.MEDIA3_ENGINE_ID
	}

	"el servidor sí puede imponer libVLC donde existe" {
		val p = plan(h264, caja(listOf(avcHw), vlc = true), ServerDeviceSettings(engine = "libvlc"))
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
		p.reason shouldContain "servidor"
	}

	// --- `enginesAllowed` es un filtro duro ---------------------------------

	"un motor fuera de enginesAllowed no se elige ni como último recurso" {
		val canal = mpeg2.copy(enginesAllowed = setOf("media3"))
		val p = plan(canal, caja(listOf(avcHw), vlc = true))
		// Hay libVLC y sería el único camino, pero el canal lo prohíbe.
		p.unsupported shouldBe true
	}

	"enginesAllowed gana incluso al servidor" {
		val canal = h264.copy(enginesAllowed = setOf("media3"))
		val p = plan(canal, caja(listOf(avcHw), vlc = true), ServerDeviceSettings(engine = "libvlc"))
		p.engineId shouldBe PlanBuilder.MEDIA3_ENGINE_ID
	}

	// --- Forzar software ----------------------------------------------------

	"forzar software desde el menú descarta el hardware" {
		val p = plan(h264, caja(listOf(avcHw, avcSw)), software = true)
		p.videoPath shouldBe DecodePath.SW_PLATFORM
		p.reason shouldContain "software forzado"
	}

	"forzar software sin decoder de plataforma usa libVLC" {
		val p = plan(mpeg2, caja(listOf(mpeg2Hw), vlc = true), software = true)
		p.videoPath shouldBe DecodePath.SW_VLC
	}

	// --- Entrelazado --------------------------------------------------------

	"un canal progresivo no pide desentrelazado" {
		plan(h264, caja(listOf(avcHw))).deinterlace shouldBe DeinterlaceMode.NONE
	}

	"un canal entrelazado asume que el VPU desentrelaza" {
		val p = plan(h264.copy(interlaced = true), caja(listOf(avcHw)))
		p.deinterlace shouldBe DeinterlaceMode.VPU
	}

	"si el servidor dice que el VPU no desentrelaza, se documenta y no se finge" {
		val p = plan(
			h264.copy(interlaced = true),
			caja(listOf(avcHw)),
			ServerDeviceSettings(vpuDeinterlaces = false),
		)
		p.deinterlace shouldBe DeinterlaceMode.NONE
		p.reason shouldContain "no desentrelaza"
	}

	"con libVLC el desentrelazado va por software" {
		val p = plan(mpeg2.copy(interlaced = true), caja(listOf(avcHw), vlc = true))
		p.deinterlace shouldBe DeinterlaceMode.SOFTWARE
	}

	// --- El offset ----------------------------------------------------------

	"el offset del canal pisa al de fábrica, y el del servidor a los dos" {
		plan(h264, caja(listOf(avcHw))).targetOffsetMs shouldBe 8000L
		plan(h264.copy(targetOffsetMs = 12000), caja(listOf(avcHw))).targetOffsetMs shouldBe 12000L
		plan(
			h264.copy(targetOffsetMs = 12000),
			caja(listOf(avcHw)),
			ServerDeviceSettings(targetOffsetMs = 20000),
		).targetOffsetMs shouldBe 20000L
	}

	// --- El códec declarado es una pista, no la verdad ----------------------

	"un canal sin códec declarado empieza por hardware" {
		val p = plan(h264.copy(videoCodec = null), caja(listOf(avcHw)))
		p.videoPath shouldBe DecodePath.HW
		p.reason shouldContain "sin declarar"
	}

	"si el stream desmiente al config, el plan se rehace y manda el stream" {
		val caps = caja(listOf(avcHw), vlc = true)   // sin MPEG-2 de ninguna clase
		val original = plan(h264, caps)
		original.videoPath shouldBe DecodePath.HW

		val revisado = PlanBuilder.revisar(
			plan = original,
			channel = h264,                 // el config dice h264
			mimeObservado = "video/mpeg2",  // el stream dice otra cosa
			caps = caps,
			servidor = null,
			tsFlags = 0,
			forzarSoftwareLocal = false,
			motorForzadoLocal = null,
		)
		revisado!!.videoPath shouldBe DecodePath.SW_VLC
	}

	"si el stream confirma el config, no se rehace nada" {
		val caps = caja(listOf(avcHw))
		val original = plan(h264, caps)
		PlanBuilder.revisar(
			original, h264, "video/avc", caps, null, 0, false, null,
		) shouldBe null
	}

	// --- Traducción de códecs ----------------------------------------------

	"los nombres de códec del config se traducen a MIME" {
		PlanBuilder.mimeDe("h264") shouldBe "video/avc"
		PlanBuilder.mimeDe("mpeg2video") shouldBe "video/mpeg2"
		PlanBuilder.mimeDe("hevc") shouldBe "video/hevc"
		PlanBuilder.mimeDe("") shouldBe null
		PlanBuilder.mimeDe("algoQueNoConozco") shouldBe null
	}
})
