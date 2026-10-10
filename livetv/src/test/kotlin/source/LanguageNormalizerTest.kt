package org.jellyfin.livetv.source

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * El idioma, que es la **única** clave de selección de audio que sirve.
 *
 * Flussonic renumera los PIDs al remuxar y entrega todos los canales con los
 * mismos, así que la etiqueta de idioma es lo que queda (`CLAUDE.md` §4.6). Y
 * en algunos satélites esa etiqueta miente, de modo que poder cambiar de pista
 * no es un lujo: es el único remedio del abonado.
 */
class LanguageNormalizerTest : StringSpec({

	"los cuatro códigos de español del lineup son el mismo idioma" {
		// `esl` no es un código válido y es justo el que usan los dos Estrellas.
		for (crudo in listOf("spa", "esp", "esl", "es", "SPA", " esl ", "Spanish")) {
			LanguageNormalizer.normalize(crudo) shouldBe "spa"
		}
	}

	"el inglés también se normaliza" {
		LanguageNormalizer.normalize("en") shouldBe "eng"
	}

	"un idioma que no conocemos pasa tal cual, no se descarta" {
		// Un lineup real trae portugués o francés, y «no lo conozco» no puede
		// significar «no tiene idioma»: el abonado lo vería como una pista sin
		// nombre y sin forma de distinguirla.
		LanguageNormalizer.normalize("por") shouldBe "por"
	}

	"«und» y el vacío no son idiomas" {
		LanguageNormalizer.normalize("und") shouldBe null
		LanguageNormalizer.normalize("") shouldBe null
		LanguageNormalizer.normalize(null) shouldBe null
	}

	// --- rescatar el idioma de un texto (pendiente 58) ----------------------

	"se rescata el código de la descripción que da Flussonic" {
		// Lo que trae el master de `discoveryhd`: NAME="spa a1" / NAME="eng a2".
		LanguageNormalizer.enTexto("spa a1") shouldBe "spa"
		LanguageNormalizer.enTexto("eng a2") shouldBe "eng"
	}

	"y de las formas en que libVLC suele escribirlas" {
		LanguageNormalizer.enTexto("Audio - [spa]") shouldBe "spa"
		LanguageNormalizer.enTexto("Pista 2 (eng)") shouldBe "eng"
		LanguageNormalizer.enTexto("esl/stereo") shouldBe "spa"
	}

	"lo que no parece un código no inventa un idioma" {
		// Es la mitad que importa: `normalize` devuelve tal cual lo que no
		// conoce, así que pasar el texto entero convertiría «audio» en idioma.
		LanguageNormalizer.enTexto("Track 1") shouldBe null
		LanguageNormalizer.enTexto("Disable") shouldBe null
		LanguageNormalizer.enTexto("a1") shouldBe null
		LanguageNormalizer.enTexto("5.1 surround") shouldBe null
		LanguageNormalizer.enTexto("") shouldBe null
		LanguageNormalizer.enTexto(null) shouldBe null
	}

	"una palabra de dos o tres letras que no es idioma pasa igual, y se acepta" {
		// Límite conocido de la regla: `xyz` no es un idioma y aquí sale como
		// tal. Se acepta porque el daño es una etiqueta rara en la barra,
		// mientras que estrechar más la regla dejaría fuera idiomas de verdad
		// que no estén en las dos listas.
		LanguageNormalizer.enTexto("xyz pista") shouldBe "xyz"
	}
})
