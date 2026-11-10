package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mx.kataliziador.palomera.movil.catalogo.Forma
import mx.kataliziador.palomera.movil.catalogo.leerTitulos
import mx.kataliziador.palomera.movil.catalogo.urlDeCartel

/**
 * De dónde salen los carteles.
 *
 * ## Lo medido el 10-nov contra el site de los probadores
 *
 * - **300 de 300 títulos tienen cartel**, así que esto no es un adorno para
 *   algunos: es lo que se ve en casi toda la pantalla;
 * - el aspecto que declara el servidor es **0,667**, o sea 2:3, el mismo que
 *   ya usaba la rejilla con bloques de color;
 * - **las imágenes se sirven sin token** (`http 200`, `image/jpeg`), así que
 *   la URL no lleva credenciales y puede cachearse sin cuidado.
 *
 * ## Por qué la etiqueta no es opcional
 *
 * Sin `tag` la URL de un título nunca cambia, así que el día que el ISP
 * corrige un cartel equivocado **el teléfono sigue enseñando el viejo** hasta
 * que alguien borre la caché. Con la etiqueta dentro, cambiar la imagen cambia
 * la URL y se descarga sola.
 */
class CartelTest : FunSpec({

	val base = "http://192.168.1.10:8096"

	test("la url lleva el tamaño pedido y la etiqueta") {
		val url = urlDeCartel(base, "abc123", "tag99", Forma.CARTEL, 336)

		url shouldContain "/Items/abc123/Images/Primary"
		url shouldContain "maxHeight=336"
		url shouldContain "tag=tag99"
	}

	test("sin etiqueta no hay cartel que pedir") {
		// Devolver una URL igualmente daría un 404 por cada tarjeta de la
		// rejilla: 60 peticiones perdidas al servidor del ISP por pantalla.
		urlDeCartel(base, "abc123", null, Forma.CARTEL, 336) shouldBe null
		urlDeCartel(base, "abc123", "", Forma.CARTEL, 336) shouldBe null
	}

	test("el fondo apaisado se pide por ancho, no por alto") {
		// Es la diferencia entre bajar una imagen de 300 px de ancho estirada
		// a toda la pantalla y una que cabe. Pedir mal el eje se ve borroso y
		// no da ningún error.
		val url = urlDeCartel(base, "abc", "t", Forma.FONDO, 780)

		url shouldContain "/Images/Backdrop"
		url shouldContain "maxWidth=780"
	}

	test("la barra final del servidor no duplica la del camino") {
		urlDeCartel("$base/", "abc", "t", Forma.CARTEL, 100) shouldContain "$base/Items/abc"
	}

	test("un titulo trae su etiqueta de cartel si el servidor la manda") {
		val json = """
			{"Items":[
			  {"Id":"a","Name":"Uno","Type":"Movie","ImageTags":{"Primary":"t1"}},
			  {"Id":"b","Name":"Dos","Type":"Movie"}
			]}
		""".trimIndent()

		val t = leerTitulos(json)

		t[0].cartel shouldBe "t1"
		// Y uno sin imagen no se descarta: se enseña con su bloque de color.
		// Perder un título del catálogo por no tener cartel sería
		// desproporcionado, que es la misma regla de siempre.
		t[1].cartel shouldBe null
		t.size shouldBe 2
	}
})
