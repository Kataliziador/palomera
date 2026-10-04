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

	/**
	 * **5 000 y no 15 000** (01-oct, con el visto bueno de Edgar).
	 *
	 * Los 15 000 son el valor que Media3 trae pensado para vídeo bajo demanda,
	 * donde el archivo entero está disponible. En directo **no cabe**: el
	 * cliente va entre 8 y 12 s por detrás del borde de emisión, así que nunca
	 * hay quince segundos de contenido publicado por delante de su posición.
	 * Pedirlo deja al reproductor viviendo en «me falta buffer», y cada bajada
	 * normal lo mete en estado de almacenamiento.
	 *
	 * Medido en `hbohd` el 01-oct, con el buffer oscilando entre 3.6 y 9.9 s:
	 *
	 * |                      | 15 000 | 5 000 |
	 * |---|---|---|
	 * | Rebuffers por minuto | 1.8    | 0.45  |
	 * | Ratio de rebuffer    | 0.72 % | 0.23 % |
	 *
	 * El ratio pasa de incumplir el objetivo de `CLAUDE.md` §10 a cumplirlo.
	 *
	 * **Lo que no era:** un defecto visible. Edgar no veía nada con ninguno de
	 * los dos valores —duraban 240 ms de media— y los contaba solo el log. Lo
	 * que se gana es una métrica limpia: el día que haya un rebuffer de verdad
	 * no quedará enterrado entre decenas de falsos.
	 */
	const val MIN_BUFFER_MS = 5_000
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

	// --- Extractor TS --------------------------------------------------------

	/**
	 * Cuántos paquetes TS busca el extractor antes de rendirse al establecer
	 * las referencias temporales.
	 *
	 * Media3 usa **600** (~113 kB). Si no encuentra timestamps en esa ventana,
	 * nunca establece el formato de vídeo — y ése es exactamente el síntoma de
	 * `estrellashd`, que almacena datos e inicializa decoders sin reportar
	 * nunca una resolución.
	 *
	 * **3000** (~564 kB) es el valor que usa un reproductor IPTV comercial
	 * analizado el 2026-09-26. Se tomó el número, no el código.
	 */
	const val TS_TIMESTAMP_SEARCH_PACKETS = 3_000

	/** El de Media3, para poder comparar desde el menú. */
	const val TS_TIMESTAMP_SEARCH_PACKETS_MEDIA3 = 600

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

	/**
	 * Tiempo mínimo entre dos vueltas al borde.
	 *
	 * Un salto tarda en surtir efecto. Sin esta espera, la siguiente
	 * actualización de playlist vuelve a leer el offset viejo y encadena
	 * saltos: medido, un salto cada 9 s que impedía arrancar el canal.
	 */
	const val LIVE_EDGE_RESYNC_COOLDOWN_MS = 30_000L

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
	/**
	 * Capacidad del buffer circular del log.
	 *
	 * Con el latido a 2 s son ~1 800 líneas por hora: 20 000 cubren una
	 * observación larga sin perder el principio, que es justo lo que hace
	 * falta cuando alguien dice «lo vi a los cuarenta minutos».
	 */
	const val LOG_RING_CAPACITY = 20_000

	/**
	 * Cada cuánto escribe el latido de diagnóstico.
	 *
	 * Dos segundos es lo bastante fino para situar un corte y lo bastante
	 * grueso para no pesar: una línea por latido en un hilo de fondo.
	 */
	const val HEARTBEAT_INTERVAL_MS = 2_000L

	// --- Valores de fábrica que el servidor puede cambiar --------------------
	//
	// Son la capa de abajo de tres: **fábrica → servidor → menú local**. El
	// servidor los pisa por modelo de caja (`deviceOverrides` del
	// `config.json`) y el menú de diagnóstico los pisa a los dos, pero solo si
	// alguien lo tocó a propósito.

	/** Ver `CLAUDE.md` §6: activo en todas, se excluye por configuración. */
	const val TUNNELING_DEFAULT = true

	/** Sin él `estrellashd` no arranca, y no perjudica a ningún otro canal. */
	const val TS_ALLOW_NON_IDR_DEFAULT = true

	const val TS_DETECT_ACCESS_UNITS_DEFAULT = false
	const val FORCE_SOFTWARE_VIDEO_DEFAULT = false

	// --- Vigilante de recuperación ------------------------------------------

	/**
	 * Cuánto se tolera almacenando sin que avance la posición antes de actuar.
	 *
	 * Diez segundos porque los rebuffers legítimos medidos se recuperan en
	 * dos: con este margen no se interrumpe ninguno sano. Por debajo, el
	 * remedio sería peor que la enfermedad — un salto innecesario reinicia el
	 * almacenamiento y alarga el corte que venía a cortar.
	 */
	const val WATCHDOG_STALL_MS = 10_000L

	/**
	 * Cuánto se espera un primer fotograma antes de dar el tunneled por malo.
	 *
	 * **Es el único fallo catastrófico que el vigilante no ve.** Su condición
	 * de «atascado» es que la posición no avance; cuando el tunneled de una
	 * caja no entrega imagen, el audio suena, la posición avanza con
	 * normalidad y la pantalla está negra. Desde dentro, todo va bien.
	 *
	 * La señal buena es `onRenderedFirstFrame`, que **sí llega en modo
	 * tunneled** —lo que se queda ciego ahí es el contador de fotogramas y
	 * `SurfaceFlinger`, que es otra cosa (`CLAUDE.md` §6)—. Reproduciendo, con
	 * la posición avanzando y sin primer fotograma, el decodificador nunca dio
	 * una imagen.
	 *
	 * Ocho segundos: un arranque sano da el primer fotograma entre 1,1 y 1,5 s
	 * medidos, y el objetivo de `CLAUDE.md` §10 para el primer canal es 4 s con
	 * 6 aceptables. Ocho queda por encima de cualquier arranque legítimo, aun
	 * con mala señal, y por debajo de lo que alguien aguanta mirando un negro.
	 */
	const val TUNNELED_NO_IMAGE_MS = 8_000L

	/**
	 * Si el rescate del tunneled está activo de fábrica.
	 *
	 * **Falso hasta que Edgar lo valide en pantalla** (`CLAUDE.md` §3): todo
	 * cambio de comportamiento del motor nace con el valor de fábrica igual al
	 * anterior. **Tiene que pasar a `true` antes del primer envío a Play**, que
	 * es cuando el riesgo que cubre deja de ser teórico: la caja mayoritaria
	 * del cliente solo se puede medir una vez publicada la app.
	 */
	const val RESCATE_TUNNELED_DEFAULT = false

	/**
	 * Espera entre escalones de recuperación.
	 *
	 * Un salto al borde tarda en surtir efecto; medir antes de que se note
	 * lleva a encadenar intentos y a no dejar que ninguno funcione.
	 */
	const val WATCHDOG_STEP_COOLDOWN_MS = 12_000L

	// --- Servidor de configuración ------------------------------------------

	/**
	 * Más corto que el de vídeo a propósito: si el servidor de configuración no
	 * responde, hay una caché en disco que sirve al instante. Hacer esperar al
	 * suscriptor por una lista que ya tenemos sería absurdo.
	 */
	/**
	 * Puerto del servicio de canales en el site del ISP.
	 *
	 * Vive en la misma máquina que Jellyfin pero es otro proceso, así que no
	 * puede compartir su puerto. Moverlo una vez desplegado obliga a tocar
	 * todos los sites a la vez que la APK, así que se cambia **antes** del
	 * primer despliegue o no se cambia.
	 */
	/**
	 * Aplicar `audioDefault` del `config.json` al sintonizar.
	 *
	 * **Activo de fábrica desde el 02-oct**, validado en pantalla por Edgar. El
	 * interruptor del menú se queda para reproducir el comportamiento anterior
	 * —arrancar con la pista que elija Media3— si alguna vez hace falta.
	 *
	 * Es un **valor por defecto, no una imposición**: en algunos satélites la
	 * etiqueta de idioma miente, y la elección manual del abonado manda sobre
	 * esto para el resto de la sintonía (`CLAUDE.md` §4, regla 6).
	 */
	const val AUDIO_DEFAULT_ENABLED = true

	const val CHANNEL_SERVICE_PORT = 8097

	/** Ruta del `config.json` dentro del servicio de canales. */
	const val CHANNEL_SERVICE_CONFIG_PATH = "/config.json"

	/**
	 * Cada cuánto se vuelve a bajar el `config.json` mientras la app sigue
	 * viva.
	 *
	 * **Antes no se volvía a bajar nunca.** La lista se pedía una vez por
	 * proceso, y una caja de salón puede tener la app abierta días: el
	 * operador quitaba un canal o ponía un aviso de mantenimiento y no llegaba
	 * a quien no reiniciase la app. Lo encontró el proyecto del servidor de
	 * canales, probándolo en un site.
	 *
	 * Quince minutos porque el documento son ~36 kB en la LAN del propio ISP:
	 * quinientas cajas son unos 70 MB al día contra una máquina que está en el
	 * mismo armario. Más corto no compra nada; más largo deja al operador sin
	 * saber cuándo llega lo que publicó.
	 */
	const val CONFIG_REFRESH_INTERVAL_MS = 15 * 60 * 1000L

	const val CONFIG_CONNECT_TIMEOUT_MS = 5_000
	const val CONFIG_READ_TIMEOUT_MS = 10_000

	/**
	 * Un `config.json` de 120 canales ronda los 40 kB. Si llega algo mucho
	 * mayor, la URL no apunta a lo que creemos —una página de error, un portal
	 * cautivo— y no se lee en memoria.
	 */
	const val CONFIG_MAX_BYTES = 2 * 1024 * 1024

	/**
	 * Topes de los `extras`: los pares etiqueta/valor que un ISP puede añadir
	 * a un canal —o a la raíz— y que la app pinta **sin saber qué son**
	 * (`CLAUDE.md` §6).
	 *
	 * Existen porque sin ellos un `config.json` decide cuánto ocupa la barra
	 * de canal, y la barra vive encima del vídeo. Seis entradas de 40
	 * caracteres caben; sesenta empujan la guía fuera de la pantalla y el
	 * abonado no tiene forma de arreglarlo.
	 *
	 * **Lo que sobra se recorta en silencio.** Rechazar el canal entero por un
	 * texto largo sería desproporcionado, y avisar en pantalla convertiría un
	 * descuido del operador en un problema del abonado.
	 */
	const val EXTRAS_MAX_ENTRIES = 6
	const val EXTRAS_MAX_LABEL_CHARS = 24
	const val EXTRAS_MAX_VALUE_CHARS = 40
}
