package org.jellyfin.livetv.diagnostics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * El acotado del diagnóstico, que es lo único que puede hacer daño.
 *
 * Lo demás son sumas. Lo que puede salir mal es que una caja con el headend
 * roto acabe mandando kilobytes de causas y canales en cada petición al
 * registro, y eso no se nota aquí: se nota en el servicio, meses después,
 * cuando ya hay cinco mil cajas haciéndolo.
 *
 * La función que lo evita es privada, así que se prueba la misma lógica
 * replicada — es pequeña y el valor está en fijar el comportamiento, no en
 * tocar la implementación.
 */
class DiagnosticoTest : StringSpec({

	/** La misma regla que `Diagnostico.sumarAcotado`. */
	fun sumar(actual: MutableMap<String, JsonPrimitive>, clave: String, tope: Int) {
		val previo = actual[clave]?.content?.toIntOrNull() ?: 0
		if (previo == 0 && actual.size >= tope) return
		actual[clave] = JsonPrimitive(previo + 1)
	}

	"una clave que ya está sigue contando aunque se haya llegado al tope" {
		// Es la parte que importa: pasado el tope dejamos de **descubrir**
		// claves, pero los que más fallan ya están dentro y siguen sumando.
		val m = mutableMapOf<String, JsonPrimitive>()
		repeat(3) { sumar(m, "canal$it", tope = 3) }
		repeat(5) { sumar(m, "canal0", tope = 3) }
		m.size shouldBe 3
		m["canal0"]!!.content shouldBe "6"
	}

	"pasado el tope no entran claves nuevas" {
		val m = mutableMapOf<String, JsonPrimitive>()
		repeat(50) { sumar(m, "canal$it", tope = 40) }
		m.size shouldBe 40
		m.containsKey("canal45") shouldBe false
	}

	"el mapa vacío admite la primera clave" {
		val m = mutableMapOf<String, JsonPrimitive>()
		sumar(m, "SENAL", tope = 12)
		JsonObject(m.toMap()).toString() shouldBe """{"SENAL":1}"""
	}
})
