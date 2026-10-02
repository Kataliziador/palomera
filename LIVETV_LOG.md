# LIVETV_LOG.md

Bitácora del módulo `livetv`. Es la memoria del proyecto entre sesiones.

**Vía M:** una entrada al cerrar cada fase.
**Vía B:** una entrada por bloque terminado, con lo hecho y lo pendiente.

---

## Plantilla

```
### [M-N | B-N] Título corto — AAAA-MM-DD

**Síntoma:** qué se observaba, en qué canal, en qué caja.
**Hipótesis:** por qué se creía que pasaba.
**Cambio:** qué se tocó, en qué archivos, detrás de qué toggle.
**Prueba:** cómo lo verificó Edgar (vía M) o cómo se validó (vía B).
**Resultado:** el número o la observación concreta.
**Estado:** cerrada | abierta | descartada
```

---

## Decisiones previas al desarrollo

### Caracterización de canales — 2026-09-20

Cinco canales medidos con `scan-canales.sh`. GOP entre 1.07 y 1.50 s. AUDs e
IDR presentes en todos los H.264.

> **Corregida el 2026-09-22.** Los conteos de esta entrada eran de cinco
> canales y son seis, y el de anamórficos estaba mal de origen. Cifras
> correctas: **cinco H.264 + AAC y uno MPEG-2 + MP2; cuatro entrelazados; dos
> anamórficos; dos con doble pista de audio.**

Detalle completo en `CHANNELS.md`. **Estado:** cerrada, con la corrección.

### Segmentación HLS a 2 s — 2026-09-21

Los segmentos medían 5.3 a 6.5 s. Medido el GOP: entre 1.07 y 1.50 s, o sea
era configuración, no límite del satélite. Se puso `segment_duration 2` y
`segment_count 6` en los cinco streams.

Resultado: segmentos de 2.0 a 3.0 s, latencia estimada de ~16 s a ~8 s,
zapping estimado de 3-6 s a 2-3 s. Sin transcodificar. **Estado:** cerrada.

### Transporte Astra → Flussonic a HTTP — 2026-09-21

El tramo interno iba por UDP: cualquier pérdida en la red local se convertía
en macrobloques aunque el satélite llegara limpio. Cambiado a `tshttp://`.

Consecuencia para diagnóstico: en canales de Astra, todo error de continuidad
viene del satélite o del descrambling, no de la red interna. TotalPlay sigue
en multicast porque no hay alternativa. **Estado:** cerrada.

### Reproducir los canales tal cual llegan — 2026-09-22

El headend no transcodifica video. El motor se desarrolla para la señal como
se recibe. Canales de referencia: 77W y 70W.

**Excepción permitida:** convertir el códec de **audio** en Flussonic (MP2 →
AAC) es una opción disponible, porque cuesta muy poca CPU y no toca el video.
No es el camino por defecto.

**Escala:** el lineup de producción tendrá ~120 canales, la mayoría de 77W
entre HD y SD. Probablemente varios más con MPEG-2 + MP2.

**Prioridad declarada por el cliente:** primero el motor y que la app funcione
completa (películas y series como Jellyfin, más una sección LiveTV nueva con
diseño propio). El EPG se deja para las fases finales.

**Riesgo aceptado:** en cajas sin desentrelazado por hardware, los canales
1080i pueden mostrar peine si el shader GPU no resulta viable. Se documentará
como limitación por dispositivo. **Estado:** cerrada.

### Toolchain fijado — 2026-09-22

Base del fork: **`jellyfin-androidtv` v0.19.10**, commit `984181a`, marcado
`upstream-base`. Se eligió el último tag **estable**; `v0.20.0-beta.2` existe
pero la regla 11 pide versiones fijas y estables.

| | Versión |
|---|---|
| Gradle (wrapper) | 9.1.0 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin | 2.2.21 |
| **Media3** | **1.8.0** |
| compileSdk / targetSdk | 36 |
| minSdk | 21 |
| JDK de compilación | 21.0.12 |

**Ninguna de estas versiones se toca "de paso"** (regla 11). Media3 1.8.0 es la
que el módulo `livetv` debe usar: subirla es un cambio de Modo A con su propia
validación en pantalla.

La caja de pruebas es **API 30**, muy por encima del `minSdk 21`, así que no
ejercita el piso de compatibilidad. **Estado:** cerrada.

---

### Inventario de la caja de pruebas — 2026-09-22

Skyworth YDA, Amlogic Cortex-A35, Android 11, en `192.168.20.132`.
MPEG-2, MP2 y AC-3 por hardware; driver de desentrelazado presente.

Es el **mejor caso posible**, así que el menú de degradación de la fase M1 es
obligatorio para ejercitar los caminos de respaldo. **Estado:** cerrada.

### Arquitectura de decisión en cascada — 2026-09-22

El motor decide por canal y por caja mediante una cascada, con prioridad:
override del servidor → preferencia del canal → detección propia. El servidor
sugiere, la caja verifica.

Especificación en `LIVETV_ENGINE.md`. **Estado:** cerrada.

### Dos modos de trabajo — 2026-09-22

Vía M (motor) paso a paso con validación visual de Edgar; vía B (todo lo
demás) autónoma. Frontera: si el cambio altera decodificación, red, buffers,
render o recuperación, es vía M. **Estado:** cerrada.

---

## Instrucciones de headend

Los cuatro cambios pendientes, en orden de lo que desbloquean. Flussonic
**Streamer 24.03**.

Advertencia previa: **el video nunca se transcodifica** (`CLAUDE.md` §2). En
cualquier pantalla de transcoder, el video va en **Copy**. Si se toca el video,
se rompe el principio rector del proyecto.

### 1. MP2 → AAC en `test-sat77w-estrellassd-mp2v` — gatea M3

En la interfaz de Flussonic, en ese stream, sección **Transcoder**:
video **Copy**, audio **AAC**, 128 kbps, 48 kHz.

En fichero de configuración el equivalente es del estilo
`transcoder vb=copy ab=128k acodec=aac;`, pero **la directiva exacta varía
entre versiones y la interfaz es más segura**. No la doy por buena sin verla.

*Verificación:* la hago yo desde aquí con `ffprobe` — el códec de audio debe
pasar de `mp2` a `aac`. Avísame y lo compruebo en un minuto.

### 2. Pasar `a093` por Flussonic — añade un canal de prueba

Es el único SD H.264 satelital y probablemente viene entrelazado, así que
aporta un caso que hoy no tenemos. Mismos ajustes que el resto:
`segment_duration 2` y `segment_count 12`.

*Verificación:* lo caracterizo y lo añado a `CHANNELS.md`.

### 3. Bajar `source_timeout` a ~10 s — gatea el objetivo de recuperación de M7

Medido: cuando la fuente cae, Flussonic tarda **~15 s** en reconstruir el
playlist, y el cliente no puede recuperarse antes que él. Es lo que hace
inalcanzable el objetivo de < 8 s de `CLAUDE.md` §10.

```
stream <nombre> {
  source_timeout 10;
}
```

*Verificación:* detengo un stream desde la interfaz y cronometro la
reconstrucción, igual que se midió el 22-sep.

### 4. Letrero de "sin señal" como fuente de respaldo — M7

Evita que un rain fade largo deje la imagen congelada, que es lo que el
suscriptor interpreta como «se rompió la tele».

El archivo ya está hecho: **`/home/proyectos/apks-livetv/sin-senal.mp4`** —
1280x720, H.264 + AAC, 10 s, fondo oscuro con «Sin señal / Restableciendo la
transmisión». Cópialo al servidor y referéncialo:

```
stream <nombre> {
  input ...;
  backup /opt/flussonic/priv/sin-senal.mp4;
}
```

Cuando haya marca por ISP (B5) se sustituye por uno con su logo; este sirve
para todos mientras tanto.

*Verificación:* corto la fuente y compruebo que aparece el letrero en vez de
un frame congelado.

---

## Pendientes conocidos

| # | Tema | Vía / fase | Notas |
|---|---|---|---|
| 1 | ~~¿Desentrelaza el VPU?~~ | — | **cerrado 26-sep con evidencia por contraste:** el único canal progresivo del conjunto (Béisbol, 720p) presenta **1:1** a 29.97, y los dos entrelazados presentan **al doble** (Estrellas SD 480i → 59.94, HBO HD 1080i → 45–52). Un decodificador que no desentrelazara daría un cuadro por cuadro codificado en los tres casos. Queda aparte la **validación visual** de la calidad del desentrelazado, que sigue siendo de M5 y de Edgar |
| 2 | ~~Confirmar ABI de la caja~~ | — | **cerrado 22-sep: `armeabi-v7a,armeabi`. Solo 32 bits, sin `arm64-v8a`.** Confirma lo que sugería `/proc/cpuinfo`. `armeabi-v7a` es obligatorio en el AAB o la caja de pruebas no instala; `arm64-v8a` también, porque Play lo exige a toda app con código nativo |
| 3 | ~~Decidir si el audio MP2 se convierte a AAC~~ | — | **cerrado 22-sep: sí, y elevado a política.** Ver entrada abajo |
| 4 | Inventario de códecs de la ZTE B866V2 | M1 | la caja mayoritaria del cliente, aún sin medir |
| 5 | Flags de extractor en Amlogic | M4 | la medición dice OFF/OFF, la experiencia de campo sugiere lo contrario |
| 6 | Bajar `source_timeout` de Flussonic a ~10 s | headend | con HTTP, Astra mantiene la conexión abierta sin datos |
| 7 | ~~Letrero de «sin señal» como fuente de respaldo~~ | — | **cerrado 01-oct: funcionando.** Edgar corta la señal y entra el letrero. La trampa que costó el rato: en `backup <ubicación>/<archivo>`, el prefijo es el **nombre de la ubicación VOD**, no una carpeta, y hay que declararla aparte (`vod fallback { storage … }`). Un nombre que no casa da `enoent`. Ver `headend/letreros/README.md` |
| 8 | Confirmar si `a095` es el mismo canal que `discoveryhd-70w` | M1 | mismos datos técnicos |
| 9 | Pasar `a093` (SD H.264 70W) por Flussonic | headend | único SD H.264 satelital, probablemente entrelazado |
| 10 | ~~Caracterizar `test-sat70w-hbohd-mp4`~~ | M1 | **cerrado**: h264 1080i `bb`, AAC |
| 11 | **`estrellashd-mp4`: pista de audio intermitente** | headend + M7 | desaparece y vuelve; revisar errores PES en Astra |
| 12 | Sobrevivir a cambios en el número de pistas a mitad de stream | M7 | ExoPlayer lo tolera mal; caso real en `estrellashd-mp4` |
| 13 | ~~Aceptar `spa`, `esp`, `esl` y `es` como español~~ | — | **cerrado 02-oct con M3.** `ChannelProfile.normalizarIdioma` acepta `spa esp esl es spanish castellano`. Verificado en la caja: `estrellashd` entrega la etiqueta cruda `esl` y el motor la expone como `audio.pistas idiomas=[spa]` |
| 14 | ~~Recapturar la muestra de `estrellashd-mp4`~~ | — | **cerrado 26-sep: la contradicción era falsa.** 60000/1001 y 30000/1001 eran los dos valores nominales del contenedor. Medido sobre 12 s del canal en vivo: 286 imágenes **progresivas** en 11.912 s (23.976 fps) con `repeat_pict` alternando 1,0,1,0 sin excepción → **cine con pulldown 3:2 señalizado**. Ver `CHANNELS.md` §3.quinquies |
| 15 | Instalar tsduck y tomar línea base de errores CC con buen clima | M7 | no está en los repos de Ubuntu, bajar el .deb |
| 22 | **`hbohd` transporta cine 23.976 con cadencia 3:2** | **M5** | microcortes que Edgar ve y los contadores no. Medido: 358 intervalos de 50 ms alternando con 357 de 33 ms, frente a 894 uniformes de 33 en `discoveryhd`. Descartado `FLAG_DETECT_ACCESS_UNITS`. Ver `CHANNELS.md` §3.quater |
| 16 | Validar desentrelazado con los **dos tipos** de codificación | M5 | **revisar:** se justificaba en que `hbohd` era `bb`, y medido es `tt`. Ya no hay canal con orden invertido en el conjunto | `discoveryhd-70w` por frames, `hbohd-70w` por campos. Estos dos bastan: cubren las cuatro combinaciones sin depender de `estrellashd`, que está en disputa (#14) |
| 17 | ~~Subir `segment_count` de 6 a 12~~ | — | **cerrado 23-sep: hecho.** Ventana medida de 29–36 s en todos los canales. Antes:| ventana DVR medida de 13.88 s en `hbohd` contra `targetOffsetMs` de 12000: 1.9 s de margen. Sin costo de CPU ni de latencia |
| 18 | ~~Aplicar MP2 → AAC a los canales MP2 del lineup~~ | — | **cerrado 26-sep: hecho y verificado.** Los seis canales entregan `aac 48000 Hz 2ch`, incluido `estrellassd-mp2v`, que era el único MP2. Con ello el módulo no necesita decoder de MP2 en ninguna caja |
| 19 | ~~El fork base trae `media3-ffmpeg-decoder` (GPL-3.0)~~ | — | **cerrado 22-sep: retirado.** Ver entrada abajo |
| 21 | **Emitir `EXT-X-SERVER-CONTROL` con `HOLD-BACK` en Flussonic** | headend | sin esa etiqueta el offset real lo fija `3 × TARGETDURATION` y el `targetOffsetMs` por canal no se aplica. Medido el 24-sep |
| 20 | Traer `scan-canales.sh` al repositorio | B | vive suelto en `/home/scan-canales.sh` y produce la fuente de verdad |
| 23 | ~~`tunneled` deja sin imagen a los canales probados~~ | — | **RETIRADO 30-sep: era falso.** Salía de dos instrumentos que en modo tunneled **no ven nada**: el decodificador escribe directo al hardware de pantalla, así que ni `setVideoFrameMetadataListener` ni `SurfaceFlinger --latency` reciben fotogramas. Edgar miró `estrellassd` con tunneled activo y **se ve bien**. En ese modo solo valen los ojos |
| ~~34~~ | ~~**`mono.m3u8` solo expone una pista de audio a Media3**~~ | — | **cerrado 02-oct: se pasa a `index.m3u8`.** Decisión de Edgar, con un argumento que faltaba: en algunos satélites la etiqueta de idioma miente, así que ver la segunda pista no es un extra sino el único remedio del abonado |
| 36 | **El master duplica el audio 1: la variante lo lleva multiplexado y además lo declara como rendition** | headend | HBO y Discovery ofrecen «Español, Español, Inglés». Lo limpio es que la variante no lleve audio cuando hay grupo `AUDIO`; en la app sería adivinar, y una regla que se equivoque esconde una pista que el abonado necesitaba |
| 35 | **Cajas con Android < 7.1.1 no validan el certificado del registro** | **B9** | `minSdk` 21 y Let's Encrypt cuelga de ISRG Root X1, que Android no trae hasta 7.1.1. No da error visible: la caja nunca entra a TV en vivo. Alcanza también al `config.json` si un site lo sirve por HTTPS. Pedido en `autorizador/docs/PETICION_APK.md` |
| 33 | **`defaultChannel` se interpreta pero ningún camino lo usa** | B2 | el reproductor siempre recibe un `id` desde la guía, así que `byId(null)` nunca ocurre. Empezará a importar con la memoria del último canal visto: la primera vez no habrá ninguno |
| 32 | **El aviso del operador se interpreta pero no se dibuja** | B4 | falta decidir dónde va sin estorbar al canal. Caduca solo, así que lo único que falta es pintarlo |
| 31 | **La fila de inicio depende de un permiso de Jellyfin** | B | `HomeSectionType.LIVE_TV` solo se añade si el usuario tiene `enableLiveTvAccess`, que es un permiso del Jellyfin del ISP sin relación con nuestros canales. La barra principal no está condicionada, así que el camino bueno funciona igual |
| ~~30~~ | ~~**El límite de 2 MB del `config.json` solo mira la longitud declarada**~~ | — | **cerrado 02-oct:** la lectura va acotada y falla en vez de truncar. Probado con una respuesta troceada de 3 MB | una respuesta troceada, sin `Content-Length`, se salta la comprobación y se lee entera en memoria. Con un servicio propio en LAN el riesgo es bajo, pero el límite no es el que dice ser |
| ~~29~~ | ~~**`audioDefault` se interpreta pero no se aplica**~~ | — | **cerrado 02-oct, validado por Edgar.** Activo de fábrica. La normalización es nuestra, no de Media3: `esl` no es código ISO y Media3 no lo casaría con `spa` |
| ~~28~~ | ~~**`logoUrl` se interpreta pero no se dibuja**~~ | — | **cerrado 02-oct** en la barra, con respaldo a iniciales cuando falta o falla. **Queda pendiente en la guía**: las tarjetas siguen sin logo, y con 120 canales es como se reconoce un canal de un vistazo |
| ~~27~~ | ~~**El inicio sigue consultando la LiveTV vieja de Jellyfin**~~ | — | **cerrado 02-oct:** fuera la fila de «en emisión» y los tres botones de DVR que llevaban de vuelta a la sección vieja. Cero llamadas |
| 26 | **La Skyworth no tiene decoder de vídeo por software para H.264 ni MPEG-2** | **M6** | solo `c2.android` de hevc, vp8, vp9, mpeg4 y h263. El paso 2 de la cascada es inalcanzable aquí para los dos códecs del lineup, así que **libVLC es el único respaldo que existe** y el cierre degradado de M3 depende de él. Medir lo mismo en la ZTE antes de generalizar |
| 25 | **`engine.attach` se llama ~2 veces por segundo mientras reproduce** | **M5** | el `update` del `AndroidView` corre en cada recomposición y las estadísticas recomponen cada 500 ms. 206 llamadas en 115 s. Rota el buffer de logcat en menos de un minuto. Solo `AspectoDeVideo.aplicar` necesita las estadísticas; `attach` debería correr cuando cambia la vista |
| 24 | **El decodificador AVC de Amlogic ignora el pulldown de H.264** | **M5 / headend** | saca 2 campos por imagen = 48 fps en contenido de cine, −17.5 % del ideal. El de MPEG-2 lo respeta (−0.2 %). Afecta igual a libVLC (47.99), así que **no se arregla en el cliente**. Decisión de Edgar: aceptarlo o tratar el canal en el headend

---

## Entradas

### [B2] La guía a 120 canales, que es el tamaño de verdad — 2026-10-02

Toda la interfaz se había construido y mirado con **seis** canales. El lineup
real son ~120, y `LIVETV_PLAN.md` B2 lo dice desde el principio: *«diseñar para
~120 canales, no para los 6 de prueba»*. Nunca se había comprobado.

Se generó un lineup sintético de 120 canales en 8 grupos, con los seis reales
dentro para que algo reproduzca de verdad, y con `defaultChannel` apuntando al
**último de todos** — el peor caso de desplazamiento.

#### Lo que encontró: el foco no llegaba

Con seis canales cada fila cabía entera en pantalla. Con 120, el canal señalado
está a quince tarjetas a la derecha de su fila, y **la tarjeta no existe**:
`LazyRow` no compone lo que no se ve, igual que `LazyColumn`.

Son **dos** desplazamientos, no uno. La columna compone la sección; la fila
compone la tarjeta. Faltaba el segundo, y el síntoma no era un error: el foco se
quedaba arriba **como si no hubiera memoria**. El mismo modo de fallo que el día
anterior, un nivel más abajo.

Arreglado: la fila del canal con foco se maneja desde fuera, se desplazan las
dos y entonces se pide el foco. Verificado con el canal 214, el último de la
lista, al final de su fila.

#### Lo que aguantó

| | 6 canales | 120 canales |
|---|---|---|
| PSS del proceso | 138 MB | **151 MB** |
| `config.json` | 2.6 kB | 36 kB |
| Interpretar el documento | — | **110 ms**, descarga incluida |

**13 MB por 114 canales más**, en una caja de 2 GB. La guía se desplaza sin
tirones y marcar un número sigue resolviendo: `198 → Señal 198`.

**Lo que esto no mide:** la fluidez del desplazamiento con el mando, que es cosa
de Edgar. Las medidas por `adb` entran a la sección con un toque y no recorren
el mismo camino de foco.

**Estado:** cerrada la comprobación de escala. Queda la validación con mando.

### [B5] La marca del proveedor se aplica — 2026-10-02

Primer trozo de B5 tal como quedó reescrita: **sin flavors**, la marca sale del
servicio del site y la app se viste al conectar.

#### Lo que decide esta fase no es el color, es el respaldo

Tres ranuras —`displayName`, `accent`, `logoUrl`— y **cada una con un respaldo
que siempre funciona**, porque la marca es lo primero que se ve y la escribe un
ISP en un JSON.

**El contraste se valida contra el fondo de la sección**, exigiendo 3:1, que es
el umbral de WCAG para texto grande y elementos de interfaz — justo lo que el
acento va a ser. Pedir 4.5 habría descartado colores de marca legibles a tres
metros y todos los ISPs habrían acabado en gris.

Verificado con un acento de marca realista pero invisible sobre negro:

```
marca.colorIlegible accent=#101840 contraste=1.23 minimo=3.0
catalogo.listo … marca=AzulISP marcaOrigen=parcial
```

**El acento malo no tira el resto de la marca.** El nombre se conserva y se
dibuja legible; solo cae el color. `marcaOrigen` distingue `servidor`, `parcial`
y `neutra`, que es lo que permite saber de un vistazo si un site está bien
configurado.

#### Dónde se ve, y dónde no

El logo **sustituye** al nombre, no lo acompaña: un logo con su nombre al lado
es una cabecera de ISP, y esta es la de la sección. Si el logo no carga, cae al
nombre.

El acento pinta el nombre y los **títulos de grupo** de la guía. **No repinta la
app**: el VOD conserva el tema de siempre, que es lo que manda `CLAUDE.md` §4
regla 2. Los títulos eran el único sitio donde el color del ISP cabe sin
competir con los canales, y además estaban en un gris fijo puesto a mano.

#### Un descuido mío, y por qué no se notó

La primera versión pasaba el acento a la guía y **no lo usaba**: la sustitución
del color no casó por la indentación y el parámetro quedó sin leer. Compiló,
instaló y se veía bien — porque el valor que no se aplicaba coincidía con el
gris que ya estaba. Lo delató la captura, no el compilador.

Es el modo de fallo de siempre en este proyecto: **lo que se parece a que
funciona**.

**Estado:** cerrada esta parte. Quedan de B5 la pantalla de licencias y el
renombrado de la app, que no dependen del servidor.

### Por qué HBO enseña tres pistas de audio y el stream tiene dos — 2026-10-02

Pregunta de Edgar al validar: el stream trae dos audios y la app ofrece tres.

**El master publica el español dos veces.** Medido descargando un segmento de
cada uno de los tres destinos que declara:

```
tracks-a1    → aac                 ← rendition, español
tracks-a2    → aac                 ← rendition, inglés
tracks-v1a1  → aac + h264          ← la variante: vídeo + audio 1 MULTIPLEXADO
```

El nombre lo dice: `v1a1` es *vídeo 1 + audio 1*. Flussonic mete el español
dentro de la variante **y además** lo declara como `EXT-X-MEDIA` aparte. Media3
ve los tres caminos y los expone todos, así que las dos primeras pistas son el
**mismo audio**.

**No es un fallo de la app ni del stream**: es cómo empaqueta Flussonic. Y
explica de paso por qué `mono.m3u8` solo enseñaba una — ahí no hay renditions,
solo la variante.

**Consecuencia para el abonado:** el selector ofrece «Español, Español, Inglés»,
y la segunda no hace nada distinto de la primera. Funciona, pero confunde.

#### Dónde arreglarlo, y por qué no en la app

**Lo limpio es el headend:** que la variante no lleve audio multiplexado cuando
ya se declaran renditions. Es lo que dice el estándar cuando se usa un grupo
`AUDIO`, y muchos empaquetadores lo mezclan igual por compatibilidad con
clientes viejos. Hay que mirar si Flussonic lo permite configurar.

**En la app sería adivinar.** Dos pistas con el mismo idioma pueden ser el mismo
audio duplicado —este caso— o dos audios distintos de verdad, como un español
latino y un castellano. La app no puede distinguirlos sin inventarse una regla,
y una regla que se equivoque **esconde una pista que el abonado necesitaba**,
que es peor que enseñar una de más.

Si el headend no puede evitarlo, la salida sería que el `config.json` lo diga
—un campo que indique qué pistas publicar— y no que la app lo deduzca.

**Estado:** abierta. Pendiente #36.

### [B] Sin lista de arranque en producción, y una petición del autorizador — 2026-10-02

#### Los seis canales de desarrollo no pueden viajar en la APK de un abonado

Recordatorio de Edgar: los canales los sirve el miniservicio del site; la lista
pegada es solo para estas pruebas. Al mirarlo, el último recurso estaba mal.

`LIVETV_BOOTSTRAP_CHANNELS` se compilaba en **todas** las variantes, con las
URLs del Flussonic de desarrollo. En la caja de un abonado cuyo site no
responde, eso son **seis canales fantasma** —con nombres de otro headend y URLs
muertas— justo en el momento en que todo falla. Es peor que no enseñar nada,
porque parece que la app funciona.

Ahora la lista existe **solo en depuración**. Verificado sobre los dex de las
dos variantes:

```
depuración: 22 dex, URLs de desarrollo = 2
release:     6 dex, URLs de desarrollo = 0
```

> Nota de método: la primera comprobación miró **un solo** `classes.dex` y el
> APK es multidex. Daba el resultado correcto por casualidad. Se rehízo sobre
> `classes*.dex`.

Y el estado vacío deja de decir «No hay canales que mostrar» cuando lo que pasó
es que no se pudo hablar con el servidor: **son dos situaciones distintas** y la
primera manda a buscar donde no es.

#### Petición del proyecto del autorizador

`/home/proyectos/autorizador/docs/PETICION_APK.md`, para B9. El contrato no
cambia. Lo sustancioso:

**`minSdk` es 21 y el certificado del registro lo emite Let's Encrypt.** Android
no trae ISRG Root X1 hasta la 7.1.1, así que una caja de 5.0–7.1.0 **nunca
conseguiría autorización**. Y no fallaría de forma visible: simplemente no
entraría nunca a TV en vivo, mientras otras cajas del mismo ISP sí. Verificado
que `minSdk = 21` en `libs.versions.toml`.

El arreglo pedido es un `X509TrustManager` que confíe en **el sistema o en las
raíces incluidas**, solo para esa conexión. Bien razonado: `network_security_config.xml`
no sirve porque Android lo ignora por debajo de API 24 —justo donde está el
problema— y un `TrustManager` que acepte todo abriría la puerta a que cualquiera
en la red del ISP se haga pasar por el registro.

**Lo que añadí desde este lado:** `ConfigRepository` usa `HttpURLConnection`,
así que **el mismo fallo alcanza al `config.json`** si un site lo sirve por
HTTPS con Let's Encrypt. Desde el autorizador no podían verlo. El arreglo tiene
que cubrir las dos conexiones.

**No se implementa todavía:** B9 no existe. Queda anotado en el plan para que se
haga con él y no después.

**Estado:** cerrada la parte de la lista de arranque; la petición, registrada.

### [M] El endpoint pasa a `index.m3u8` — 2026-10-02 → **cerrada**

Revierte una decisión cerrada de `CLAUDE.md` §6. Decisión de Edgar, con un
argumento que yo no tenía:

> «en ciertos satélites el audio está invertido: aunque dice ES, suele traer
> audio en inglés»

Eso cambia el peso entero del asunto. Yo lo había planteado como «180 ms de
zapeo contra tener el segundo audio en los canales de cine». **No es eso.** Si
la etiqueta miente, la segunda pista no es un extra: es **el único remedio** que
le queda al abonado, y con `mono.m3u8` ni siquiera la ve.

#### Lo medido

| endpoint | lo que ve la app en `discoveryhd` |
|---|---|
| `mono.m3u8` | `cuantas=1 idiomas=[spa]` |
| `index.m3u8` | `cuantas=3 idiomas=[spa, spa, eng]` |

El segmento trae dos PIDs de audio (`0xdd` spa, `0xde` eng) en los dos casos. La
diferencia es la declaración: el master los publica como `EXT-X-MEDIA` separadas
y Media3 los trata como renditions; multiplexados en el mismo TS, el extractor
solo saca uno.

#### No reintroduce ABR, y ahora se vigila

Los seis canales declaran **un solo** `EXT-X-STREAM-INF`:

```
beisbol  mileniosd  estrellashd  estrellassd      variantes=1  audios=0
discoveryhd  hbohd                                variantes=1  audios=2
```

Pero eso lo sostiene el headend, no la app. Se añadió `abr.variasVariantes`: un
master con más de una calidad **se registra** en vez de poner a Media3 a conmutar
en silencio. §6 lo prohíbe y hasta ahora no había forma de notar la infracción.

#### Los seis, por el endpoint nuevo

```
estrellashd  HW avc     1 [spa]
estrellassd  HW mpeg2   1 [spa]
mileniosd    HW avc     1 [spa]
hbohd        HW avc     3 [spa, spa, eng]
discoveryhd  HW avc     3 [spa, spa, eng]
beisbol      HW avc     1 [spa]
```

Los seis arrancan en español, ninguno disparó `abr.variasVariantes`, y la
cascada resuelve igual que antes. **El MPEG-2 también reproduce** por el master,
que era lo que más dudas me daba.

#### Qué se cambió, y qué no

El endpoint **no vive en la app**: viaja en la `url` de cada canal del
`config.json`. Así que esto es un cambio de **datos**, no de código — salvo el
vigilante de ABR. Actualizados `headend/config.json`, la lista de arranque del
`BuildConfig`, `CLAUDE.md` §6, el contrato, `INTEGRACION_CANALES.md` y el
proyecto del servicio de canales.

Y en `CLAUDE.md` §4 regla 6 queda escrito que **la etiqueta puede mentir**: la
preferencia por idioma es un valor por defecto, nunca una imposición, y la
elección del abonado manda para el resto de la sintonía.

**Validado por Edgar el 02-oct:** «se ven bien, igual que antes». Los seis
canales sin regresión visible y sin penalización de zapeo apreciable.

**Estado:** cerrada.

### [M] `audioDefault` se aplica — y de paso, `mono.m3u8` oculta las otras pistas — 2026-10-02 → **cerrada**

Modo A. Detrás del interruptor «Idioma de audio del config.json» del menú, **de
fábrica apagado**, que es el comportamiento anterior.

#### No se delega en Media3, y la razón es medible

Lo obvio era `setPreferredAudioLanguages("spa")` y dejar que Media3 case. **No
habría funcionado donde más falta hace:** `estrellashd` y `estrellassd` etiquetan
el español como `esl`, que no es un código ISO. Media3 no lo casa con `spa` y la
preferencia se quedaría muda justo en los canales donde el dato existe.

La normalización es nuestra (`LanguageNormalizer`, que acepta `spa esp esl es
spanish castellano`), así que la comparación también. Verificado:

```
tracks.crudo … audio[esl/mp4a-latm*]
audio.pistas cuantas=1 idiomas=[spa]
audio.porDefectoYaActivo canal=estrellashd idioma=spa
```

La pista cruda es `esl`, se reconoce como `spa` y **no se conmuta nada**, que es
lo correcto. Con el emparejamiento de Media3 esto habría fallado en silencio.

#### Y conmuta de verdad

Con `discoveryhd` pidiendo `eng`:

```
audio.pistas cuantas=3 idiomas=[spa, spa, eng]
audio.porDefectoAplicado canal=discoveryhd pedido=eng antes=spa
tracks.crudo … audio[es] | audio[es] | audio[en*]     ← el asterisco se movió
```

#### Dos defectos de mi propio diseño, encontrados al probar

**Me rendía demasiado pronto.** Marcaba «resuelto» aunque la pista pedida no
estuviera, así que si el segundo audio llegaba un segundo después —y en un canal
de cine **aparece y desaparece con el programa**, medido el 01-oct— ya no se
volvía a intentar. Ahora se reintenta en cada cambio de pistas; lo que se da una
sola vez es el aviso.

**Y podía pelearse con el abonado.** Si alguien se pasaba a inglés a mano y el
canal cambiaba de pistas, se lo devolvíamos a español. Ahora `selectAudioTrack`
cierra la puerta: quien elige, manda.

---

### `mono.m3u8` solo expone **una** pista de audio a Media3 — 2026-10-02

Esto salió al intentar validar lo anterior, y es más grande.

**El segmento tiene dos PIDs de audio.** Medido sobre un segmento descargado de
`discoveryhd`:

```
id=0xdd  aac  TAG:language=spa
id=0xde  aac  TAG:language=eng
id=0xd3  h264
```

**Y la app ve una sola pista**, consistentemente, durante 50 s de observación:
`audio.pistas cuantas=1 idiomas=[spa]`. No es el audio intermitente del 01-oct:
el stream las tiene **ahora** y ffprobe las ve.

**El mismo canal por `index.m3u8` las ve todas:**

| endpoint | lo que ve la app |
|---|---|
| `mono.m3u8` | `cuantas=1 idiomas=[spa]` |
| `index.m3u8` | `cuantas=3 idiomas=[spa, spa, eng]` |

La diferencia está en cómo se declaran: `index.m3u8` las publica como
`EXT-X-MEDIA` separadas, con su propia URI, y Media3 las trata como renditions.
En `mono.m3u8` van multiplexadas en el mismo TS y el extractor solo expone una.

**Corrige la entrada del 01-oct.** Aquella decía «`mono.m3u8` sí entrega las dos
pistas» con una tabla que daba `spa, spa, eng` en ambas columnas. Esa columna
estaba mal: `spa, spa, eng` es exactamente lo que da `index.m3u8` hoy, y
`mono.m3u8` da una. El endpoint nunca se comparó bien.

**Afecta a una decisión cerrada de `CLAUDE.md` §6**, que fija `mono.m3u8`
«media playlist directo, no el master». El motivo era simplicidad y evitar ABR;
el costo, que se desconoció, es **quedarse sin el segundo audio en HBO y
Discovery**, que es una función de producto.

**Usar `index.m3u8` no reintroduce ABR:** el master de estos canales declara un
solo `EXT-X-STREAM-INF`, o sea una sola calidad. Lo medido el 01-oct da ~180 ms
más de primer fotograma y una conexión más.

**No se cambia nada sin Edgar.** Es §6 y es Modo A. Pendiente #34.

**Estado:** abierta — falta la validación en pantalla.

### [B] El contrato crece: aviso, canal por defecto, alias, no disponible — 2026-10-02

Cinco campos nuevos salidos de pensar qué más tiene sentido que gestione el
servicio del site. Todos **opcionales**, y por eso con pruebas: un campo
opcional mal interpretado no se ve —el documento se acepta igual y la función
sencillamente no ocurre—.

| Campo | Dónde | Estado |
|---|---|---|
| `message` | raíz | se lee y caduca solo; no se dibuja |
| `defaultChannel` | raíz | se lee; ningún camino lo usa todavía |
| `unavailable` + `unavailableMessage` | canal | **implementado** |
| `aliases` | canal | se lee; no hay búsqueda |
| `rating`, `backupUrl` | canal | se leen; sin comportamiento por diseño |

#### Lo único con comportamiento completo: el canal no disponible

**Hay dos formas de que un canal no se vea y no son lo mismo.** No publicarlo
—el servicio no lo emite— es lo correcto mientras se da de alta o cuando se
retira. Publicarlo como no disponible lo deja en la guía, con su número y su
sitio, y al sintonizarlo enseña el motivo.

La segunda existe porque **un canal que desaparece genera una llamada a soporte
y uno que explica no**. Si alguien ve el 207 todos los días y un martes no está,
llama.

Se comprueba **antes** de construir plan ni motor: no tiene sentido abrir un
decodificador para nada. Verificado en la caja:

```
canal.noDisponible canal=mileniosd motivo=Fuera de servicio por mantenimiento del headend
```

Y en pantalla, el nombre del canal con el motivo debajo.

#### El aviso del operador lleva fecha, y esa es la parte importante

`until` existe porque el fallo previsible de un aviso no es el texto: es **el
aviso que nadie quitó**. Un mantenimiento de hace tres meses sigue en pantalla
porque quien lo puso se olvidó. Con fecha se retira solo.

Y una fecha ilegible **no tira el aviso**: se trata como sin caducidad y se
registra. Perder el aviso es peor que enseñarlo de más.

#### Lo que no se adoptó, y por qué

**Paquetes** (básico, premium). La v1 no tiene autenticación de abonado, así que
la app no puede saber quién es quién: servir lineups distintos es imposible hoy,
no difícil. Decisión de Edgar.

`rating` y `backupUrl` sí entraron **aunque no hagan nada**: el sitio natural de
ese dato es el `config.json`, y meterlos después obligaría a volver a pasar por
todos los sites. Conmutar de fuente además exige motor y validación en pantalla.

#### Un error mío en las pruebas

Escribí a mano el epoch de `2026-10-08T04:00:00Z` y me equivoqué en cuatro
horas. La prueba falló, el parser tenía razón. Ahora la prueba lo **calcula**:
un epoch a ojo acaba comprobando mi aritmética en vez del código.

**Estado:** cerrada.

### [B] Tres huecos cerrados: logo, límite de tamaño y la LiveTV vieja — 2026-10-02

Los tres salieron de escribir el contrato y mirar el código en vez de fiarme de
lo que recordaba.

#### #28 — El logo se dibuja, con respaldo de verdad

`logoUrl` se interpretaba y se tiraba. Ahora se dibuja, en
`app/.../ui/livetv/LogoDeCanal.kt`.

**Las iniciales no se pintan detrás de la imagen**, que era la solución fácil:
un logo con transparencia las dejaría asomar y se leería como suciedad. Se
pintan solo cuando no hay logo **o cuando el logo no cargó**, y para saber lo
segundo hace falta el estado de carga — por eso usa Coil directamente en vez del
`AsyncImage` común de la app, que no lo expone.

El estado de fallo va atado a la URL: al zapear, un canal sin logo no hereda el
fallo del anterior ni uno con logo se queda con las iniciales del que falló.

Probado con los tres casos, no solo el bueno:

| Canal | `logoUrl` | Resultado |
|---|---|---|
| Estrellas HD | PNG real con transparencia | la imagen, sin iniciales asomando |
| Estrellas SD | ausente | `EST` |
| Béisbol | URL rota a propósito | `BÉI` |

#### #30 — El límite de 2 MB ahora es un límite

Se comprobaba contra la **longitud declarada**, así que una respuesta troceada
se lo saltaba y se leía entera en memoria en una caja de 2 GB. Ahora la lectura
va acotada.

**Falla en vez de truncar.** Un JSON cortado a la mitad no es un JSON más corto:
es basura que el analizador rechazaría igual, pero con un mensaje que no diría la
causa.

Probado con un servidor que responde `Transfer-Encoding: chunked` sin declarar
longitud y manda 3 MB:

```
config.descargaFallida causa=IllegalStateException: el cuerpo supera 2097152 bytes sin declararlo
config.desdeCache canales=6 · catalogo.listo origen=CACHE
```

#### #27 — El inicio ya no consulta la LiveTV de Jellyfin

Eran **dos cosas**, no una. La consulta salía de `helper.loadOnNow()`
(`/LiveTv/Programs/Recommended`), que llena una fila con la programación de la
LiveTV **de Jellyfin** — la sección que reemplazamos. En el Jellyfin de un ISP
sale vacía o falla, y mientras tanto gasta una petición en cada carga del inicio.

Y al mirarlo apareció la otra: la fila de botones ofrecía **cuatro**, y tres de
ellos —grabaciones, programación y series— llevan de vuelta a la sección vieja y
a funciones de DVR que están fuera de alcance (§7). O sea, un camino de regreso
a lo que la regla 5 declara inalcanzable.

Ahora la fila tiene **un** botón, el que lleva a la sección nueva, y la fila de
«en emisión» no se añade. Las ramas del `when` se quedan sin botón que las
dispare pero **no se borran**: el código viejo permanece en el árbol e
inalcanzable, que es justo lo que pide la regla 5.

Medido antes y después: `GET /LiveTv/Programs/Recommended` pasa de dispararse en
cada carga del inicio a **cero apariciones**.

**Esto añade `HomeRowsFragment.kt` a la lista de enganches** de la verificación
(a). Se justifica por §4 regla 5 y §7: la sección vieja no debe ser alcanzable y
el DVR está fuera de alcance. El camino de VOD sigue intacto.

#### Lo que se vio de paso y no se tocó

**La fila de inicio solo aparece si el usuario de Jellyfin tiene
`enableLiveTvAccess`.** Es un permiso del Jellyfin del ISP que no tiene nada que
ver con nuestros canales: en un Jellyfin sin LiveTV configurada puede estar
apagado y entonces la fila no sale. **La barra principal no está condicionada**,
así que el camino bueno siempre funciona. Pendiente #31.

**Estado:** cerrada.

### Tres proyectos en sintonía, y el mecanismo para que siga pasando — 2026-10-02

Al montar el autorizador quedaron tres repositorios que dependen de un contrato
que vive en uno de ellos. Eso se desincroniza solo si no se diseña para no
hacerlo.

**Los contratos los escribe este proyecto**, porque es quien decide qué sabe
leer. Los otros dos llevan una **copia** con el commit del que salió y el comando
para comprobar si se quedaron atrás:

```bash
git -C /home/proyectos/jellyfin-livetv-v1 log --oneline -1 -- CONFIG_SCHEMA.md
```

**Y la regla que lo sostiene, en `CLAUDE.md` §1:** si cambia un contrato, o
cambia el comportamiento que describen los `INTEGRACION_*.md` —tiempos de espera,
escalera de respaldo, qué se descarta, qué se mide— se actualiza el documento de
integración **en el mismo commit**. Un documento de integración desfasado es peor
que no tenerlo: se cree.

#### Por qué los contratos no bastaban

`CONFIG_SCHEMA.md` dice **qué** emitir. No dice que la app espera 5 s y 10 s y no
reintenta —así que generar el `config.json` a demanda es mala idea—, ni que un
canal descartado **no se ve en pantalla**, solo en el log, ni que una lista vieja
gana a ninguna, ni que `logoUrl` y `audioDefault` se leen y no se usan todavía.

Eso es lo que llevan los dos documentos nuevos, uno por consumidor:

- `INTEGRACION_CANALES.md` — cuándo pregunta, la escalera de respaldo, qué se
  descarta en silencio y qué tira el documento entero, qué mide la app y prefiere
  sobre lo declarado, lo que no va a hacer nunca, y el procedimiento completo
  para probar contra una caja con las líneas de log que son el veredicto.
- `INTEGRACION_REGISTRO.md` — lo mismo para el autorizador, con un apartado
  dedicado a la normalización del host, que es donde se rompe esta integración:
  falla **para unos clientes y no para otros**, que es el síntoma más caro de
  diagnosticar. Las doce pruebas de `ServiceUrlTest` son su especificación
  ejecutable y se piden como pruebas del otro lado.

#### Un cabo que el autorizador no puede resolver solo

**Su dirección va compilada en la APK.** La app tiene que saber a quién preguntar
antes de preguntar nada, así que no puede venir del registro. Cambiarla después
del primer despliegue exige una versión para todos los ISPs a la vez. Quedó como
hallazgo abierto en ese proyecto: elegir el dominio antes de producción, y uno
que no ate a un proveedor.

**Estado:** cerrada. Sin tocar código.

### El registro de sites: lo que compra no es lo que parece — 2026-10-02

Edgar propuso un autorizador en la nube: la APK pregunta si el host que escribió
el abonado pertenece a un ISP con contrato, y el registro le devuelve además
dónde está su servicio de canales y su marca.

**El argumento bueno no era el control de acceso.** Lo que de verdad compra es
**matar la convención del puerto**. Hoy la APK encuentra el servicio de canales
cambiando el puerto por una constante compilada, y eso obligó a escribir «fíjalo
antes del primer despliegue o tocas todos los sites a la vez que la app». Con
registro, cada site dice dónde está el suyo y cambiarlo es editar una fila.

| | Sin registro | Con registro |
|---|---|---|
| Dar de alta un ISP | instalarle el servicio | **+ una fila** |
| Quitarle el acceso | ir a su site | una fila |
| Mover el servicio de canales | imposible sin publicar versión | una fila |

**Y una corrección a mí mismo.** El 02-oct escribí que el registro central de
marcas «sobraba» porque el site sirve la suya. Sobraba *para marcar*. No para
autorizar ni para ligar, que son otra cosa, y esos dos usos lo justifican solos.

#### Lo que no es, escrito para que nadie se lleve un susto

**No es un candado técnico.** Este fork es GPL-2.0: el código es público por
obligación, la comprobación se ve a simple vista, y una APK de Play se decompila
en minutos. Es un **interruptor operativo sobre gente que coopera** —un ISP con
contrato al que se deja de autorizar queda fuera sin visitar su site— y para eso
basta. Lo que no hará es impedir que alguien decidido la use sin pagar; para eso
el control tendría que estar en el acceso al contenido, que es autenticación de
abonado y está fuera de alcance.

Queda escrito en `REGISTRY_SCHEMA.md` §1 con esas palabras, porque un documento
que lo presente como seguridad invita a construir sobre él cosas que no aguanta.

#### Las tres reglas que salieron de preguntarse qué pasa cuando falla

Un autorizador mal diseñado es **un punto único de fallo entre nuestro servidor y
la televisión de todos los abonados de todos los ISPs a la vez**.

1. **La autorización caduca y mientras tanto no se vuelve a preguntar.** En
   semanas, no en horas, decidido por Edgar. El daño de cortar de más es mucho
   peor que el de cortar tarde.
2. **Sin respuesta y vigente, se entra.** Hay despliegues donde la caja llega a
   la red del ISP pero no a internet.
3. **La puerta cubre solo la sección de canales.** Bloquear el login de Jellyfin
   bloquearía también el VOD que se sirve con Katalog, y convertiría un fallo del
   registro en un ISP entero sin nada.

Más un periodo de gracia cuando caduca y el registro no contesta: cortarle a un
cliente que paga por un problema de red propio es peor que tardar en cortarle a
uno que no paga.

**`until` es obligatorio en la respuesta.** Una autorización sin caducidad es
eterna, y bastaría un fallo del registro para dejar una caja autorizada para
siempre sin forma de retirarlo.

#### Decidido sin preguntar, por si alguien discrepa

- **La puerta solo cubre TV en vivo.** Se propuso, Edgar no objetó y se escribió
  así. Es reversible.
- **Cajas que nunca alcanzan internet:** no se construye hasta que el escenario
  sea real, pero el diseño no lo impide —la autorización viajaría firmada dentro
  del `config.json` del site—. Queda escrito para que nadie cierre esa puerta sin
  darse cuenta.

#### De paso, una trampa para el otro proyecto

Al confirmar Edgar que Flussonic puede estar en otra IP: en la APK no hay nada
que hacer —las URLs viajan en el `config.json` y el HTTP en claro ya está
permitido—, pero el generador del site puede leer la API de Flussonic por una
dirección interna y **emitir esa misma dirección**. Aquí se vería bien y en la
sala del abonado no cargaría ningún canal. Anotado como regla 9 y como hallazgo
en `/home/proyectos/servidor-canales`.

**Estado:** cerrada. Propuesta en `REGISTRY_SCHEMA.md`, `CLAUDE.md` §1/§6/§8 y
`LIVETV_PLAN.md` B9. Sin tocar código.

### El contrato del `config.json`, escrito — 2026-10-02

El servicio de canales de cada site es un desarrollo aparte, así que la frontera
entre los dos proyectos tenía que dejar de estar en mi cabeza. `CONFIG_SCHEMA.md`
documenta cada campo: tipo, si es obligatorio, qué hace la app con él y **qué
pasa si falta**.

**Tres estados por campo, y la distinción importa:** *implementado* (la app lo
lee y lo usa), *interpretado* (el parser lo lee y nadie lo consume todavía) y
*acordado* (decidido en `CLAUDE.md`, aún sin parser). Un servicio que emita solo
lo implementado funciona completo; lo demás se puede emitir desde ya porque la
app ignora lo que no entiende.

**Escribirlo destapó tres cosas que iba a documentar mal**, y todas por asumir
en vez de mirar:

- **`logoUrl` se interpreta y no se dibuja.** La barra y la guía enseñan siempre
  iniciales. Pendiente #28.
- **`audioDefault` se interpreta y no se aplica.** El `config.json` puede decir
  qué idioma arranca y la app lo ignora; elige la que elija Media3. La
  normalización de idioma y la selección por identidad ya existen: falta
  aplicarla al preparar. Pendiente #29.
- **La insignia HD no es lo que creía.** No sale solo de la medición: es
  `channel.hd || videoHeight >= 720`. La combinación es mejor que cualquiera de
  las dos sueltas, pero no era lo que estaba a punto de escribir.

Y dos límites que el código no cumple como parecía:

- **El tope de 2 MB solo mira la longitud declarada.** Una respuesta troceada se
  lo salta. Pendiente #30.
- **El `Content-Type` no se comprueba.** Se interpreta el cuerpo venga como
  venga. Queda como recomendación al que escriba el servicio, no como requisito.

**Las cinco cosas que más fácil se hacen mal** cierran el documento, y la
primera es comprobar que el audio es AAC antes de publicar un canal: es la
defensa más barata contra la avería que no se ve.

**Estado:** cerrada. Sin tocar código.

### [B6] El audio que no llegó normalizado se dice, no se adivina — 2026-10-02

Primera defensa de las tres que pide `CLAUDE.md` §2 desde que el AAC dejó de
estar garantizado. La que vive en la app.

**Existe por cómo falla, no por cómo suena.** La caja de pruebas decodifica
`audio/mpeg` y `audio/ac3` por hardware, así que un canal mal normalizado se oye
perfecto aquí y es silencio en una caja barata. Sin esto, el síntoma que llega
es «un abonado no tiene sonido» y hay que ir al site a buscarlo.

**El aviso dice si esta caja puede decodificarlo**, que es lo que separa dos
problemas distintos: `audio.noNormalizado` en WARN cuando suena pero solo
porque esta caja trae el decoder, y `audio.sinDecoder` en ERROR cuando está
mudo aquí y ahora. El segundo entra en el volcado automático porque es una
visita al site, no una curiosidad.

Se da **una vez por sintonía**, no por evento de pistas, o un canal con audio
intermitente llenaría el log.

#### Probado fabricando el fallo

Los seis canales del lineup son AAC, así que el aviso no se dispara solo. Se
construyó un canal mal normalizado —`beisbol` con el vídeo copiado y el audio
recodificado a MP2— servido desde un servidor de prueba, con el `config.json`
**declarando `aac`**, que es justo la mentira que se quiere cazar:

```
tracks.crudo  audio[es/mpeg-L2!nosoportada*]
WARN audio.noNormalizado canal=mp2-sin-normalizar declarado=aac
     observado=audio/mpeg-L2 modelo=4K Google TV Box puedeDecodificar=true
decoder.audio name=OMX.amlogic.audio.decoder.mp2
```

Y con un canal real, ninguna marca: `audio.normalizado codec=audio/mp4a-latm`.

**La rama `audio.sinDecoder` no se probó en la caja**: no hay un códec de audio
válido en MPEG-TS que esta Skyworth no sepa decodificar. Difiere en un booleano
que sale de `DeviceCapabilities.decodersFor`.

#### También en pantalla, porque el nombre del decoder no basta

El overlay resumía `OMX.amlogic.audio.decoder.mp2` como `a:amlogic.audio` y
perdía justo el códec. Ahora escribe el códec **solo cuando no es AAC**:

```
mp2-sin-normalizar  v:amlogic.avc  a:amlogic.audio ⚠MPEG-L2  1280x720→16:9
estrellashd         v:amlogic.avc  a:android.aac             1440x1080→16:9
```

Cuando todo está bien no gasta sitio; cuando el site está mal, el dato está en
pantalla sin pedir el log.

#### Un filo del commit anterior, encontrado al probar

La caché se indexa por servidor de Jellyfin, pero el override del menú cambia
la **fuente** sin cambiar el servidor: apuntar la caja a un servidor de pruebas
guardaba esa lista en la caché del site real, y al quitar el override la caja
se quedaba con el lineup equivocado. Ahora el override usa su propia clave.
Era un footgun de técnico, no de producción, pero de los que se descubren tarde.

**Estado:** cerrada.

### [B1] La URL de canales se deriva del host de Jellyfin — 2026-10-02

Primer paso real de la arquitectura por site. Hasta hoy la URL venía de
`BuildConfig.LIVETV_CONFIG_URL`, que está **vacía**: la caja llevaba días
viviendo de una caché que no podía refrescar, y por eso estuvo horas con
tunneled apagado sin que nadie lo notara.

**La convención es el puerto, no una ruta.** Un puerto solo lo ocupa un
proceso, así que el servicio de canales no puede escuchar en el de Jellyfin. Se
eligió el puerto propio sobre una ruta tras proxy porque funciona haya nginx o
no, y montar proxy en cada site es trabajo que no hace falta. Queda en
`LiveTuning.CHANNEL_SERVICE_PORT = 8097`.

**Lo que se descarta de la dirección y por qué.** Ruta, consulta, ancla y
credenciales: Jellyfin puede estar en `https://tv.isp.mx/jellyfin` y el
servicio sigue en `https://tv.isp.mx:8097`, misma máquina y otro proceso. El
esquema **se conserva** —asumir `http` mataría la sección en un site con TLS, y
asumir `https` en todos los demás— y una dirección sin esquema se rechaza en vez
de adivinarse.

Doce pruebas sobre `ServiceUrl`, que existen por cómo falla esto: un error aquí
no se ve, porque la app cae a la caché o a la lista de arranque y **parece que
funciona**, enseñando los canales de prueba en vez del lineup del ISP.

**La caché pasa a ser por servidor**, `config-<uuid del servidor>.json`. Con la
URL derivada, una caja que cambie de site heredaría la lista anterior: canales
que ya no existen, apuntando a un Flussonic que no es el suyo. La clave es el
identificador del servidor y no su dirección, para que un ISP que cambie de
dominio no pierda su lista.

**Override desde el menú** (`configUrlOverride`) para apuntar una caja a un
servidor de pruebas sin tocar su Jellyfin. **Degrada**, a propósito: una caja
mirando otro lineup no mide lo que parece.

**Verificado en la caja, los tres caminos:**

```
# derivado, sin servicio levantado → respaldo
catalogo.destino url=http://192.168.20.249:8097/config.json procedencia=derivada de Jellyfin
config.descargaFallida … ConnectException
catalogo.desdeArranque canales=6

# override a un servidor de prueba → red
catalogo.destino url=http://192.168.11.102:8097/config.json procedencia=menú
config.desdeRed canales=6 · catalogo.listo origen=RED grupos=4
engine.prepare … degradacion=config=manual

# derivado otra vez, ya con caché del site → caché
config.desdeCache canales=6 antiguedadHoras=0 · catalogo.listo origen=CACHE
```

Y el archivo en disco: `config-6c4a5bb5-b704-4a82-b1d0-54b3ab58aaca.json`.

**Pendiente menor:** cada servidor que una caja vea deja su archivo de caché
(2.6 KB). Acotado y sin consecuencia en producción —una caja ve un site— pero
una caja de pruebas los acumula.

**Visto de paso, sin tocar:** el inicio sigue pidiendo
`/LiveTv/Programs/Recommended` al Jellyfin, o sea la sección LiveTV **vieja**
del upstream sigue consultándose aunque sea inalcanzable (regla §4.5). No es
regresión de este bloque. Pendiente #27.

**Estado:** cerrada.

### Un servidor por site: la arquitectura cambia de forma — 2026-10-02

Hasta hoy los documentos describían **un** servidor de configuración, nuestro y
central, y una APK por ISP vía `productFlavors`. Edgar aclaró la topología real
y es otra:

```
EN EL SITE DE CADA ISP
  Satélite → Astra → Flussonic        ← headend propio del ISP
  Jellyfin                            ← VOD, lo alimentamos con Katalog
  Servidor de canales + EPG           ← lista, marca y guía
                 ↑ LAN
         APK (una sola, ficha nuestra en Play)
```

**Katalog no toca la app.** Lleva el VOD al Jellyfin local; la app solo ve
Jellyfin. Al lado se añade el servidor de canales para que la misma APK tenga
VOD y TV en vivo.

#### Lo que esto simplifica

**Los flavors desaparecen.** Los ISPs no quieren ficha propia —puede ser la
nuestra— así que una APK, una firma, una revisión de Google, un despliegue. El
precio aceptado: somos el editor de registro, y las reseñas, el soporte y
cualquier aviso de política caen sobre nosotros; y una APK mala rompe a todos a
la vez, lo que obliga a usar de verdad el despliegue por fases de Play.

**El registro central de marcas sobra.** Llegué a proponer un mapa
host → marca servido por nosotros. Con el servidor de canales en el site, la
marca sale de ahí junto con la lista: nada que mantener, nada que actualizar
cuando un ISP cambie de dominio. Dar de alta un proveedor es instalarle el
servidor.

**El huevo y la gallina se resuelve solo.** El abonado ya escribe la dirección
de Jellyfin; el servidor de canales se deriva de ese host. Cero configuración
añadida.

**El muro de la EPG cambia de lado.** B8 estaba parada en «aún no hay EPG
generado», que era producirla desde el EIT de Astra. Eso pasa a ser trabajo del
headend y de la tubería, igual que Katalog con el VOD. Lo que queda en la app
—bajar, interpretar, cachear, pintar actual y siguiente— es trabajo normal.

#### Lo que esto rompe, y es lo serio

**El AAC deja de ser una garantía.** `CLAUDE.md` §2 permite una sola excepción
—normalizar el audio a AAC en Flussonic— y §6 cuelga de ella la decisión de no
llevar decoders de MP2, MP3 ni AC-3. Eso valía con **un** headend nuestro. Con
uno por ISP es un requisito de despliegue, y se cumple donde lo configuremos
bien.

**Falla en silencio y caro.** Medido en la caja de pruebas:

```
audio/mp4a-latm  audio/mpeg  audio/ac3  audio/eac3  audio/vnd.dts …
```

La Skyworth decodifica MP2 y AC-3 por hardware. Un canal mal normalizado **se
oye perfecto aquí** y es silencio en una caja barata. Nadie se entera hasta que
llama un abonado de un site al que hay que ir — justo lo que advierte §9 sobre
desarrollar contra la mejor caja posible.

Tres defensas, las tres adoptadas: el generador del `config.json` no publica un
canal que no sea AAC; la app registra el códec recibido y avisa cuando no lo
es; y la guía de Flussonic pasa a ser el manual de despliegue de cada site. Lo
que **no** se hace es reintroducir decoders: la salida es arreglar el site, no
cargar en ~120 cajas el peso de un headend mal configurado.

**Los `deviceOverrides` no son del ISP.** «Este modelo no soporta tunneled» se
gana una vez mirando una pantalla y vale para todos. En el archivo de cada site
habría que copiarlo a N sitios a mano, y el día que se salte uno ese ISP
arranca sin imagen en un modelo que ya sabíamos que fallaba. La salida es
**generar** el archivo, no editarlo: la herramienta que lee la API de Flussonic
inyecta nuestra tabla de modelos al generarlo.

**El esquema tiene que sobrevivir a sus propios servidores.** La APK se
actualiza para todos a la vez por Play; los servidores, site por site. Una APK
nueva convivirá con servidores viejos durante meses. Y el cache hay que
indexarlo por servidor: hoy es un solo `files/livetv/config.json` y una caja
que cambie de servidor hereda la lista de la anterior.

**`CHANNELS.md` cambia de significado**: pasa de «los canales» a «el lineup de
referencia». Los de producción serán otros y nadie los habrá medido. Es
exactamente para lo que se escribió la cascada de M3, un día antes de saber que
hacía falta también para esto.

#### Una corrección

Dije que dónde vive Flussonic era «la pregunta que decide el resto». Exageré:
para el diseño de la APK da igual, la lista trae las URLs que traiga. Donde sí
importaba era en si lo medido valía, porque todo este repositorio está medido
contra una LAN. Con un headend por ISP, **es LAN** y las cifras valen.

#### Una APK o N: la decisión, y por qué esta

Edgar dudó entre una APK para todos y una por ISP. La duda venía de tratar como
una sola pregunta lo que son dos: **cuántas fichas de Play** y **cuántos
códigos fuente**. Se pueden tener N fichas desde un solo código — para eso
existen los `productFlavors`.

**N códigos fuente queda descartado y no se re-discute.** Todo §4 existe para
que los rebases contra upstream sean triviales; con forks divergentes, cada
arreglo del motor —como el de la constante del extractor de este mismo día— hay
que aplicarlo N veces, y a la tercera ya no son iguales.

Entre una ficha y N, decidió la **asimetría**, no el costo:

- Empezar con una y que un ISP exija la suya: se añade un flavor flaco
  —`applicationId`, nombre, icono, servidor por defecto— en un día, y la marca
  dinámica sigue funcionando dentro.
- Empezar con N y que nadie las necesitara: meses de N envíos a la tienda por
  cada versión, y consolidar exige migrar abonados entre `applicationId`, que
  **Play no permite**. Hay que reinstalar.

Las dos arquitecturas conviven. Una se amplía barato y la otra no se deshace.

**`vodEnabled` se retiró de las ranuras de marca:** siempre va a haber VOD.
Queda `VOD_ENABLED` en `build.gradle.kts`, a limpiar cuando se toque código.

**Y se añadieron los contenedores genéricos** (§6). Buena parte de lo que un ISP
querrá añadir no es comportamiento sino texto que se muestra; con una lista de
pares etiqueta/valor que la app pinta sin saber qué son, eso deja de costar una
versión. Hay que definirlos ahora: después, los primeros datos extra ya se
habrán pagado.

**Estado:** propuesta escrita en `CLAUDE.md` §1, §2, §6 y §8,
`LIVETV_PLAN.md` B4, B5, B6 y B8, y `CHANNELS.md`. Revisada y aceptada por
Edgar. Sin tocar código.

### [M3] La cascada decide el plan por canal y por caja — 2026-10-02

Hasta hoy el reproductor llevaba esto cableado:

```kotlin
reason = "M0: plan fijo, sin cascada"
```

Media3, hardware, sin desentrelazar, para todo. Funcionaba porque la caja de
pruebas es el mejor caso posible —trae MPEG-2 por hardware— y nunca hizo falta
el segundo paso. En la ZTE puede no haberlo.

**Tres fuentes en el orden de `CLAUDE.md` §6, y el servidor no manda sobre la
realidad.** Una orden de usar libVLC donde no está compilado se registra y se
ignora; obedecerla a ciegas convertiría una errata en el `config.json` en un
canal muerto en miles de casas. `enginesAllowed` es filtro duro: un motor fuera
de esa lista no se elige **nunca**, ni como último recurso, ni aunque lo pida
el servidor.

**El plan es ahora el único lugar donde se decide.** Los flags del extractor
los trae él, no los recalcula el motor; y el selector de códecs obedece
`plan.videoPath` en vez de volver a mirar el menú por su cuenta. Daban la misma
respuesta, pero con dos sitios decidiendo lo mismo basta un descuido para que
el log diga una cosa y la caja haga otra.

**Probado contra cajas que no tenemos.** Diecinueve pruebas con capacidades
sintéticas: sin MPEG-2, sin nada, con libVLC y sin él. Probar la cascada contra
la Skyworth no ejercita nada, porque resuelve los seis canales en el primer
paso.

**Medido en la caja, los seis canales:**

```
plan.construido canal=estrellashd  motor=media3 video=HW deint=NONE   motivo=hw para video/avc
plan.construido canal=beisbol      motor=media3 video=HW deint=NONE   motivo=hw para video/avc
plan.construido canal=discoveryhd  motor=media3 video=HW deint=VPU    motivo=hw para video/avc
plan.construido canal=hbohd        motor=media3 video=HW deint=NONE   motivo=hw para video/avc
plan.construido canal=mileniosd    motor=media3 video=HW deint=NONE   motivo=hw para video/avc
plan.construido canal=estrellassd  motor=media3 video=HW deint=NONE   motivo=hw para video/mpeg2
```

Idéntico al plan fijo. Lo único que cambia en esta caja es que el motivo dice
la verdad. Edgar los vio los seis y los declaró bien.

**Un canal sin camino de decodificación ya no es pantalla negra:** sale su
nombre y la razón por la que no se puede reproducir.

**§4.6, el códec declarado es una pista y el stream es la verdad.** Si lo
observado desmiente al `config.json`, el plan se rehace. Solo se vuelve a
preparar si el camino cambia de verdad: un códec distinto que lleva al mismo
sitio no justifica cortarle dos segundos al abonado. Se registra siempre,
coincida o no, porque «no hubo discrepancia» y «nunca se llegó a comprobar» se
veían igual en el log y son cosas distintas cuando un canal no arranca.

```
plan.codecObservado canal=estrellashd declarado=h264 observado=video/avc
```

#### El fallo que esto destapó, y era mío

Al mover los flags del extractor al plan copié las constantes a mano:

```kotlin
const val FLAG_ALLOW_NON_IDR_KEYFRAMES = 1 shl 2   // mal
```

El valor real es `1`. **`1 shl 2` es `FLAG_IGNORE_H264_STREAM`.** Como ese flag
viene activo de fábrica, le estaba diciendo al extractor que descartara el
vídeo de los cinco canales H.264: audio perfecto, pantalla negra. MPEG-2 se
salvó por ser otro códec, y eso fue lo que lo delató —el único canal con pista
de vídeo en el log era justo el raro—. Los subtítulos CEA-608 habían
desaparecido con él sin que nadie lo notara.

Nació y murió dentro de la misma sesión: no llegó a ningún commit ni a ninguna
APK de Edgar. **Las constantes ahora se importan de Media3.** No hay ninguna
razón para copiarlas: el módulo ya depende del extractor.

Verificado después:

```
tracks.crudo grupos=4  video[?/avc*] | audio[esl/mp4a-latm*] | texto[?/cea-608] | t5
decoder.video OMX.amlogic.avc.decoder.awesome2 initMs=67
```

#### Dos hallazgos de paso

**El cache de la caja llevaba un `deviceOverrides` de pruebas con
`"tunneling": false`**, del servidor temporal del 30-sep. Con `CONFIG_URL`
vacía la app no puede refrescarlo nunca, así que la caja llevaba horas
reproduciendo con tunneled apagado, contra la política del 01-oct. Sustituido
por el `config.json` del repo; arranca con `tunneled=true` y `degradacion=de
fábrica`. **Mientras no haya un `CONFIG_URL` real, el cache es un estado
congelado que nadie va a notar.**

**`engine.attach` se llama ~2 veces por segundo** mientras reproduce (206 veces
en 115 s). El bloque `update` del `AndroidView` corre en cada recomposición y
las estadísticas recomponen cada medio segundo. Es anterior a esta fase y no se
tocó —es render, o sea Modo A, y §3 prohíbe arreglar dos cosas de motor en el
mismo commit—. Inunda el log y rota el buffer de logcat en menos de un minuto,
lo que estorba para diagnosticar. Pendiente #25.

#### La mitad degradada del cierre no se puede probar, y el motivo importa

`LIVETV_PLAN.md` cierra M3 con «los seis canales funcionan en modo normal **y**
en modo degradado»: vetar el MPEG-2 de hardware y ver que `estrellassd` sigue
por el camino de respaldo. **Esa prueba no puede pasar en esta caja**, y al ir
a montarla salió por qué. Decoders de vídeo de la Skyworth:

```
video/avc     OMX.amlogic.avc.decoder.awesome2      (hardware)
video/mpeg2   OMX.amlogic.mpeg2.decoder.awesome2    (hardware)
c2.android.*  hevc, vp8, vp9, mpeg4, h263           (software)
```

**No hay `c2.android.avc.decoder` ni `c2.android.mpeg2.decoder`.** De los dos
códecs que este lineup usa de verdad, la caja no trae ningún decoder de
software. El paso 2 de la cascada —software de plataforma— es **inalcanzable
aquí para H.264 y para MPEG-2**; se salta directo al paso 3.

Dicho de otro modo: **libVLC no es un refinamiento, es el único respaldo que
existe.** Hasta M6, vetar el decoder de hardware de un canal lo deja sin camino
y la cascada lo marca como no reproducible — que es la respuesta correcta y la
que se ve en pantalla, pero no es «funciona en modo degradado».

Conviene no generalizarlo: es una caja, no la regla. La ZTE puede traer los
`c2.android` genéricos. Pero era justo lo que la cascada existía para descubrir,
y lo descubrió el primer día.

**Estado:** cerrada la mitad normal —Edgar vio los seis canales—. La mitad
degradada queda **bloqueada por M6**, no pendiente de prueba. Ver pendiente #26.

### Los ajustes por modelo de caja ya llegan al motor — 2026-10-02

Escribí el analizador de `deviceOverrides` el 30-sep, lo probé con catorce
pruebas, y **nunca lo conecté**. Estaban interpretados y ahí se quedaban. O
sea: la salida de emergencia que le prometí a Edgar —«si una caja no soporta
tunneled, lo excluyo desde el `config.json` sin recompilar»— **no existía**, y
estábamos a punto de probar una caja nueva sin ella.

**Tres capas, y el orden importa: fábrica → servidor → menú local.**

El menú gana porque es la herramienta con la que se depura una caja concreta;
si el servidor pudiera pisarlo, no habría forma de probar nada en campo. Pero
gana **solo si alguien lo tocó**: un valor sin tocar no es una opinión, y por
eso la resolución mira si la clave existe en preferencias, no su valor.

Eso arregla de paso el problema del 01-oct, cuando el nuevo valor de fábrica de
tunneled no entró porque había un `false` guardado: ahora un valor guardado
significa «el menú manda aquí», que es exactamente lo que era.

**Los ajustes se funden campo a campo**, no en bloque: se puede decir «todas
las cajas de este fabricante sin tunneled» y además «y este modelo tampoco con
nonIDR», sin repetir en el segundo lo que ya decía el primero.

**Verificado contra un servidor de configuración real**, levantado para la
prueba y apuntando a la caja de pruebas con `"tunneling": false`:

```
config.interpretada version=1 canales=6 grupos=4 ajustesPorCaja=1
config.desdeRed canales=6
dev.ajustesDelServidor tunneling=false
engine.prepare ... tunneled=false  degradacion=de fábrica
```

El motor obedeció al servidor, y la línea de degradación sigue diciendo «de
fábrica» — que es la semántica correcta: **un ajuste del servidor es
configuración, no una desviación.** Solo el menú local degrada.

**Y por primera vez la lista salió con los datos de verdad**: grupos
«Nacionales», «Películas», «Documentales» y números 101, 102, 103, 201. Hasta
hoy todo lo que se había visto eran los seis canales del `BuildConfig`
numerados del 1 al 6.

**Dos defectos de la lista, corregidos al mirar la captura:** los títulos de
grupo salían en el gris del tema, pensado para el fondo claro de la app, y
sobre el negro de esta sección eran ilegibles; y el número del canal quedaba
cortado por el borde superior de la tarjeta.

### `mono.m3u8` sí entrega las dos pistas de audio — 2026-10-01

Edgar vio dos audios en Flussonic y ninguno en la app, y yo diagnostiqué que el
endpoint `mono.m3u8` solo exponía el primero. **Era falso.** Lo medí leyendo el
log en un momento en que HBO llevaba una sola pista.

**La comparación, con los tres endpoints del mismo canal:**

| | mono.m3u8 | index.m3u8 |
|---|---|---|
| Pistas de audio | `spa, spa, eng` | `spa, spa, eng` |
| Primer fotograma | 2424 y 2457 ms | 2606 ms |
| Conexiones al servidor | 3–4 | 4 |
| Rebuffers en 110 s | 0 y 1 | 0 |

**No hay razón para cambiar de endpoint.** `mono.m3u8` entrega lo mismo, y la
decisión de `CLAUDE.md` §6 se queda como está.

**Lo que de verdad pasa:** el segundo audio **va y viene con el programa**. A
las 17:32 HBO emitía una sola pista; a las 18:08, tres —y las tres aparecen
antes incluso del primer fotograma—. Un canal de cine añade y quita el audio
original según lo que esté dando, y eso explica que Edgar lo viera en el
servidor y no en la app: miraba en momentos distintos.

**Consecuencia para la interfaz, y no es menor:** el control de audio tiene que
poder **aparecer y desaparecer a mitad de canal**. Ya lo hace —se recalcula con
las estadísticas— pero conviene saber que ese es el comportamiento correcto y
no un parpadeo.

**Verificado de punta a punta:** con tres pistas presentes, el control se
habilita y el cambio funciona — `pista.seleccionada que=audio idioma=eng`, sin
cortar el vídeo.

**Y un defecto encontrado de paso:** con siete canales, la fila **no se
desplaza con el gesto táctil** y el séptimo quedaba inalcanzable. Con el mando
debería ir, porque el desplazamiento lo dispara el foco, pero **está sin
comprobar con el control**. Queda anotado.

### Tunneled pasa a ser el valor de fábrica — 2026-10-01

Decisión de Edgar, y **invierte una decisión cerrada de `CLAUDE.md` §6**, que
decía «desactivado por defecto, se activa por override remoto tras validar».
El documento queda actualizado.

**Por qué.** Es lo único medido que elimina los microcortes del cine en H.264,
y hoy se agotaron las alternativas:

| Vía probada hoy | Resultado |
|---|---|
| `targetOffsetMs` a 12000, para que el control de velocidad deje de corregir | **254 de 256 tropiezos siguen midiendo exactamente 333 ms** |
| `FLAG_DETECT_ACCESS_UNITS` (probado el 25-sep) | el fps pasó de 50.40 a 49.85: nada |
| libVLC (medido el 26-sep) | 47.99 contra nuestros 48.07: tampoco |
| Forzar vídeo por software | no se llegó a probar: Edgar cortó, y con razón — su mejor resultado posible era confirmar un diagnóstico, no dar una solución usable |

**Y la causa quedó cerrada con aritmética.** El dato que faltaba lo dio Edgar:
*«el audio no se detiene pero el vídeo sí»*. Eso descarta red, buffer y reloj, y
lo encierra en el camino de vídeo:

```
en 1.585 s de cine hay 38 imágenes
el flujo pide          95 campos  (cadencia 3:2)
el decodificador da    76 campos  (2 fijos por imagen)
faltan                 19 campos = 317 ms   →   medido 333 ms (20 refrescos)
```

El decodificador AVC de Amlogic ignora el `repeat_first_field` y se queda corto
un tercio de segundo cada ciclo; ExoPlayer, que sí respeta los tiempos
correctos, **tiene que esperar**. El reloj lo lleva el audio, y por eso el audio
nunca se detiene.

**Es la misma causa que los 48 fps de `estrellashd`**: un solo defecto del
decodificador, dos síntomas distintos según cómo reaccione el reproductor.

**El modelo es de exclusión, no de inclusión.** Tunneled viene activo en todas
las cajas; los modelos que no deban usarlo se apagan por `deviceOverrides` del
`config.json` con `"tunneling": false`. Edgar asume el riesgo conocido: su
fallo da pantalla negra silenciosa y es la función más dependiente del
fabricante de toda la pila.

**Un detalle operativo que apareció al fijarlo:** un valor de fábrica nuevo
**no pisa una preferencia ya escrita**. La caja tenía `tunneled=false` guardado
de cuando Edgar lo apagó a mano, así que hizo falta restablecer para que el
nuevo defecto entrara. Lo mismo valdrá en campo para cualquier caja donde
alguien haya tocado el menú.

**Verificado con todo de fábrica, sin tocar un interruptor:** `tunneled=true`,
`degradacion=de fábrica`, HBO a 1920x1080→16:9, **0 entregas tardías, 0 frames
perdidos, 0 rebuffers**, primer fotograma en 1813 ms.

### El letrero de «sin señal» ya entra — 2026-10-01

Cierra el pendiente 7, abierto desde el 22 de septiembre, y quita la causa más
probable del congelamiento de anoche.

**Lo que costó el rato fue un detalle de sintaxis que la documentación de
Flussonic no explica.** En `backup vod/archivo.mp4`, el `vod/` **no es una
carpeta**: es el nombre de una ubicación VOD que se declara aparte. La de Edgar
se llama `fallback`, así que la línea correcta era
`backup fallback/h264.mp4`, y cualquier otro prefijo devuelve `enoent`.

De paso, dos diagnósticos míos que la evidencia corrigió:

1. Dije que su configuración «no se estaba aplicando», a partir de que el
   playlist seguía con 12 segmentos y `TARGETDURATION 4`. El `enoent` demostró
   lo contrario: el `backup` estaba aplicado y disparaba al cortar la señal.
   Lo que no había cambiado eran los parámetros de segmentación, que es otra
   cosa.
2. Generé el letrero de MPEG-2 en MPEG-TS porque el contenedor MPEG-PS no
   admite AAC, y su instalación parecía aceptar solo MP4. **MPEG-2 cabe en
   MP4**, verificado con `ffprobe`; el rodeo no hacía falta.

**Lo que esto cambia para el motor.** Con el letrero, el playlist **nunca deja
de avanzar**: cuando la fuente calla, Flussonic publica el letrero y sigue
emitiendo segmentos. El cliente no se descuelga, no sale de la ventana y no
acaba pidiendo segmentos borrados — que es exactamente la cadena que dejó la
imagen muerta siete horas el 30-sep.

**No sustituye al vigilante del motor.** Un corte de WiFi largo deja al cliente
descolgado igual, y ahí el headend no puede hacer nada. Sigue pendiente la
decisión de Edgar sobre si ese vigilante va activo por defecto.

**Lo siguiente que hay que probar, y es corto:** entrar y salir del letrero es
un **cambio de formato a mitad de emisión**. Hay que confirmar que el cliente
sobrevive a la transición en los dos sentidos, porque es justo el tipo de
cambio con el que este decodificador se ha mostrado frágil.

### `tunneled` arregla los microcortes de HBO, y mis instrumentos son ciegos en ese modo — 2026-09-30

Primera observación con el latido de diagnóstico ya instalado, y sirvió para lo
que se construyó.

#### El microcorte de HBO, medido por fin

Edgar miró `hbohd` 59 s de fábrica. El log lo capturó con hora:

```
10:13:18.503  frames.entregaTardia huecoMs=333
10:13:20.091  frames.entregaTardia huecoMs=333
10:13:21.676  frames.entregaTardia huecoMs=333
…21 veces en 31 s
```

**Un fotograma llega 333 ms tarde cada 1.585 s, con regularidad de metrónomo.**
Coincide con lo que Edgar describe desde el 24-sep: «cada 2 segundos se queda
pausado y luego continúa».

Queda descartado todo lo de fuera:

| Sospechoso | Medido |
|---|---|
| Red | buffer 9–13 s, offset clavado en 11.1 s, **0 rebuffers, 0 reintentos**, posición siempre avanzando |
| Frames perdidos | **0** en toda la observación |
| El flujo de origen | cadencia 3:2 de manual: 238 intervalos de 50 ms y 237 de 33 ms, **sin un solo salto de PTS** |
| Fotogramas clave | GOP de 1.168 s contra tropiezos cada 1.585 s: **no coinciden** |

El tropiezo **se fabrica dentro de la caja**, entre el decodificador y la
pantalla. Y encaja con el 27-sep, cuando `hbohd` emitía vídeo entrelazado y
salió limpio (59.86 fps, 3 tardías en 12 s): lo que cambia es el contenido.
Este decodificador AVC maneja mal el telecine de H.264.

#### Y un hallazgo nuevo al sintonizar

Tras el primer fotograma, la imagen se quedó **8.5 s congelada** mientras el
audio y el reloj seguían:

```
10:13:05.757  frames=1   buffer 11.4s  posición avanzando
10:13:07.764  frames=0   buffer 11.9s  posición avanzando
10:13:09.769  frames=0   buffer  9.9s  posición avanzando
10:13:11.778  frames=0   buffer 12.6s  posición avanzando
10:13:13.786  frames=10  ← arranca
```

Pendiente de que Edgar confirme si se ve en pantalla.

#### Tunneled lo arregla — y aquí viene la corrección

Edgar activó tunneled y **los microcortes desaparecieron**. Es la segunda vez
que lo observa; la primera fue el 26-sep, sin instrumento con que medirlo.

**Mi medición no lo corrobora, y no puede.** Los 27 latidos de esa sesión dicen
`frames=0 fps=0.0`: en tunneled el decodificador escribe directo al hardware de
pantalla y ExoPlayer nunca suelta los fotogramas, así que
`setVideoFrameMetadataListener` no dispara. Un contador ciego no cuenta
tropiezos. Los «0 huecos» no prueban nada.

**Y eso invalida el pendiente 23, que yo mismo escribí.** Concluí que tunneled
dejaba `estrellassd` y `estrellashd` «sin un solo fotograma, pantalla negra
silenciosa» a partir de esos mismos dos instrumentos —el contador del overlay y
`SurfaceFlinger --latency`—, **que son ciegos igual**. Edgar miró `estrellassd`
con tunneled activo: **se ve bien**. La pantalla negra no existía.

Es la tercera vez en esta bitácora que una conclusión sale de un instrumento
que no podía ver lo que yo le pedía. Regla que queda escrita: **antes de
afirmar que algo no ocurre, comprobar que el instrumento podría verlo si
ocurriera.**

#### Lo que sí está medido de tunneled

| | Sin tunneled | Con tunneled |
|---|---|---|
| Primer fotograma (zapping) | 1 421 ms | **3 909 ms** |
| Arranque | limpio | `ERROR_CODE_FAILED_RUNTIME_CHECK`, se recupera solo |

El zapping se triplica y no cumple ni el objetivo (2 s) ni el aceptable (3 s).
Pero **3.7 de esos 3.9 s transcurren entre el error y la recuperación**: el
coste puede no ser inherente a tunneled sino a cómo lo pedimos. Va a M5.

### Barrido completo de los seis canales, con fuente y pantalla pareadas — 2026-09-26

Bloque autónomo. `nonIDR=on` y todo lo demás de fábrica. Ritmo medido por
conteo incremental, no por promedio.

| Canal | Decoder | Resolución → aspecto | Ritmo real | Zapping | Rebuffers | Frames perdidos |
|---|---|---|---|---|---|---|
| Estrellas HD | avc | 1440x1080 → **16:9** | 48.09 / 47.66 | 1627 ms | 0 | 0 |
| Béisbol | avc | 1280x720 → 16:9 | 29.95 / 30.00 | 1048 ms | 0 | 0 |
| Discovery HD | avc | 1920x1080 → 16:9 | 59.96 / 59.99 | 1080 ms | 0 | 0 |
| HBO HD | avc | 1920x1080 → 16:9 | 59.86 / 59.86 | 1094 ms | 0 | 0 |
| Estrellas SD | **mpeg2** | 544x480 → **4:3** | 59.94 / 59.92 | 1444 ms | 0 | 0 |
| Milenio SD | avc | 1024x576 → 16:9 | 29.98 / 30.01 | 1033 ms | 0 | 0 |

**Los seis reproducen. Cero rebuffers y cero frames perdidos en los seis**, y
el zapping entre 1.03 y 1.63 s, por debajo del objetivo p50 de 2 s.
`mileniosd` se mide **por primera vez**: lo desbloqueó el arreglo del
desplazamiento de la fila.

Las cadencias salen donde deben: `beisbol` y `mileniosd` (progresivos 29.97)
presentan cada imagen **dos refrescos** —377 y 383 intervalos de 33.4 ms—, y
`discoveryhd`, `hbohd` y `estrellassd` presentan **un refresco** por campo.

#### El decodificador AVC de Amlogic ignora el pulldown de H.264

Medido con fuente y pantalla **en el mismo instante**, que es la única forma
válida: un canal generalista alterna cine y vídeo a lo largo del día.

| Canal | Fuente en ese momento | Pantalla | Desviación |
|---|---|---|---|
| **Estrellas HD** | **cine** 23.35 img/s → 58.29 campos/s | 48.07 | **−17.5 %** |
| Estrellas SD | **cine** 23.99 img/s → 60.02 campos/s | 59.90 | **−0.2 %** |
| HBO HD | entrelazado 29.59 → 59.19 | 59.96 | +1.3 % |
| Discovery HD | entrelazado 29.57 → 59.15 | 59.96 | +1.4 % |

**Los dos canales de cine se comportan al revés, y lo que los separa es el
códec.** `estrellassd` es MPEG-2 y sale perfecto; `estrellashd` es H.264 y sale
a 48 fps, que es exactamente **2 campos por imagen**: el pulldown ignorado.

La explicación encaja con el formato: en MPEG-2 el pulldown viaja en
`repeat_first_field`, un campo de la cabecera de imagen que el decodificador
lee siempre. En H.264 viaja en **SEI `pic_timing` / `pic_struct`**, que es
opcional y este decodificador no honra.

Confirmado en tres mediciones independientes de `estrellashd` con cine al aire:
**48.09, 47.71 y 47.47 fps**, entre −17.5 % y −19.7 % del ideal.

**Consecuencia:** 48 fps contra un panel de 59.94 Hz de modo único produce un
batido de ~1 s. Es lo que Edgar describió como «se ve extraño» en VLC —que mide
**47.99** en el mismo canal—, y afecta igual a los dos motores. **No es un
fallo del cliente ni se arregla en el cliente**: está por debajo de Media3 y de
libVLC, en el decodificador de la caja.

#### Tunneled **no** lo arregla: deja el canal sin imagen

Se probó porque en modo tunneled la cadencia la lleva el hardware de
presentación. Resultado: `estrellashd` con tunneled da **0 fps, ni un solo
fotograma**, igual que `estrellassd`. Son ya **dos canales de dos probados** en
los que tunneled produce pantalla negra silenciosa. Refuerza el pendiente 23 y
lo sube de aviso a prohibición práctica: no puede activarse sin validar canal
por canal en pantalla.

#### Lo que hace falta de Edgar

1. **Visto bueno a `FLAG_ALLOW_NON_IDR_KEYFRAMES` por defecto.** Sin él
   `estrellashd` no arranca; con él no perjudica a ninguno de los seis.
2. **Juzgar el tirón de `estrellashd` con cine al aire.** Es el único defecto
   visual que queda y no tiene arreglo en el cliente. La decisión es si se
   acepta o si el canal se trata en el headend.
3. **Validar con el mando** que la fila llega hasta Milenio SD. La
   automatización entra con un toque y no recorre el mismo camino de foco, así
   que esto no lo puedo comprobar yo.
4. **Comprobar los aspectos**: `estrellashd` a 16:9 y `estrellassd` a 4:3, que
   son los dos anamórficos.

### `estrellashd` también es cine: 23.976 progresivo con 3:2 señalizado — 2026-09-26

Edgar vio el canal «extraño» en VLC y preguntó por qué el ritmo salía
irregular. La respuesta no estaba en el reproductor.

**Primero, un error de método propio.** El ritmo de presentación se venía
midiendo promediando los intervalos de `dumpsys SurfaceFlinger --latency`. Ese
búfer guarda 128 entradas y **conserva restos antiguos**: en una lectura el
intervalo más viejo era de 400 s y crecía 5 s por lectura. La media salía
contaminada. Contando fotogramas **nuevos**, con muestreo más rápido que el
giro del búfer (~2 s a 60 fps), VLC da **47.99 fps sostenidos** en este canal,
no «46–52 irregulares».

Quedan afectadas por el método malo las cifras de `hbohd` de fábrica publicadas
hoy (45.69 / 52.09 / 46.05). Las de `estrellassd` y `beisbol` **no**: su media
salía exactamente 16.7 y 33.4 ms, lo que solo es posible sin valores atípicos.

**Y la causa, medida sobre 12 s del canal en vivo:**

| Dato | Valor |
|---|---|
| Imágenes | 286 en 11.912 s → **24.010 fps** |
| `interlaced_frame` | **0 en las 286** |
| `repeat_pict` | `1,0,1,0,…` sin una sola excepción |
| Campos pedidos | 715 → **60.02 campos/s** |

Es **telecine suave**: 24 imágenes progresivas por segundo con
`repeat_first_field` marcando cuáles duran tres campos. Un reproductor que
ignore ese marcador saca 2 campos por imagen = **48 fps**, y 48 no divide a
59.94: unas imágenes duran un refresco y otras dos, con un batido de ~1 s. Eso
es lo que Edgar está viendo. No es falta de CPU: VLC decodifica por hardware y
consume 1.1 de 4 núcleos, con 258 % de los 400 % ociosos.

**Comparación en el mismo canal, con el método bueno y a los pocos minutos uno
de otro:**

| Reproductor | fps reales | Cadencia |
|---|---|---|
| **Nuestro motor** (con `nonIDR`) | **59.90** | 709 intervalos de 1 refresco, 13 de 2 |
| VLC | **47.99** | 48 contra 59.94 → batido de ~1 s |

Nuestro overlay, que es un instrumento independiente del compositor, dice 59.79
en la misma ventana: los dos coinciden. La primera ventana dio 56.99 porque
incluía el arranque.

**Nuestro motor reconstruye los 59.94 campos/s que el flujo pide; VLC no.** En
este canal el nuestro es claramente mejor, al revés de lo que sugería la
impresión visual. Además: SAR 1.333 resuelto a 16:9, 0 frames perdidos, 1
rebuffer y 892 ms de zapping.

**Consecuencias:**

1. **Pendiente 14 cerrado.** La contradicción 60000/1001 vs 30000/1001 era
   falsa: los dos eran valores nominales del contenedor.
2. **`estrellashd` deja de ser «1080i por campos»** en los documentos. No está
   entrelazado en origen.
3. **Dos canales de seis transportan cine a 23.976**, no uno. En `hbohd` la
   cadencia viene aplicada en los campos; en `estrellashd` viene solo
   señalizada. El motor tiene que tratar los dos casos.
4. La salida correcta en un panel de 59.94 Hz es la cadencia 3:2 —intervalos
   alternando 50 y 33 ms—, que es justo la firma ya medida en `hbohd`. Es
   decir: **lo que en `hbohd` se anotó como defecto es el comportamiento
   correcto**, y habrá que separar cuánto de lo que Edgar ve ahí es el tirón
   propio del cine 24p en 60 Hz y cuánto es un fallo de verdad.

### La degradación pegada invalidó dos conclusiones — 2026-09-26

**Edgar preguntó si alguna configuración nuestra se queda pegada y afecta a
otras apps.** La respuesta tiene dos mitades opuestas, y la segunda obliga a
corregir dos entradas de esta bitácora.

#### Nada nuestro sale del sandbox

Auditoría del módulo y verificación en la caja:

| Vector | Resultado |
|---|---|
| Permisos de `livetv/` | **solo `INTERNET`** |
| `Settings.Global` / `Settings.System` | **0** entradas nuestras |
| `Settings.Secure` | **1**: `media_button_receiver` → `playback.media3.session.AndroidMediaService`. La escribe la plataforma al publicar una `MediaSession`, es del camino de VOD del upstream, y solo enruta las teclas de medios |
| Propiedades del sistema (`getprop`) | **0** |
| Cambio de modo de pantalla / `setFrameRate` | **imposible en esta caja**: `dumpsys display` reporta **un único modo**, 1920x1080 @ 59.94006 Hz |
| `AudioManager`, sesión de audio, foco | el módulo no los toca |
| Servicios declarados | `AuthenticatorService` y `LibraryDreamService`, ambos del upstream. El salvapantallas activo del sistema es `dreamx/.service.Backdrop` de Google, no el nuestro |
| Huella en disco | 780 KB en 28 volcados automáticos en `Descargas` |
| Carga de la caja | 5–6 con 4 núcleos A35 y 862 MB de 2 GB disponibles, con Netflix, Tubi, Mplay, `launcherx`, `gms` y otra app de IPTV residentes. **Es su estado normal, no residuo de las pruebas** |

Que la caja tenga **un solo modo de pantalla** cierra además una puerta: nadie
puede dejar el display en otra frecuencia, y el cine 23.976 de `hbohd`
**siempre** va a necesitar conversión de cadencia contra 59.94 Hz. No es
ajustable desde el cliente.

#### Lo que sí estaba pegado: nuestro propio menú

Al arrancar hoy con la caja recién reiniciada, el overlay decía:

```
degradación   video=software  stuck=3.5  sin resync  tunneled
```

Cuatro interruptores del menú de degradación **sobrevivieron al cierre de la
app y al reinicio de la caja**, porque viven en `SharedPreferences`. Con el
overlay oculto no hay ninguna señal de ello. Y la combinación
`video=software` + `tunneled` es incompatible — el decodificador por software
no puede entregar en modo túnel — así que el canal **no entregó un solo
fotograma**.

**Esto no afecta a otras apps. Afecta a nuestras mediciones, que es peor,**
porque se publican como hechos.

#### Consecuencia: `estrellassd` no presenta a la mitad

Tras devolver todo a fábrica y medir con el mismo procedimiento:

| Sintonía | SurfaceFlinger (3 lecturas) | Overlay | Frames perdidos |
|---|---|---|---|
| 1 | 58.55 · 59.75 · 59.46 | 55.98 | 20 |
| 2 | 59.94 · 58.54 · 57.22 | 59.58 | 0 |
| 3 | 59.94 · 59.94 · 59.94 | 60.12 | 22 |
| 4 | 59.94 · 58.54 · 57.22 | 60.29 | 1 |
| 5 | 59.94 · 59.94 · 59.94 | 59.82 | 18 |

**Cinco sintonías frescas, cinco veces el ritmo completo.** Y el barrido por
canal, también de fábrica:

| Canal | Contenido | Presentación | Lectura |
|---|---|---|---|
| Béisbol | 720p **progresivo** 29.97 | 29.97 / 29.73 / 28.83 | **1:1** |
| Estrellas SD | 480**i** 29.97 MPEG-2 | 59.94 / 59.94 / 59.94 | **2×** |
| HBO HD | 1080**i** con cine 23.976 | 45.69 / 52.09 / 46.05 | 2× irregular |
| Estrellas HD | 1080i H.264 | sin fotogramas | necesita el flag nonIDR |

El patrón es limpio: **lo progresivo se presenta 1:1 y lo entrelazado al doble
del ritmo de cuadro, es decir al ritmo de campo.**

#### Dos correcciones

1. **«El decodificador MPEG-2 presenta a la mitad» no se sostiene.** La entrada
   de hoy con ese título midió 14.99 fps y 66.7 ms en `estrellassd`; hoy el
   mismo canal, con el mismo decodificador `OMX.amlogic.mpeg2.decoder.awesome2`
   y de fábrica, mide 59.94 en ocho mediciones seguidas. La lectura de 14.99 no
   es reproducible. Aquella entrada afirmaba haber descartado la degradación
   «con las preferencias borradas», y el volcado de hoy demuestra que **no
   estaban borradas**.

2. **Queda sin premisa la nota «libVLC no sortea el problema del decodificador
   MPEG-2».** Se apoyaba en que VLC medía 14.98 en el mismo canal. Medido hoy en
   arranque limpio, antes de que nuestra app se ejecutara siquiera: **59.94 fps,
   16.7 ms**. No había nada que sortear en esa medición. Lo que libVLC aporta
   —decodificador MPEG-2 propio y desentrelazador por software— sigue siendo
   válido para cajas que no traigan decodificador MPEG-2, que es el alcance que
   `CLAUDE.md` §6 le da; pero **no** está justificado por esta medición.

#### Pendiente 1, respondido con evidencia

**¿Desentrelaza el VPU? Sí.** La prueba no es una lectura suelta sino el
contraste: el único canal progresivo del conjunto presenta 1:1, y los dos
entrelazados presentan al doble. Un decodificador que no desentrelazara
entregaría un cuadro por cuadro codificado en los tres casos.

#### Lo que se arregló para que no vuelva a pasar

El aviso de degradación ya no vive solo dentro del menú:

- En la lista de canales, una línea amarilla cuando hay algo fuera de fábrica.
- En el reproductor, la misma línea **cuando el overlay está oculto** — con el
  overlay visible ya aparecía.

Ambos se apagan solos al restablecer. Es interfaz de la sección (Modo B) y no
toca el motor.

#### Pendiente nuevo

`hbohd` de fábrica presenta **irregular**: 41.31 fps en el overlay, 45.69 /
52.09 / 46.05 en el compositor y **38 entregas tardías** con 0 rebuffers. Es el
cine 23.976 contra un panel de 59.94 Hz de modo único (pendiente 22), y sigue
siendo el caso real que la reproducción tunneled estabiliza.

#### El flag `nonIDR` queda limpio de la cámara lenta

Quedaba un hueco: en el barrido el único interruptor activo era `nonIDR=on` y
ya medía 14.93 fps. Tres sintonías frescas con **ese flag y nada más**:

| Sintonía | SurfaceFlinger | Overlay | Frames perdidos |
|---|---|---|---|
| 1 | 59.94 · 59.94 · 59.94 | 60.29 | 161 |
| 2 | 59.94 · 60.42 · 53.19 | 59.35 | 0 |
| 3 | 59.94 · 60.13 · 59.94 | 59.85 | 0 |

**El flag no tiene nada que ver con el ritmo de presentación**, que es lo que
decía el análisis del código: `FLAG_ALLOW_NON_IDR_KEYFRAMES` solo se consume al
construir el lector de H.264, y `estrellassd` es MPEG-2, así que sobre este
canal no puede hacer nada. Ahora está medido además de razonado.

Con esto son **once sesiones del mismo día a ritmo completo** (ocho de fábrica,
tres con el flag). El 14.93 del barrido sigue sin explicación mecánica, pero no
es del flag y no es reproducible.

#### Y la prueba cruzada, que era la pregunta literal

| Momento | VLC en el mismo canal |
|---|---|
| Arranque limpio, antes de que nuestra app se ejecutara | **59.94 fps / 16.7 ms** |
| Justo después de una sesión nuestra **con tunneled activo** | **59.94 · 59.94 · 59.94** |

**Nuestra app no le deja nada al de al lado.** Lo que sí salió de esa prueba es
un hallazgo propio: **con `tunneled` activo, `estrellassd` no entrega un solo
fotograma.** El decodificador elegido sigue siendo
`OMX.amlogic.mpeg2.decoder.awesome2`, el overlay marca `fps —` y el compositor
no ve nada. Es exactamente la «pantalla negra silenciosa» que `CLAUDE.md` §6
anticipa, y significa que **la reproducción tunneled no puede activarse de
forma global**: ayuda a `hbohd` (H.264) y mata al canal MPEG-2. Si entra, entra
por canal y por decodificador.

### Cámara lenta en `estrellassd` — el decodificador MPEG-2 presenta a la mitad — 2026-09-26 — **SUPERADA**

> **No sostener nada de esta entrada.** La sesión que la produjo llevaba cuatro
> interruptores de degradación pegados de pruebas anteriores, y la afirmación de
> haber medido «con las preferencias borradas» era falsa sin saberlo. De fábrica
> el canal mide 59.94 fps en ocho lecturas. Ver «La degradación pegada invalidó
> dos conclusiones», arriba. Se conserva porque el error, y cómo se detectó,
> valen más que la conclusión.

**Edgar vio dos canales «como en cámara lenta»: `estrellassd` y `hbohd`.**
Conecta con el dato que se había marcado como raro el mismo día: 14.93 fps
donde el contenido es 29.97.

**Medido con dos instrumentos independientes:**

| Canal | SurfaceFlinger | Overlay | Decoder | Aspecto visual |
|---|---|---|---|---|
| Béisbol | **29.73** | 29.38 | `avc` | correcto |
| Discovery HD | **29.97** | 29.94 | `avc` | correcto |
| **Estrellas SD** | **14.99** | 14.96 | **`mpeg2`** | cámara lenta |

El intervalo medido en `estrellassd` es de **66.7 ms**, exactamente **4
refrescos** de un panel a 59.94 Hz, cuando deberían ser 2. Cada fotograma se
presenta el doble de tiempo.

**El stream está bien:** 599 cuadros en 20 s = 29.95 fps, `field_order=tt`,
`avg_frame_rate` 29.97. No es la fuente.

**Y la línea de tiempo va a tiempo real:** el live offset se mantiene en
11 186 → 11 191 ms a lo largo de 100 s. No se está quedando atrás; presenta la
mitad de los cuadros a velocidad correcta.

**Descartado el flag.** Se sospechó de `FLAG_ALLOW_NON_IDR_KEYFRAMES`, pero con
las preferencias borradas y el motor **de fábrica** el canal sigue en 14.96
fps. El flag no tiene nada que ver.

**Lo que distingue a este canal es el decodificador:** es el único que usa
`OMX.amlogic.mpeg2.decoder.awesome2`. Los dos que van bien usan el de H.264.
`hbohd` es un caso aparte, con su cadencia 3:2.

**Dos cosas que corregir de lo dicho antes:**

1. Se declaró **respondido el pendiente 1** («¿desentrelaza el VPU?») a partir
   de una lectura de **59.91 fps** en este mismo canal. Hoy el mismo canal mide
   **14.99** con dos instrumentos. **Las dos lecturas no se pueden reconciliar**
   y no debió cerrarse un pendiente abierto desde el primer día con una sola
   medición. **Se reabre.**
2. El barrido de los seis canales concluyó que «el flag no rompe nada» mirando
   rebuffers y frames perdidos, que salían a cero. **El fps ya mostraba 14.93 y
   se anotó como “dato que no cuadra” sin perseguirlo.** La señal estaba y no
   se siguió.

**Va a M5.** Es render y desentrelazado, no red ni extractor.

**¿Lo sortearía libVLC? Medido: no.** Reproduciendo el mismo canal en VLC
sobre la misma caja, el compositor del sistema da **14.98 fps con intervalo de
66.7 ms** — idéntico a nuestros 14.99. El fallo ocurre **por debajo de ambos
reproductores**: es el decodificador MPEG-2 de hardware de Amlogic.

Eso acota el valor real de libVLC (fase M6): **no** es que sea mejor motor,
sino que **trae su propio decodificador MPEG-2 por software y su propio
desentrelazador**, que es justo lo que esta caja no tiene — se midió que su
único decodificador MPEG-2 es el de hardware. Coincide exactamente con el
alcance que `CLAUDE.md` §6 ya le daba: «MPEG-2 sin decoder en la caja, y
desentrelazado por software de SD».

**Prueba pendiente que decide si M6 merece la pena:** forzar decodificación por
software **en VLC** sobre este canal. Si entonces da 29.97, libVLC resuelve el
caso y M6 está justificado. Si sigue en 15, no lo resuelve nadie en el cliente.

### Barrido de los 6 canales con `FLAG_ALLOW_NON_IDR_KEYFRAMES` — 2026-09-26

El flag arregla `estrellashd`, pero era el tipo de ajuste que puede romper
otro canal. Barrido con el flag activo y **tunneled apagado**:

| Canal | Resolución | Aspecto | fps | Frames perd. | Rebuffers | Ratio | 1er frame |
|---|---|---|---|---|---|---|---|
| Estrellas HD (77W) | 1440x1080 | 16:9 | 29.91 | 0 | **0** | 0.000 % | 2241 ms |
| Estrellas SD (77W) | 544x480 | **4:3** | 14.93 | 0 | **0** | 0.000 % | 2120 ms |
| Discovery HD (70W) | 1920x1080 | 16:9 | 29.82 | 0 | **0** | 0.000 % | 2088 ms |
| **HBO HD (70W)** | 1920x1080 | 16:9 | 27.42 | **45** | **11** | **12.76 %** | 2077 ms |
| Béisbol | 1280x720 | 16:9 | 29.78 | 0 | 0 | 0.000 % | 2048 ms |
| Milenio SD | — | — | — | — | — | — | **no probado** |

**El flag no rompe nada.** Cuatro de cinco salen con cero rebuffers y cero
frames perdidos. Los aspectos son correctos en todos, incluido el anamórfico
20:17 de `estrellassd` resuelto a 4:3.

**Recomendación: activarlo por defecto.** Arregla un canal, no perjudica a
ninguno, y es lo que hace el reproductor comercial analizado. Queda pendiente
el visto bueno de Edgar, que es quien cambia un valor por defecto de Modo A.

**HBO necesita las dos cosas.** Con el flag solo se queda en 12.76 % de ratio;
es con **tunneled** cuando se estabiliza. Son problemas distintos: el flag
resuelve que no arranque, el tunneled resuelve la cadencia 3:2.

**Milenio SD no se pudo medir.** El D-pad no alcanza la última tarjeta de la
fila — es el defecto de foco anotado para **B2**, que ya no es solo estética:
**bloquea una medición**. Sube de prioridad.

**Dato que no cuadra y queda señalado:** `estrellassd` midió **14.93 fps**
donde antes dio **59.91** sobre la misma fuente de 29.97. Puede ser artefacto
de la ventana deslizante de 2 s, o que el VPU cambie de modo de desentrelazado.
No se da por bueno ninguno de los dos valores hasta entenderlo.

**Zapping:** el primer frame sale entre **2048 y 2241 ms** en los cinco. El
objetivo de §10 es p50 < 2 s, así que se queda **justo por encima**. Medido
con tunneled apagado; con tunneled la métrica no es fiable.

### Estrellas HD decodifica por primera vez — `FLAG_ALLOW_NON_IDR_KEYFRAMES` — 2026-09-26

**El fallo más antiguo del proyecto tiene causa.** `estrellashd` no arrancaba
desde el primer día: almacenaba datos, inicializaba los decoders y **nunca
reportaba un formato de vídeo**.

**La causa es que sus segmentos no empiezan en un IDR.** Por defecto ExoPlayer
espera un IDR y descarta todo lo anterior, así que nunca establecía el formato.
Con `FLAG_ALLOW_NON_IDR_KEYFRAMES` acepta I-frames con punto de recuperación y
arranca.

**Aislado a un solo flag.** Se activaron primero los dos que usa el reproductor
comercial más la búsqueda amplia; luego se fueron quitando uno a uno.
`FLAG_ALLOW_NON_IDR_KEYFRAMES` **por sí solo** basta:

```
resolución    1440x1080
SAR           1.333          ← el anamórfico 4:3 que CHANNELS.md documentaba
aspecto       1.778 (16:9)   ← correcto
1er frame     1381 ms
```

**Descartado en la misma sesión:** `setTsExtractorTimestampSearchBytes`
ampliado a 3000 paquetes **no** lo arregla. Se implementó
`WideSearchHlsExtractorFactory`, se verificó que la factory se invocaba
(`extractor.ventanaAmpliada`) y el canal seguía sin reportar formato. La
hipótesis era razonable y era falsa. `FLAG_DETECT_ACCESS_UNITS` tampoco aporta
nada por sí mismo.

**Pero no está resuelto, solo diagnosticado.** Con el flag, el canal
reproduce con **33 rebuffers en 1:44 y un ratio del 29 %**, frente a un
objetivo del 0.3 %. Pasa de «nunca decodifica» a «decodifica mal».

Lo más probable es que arrancar fuera de un IDR deje referencias incompletas
hasta el siguiente refresco real, y eso se traduzca en artefactos y
reinicios. Queda por comprobar si combinarlo con **tunneled** lo estabiliza:
**esa prueba no se ha hecho** — un intento salió inválido porque el
interruptor no llegó a activarse, y el dato se descartó en vez de darlo por
bueno.

**Lo que esto cambia:** el pendiente 5 y la fase **M4** dejan de ser una
sospecha («la medición dice OFF/OFF, la experiencia de campo sugiere lo
contrario») y pasan a tener una respuesta medida para un canal concreto. El
flag **no** debe activarse globalmente sin comprobar qué hace en los otros
cinco: es exactamente el tipo de ajuste que arregla un canal y rompe otro.

---

### Estrellas HD resuelto — eran **dos** causas — 2026-09-26

**Edgar aportó el dato que faltaba:** ese canal, aunque es de satélite, le
llegaba **por IP desde otro servidor con el enlace degradado**. Lo movió a un
servidor con mejor conexión.

Eso obligó a repetirlo todo, porque **las mediciones de inestabilidad estaban
contaminadas**: un enlace con pérdida destruye frames, y eso podía explicar
tanto los rebuffers como la ausencia de IDR.

**Verificado en el stream que no era el enlace.** Análisis de unidades NAL:

```
277  Coded slice of a non-IDR picture
  0  Coded slice of an IDR picture     ← ninguna, en absoluto
  4  Recovery Point (SEI)
```

**El canal no emite IDRs**, punto. Usa I-frames con punto de recuperación, que
es lo habitual en emisión para permitir empalmes. No es un daño del transporte:
es su codificación de origen.

**Con el enlace nuevo y el flag activo:**

```
En vivo · 1440x1080 · SAR 1.333 → 16:9
fps medido        29.86   (nominal 29.97)
frames perdidos   0
rebuffers         0
ratio             0.000 %
1er frame         2135 ms
```

**Control, con el enlace nuevo y SIN el flag:** sigue en «Almacenando…» con
`resolución —`. El flag es necesario **independientemente del enlace**.

**Las dos causas quedan separadas limpiamente:**

| Causa | Síntoma | Solución |
|---|---|---|
| Sin IDRs en el stream | nunca decodifica | `FLAG_ALLOW_NON_IDR_KEYFRAMES` |
| Enlace IP degradado | 33 rebuffers, 29 % de ratio | servidor con mejor conexión (Edgar) |

**Estrellas HD queda resuelto.** Van **6 de 6 canales**, por primera vez desde
que empezó el proyecto.

**Lección de método:** durante días se midió ese canal sin saber que su
transporte estaba degradado, y se construyeron hipótesis sobre datos sucios —
incluida la de que el decodificador de Amlogic «no podía» con él. Conviene
preguntar por la salud del transporte **antes** de atribuir un fallo al
cliente.

### Análisis comparativo de un reproductor IPTV comercial — 2026-09-26

Edgar aportó **StreamBox Mobile Pro** (CodeCanyon, v13.0, Java, 227 archivos).
Se analizó **para comparar técnicas y valores, nunca para copiar código**: su
licencia prohíbe redistribuir y este repositorio es GPL-2.0 y será público, así
que copiar de ahí violaría ambas licencias a la vez. El zip quedó excluido por
`.gitignore` — no estaba, y cualquier `git add -A` lo habría commiteado.

**Su motor es más simple que el nuestro.** No usa tunneling, ni
`LoadErrorHandlingPolicy` propia, ni configura `targetOffsetMs`. Reintenta
**5 veces con 600 ms** y se rinde — nuestra política de reintentos
efectivamente infinitos con backoff es mejor para el rain fade que exige
`CLAUDE.md` §6.

**Tres cosas que sí merece la pena probar:**

| Ajuste | Ellos | Nosotros |
|---|---|---|
| `setTsExtractorTimestampSearchBytes` | **3000 paquetes** (~564 kB) | por defecto (600, ~113 kB) |
| `FLAG_DETECT_ACCESS_UNITS` | **ON** | off |
| `FLAG_ALLOW_NON_IDR_KEYFRAMES` | **ON** | off |
| `bufferForPlaybackMs` | **1 000** | 2 000 |
| min / max buffer | 40 000 / 150 000 | 15 000 / 30 000 |
| Media3 | 1.9.1 | 1.8.0 |

1. **`setTsExtractorTimestampSearchBytes` es la pista más prometedora.** Buscan
   timestamps **cinco veces más lejos** que el valor por defecto. Si el
   extractor no encuentra referencias temporales dentro de su ventana, nunca
   establece el formato de vídeo — y **ése es exactamente el síntoma de
   `estrellashd`**: almacena datos, inicializa decoders y deja `resolución` en
   «—» para siempre. Merece probarse antes que cualquier otra cosa.
2. **Los dos flags de TS activados** son justo la «experiencia de campo» que
   cita el pendiente 5, ahora con una fuente concreta. Va a M4.
3. **`bufferForPlaybackMs` a la mitad** arrancaría antes; afecta directamente
   al zapping.

**Y una decisión de producto que conviene copiar como idea:** ofrecen al
usuario final un **interruptor de decodificación hardware/software**. Es una
salida pragmática para los canales donde el decodificador se atasca, sin
depender de un override remoto.

**Matiz sobre su configuración:** su modo «hardware» usa
`EXTENSION_RENDERER_MODE_PREFER`, que **prefiere** los renderers de extensión
sobre los de plataforma — lo contrario de lo que su propio comentario dice
(«try hardware first»). No es un modelo a seguir; `CLAUDE.md` §6 ya razonaba
mejor con `ON`.

**Lo que no aporta:** el tunneling que resolvió lo de HBO no lo usan. Ese
hallazgo es nuestro.

### Reproducción tunneled — la pregunta de Edgar que reabrió el caso — 2026-09-26

**Edgar preguntó: «si este canal va bien en otros reproductores, ¿por qué aquí
no?»** La pregunta era correcta y desmontaba la conclusión previa. Si otros
pueden, «no hay solución en el cliente» estaba mal: solo significaba que no se
había encontrado **el camino que ellos usan**.

**Lo estaba en nuestros propios documentos.** El decodificador declara:

```xml
<Feature name="adaptive-playback" />
<Feature name="tunneled-playback" />
<Feature name="low-latency" />
```

En modo **tunneled** el decodificador entrega directamente al hardware de
pantalla y el SoC gestiona sincronía, desentrelazado y cadencia en su propio
pipeline — que es para lo que Amlogic lo diseñó. Es el camino de las apps
nativas de TV. **El motor no lo usaba**, y `CLAUDE.md` §6 ya lo tenía anotado
como algo a activar tras validar.

**Implementado** como interruptor en el menú, apagado por defecto (§6: su fallo
produce pantalla negra silenciosa). Se pide por el selector de pistas con
`setTunnelingEnabled`.

**Medido con HBO puro, hardware + tunneled:**

```
decoder video     OMX.amlogic.avc.decoder.awesome2
1er frame         1346 ms
rebuffers         0
frames perdidos   0
fps medido        —
entregas tardías  —
```

**Las dos métricas vacías no son un fallo: son la firma del tunneled.** Los
frames van del decodificador a la pantalla sin pasar por la app, así que
`setVideoFrameMetadataListener` no dispara.

**Contrapartida seria:** en tunneled **el diagnóstico se queda ciego**
precisamente en las métricas que costó construir — fps medido y entregas
tardías, que son las que midieron este fallo. Si el tunneled se adopta, hay que
aceptar que el overlay pierde sus dos señales más finas, o encontrar otra vía
para obtenerlas.

**Error de método propio:** la primera medición salió contaminada porque quedó
activo el forzado por software de un experimento anterior. La degradación
mostrada en el overlay (`video=software tunneled`) fue lo que lo delató — que
es exactamente para lo que se puso ahí.

**Validado en pantalla por Edgar:** con tunneled, **los microcortes de HBO
desaparecen**. Ése es el dato fiable de que el tunneled produce buena imagen.

**Barrido de los cinco canales con tunneled:**

| Canal | Resultado |
|---|---|
| Estrellas HD (77W) | ✗ **sigue sin arrancar** |
| Estrellas SD (77W) | ✓ 1416 ms |
| Discovery HD (70W) | ✓ |
| HBO HD (70W) | ✓ (falló 1 vez de 9) |
| Béisbol | ✓ |

**Que Estrellas HD siga fallando confirma que son dos fallos distintos.** El
tunneled no toca su causa: High profile con transformada 8x8 sobre entrelazado.

**Repetibilidad de HBO: 8 de 8, dos veces** — antes y después de reiniciar la
caja.

**Pero la medición tiene un límite serio que hay que dejar escrito.** En
tunneled los frames no pasan por la app, y eso rompe tres cosas:

1. `fps medido` y `entregas tardías` quedan **siempre a cero**. Son las dos
   métricas que sirvieron para medir este mismo fallo.
2. `onRenderedFirstFrame` llega **tarde y de forma errática** — se midieron
   28 413 ms y 35 365 ms en canales que estaban reproduciendo con normalidad.
   **El tiempo de zapping no es medible en tunneled con la instrumentación
   actual**, y ése es el criterio de M8.
3. El estado «En vivo» viene de `onIsPlayingChanged`, **no de que haya
   imagen**. En tunneled los dos se separan, así que el «8 de 8» mide que el
   reproductor se declara reproduciendo, no que se vea algo.

**Corrección a una conclusión propia.** Antes del reinicio los tiempos crecían
de forma monótona (1.6 → 8.0 s) y se interpretó como degradación acumulativa.
Tras reiniciar **no se reprodujo**: salieron erráticos (2.1, 1.6, 28.4, 2.8,
3.8, 2.4). La interpretación era incorrecta.

**Dato de contexto medido:** la caja se estabiliza en **carga ~5.5 y ~200 MB
libres de 2 GB** recién reiniciada y sin reproducir nada. No era residuo de las
pruebas: es su estado normal. Durante la sesión larga llegó a 99 MB libres con
441 MB en swap, así que las pruebas **sí** añaden presión sobre una base ya
justa.

**Pendiente antes de adoptar el tunneled:** recuperar la medición de zapping y
de presencia de imagen por otra vía, o M8 se queda sin criterio.

### Qué atasca al decodificador de Amlogic — análisis del transcodificado — 2026-09-26

**Idea de Edgar:** transcodificar HBO en Flussonic hace desaparecer los
microcortes. Se analizó el transcodificado a fondo y se comparó contra las
capturas del canal puro guardadas de días anteriores.

**El dato que lo cambia todo:** el transcodificado se reproduce **con el mismo
decodificador de hardware** que se atascaba —
`OMX.amlogic.avc.decoder.awesome2`— en 1087 ms, a 23.86 fps, con 0 frames
perdidos. **El decodificador sí puede. Lo que lo atasca es cómo viene
codificado el contenido.**

**Comparación de los parámetros de secuencia (SPS):**

| | Transcodificado ✓ | HBO puro ✗ | Discovery ✓ | Estrellas HD ✗ |
|---|---|---|---|---|
| `profile_idc` | 77 Main | 77 Main | 77 Main | **100 High** |
| `level_idc` | **31** | 40 | 40 | 40 |
| `frame_mbs_only_flag` | **1** | 0 | 0 | 0 |
| `transform_8x8_mode_flag` | — | — | — | **1** |
| `pic_struct_present_flag` | **0** | 1 | 1 | 1 |
| `max_dec_frame_buffering` | **4 explícito** | ausente | ausente | ausente |

Los SPS de los **tres streams puros son idénticos entre sí** salvo Estrellas HD.
La cabecera no distingue a HBO-que-falla de Discovery-que-va.

**Donde sí está la diferencia: la estructura de imagen.**

| Stream | Imágenes de cuadro | Imágenes de campo | Resultado |
|---|---|---|---|
| HBO puro, **falla** | 488 | **3** | ✗ **mezclado** |
| HBO puro, funcionaba | 717 | 0 | ✓ uniforme |
| Discovery | 0 | 897 | ✓ uniforme |
| Transcodificado | 960 | 0 | ✓ uniforme |

**Los que funcionan son uniformes; el que falla mezcla.** Tres imágenes de campo
entre 488 de cuadro bastan para atascarlo. Eso es **PAFF**, permitido por
`frame_mbs_only_flag = 0`, y explica por qué HBO funcionó 31 minutos seguidos y
dejó de arrancar tras un cambio de programa: cambió el patrón de codificación,
no el canal.

**Estrellas HD es un caso distinto.** Es **uniforme** (61 imágenes de campo) y
aun así falla. Su rasgo único es **High profile con `transform_8x8_mode_flag`
sobre contenido entrelazado**: es el único canal que no es Main.

**Dos disparadores, no uno:**

1. **PAFF** — alternar imágenes de cuadro y de campo dentro del stream (HBO)
2. **High profile con transformada 8x8 + entrelazado** (Estrellas HD)

**Y aparte, la explicación de los microcortes.** El HBO puro transporta cine
23.976 mediante cadencia 3:2 dentro de un contenedor de 29.97. El
transcodificado hace **telecine inverso** y entrega 23.976 progresivo nativo
—medido, 23.86 fps en el motor—, así que no queda cadencia que malinterpretar.

**Nivel de certeza.** Está **probado** que el mismo decodificador reproduce el
transcodificado y no el puro, y están **medidas** las diferencias de
codificación. Que el disparador concreto sea el PAFF es una **correlación
fuerte sobre cinco streams**, no un aislamiento causal: haría falta construir
un stream de prueba que solo varíe eso.

**Confirmado al quitar el transcode — 2026-09-26.** Mismo canal, mismo
decodificador, misma caja, en cuestión de minutos:

| | Transcodificado | Puro |
|---|---|---|
| fps medido | **23.86** (= el contenido) | **49.85** |
| frames perdidos | 0 | 25 |
| **entregas tardías** | 2 (máx 250 ms) | **34 (máx 2268 ms)** |
| rebuffers | 1 | 2 |
| 1er frame | 1087 ms | 1524 ms |

**Las «entregas tardías» son la medida del síntoma de Edgar.** Un hueco máximo
de **2.3 segundos** entre frames presentados es, literalmente, la pausa cada
dos segundos que describió. La métrica se añadió el día anterior precisamente
porque los contadores de rebuffer y frames perdidos no veían nada, y ha
resultado ser la que mide este fallo.

El stream puro de esta captura: **955 imágenes de cuadro + 1 de campo**,
cadencia 3:2 perfecta (478 de 33 ms, 477 de 50 ms), 956 cuadros en 40.05 s =
23.87 fps. **Una sola imagen de campo entre 956** basta.

El motor rinde **49.85 fps sobre contenido de 23.87**: el VPU desentrelaza la
cadencia hacia ~60 fps y **no la sostiene**, quedándose en 50 y dando tirones.
Con el transcodificado entrega 23.86, exactamente el contenido, sin nada que
desentrelazar.

**Corrección a una corrección.** El día anterior se «corrigió» `CHANNELS.md`
porque decía `bb` y se midió `tt`. Esta captura vuelve a dar **`bb`**: el orden
de campo **varía con el contenido**. El documento no estaba mal — capturó otro
momento. La lección es que en este canal las propiedades no son estables, y
anotarlas como fijas es el error.

**Severidad variable.** Ayer el canal **no arrancaba**; hoy arranca con
tirones. El mismo canal, el mismo decodificador: lo que cambia es el contenido
emitido.

### El decodificador de hardware de Amlogic se atasca con cierto contenido — 2026-09-25

**Es el hallazgo más importante hasta ahora y reordena varias fases.**

**Cómo empezó.** Edgar vio microcortes en HBO. Persiguiéndolo, HBO dejó de
arrancar del todo — y con **la misma firma exacta que `estrellashd`**: almacena
datos sin problema, inicializa los decoders, **nunca reporta formato de vídeo**,
nunca entrega un frame, y **no registra ni un error**.

**La prueba decisiva**, hecha con el menú de degradación de M1:

| | Hardware (de fábrica) | Software forzado |
|---|---|---|
| Estado | «Almacenando…» para siempre | **«En vivo» en 730 ms** |
| Decoder | `OMX.amlogic.avc.decoder.awesome2` | `OMX.google.h264.decoder` |
| Resolución reportada | **—** | 1920x1080 |

**El stream es perfectamente decodificable. Lo que falla es el decodificador de
hardware.**

El registro del propio decodificador lo confirma:

```
15:17:39  AmlogicVideoDecoder: setOutputBuf ... :15   ← reserva búferes
          (silencio absoluto del decodificador de vídeo)
15:17:43  c2.android.aac.decoder: procesando
15:18:04  c2.android.aac.decoder: procesando          ← el audio sigue vivo
```

Coge sus búferes de salida y enmudece. El audio sigue decodificando, y como
ExoPlayer no arranca hasta que **todos** los renderers tienen datos, el
reproductor se queda esperando un vídeo que no llega.

**Es dependiente del contenido, no del canal.** HBO funcionó **31 minutos
seguidos** esta misma tarde y dejó de arrancar tras un cambio de programa. Los
parámetros declarados no distinguen a los que fallan de los que van:

```
HBO-ahora-FALLA     Main, 2 B-frames, nivel 40, refs 1
HBO-antes-OK        Main, 2 B-frames, nivel 40, refs 1
Discovery-OK        Main, 2 B-frames, nivel 40, refs 1
EstrellasHD-FALLA   High, 2 B-frames, nivel 40, refs 1
```

**El disparador no se ve leyendo la cabecera. Solo midiendo.**

**Lo que esto reordena:**

1. **`estrellashd` no es «un canal roto».** Es el mismo fallo de decodificador,
   y **la hipótesis de los flags del extractor (M4) queda muy debilitada**: ya
   se probó `FLAG_DETECT_ACCESS_UNITS` en HBO sin efecto alguno (fps 50.40 →
   49.85).
2. **No hay respaldo viable a 1080i en esta caja.** El software da **5.97 fps
   con 711 frames perdidos**: sirve para diagnosticar, no para ver televisión.
   **M6 (libVLC) pasa de opcional a crítico**, y aun así es dudoso que un
   Cortex-A35 sostenga 1080i por software.
3. **Riesgo de producto serio.** Si el decodificador se atasca con contenido
   real y emitido, el suscriptor ve pantalla negra sin aviso. No es un canal de
   pruebas raro: es HBO en emisión normal.

**Sin resolver:** qué tiene ese contenido que atasca al decodificador. Las
cabeceras no lo dicen.

**De regalo, el pendiente 1 queda respondido.** `estrellassd` mide **59.91 fps
sobre una fuente de 29.97**: el VPU de Amlogic **sí desentrelaza**, doblando la
cadencia. Era la pregunta abierta desde el primer día.

---

### Bucle de resincronización — fallo propio, corregido — 2026-09-25

Al perseguir lo de HBO apareció un fallo que **introduje yo** en M2. La vuelta
al borde de emisión se disparaba **mientras el canal todavía almacenaba**: el
salto reiniciaba el almacenamiento, el offset volvía a leer alto y disparaba
otra vez. Un salto cada 9 s que impedía arrancar.

**Y explica el `1er frame 240538 ms` de la prueba de 30 minutos, que descarté
como artefacto.** No lo era: eran cuatro minutos de bucle. Me lo salté
demasiado rápido.

Corregido con tres guardas: solo con imagen ya en pantalla, solo en estado
`READY`, y con 30 s de enfriamiento entre saltos.

**Nueva instrumentación:** «entregas tardías», que mide **cuándo sale cada
frame a pantalla** y no cuándo se decodifica. Es lo único que delata un tirón
cuando el stream está limpio, el refresco es correcto y los contadores de
rebuffer y frames perdidos están a cero.

### [M2] Medición y diagnóstico de estabilidad — 2026-09-24

**Medido, con la instrumentación de M1 y no con impresiones:**

| | `estrellassd` (77W) | `beisbol` (línea base) | Objetivo |
|---|---|---|---|
| Duración | **1 h 33 min** | 43 min | — |
| Rebuffers | 2 | 3 | — |
| Por hora | **1.3** | 4.2 | < 1 |
| Ratio | **0.035 %** | 0.264 % | < 0.3 % |
| Reintentos de red | **0** | 0 | — |
| Frames perdidos | 22 | 0 | — |
| 1er frame | — | **451 ms** | < 4000 |

**Los rebuffers no son inestabilidad continua.** A los 20 minutos `estrellassd`
ya llevaba sus 2; en los **73 minutos siguientes, cero**. Se agolpan al
arrancar y después el canal es sólido más de una hora.

**Dos correcciones a alarmas propias.** El «15 % de ratio» que se reportó al
construir el overlay estaba medido a los 30 s, con el denominador minúsculo;
sostenido baja a 0.035 %. Y el offset **no crece indefinidamente** como se dijo:
se estabiliza.

**Hallazgo principal: `targetOffsetMs` no se aplica.**

Comprobado con el menú de degradación de M1, que existe justo para esto:

```
pedido 8000 ms  → se estabiliza en 12380 ms
pedido 4000 ms  → 10180 → 10429 → 11509 → 11751 → 11752   (se aleja del objetivo)
```

El playlist es `#EXT-X-VERSION:3` con `#EXT-X-TARGETDURATION:4` y **sin
`EXT-X-SERVER-CONTROL`**. Sin esa etiqueta, la especificación HLS fija el
margen seguro en **3 × TARGETDURATION = 12 s**, y Media3 no baja de ahí por
mucho que se le pida. Los 12380 ms medidos son exactamente ese número.

Consecuencias:

1. **El `targetOffsetMs` por canal del `config.json` no va a funcionar como
   está diseñado** (`LIVETV_ENGINE.md` §5 y §8). El offset real lo fija el
   `TARGETDURATION` del playlist, que varía por canal (3 o 4 → 9 o 12 s).
2. **No es necesariamente malo.** Para los satelitales el documento pedía
   12000 ms por resiliencia ante lluvia, y 12380 es justo eso. El problema no
   es el valor, es que **no lo controlamos**.
3. **Para controlarlo hay que ir al headend:** que Flussonic emita
   `EXT-X-SERVER-CONTROL` con un `HOLD-BACK` explícito. Nuevo pendiente.
4. La regla de recortar el offset a la mitad de la ventana sigue siendo
   correcta, pero **no es la restricción que manda**: manda el suelo, no el
   techo.

**Implementado sobre ese diagnóstico:**

1. **Umbral de playlist detenido de 3.5 a 10** (vía `DefaultHlsPlaylistTracker`).
   Con 3.5 el error salta a los ~14 s y Flussonic tarda ~15 s en reconstruir el
   playlist: el defecto de Media3 saltaba **justo antes** de que el servidor se
   recuperase, convirtiendo un chubasco en un error de reproducción — lo que
   `CLAUDE.md` §6 prohíbe. Con 10 el margen sube a ~40 s. **Con interruptor en
   el menú para volver al 3.5 y comparar en pantalla.**
2. **El desajuste de offset deja de ser invisible.** El motor avisa una vez por
   sesión (`offset.noSeAplica pedidoMs=8000 realMs=10525 causa=...`) y el
   overlay lo marca en ámbar: `target offset 8000 ms (no se aplica)`.
3. **`LIVETV_ENGINE.md` §5 corregido.** Decía que el `targetOffsetMs` se
   controla por canal desde el `config.json`; ahora dice la verdad, que hay un
   suelo impuesto por HLS y que hace falta el pendiente 21 para controlarlo.

**Nota de método:** el constructor de `DefaultHlsPlaylistTracker` se comprobó
con `javap` sobre el `.aar` de Media3 1.8.0 en vez de suponerlo — el
coeficiente es el quinto parámetro, después del `CmcdConfiguration`, y las dos
suposiciones previas eran falsas.

**La prueba de corte de Edgar destapó un fallo mayor que el que arreglaba.**

Cortó la red ~15 s con el umbral nuevo y **el canal se recuperó solo**. Pero el
registro contaba otra cosa:

```
antes del corte   live offset ~10 500 ms
después           live offset  22 754 ms  → 22 754 → 22 754   (congelado)
ventana DVR       ~28 500 ms
```

Durante el corte el directo siguió avanzando y, al volver, el reproductor
retomó **donde estaba**, no donde está el directo. El offset pasó de 10.5 s a
22.75 s dentro de una ventana de 28, y **no vuelve solo**.

Es decir: **la recuperación funciona una vez.** Quedaban ~5 s de margen; otro
corte de más de 6 s habría tirado la posición fuera de la ventana. En una tarde
de lluvia con cortes repetidos, el canal acaba roto. Es justo el fallo que pasa
por bueno en una prueba de 15 segundos.

**Corregido:** el motor vuelve al borde de emisión cuando el offset supera la
mitad de la ventana. Reaccionar a `BEHIND_LIVE_WINDOW` cuando ya ocurrió
(`CLAUDE.md` §6) llega tarde; hay que volver **antes** de llegar ahí. Con
interruptor en el menú para comparar.

Verificado que **no se dispara sin motivo**: en operación normal el offset se
queda en 12.7 s sobre una ventana de 33 s —el 38 %— y hubo **0** resyncs.

**Lo que no pude verificar yo:** que el resync dispare durante un corte real. La
caja está en `eth0`, que es por donde va el `adb`: cortarle la red me corta a
mí. Esa prueba es de Edgar.

**Prueba de Edgar** (`LIVETV_PLAN.md` M2): 30 min de `estrellassd` (77W) y 30
de `hbohd` (70W) sin cortes ni saltos. El criterio es **< 1 rebuffer por hora**.

Y la que de verdad cierra lo nuevo: **cortar la red dos veces seguidas**, unos
15 s cada una. Antes, el offset se quedaba en ~22 s y el segundo corte habría
roto el canal. Ahora debe volver a ~12 s tras cada uno.

Conviene además probar el interruptor del umbral: con 3.5 y la red cortada unos
15 s debería aparecer error; con 10 debería aguantar y recuperarse solo.

**Estado:** **abierta** — falta la validación en pantalla.

### [M1] Diagnóstico y degradación — 2026-09-23 — **pendiente de validación**

**Objetivo:** poder ver qué pasa y poder simular cajas peores. Sin esto, M3 y
M4 se desarrollan a ciegas contra la única caja que lo tiene todo por hardware.

**Construido:**

- `DeviceCapabilities` — enumera con `MediaCodecList(ALL_CODECS)`, cachea por
  `Build.FINGERPRINT` e infiere hardware por prefijo por debajo de API 29.
- `DevOptions` — menú de degradación persistido. **Todo arranca en el valor de
  fábrica**: abrir el menú no cambia nada por sí solo.
- `DegradableCodecSelector` — veta decoders por nombre y descarta los de
  hardware bajo demanda. Es la pieza que ejercita los caminos de respaldo.
- Flags de TS en caliente vía `DefaultHlsExtractorFactory` — lo que M4 necesita
  para barrer las cuatro combinaciones sin recompilar.
- Overlay de estadísticas con el **motivo** de la decisión, decoders activos,
  buffer, bitrate, frames perdidos, live offset contra ventana DVR y
  degradación activa.
- Exportación del log a **Descargas**.

**Hallazgos durante la construcción:**

1. **`estrellassd` (MPEG-2 + MP2) reproduce**, con
   `OMX.amlogic.mpeg2.decoder.awesome2` y `OMX.amlogic.audio.decoder.mp2`,
   ambos por hardware. Era el sexto canal, hasta ahora sin probar. **Van 5 de
   6**; solo falla `estrellashd`.
2. **Esta caja no tiene decoder MPEG-2 por software.** Medido al forzar
   software: `antes=[OMX.amlogic.mpeg2.decoder.awesome2] despues=[]`. Para
   MPEG-2, el único camino alternativo es **libVLC**, lo que confirma que M6 no
   es opcional en cajas sin ese decoder por hardware.
3. **La exportación del log no servía.** Escribía en `getExternalFilesDir()`,
   que en Android 10+ no es accesible ni por `adb pull` ni por un gestor de
   archivos: el log quedaba encerrado en la caja. Rehecha contra `MediaStore`,
   y verificada extrayéndola. Nótese que MediaStore impone que la extensión
   concuerde con el tipo MIME.
4. **Dos métricas mentían y se corrigieron.** «Tiempo desde el último frame»
   se apoyaba en `onRenderedFirstFrame`, que dispara una sola vez, así que
   medía desde el arranque; ahora se mide el **avance de la posición**, que es
   la señal fiable de imagen congelada y la que M7 necesita. Y «bitrate»
   informaba la estimación de ancho de banda —52 Mbps en una LAN— en vez del
   bitrate del canal; ahora se miden los bytes realmente descargados (2893
   kbps en `estrellassd`, coherente con `CHANNELS.md`) y el ancho de banda se
   informa aparte.

**Prueba de Edgar** (`LIVETV_PLAN.md` M1): el overlay muestra datos correctos;
vetar un decoder desde el menú cambia visiblemente el comportamiento; el log se
exporta.

**Comprobado por instrumentos antes de entregar:** forzar software sobre
`estrellassd` deja el video sin candidatos y sin frames, con
`codec.sinCandidatos` en el registro; al restablecer, vuelve a reproducir por
hardware.

**Correcciones tras el primer intento de validación de Edgar:**

5. **El menú era inusable con el mando.** La lista de 54 decoders quedaba en
   medio, así que los controles útiles estaban a sesenta pulsaciones — con un
   mando, lo que está a sesenta pulsaciones no existe. Reordenado: estado,
   diagnóstico, degradación, flags, sintonía y restablecer **caben en la
   primera pantalla**; la lista de decoders va al final y plegada.
6. **La exportación pasa a ser automática.** Pedirle a un suscriptor que entre
   a un menú y pulse un botón no es un plan de diagnóstico. El log se vuelca
   **solo a Descargas en cuanto se registra un error**, con antirrebote de
   120 s para que una tormenta no genere cien archivos, y en un hilo aparte
   para no bloquear la reproducción. El botón manual se conserva.

   Verificado provocando un error real: `livetv-auto-20260923-213946.txt`,
   2314 bytes, con `codec.sinCandidatos mime=video/mpeg2
   degradacion=video=software` dentro y sin pulsar nada.

**Ampliación del overlay tras la validación de Edgar — 2026-09-24**

Edgar preguntó qué más convendría ver. Se revisó qué le hace falta a las fases
que vienen y aparecieron datos que **no son opcionales: son criterios de cierre
que no se podían medir**.

Añadidos: **ms hasta el primer frame** (zapping, §10), **rebuffers y su ratio**
(el cierre de M2 es «rebuffers por hora < 1»), **resolución, SAR y aspecto
resultante** (M5), **fps medido** (zanja la disputa de `estrellashd`),
**pistas de audio y cuál está activa** (M3), **reintentos de carga** (delata
lluvia antes de que se vea) y **tiempo en el canal** (maratón de 48 h).

Tres correcciones de honestidad sobre datos que habrían engañado:

- El **fps** se mide en ventana deslizante de 2 s, no como media acumulada:
  una media arrastra el hueco de cada rebuffer para siempre y nunca vuelve al
  valor real.
- El **PID** ya no se inventa. El `id` que expone Media3 en HLS es un índice de
  pista, y se estaba enseñando como PID: salía `pid=15` donde los PID medidos
  son `0xdd`/`0xde`. Ahora solo se muestra si cae en el rango válido.
- El **overlay sobrevive a «restablecer a fábrica»**: es instrumentación, no
  degradación, y apagarlo justo cuando el operador restablece para comparar
  sería quitarle el instrumento en mitad de la medición.

**Verificado en la caja:** `estrellassd` reporta `544x480 · SAR 1.176 ·
aspecto 1.333 (4:3)` — el anamórfico 20:17 resuelto exactamente — y **1400 ms
hasta el primer frame**, por debajo del objetivo de zapping.

**Tres hallazgos que el overlay sacó a la luz nada más existir:**

| Hallazgo | Va a |
|---|---|
| ExoPlayer expone **1 de las 2 pistas de audio** de `discoveryhd` y `hbohd`, aunque el TS crudo lleva `0xdd` (spa) y `0xde` (eng) | **M3** |
| `estrellassd`: **4 rebuffers y 15 % de ratio** en los primeros 30 s, contra un objetivo de 0.3 % | **M2** |
| `estrellassd` (480i) mide **60.2 fps**. Puede ser el VPU desentrelazando a 59.94 —lo que respondería el pendiente 1— o el listener contando campos. **No se concluye con un solo número** | **M5** |

**Estado:** **abierta** — falta la validación en pantalla.

### Retirada de `media3-ffmpeg-decoder` (GPL-3.0) — 2026-09-22

**Síntoma:** `CLAUDE.md` §6 prohibía `jellyfin-androidx-media` por GPL-3.0,
pero el fork base **ya la traía**, en `app/build.gradle.kts:161` y en
`playback/media3/exoplayer`.

**El motivo escrito era incorrecto.** Decía "incompatibles con la distribución
en Play"; en Play sí se distribuyen apps GPL-3.0, la tienda con ese conflicto
es la de Apple. El motivo real: este fork es **GPL-2.0 sin cláusula "or
later"** (README y `LICENSE`), y GPL-2.0 y GPL-3.0 se consideran mutuamente
incompatibles.

**Qué aportaba, verificado en el origen** (`build.sh` del repositorio
`jellyfin/jellyfin-androidx-media`, licencia confirmada en el POM de Maven
Central):

```
ENABLED_DECODERS=(flac alac pcm_mulaw pcm_alaw mp3 aac ac3 eac3 dca mlp truehd)
```

Todos de **audio**, ninguno de video — confirma que por esa vía nunca se iba a
decodificar MPEG-2. De ellos, solo **DTS/DCA, TrueHD, MLP y ALAC** dependían
realmente de la extensión; el resto los trae la plataforma.

**Por qué no era borrar una línea:** `deviceProfile.kt` declaraba DTS y TrueHD
en un **array fijo**, sin derivarlo de las capacidades de la caja, y
`VideoManager.java:190` activa la extensión como respaldo incluso con la
preferencia apagada. Quitar la librería sin recortar ese array habría dado
películas **sin sonido** en vez de transcodificación en el servidor.

**Datos que decidieron:** biblioteca de pruebas consultada por la API de
Jellyfin — **582 títulos, 1075 pistas de audio, 100 % AAC**, biblioteca de
música vacía. **Cero títulos afectados.** Confirmado además que las
bibliotecas de producción están normalizadas igual.

**Cambio:** dependencia retirada de los dos `build.gradle.kts`; ALAC, DCA,
DTS, MLP y TRUEHD fuera de `supportedAudioCodecs`. AC3 y EAC3 se conservan:
los decodifica la plataforma en cajas de TV y ya estaban tras `ac3Enabled`.

**Excepción registrada:** es el único cambio permitido dentro de `playback/`.
La verificación (b) de `CLAUDE.md` §4 lo contempla por nombre.

**Nota para el futuro:** estos decoders **no necesitan GPL**. El build de
Jellyfin no pasa `--enable-gpl`, así que su FFmpeg es LGPL; el GPL-3.0 sale de
la licencia de su repositorio envoltorio. Si alguna vez hace falta DTS o
TrueHD, la salida limpia es compilar la extensión LGPL propia.

**Estado:** cerrada.

---

### Desfase de audio que crece con las horas — 2026-09-23 — **abierta**

**Síntoma, observado por Edgar:** con un canal corriendo **~3 horas**, el audio
acabó **bastante** desfasado. **Desconectar y volver a conectar el cable HDMI
lo corrigió**, y quedó sincronizado **en todos los canales**.

**Por qué ese dato vale tanto.** Un ciclo de HDMI no reinicia la app, ni el
decodificador, ni el stream. Lo que sí hace es tirar y recrear la **ruta de
salida de audio**: Android reenumera el dispositivo, AudioFlinger rehace el
stream y el HAL vuelve a declarar su latencia. De ahí salen tres conclusiones:

1. **No es del stream ni del canal.** Es estado a nivel de caja, y por eso se
   arregló en todos los canales a la vez.
2. **No es el procesado de imagen de la tele.** Eso daría una latencia fija,
   no algo que crece durante tres horas.
3. **Crecer con el tiempo es la firma de una deriva de reloj** entre el DAC de
   audio y el reloj del sistema.

Encaja con lo observado antes: el desfase «ligero» del primer día y el
«bastante» de las tres horas son probablemente el mismo fenómeno en distintas
fases de acumulación. La caja llevaba **2 días y 10 horas encendida**.

**Evidencia medida:**

- `persist.sys.media.avsync = true` (sincronía AV de Amlogic activa).
- Latencia del bucle de escritura del HAL: **260 ms** antes del ciclo HDMI,
  **282 ms** después, con `min=-1383 ms` — ese pico negativo es la huella del
  propio tirón de cable.
- En el arranque de la reproducción, el HAL emite:
  `E audio_virtual_buf: ms12 system input underrun happens … diff=53389474951`
  — **53 segundos** entre los punteros de lectura y escritura del búfer
  interno de MS12, el pipeline de audio de Amlogic. Ocurre **por debajo de
  cualquier reproductor**.

**Lo que esto le exige al proyecto.** El objetivo de **48 h de uptime continuo**
(§10) es incompatible con un audio que se desfasa en tres horas. M8 ya preveía
una reconexión programada antes del rollover de PTS (~26.5 h); este hallazgo
dice que además hace falta **reiniciar la ruta de audio, y mucho más a menudo**.

**Hipótesis a confirmar, y es barata:** si recrear el player desde la app
(salir del canal con ATRÁS y volver a entrar) también limpia el desfase,
entonces el cliente puede corregirlo solo y nadie tiene que tocar un cable.
Si solo lo arregla el ciclo de HDMI, el estado vive más abajo que AudioTrack y
la salida es otra.

**Próxima prueba para Edgar:** la siguiente vez que aparezca el desfase,
**antes de tocar el HDMI**, salir del canal y volver a entrar. Ese único dato
decide si hay arreglo por software.

**No se ha tocado nada.** Sincronía es Modo A, y la causa todavía no está
confirmada. Medición de deriva en curso.

### Culpable identificado — 2026-09-23

**Prueba 2, la decisiva (Edgar).** Se generó un archivo local con destello a
pantalla completa y clic, verificado sincronizado en origen: destellos en
1.000/2.000/3.000 s y clics en 1.00002/2.00002/3.00002 s — desviación constante
de 0.02 ms y **cero deriva**. Se reprodujo **desde el almacenamiento local de
la caja, con VLC**.

**Resultado: salió desincronizado.** Un ciclo de HDMI lo corrigió, por segunda
vez.

Eso elimina de una sola prueba, y con certeza:

| Sospechoso | Por qué queda descartado |
|---|---|
| Satélite, Astra, Flussonic | el archivo es local, no pasó por ahí |
| La red | reproducción desde `/sdcard` |
| El stream y su muxado | archivo generado y verificado aquí |
| **El módulo `livetv` y Media3** | **se reprodujo con VLC, otro motor** |

**Prueba 1, confirmación independiente.** Capturados 6 minutos de `mileniosd`
y medido el avance de cada línea de tiempo:

```
video  10801 paquetes  span = 360.000 s   (10800 frames a 30 fps = 360.000 s)
audio  16875 paquetes  span = 359.979 s   (16875 x 1024 / 48000 = 360.000 s)
```

Los 21 ms de diferencia son exactamente una trama AAC: efecto de borde del
recorte, no deriva. **El stream no deriva.**

**Conclusión: el culpable es la ruta de salida de audio de la caja.**

Queda la tele como sospechoso residual, pero es poco verosímil: el retardo que
añade un televisor al procesar imagen es **fijo**, y no lo borraría un
reenchufe de cable. Lo que sí encaja es lo medido en el HAL —
`ms12 system input underrun` con 53 s entre punteros del búfer interno de
Amlogic — y que la cura sea precisamente destruir y recrear esa ruta.

**La pregunta que queda abierta, y es la que decide el proyecto:** ¿hasta qué
profundidad hay que reiniciar para limpiarlo? De eso depende que la app pueda
curarlo sola o que sea una limitación de hardware que haya que documentar.

**Escalera de prueba pendiente**, a ejecutar **con el desfase presente** y
parando en el primer paso que funcione:

1. Salir del canal y volver a entrar — la app suelta el `AudioTrack`
2. Cerrar la app del todo y reabrirla — muere el proceso, se cierra el stream
3. Suspender la caja y despertarla — normalmente cierra la salida HDMI
4. Reiniciar la caja
5. Ciclo de HDMI

Si funciona el 1 o el 2, **el cliente puede curarlo solo** y esto pasa a ser un
watchdog de audio en M7 más una reconexión periódica en M8. Del 3 en adelante,
queda fuera del alcance de la app.

### Escalera de reinicio — resultado — 2026-09-23

Ejecutada por Edgar **con el desfase presente**:

| Paso | Resultado |
|---|---|
| 1. Salir del canal y volver a entrar | **sigue** — y también en otras apps, **incluida YouTube** |
| 2. Cerrar la app del todo con ATRÁS | **sigue** — también en las demás apps |
| 3. Apagar/suspender la caja con el mando | **se corrige** |

**Dos conclusiones, y la primera es definitiva.**

**El desfase es del sistema, no de ninguna app.** Que YouTube lo tenga al mismo
tiempo lo confirma por tercera vía independiente, tras el archivo local y la
prueba con VLC.

**La app no puede curarlo.** Soltar el `AudioTrack` no basta; matar el proceso
tampoco. Ya se había medido que al salir la salida entra en `Standby` a los 3 s,
y el paso 2 demuestra que **el reposo no limpia el estado**: hace falta que la
salida se **cierre**, no que se duerma. Eso ocurre al suspender el aparato o al
reenchufar el HDMI, y ninguna de las dos cosas está al alcance de una app
normal.

**Consecuencia para el producto.** Esto deja de ser un asunto del módulo y pasa
a ser una **limitación del dispositivo**. El objetivo de 48 h de uptime
continuo (§10) **no es alcanzable en esta caja** sin intervención externa.

**Lo que hay que averiguar antes de diseñar nada:** si la **ZTE ZXV10 B866V2**
—la caja mayoritaria del primer cliente, mismo fabricante de SoC— tiene el
mismo fallo. El archivo `sync-test.mp4` ya está hecho: basta copiarlo y
reproducirlo. Si la ZTE sale limpia, es un modelo; si sale igual, afecta a todo
el parque y condiciona el diseño desde ahora.

**Mitigaciones a evaluar cuando se sepa eso**, ninguna bonita: apoyarse en que
la caja se suspenda sola por inactividad, negociar firmware con el fabricante,
o descartar el modelo. Compensar con un retardo fijo **no sirve**: el desfase
crece con las horas.

**Estado:** causa cerrada, responsabilidad fuera del cliente. Mitigación
pendiente de la medición en la ZTE.

---

### El audio seguía sonando al salir al HOME — 2026-09-23

**Síntoma, visto por Edgar:** con un canal sintonizado, salir al lanzador de la
caja con HOME dejaba **el audio sonando**. Con ATRÁS sí cortaba.

**Causa raíz:** la limpieza colgaba solo del `DisposableEffect`, que se dispara
cuando el reproductor sale de la composición. Con ATRÁS el fragmento se
destruye y limpia; con HOME **solo se detiene**, así que el motor seguía vivo
sonando por encima del lanzador y consumiendo ancho de banda de un stream que
nadie veía.

**Cambio:** la reproducción se ata al **ciclo de vida**. `ON_STOP` detiene el
motor; `ON_START` lo prepara y arranca. En vivo no se almacena en segundo
plano: al volver se va al borde de emisión, no a lo que se perdió.

**Verificado por instrumentos:** reproduciendo `Standby:no`; tras HOME,
`engine.stop` y `Standby:yes`; tras ATRÁS, `Release` y `Standby:yes`.

**Pendiente de mirar con el mando:** al volver a la app desde el lanzador, la
navegación reabre en Inicio y no en el canal. Puede ser artefacto de relanzar
por `adb` en vez de con el mando.

**Estado:** cerrada.

---

### Estrellas HD no arranca con Media3 — 2026-09-22 — **abierta**

**Síntoma:** `test-sat77w-estrellashd-mp4` se queda en «Almacenando…»
indefinidamente. El decoder de video se inicializa
(`OMX.amlogic.avc.decoder.awesome2`, 75 ms) y el de audio también
(`c2.android.aac.decoder`), pero **no sale ni un frame**, y el motor **no
registra ningún error ni reintento**. Falla en silencio.

**Aislamiento:** de los seis canales, **es el único que falla**.

| Canal | Resultado |
|---|---|
| `estrellashd` 1440x1080 High@4.0 tt | **atascado, 0 frames** |
| `beisbol` 1280x720 Main@3.1 progresivo | en vivo |
| `discoveryhd` 1920x1080 tt | en vivo |
| `hbohd` 1920x1080 bb | en vivo |
| `mileniosd` 1024x576 progresivo | en vivo |
| `estrellassd` MPEG-2 | sin probar (ver defecto de foco) |

Eso descarta de entrada varias hipótesis: **no es el entrelazado** —
`discoveryhd` y `hbohd` son 1080i y funcionan— ni la codificación por campos,
que `hbohd` también tiene.

**Lo que distingue a este canal:** perfil **High** (los demás son Main),
1440x1080 **anamórfico 4:3** (el único HD que lo es), y `ffprobe` reporta
sobre sus segmentos `number of reference frames (0+5) exceeds max (4)` y
`mmco: unref short failure`. Puede ser un artefacto de analizar un segmento
aislado, o un problema real de gestión de referencias que el decoder por
hardware no tolera.

**Contexto previo que encaja:** `CHANNELS.md` ya registraba que la captura de
este canal falló (pendiente 14), que su pista de audio es intermitente
(pendiente 11) y que sus fps están en disputa. Es el canal problemático del
lineup y ahora hay un síntoma concreto.

**Evidencia nueva del overlay de M1 (2026-09-24), y es la mejor que hay:**

```
resolución          —          ← nunca se reportó formato de video
buffer              31584 ms   ← 31 s de datos almacenados sin problema
1er frame           —          ← nunca llegó
rebuffers           0
```

Almacena treinta segundos de datos y **nunca reporta un formato de video**. La
red, el playlist y el demuxado funcionan; lo que no produce salida es el
**decodificador**. Eso descarta la mitad del espacio de búsqueda.

**Hipótesis principal, sin confirmar:** flags del extractor de TS
(`FLAG_DETECT_ACCESS_UNITS`, `FLAG_ALLOW_NON_IDR_KEYFRAMES`) — que es
exactamente el pendiente 5 y el objeto de la fase **M4**, y que en HLS hay que
pasar vía `DefaultHlsExtractorFactory`. No se ha tocado nada: M0 no incluye
flags configurables y arreglarlo a ciegas violaría el Modo A.

**Descartado como causa:** el stream **sí** lleva audio y video en este momento
(140 y 178 paquetes en un segmento), así que no es la pista intermitente.

**Prueba de control pendiente:** reproducir el mismo stream con libVLC en la
misma caja. VLC descarga y demuxa, pero no pude determinar por registro si
pinta imagen, y `screencap` no captura la capa de video por overlay. **Requiere
mirar la pantalla.**

**Estado:** abierta. Va a **M4**.

---

### Defecto de foco en la fila de canales — 2026-09-22 — **corregido 26-sep, pendiente de validar con el mando**

Con el control, el foco no siempre entra en la fila de canales al abrir la
sección: se queda en la barra principal. Cuatro intentos de arreglo fallaron
(foco en la primera tarjeta, `focusGroup` en la fila, quitar la petición de
foco, y el patrón de grupo de `HomeFragment`).

**Error de método propio:** las medidas automáticas entran a la sección con un
**toque**, que no recorre el mismo camino de foco que el mando. Edgar sí pudo
abrir canales con el control, así que la medición y la realidad no coinciden y
la medición es la sospechosa. Se revirtió a la variante que él validó.

**Mitigación aplicada:** las tarjetas pasaron de 220x124 dp a 136x92 dp para
que los **seis canales quepan en pantalla sin desplazamiento**. La fila tampoco
desplaza el foco más allá de lo visible, y eso con ~120 canales es inviable.

**No se persigue más aquí:** la lista definitiva es **B2**, con grupos,
favoritos, último canal y entrada por número. El foco y el desplazamiento se
resuelven allí, sobre la estructura real y no sobre este andamiaje.

**Estado:** abierta. Va a **B2**.

---

### [M0] Andamiaje — 2026-09-22 → **cerrada 2026-09-23**

**Síntoma:** no había código. Cinco documentos y un zip con esos documentos.

**Cambio:**

- Fork fijado en **v0.19.10** (`984181a`), tag `upstream-base`. Toolchain
  anotado arriba. Plataforma `android-36` instalada.
- **APK de referencia del upstream sin tocar**, compilada e instalada en la
  caja antes de escribir nada. Es el *antes* contra el que se juzga el VOD.
- Módulo `livetv`: `LiveEngine` como interfaz, `Media3LiveEngine`,
  `LiveTuning` con todas las constantes, `LiveLog` con buffer circular
  exportable, `ChannelProfile` y `LanguageNormalizer`.
- Interfaz en `app/.../ui/livetv/`: `LiveTvFragment` y `LiveTvPlayerFragment`,
  sobre `JellyfinTheme` y `ui/base/`.
- Enganches: botón "TV en vivo" en la barra principal junto a Inicio y Buscar,
  y la fila del inicio redirigida a la sección nueva. Reutiliza la cadena
  `pref_live_tv_cat`, ya traducida: cero cadenas nuevas.

**Prueba (automatizada, previa a la de Edgar):** en la caja Skyworth YDA,
`beisbol` reproduce; después, una película del Jellyfin de pruebas arranca por
`CustomPlaybackOverlayFragment` → `PlaybackController` → `VideoManager` en
*direct stream*, decodificando por `OMX.amlogic.avc.decoder.awesome2`.

**Resultado medido:**

| | |
|---|---|
| Decoder de video | `OMX.amlogic.avc.decoder.awesome2` (**hardware**), init 49 ms |
| Decoder de audio | `c2.android.aac.decoder` (software), init 59 ms |
| `prepare` → primer frame | **~1.0 s** |
| Live offset obtenido | 10 515 ms (se pidieron 8 000) |
| Camino de VOD vs upstream | **diff vacío** |
| Archivos existentes tocados | 5, 37 líneas, solo cableado |

**Defecto encontrado y corregido en la misma fase:** el motor se construía dos
veces y la primera instancia se liberaba de inmediato, porque el
`DisposableEffect` dependía del `SurfaceView`, que llega en una recomposición
posterior. Desperdiciaba ~100 ms en cada entrada a un canal, que se pagan
directamente en el zapping. Corregido atando el motor al canal y entregando la
superficie por el `update` del `AndroidView`.

**Segundo defecto, encontrado por Edgar en la validación:** a los 5 minutos el
salvapantallas de la app tapaba el canal. Causa raíz: el reproductor en vivo no
tomaba el bloqueo del `InteractionTrackerViewModel`. La app ya trae el helper
`ScreensaverLock`, que `VideoPlayerScreen` usa con `enabled = playing`;
`LiveTvPlayerFragment` simplemente no lo usaba. Ver televisión **es** la
condición que el temporizador de inactividad interpreta como estar ausente.

A diferencia del VOD, en vivo el bloqueo dura toda la pantalla y no solo
mientras `playing`: no hay pausa, y durante un rebuffer por lluvia lo último
que debe ocurrir es que la imagen se sustituya por un carrusel de carátulas.

Verificado: **7 min 40 s continuos** con el canal en pantalla, pasado el umbral
de 300 s, sin salvapantallas. En esa misma ventana, **cero** errores de player,
reintentos de carga, frames perdidos y `BEHIND_LIVE_WINDOW`.

**Observación para M2:** el offset real quedó en 10.5 s sobre una ventana de
18.59 s en `beisbol`. Por encima de la mitad de la ventana, que es el techo que
fija `LiveTuning.TARGET_OFFSET_MAX_WINDOW_FRACTION`. En `beisbol` no molesta;
en `hbohd`, con 13.88 s de ventana, sí. Refuerza el pendiente 17.

---

#### Cierre — declarado por Edgar el 2026-09-23

**Criterios de `LIVETV_PLAN.md` M0:**

| Criterio | Resultado |
|---|---|
| El canal reproduce | imagen correcta, arranque rápido |
| Películas y series como en la APK de referencia | verificado en pantalla |
| Versiones fijadas y anotadas | v0.19.10 · Media3 1.8.0 · Gradle 9.1.0 · Kotlin 2.2.21 |
| `git diff upstream-base..HEAD -- ':!livetv/'` | solo cableado, justificado abajo |

**Huella sobre el upstream al cerrar:**

```
camino de VOD .......... 1 archivo, 1 línea   (la excepción GPL-3.0 registrada)
cableado y navegación .. 7 archivos, 59 líneas
módulo + interfaz ...... 10 archivos, 1064 líneas  (todo nuevo)
```

Los 7 archivos son: registro del módulo, campos de `BuildConfig`, dos destinos
de navegación, el botón de la barra, la fila del inicio redirigida y el
recorte de códecs del perfil. Ninguno altera el comportamiento del VOD.

**Tres defectos encontrados durante la validación, corregidos y verificados:**

1. **El motor se construía dos veces** y la primera instancia se liberaba de
   inmediato, porque el efecto dependía del `SurfaceView`. ~100 ms
   desperdiciados en cada entrada a un canal, que se pagan en el zapping.
2. **El salvapantallas tapaba el canal a los 5 minutos.** El reproductor no
   tomaba el bloqueo del `InteractionTrackerViewModel`. Verificado: 7 min 40 s
   continuos sin salvapantallas, y en esa ventana cero errores, reintentos,
   frames perdidos y `BEHIND_LIVE_WINDOW`.
3. **El audio seguía sonando al salir al HOME.** La limpieza colgaba solo de la
   composición; ahora la reproducción va atada al ciclo de vida.

**Medido en la caja Skyworth YDA:**

| | |
|---|---|
| Decoder de video | `OMX.amlogic.avc.decoder.awesome2` (hardware) |
| Decoder de audio | `c2.android.aac.decoder` (plataforma) |
| `prepare` → primer frame | **~1.0 s** (objetivo de arranque: < 4 s) |
| Canales que reproducen | 5 de 6 |

**Lo que queda abierto y no es de M0:**

- **Estrellas HD no arranca** → **M4**. Único de los seis que falla; Discovery
  y HBO, también 1080i satelitales, van bien.
- **Desfase de audio creciente** → **fuera del cliente**. Demostrado del
  sistema con tres pruebas independientes; la app no puede curarlo.
- **Foco y desplazamiento de la fila de canales** → **B2**.

**Estado:** **cerrada.**

---

### [Previo a M0] Auditoría de documentos contra el Flussonic real — 2026-09-22

**Síntoma:** antes de escribir la primera línea de código se reverificaron los
seis canales contra `192.168.11.250:8181`. `CHANNELS.md`, que se declara
fuente de verdad, contenía cifras que no coinciden con lo que la app recibe.

**Hallazgos medidos:**

1. **Flussonic renumera los PIDs.** Los seis canales llegan con video `0xd3` y
   audio `0xdd` (+`0xde` el segundo). Los valores registrados — `0x3fd`/`0x3fe`
   en el mp2v, `0x101`/`0x102` en hbohd — eran del lado Astra. **Esto invalida
   la regla 5 de `CLAUDE.md` tal como estaba escrita:** el PID es un alias de
   la posición y no distingue el español del inglés en los canales de 70W. La
   clave de selección pasa a ser el idioma normalizado.
2. **Los conteos eran de cinco canales y son seis.** `hbohd-70w` entró al
   cerrar el pendiente #10 sin propagarse. Y el conteo de anamórficos estaba
   mal de origen: son **dos de seis**, no cuatro de cinco. Cambiaba el criterio
   de cierre de M5.
3. **La ventana DVR contradecía el `targetOffsetMs`.** Medida: 13.88 s en
   `hbohd` y 14.71 s en `discoveryhd`, los dos canales que llevaban
   `targetOffsetMs = 12000`. Quedaban 1.9 s antes de `BEHIND_LIVE_WINDOW`: el
   colchón contra la lluvia era lo que rompía el canal.
4. **El paso FFmpeg de la cascada de video nunca existió.** La extensión
   FFmpeg de Media3 es solo de audio.
5. **El fork base ya trae `media3-ffmpeg-decoder` (GPL-3.0)**, el mismo que
   `CLAUDE.md` §6 prohíbe, en `app/build.gradle.kts:161`. Viene de upstream y
   sirve al VOD.

**Decisiones tomadas:**

- **Audio a AAC en Flussonic, elevado a política.** Con ~120 canales la
  excepción caso por caso no escalaba. Caen la extensión FFmpeg, el NDK, la
  compilación nativa y el cumplimiento LGPL-2.1, y con ellos la preocupación
  por AC-3/E-AC-3 y la superficie de la prohibición de nombrar Dolby. M3 se
  reduce a la cascada de video.
- **`segment_count` de 6 a 12 en Flussonic.** Ventana de ~28 s, sin costo de
  CPU ni de latencia: el live edge no se mueve. Nueva regla: `targetOffsetMs`
  nunca por encima de la mitad de la ventana medida, verificada en runtime.
- **El GPL-3.0 heredado se decide en B7**, no antes: quitarlo choca con "el
  VOD queda intacto".

**Cambio:** los cinco `.md` corregidos. Sin código tocado — no había código.

**Resultado:** 31 correcciones. Pendientes 17 a 20 abiertos, #3 cerrado, #13
subido de B4 a M3, #14 y #16 ampliados.

**Estado:** cerrada.
