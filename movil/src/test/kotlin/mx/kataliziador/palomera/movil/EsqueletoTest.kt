package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import org.jellyfin.livetv.capabilities.DecoderInfo

/**
 * La prueba de que el cableado está hecho: que este módulo ve `:livetv` y que
 * `kotest` corre aquí. Cuando la fase 1 traiga lógica propia, esto se borra.
 */
class EsqueletoTest : StringSpec({
	"el modulo movil ve el motor de canales" {
		val caps = DeviceCapabilities.paraPruebas(
			decoders = listOf(
				DecoderInfo(
					name = "c2.android.avc.decoder",
					mime = "video/avc",
					hardware = false,
					maxInstances = 1,
				),
			),
			model = "TelefonoDePrueba",
		)

		caps.hasAnyFor("video/avc") shouldBe true
		caps.hasHardwareFor("video/avc") shouldBe false
		// Un teléfono sin MPEG-2 es el caso de R1, y la cascada ya sabe leerlo.
		caps.decoderFor("video/mpeg2") shouldBe null
	}
})
