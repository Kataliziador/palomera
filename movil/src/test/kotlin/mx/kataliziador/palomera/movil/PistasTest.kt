package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.ui.Pista
import mx.kataliziador.palomera.movil.ui.nombreDePista

/**
 * Cómo se le nombran al abonado las pistas de audio y de subtítulos.
 *
 * ## Por qué esto no es un lujo
 *
 * `CLAUDE.md` §6 lo dice sin rodeos: **en algunos satélites la etiqueta de
 * idioma miente** —una pista marcada `spa` trae inglés—, y si la app sólo
 * expone una pista el abonado no tiene salida. «Poder cambiar de audio no es
 * un lujo del canal de cine: es el único remedio cuando el headend etiqueta
 * mal.»
 *
 * Hasta el 11-nov el reproductor del teléfono **no dejaba cambiarla**.
 *
 * ## Y el nombre tiene que servir para elegir
 *
 * Una lista que diga «Pista 1 / Pista 2 / Pista 3» no ayuda a nadie. Lo que
 * distingue es el idioma, y detrás el número de canales, que es lo que separa
 * un estéreo de un 5.1 del mismo idioma.
 */
class PistasTest : FunSpec({

	test("el idioma se dice en español, no en codigo") {
		// Usa el `LanguageNormalizer` del motor, que ya sabe que `spa`, `esp`,
		// `esl` y `es` son lo mismo (§4.6).
		nombreDePista(Pista("1", "spa", canales = 2, etiqueta = null)) shouldBe "Español"
		nombreDePista(Pista("2", "eng", canales = 2, etiqueta = null)) shouldBe "Inglés"
		nombreDePista(Pista("3", "esl", canales = 2, etiqueta = null)) shouldBe "Español"
	}

	test("con el mismo idioma, los canales desempatan") {
		// Es lo que separa el estéreo del 5.1 en una película que trae los dos
		// en español. Sin esto, dos filas idénticas.
		nombreDePista(Pista("1", "spa", canales = 6, etiqueta = null)) shouldBe "Español · 5.1"
		nombreDePista(Pista("2", "spa", canales = 1, etiqueta = null)) shouldBe "Español · Mono"
	}

	test("si el stream trae un nombre propio, manda") {
		// Algunos canales rotulan «Comentarios» o «Audio descripción», y eso
		// dice más que el idioma.
		nombreDePista(Pista("1", "spa", canales = 2, etiqueta = "Audio descripción")) shouldBe
			"Audio descripción"
	}

	test("pero una etiqueta que sólo repite el idioma NO manda") {
		// **Medido el 11-nov en Las Estrellas**: Flussonic rotula las pistas
		// «spa a1» y «eng a2», que son nombres de pista técnicos, no nombres
		// para una persona. Dándoles preferencia, la lista decía «spa a1» y
		// «eng a2» en vez de «Español» e «Inglés» — lo mismo, peor dicho.
		//
		// Una etiqueta sólo aporta si dice algo que el idioma no diga.
		nombreDePista(Pista("1", "spa", canales = 2, etiqueta = "spa a1")) shouldBe "Español"
		nombreDePista(Pista("2", "eng", canales = 2, etiqueta = "eng a2")) shouldBe "Inglés"
		nombreDePista(Pista("3", "spa", canales = 2, etiqueta = "Español")) shouldBe "Español"
	}

	test("una pista sin idioma no se queda sin nombre") {
		// **Es el caso que importa**: §6 dice que la etiqueta puede faltar o
		// mentir. Una fila en blanco no se puede elegir, así que algo tiene
		// que poner — y «Pista 2» al menos se distingue de «Pista 1».
		nombreDePista(Pista("2", null, canales = 2, etiqueta = null)) shouldBe "Pista 2"
		nombreDePista(Pista("x", "", canales = 2, etiqueta = null)) shouldBe "Pista x"
	}

	test("un idioma que no conocemos se enseña tal cual") {
		// Menos malo que esconderlo: el abonado puede probar.
		nombreDePista(Pista("1", "cat", canales = 2, etiqueta = null)) shouldBe "CAT"
	}
})
