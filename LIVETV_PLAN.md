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

## M7 — Resiliencia ante lluvia — **abierta**

**Objetivo:** que un corte no requiera intervención del suscriptor.

**Lo que ya está y está validado en pantalla:** la escalera del vigilante
(pendiente 69, los dos cortes provocados por Edgar el 09-oct: 16 s por el
escalón 2 reiniciando el canal en Flussonic, 26 s por el escalón 3 con el cable
fuera), el playlist vacío como espera, el mapeo de errores y la regla de no
vetar decoders durante corrupción.

**Lo que falta, y es lo que de verdad se parece a la lluvia:** `tc netem
loss 5% delay 100ms 20ms`, un corte de **5 minutos**, y el golden sample de
lluvia real en bucle —que este mismo documento llama «la mejor prueba que
existe»—. Todo lo que hemos roto hasta ahora es corte limpio, y la banda Ku no
falla así. Más el pendiente 12.

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

## M8 — Maratón y zapping — **abierta**

**Objetivo:** sobrevivir a la operación real.

**Estado medido al 11-oct:** la sesión más larga son **9,3 h** contra un mínimo
aceptable de 24 h en §10, y el zapping va bien pero con pocas muestras —4
sintonías con el primer fotograma entre 1 651 y 1 931 ms—. Nada de esto está
cerrado.

- ~~Reconexión programada antes del rollover de PTS (~26.5 h)~~ **probablemente
  no hay que escribirla.** Leído el 11-oct en la fuente de Media3 1.8.0:
  `TimestampAdjuster` dice «taking into account timestamp rollover», lleva
  `MAX_PTS_PLUS_ONE = 0x200000000L` —los 33 bits— y estima el número de vueltas
  en `adjustTsTimestamp`; el camino HLS lo usa vía `TimestampAdjusterProvider`,
  que entrega un `TimestampAdjuster(MODE_SHARED)` por secuencia de
  discontinuidad. **Queda por comprobar con un remojo que cruce las 26,5 h**, y
  solo si se rompe se escribe algo
- Sin fugas de memoria en zapping repetido
- Medir el zapping real

**Pruebas de Edgar:** un canal 48 h continuas; 50 cambios de canal seguidos.

**Cierre:** vivo a las 48 h, memoria estable, zapping p50 < 2 s.

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
  03-oct.** Se llama **Palco**; `applicationId` `mx.kataliziador.palco`;
  logotipo, icono y banner propios, en vector. Queda comprobar el nombre en
  IMPI (clases 38 y 41) y en la Play Console
- ~~Pantalla de información: "no afiliado al proyecto Jellyfin" + GPL-2.0~~
  **hecho el 03-oct.** La LGPL-2.1 se añade cuando entre libVLC en M6: hoy no
  viaja en el APK y declararla sería falso

**Cierre:** dos servidores de canales distintos visten la app distinto sin
recompilar, y sin ninguno la app arranca con la marca neutra y la lista de
`BuildConfig`.

## B9 — Registro de sites
Consulta al registro antes de derivar por convención (`REGISTRY_SCHEMA.md`).

- Normalización del host igual que en `ServiceUrl`
- Autorización guardada por host, con su caducidad
- **Degradación:** sin respuesta y vigente se entra; caducada hay gracia;
  pasada la gracia se cierra **explicando**, no con un error genérico
- La puerta cubre **solo** la sección de canales; el VOD no se toca
- `channelsUrl` y `brand` del registro pisan a la convención y al site

**Dependencia:** B1. **Criterio de cierre:** dar de alta un host en el registro
y que una caja entre sin tocar la app; quitarlo y que se cierre con su mensaje
al caducar. Y con el registro apagado, una caja ya autorizada sigue entrando.

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
- Lo mismo para el cliente OkHttp de Coil, o la autorización funcionaría y el
  logo de la marca no.

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
  **Falta el códec de audio recibido**, que es como se detecta un site con el
  AAC mal configurado (§2)
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
- Banner de ficha 1280×720 e icono 512×512 — hay que generarlos, el del APK es
  vectorial
- **Política de privacidad** y **formulario de Seguridad de los datos**: ahora
  obligan de verdad, porque la app manda `installId` y diagnóstico (B6)
- **Repositorio público del fork**, que es la oferta de código de la GPL-2.0
- **Versión real**: hoy compila como `0.0.0-dev.1` y eso no se publica
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
