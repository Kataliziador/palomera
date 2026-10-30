package org.jellyfin.livetv.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Lo que se prueba aquí es **que «no hay» y «no se supo» no se confundan**.
 *
 * Son las dos respuestas que llevan al abonado a sitios distintos: la primera
 * a teclear su código, la segunda a volver a intentarlo o a escribir la
 * dirección que ya tiene. Confundirlas le dice «tu proveedor no existe» a
 * alguien cuya caja simplemente no llega a internet.
 */
class ProveedorParserTest : StringSpec({

	"una respuesta con proveedor se interpreta entera" {
		val r = ProveedorParser.parse(
			"""
			{
			  "proveedor": {
			    "id": "redtv",
			    "nombre": "RedTV",
			    "serverUrl": "https://tv.redtv.mx:8096"
			  },
			  "brand": { "displayName": "RedTV", "accent": "#E8A33D" }
			}
			""".trimIndent()
		)

		// `shouldBeInstanceOf` no se puede usar: es inline y viene compilada
		// para JVM 11, y este módulo compila las pruebas a 1.8.
		(r is RespuestaProveedor.Encontrado) shouldBe true
		val encontrado = r as RespuestaProveedor.Encontrado
		encontrado.proveedor.id shouldBe "redtv"
		encontrado.proveedor.nombre shouldBe "RedTV"
		encontrado.proveedor.serverUrl shouldBe "https://tv.redtv.mx:8096"
		encontrado.proveedor.brand?.displayName shouldBe "RedTV"
	}

	"sin marca sigue siendo un proveedor: la marca la puede poner el site" {
		val r = ProveedorParser.parse(
			"""{"proveedor":{"id":"a","nombre":"A","serverUrl":"http://a.mx:8096"}}"""
		)
		(r as RespuestaProveedor.Encontrado).proveedor.brand shouldBe null
	}

	"«proveedor: null» es «no te conozco», no un error" {
		ProveedorParser.parse("""{"proveedor":null}""") shouldBe RespuestaProveedor.Ninguno
	}

	"un proveedor sin dirección no es medio proveedor: no se ofrece" {
		ProveedorParser.parse(
			"""{"proveedor":{"id":"a","nombre":"A"}}"""
		) shouldBe RespuestaProveedor.Ninguno
	}

	"una dirección que no es http ni https se descarta" {
		// Enseñaría el nombre de un proveedor que no lleva a ninguna parte, y
		// el abonado no tendría forma de saber que el dato es nuestro.
		for (mala in listOf("ftp://a.mx", "tv.redtv.mx:8096", "javascript:alert(1)", "")) {
			ProveedorParser.parse(
				"""{"proveedor":{"id":"a","nombre":"A","serverUrl":"$mala"}}"""
			) shouldBe RespuestaProveedor.Ninguno
		}
	}

	"un cuerpo que no es JSON es «no se supo», nunca «no hay»" {
		// La diferencia importa: una página de error de un portal cautivo
		// llega aquí como basura, y tratarla como «no existe tu proveedor»
		// mandaría al abonado a buscar un código que no necesita.
		ProveedorParser.parse("<html>error</html>") shouldBe RespuestaProveedor.NoSeSupo
		ProveedorParser.parse("") shouldBe RespuestaProveedor.NoSeSupo
	}

	"lo que no se entiende del cuerpo se ignora, no tumba la respuesta" {
		// Es lo que permite que un registro nuevo hable con una APK vieja.
		val r = ProveedorParser.parse(
			"""
			{
			  "proveedor": {"id":"a","nombre":"A","serverUrl":"http://a.mx:8096","futuro":42},
			  "loQueVenga": {"x": 1}
			}
			""".trimIndent()
		)
		(r as RespuestaProveedor.Encontrado).proveedor.id shouldBe "a"
	}
})
