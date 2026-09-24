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

	// --- Playlist detenido ---------------------------------------------------

	/**
	 * Cuántas duraciones-objetivo espera Media3 antes de declarar el playlist
	 * atascado y lanzar error.
	 *
	 * El valor de Media3 es **3.5**, que con `TARGETDURATION 4` da unos 14 s.
	 * Está medido que cuando la fuente cae, Flussonic devuelve un playlist
	 * **vacío** y tarda **~15 s** en reconstruirlo. Es decir: el umbral por
	 * defecto salta justo antes de que el servidor se recupere, y convierte un
	 * chubasco en un error de reproducción.
	 *
	 * Eso choca de frente con la regla de `CLAUDE.md` §6: prohibido cerrar el
	 * player ante un error de red, porque un canal caído por lluvia es un
	 * estado temporal. Con 10 el margen sube a ~40 s, que cubre de sobra la
	 * reconstrucción del playlist.
	 */
	const val PLAYLIST_STUCK_COEFFICIENT = 10.0

	/** El de Media3, para poder comparar desde el menú. */
	const val PLAYLIST_STUCK_COEFFICIENT_MEDIA3 = 3.5

	// --- Live offset: el suelo que impone HLS --------------------------------

	/**
	 * Sin `EXT-X-SERVER-CONTROL` en el playlist, la especificación HLS fija el
	 * margen seguro en **3 × TARGETDURATION**, y Media3 no baja de ahí por
	 * mucho que se le pida.
	 *
	 * Medido el 2026-09-24: pidiendo 8000 ms el offset se estabiliza en 12380,
	 * y pidiendo 4000 se **aleja** hasta 11752. Con `TARGETDURATION 4` el suelo
	 * son 12 s, que es exactamente lo observado.
	 *
	 * Se usa para avisar cuando lo pedido es inalcanzable, en vez de dejar que
	 * alguien pierda un día preguntándose por qué el `config.json` no hace nada.
	 */
	const val HLS_HOLD_BACK_TARGET_DURATIONS = 3

	/**
	 * Fracción de la ventana DVR por encima de la cual el motor vuelve al borde
	 * de emisión.
	 *
	 * Medido el 2026-09-24: tras un corte de red de 15 s el reproductor se
	 * recupera, pero retoma **donde estaba**, no donde está el directo. El
	 * offset saltó de 10.5 s a **22.75 s** y **ahí se quedó**, dentro de una
	 * ventana de ~28 s.
	 *
	 * O sea: la recuperación funciona **una vez**. Cada corte consume margen de
	 * forma permanente, y el siguiente tira la posición fuera de la ventana. En
	 * una tarde de lluvia con cortes repetidos, el canal acaba roto.
	 *
	 * Por eso no basta con reaccionar a `BEHIND_LIVE_WINDOW` cuando ya ocurrió
	 * (`CLAUDE.md` §6): hay que volver al borde **antes** de llegar ahí.
	 */
	const val LIVE_EDGE_RESYNC_WINDOW_FRACTION = 0.5

	/** Margen mínimo para no resincronizar por ruido en ventanas pequeñas. */
	const val LIVE_EDGE_RESYNC_MIN_EXCESS_MS = 3_000L

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
