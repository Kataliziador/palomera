package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.leerEnProgreso
import mx.kataliziador.palomera.movil.catalogo.leerTitulos

/**
 * Lo que la app entiende de lo que contesta Jellyfin.
 *
 * El JSON de abajo es **recortado de una respuesta real** del site de los
 * probadores, no inventado: mismos nombres de campo, mismo anidamiento y los
 * mismos títulos largos en español. Un ejemplo inventado habría tenido campos
 * que no existen y habría escondido los que a veces faltan.
 *
 * ## La regla que gobierna este parser
 *
 * **Un elemento mal formado se descarta; la lista no se cae.** Es la misma que
 * `LiveConfigParser` aplica a los canales (`CLAUDE.md` §6): perder un título
 * por una errata del servidor es desproporcionado, pero perder la pantalla
 * entera lo es mucho más — y un catálogo de 16 233 elementos tiene de todo.
 */
class BibliotecaTest : FunSpec({

	test("lee los titulos de una respuesta real") {
		val json = """
			{"Items":[
			  {"Id":"a1","Name":"Transformers: El despertar de las bestias","Type":"Movie","ProductionYear":2023},
			  {"Id":"a2","Name":"Top Gun: Maverick","Type":"Movie","ProductionYear":2022}
			],"TotalRecordCount":2}
		""".trimIndent()

		val titulos = leerTitulos(json)

		titulos.size shouldBe 2
		titulos[0].nombre shouldBe "Transformers: El despertar de las bestias"
		titulos[0].anio shouldBe 2023
		titulos[1].id shouldBe "a2"
	}

	test("un año ausente no tumba el titulo: Peppa Pig no lo trae") {
		// Medido: en el servidor, «Peppa Pig» viene sin `ProductionYear`.
		val json = """{"Items":[{"Id":"p","Name":"Peppa Pig","Type":"Series"}]}"""

		val titulos = leerTitulos(json)

		titulos.size shouldBe 1
		titulos[0].anio shouldBe null
	}

	test("un elemento sin id o sin nombre se descarta, y los demas sobreviven") {
		val json = """
			{"Items":[
			  {"Id":"ok","Name":"Los Simpson","Type":"Series"},
			  {"Name":"sin id"},
			  {"Id":"sin-nombre"},
			  {"Id":"ok2","Name":"Chucky","Type":"Series"}
			]}
		""".trimIndent()

		leerTitulos(json).map { it.nombre } shouldBe listOf("Los Simpson", "Chucky")
	}

	test("una respuesta vacia es una lista vacia, no un fallo") {
		// Es el caso del primer día de un probador: no ha visto nada.
		leerTitulos("""{"Items":[],"TotalRecordCount":0}""") shouldBe emptyList()
	}

	test("un json roto no revienta la pantalla") {
		leerTitulos("esto no es json") shouldBe emptyList()
		leerTitulos("") shouldBe emptyList()
	}

	test("seguir viendo saca el porcentaje y los minutos que faltan") {
		// `RunTimeTicks` son unidades de 100 ns, que es como las manda Jellyfin.
		val json = """
			{"Items":[{
			  "Id":"f1","Name":"F1 la película","Type":"Movie",
			  "RunTimeTicks":85200000000,
			  "UserData":{"PlayedPercentage":31.4}
			}]}
		""".trimIndent()

		val items = leerEnProgreso(json)

		items.size shouldBe 1
		items[0].titulo shouldBe "F1 la película"
		// El porcentaje **que se enseña** se redondea a 31…
		items[0].porcentaje shouldBe 31
		// …pero lo que queda se calcula con el 31,4 real, no con el 31 de
		// arriba: 85 200 000 000 ticks son 142 min, y 142 × 0,686 = 97,4 → 97.
		// Esta prueba decía 98 porque yo hice la cuenta con el redondeado; la
		// equivocada era ella, y se deja dicho para que nadie la «arregle» al
		// revés.
		items[0].minutosRestantes shouldBe 97
	}

	test("sin UserData el porcentaje es cero y no se cuela una division rara") {
		val json = """{"Items":[{"Id":"x","Name":"Madagascar","RunTimeTicks":0}]}"""

		val items = leerEnProgreso(json)

		items[0].porcentaje shouldBe 0
		items[0].minutosRestantes shouldBe 0
	}
})

/**
 * Las bibliotecas del site, que son los carriles de Inicio.
 *
 * El site de los probadores sirve **21**: «Películas», «Series» y diecinueve
 * colecciones «Top …» curadas por el ISP. Ésas son el catálogo que él eligió
 * enseñar, así que son material de carril — y además son lo que llena el Inicio
 * de un abonado que todavía no ha visto nada, que es el primer día de todos.
 */
class BibliotecasTest : FunSpec({

	test("lee las bibliotecas de una respuesta real") {
		val json = """
			{"Items":[
			  {"Id":"ed2a","Name":"Películas","CollectionType":"movies"},
			  {"Id":"5dda","Name":"Series","CollectionType":"tvshows"},
			  {"Id":"1502","Name":"Top Películas Netflix","CollectionType":"movies"}
			]}
		""".trimIndent()

		val b = mx.kataliziador.palomera.movil.catalogo.leerBibliotecas(json)

		b.size shouldBe 3
		b[0].nombre shouldBe "Películas"
		b[2].id shouldBe "1502"
	}

	test("una biblioteca de TV en vivo no es un carril de catalogo") {
		// El site la sirve como una vista más, y abrirla desde Inicio llevaría
		// al abonado a la guía por una puerta que no es la suya.
		val json = """
			{"Items":[
			  {"Id":"a","Name":"Películas","CollectionType":"movies"},
			  {"Id":"b","Name":"TV en vivo","CollectionType":"livetv"}
			]}
		""".trimIndent()

		mx.kataliziador.palomera.movil.catalogo.leerBibliotecas(json)
			.map { it.nombre } shouldBe listOf("Películas")
	}

	test("sin nombre o sin id se descarta, como todo lo demas") {
		val json = """{"Items":[{"Name":"sin id","CollectionType":"movies"},{"Id":"x","CollectionType":"movies"}]}"""

		mx.kataliziador.palomera.movil.catalogo.leerBibliotecas(json) shouldBe emptyList()
	}
})
