package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import mx.kataliziador.palomera.movil.catalogo.cabeceraDeAutorizacion
import mx.kataliziador.palomera.movil.catalogo.nombreDeAparato

/**
 * Cómo se presenta la app al Jellyfin del ISP.
 *
 * ## Por qué tiene pruebas una cadena de texto
 *
 * Porque **es lo que el operador ve en el panel de abonados al confirmar un
 * alta** (`CLAUDE.md` §8), y porque Jellyfin la parsea: una comilla sin cerrar
 * o un campo de menos y el servidor no asocia la reproducción a ninguna sesión.
 * Falla en silencio y con un 200 de respuesta — la app cree que avisó.
 *
 * Y porque §8 fija el nombre: es **«Palomera»**, no la marca del ISP. Las
 * ranuras de marca visten la app por dentro; quien pregunta «qué cliente es
 * esta sesión» necesita saber que es la nuestra.
 */
class IdentidadTest : FunSpec({

	test("la cabecera lleva los cuatro campos que Jellyfin necesita") {
		val c = cabeceraDeAutorizacion(
			cliente = "Palomera",
			aparato = "Redmi Note 13",
			idDeInstalacion = "a1b2c3",
			version = "0.0.1",
			token = "tok",
		)

		c shouldContain """Client="Palomera""""
		c shouldContain """Device="Redmi Note 13""""
		c shouldContain """DeviceId="a1b2c3""""
		c shouldContain """Version="0.0.1""""
		c shouldContain """Token="tok""""
		c.startsWith("MediaBrowser ") shouldBe true
	}

	test("una comilla en el modelo no rompe la cabecera") {
		// No es hipotético: hay fabricantes que meten comillas y acentos en
		// `Build.MODEL`. Con la cabecera rota, Jellyfin contesta 200 y no
		// asocia nada — el peor fallo posible, porque parece que funcionó.
		val c = cabeceraDeAutorizacion(
			cliente = "Palomera",
			aparato = """Tele "Pro" 55""",
			idDeInstalacion = "x",
			version = "1",
			token = "t",
		)

		c shouldNotContain """"Pro""""
		c shouldContain "Device="
	}

	test("un aparato sin nombre no deja el campo vacio") {
		// Un `Device=""` deja al operador mirando una fila en blanco en su
		// panel, que es justo el dato con el que confirma un alta.
		nombreDeAparato(fabricante = "", modelo = "") shouldBe "Teléfono"
		nombreDeAparato(fabricante = "Xiaomi", modelo = "") shouldBe "Xiaomi"
	}

	test("el modelo no repite el fabricante") {
		// «Xiaomi Xiaomi 2511FPC34G» es lo que sale de concatenar sin mirar.
		nombreDeAparato(fabricante = "Xiaomi", modelo = "Xiaomi 2511FPC34G") shouldBe "Xiaomi 2511FPC34G"
		nombreDeAparato(fabricante = "Xiaomi", modelo = "2511FPC34G") shouldBe "Xiaomi 2511FPC34G"
	}
})
