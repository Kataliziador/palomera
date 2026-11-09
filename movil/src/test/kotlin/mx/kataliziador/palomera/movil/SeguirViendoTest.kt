package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.EnProgreso
import mx.kataliziador.palomera.movil.catalogo.sinDuplicados

/**
 * «Seguir viendo» sin repetir el mismo título.
 *
 * ## El fallo, medido el 09-nov contra el servidor
 *
 * `/Users/{id}/Items/Resume` devolvió **11 elementos para 8 títulos**: «¡Canta!
 * 2» y «Mayday» dos veces, y en el catálogo general «Top Gun: Maverick» sale
 * **tres**. No es un error del servidor: el mismo título vive en varias
 * bibliotecas —«Películas» y «Top Películas Netflix», por ejemplo— y cada
 * copia es un elemento con su propio id y su propio progreso.
 *
 * En una tele, con doce carriles, pasa desapercibido. En la **primera
 * pantalla** de un teléfono, donde caben tres tarjetas, dos de ellas iguales
 * parecen un error de la app.
 *
 * ## Por qué se queda el más avanzado
 *
 * Las dos copias llevan progreso por separado, así que el abonado vio **una**
 * de ellas. Quedarse con la de más avance es quedarse con la que de verdad
 * tocó; la otra está a cero o atrás, y reanudar por ahí le haría repetir.
 */
class SeguirViendoTest : FunSpec({

	test("sin duplicados no cambia nada, ni el orden") {
		val lista = listOf(
			EnProgreso("1", "F1 la película", 31),
			EnProgreso("2", "Madagascar", 26),
		)

		sinDuplicados(lista) shouldBe lista
	}

	test("dos copias del mismo titulo se quedan en una") {
		val lista = listOf(
			EnProgreso("a", "¡Canta! 2", 19),
			EnProgreso("b", "¡Canta! 2", 19),
		)

		sinDuplicados(lista) shouldBe listOf(EnProgreso("a", "¡Canta! 2", 19))
	}

	test("se queda la copia mas avanzada, que es la que el abonado vio") {
		val lista = listOf(
			EnProgreso("a", "Mayday", 4),
			EnProgreso("b", "Mayday", 13),
		)

		sinDuplicados(lista) shouldBe listOf(EnProgreso("b", "Mayday", 13))
	}

	test("el superviviente conserva el sitio de la primera aparicion") {
		// Si el más avanzado saltara al final, la fila se reordenaría sola
		// entre dos aperturas de la app y el abonado perdería de vista lo que
		// estaba viendo.
		val lista = listOf(
			EnProgreso("a", "Mayday", 4),
			EnProgreso("b", "Madagascar", 26),
			EnProgreso("c", "Mayday", 13),
		)

		sinDuplicados(lista).map { it.titulo } shouldBe listOf("Mayday", "Madagascar")
	}

	test("los once elementos reales del servidor quedan en ocho titulos") {
		val delServidor = listOf(
			EnProgreso("1", "F1 la película", 31),
			EnProgreso("2", "¡Canta! 2", 19),
			EnProgreso("3", "¡Canta! 2", 19),
			EnProgreso("4", "Las guerreras k-pop", 8),
			EnProgreso("5", "Madagascar", 26),
			EnProgreso("6", "La purga infinita", 29),
			EnProgreso("7", "Mayday", 13),
			EnProgreso("8", "Mayday", 13),
		)

		sinDuplicados(delServidor).map { it.titulo } shouldBe listOf(
			"F1 la película",
			"¡Canta! 2",
			"Las guerreras k-pop",
			"Madagascar",
			"La purga infinita",
			"Mayday",
		)
	}

	test("los espacios y las mayusculas no crean un duplicado nuevo") {
		// El mismo título catalogado a mano en dos bibliotecas puede venir con
		// un espacio de más o con otra caja.
		val lista = listOf(
			EnProgreso("a", "Top Gun: Maverick", 12),
			EnProgreso("b", "  top gun: maverick  ", 40),
		)

		sinDuplicados(lista) shouldBe listOf(EnProgreso("b", "  top gun: maverick  ", 40))
	}

	test("una lista vacia no revienta") {
		sinDuplicados(emptyList()) shouldBe emptyList()
	}
})
