package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.leerDetalle
import mx.kataliziador.palomera.movil.catalogo.proveedorDe

/**
 * La ficha de un título, leída de lo que contesta el site.
 *
 * El JSON es **recortado de la respuesta real** de «Top Gun: Maverick» en el
 * site de los probadores: mismos nombres de campo, mismas etiquetas de pista
 * —«Español Latino», «Original - English»— y la misma clasificación mexicana.
 */
class FichaTest : FunSpec({

	val topGun = """
		{
		  "Id":"tg","Name":"Top Gun: Maverick","Type":"Movie",
		  "ProductionYear":2022,
		  "RunTimeTicks":78646610000,
		  "OfficialRating":"MX-B",
		  "Genres":["Acción","Drama"],
		  "Overview":"Después de más de 30 años de servicio como uno de los mejores aviadores.",
		  "MediaStreams":[
		    {"Type":"Subtitle","Language":"spa","DisplayTitle":"Spanish - SUBRIP - External"},
		    {"Type":"Video","Language":"eng","DisplayTitle":"720p H264 SDR"},
		    {"Type":"Audio","Language":"spa","DisplayTitle":"Español Latino - Spanish"},
		    {"Type":"Audio","Language":"eng","DisplayTitle":"Original - English - AAC"}
		  ],
		  "UserData":{"PlaybackPositionTicks":0,"Played":false}
		}
	""".trimIndent()

	test("lee lo que la ficha necesita enseñar") {
		val d = leerDetalle(topGun)!!

		d.nombre shouldBe "Top Gun: Maverick"
		d.anio shouldBe 2022
		d.clasificacion shouldBe "MX-B"
		d.generos shouldBe listOf("Acción", "Drama")
		// 78 646 610 000 ticks ÷ 600 000 000 = 131 min.
		d.minutos shouldBe 131
	}

	test("las pistas de audio salen en español, no en codigos") {
		// El abonado elige «Español» o «Inglés», no «spa» ni «eng».
		leerDetalle(topGun)!!.audios shouldBe listOf("Español", "Inglés")
	}

	test("los subtitulos van aparte del audio") {
		leerDetalle(topGun)!!.subtitulos shouldBe listOf("Español")
	}

	test("un titulo sin pistas declaradas no inventa ninguna") {
		val d = leerDetalle("""{"Id":"x","Name":"Sin pistas"}""")!!

		d.audios shouldBe emptyList()
		d.subtitulos shouldBe emptyList()
		d.minutos shouldBe 0
	}

	test("una respuesta ilegible no devuelve una ficha a medias") {
		// Media ficha pintada es peor que el estado vacío, que sí está diseñado.
		leerDetalle("no es json") shouldBe null
		leerDetalle("""{"Name":"sin id"}""") shouldBe null
	}
})

/**
 * De qué proveedor es una biblioteca del site.
 *
 * El ISP nombra sus colecciones «Top Películas Netflix», «Top Series HBO Max»…
 * Esa última palabra es la que el abonado reconoce, y es con la que se filtra
 * el catálogo. «Películas» y «Series» a secas no son de nadie.
 */
class ProveedorTest : FunSpec({

	test("saca la marca de una coleccion del ISP") {
		proveedorDe("Top Películas Netflix") shouldBe "Netflix"
		proveedorDe("Top Series HBO Max") shouldBe "HBO Max"
		proveedorDe("Top Películas Prime Video") shouldBe "Prime Video"
	}

	test("las bibliotecas generales no son de ningun proveedor") {
		proveedorDe("Películas") shouldBe null
		proveedorDe("Series") shouldBe null
	}

	test("«Niños» es una coleccion del ISP aunque no sea una marca") {
		// No es un proveedor, pero es como el ISP lo llama y el abonado lo
		// entiende igual. Filtrar por «Niños» es exactamente lo que hará un
		// padre, así que se trata como una más.
		proveedorDe("Top Películas Niños") shouldBe "Niños"
	}

	test("un nombre que no sigue el patron se deja en paz") {
		proveedorDe("Documentales") shouldBe null
		proveedorDe("") shouldBe null
	}
})
