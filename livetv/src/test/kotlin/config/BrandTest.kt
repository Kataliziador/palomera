package org.jellyfin.livetv.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * La marca es lo primero que ve un abonado, y la escribe un ISP en un JSON.
 *
 * Estas pruebas existen porque el fallo aquí **no se parece a un fallo**: un
 * acento mal escrito o ilegible no rompe nada, solo deja la sección con una
 * identidad que nadie eligió, o peor, con un texto que no se lee a tres metros.
 */
class BrandTest : StringSpec({

	"una marca completa se acepta tal cual" {
		val m = BrandParser.parse("GarraTV", "#E8A33D", "http://s/logo.png")
		m.displayName shouldBe "GarraTV"
		m.accent shouldBe 0xFFE8A33D.toInt()
		m.logoUrl shouldBe "http://s/logo.png"
		m.origen shouldBe "servidor"
	}

	"sin nada, la marca neutra" {
		BrandParser.parse(null, null, null) shouldBe Brand.NEUTRA
	}

	"un nombre en blanco no es un nombre" {
		BrandParser.parse("   ", null, null) shouldBe Brand.NEUTRA
	}

	// --- el color: dos motivos de rechazo, y no son el mismo --------------

	"un color mal escrito cae al neutro sin tirar el resto de la marca" {
		val m = BrandParser.parse("GarraTV", "azul", null)
		m.displayName shouldBe "GarraTV"
		m.accent shouldBe ACENTO_NEUTRO
		m.origen shouldBe "parcial"
	}

	"se acepta con y sin almohadilla" {
		BrandParser.colorValidado("#E8A33D") shouldBe 0xFFE8A33D.toInt()
		BrandParser.colorValidado("E8A33D") shouldBe 0xFFE8A33D.toInt()
	}

	"longitudes raras se rechazan" {
		BrandParser.colorValidado("#FFF") shouldBe null
		BrandParser.colorValidado("#E8A33DFF") shouldBe null
	}

	"un color ilegible sobre el fondo de la sección se rechaza" {
		// Azul oscuro de marca: perfectamente válido como hex y perfectamente
		// invisible a tres metros sobre el fondo de la sección.
		BrandParser.colorValidado("#101840") shouldBe null
	}

	"el contraste se mide contra el fondo real, no contra negro puro" {
		// El fondo de la sección es `#101010`, no `#000000`. Un gris que pasa
		// justo contra negro puro —3,05— vale 2,76 sobre el fondo de verdad, y
		// ahí es donde el abonado tiene que leerlo.
		(BrandParser.contraste(0xFF5A5A5A.toInt(), 0xFF000000.toInt()) > 3.0) shouldBe true
		BrandParser.colorValidado("#5A5A5A") shouldBe null
	}

	"el umbral nuevo no se come los colores de marca que sí se leen" {
		// 3,01 contra el fondo real: justo por encima, y se acepta. Subir el
		// umbral «por si acaso» dejaría a los ISPs en gris.
		BrandParser.colorValidado("#2E5FA8") shouldNotBe null
	}

	"un color de marca legible se acepta aunque no sea blanco" {
		// Lo contrario también importa: exigir demasiado contraste descartaría
		// colores de marca legítimos y todos los ISPs acabarían en gris.
		BrandParser.colorValidado("#E8A33D") shouldNotBe null
		BrandParser.colorValidado("#C62828") shouldNotBe null
	}

	"el neutro pasa su propia validación sobre el fondo de la sección" {
		// Si el respaldo no fuera legible, no sería un respaldo.
		(BrandParser.contraste(ACENTO_NEUTRO, 0xFF101010.toInt()) > 3.0) shouldBe true
	}

	"el contraste es simétrico y el de un color consigo mismo es 1" {
		val n = 0xFF808080.toInt()
		BrandParser.contraste(n, n) shouldBe 1.0
		BrandParser.contraste(0xFFFFFFFF.toInt(), 0xFF000000.toInt()) shouldBe
			BrandParser.contraste(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
	}

	// --- desde el config.json --------------------------------------------

	"la marca llega desde el documento" {
		val c = LiveConfigParser.parse("""
			{ "version":1,
			  "brand": { "displayName":"RedTV", "accent":"#E8A33D" },
			  "channels":[{"id":"c1","name":"Uno","url":"http://e/1.m3u8"}] }
		""".trimIndent())
		c.brand.displayName shouldBe "RedTV"
		c.brand.origen shouldBe "servidor"
	}

	"un documento sin marca trae la neutra, no nula" {
		val c = LiveConfigParser.parse("""
			{ "version":1, "channels":[{"id":"c1","name":"Uno","url":"http://e/1.m3u8"}] }
		""".trimIndent())
		c.brand shouldBe Brand.NEUTRA
	}
})
