package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import mx.kataliziador.palomera.movil.ui.aspectoDeVideo

/**
 * La forma con la que se pinta el vídeo.
 *
 * ## Lo medido el 10-nov en el emulador
 *
 * El reproductor ponía el vídeo en un `SurfaceView` con `fillMaxSize()` y
 * **ninguna corrección de aspecto**, así que un canal 16:9 se estiraba a los
 * 1080×2400 de la pantalla. Se ve en la captura de Las Estrellas: el logo del
 * anuncio sale alargado.
 *
 * ## Y el SAR no es un adorno
 *
 * `CLAUDE.md` §6 lo dice para la caja: **dos de los seis canales de referencia
 * son anamórficos** —`estrellashd-mp4` a 4:3 y `estrellassd-mp2v` a 20:17— y
 * sus píxeles no son cuadrados. Con 720×480 y un SAR de 20/17, la imagen no
 * mide 3:2 sino 1,76:1. Ignorarlo deforma, y es el mismo satélite que ve el
 * teléfono.
 */
class AspectoTest : FunSpec({

	test("con pixeles cuadrados, el aspecto es el de siempre") {
		aspectoDeVideo(1920, 1080, 1f).shouldBeWithinPercentageOf(16.0 / 9.0, 0.1)
		aspectoDeVideo(1280, 720, 1f).shouldBeWithinPercentageOf(16.0 / 9.0, 0.1)
	}

	test("un canal anamorfico se corrige con su SAR") {
		// `estrellassd-mp2v`: 720×480 con SAR 20/17. Sin corregir sale 1,5:1
		// —una cara estrecha—; con el SAR, 1,76:1.
		aspectoDeVideo(720, 480, 20f / 17f).shouldBeWithinPercentageOf(1.7647, 0.1)
		// Y el de 4:3 sobre 720×480.
		aspectoDeVideo(720, 480, 4f / 3f).shouldBeWithinPercentageOf(2.0, 0.1)
	}

	test("sin tamaño todavía, no se inventa una forma") {
		// Antes del primer fotograma el reproductor no sabe el tamaño. Elegir
		// 16:9 «por si acaso» haría saltar la imagen al llegar el primero.
		aspectoDeVideo(0, 0, 1f).shouldBeWithinPercentageOf(0.0, 0.1)
		aspectoDeVideo(1920, 0, 1f).shouldBeWithinPercentageOf(0.0, 0.1)
	}

	test("un SAR absurdo se ignora en vez de deformar") {
		// Un `pixelWidthHeightRatio` de 0 o negativo llega de streams rotos.
		// Usarlo daría una división por cero o una imagen invertida.
		aspectoDeVideo(1920, 1080, 0f).shouldBeWithinPercentageOf(16.0 / 9.0, 0.1)
		aspectoDeVideo(1920, 1080, -2f).shouldBeWithinPercentageOf(16.0 / 9.0, 0.1)
	}
})
