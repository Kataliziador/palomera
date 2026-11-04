package org.jellyfin.androidtv.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * All possible homesections, "synced" with jellyfin-web.
 *
 * https://github.com/jellyfin/jellyfin-web/blob/master/src/components/homesections/homesections.js
 */
enum class HomeSectionType(
	override val serializedName: String,
	override val nameRes: Int,
	/** Ver [LIVE_TV]: una sección que ya no se dibuja no se puede elegir. */
	override val hidden: Boolean = false,
) : PreferenceEnum {
	LATEST_MEDIA("latestmedia", R.string.home_section_latest_media),
	LIBRARY_TILES_SMALL("smalllibrarytiles", R.string.home_section_library),
	LIBRARY_BUTTONS("librarybuttons", R.string.home_section_library_small),
	RESUME("resume", R.string.home_section_resume),
	RESUME_AUDIO("resumeaudio", R.string.home_section_resume_audio),
	RESUME_BOOK("resumebook", R.string.home_section_resume_book),
	ACTIVE_RECORDINGS("activerecordings", R.string.home_section_active_recordings),
	NEXT_UP("nextup", R.string.home_section_next_up),

	/**
	 * La fila de TV en vivo del inicio, que **ya no se dibuja** y por eso no se
	 * ofrece.
	 *
	 * `HomeRowsFragment` la resuelve con `-> Unit` desde el 04-nov: era una
	 * cabecera propia y un único botón «Guía de TV en vivo» para ir exactamente
	 * adonde va el botón de `MainToolbar`, que está en las tres pantallas y no
	 * depende de permisos ni de la configuración del servidor. Un renglón entero
	 * del inicio para repetir algo que se ve siempre.
	 *
	 * **Seguía ofreciéndose en Preferencias → Inicio, y elegirla no hacía
	 * nada**: la pantalla pinta este enum entero. `hidden` es el mecanismo que
	 * el propio DSL ya tenía para esto ([org.jellyfin.preference.PreferenceEnum]),
	 * así que no hace falta tocar la pantalla.
	 *
	 * **No se borra el valor.** El nombre serializado `livetv` lo escribe
	 * jellyfin-web y puede estar guardado en la configuración de cualquier
	 * usuario; sin esta entrada, leerlo sería un valor desconocido. Se queda
	 * para poder leerlo e ignorarlo.
	 *
	 * Si alguna vez el inicio tiene que enseñar televisión, lo que corresponde
	 * es una fila **con canales** del catálogo propio (pendiente B8), no un
	 * botón, y entonces esto vuelve a ser elegible.
	 */
	LIVE_TV("livetv", R.string.home_section_livetv, hidden = true),

	NONE("none", R.string.home_section_none),
}
