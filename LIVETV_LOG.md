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

## Pendientes conocidos

| # | Tema | Vía / fase | Notas |
|---|---|---|---|
| 1 | **Validación visual del desentrelazado** en la caja de pruebas | M5 | decide si aplica el nivel 1 y si libVLC entra desde el principio |
| 2 | ~~Confirmar ABI de la caja~~ | — | **cerrado 22-sep: `armeabi-v7a,armeabi`. Solo 32 bits, sin `arm64-v8a`.** Confirma lo que sugería `/proc/cpuinfo`. `armeabi-v7a` es obligatorio en el AAB o la caja de pruebas no instala; `arm64-v8a` también, porque Play lo exige a toda app con código nativo |
| 3 | ~~Decidir si el audio MP2 se convierte a AAC~~ | — | **cerrado 22-sep: sí, y elevado a política.** Ver entrada abajo |
| 4 | Inventario de códecs de la ZTE B866V2 | M1 | la caja mayoritaria del cliente, aún sin medir |
| 5 | Flags de extractor en Amlogic | M4 | la medición dice OFF/OFF, la experiencia de campo sugiere lo contrario |
| 6 | Bajar `source_timeout` de Flussonic a ~10 s | headend | con HTTP, Astra mantiene la conexión abierta sin datos |
| 7 | Letrero de "sin señal" como fuente de respaldo en Flussonic | headend | evita que un rain fade largo congele la imagen |
| 8 | Confirmar si `a095` es el mismo canal que `discoveryhd-70w` | M1 | mismos datos técnicos |
| 9 | Pasar `a093` (SD H.264 70W) por Flussonic | headend | único SD H.264 satelital, probablemente entrelazado |
| 10 | ~~Caracterizar `test-sat70w-hbohd-mp4`~~ | M1 | **cerrado**: h264 1080i `bb`, AAC |
| 11 | **`estrellashd-mp4`: pista de audio intermitente** | headend + M7 | desaparece y vuelve; revisar errores PES en Astra |
| 12 | Sobrevivir a cambios en el número de pistas a mitad de stream | M7 | ExoPlayer lo tolera mal; caso real en `estrellashd-mp4` |
| 13 | Aceptar `spa`, `esp`, `esl` y `es` como español | **M3** (subió de B4) | `estrellashd` y `estrellassd` usan `esl`. Con los PIDs normalizados por Flussonic, el idioma es la **única** clave de selección de audio: es motor, no interfaz |
| 14 | Recapturar la muestra de `estrellashd-mp4` | M1 | su captura falló, no hay golden sample. **Ahora además resuelve una contradicción:** HLS da 60000/1001, el `.ts` crudo da 30000/1001 `tt`. Hasta aclararlo no se puede afirmar que sea el ejemplo de codificación por campos de 77W |
| 15 | Instalar tsduck y tomar línea base de errores CC con buen clima | M7 | no está en los repos de Ubuntu, bajar el .deb |
| 16 | Validar desentrelazado con los **dos tipos** de codificación | M5 | `discoveryhd-70w` por frames, `hbohd-70w` por campos. Estos dos bastan: cubren las cuatro combinaciones sin depender de `estrellashd`, que está en disputa (#14) |
| 17 | **Subir `segment_count` de 6 a 12 en Flussonic** | headend | ventana DVR medida de 13.88 s en `hbohd` contra `targetOffsetMs` de 12000: 1.9 s de margen. Sin costo de CPU ni de latencia |
| 18 | **Aplicar MP2 → AAC a los canales MP2 del lineup** | headend | empezando por `estrellassd-mp2v`. Gatea el cierre real de M3 |
| 19 | ~~El fork base trae `media3-ffmpeg-decoder` (GPL-3.0)~~ | — | **cerrado 22-sep: retirado.** Ver entrada abajo |
| 20 | Traer `scan-canales.sh` al repositorio | B | vive suelto en `/home/scan-canales.sh` y produce la fuente de verdad |

---

## Entradas

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

**Estado:** abierta. Va a **M8**, con dependencia de M7 (watchdog).

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

### Defecto de foco en la fila de canales — 2026-09-22 — **abierta**

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

### [M0] Andamiaje — 2026-09-22 — **pendiente de validación**

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

**Estado:** **abierta** — falta que Edgar lo valide en pantalla. El criterio de
cierre está en `LIVETV_PLAN.md` M0.

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
