# LIVETV_PLAN.md

Plan de trabajo. Dos vías con reglas distintas, según `CLAUDE.md` sección 3.

- **Vía M (motor):** una fase, una falla, un commit, una APK. Edgar valida en
  pantalla y declara el cierre. No se avanza sin su visto bueno.
- **Vía B (todo lo demás):** Claude Code trabaja de corrido, detecta fallas y
  las corrige solo.

Las dos vías avanzan en paralelo. La vía B nunca bloquea a la M ni al revés,
salvo las dependencias señaladas.

---

# VÍA M — Motor de reproducción

## M0 — Andamiaje

**Objetivo:** que exista el módulo y el enrutador sin romper nada.

- Fork de `jellyfin-androidtv` fijado en un **tag de release**, marcado
  `upstream-base`. Sin esa etiqueta, la verificación de la regla 6 no tiene
  contra qué comparar
- **APK de referencia del upstream sin tocar**, instalada y probada en la
  caja **antes** de escribir una línea. Es la línea base sin la cual "VOD sin
  regresión" no es verificable: hace falta un *antes*
- Módulo `livetv` con la interfaz `LiveEngine`
- `Media3LiveEngine` mínimo que reproduce una URL HLS fija
- Enrutador de una decisión en el código existente
- `LiveTuning.kt` con las constantes iniciales

**Prueba de Edgar:** reproducir `beisbol`; abrir una película y una serie de
Jellyfin y confirmar que se comportan igual **que en la APK de referencia**.

**Cierre:** canal reproduce, VOD sin regresión, versiones fijadas y anotadas
en `LIVETV_LOG.md`, y `git diff --stat upstream-base..HEAD -- ':!livetv/'`
muestra solo el enrutador.

---

## M1 — Diagnóstico y degradación — **cerrada 2026-10-09**

Se dio por cerrada de hecho hace semanas —M3 a M6 se apoyan en ella y ninguna
habría podido medirse sin el menú de degradación ni el log estructurado— y la
bitácora seguía diciendo «pendiente de validación» desde el 23-sep. Corregido el
11-oct. Lo único que sigue abierto de su alcance es el **inventario de códecs de
la ZTE B866V2** (pendiente 4), que necesita la caja físicamente, y el pendiente
8. El menú, desde el 11-oct, solo en compilaciones de depuración (§7 de
`LIVETV_ENGINE.md`).

**Objetivo:** poder ver qué está pasando y poder simular cajas peores. Sin
esto, las fases siguientes son adivinanza.

- Overlay de stats: canal, motor, decoder activo, **motivo de la decisión**,
  buffer, bitrate, frames perdidos, tiempo desde el último frame
- Logging estructurado con buffer circular exportable
- `DeviceCapabilities` con `MediaCodecList(ALL_CODECS)`, cacheado por
  `Build.FINGERPRINT`
- **Menú de degradación completo** (ver `LIVETV_ENGINE.md` §7)

**Prueba de Edgar:** el overlay muestra datos correctos; vetar el decoder
MPEG-2 desde el menú cambia visiblemente el comportamiento; el log se exporta.

**Cierre:** se puede describir una falla con datos, no con impresiones.

---

## M2 — Estabilidad base — **cerrada 2026-10-09**

Cerrada con el `targetOffsetMs` del pendiente 67: medido con 14 000, buffer p5
de 4,3 a 9,1 s y **cero cortes en 38 minutos**, con ratio y rebuffers/hora
cumpliendo §10 por primera vez en una sesión larga. Lo que queda de estabilidad
ya no es «un canal sano»: es lo que pasa cuando el origen falla, y eso es M7.

**Objetivo:** que un canal sano corra sin microcortes.

- `DefaultLoadControl` con los valores de `LIVETV_ENGINE.md` §5
- `connectTimeoutMs`, `readTimeoutMs`
- `LoadErrorHandlingPolicy` con backoff y reintentos casi infinitos
- `targetOffsetMs` por canal, **recortado en runtime** si excede la mitad de
  la ventana DVR medida del playlist (ver `CHANNELS.md` §2.bis)
- Umbral de playlist detenido ampliado en satelitales

**Prueba de Edgar:** 30 minutos de **`estrellassd-mp2v` (77W)** y 30 de
`hbohd-70w`, sin cortes ni saltos.

El orden sale de la prioridad de estabilidad (`CHANNELS.md` §0): **77W primero,
70W después, el resto al final**. `estrellassd` además es hoy el peor medido
—15 % de ratio de rebuffer— así que es el que de verdad pone a prueba la fase.

`beisbol` se sigue usando como **línea base de diagnóstico**, para separar lo
que es del motor de lo que es del canal, pero **no cuenta como criterio de
cierre**.

**Cierre:** rebuffers por hora < 1 en canal sano.

---

## M3 — Cascada de códecs

**Objetivo:** los seis canales con video y audio correctos.

Esta fase se redujo mucho al decidirse la normalización de audio a AAC: **no
hay extensión FFmpeg, ni NDK, ni cascada de audio.** Queda solo el video.

- `BackendDecider` con la cascada de video (HW → SW plataforma → libVLC)
- Normalización de idioma `spa`/`esp`/`esl`/`es` y selección de audio por
  idioma, nunca por PID ni por índice — es lo único que hay que resolver en
  audio, y es de motor, no de interfaz
- `videoCodec`/`audioCodec` desde el `config.json`, con reevaluación del plan
  si el stream desmiente al config
- Motivo de cada decisión visible en el overlay

**Prueba de Edgar:** los seis canales reproducen con audio, y en
`discoveryhd-70w` y `hbohd-70w` el selector distingue español de inglés.
Luego, con el menú de degradación, vetar el MPEG-2 de hardware y confirmar que
`estrellassd-mp2v` sigue funcionando por el camino de respaldo.

**Cierre:** los seis canales funcionan en modo normal **y** en modo degradado.

> **02-oct: la mitad normal está cerrada y la degradada está bloqueada por
> M6.** La caja de pruebas no tiene decoder de vídeo por software para H.264
> ni para MPEG-2 —solo `c2.android` de hevc, vp8, vp9, mpeg4 y h263—, así que
> vetar el decoder de hardware no cae al paso 2 de la cascada: se queda sin
> camino. **libVLC es el único respaldo que existe en esta caja.** Ver
> pendiente #26 de `LIVETV_LOG.md`.

**Dependencia de headend:** el audio AAC del canal mp2v. Mientras no se
aplique, ese canal sigue en MP2 y depende del decoder de hardware de la caja
de pruebas — que lo tiene, así que no bloquea, pero tampoco prueba nada.

---

## M4 — Flags de extractor en Amlogic

**Objetivo:** resolver la contradicción entre la medición y la experiencia de
campo.

- Flags configurables en caliente desde el menú
- Detección de SoC con override manual
- Para HLS, pasarlos vía `DefaultHlsExtractorFactory`

**Prueba de Edgar:** las cuatro combinaciones de los dos flags, con los cuatro
canales H.264, en la caja de pruebas.

**Cierre:** ninguna combinación produce artefactos ni impide el arranque. Se
documenta en `LIVETV_LOG.md` cuál queda por defecto para Amlogic.

---

## M5 — Render: aspecto y entrelazado — **cerrado 2026-10-08**

Declarado por Edgar en pantalla: *«no hay peines en ninguno, la imagen se ve
bien»* y *«llenar y zoom quitan las franjas»*.

**Lo que costó no fue el entrelazado: fue descubrir que el SAR nunca se había
aplicado.** Los modos de imagen no hacían nada porque escalar un `SurfaceView`
no llega a la superficie, y debajo había algo peor — nadie fijaba el modo de
escalado del códec, así que los dos canales anamórficos llevaban saliendo
deformados desde M2. Se arregló dimensionando la superficie, y se validó.

**Con una excepción, y es la que abre M6:** `estrellassd` se presenta a **15
fps**, la mitad de su cadencia, con peine. Medido y acotado — no es el render,
no es la app, no es el stream: es el único decodificador MPEG-2 que tiene la
caja. Es literalmente el caso que §6 le reserva a libVLC. El plan admite cerrar
así —«o con la limitación documentada por modelo y el nivel correspondiente
implementado»— y el nivel es M6.

Las dos decisiones que colgaban de esta fase también quedaron tomadas: el
**tunneled se queda** (0 rebuffers en 20 min con él, 15 en 1,6 min sin él) y el
**pulldown se acepta**, porque el tunneled es justo lo que lo mitiga.

---

### Lo que pedía la fase

**Objetivo:** que la imagen se vea bien, no solo que reproduzca.

- Respetar el `pixelWidthHeightRatio`
- Modos de ajuste manual
- Recalcular el resize mode en `onVideoSizeChanged`
- Implementar los niveles de la cascada de entrelazado según lo que se observe

**Prueba de Edgar:** los **dos** canales anamórficos con proporciones
correctas — `estrellashd` (4:3) y `estrellassd` (20:17); en los otros cuatro,
1:1, respetar el SAR no cambia nada. Los **cuatro** entrelazados sin peine en
escenas con movimiento lateral, cubriendo los dos órdenes de campo
(`discoveryhd` es `tt`, `hbohd` es `bb`) y los dos tipos de codificación.

**Cierre:** sin peine visible en la caja de pruebas, o con la limitación
documentada por modelo y el nivel correspondiente implementado.

---

## M6 — Backend libVLC — **cerrado 2026-10-09**

Declarado por Edgar: *«probé los cinco modos, todos se ven correctos»*, sin
peine y con cadencia normal.

Entró porque hubo un caso medido, no por si acaso: `estrellassd` se presentaba
a 15 fps con peine y la caja no tiene otro decodificador de MPEG-2. Con libVLC
—software forzado y yadif2x— se ve bien y cuesta menos del 16 % de CPU.

**Lo que costó no fue el motor: fueron seis cosas que no hacían lo que decían.**
El veto de decoders del menú, que nadie leía. `hasVlcBackend`, que dependía de
quién preguntara primero. El canal publicado como progresivo siendo 480i. El
latido, que vivía solo en el otro motor. El `audioMime`, que encendía un aviso
falso. Y el SAR, que libVLC aplicaba dos veces.

**Precio:** la descarga por caja pasa de ~20 MB a ~47 MB.

### Lo que queda abierto, y conviene no olvidarlo

El respaldo existe y está probado, pero **todavía no se dispara solo en casa de
un abonado**. La regla que lo activa —nivel 2 de `LIVETV_ENGINE.md` §4.4, SD
entrelazado sin VPU que desentrelace— ya está implementada y gobernada por
`vpuDeinterlaces: false` en los `deviceOverrides`. Le falta el otro dato:

- **`interlaced` llega mal para MPEG-2** (pendiente 53). Flussonic devuelve
  `field_order=unknown` y el generador publica `false`, así que la regla no
  reconoce el canal como entrelazado. Hoy se suple con «Forzar
  desentrelazado» del menú, que es un interruptor de diagnóstico, no una
  solución: hace falta que el operador pueda corregir ese campo en el lineup.

Hasta entonces, libVLC es una capacidad demostrada y una palanca manual.

---

### Lo que pedía la fase

**Solo si M3 o M5 lo requieren.** Si la caja de pruebas cubre todo por
hardware y desentrelaza, esta fase se pospone para cuando aparezca la primera
caja que la necesite.

- `VlcLiveEngine` implementando la misma interfaz
- Decodificación por software forzada cuando haya que desentrelazar
- Selección automática según la cascada
- Verificar `armeabi-v7a` y `arm64-v8a` en el AAB

**Prueba de Edgar:** con el MPEG-2 de hardware vetado, el canal mp2v
reproduce por libVLC con audio y sin peine. El cambio de backend es invisible.

**Cierre:** el camino de respaldo funciona de verdad, no solo en teoría.

---

## M7 — Resiliencia ante lluvia — **cerrada 2026-10-19**

**Objetivo:** que un corte no requiera intervención del suscriptor.

**Lo que ya está y está validado en pantalla:** la escalera del vigilante
(pendiente 69, los dos cortes provocados por Edgar el 09-oct: 16 s por el
escalón 2 reiniciando el canal en Flussonic, 26 s por el escalón 3 con el cable
fuera), el playlist vacío como espera, el mapeo de errores y la regla de no
vetar decoders durante corrupción.

**Lo que faltaba, y es lo que de verdad se parece a la lluvia** —`tc netem
loss 5% delay 100ms 20ms`, un corte de **5 minutos** y el golden sample de
lluvia real en bucle—, **se hizo el 19-oct y Edgar lo validó en pantalla.** La
entrada de cierre está en `LIVETV_LOG.md`; el resumen:

- **corte de 5 minutos, dos veces, cifras idénticas:** 284 s atascado, escalón
  11 de la escalera, imagen **7 s** después de volver la señal. En pantalla:
  congela, avisa, no pide nada al abonado y vuelve sola;
- **golden sample en los dos motores.** Media3 se ve con bloques de basura y
  sigue; libVLC se pixelea con el audio cortado —`playing silence`, buffers
  tirados— pero **sin desfase** y sin pararse;
- **tramo IP degradado:** el 5 % que pedía el plan **no muerde** (1 segmento
  perdido cada 2,5 min); al 30 % el motor reintenta con `HttpDataSourceException`
  y no se para ni una vez.

**El pendiente 12 sale de esta fase** y pasa a su propio renglón: la lluvia
rompe la señal, y el 12 es que el stream cambie de forma. Estaban juntos por
historia, no por parentesco. Sigue sin medir.

**El bloqueo que lo tenía parado se levantó el 14-oct, y no era de medida sino
de dónde ejecutar.** Las tres pruebas pedían degradar el headend, y el de
pruebas es **compartido y de solo lectura** (`CLAUDE.md` §9): aplicarle netem
habría roto el trabajo de otros, y la caja no es nuestra para rootear. La
salida es ponerse en medio, y ya hay dos herramientas para ello:

- **`herramientas/lluvia.py`** — una pasarela que sirve un `config.json` cuyos
  canales apuntan a ella y reenvía al headend perdiendo, retrasando, truncando
  o cortando lo que se le pida en caliente. La caja se apunta con
  `configUrlOverride`, que ya existía para esto. Comprobada mando a mando el
  14-oct: limpia pasa `ffprobe` entera, al 50 % de pérdida fallan 10 de 20
  peticiones, el retardo sale donde se pide, el truncado deja a `curl` con
  salida 18 y el corte da *connection reset* hasta que vence. **No es netem**:
  tira peticiones HTTP, no paquetes IP, y eso está escrito en su cabecera para
  que nadie confunda lo que mide. A cambio deja rastro de cada decisión.
- **`herramientas/corromper-ts.py`** — el golden sample, por fin. Rompe un TS
  limpio como lo rompe la banda Ku: **paquetes enteros y a ráfagas**, porque un
  desvanecimiento se lleva docenas seguidos y un 1 % uniforme es una prueba más
  fácil que la realidad; más un pequeño porcentaje de payload alterado con TEI,
  que es el otro fallo —el decoder pinta basura en vez de quedarse sin datos—.
  Respeta PAT y los PMT, que localiza leyendo el PAT, salvo con `--sin-piedad`.
  Medido el 14-oct sobre 60 s de `estrellassd`: el limpio decodifica con **0
  errores** y el de lluvia con **345** del tipo correcto —`MVs not available`,
  `ac-tex damaged`, `slice mismatch`—, y sigue siendo un stream válido de 60 s,
  así que se sirve en bucle como directo. Con semilla fija, **dos versiones de
  la app se comparan contra exactamente la misma avería**.

Las grabaciones viven **fuera del repositorio**, en `/home/proyectos/muestras-lluvia/`:
son emisión ajena y este repositorio va a ser público para cumplir la oferta de
código de la GPL-2.0 (`PLAY.md` §5). Lo que se versiona es cómo rehacerlas.

Queda ejecutarlo contra la caja, que es Modo A y es de Edgar; hoy está ocupada
con el remojo largo de M8.

El bucle de inanición del **75 queda cerrado el 12-oct** y encendido de fábrica:
7 h 22 min de `mileniosd` dieron 6 cortes contra 35 y **0,26 % de imagen parada
contra 0,88 %**, con las tres métricas de §10 en verde por primera vez en una
sesión larga. Pero esa sesión dejó en su lugar un pendiente mayor, el **74**
medido: el objetivo de latencia no se mantiene solo, así que la sesión entera
depende de la meseta donde cayó el último salto —entre 3,6 y 14,8 s de buffer en
la misma sesión— y eso es lo que decide si un parón del origen se traga o se ve.

- `LiveWatchdog` con la escalera de recuperación
- **Tratar el playlist vacío como estado de espera, nunca como error.**
  Medido: cuando la fuente cae, Flussonic devuelve un playlist sin segmentos
  y deja las peticiones colgadas
- Sobrevivir a cambios en el número de pistas a mitad de stream
- Mapeo de `PlaybackException` por código
- Mensajes que distinguen señal de conexión
- Regla de no vetar decoders por errores durante corrupción

**Pruebas de Edgar:**
- Desconectar la red 10 s, 60 s, 5 min
- Cortar la entrada en Astra
- `tc qdisc add dev eth0 root netem loss 5% delay 100ms 20ms`
- Reproducir en bucle un golden sample con lluvia simulada

**Cierre:** el cliente se recupera solo en todos los casos, sin tocar el
control, y nunca queda en estado de error permanente.

**Nota medida:** cuando la fuente cae en Flussonic, el playlist tarda ~15 s en
reconstruirse. El cliente no puede ser más rápido que eso. El objetivo de
< 8 s depende de bajar el `source_timeout` y poner el letrero de respaldo en
el headend (pendientes 6 y 7 de `LIVETV_LOG.md`).

---

## M8 — Maratón y zapping — **cerrada 2026-10-25**

**Objetivo:** sobrevivir a la operación real.

**Cerrada el 25-oct con el visto bueno de Edgar**, que es lo que cierra una
fase de Modo A (`CLAUDE.md` §3). Las tres medidas, y de dónde sale cada una:

| criterio | medida | de dónde |
|---|---|---|
| vivo a las 48 h | **51,0 h** con un solo pid: 26 cortes (**0,5/h**), ratio **0,03 %**, primer fotograma 1 496 ms, cuatro atascos del vigilante recuperados solos | remojo de la release, 22-oct, por `adb` |
| zapping p50 < 2 s | **p50 583 ms · p95 819 ms · máx 1 094 ms**, 42 sintonías | **Edgar con el mando**, 25-oct, release `0.1.0-rc.3` |
| memoria estable | binders 20 → 97 durante el zapeo y **de vuelta a 63**, planos cuatro minutos; heap nativo asentado en ~19 MB; el java sube y lo recoge el recolector | mismo día, `dumpsys meminfo` cada 30 s |

**Los 50 cambios de canal los hizo Edgar con el control, no `adb`**, que era
justo lo que faltaba el 22-oct. Súmale los 450 ms de `ZAP_SETTLE_MS` y lo que
percibe el abonado es ~1,0 s de mediana, contra los 2 s de objetivo de §10.

**Y la serie destapó el hueco que quedaba y lo tapó:** las dos medidas de
zapping anteriores salían de un canal fijo, así que nadie había mirado la
memoria **zapeando en serie**. Se midió, sube mientras se zapea y suelta al
parar. Trece minutos de observación, una sesión, 42 sintonías y **una sola
caja** (§9): no descarta un goteo de días, sí lo que había que decidir.

**De paso quedó probado el arreglo del pendiente 82 en uso real:** nueve
pasadas por canales caídos —cinco por `foxny`, cuatro por `universal`— y las
ocho que tuvieron continuación salieron con la flecha, entre 1,7 y 3,2 s. Cero
callejones. La novena es la última pulsación de la serie.

**Lo que esta fase NO cierra, y conviene no leerlo de más:** el remojo de 51 h
es de un canal quieto y el zapeo es de dos minutos. No hay ninguna medida de
48 h **zapeando**, ni de ninguna caja que no sea la Skyworth.

**Estado anterior, al 15-oct:** la sesión más larga son **36,1 h** —remojo de
`mileniosd` del 14 al 15-oct, un solo pid, 2,1 cortes/h y 0,31 % de ratio—, o sea
ya por encima de las 24 h «aceptable» de §10 y por debajo de las 48 h objetivo.
**Pero es la APK de depuración**, con el anillo de diagnóstico y un `logcat`
atado 36 h, así que el número que cuenta para §10 sale de un remojo de la
**release**. El zapping sigue con pocas muestras —4 sintonías con el primer
fotograma entre 1 651 y 1 931 ms—. Nada de esto está cerrado.

- ~~Reconexión programada antes del rollover de PTS (~26.5 h)~~ **probablemente
  no hay que escribirla.** Leído el 11-oct en la fuente de Media3 1.8.0:
  `TimestampAdjuster` dice «taking into account timestamp rollover», lleva
  `MAX_PTS_PLUS_ONE = 0x200000000L` —los 33 bits— y estima el número de vueltas
  en `adjustTsTimestamp`; el camino HLS lo usa vía `TimestampAdjusterProvider`,
  que entrega un `TimestampAdjuster(MODE_SHARED)` por secuencia de
  discontinuidad. El remojo del 15-oct **cruzó las 26,5 h** y siguió 9,5 h más
  sin reiniciar, pero **nada en el log registra PTS**: un vuelco limpio y un
  vuelco que no ocurrió se escriben igual, así que eso era **inferido**.
  **Instrumento puesto el 15-oct:** el PTS no se lee desde la API —
  `TimestampAdjuster` lo lleva por dentro y no publica su estado—, pero un
  vuelco mal manejado tiene una sola firma observable, un salto de posición de
  `MS_DE_UN_VUELCO_DE_PTS` (95 443 717 ms ≈ 26,5 h), y
  `onPositionDiscontinuity` ya lo vigila con `pts.posibleVuelco`. **Cerrado con
  dato el 22-oct:** el remojo de release de 51,0 h cruzó las 26,5 h y
  `pts.posibleVuelco` salió **cero veces**. Y el instrumento no estaba ciego:
  el mismo callback clasificó **25 discontinuidades** como `salto.aterrizaje`
  en esas horas, así que se estaba ejecutando y ninguna tenía la firma del
  vuelco de 33 bits. Lo que esto sigue **sin** decir es que el vuelco ocurriera:
  el PTS crudo de Flussonic no se lee desde la API. Es la ausencia medida del
  síntoma, no la presencia medida del suceso
- Sin fugas de memoria en zapping repetido. **En sesión continua, medido dos
  veces** (15-oct y 22-oct): el heap java se **aplana en la hora 12** —el anillo
  de diagnóstico llenándose, techo por construcción— y el nativo acumula
  **+1,72 MB/h** de asignaciones vivas y frías, misma pendiente que la serie
  anterior y con la proyección de las 48 h cumplida al kilobyte (pendiente 79).
  **El zapeo repetido sigue sin medir:** las dos series son de un canal fijo
- ~~Medir el zapping real~~ **hecho el 20-oct**: 56 cambios con las flechas
  dentro del reproductor, p50 1 459 ms y p95 1 744 ms sobre los 49 de Media3.
  Los 7 de libVLC no son comparables y de ahí salió el pendiente 81

~~**Pruebas de Edgar:** un canal 48 h continuas; 50 cambios de canal
seguidos.~~ **Hechas.** Los 50 cambios, con el mando el 25-oct. Las 48 h, por
medida del 22-oct: Edgar decidió el 25-oct no repetir el remojo —«tomó
demasiado tiempo»— y dar por buena la de 51 h, que es la misma release.

~~**Cierre:** vivo a las 48 h, memoria estable, zapping p50 < 2 s.~~
**Los tres cumplidos**, con la tabla de arriba.

---

# VÍA B — Trabajo autónomo

Claude Code avanza solo, corrige lo que encuentre y reporta al final de cada
bloque. Sin esperas.

## B1 — Cliente del servidor de configuración
`ConfigRepository` que consume el `config.json`, con caché local,
configuración de fábrica y versión de esquema. **Dependencia:** M0.

## B2 — Lista de canales y navegación
**Diseñar para ~120 canales**, no para los 6 de prueba. Grupos, navegación por
D-pad, último canal, favoritos, entrada directa por número y búsqueda. Una
lista plana de 120 no es navegable con control remoto. **Dependencia:** B1.

~~Búsqueda~~ **hecha el 03-oct.** `ChannelSearch` en el módulo, con sus
pruebas: casa contra nombre y `aliases`, ignora acentos, mayúsculas, signos y
espacios, y lo exacto gana a lo que empieza igual. Los dígitos buscan el
número. Se abre con «Buscar canal» y filtra mientras se escribe.

## B3 — Sección LiveTV, reescrita desde cero
La sección de canales en vivo se **reescribe entera**, y **reemplaza** la
sección LiveTV de Jellyfin: la fila del inicio apunta a la nueva y la vieja
queda inalcanzable (`CLAUDE.md` §4, regla 5).

**Lleva el mismo diseño que el resto de la app**, no uno propio: `JellyfinTheme`
y los componentes de `ui/base/`, para que Inicio, Buscar y TV en vivo se vean
como una sola aplicación. Por eso su código vive en `app/.../ui/livetv/` y no
en el módulo.

La navegación de películas y series se conserva tal cual. **Dependencia:** B2.

## B8 — EPG — **cerrado 2026-10-05**
Consumo del XMLTV, programa actual y siguiente en la barra.

**Hecho y medido en la caja:** `epg.interpretada canales=7 programas=59
descartados=0 fueraDeVentana=142 canalesDesconocidos=0`, y en pantalla HBO HD
con «Lluvia de hamburguesas 2», 16:47–18:26, «quedan 31 min», barra de progreso
y «Luego Kung Fu Panda 2». Las horas coinciden con las `21:47–23:26 +0000` que
emite el servidor, convertidas al huso de la caja.

**Lo que no entra y queda nombrado:** la guía **en rejilla** —la parrilla de
varias horas por varios canales— no se ha hecho. La barra enseña ahora y luego,
que es lo que el plan pedía; una rejilla es otra pantalla.

**Y desde el 20-oct la programación también se ve en la sección**, no solo
sobre el vídeo: cada tarjeta lleva una línea de progreso en el borde —gráfica,
no texto, que es lo único que se lee a tres metros en 168 dp— y encima de la
guía va una línea con el título, la hora y lo que queda del canal que tiene el
foco, con «Luego». Medido en el emulador de TV, que tiene el mismo lienzo que
la caja (1920×1080 a 320 dpi): `epg.interpretada canales=7 programas=66` y la
línea siguiendo al foco por cuatro canales. Sigue sin ser la rejilla.

**Reescrita el 02-oct.** El muro de este bloque no era consumir la guía sino
producirla, y con el servidor de canales+EPG en cada site (`CLAUDE.md` §1) esa
mitad **cambia de lado**: generar el XMLTV desde el EIT de Astra es trabajo del
headend y de la tubería de distribución, como Katalog con el VOD. No es trabajo
de la app.

Lo que queda aquí es tratable y ya tiene su hueco esperando —hoy la barra de
canal lleva `DummyEpg.kt`—:

- El `config.json` lleva **puntero** al XMLTV y su cadencia, no la guía dentro
- Descarga, interpretación y caché propios, con su propio ritmo
- Programa actual y siguiente en la barra; guía por canal
- **Un site sin EPG es normal**, no un error: la barra se degrada sin guía

Sigue siendo la última por decisión del cliente: la prioridad es el motor y que
la app funcione completa sin guía.

### Lo que cuenta y pide el proyecto del servidor de canales

Entregado el 04-oct en `/home/proyectos/servidor-canales/docs/PETICION_APK.md`.
El contrato no cambia. La otra mitad de este bloque **ya está hecha allí**: el
servicio descarga la guía, la filtra y la sirve con nuestros `id`.

- **El `epg.xml` ya se sirve** en `http://<host>:8097/epg.xml` y se anuncia con
  `epg: { url, refreshMinutes: 1440 }`. El §2 de la petición dice exactamente
  qué va a encontrar B8: XMLTV con el `id` del canal como `channel id`, horas
  siempre `YYYYMMDDHHMMSS +0000`, 2–3 días por delante, sin `icon`, y **no
  todos los canales publicados** tienen guía
- **El puntero aparece y desaparece.** Solo se emite mientras hay guía al día;
  un `config.json` posterior **sin** `epg` significa «ya no hay guía»
- **Piden que la caja guarde la guía y respete `refreshMinutes`**: una petición
  al día, no una por entrada a la sección (§3.1)
- **Preguntan si la app acepta `Content-Encoding: gzip`**; no lo activarán
  hasta que este proyecto diga que sí (§3.2). Ojo con el límite de 2 MB del
  `config.json`, que se mide sobre la longitud declarada
- De paso, lo medido allí con la caja: los streams con 4 segmentos de ~5,6 s
  se cortan —11 rebuffers en 70 s, 0 con 12 × 2,8 s—; no piden cambiar la app
  (§4.1)

## B4 — Overlays e interfaz
Barra de info de canal, selector de pista de audio, selector de aspecto,
mensajes de estado. La **interfaz** es vía B; cómo el motor aplica esos
cambios es vía M.

**Ojo con el selector de audio:** la normalización de `spa`/`esp`/`esl`/`es`
**no vive aquí**. Subió a M3 al descubrirse que Flussonic normaliza los PIDs y
el idioma es la única clave de selección que queda. B4 dibuja el selector; M3
decide qué pista es cuál.

~~**Qué están dando, en la sección**~~ **hecho el 20-oct.** La guía ya se
descargaba y se interpretaba desde B8, y solo la veía quien ya estaba dentro de
un canal: la lista no decía nada de la programación. Con ~120 canales eso
convierte la sección en un listín. Ahora la dice una vez —arriba, para el canal
con el foco— y la insinúa en cada tarjeta con una línea de progreso.

~~**La interfaz de la sección: sencilla, funcional y estética**~~ **hecho el
21-oct**, en seis commits y con el emulador `api22tv` como instrumento. La
primera tarjeta pasa del dp **264** de 540 al **202**, de fila y media a dos
filas completas; sin logo manda el número del canal, que es único, en vez de
tres iniciales que medio lineup comparte; los 46 literales de color pasan a una
paleta con nombres; el acento del ISP deja los títulos de grupo y se queda en
las barras de progreso; y vacío, error y carga dejan de ser una línea gris en
una esquina. Entrada completa en `LIVETV_LOG.md`, con los seis caminos de foco
medidos.

**Lo que esa revisión dejó fuera a propósito:** la parrilla —otra pantalla, ya
diseñada en el sistema de diseño del proyecto: 4 dp por minuto, columna de
canales de 132 dp, línea de ahora—, los 95 dp de la barra principal, que es
compartida con Inicio y Buscar, y cualquier cosa que pase encima de vídeo, que
es Modo A.

~~**Los `extras` entran aquí** (`CLAUDE.md` §6)~~ **hecho el 03-oct.** La barra
pinta los del canal bajo «Luego» y la cabecera de la sección los de la raíz,
sin saber qué son, con topes en `LiveTuning` —6 entradas, 24 y 40 caracteres—
y recorte silencioso. Las tarjetas de la guía **no** los enseñan: con 120
canales una tarjeta tiene que leerse de un vistazo.

## B5 — Marca dinámica
**Reescrita el 02-oct: ya no hay flavors.** Una sola APK y una sola ficha de
Play nuestra; la marca sale del servidor de canales del site (`CLAUDE.md` §8).

- ~~Marca desde el servicio del site: `displayName`, `accent`, `logoUrl`~~
  **hecho el 02-oct**, con validación de contraste 3:1 y respaldo por ranura
- `BrandResolver`: la marca del **registro** pisa a la del site (B9)
- Ranuras fijas (`displayName`, `accent`, `logo`) con validación
  de contraste y caída al valor neutro
- ~~Marca neutra **diseñada**, que es lo que se ve antes de entrar y sin red~~
  **hecha el 03-oct**: logotipo monocromo —arco y antepecho— sobre el negro de
  la pantalla de arranque. Monocromo a propósito, para no competir con el
  acento que cada ISP ponga encima
- Caché de marca y de logo, indexada por servidor
- ~~Renombrado de la app: fuera el nombre y el logo de Jellyfin~~ **hecho el
  03-oct**, y **rehecho el 27-oct**: se llamó Palco hasta que se comprobó que
  estaba tomado en Play y en la App Store. Hoy se llama **Palomera**;
  `applicationId` `mx.kataliziador.palomera`. Queda comprobar el nombre en IMPI
  (clases 38 y 41), en la Play Console y en Samsung, LG y Roku — **y rehacer el
  dibujo**, que todavía deletrea PALCO
- ~~Pantalla de información: "no afiliado al proyecto Jellyfin" + GPL-2.0~~
  **hecho el 03-oct.** La LGPL-2.1 se añade cuando entre libVLC en M6: hoy no
  viaja en el APK y declararla sería falso

**Cierre:** dos servidores de canales distintos visten la app distinto sin
recompilar, y sin ninguno la app arranca con la marca neutra y la lista de
`BuildConfig`.

## B9 — Registro de sites
Consulta al registro antes de derivar por convención (`REGISTRY_SCHEMA.md`).

**Los cinco puntos están construidos.** Llevaban sin marcar y el plan pedía
trabajo hecho, que cuesta un día a quien lo lea; se verificó uno a uno contra el
código el 15-oct y se anota la línea de cada uno para que se pueda refutar.

- ~~Normalización del host igual que en `ServiceUrl`~~ `SiteAuthorization.kt:15`
  (la clave es el host normalizado) y `ServiceUrl.kt:93`, que explica por qué los
  dos lados tienen que normalizar igual
- ~~Autorización guardada por host, con su caducidad~~ `SiteAuthorization.kt:23`,
  `untilEpochMs`, y la regla escrita en la línea 21: antes se descarta la
  respuesta entera que guardar una autorización sin caducidad
- ~~**Degradación:** sin respuesta y vigente se entra; caducada hay gracia;
  pasada la gracia se cierra **explicando**~~ `Acceso.kt:73` y `:78`, las dos
  ramas con su motivo, y `DecisorDeAccesoTest.kt:69` y `:75` que las prueban
- ~~La puerta cubre **solo** la sección de canales; el VOD no se toca~~
  `LiveTvFragment.kt:464`: la pantalla de «no autorizado» es de la sección, y el
  módulo no depende de `app` (§4.1), así que no tiene por dónde tocar el VOD
- ~~`channelsUrl` y `brand` del registro pisan a la convención y al site~~
  `LiveTvCatalog.kt:200`, donde el `channelsUrl` del registro va delante de la
  derivación, y `:286`, que registra «registro» como origen de la decisión

**Dependencia:** B1. **Criterio de cierre:** dar de alta un host en el registro
y que una caja entre sin tocar la app; quitarlo y que se cierre con su mensaje
al caducar. Y con el registro apagado, una caja ya autorizada sigue entrando.
**El criterio sigue abierto**: eso se comprueba con cajas en campo, no aquí.

### Lo que pide el proyecto del autorizador

Entregado el 02-oct en `/home/proyectos/autorizador/docs/PETICION_APK.md`. El
contrato no cambia; esto es lo que `INTEGRACION_REGISTRO.md` §7 dejaba abierto.

- `REGISTRY_URL` en `BuildConfig`: **dominio, no IP** —mudar el servidor tiene
  que ser un registro DNS, no una APK—, HTTPS sin puerto, y el código compone
  `+ "/v1/resolve"`. Más el override del menú para apuntar a un registro de
  pruebas.
- Tiempos de ~10 s: si no contesta, la app ya sabe degradarse y esperar más solo
  retrasa la entrada a TV en vivo.
- **Confianza en ISRG Root X1/X2 incluida en la app, solo para el registro.**
  `minSdk` es **21** y Android no trae esa raíz hasta la 7.1.1, así que una caja
  de Android 5.0–7.1.0 **nunca conseguiría autorización** — y no daría error
  visible: simplemente no entraría nunca a TV en vivo, que es el reporte más
  caro de diagnosticar. `network_security_config.xml` no vale: Android lo ignora
  por debajo de API 24, justo donde está el problema. Tiene que ser un
  `X509TrustManager` que confíe en **el sistema o en las incluidas**, nunca uno
  que acepte todo.
- ~~Lo mismo para el cliente OkHttp de Coil, o la autorización funcionaría y el
  logo de la marca no.~~ **Hecho**, y no ampliando el `ImageLoader` global —que
  era la tentación y habría dado confianza ampliada a todas las imágenes de la
  app—: `LogoLoader.kt` construye uno aparte, con `RegistryTrust` y solo para el
  logo de la marca.

**Añadido desde este lado, que allí no podían ver:** `ConfigRepository` usa
`HttpURLConnection`, así que **el mismo fallo alcanza al `config.json`** si un
site lo sirve por HTTPS con Let's Encrypt. En la LAN lo normal será `http`, pero
un ISP que publique su Jellyfin hacia fuera se lleva el problema entero. El
arreglo debe cubrir las dos conexiones, no solo la del registro.

## B6 — Telemetría
**Dos capas que no se mezclan** (`CLAUDE.md` §8):

- ~~**Diagnóstico técnico**, anónimo y siempre activo~~ **hecho el 05-oct.**
  Modelo, ABIs, RAM, **inventario de decoders de vídeo**, sesiones, ms al
  primer fotograma, rebuffers, rescates del tunneled y fallos por causa y por
  canal. Viaja montado en la consulta al registro, no en un servicio nuevo.
  **El códec de audio recibido también está** (`a968e58`, 11-oct): el motor lo
  observa en `Media3LiveEngine.kt:1731` y lo acumula por canal en
  `audioSinNormalizar`, que es como se detecta un site con el AAC mal
  configurado (§2 defensa 2). Esta línea decía que faltaba y estaba
  desactualizada; corregida el 15-oct
- ~~**Y el registro lo guarda**~~ **hecho el 15-oct, en el otro repositorio.**
  Hasta ese día la APK mandaba el inventario de decoders y el diagnóstico
  entero y **el autorizador los descartaba en silencio**: su modelo de entrada
  declaraba tres campos de `device` y Pydantic tira lo que no declara. Diez días
  enviando a la basura justo el dato que justifica B6. Ver
  `autorizador` PR #10 y su `docs/BITACORA.md`
- **Datos de abonado**, detrás de un interruptor por proveedor y con contrato:
  identidad y qué vio quién. Exige aviso de privacidad y el formulario de
  Seguridad de los datos de Play, y el editor somos nosotros.

Sin dependencia de Google Play Services, porque Fire TV y muchas cajas
genéricas no lo traen.

## B7 — Preparación para Play Store

**La parte de código, hecha el 05-oct.** `PLAY.md` recoge el estado y lo que
falta.

- ~~Manifiesto con `leanback`, `touchscreen` y `faketouch` en
  `required="false"`~~ — los cuatro, con `microphone`
- ~~`LEANBACK_LAUNCHER`~~ — ya venía del upstream
- ~~AAB con `armeabi-v7a` y `arm64-v8a`~~ — **no había nada que configurar**:
  sin NDK propio, la única nativa es `libandroidx.graphics.path` y el bundle
  reparte las cuatro arquitecturas solo
- ~~target API vigente~~ — 36
- ~~El registro, nunca en claro~~ — `domain-config` para `authjelly.satmx.net`,
  que pedía `PETICION_APK` §1
- ~~Banner de ficha 1280×720 e icono 512×512 — hay que generarlos, el del APK
  es vectorial~~ **hechos el 05-oct**, en `herramientas/marca/png/`. Del icono
  hay dos y hay que elegir: `palco-icono-512-ficha.png` es el que conviene
  subir (`PLAY.md` §6 dice por qué)
- **Política de privacidad** y **formulario de Seguridad de los datos**: ahora
  obligan de verdad, porque la app manda `installId` y diagnóstico (B6)
- **Repositorio público del fork**, que es la oferta de código de la GPL-2.0
- ~~**Versión real**: hoy compila como `0.0.0-dev.1` y eso no se publica~~
  **el build ya no deja**, desde el 16-oct: una tarea de release con
  `versionCode < 100` se detiene y dice cómo fijar el número. Elegirlo sigue
  siendo de Edgar; publicar el `1` por descuido ya no se puede
- **Clave de firma y su custodia**, y decidir si se usa Play App Signing

**Cierre:** un AAB firmado, subido, y una ZTE instalándolo desde la ficha.

---

# Simulación de fallas

```bash
# Pérdida y jitter
tc qdisc add dev eth0 root netem loss 5% delay 100ms 20ms
# Corte total
tc qdisc change dev eth0 root netem loss 100%
# Quitar
tc qdisc del dev eth0 root
```

Además: cortar la entrada en Astra, y reproducir en bucle un golden sample
capturado durante lluvia real. **Esa última es la mejor prueba que existe.**

---

# Definición de "hecho" en la vía M

1. Edgar instaló la APK y probó en pantalla
2. Verificó el criterio de cierre
3. Confirmó que **el VOD de Jellyfin sigue igual** que en la APK de
   referencia de M0
4. `git diff --stat upstream-base..HEAD -- ':!livetv/'` sigue limpio
5. La entrada quedó escrita en `LIVETV_LOG.md`
