package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.Episodio
import mx.kataliziador.palomera.movil.catalogo.etiquetaDeEpisodio
import mx.kataliziador.palomera.movil.catalogo.leerEpisodios
import mx.kataliziador.palomera.movil.catalogo.rotulo

/**
 * Las series, y el fallo que arreglan.
 *
 * ## Lo medido el 10-nov
 *
 * Pulsar «Reproducir» en una serie llamaba a `PlaybackInfo` con el id de la
 * **serie**, y el servidor contestaba literalmente `Error processing request`.
 * Una serie no es un vídeo: no hay nada que reproducir en ella, sólo en sus
 * episodios.
 *
 * Lo que sí hay es `/Shows/NextUp`, que para esa misma serie devolvió
 * «Episodio 1 | T1:E1». Así que el botón no tiene que desaparecer — tiene que
 * saber a qué episodio lleva, y decirlo antes de que lo pulsen.
 */
class SerieTest : FunSpec({

	test("el boton dice a que episodio lleva") {
		// Decir sólo «Reproducir» en una serie de ocho temporadas es pedirle al
		// abonado que pulse para averiguar dónde cae.
		etiquetaDeEpisodio(temporada = 1, numero = 1, empezado = false) shouldBe "Reproducir T1:E1"
		etiquetaDeEpisodio(temporada = 2, numero = 5, empezado = true) shouldBe "Continuar T2:E5"
	}

	test("sin numeracion no se inventa una") {
		// Hay episodios sueltos —especiales, pilotos— sin temporada. Enseñar
		// «T0:E0» sería peor que no enseñar nada.
		etiquetaDeEpisodio(temporada = null, numero = null, empezado = false) shouldBe "Reproducir"
		etiquetaDeEpisodio(temporada = null, numero = 3, empezado = false) shouldBe "Reproducir E3"
	}

	test("los episodios se leen con su temporada y su numero") {
		val json = """
			{"Items":[
			  {"Id":"e1","Name":"Episodio 1","ParentIndexNumber":1,"IndexNumber":1,
			   "RunTimeTicks":13200000000,"UserData":{"PlayedPercentage":0}},
			  {"Id":"e2","Name":"Episodio 2","ParentIndexNumber":1,"IndexNumber":2,
			   "RunTimeTicks":13200000000,"UserData":{"PlayedPercentage":64.0}}
			]}
		""".trimIndent()

		val eps = leerEpisodios(json)

		eps.size shouldBe 2
		eps[0] shouldBe Episodio("e1", "Episodio 1", 1, 1, 22, 0, null)
		eps[1].porcentaje shouldBe 64
	}

	test("un episodio sin id ni nombre se descarta, no tira la lista") {
		// Misma regla que para los canales: perder la temporada entera por una
		// errata en un episodio sería desproporcionado.
		val json = """{"Items":[{"Name":"sin id"},{"Id":"e9","Name":"Bueno","IndexNumber":9}]}"""

		leerEpisodios(json).map { it.id } shouldBe listOf("e9")
	}

	test("el rotulo de la fila es corto, porque va al lado del nombre") {
		rotulo(temporada = 1, numero = 4) shouldBe "T1:E4"
		rotulo(temporada = null, numero = 4) shouldBe "E4"
		rotulo(temporada = null, numero = null) shouldBe ""
	}
})
