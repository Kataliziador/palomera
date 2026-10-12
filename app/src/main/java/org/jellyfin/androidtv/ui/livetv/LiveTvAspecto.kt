package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import org.jellyfin.livetv.diagnostics.LiveLog
import org.jellyfin.livetv.engine.AspectMode

/**
 * El modo de ajuste de imagen que eligió quien mira, recordado.
 *
 * Antes vivía en un `remember` del reproductor, así que volvía a «Ajustar» al
 * salir de la pantalla y en cada arranque de la caja. Quien tiene un televisor
 * 16:9 y no quiere franjas en los canales 4:3 tenía que volver a elegir cada
 * vez, y con un lineup real de ~120 canales eso es una vez por sesión y para
 * siempre.
 *
 * **Es de la caja, no del operador**, igual que [LiveTvUltimoCanal] y los
 * favoritos: no viaja al servidor y no sale de aquí. Y por la misma razón no
 * lleva interruptor de desarrollador — lo que cambia no es cómo el motor aplica
 * el aspecto, que no se toca, sino que se recuerde cuál se pidió.
 *
 * **De fábrica sigue siendo [AspectMode.FIT]**, que es el comportamiento
 * anterior: una instalación nueva se ve exactamente igual que antes de este
 * cambio, con la proporción real y las franjas que le correspondan. Esto solo
 * existe para que quien decida lo contrario no tenga que repetirlo.
 *
 * ## Qué cuesta cada modo, porque no hay forma de no pagar
 *
 * Un 4:3 en una pantalla 16:9 no se puede llenar sin recortar o sin deformar:
 *
 * - `FILL` recorta el **25 % del alto** —12,5 % arriba y 12,5 % abajo— y deja
 *   la geometría intacta. Es el que quita las franjas sin que nadie salga
 *   gordo, y en un canal que ya es 16:9 **no hace nada**, así que se puede
 *   dejar puesto sin estropear el resto del lineup.
 * - `RATIO_16_9` no pierde imagen y la ensancha un 33 %.
 * - `FIT` deja las franjas y es exacto.
 *
 * Medido sobre los seis canales de desarrollo: **solo `estrellassd` tiene
 * franjas** —544×480 con SAR 20/17, que da 4:3—. `estrellashd` es 1440×1080 con
 * SAR 4:3, o sea 16:9 en pantalla, y los otros cuatro son 1:1. En el lineup
 * real de un ISP habrá muchos más SD 4:3, que es donde esto importa de verdad.
 */
class LiveTvAspecto private constructor(context: Context) {

	private val prefs = context.getSharedPreferences("livetv.aspecto", Context.MODE_PRIVATE)

	/**
	 * El modo guardado, o [AspectMode.FIT] si nadie ha elegido nada.
	 *
	 * Un valor que ya no exista en el enum —porque alguien renombre un modo en
	 * una versión futura— cae a `FIT` y se registra, en vez de tirar la
	 * pantalla del reproductor. Es la misma regla que el resto del proyecto
	 * aplica a los datos guardados: lo desconocido se ignora y se anota.
	 */
	var modo: AspectMode
		get() {
			val guardado = prefs.getString(KEY, null) ?: return AspectMode.FIT
			return runCatching { AspectMode.valueOf(guardado) }.getOrElse {
				LiveLog.warn("aspecto.modoDesconocido", "guardado" to guardado)
				AspectMode.FIT
			}
		}
		set(valor) {
			if (valor == modo) return
			prefs.edit().putString(KEY, valor.name).apply()
			LiveLog.debug("aspecto.recordado", "modo" to valor)
		}

	companion object {
		private const val KEY = "modo"

		@Volatile
		private var instancia: LiveTvAspecto? = null

		fun of(context: Context): LiveTvAspecto =
			instancia ?: synchronized(this) {
				instancia ?: LiveTvAspecto(context.applicationContext).also { instancia = it }
			}
	}
}
