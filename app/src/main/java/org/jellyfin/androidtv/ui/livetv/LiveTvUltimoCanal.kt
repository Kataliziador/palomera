package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * El último canal que se sintonizó en esta caja.
 *
 * Con ~120 canales, volver a la sección y tener que buscar otra vez el canal de
 * siempre es la diferencia entre una guía usable y una lista. Se guarda **por
 * `id`**, igual que los favoritos: el operador va a renumerar y reordenar el
 * lineup, y lo que la persona veía no puede moverse de sitio cuando lo haga.
 *
 * Es de la caja, no del operador: no viaja al servidor ni sale de aquí.
 */
class LiveTvUltimoCanal private constructor(context: Context) {

	private val prefs = context.getSharedPreferences("livetv.ultimo", Context.MODE_PRIVATE)

	/** `null` mientras nadie haya sintonizado nada en esta caja. */
	val id: String? get() = prefs.getString(KEY, null)

	fun recordar(channelId: String) {
		if (channelId == id) return
		prefs.edit().putString(KEY, channelId).apply()
		LiveLog.debug("ultimoCanal.guardado", "canal" to channelId)
	}

	/**
	 * A qué canal mirar al entrar a la sección, de lo más personal a lo más
	 * genérico: lo último que vio esta persona, lo que el ISP puso por defecto,
	 * y el primero de la lista.
	 *
	 * **El `defaultChannel` del `config.json` solo decide la primera vez.** Una
	 * caja recién instalada no tiene historia, y ese es exactamente el momento
	 * en que el abonado se forma una opinión del servicio.
	 */
	fun inicial(canales: List<String>, porDefecto: String?): String? {
		val ultimo = id
		// Un canal que ya no está en el lineup no sirve: el operador pudo
		// retirarlo, y dejar el foco apuntando a un hueco sería peor que no
		// recordarlo.
		if (ultimo != null && ultimo in canales) return ultimo
		if (porDefecto != null && porDefecto in canales) return porDefecto
		return canales.firstOrNull()
	}

	companion object {
		private const val KEY = "ultimoCanalId"

		@Volatile
		private var instancia: LiveTvUltimoCanal? = null

		fun of(context: Context): LiveTvUltimoCanal =
			instancia ?: synchronized(this) {
				instancia ?: LiveTvUltimoCanal(context.applicationContext).also { instancia = it }
			}
	}
}
