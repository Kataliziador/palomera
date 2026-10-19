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

	// --- Nivel 2: SD entrelazado sin VPU que desentrelace -------------------

	"con el VPU que no desentrelaza, el SD entrelazado va por libVLC aunque haya hardware" {
		// Es la única rama que descarta hardware **teniendo** hardware, y sin
		// ella M6 solo servía con el decoder vetado a mano desde el menú: o
		// sea, nunca en casa de un abonado. Lo gobierna `vpuDeinterlaces`, que
		// es conocimiento nuestro por modelo.
		val p = plan(
			mpeg2.copy(interlaced = true),
			caja(listOf(mpeg2Hw), vlc = true),
			servidor = ServerDeviceSettings(vpuDeinterlaces = false),
		)
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
		p.videoPath shouldBe DecodePath.SW_VLC
		p.deinterlace shouldBe DeinterlaceMode.SOFTWARE
	}

	"si el VPU sí desentrelaza, el mismo canal se queda en hardware" {
		val p = plan(
			mpeg2.copy(interlaced = true),
			caja(listOf(mpeg2Hw), vlc = true),
			servidor = ServerDeviceSettings(vpuDeinterlaces = true),
		)
		p.videoPath shouldBe DecodePath.HW
		p.deinterlace shouldBe DeinterlaceMode.VPU
	}

	"un canal progresivo no se desvía a libVLC aunque el VPU no desentrelace" {
		// La regla es para entrelazado. Mandar lo progresivo a software sería
		// pagar CPU por nada y perder el camino de hardware que funciona.
		val p = plan(
			mpeg2,
			caja(listOf(mpeg2Hw), vlc = true),
			servidor = ServerDeviceSettings(vpuDeinterlaces = false),
		)
		p.videoPath shouldBe DecodePath.HW
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

	// --- El veto de decoders por modelo ------------------------------------
	//
	// Es la salida del pendiente 63: el defecto de la Skyworth no es que su VPU
	// no desentrelace —sí lo hace en H.264, medido en M5— sino que su decoder
	// de MPEG-2 entrega la mitad de la cadencia. Vetarlo por nombre deja ese
	// códec sin decoder de plataforma y la cascada cae al respaldo **sin tocar
	// el H.264**, que es lo que un `vpuDeinterlaces: false` por modelo no sabe
	// hacer: se llevaría a software los tres canales 1080i de la caja.

	"vetar el decoder de un códec desde el servidor lleva ese canal a libVLC" {
		val caja = caja(listOf(avcHw, mpeg2Hw), vlc = true)
			.excluyendo(setOf("OMX.amlogic.mpeg2.decoder"))
		val p = plan(mpeg2, caja)
		p.videoPath shouldBe DecodePath.SW_VLC
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
		p.reason shouldContain "sin decoder de plataforma"
	}

	"y no toca a los demás códecs de la misma caja" {
		val caja = caja(listOf(avcHw, mpeg2Hw), vlc = true)
			.excluyendo(setOf("OMX.amlogic.mpeg2.decoder"))
		val p = plan(h264.copy(interlaced = true), caja)
		p.videoPath shouldBe DecodePath.HW
		p.engineId shouldBe PlanBuilder.MEDIA3_ENGINE_ID
		p.deinterlace shouldBe DeinterlaceMode.VPU
	}

	"un nombre con otras mayúsculas también veta" {
		// El nombre viaja en el `config.json` y lo escribe una persona. Un veto
		// que no casa por una mayúscula no hace nada y no se nota hasta que
		// alguien mira la tele.
		val caja = caja(listOf(mpeg2Hw), vlc = true)
			.excluyendo(setOf("omx.AMLOGIC.mpeg2.DECODER"))
		plan(mpeg2, caja).engineId shouldBe PlanBuilder.VLC_ENGINE_ID
	}

	"un nombre que esa caja no tiene no cambia nada" {
		val caja = caja(listOf(mpeg2Hw)).excluyendo(setOf("OMX.otra.marca.mpeg2"))
		plan(mpeg2, caja).videoPath shouldBe DecodePath.HW
	}

	"si se veta el único decoder y no hay libVLC, el canal queda sin camino" {
		// Y se dice. Es el precio de un veto mal escrito, y tiene que verse en
		// el log en vez de salir como pantalla negra sin explicación.
		val caja = caja(listOf(mpeg2Hw)).excluyendo(setOf("OMX.amlogic.mpeg2.decoder"))
		plan(mpeg2, caja).unsupported shouldBe true
	}

	// --- `enginesAllowed` es un filtro duro ---------------------------------

	"un motor fuera de enginesAllowed no se elige ni como último recurso" {
		val canal = mpeg2.copy(enginesAllowed = setOf("media3"))
		val p = plan(canal, caja(listOf(avcHw), vlc = true))
		// Hay libVLC y sería el único camino, pero el canal lo prohíbe.
		p.unsupported shouldBe true
	}

	"un canal que prohíbe media3 se va a libVLC teniendo hardware" {
		// El pendiente 64: esto elegía Media3, que es justo el motor prohibido.
		// El filtro era duro en un sentido y blando en el otro.
		val canal = h264.copy(enginesAllowed = setOf("libvlc"))
		val p = plan(canal, caja(listOf(avcHw), vlc = true))
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
		p.videoPath shouldBe DecodePath.SW_VLC
	}

	"y si no hay libVLC, se queda sin camino en vez de usar el prohibido" {
		val canal = h264.copy(enginesAllowed = setOf("libvlc"))
		plan(canal, caja(listOf(avcHw), vlc = false)).unsupported shouldBe true
	}

	"prohibir media3 también descarta el software de plataforma" {
		val canal = h264.copy(enginesAllowed = setOf("libvlc"))
		val p = plan(canal, caja(listOf(avcSw), vlc = true))
		p.videoPath shouldBe DecodePath.SW_VLC
	}

	"forzar software desde el menú no salta por encima de enginesAllowed" {
		val canal = h264.copy(enginesAllowed = setOf("libvlc"))
		val p = plan(canal, caja(listOf(avcHw, avcSw), vlc = true), software = true)
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
	}

	"sin códec declarado y con media3 prohibido, el respaldo" {
		// La rama de «se asume hardware» también implicaba Media3.
		val canal = ChannelProfile(id = "c3", name = "X", url = "http://e/3.m3u8", videoCodec = null)
			.copy(enginesAllowed = setOf("libvlc"))
		val p = plan(canal, caja(listOf(avcHw), vlc = true))
		p.engineId shouldBe PlanBuilder.VLC_ENGINE_ID
		p.reason shouldContain "códec sin declarar y media3 no permitido"
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

/**
 * Qué causa se le atribuye a cada fallo.
 *
 * Existe porque el mensaje **dirige a quien lo lee**: decir «señal débil por
 * condiciones climáticas» ante una aserción de Media3 manda a un técnico a
 * revisar una antena que está perfecta. Hasta el 03-oct todos los errores
 * decían eso, y hasta el 19-oct lo seguía diciendo el caso de señal aunque el
 * fallo fuera el servidor apagado. Hoy ese caso dice «Recuperando la señal…»;
 * lo que esta prueba fija es la clasificación, no el texto.
 */
class CausaDeFalloTest : StringSpec({

	// El clasificador vive en el motor, que necesita Android. Aquí se fija la
	// **regla**, que es lo que de verdad hay que no romper: los códigos de
	// entrada/salida y de analizado son señal; el resto, no.
	fun causa(code: Int): CausaDeFallo = when (code) {
		in 2000..2007 -> CausaDeFallo.SENAL     // ERROR_CODE_IO_*
		in 3001..3004 -> CausaDeFallo.SENAL     // ERROR_CODE_PARSING_*
		else -> CausaDeFallo.INTERNO
	}

	"un segmento que no llega es señal" {
		causa(2001) shouldBe CausaDeFallo.SENAL  // IO_NETWORK_CONNECTION_FAILED
		causa(2004) shouldBe CausaDeFallo.SENAL  // IO_BAD_HTTP_STATUS
	}

	"un contenedor roto es señal: eso lo hace la lluvia" {
		causa(3001) shouldBe CausaDeFallo.SENAL  // PARSING_CONTAINER_MALFORMED
	}

	"un fallo del decodificador no es el satélite" {
		causa(4001) shouldBe CausaDeFallo.INTERNO  // DECODER_INIT_FAILED
		causa(4003) shouldBe CausaDeFallo.INTERNO  // DECODING_FAILED
	}

	"una aserción interna de Media3 tampoco" {
		causa(1004) shouldBe CausaDeFallo.INTERNO  // FAILED_RUNTIME_CHECK
	}
})
