package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * Los canales que el suscriptor marcó como favoritos.
 *
 * Viven en la caja y no en el servidor: son de la persona, no del operador, y
 * un lineup de 120 canales sin favoritos obliga a recorrer la guía entera cada
 * vez para llegar a los cuatro que de verdad se ven.
 *
 * Se guardan **por `id` de canal**, no por número ni por posición: el operador
 * va a renumerar y reordenar el lineup, y los favoritos de la gente no pueden
 * moverse de sitio cuando lo haga.
 */
class LiveTvFavorites private constructor(context: Context) {

	private val prefs: SharedPreferences =
		context.getSharedPreferences("livetv.favorites", Context.MODE_PRIVATE)

	private val _ids = MutableStateFlow(leer())
	val ids: StateFlow<Set<String>> get() = _ids

	fun isFavorite(channelId: String): Boolean = channelId in _ids.value

	fun toggle(channelId: String) {
		val actuales = _ids.value
		val nuevos = if (channelId in actuales) actuales - channelId else actuales + channelId
		prefs.edit().putStringSet(KEY, nuevos).apply()
		_ids.value = nuevos
		LiveLog.info(
			"favoritos.cambio",
			"canal" to channelId,
			"ahora" to if (channelId in nuevos) "favorito" else "normal",
			"total" to nuevos.size,
		)
	}

	// Se copia el conjunto: `getStringSet` devuelve una instancia que no se debe
	// modificar ni conservar, y guardarla tal cual acaba en fallos difíciles de
	// perseguir cuando otro proceso escribe las preferencias.
	private fun leer(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

	companion object {
		private const val KEY = "channelIds"

		@Volatile
		private var instancia: LiveTvFavorites? = null

		fun of(context: Context): LiveTvFavorites =
			instancia ?: synchronized(this) {
				instancia ?: LiveTvFavorites(context.applicationContext).also { instancia = it }
			}
	}
}
