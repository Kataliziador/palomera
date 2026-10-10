package org.jellyfin.livetv.engine

import androidx.media3.common.C

/**
 * Cuánto lleva la reproducción sin progresar. Es la única señal en la que se
 * apoya el vigilante, así que de ella depende que una caja atascada se cure.
 *
 * ## Progresar es reproducir **y** moverse, y hacen falta las dos
 *
 * Cada mitad de esa frase viene de un fallo distinto medido en pantalla, y
 * ninguna de las dos sola los ve los dos.
 *
 * **Que la posición cambie no basta** (09-oct). Una sesión de `discoveryhd` se
 * quedó parada **98 minutos** sin que nada la rescatara:
 *
 * ```
 * estado=almacenando reproduciendo=false posS=-5893.0 sinAvanzarMs=314  bufferS=35.8 directoS=5928.0
 * estado=almacenando reproduciendo=false posS=-5895.1 sinAvanzarMs=1312 bufferS=35.8 directoS=5932.0
 * ```
 *
 * El reproductor estaba detenido en un punto que la ventana ya había dejado
 * atrás —de ahí la posición negativa, cayendo dos segundos por cada dos
 * segundos de reloj— con 35,8 s de datos delante sin consumir. La posición
 * **cambiaba sin parar**, así que `sinAvanzarMs` no pasaba de dos segundos: el
 * vigilante nunca vio un atasco y su escalera de rescate, escrita y probada, no
 * llegó a empezar.
 *
 * **Y que esté reproduciendo tampoco basta** (30-sep). Ahí el reproductor
 * decía `reproduciendo=true` con la imagen muerta. Si solo se mirara el estado,
 * ese fallo sería invisible.
 *
 * **Lo que no se puede usar es «la posición sube».** En un directo la posición
 * se mide desde el principio de la ventana y la ventana corre al mismo ritmo
 * que la reproducción, así que en régimen normal la posición **no avanza: se
 * queda quieta oscilando**. Medido en la misma sesión sana, una banda de 19,1 a
 * 23,6 s durante minutos. Pedir que suba daría un atasco falso cada veinte
 * segundos en todos los canales — lo dijo la prueba de esta clase antes de que
 * el error saliera del repositorio.
 */
internal class RelojDeAvance {

	private var ultimaPosMs = C.TIME_UNSET
	private var desdeMs = 0L

	fun reiniciar(ahoraMs: Long) {
		ultimaPosMs = C.TIME_UNSET
		desdeMs = ahoraMs
	}

	/**
	 * Anota el estado de ahora y devuelve cuánto lleva sin progresar.
	 *
	 * @param posMs posición del reproductor, o [C.TIME_UNSET] si no hay.
	 * @param reproduciendo lo que dice el reproductor de sí mismo.
	 */
	fun observar(posMs: Long, reproduciendo: Boolean, ahoraMs: Long): Long {
		val semovio = posMs != C.TIME_UNSET && posMs != ultimaPosMs
		ultimaPosMs = posMs
		if (reproduciendo && semovio) desdeMs = ahoraMs
		return ahoraMs - desdeMs
	}
}
