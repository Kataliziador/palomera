package mx.kataliziador.palomera.movil.ui.tema

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Los colores de Palomera, en crudo, y la forma de comprobar que se leen.
 *
 * ## Por qué los valores viven aquí y no en el objeto de Compose
 *
 * Para poder **probarlos sin Android**. `androidx.compose.ui.graphics.Color`
 * arrastra la plataforma a una prueba de unidad; un `Long` no. Así
 * [ContrasteTest] corre en la JVM y falla en segundos cuando alguien cambia un
 * gris, en vez de descubrirse mirando una pantalla.
 *
 * [Paleta] es la cara de Compose de estos mismos números, y no tiene otros.
 *
 * ## De dónde salen
 *
 * Son **exactamente** los de `LiveTvPaleta` de la app de TV, que los eligió
 * midiendo contraste contra los fondos reales y descartó dos grises por no
 * llegar al suelo de 4,5:1. El teléfono los copia porque `movil/` no puede
 * depender de `app/`; la copia está fijada por pruebas para que no derive, y
 * la deuda está anotada en `docs/diseno-movil.md` §9.
 */
object Tintas {
	/** El fondo de la app. Es el `background` del tema de la caja. */
	const val FONDO = 0xFF101010L

	/** El hueco de algo que todavía no ha llegado: una fila fantasma. */
	const val HUECO = 0xFF161618L

	/** El mosaico de una tarjeta en reposo, y el renglón de un canal. */
	const val MOSAICO = 0xFF1C1C1EL

	/** Lo que sube: pastilla, menú, distintivo, hoja. */
	const val ELEVADO = 0xFF2E2E31L

	/** Tinta principal: títulos, el nombre del canal, cifras. */
	const val TINTA = 0xFFFFFFFFL

	/** Tinta secundaria: sinopsis, lo que viene luego, cuerpo de avisos. */
	const val TINTA_APAGADA = 0xFFBDBDBDL

	/** Tinta terciaria: horas, etiquetas, años, detalle técnico. */
	const val TINTA_SUTIL = 0xFF9DA1A6L

	/** Algo no está como debería y el abonado puede entenderlo. */
	const val AVISO = 0xFFE2B33CL

	/** El punto del directo. Es una marca, no un fondo de texto. */
	const val EN_VIVO = 0xFFE53935L

	/**
	 * El acento cuando el site no trae marca configurada.
	 *
	 * Coincide con [TINTA_APAGADA] a propósito, igual que en la caja: así una
	 * instalación sin marca queda monocroma en vez de medio teñida.
	 */
	const val ACENTO_NEUTRO = TINTA_APAGADA
}

/**
 * El contraste WCAG 2.1 entre dos colores opacos, de 1 a 21.
 *
 * El canal alfa **se ignora**: esto compara dos colores ya compuestos. Para
 * tinta sobre vídeo no sirve un número fijo —lo que hay detrás lo pone el ISP—
 * y por eso esas dos tintas son blanco con alfa (ver [Paleta]).
 */
fun contraste(unColor: Long, otroColor: Long): Double {
	val a = luminancia(unColor)
	val b = luminancia(otroColor)
	return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** Luminancia relativa, la de la fórmula de WCAG. */
private fun luminancia(argb: Long): Double {
	val r = canal(((argb shr 16) and 0xFF).toInt())
	val g = canal(((argb shr 8) and 0xFF).toInt())
	val b = canal((argb and 0xFF).toInt())
	return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun canal(valor: Int): Double {
	val c = valor / 255.0
	return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}
