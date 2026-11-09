package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.ui.tema.Tintas
import mx.kataliziador.palomera.movil.ui.tema.contraste

/**
 * Que la paleta del teléfono siga siendo la de la caja, y siga siendo legible.
 *
 * ## Por qué esto se prueba y no se mira
 *
 * `LiveTvPaleta` de la app de TV lleva una tabla de contrastes WCAG medidos, y
 * dos grises se quedaron fuera por no llegar al suelo de 4,5:1. Esa tabla es
 * conocimiento caro: se sacó comparando contra los fondos reales, no contra
 * negro puro.
 *
 * El teléfono **copia** esos valores porque `movil/` no puede depender de
 * `app/` (ver `docs/diseno-movil.md` §9). Una copia sin prueba deriva: alguien
 * aclara un gris para que «se lea mejor» y rompe la otra mitad de la tabla sin
 * enterarse. Esto fija las dos cosas —el valor exacto y el contraste que
 * produce— para que la deriva falle aquí y no en una tele.
 */
class ContrasteTest : FunSpec({

	test("los valores son exactamente los de LiveTvPaleta, no unos parecidos") {
		Tintas.FONDO shouldBe 0xFF101010L
		Tintas.HUECO shouldBe 0xFF161618L
		Tintas.MOSAICO shouldBe 0xFF1C1C1EL
		Tintas.ELEVADO shouldBe 0xFF2E2E31L
		Tintas.TINTA shouldBe 0xFFFFFFFFL
		Tintas.TINTA_APAGADA shouldBe 0xFFBDBDBDL
		Tintas.TINTA_SUTIL shouldBe 0xFF9DA1A6L
		Tintas.AVISO shouldBe 0xFFE2B33CL
		Tintas.EN_VIVO shouldBe 0xFFE53935L
	}

	test("el calculo de contraste es el de WCAG, comprobado con dos casos conocidos") {
		// Blanco sobre negro es el máximo posible, 21:1. Si esto falla, lo que
		// está mal es la fórmula y no la paleta.
		contraste(0xFFFFFFFFL, 0xFF000000L) shouldBe (21.0 plusOrMinus 0.01)
		contraste(0xFF808080L, 0xFF808080L) shouldBe (1.0 plusOrMinus 0.001)
	}

	test("las cuatro tintas pasan el suelo de 4,5:1 sobre los tres fondos") {
		val tintas = listOf(Tintas.TINTA, Tintas.TINTA_APAGADA, Tintas.TINTA_SUTIL, Tintas.AVISO)
		val fondos = listOf(Tintas.FONDO, Tintas.MOSAICO, Tintas.ELEVADO)

		for (t in tintas) {
			for (f in fondos) {
				contraste(t, f) shouldBeGreaterThanOrEqualTo 4.5
			}
		}
	}

	test("y dan los mismos numeros que la tabla medida de la app de TV") {
		// Sobre `#101010`, la columna que la caja tiene escrita.
		contraste(Tintas.TINTA, Tintas.FONDO) shouldBe (19.03 plusOrMinus 0.05)
		contraste(Tintas.TINTA_APAGADA, Tintas.FONDO) shouldBe (10.13 plusOrMinus 0.05)
		contraste(Tintas.TINTA_SUTIL, Tintas.FONDO) shouldBe (7.32 plusOrMinus 0.05)
		contraste(Tintas.AVISO, Tintas.FONDO) shouldBe (9.74 plusOrMinus 0.05)
	}

	test("el gris que la caja descarto sigue sin pasar, para que nadie lo reintroduzca") {
		// `#7D8187` daba 4,86 sobre la sección y **4,34 sobre una tarjeta**, que
		// es lo que lo tumbó. Si alguien lo trae al teléfono «porque se ve
		// más suave», esto lo para.
		contraste(0xFF7D8187L, Tintas.MOSAICO) shouldBe (4.34 plusOrMinus 0.05)
	}
})
