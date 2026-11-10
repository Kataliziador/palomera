package mx.kataliziador.palomera.movil.ui

/**
 * La proporción con la que hay que pintar un vídeo, o `0` si aún no se sabe.
 *
 * ## Por qué no basta con ancho / alto
 *
 * Porque **los píxeles no siempre son cuadrados**. `CLAUDE.md` §6 lo fija para
 * la caja: dos de los seis canales de referencia son anamórficos
 * —`estrellashd-mp4` a 4:3 y `estrellassd-mp2v` a 20:17— y es el mismo
 * satélite que llega al teléfono. Con 720×480 y un SAR de 20/17 la imagen no
 * mide 1,5:1 sino 1,76:1; usar el primero estrecha las caras.
 *
 * Medido el 10-nov: el reproductor no corregía nada y estiraba el vídeo a los
 * 1080×2400 de la pantalla.
 */
fun aspectoDeVideo(ancho: Int, alto: Int, sar: Float): Double {
	// Antes del primer fotograma no hay tamaño. Elegir 16:9 «por si acaso»
	// haría saltar la imagen en cuanto llegue el de verdad.
	if (ancho <= 0 || alto <= 0) return 0.0

	// Un SAR de 0 o negativo llega de streams rotos: dividiría por cero o
	// daría una imagen invertida. Se ignora, que es lo peor que puede pasar
	// con un stream que ya venía mal.
	val proporcion = if (sar > 0f) sar else 1f

	return ancho.toDouble() * proporcion / alto.toDouble()
}
