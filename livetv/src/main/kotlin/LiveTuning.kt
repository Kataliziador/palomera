package org.jellyfin.livetv

/**
 * Toda la sintonía del motor vive aquí (CLAUDE.md regla 7: cero números mágicos).
 *
 * Los valores son los de LIVETV_ENGINE.md §5, medidos contra los canales reales.
 * Cada uno es sobrescribible por canal desde el `config.json`; lo de aquí es el
 * valor de fábrica, el que se usa cuando el servidor de configuración no responde.
 */
object LiveTuning {
	// --- Buffer -------------------------------------------------------------
	const val MIN_BUFFER_MS = 15_000
	const val MAX_BUFFER_MS = 30_000
	const val BUFFER_FOR_PLAYBACK_MS = 2_000
	const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000

	// --- Red ----------------------------------------------------------------
	/** Un canal caído por lluvia es un estado temporal, no un error (CLAUDE.md §6). */
	const val CONNECT_TIMEOUT_MS = 8_000
	const val READ_TIMEOUT_MS = 30_000

	// --- Live offset --------------------------------------------------------
	/** Canales sin particularidades (TotalPlay). */
	const val TARGET_OFFSET_DEFAULT_MS = 8_000L

	/** Satelitales: banda Ku, la lluvia es frecuente y el colchón compra resiliencia. */
	const val TARGET_OFFSET_SATELLITE_MS = 12_000L

	/**
	 * El `targetOffsetMs` nunca puede pasar de esta fracción de la ventana DVR
	 * medida del playlist.
	 *
	 * Medido el 2026-09-22: con `segment_count 6`, `hbohd-70w` daba una ventana
	 * de 13.88 s. Un offset de 12 s dejaba 1.9 s antes de BEHIND_LIVE_WINDOW: el
	 * colchón pensado para la lluvia era justo lo que rompía el canal. Ver
	 * CHANNELS.md §2.bis.
	 */
	const val TARGET_OFFSET_MAX_WINDOW_FRACTION = 0.5

	// --- Reintentos ---------------------------------------------------------
	/**
	 * Reintentos efectivamente infinitos: prohibido cerrar el player ante un
	 * error de red (CLAUDE.md §6). No es Int.MAX_VALUE para que el contador
	 * siga siendo legible en los logs.
	 */
	const val MAX_RETRIES = 1_000_000
	const val RETRY_BACKOFF_BASE_MS = 500L
	const val RETRY_BACKOFF_MAX_MS = 8_000L

	// --- Diagnóstico --------------------------------------------------------
	/** Entradas del buffer circular de log, exportable a archivo desde la app. */
	const val LOG_RING_CAPACITY = 2_000
}
