package mx.kataliziador.palomera.movil.ui

import androidx.compose.ui.graphics.Color

/**
 * El color del hueco de un cartel, mientras no haya imágenes.
 *
 * **Se deriva del id**, así que el mismo título tiene siempre el mismo tono:
 * en Inicio, en Catálogo y entre dos aperturas de la app. Si fuera al azar, la
 * rejilla parpadearía y el abonado no podría reconocer una película por su
 * mancha de color, que es justo lo que hace mientras desliza.
 *
 * Son cuatro grises templados de la familia de `Paleta.mosaico`: lo bastante
 * distintos para separar tarjetas vecinas, lo bastante apagados para no
 * competir con el texto que llevan debajo.
 *
 * Desaparece el día que el site sirva carátulas.
 */
fun tonoDeCartel(id: String): Color = TONOS[Math.floorMod(id.hashCode(), TONOS.size)]

private val TONOS = listOf(
	Color(0xFF2A2D34),
	Color(0xFF332B2B),
	Color(0xFF26302B),
	Color(0xFF2E2A33),
)
