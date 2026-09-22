package org.jellyfin.androidtv.ui.livetv

import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.livetv.source.ChannelProfile

/**
 * Lista provisional de canales de prueba, leída de `BuildConfig`.
 *
 * **Es andamiaje de M0 y desaparece en B1**, cuando el `ConfigRepository`
 * consuma el `config.json` del servidor de configuración. Existe para que las
 * fases de motor se puedan validar contra los seis canales sin recompilar cada
 * vez que haga falta cambiar de uno.
 *
 * Nada está codificado aquí (CLAUDE.md regla 12): el contenido llega por
 * `BuildConfig`, con el formato
 * `id|nombre|url|videoCodec|audioCodec` y `;` entre canales.
 */
object BootstrapChannels {

	/** Clave del argumento con que la sección le dice al reproductor qué canal abrir. */
	const val EXTRA_CHANNEL_ID = "livetv.channelId"

	val all: List<ChannelProfile> by lazy { parse(BuildConfig.LIVETV_BOOTSTRAP_CHANNELS) }

	fun byId(id: String?): ChannelProfile? = all.firstOrNull { it.id == id } ?: all.firstOrNull()

	private fun parse(raw: String): List<ChannelProfile> = raw
		.split(';')
		.filter { it.isNotBlank() }
		.map { entry ->
			val f = entry.split('|')
			require(f.size >= 5) {
				"Canal mal formado en LIVETV_BOOTSTRAP_CHANNELS: '$entry'. " +
					"Se esperaba id|nombre|url|videoCodec|audioCodec"
			}
			ChannelProfile(
				id = f[0].trim(),
				name = f[1].trim(),
				url = f[2].trim(),
				videoCodec = f[3].trim(),
				audioCodec = f[4].trim(),
			)
		}
}
