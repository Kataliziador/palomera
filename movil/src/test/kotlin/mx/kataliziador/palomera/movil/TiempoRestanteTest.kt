package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.tiempoRestante

/**
 * Lo que queda de una película, dicho como lo piensa una persona.
 *
 * ## Por qué esto existe
 *
 * La primera versión de «Seguir viendo» enseñaba «69 % por ver». Es cierto y
 * **no sirve**: nadie decide si le da tiempo a terminar una película en
 * porcentajes. Se vio al mirar la pantalla en el teléfono, no en la maqueta
 * —que ya decía «Quedan 1 h 38 min»—, y es el ejemplo de por qué una maqueta
 * en un navegador no sustituye al aparato.
 *
 * Las reglas son de redacción, no de cálculo, y por eso se fijan aquí: el
 * formato es lo que se lee de un vistazo desde un carril que se desliza.
 */
class TiempoRestanteTest : FunSpec({

	test("menos de una hora va en minutos a secas") {
		tiempoRestante(45) shouldBe "Quedan 45 min"
		tiempoRestante(1) shouldBe "Quedan 1 min"
	}

	test("una hora o mas se parte en horas y minutos") {
		tiempoRestante(98) shouldBe "Quedan 1 h 38 min"
		tiempoRestante(165) shouldBe "Quedan 2 h 45 min"
	}

	test("la hora en punto no arrastra un «0 min» que sobra") {
		tiempoRestante(60) shouldBe "Quedan 1 h"
		tiempoRestante(120) shouldBe "Quedan 2 h"
	}

	test("lo que esta por acabar no dice «0 min», que parece un fallo") {
		// Un título al 99 % redondea a cero, y «Quedan 0 min» se lee como un
		// error de la app en vez de como «ya casi».
		tiempoRestante(0) shouldBe "Casi terminada"
	}

	test("un dato imposible no rompe el carril") {
		// Si el servidor manda una duración rara, la tarjeta tiene que seguir
		// dibujándose: perder un renglón es peor que perder una frase.
		tiempoRestante(-5) shouldBe "Casi terminada"
	}
})
