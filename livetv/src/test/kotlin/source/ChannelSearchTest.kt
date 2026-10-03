package org.jellyfin.livetv.source

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * La búsqueda es la pieza que decide si un lineup de 120 canales es usable.
 *
 * Lo que se prueba aquí no es «encuentra algo», que es trivial, sino **qué
 * queda arriba**: un acierto en tercer lugar, en una lista que se lee a tres
 * metros, es un fallo.
 */
class ChannelSearchTest : StringSpec({

	fun canal(id: String, nombre: String, numero: Int? = null, alias: List<String> = emptyList()) =
		ChannelProfile(
			id = id,
			name = nombre,
			url = "http://e/$id.m3u8",
			number = numero,
			aliases = alias,
		)

	val lineup = listOf(
		canal("canal22", "Canal 22", 122),
		canal("estrellashd", "Estrellas HD", 101, listOf("canal 2", "las estrellas")),
		canal("estrellassd", "Estrellas SD", 102),
		canal("discoveryhd", "Discovery HD", 201),
		canal("tnt", "T.N.T.", 202, listOf("tnt series")),
		canal("canal5", "Canal-5", 105),
		canal("milenio", "Milenio Televisión", 103),
	)

	fun ids(q: String) = ChannelSearch.buscar(lineup, q).map { it.id }

	"una consulta en blanco no devuelve nada, no devuelve todo" {
		ChannelSearch.buscar(lineup, "").shouldBeEmpty()
		ChannelSearch.buscar(lineup, "   ").shouldBeEmpty()
	}

	"el que empieza por la consulta va antes que el que la lleva en medio" {
		// «Estrellas HD» y «Estrellas SD» empiezan por «estrellas»; el alias
		// «las estrellas» del primero solo la contiene.
		ids("estrellas") shouldBe listOf("estrellashd", "estrellassd")
	}

	"a igual puntuación manda el orden de la guía" {
		// Los dos empiezan igual, así que desempata el orden que llega, que es
		// el del número de canal.
		ids("estrellas s") shouldBe listOf("estrellassd")
		ids("estrellas") shouldBe listOf("estrellashd", "estrellassd")
	}

	"un alias encuentra el canal que el nombre no encontraría" {
		ids("las estrellas") shouldBe listOf("estrellashd")
	}

	"un alias exacto gana a un nombre que solo empieza igual" {
		// Visto en la caja: «canal 2» enseñaba primero «Canal 22». El alias de
		// «Estrellas HD» **es** «canal 2», así que va delante.
		ids("canal 2") shouldBe listOf("estrellashd", "canal22")
	}

	"el nombre pesa más que el alias de otro" {
		// «canal 5» es el nombre de uno y parte del alias de ninguno; el alias
		// «canal 2» no debe colarse delante.
		ids("canal 5") shouldBe listOf("canal5")
	}

	"se ignoran acentos y mayúsculas" {
		ids("MILENIO TELEVISION") shouldBe listOf("milenio")
		ids("televisión") shouldBe listOf("milenio")
	}

	"se ignoran los signos, en la consulta y en el nombre" {
		ids("tnt") shouldBe listOf("tnt")
		ids("t.n.t") shouldBe listOf("tnt")
		ids("canal5") shouldBe listOf("canal5")
	}

	"una consulta de dígitos busca el número de canal" {
		ids("201") shouldBe listOf("discoveryhd")
	}

	"un número que no existe no devuelve nada" {
		ids("999").shouldBeEmpty()
	}

	"lo que no casa no aparece" {
		ids("fútbol").shouldBeEmpty()
	}
})
