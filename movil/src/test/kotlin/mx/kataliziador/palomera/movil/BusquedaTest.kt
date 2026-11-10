package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.MINIMO_PARA_BUSCAR
import mx.kataliziador.palomera.movil.catalogo.sePreguntaAlServidor

/**
 * Cuándo se le pregunta al servidor del ISP, y cuándo no.
 *
 * ## Por qué esto no es un detalle
 *
 * La búsqueda escribe mientras el abonado teclea, así que **cada tecla es una
 * petición** si nadie lo impide. Con una sola letra, además, Jellyfin devuelve
 * medio catálogo —16 233 elementos en el site de pruebas—, que ni le sirve a
 * quien busca ni le sienta bien al servidor del ISP, que atiende a todas las
 * casas a la vez.
 *
 * Los canales son otra cosa: ya están en memoria, así que se filtran desde la
 * primera letra y sin pedirle nada a nadie.
 */
class BusquedaTest : FunSpec({

	test("con una sola letra no se molesta al servidor") {
		sePreguntaAlServidor("") shouldBe false
		sePreguntaAlServidor("a") shouldBe false
	}

	test("desde dos letras si") {
		sePreguntaAlServidor("ab") shouldBe true
		MINIMO_PARA_BUSCAR shouldBe 2
	}

	test("los espacios no cuentan como letras") {
		// «a » son dos caracteres y una letra. Sin esto, la barra espaciadora
		// dispara una consulta que devuelve el catálogo entero.
		sePreguntaAlServidor("a ") shouldBe false
		sePreguntaAlServidor("  ") shouldBe false
	}

	test("dos digitos tambien buscan") {
		// Quien teclea «20» puede estar buscando «2012» o el canal 20. La
		// búsqueda no decide por él: enseña las dos cosas.
		sePreguntaAlServidor("20") shouldBe true
	}
})
