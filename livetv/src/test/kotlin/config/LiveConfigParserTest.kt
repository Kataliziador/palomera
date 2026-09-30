package org.jellyfin.livetv.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Lo que se prueba aquí no es el camino feliz, que es trivial, sino **lo que
 * pasa cuando el `config.json` viene mal**.
 *
 * Ese archivo lo escribe una persona a mano para ~120 canales. Va a tener
 * erratas, y la diferencia entre un producto usable y uno frágil está en si una
 * errata tira un canal o tira el lineup entero.
 */
class LiveConfigParserTest : StringSpec({

	fun config(cuerpo: String) = """{"version":1,$cuerpo}"""

	val unCanal = """{"id":"c1","name":"Canal 1","url":"http://ejemplo/1.m3u8"}"""

	"interpreta un canal completo" {
		val c = LiveConfigParser.parse(
			config(
				""""channels":[{
					"id":"estrellashd","number":102,"order":3,"name":"Estrellas HD",
					"url":"http://ejemplo/eshd.m3u8","logoUrl":"http://ejemplo/l.png",
					"videoCodec":"h264","audioCodec":"aac","hd":true,
					"audioDefault":"spa","targetOffsetMs":8000,
					"enginesAllowed":["media3","libvlc"]
				}]"""
			)
		)

		val canal = c.channels.single()
		canal.id shouldBe "estrellashd"
		canal.number shouldBe 102
		canal.order shouldBe 3
		canal.hd shouldBe true
		canal.targetOffsetMs shouldBe 8000L
		canal.audioDefault shouldBe "spa"
		canal.enginesAllowed shouldBe setOf("media3", "libvlc")
	}

	"un campo ausente no es lo mismo que un cero" {
		val c = LiveConfigParser.parse(config(""""channels":[$unCanal]"""))
		val canal = c.channels.single()
		canal.number.shouldBeNull()
		canal.targetOffsetMs.shouldBeNull()
		canal.enginesAllowed.shouldBeNull()
	}

	"un canal con errata se descarta, los demás sobreviven" {
		val c = LiveConfigParser.parse(
			config(
				""""channels":[
					{"id":"bueno","name":"Bueno","url":"http://ejemplo/b.m3u8"},
					{"id":"sinUrl","name":"Sin URL"},
					{"name":"Sin id","url":"http://ejemplo/x.m3u8"},
					{"id":"otroBueno","name":"Otro","url":"https://ejemplo/o.m3u8"}
				]"""
			)
		)
		c.channels.map { it.id } shouldContainExactly listOf("bueno", "otroBueno")
	}

	"una URL que no es http se descarta" {
		val c = LiveConfigParser.parse(
			config(
				""""channels":[
					$unCanal,
					{"id":"malo","name":"Malo","url":"file:///etc/passwd"}
				]"""
			)
		)
		c.channels.map { it.id } shouldContainExactly listOf("c1")
	}

	"un id repetido se descarta, se queda el primero" {
		val c = LiveConfigParser.parse(
			config(
				""""channels":[
					{"id":"dup","name":"Primero","url":"http://ejemplo/1.m3u8"},
					{"id":"dup","name":"Segundo","url":"http://ejemplo/2.m3u8"}
				]"""
			)
		)
		c.channels.single().name shouldBe "Primero"
	}

	"un grupo desconocido no tira el canal" {
		val c = LiveConfigParser.parse(
			config(
				""""groups":[{"id":"cine","name":"Cine"}],
				   "channels":[{"id":"c1","name":"C1","url":"http://e/1.m3u8","group":"noExiste"}]"""
			)
		)
		c.channels.single().group.shouldBeNull()
	}

	"los grupos salen ordenados" {
		val c = LiveConfigParser.parse(
			config(
				""""groups":[
					{"id":"c","name":"C","order":3},
					{"id":"a","name":"A","order":1},
					{"id":"b","name":"B","order":2}
				],"channels":[$unCanal]"""
			)
		)
		c.groups.map { it.id } shouldContainExactly listOf("a", "b", "c")
	}

	"sin ningún canal utilizable se rechaza la configuración entera" {
		rechaza {
			LiveConfigParser.parse(config(""""channels":[{"name":"sin id ni url"}]"""))
		}
	}

	"un JSON roto se rechaza con su motivo" {
		rechaza {
			LiveConfigParser.parse("{esto no es json")
		}
	}

	"sin «version» se rechaza" {
		rechaza {
			LiveConfigParser.parse("""{"channels":[$unCanal]}""")
		}
	}

	"una versión más nueva se acepta: el suscriptor no se queda sin televisión" {
		val c = LiveConfigParser.parse("""{"version":99,"channels":[$unCanal]}""")
		c.version shouldBe 99
		c.channels.size shouldBe 1
	}

	"un ajuste por caja sin criterios se descarta" {
		val c = LiveConfigParser.parse(
			config(
				""""channels":[$unCanal],
				   "deviceOverrides":[{"match":{},"tunneling":true}]"""
			)
		)
		c.deviceOverrides.shouldBeEmptyList()
	}

	"la especificidad cuenta los criterios impuestos" {
		DeviceMatch(model = "X").specificity shouldBe 1
		DeviceMatch(manufacturer = "A", model = "X", sdkInt = 30).specificity shouldBe 3
		DeviceMatch().specificity shouldBe 0
	}

	"los campos desconocidos no rompen nada" {
		val c = LiveConfigParser.parse(
			"""{"version":1,"futuro":{"algo":1},
			    "channels":[{"id":"c1","name":"C1","url":"http://e/1.m3u8","loQueSea":true}]}"""
		)
		c.channels.single().id shouldBe "c1"
	}
})

private fun <T> List<T>.shouldBeEmptyList() {
	check(isEmpty()) { "se esperaba una lista vacía, había $size elementos" }
}

/**
 * Comprueba que el bloque rechaza la configuración.
 *
 * No se usa `shouldThrow` de kotest porque viene compilado para JVM 11 y este
 * proyecto fija 1.8 en el `build.gradle.kts` raíz. Cambiar el objetivo global
 * para una prueba sería tocar la compilación «de paso» (CLAUDE.md regla 11).
 */
private fun rechaza(bloque: () -> Unit) {
	val resultado = runCatching(bloque)
	check(resultado.isFailure) { "se esperaba que rechazara la configuración, y la aceptó" }
	val fallo = resultado.exceptionOrNull()
	check(fallo is LiveConfigParser.InvalidConfig) {
		"se esperaba InvalidConfig, llegó ${fallo!!::class.java.simpleName}: ${fallo.message}"
	}
}
