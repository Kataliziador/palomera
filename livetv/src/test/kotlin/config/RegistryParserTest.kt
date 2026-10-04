package org.jellyfin.livetv.config

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import io.kotest.core.spec.style.StringSpec

/**
 * Lo que se prueba aquí es **qué se descarta**.
 *
 * Una autorización mal interpretada no se nota: la caja entra, que es lo que
 * iba a hacer de todas formas. Lo que se nota meses después es una autorización
 * sin caducidad que nadie puede retirar.
 */
class RegistryParserTest : StringSpec({

	val ahora = java.time.Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
	val dentroDeUnMes = "2026-11-04T12:00:00Z"

	fun parse(cuerpo: String) = RegistryParser.parse("redtv.ispjelly.net", cuerpo, ahora)

	/** Se descarta = lanza `InvalidResponse`, no otra cosa y no nada. */
	fun descartada(bloque: () -> Unit) {
		val e = runCatching(bloque).exceptionOrNull()
		(e is RegistryParser.InvalidResponse) shouldBe true
	}

	"una respuesta autorizada completa se interpreta entera" {
		val a = parse(
			"""
			{
			  "authorized": true,
			  "until": "$dentroDeUnMes",
			  "site": { "id": "redtv", "channelsUrl": "http://redtv.ispjelly.net:8097/config.json" },
			  "brand": { "displayName": "RedTV", "accent": "#E8A33D" }
			}
			""".trimIndent()
		)
		a.authorized shouldBe true
		a.untilEpochMs shouldBe java.time.Instant.parse(dentroDeUnMes).toEpochMilli()
		a.channelsUrl shouldBe "http://redtv.ispjelly.net:8097/config.json"
		a.brand!!.displayName shouldBe "RedTV"
		a.vigente(ahora) shouldBe true
	}

	"sin «authorized» se descarta la respuesta entera" {
		descartada { parse("""{"until":"$dentroDeUnMes"}""") }
	}

	"autorizada sin «until» se descarta: una autorización eterna no se guarda" {
		descartada { parse("""{"authorized":true}""") }
	}

	"autorizada con «until» ilegible también se descarta" {
		descartada {
			parse("""{"authorized":true,"until":"el martes"}""")
		}
	}

	"la caducidad se acepta en milisegundos epoch" {
		val ms = java.time.Instant.parse(dentroDeUnMes).toEpochMilli()
		parse("""{"authorized":true,"until":$ms}""").untilEpochMs shouldBe ms
	}

	"una caducidad en el pasado se interpreta, pero no está vigente" {
		val a = parse("""{"authorized":true,"until":"2026-09-01T00:00:00Z"}""")
		a.authorized shouldBe true
		a.vigente(ahora) shouldBe false
	}

	"denegar no necesita «until»" {
		val a = parse("""{"authorized":false,"message":"No está dado de alta."}""")
		a.authorized shouldBe false
		a.untilEpochMs.shouldBeNull()
		a.message shouldBe "No está dado de alta."
	}

	"un channelsUrl que no es http ni https se ignora, el resto vale" {
		val a = parse(
			"""{"authorized":true,"until":"$dentroDeUnMes","site":{"channelsUrl":"ftp://x/y"}}"""
		)
		a.authorized shouldBe true
		a.channelsUrl.shouldBeNull()
	}

	"sin «brand» la marca queda nula, que no es lo mismo que la neutra" {
		// Nula = el registro no opina y manda la del site. Neutra = se eligió
		// una marca y resultó ser la de fábrica.
		parse("""{"authorized":true,"until":"$dentroDeUnMes"}""").brand.shouldBeNull()
	}

	"los campos desconocidos se ignoran en silencio" {
		val a = parse(
			"""{"authorized":true,"until":"$dentroDeUnMes","loQueVenga":{"x":1},"otro":[1,2]}"""
		)
		a.authorized shouldBe true
	}

	"la respuesta real del registro en producción se interpreta entera" {
		// Capturada el 2026-10-04 contra https://authjelly.satmx.net con el
		// host del Jellyfin de pruebas. Está aquí tal cual porque una prueba
		// escrita a mano comprueba lo que yo creo que manda el registro; ésta
		// comprueba lo que manda.
		val a = parse(
			"""{"authorized":true,"until":"2026-10-25T20:31:18Z","site":{"id":"pruebas",""" +
				""""channelsUrl":"http://192.168.20.249:8097/config.json"},"brand":{"displayName":"pruebas",""" +
				""""accent":"#E8A33D","logoUrl":"https://authjelly.satmx.net/marca/f7147a05bdda6f00.png"}}"""
		)
		a.authorized shouldBe true
		a.channelsUrl shouldBe "http://192.168.20.249:8097/config.json"
		a.brand!!.displayName shouldBe "pruebas"
		a.brand!!.origen shouldBe "servidor"
		a.vigente(ahora) shouldBe true
	}

	"lo que no es JSON se descarta con su motivo" {
		descartada { parse("<html>502</html>") }
	}
})
