package mx.kataliziador.palomera.movil.ui.tema

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * La escala del teléfono.
 *
 * ## Por qué no es la de la caja
 *
 * El sistema «TV en Vivo ISP» pone el **suelo de lectura en 16 sp**, y lo
 * justifica: es «lo que la app aplica a todo lo que hay que leer desde el
 * sofá». A treinta centímetros eso es enorme —caben tres cosas donde deberían
 * caber seis—, así que aquí el suelo baja a **13 sp**.
 *
 * **Lo que no baja es el blanco del dedo.** 44 dp es el mínimo de cualquier
 * cosa que se toque, y manda sobre la tipografía: un renglón de 13 sp se
 * acompaña del relleno que haga falta, no se encoge su zona táctil.
 *
 * [etiqueta], de 11 sp, es la única por debajo del suelo, y sigue la misma
 * regla que `ltv-label` en la caja: **sólo para marcas que se reconocen sin
 * leerse** —«HD», el número de un canal, un año—, nunca para texto corrido.
 */
object Tipografia {
	/** Título de pantalla: «Catálogo», «Ajustes». */
	val titulo = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)

	/** Título de una ficha, encima del arte. */
	val cabecera = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold)

	/** Título de carril o de sección: «Seguir viendo». */
	val seccion = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)

	/** Texto corrido: sinopsis, cuerpo de un aviso. */
	val cuerpo = TextStyle(fontSize = 14.sp)

	/** Lo que va debajo de un cartel, y el nombre de un canal en la lista. */
	val tarjeta = TextStyle(fontSize = 12.sp)

	/** Año, duración, horario, lo que queda. */
	val detalle = TextStyle(fontSize = 12.sp)

	/** Ver la nota de arriba: marcas, no texto. */
	val etiqueta = TextStyle(fontSize = 11.sp)
}

/** El blanco mínimo de cualquier cosa que se toque, en dp. */
const val BLANCO_TACTIL_DP = 44
