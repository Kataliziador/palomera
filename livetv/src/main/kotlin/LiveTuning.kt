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
	/**
	 * El valor de fábrica, y el último de la escalera.
	 *
	 * `PlanBuilder.offsetDe()` resuelve `servidor ?: canal ?: este`, así que
	 * esto solo gobierna un canal del que nadie dijo nada. **El valor bueno lo
	 * calcula el generador del `config.json` por canal**, desde la ventana DVR
	 * que le da la API de Flussonic, y eso es mejor que cualquier constante:
	 * la ventana depende del `segment_count` de cada stream.
	 *
	 * Aquí vivió un `TARGET_OFFSET_SATELLITE_MS = 12_000L` que **no usaba
	 * nadie**, y se retiró el 05-oct por dos motivos. El primero es que mentía:
	 * quien leyera este archivo creería que los satelitales van a 12 s cuando
	 * van a 8. El segundo es que además era un valor equivocado — medido el
	 * 03-oct sobre producción, los streams sin `segment_count` propio sirven
	 * 4 segmentos de ~5,5 s, o sea una ventana de ~22 s, y el techo de media
	 * ventana deja el offset por debajo de ~11 s (`CLAUDE.md` §6).
	 */
	const val TARGET_OFFSET_DEFAULT_MS = 8_000L

	/**
	 * A partir de esta fracción de la ventana DVR, el offset **pedido** se
	 * registra como sospechoso.
	 *
	 * **Avisa, no recorta**, y el nombre lo dice desde el 05-oct: antes se
	 * llamaba `…MAX_WINDOW_FRACTION` y su documentación decía «nunca puede
	 * pasar», que era falso. `offset.superaLaVentana` se escribe en el registro
	 * y a Media3 se le pasa lo pedido igual.
	 *
	 * Eso no es un descuido, porque **el que de verdad protege es otro**: la
	 * vuelta al borde ([LIVE_EDGE_RESYNC_WINDOW_FRACTION], misma fracción)
	 * actúa sobre el offset **real** y salta al directo antes de que la
	 * posición se salga. Lo pedido es una intención; lo real es lo que rompe el
	 * canal, y es sobre lo real donde hay que actuar.
	 *
	 * Lo que este aviso sí compra es saber **de quién es la culpa**: un offset
	 * pedido por encima del techo señala un `config.json` mal generado para ese
	 * stream, y eso se arregla en el generador, no en la caja.
	 *
	 * Recortar de verdad lo pedido cambiaría el comportamiento de
	 * reproducción, así que sería Modo A con validación en pantalla.
	 *
	 * Medido el 2026-09-22: con `segment_count 6`, `hbohd-70w` daba una ventana
	 * de 13.88 s. Un offset de 12 s dejaba 1.9 s antes de BEHIND_LIVE_WINDOW: el
	 * colchón pensado para la lluvia era justo lo que rompía el canal. Ver
	 * CHANNELS.md §2.bis.
	 */
	const val TARGET_OFFSET_WINDOW_WARN_FRACTION = 0.5

	/**
	 * Cuánto espera `prepare()` a que llegue la superficie antes de arrancar
	 * sin ella (`PREPARAR_CON_SUPERFICIE_DEFAULT`).
	 *
	 * Es una red, no un plazo de verdad: la superficie llega en el mismo
	 * fotograma de composición, unos milisegundos. Existe para que un cambio
	 * futuro en la interfaz que nunca la entregue dé un arranque igual al de
	 * antes en vez de una pantalla negra para siempre.
	 */
	const val ESPERA_SUPERFICIE_MS = 1_500L

	/**
	 * Preparar el reproductor **después** de tener la superficie.
	 *
	 * **Activo de fábrica desde el 10-oct**, validado en pantalla por Edgar.
	 * Lo anterior era preparar en cuanto se construye y aceptar la superficie
	 * en caliente, y eso lanzaba una aserción interna de Media3 —
	 * `Assertions.checkState` desde `ExoPlayerImplInternal.handleMessage`— que
	 * obligaba a preparar el reproductor una segunda vez entera. Medido con 17
	 * sintonías: **9 errores de 11** apagado, **0 de 6** encendido.
	 *
	 * **No es más rápido, y conviene no esperarlo:** el primer fotograma sale
	 * igual, 1 794 ms de mediana contra 1 784. Lo que se gana es que la caja
	 * deje de hacer un trabajo que no sirve y que el log deje de llevar un
	 * `ERROR` por canal tapando los de verdad. La estimación de que costaba
	 * 400-800 ms de zapeo salió de una sola muestra mal comparada y era falsa
	 * (pendiente 68).
	 *
	 * El interruptor se queda para reproducir el comportamiento anterior.
	 */
	const val PREPARAR_CON_SUPERFICIE_DEFAULT = true

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
	 * Cuánto tiene que **adelantar** un salto para que valga el corte que cuesta.
	 *
	 * Medido el 10-oct: de los nueve saltos de la sesión, tres iban de 26 843 a
	 * 19 267, o sea **7,6 s hacia atrás**, alejándose del directo. Pasaba porque
	 * el offset que los dispara está inflado por una lista congelada mientras la
	 * posición ya está a 6,4 s del final de la ventana: no hay a dónde ir. Los
	 * seis que sirven adelantaban +61 444 y +19 274.
	 *
	 * Tres segundos es algo más que un segmento de cualquiera de los canales
	 * medidos (2,0 a 2,8 s). Por debajo de un segmento el salto reinicia el
	 * almacenamiento para ganar menos de lo que tira.
	 */
	const val LIVE_EDGE_RESYNC_MIN_GAIN_MS = 3_000L

	/**
	 * Tiempo mínimo entre dos vueltas al borde.
	 *
	 * Un salto tarda en surtir efecto. Sin esta espera, la siguiente
	 * actualización de playlist vuelve a leer el offset viejo y encadena
	 * saltos: medido, un salto cada 9 s que impedía arrancar el canal.
	 */
	const val LIVE_EDGE_RESYNC_COOLDOWN_MS = 30_000L

	/**
	 * Volver al borde **calculando la posición**, en vez de pedirle al
	 * reproductor su «posición por defecto».
	 *
	 * `false` mantiene lo anterior: `seekToDefaultPosition()`. Medido en la
	 * noche del 10-oct, eso **no devuelve al directo**: la vuelta al borde
	 * disparó **547 veces en 9,3 h** y después de cada salto el offset seguía
	 * en 37 s con una ventana de 33,6. Y cada salto cuesta un rebuffer, así que
	 * **549 de los 579 cortes de esa noche los provocó este mecanismo**, no la
	 * red: la mediana fue de 142 ms, invisible en pantalla, pero el ratio de
	 * rebuffer subió a 1,12 % cuando la sesión limpia de 38 minutos había dado
	 * 0 %.
	 *
	 * Encendido, el salto va a `duración de la ventana − offset objetivo`, que
	 * es una posición que se puede comprobar en el log antes y después.
	 *
	 * **Invertido a `true` el 10-oct tras medirlo**, con 203 minutos de
	 * `mileniosd` de día: de 547 saltos en 9,3 h a **9 en 3,4 h**, y seis de
	 * los nueve dejan el directo en 17-19 s, comprobado con la columna
	 * `offset.trasElSalto` que se añadió para esto. Los tres que no sirven
	 * saltan **hacia atrás**, y de eso se ocupa
	 * [LIVE_EDGE_RESYNC_MIN_GAIN_MS].
	 */
	const val RESYNC_CALCULANDO_DEFAULT = true

	/**
	 * No volver al borde cuando el salto no adelantaría (pendiente 73).
	 *
	 * `false` mantiene lo anterior: saltar siempre que el offset pase del umbral,
	 * aunque el destino calculado esté **detrás** de donde ya está el
	 * reproductor. Medido el 10-oct, eso son 3 de los 19 cortes de la sesión, uno
	 * al principio de cada ráfaga, y ninguno recupera nada.
	 *
	 * Encendido, esa vez no se salta: se registra `offset.saltoNoAdelanta` y se
	 * vuelve a mirar al terminar el enfriamiento, que es exactamente cuando
	 * llegaba el salto que sí sirve. **La recuperación no se retrasa**; lo que se
	 * ahorra es el corte.
	 */
	const val RESYNC_SOLO_SI_ADELANTA_DEFAULT = false

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
	 * El rescate del tunneled, **activo de fábrica desde el 05-oct**, validado
	 * en pantalla por Edgar.
	 *
	 * Medido en la caja con la simulación puesta: de siete sesiones, la armada
	 * disparó el rescate y **las siete acabaron con imagen**
	 * (`tunRescate=1`, `conImagen=7`). Edgar lo miró un buen rato en
	 * `estrellashd` y el corte le pareció tolerable.
	 *
	 * **Por qué se enciende de fábrica y no se deja opcional:** el riesgo que
	 * cubre deja de ser teórico en el primer envío a Play. La caja mayoritaria
	 * del cliente solo se puede medir una vez publicada, el tunneled está
	 * activo de fábrica y se excluye por modelo —o sea, por conocimiento que de
	 * ella no tenemos—. Si lo implementa mal, el síntoma es pantalla negra con
	 * sonido, en todos los canales y en la caja que más abonados tiene. Esto lo
	 * convierte en un hipo de ocho segundos que se cura solo.
	 *
	 * El interruptor del menú se queda para reproducir el comportamiento
	 * anterior si alguna vez hace falta.
	 */
	const val RESCATE_TUNNELED_DEFAULT = true

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

	// --- Registro de sites --------------------------------------------------

	/**
	 * Diez segundos, pedidos por el proyecto del autorizador.
	 *
	 * Si no contesta, la app ya sabe degradarse: esperar más solo retrasa la
	 * entrada a TV en vivo sin cambiar el resultado.
	 */
	const val REGISTRY_CONNECT_TIMEOUT_MS = 10_000
	const val REGISTRY_READ_TIMEOUT_MS = 10_000

	/**
	 * Cuánto se sigue entrando después de que caduque la autorización, cuando
	 * el registro no contesta.
	 *
	 * **Catorce días, y empezar generoso a propósito** (`REGISTRY_SCHEMA.md`
	 * §4): cortarle a un cliente que paga por un problema de red nuestro es
	 * peor que tardar en cortarle a uno que no paga. La ventana que emite el
	 * registro ya se mide en semanas; esto es el colchón sobre ella.
	 */
	const val REGISTRY_GRACE_MS = 14L * 24 * 60 * 60 * 1000

	const val CONFIG_CONNECT_TIMEOUT_MS = 5_000
	const val CONFIG_READ_TIMEOUT_MS = 10_000

	/**
	 * Un `config.json` de 120 canales ronda los 40 kB. Si llega algo mucho
	 * mayor, la URL no apunta a lo que creemos —una página de error, un portal
	 * cautivo— y no se lee en memoria.
	 */
	const val CONFIG_MAX_BYTES = 2 * 1024 * 1024

	// --- libVLC (M6) ---------------------------------------------------------
	/**
	 * Colchón de red de libVLC, en ms.
	 *
	 * Es su equivalente al `minBuffer` de Media3, y por eso se le da el mismo
	 * valor: el canal es el mismo y la red es la misma LAN. Más alto solo
	 * retrasaría el arranque de un camino que ya se elige por necesidad.
	 */
	const val VLC_NETWORK_CACHING_MS = MIN_BUFFER_MS

	/**
	 * El filtro de desentrelazado.
	 *
	 * `yadif2x` interpola **y dobla la cadencia**, que es justo lo que le falta
	 * al caso que trae libVLC aquí: `estrellassd` se presentaba a 15 fps con
	 * peine, o sea mitad de cadencia *y* sin desentrelazar. `yadif` a secas
	 * arreglaría el peine y dejaría la mitad de los fotogramas.
	 *
	 * Cuesta CPU, y es el precio del único camino que desentrelaza: con
	 * decodificación por hardware el filtro no llega a ejecutarse
	 * (`LIVETV_ENGINE.md` §4.4).
	 */
	const val VLC_DEINTERLACE_MODE = "yadif2x"

	// --- EPG -----------------------------------------------------------------
	/**
	 * La guía **no viaja dentro del `config.json`** (`CLAUDE.md` §6): la lista
	 * cambia cuando alguien la cambia y la guía cambia cada día y pesa megas.
	 * Juntas, cada caja se bajaría la guía entera para comprobar si cambió un
	 * canal.
	 *
	 * Los tiempos de espera son más largos que los del `config.json` por eso
	 * mismo: no es un archivo de 40 kB, y una caja en una LAN cargada puede
	 * tardar.
	 */
	const val EPG_CONNECT_TIMEOUT_MS = 8_000
	const val EPG_READ_TIMEOUT_MS = 30_000

	/**
	 * Tope de la descarga del XMLTV.
	 *
	 * Medido el 05-oct sobre el site de pruebas: 7 canales con un día de
	 * programación ocupan 96 kB. Extrapolado a 120 canales y una semana, unos
	 * 12 MB. 24 deja holgura de sobra y sigue siendo un límite que distingue
	 * «guía grande» de «la URL no apunta a una guía».
	 *
	 * **Se descarga a fichero, no a memoria.** Esa es la diferencia de fondo
	 * con el `config.json`, y es lo que permite que el tope sea tan alto: lo
	 * que nunca cabe en una caja de 2 GB es el documento entero como `String`.
	 */
	const val EPG_MAX_BYTES = 24L * 1024 * 1024

	/** Si el `config.json` no dice `refreshMinutes`. Una guía diaria no cambia más. */
	const val EPG_REFRESH_DEFAULT_MINUTES = 6 * 60

	/**
	 * Cuánto futuro se conserva en memoria al interpretar la guía.
	 *
	 * La barra solo enseña «ahora» y «luego», pero guardar solo dos haría que
	 * cada cambio de programa exigiera releer el fichero. Seis horas caben de
	 * sobra y cubren una tarde entera sin volver a disco.
	 */
	const val EPG_WINDOW_HOURS = 6

	/**
	 * Tope de programas por canal, y de canales con programación.
	 *
	 * Un XMLTV mal generado —un bucle, un canal con un programa por minuto— no
	 * puede llenar la memoria de la caja. Pasado el tope se dejan de añadir y
	 * se registra: la barra sigue funcionando con lo que entró, que está
	 * ordenado por hora y por tanto es lo más próximo.
	 */
	const val EPG_MAX_PROGRAMAS_POR_CANAL = 64
	const val EPG_MAX_CANALES = 500

	/**
	 * Margen hacia atrás al quedarse con los programas.
	 *
	 * Sin él, un programa que empezó hace dos horas y dura tres se habría
	 * descartado y la barra diría que no hay nada dando. Con él entra, y
	 * `franja()` lo reconoce como el de ahora.
	 */
	const val EPG_MARGEN_PASADO_HORAS = 6

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
