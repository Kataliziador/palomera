package mx.kataliziador.palomera.movil.ui.tema

import androidx.compose.ui.graphics.Color

/**
 * La paleta como la consume la interfaz.
 *
 * No tiene ningún valor propio: todos salen de [Tintas], que es donde están
 * probados. Si hace falta un color nuevo, se añade allí **con su prueba de
 * contraste** y luego aquí.
 */
object Paleta {
	val fondo = Color(Tintas.FONDO)
	val hueco = Color(Tintas.HUECO)
	val mosaico = Color(Tintas.MOSAICO)
	val elevado = Color(Tintas.ELEVADO)

	val tinta = Color(Tintas.TINTA)
	val tintaApagada = Color(Tintas.TINTA_APAGADA)
	val tintaSutil = Color(Tintas.TINTA_SUTIL)

	val aviso = Color(Tintas.AVISO)
	val enVivo = Color(Tintas.EN_VIVO)
	val acentoNeutro = Color(Tintas.ACENTO_NEUTRO)

	/**
	 * Las dos tintas secundarias **cuando lo que hay detrás es vídeo**.
	 *
	 * Un gris fijo no sirve ahí y está medido en la caja: sobre una carta de
	 * ajuste blanca, `#BDBDBD` daba 1,66:1. Blanco con alfa se compone con lo
	 * que haya debajo, así que sube cuando el fondo sube.
	 *
	 * **No sustituyen a las de arriba**, que siguen siendo las correctas donde
	 * el fondo lo ponemos nosotros.
	 */
	val tintaApagadaSobreVideo = Color.White.copy(alpha = 0.78f)

	/** Ver [tintaApagadaSobreVideo]. */
	val tintaSutilSobreVideo = Color.White.copy(alpha = 0.62f)

	/** El velo bajo las marcas que caen encima de una imagen. */
	val velo = Color(0x8C000000)
}
