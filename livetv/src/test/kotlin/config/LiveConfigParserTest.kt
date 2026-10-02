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

/**
 * Los campos añadidos el 02-oct: aviso del operador, canal por defecto, alias,
 * no disponible, clasificación y fuente de respaldo.
 *
 * Se prueban porque todos son **opcionales**, y un campo opcional mal
 * interpretado no se ve: el documento se acepta igual y la función sencillamente
 * no ocurre.
 */
class LiveConfigAmpliadoTest : StringSpec({

	fun doc(raiz: String = "", canal: String = "") = """
		{
		  "version": 1
		  $raiz,
		  "channels": [
		    { "id":"c1", "name":"Uno", "url":"http://e/1.m3u8" $canal }
		  ]
		}
	""".trimIndent()

	// --- aviso del operador ---------------------------------------------

	"el aviso se interpreta con su caducidad en ISO" {
		val c = LiveConfigParser.parse(
			doc(raiz = """, "message": { "text":"Mantenimiento", "until":"2026-10-08T04:00:00Z" }""")
		)
		c.message!!.text shouldBe "Mantenimiento"
		// Calculado, no escrito a mano: un epoch a ojo se equivoca en horas y la
		// prueba acaba comprobando mi aritmética en vez del parser.
		c.message!!.untilEpochMs shouldBe java.time.Instant.parse("2026-10-08T04:00:00Z").toEpochMilli()
	}

	"un aviso sin caducidad está vigente siempre" {
		val c = LiveConfigParser.parse(doc(raiz = """, "message": { "text":"Hola" }"""))
		c.message!!.vigente(Long.MAX_VALUE) shouldBe true
	}

	"un aviso caducado deja de estar vigente" {
		val c = LiveConfigParser.parse(
			doc(raiz = """, "message": { "text":"Viejo", "until":"2020-01-01T00:00:00Z" }""")
		)
		c.message!!.vigente(System.currentTimeMillis()) shouldBe false
	}

	"una fecha ilegible no tira el aviso: perderlo es peor que enseñarlo de más" {
		val c = LiveConfigParser.parse(
			doc(raiz = """, "message": { "text":"Aviso", "until":"el martes" }""")
		)
		c.message!!.text shouldBe "Aviso"
		c.message!!.untilEpochMs shouldBe null
	}

	"un aviso sin texto no es un aviso" {
		LiveConfigParser.parse(doc(raiz = """, "message": { "until":"2026-10-08T04:00:00Z" }""")).message shouldBe null
	}

	// --- canal por defecto ----------------------------------------------

	"el canal por defecto se acepta si existe" {
		LiveConfigParser.parse(doc(raiz = """, "defaultChannel":"c1"""")).defaultChannel shouldBe "c1"
	}

	"un canal por defecto que no existe se ignora, no rompe" {
		LiveConfigParser.parse(doc(raiz = """, "defaultChannel":"fantasma"""")).defaultChannel shouldBe null
	}

	// --- campos de canal ------------------------------------------------

	"los alias se interpretan y los vacíos se descartan" {
		val c = LiveConfigParser.parse(doc(canal = """, "aliases":["canal 2","  ","las estrellas"]"""))
		c.channels[0].aliases shouldBe listOf("canal 2", "las estrellas")
	}

	"sin alias la lista queda vacía, no nula" {
		LiveConfigParser.parse(doc()).channels[0].aliases shouldBe emptyList()
	}

	"un canal no disponible conserva su motivo" {
		val c = LiveConfigParser.parse(
			doc(canal = """, "unavailable":true, "unavailableMessage":"Mantenimiento del headend"""")
		)
		c.channels[0].unavailable shouldBe true
		c.channels[0].unavailableMessage shouldBe "Mantenimiento del headend"
	}

	"por defecto un canal está disponible" {
		LiveConfigParser.parse(doc()).channels[0].unavailable shouldBe false
	}

	"clasificación y fuente de respaldo se interpretan aunque no hagan nada" {
		val c = LiveConfigParser.parse(doc(canal = """, "rating":"B15", "backupUrl":"http://e/1b.m3u8""""))
		c.channels[0].rating shouldBe "B15"
		c.channels[0].backupUrl shouldBe "http://e/1b.m3u8"
	}
})
