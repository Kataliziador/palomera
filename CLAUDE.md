# CLAUDE.md

Reglas de este repositorio. Léelas completas antes de tocar código.

---

## 1. Qué es este proyecto

Fork de `jellyfin-androidtv` (GPL-2.0) con **un módulo nuevo y aislado para
canales de TV en vivo**. Todo el VOD de Jellyfin queda **intacto**.

El producto se vende con marca blanca a varios ISPs mexicanos y se publica en
Google Play Store.

```
Satélite Ku 77W / 70W ──→ Cesbo Astra ──→ Flussonic ──→ HLS ──→ APK
Multicast TotalPlay ─────────────────────↗
```

Documentos hermanos:
- `CHANNELS.md` — datos medidos de cada canal. Fuente de verdad, no adivines.
- `LIVETV_ENGINE.md` — especificación del motor: cascada, capacidades, sintonía.
- `LIVETV_PLAN.md` — fases de trabajo y criterios de cierre.
- `LIVETV_LOG.md` — bitácora. Se actualiza al cerrar cada fase.

---

## 2. Principio rector

**Los canales se reproducen tal cual llegan del satélite.**

El motor se desarrolla y afina para funcionar con la señal como se recibe:
MPEG-2, audio MP2, entrelazado, anamórfico, varias pistas de audio. Cuando un
canal presente un problema, la solución se busca **en el cliente**.

Los canales de referencia son los de **satélite 77W y 70W** (ver `CHANNELS.md`).
Los de TotalPlay son H.264 progresivo sin particularidades y sirven de línea base.

**Única excepción permitida, y ya decidida:** el **audio se normaliza a AAC
en Flussonic**. Cuesta muy poca CPU en el servidor y no toca el video. Se elevó
de "opción disponible" a política el 2026-09-22, porque con ~120 canales la
excepción caso por caso no escala. El video **nunca** se transcodifica.

Consecuencia: la app no necesita decoders de MP2, MP3 ni AC-3, y con ellos
desaparecen la extensión FFmpeg, el NDK y toda la compilación nativa.

---

## 3. Dos modos de trabajo

Este proyecto tiene dos tipos de trabajo con reglas distintas. Identifica
siempre en cuál estás antes de empezar.

**Edgar** es quien valida en pantalla y declara el cierre de cada fase de
Modo A. Ningún criterio de Modo A se da por cumplido sin su visto bueno.

### Modo A — Motor de reproducción: paso a paso, con validación humana

Aplica a todo lo que afecte **qué se ve y cómo se ve**: decodificación,
selección de backend, buffers, red, recuperación, flags de extractor, render,
aspecto, entrelazado, sincronía, zapping.

Reglas del Modo A:
- **Una fase = una falla = un commit = una APK.**
- Edgar la prueba **en pantalla** y declara si está resuelta.
- **No se avanza** a la siguiente fase sin su visto bueno explícito.
- Prohibido arreglar dos cosas de motor en el mismo commit.
- Cada cambio de comportamiento va detrás de un toggle en el menú de
  desarrollador, con el valor por defecto igual al comportamiento anterior.
- Al cerrar, se escribe la entrada en `LIVETV_LOG.md`.

**Por qué:** los fallos de reproducción se juzgan mirando la pantalla. Ninguna
prueba automática distingue "se ve bien" de "se ve con peine" o "el audio va
medio segundo adelantado".

### Modo B — Todo lo demás: autónomo

Aplica a lista de canales, EPG, navegación, guía, favoritos, overlays,
estética, marca blanca, cliente del servidor de configuración, telemetría,
build, empaquetado y preparación para Play Store.

Reglas del Modo B:
- Claude Code trabaja de corrido: detecta fallas, las corrige y sigue.
- Puede agrupar varios cambios en un commit si son del mismo tema.
- No hace falta esperar validación por cada arreglo.
- Se reporta al final del bloque lo que se hizo y lo que quedó pendiente.

### La frontera

Si un cambio altera **decodificación, red, buffers, render o recuperación**,
es Modo A aunque parezca de interfaz.

Ejemplo: el botón para cambiar de pista de audio es Modo B; cómo el motor
aplica ese cambio sin cortar el video es Modo A. Ante la duda, trátalo como
Modo A y pregunta.

---

## 4. Aislamiento del módulo

### El invariante

**Películas y series se comportan exactamente como en el upstream.** Ese es el
fin. Todo lo demás de esta sección son medios, y un medio que estorbe se
cambia; el invariante no.

No es "no toques estos archivos". Es "no cambies ese comportamiento". Tocar un
archivo del camino de VOD está permitido si se demuestra que el VOD no cambia;
no tocarlo pero alterar su comportamiento desde fuera está prohibido.

### Las reglas

1. **El motor vive en el módulo Gradle `livetv`.** Cascada, capacidades,
   buffers, resiliencia, diagnóstico y cliente de configuración. El módulo
   **no depende de `app`**. Es la parte difícil y volátil, y la que debe
   sobrevivir intacta a los rebases del upstream.
2. **La interfaz de LiveTV vive en `app/.../ui/livetv/`, reescrita desde
   cero.** Usa `JellyfinTheme` y los componentes de `ui/base/`, porque la
   sección tiene que verse como el resto de la app. El sistema de diseño no se
   extrae a un módulo aparte: moverlo haría chocar cada cambio de upstream al
   tema en cada rebase, que es justo el dolor que esta sección evita.
3. **El camino de VOD no se modifica** — `PlaybackController`, `VideoManager`,
   `CustomPlaybackOverlayFragment`, `PlaybackManager`. No porque el directorio
   sea sagrado, sino porque es el camino de películas y series y el invariante
   manda. Si alguna vez hiciera falta tocarlo, es Modo A con validación en
   pantalla del VOD, no solo del canal.
4. **PROHIBIDO construir sobre `playback/core`** (el playback rewrite del
   upstream): está incompleto y va a cambiar.
5. **La sección LiveTV de Jellyfin se reemplaza, no convive.** La fila del
   inicio apunta a la sección nueva. El código viejo (`ui/livetv/`
   `LiveTvGuideFragment`, `TvManager`, `GuideFilters`) se queda en el árbol
   pero inalcanzable: borrarlo agrandaría el diff contra upstream sin ganar
   nada. Grabación y DVR ya están fuera de alcance (§7).
6. **Selección de pistas propia, por identidad de pista del stream:**
   idioma normalizado como clave primaria, PID como desempate estable dentro
   de una misma sesión. Nunca por índice de Jellyfin. Prohibido reusar
   `offsetStreamIndex` ni `getExoPlayerTrack`/`setExoPlayerTrack`.

   **Por qué no basta el PID** (medido 2026-09-22): Flussonic renumera los
   PIDs al remuxar y entrega los seis canales con video `0xd3` y audio `0xdd`
   (+`0xde` el segundo). El PID es un alias de la posición y no distingue
   nada; en `discoveryhd-70w` y `hbohd-70w` el español y el inglés se separan
   **solo por la etiqueta de idioma**. Esa etiqueta debe normalizarse
   aceptando `spa`, `esp`, `esl` y `es` como español (ver `CHANNELS.md`).
7. **Verificación obligatoria antes de cada commit.** El diff ya no sirve
   como prueba única, porque ahora hay código nuevo legítimo fuera de
   `livetv/`. Son dos comprobaciones:

   ```bash
   # a) nada tocado fuera del territorio permitido
   git diff --stat upstream-base..HEAD -- \
     ':!livetv/' ':!app/src/main/java/org/jellyfin/androidtv/ui/livetv/' \
     ':!*/flavors/*' ':!*.md'
   ```

   Lo que aparezca aquí son los enganches de navegación, y se justifican uno a
   uno en el commit.

   ```bash
   # b) el camino de VOD, intacto
   git diff --stat upstream-base..HEAD -- \
     'app/src/main/java/org/jellyfin/androidtv/ui/playback/' 'playback/'
   ```

   **Esta segunda debe salir vacía**, con **una excepción registrada**: la
   retirada de `media3-ffmpeg-decoder` de
   `playback/media3/exoplayer/build.gradle.kts` (2026-09-22, ver arriba). No
   hay ninguna más, y añadir otra exige la misma validación en pantalla.

   Si aparece algo fuera de esa excepción, la APK no se entrega sin que Edgar
   valide películas y series contra la APK de referencia de M0.

**Razón:** el upstream está migrando su reproductor a Media3. Si el módulo no
lo toca, los rebases son triviales y las mejoras del VOD llegan gratis.

---

## 5. Reglas de ingeniería

7. **Cero números mágicos.** Toda la sintonía en `LiveTuning.kt`.
8. **Cero `try/catch` silenciosos.** Todo error se registra con canal, URL,
   códec, modelo de caja, decoder activo y estado del buffer.
9. **Logging estructurado desde el commit #1**, con buffer circular
   exportable a archivo desde la app.
10. **`LiveEngine` es una interfaz desde el primer commit**, aunque solo
    exista la implementación Media3.
11. **Prohibido actualizar Gradle, Kotlin, Compose o Media3 "de paso".**
    Versiones fijadas en `libs.versions.toml`.
12. **Prohibido hardcodear marca, color, logo, URL o nombre de canal** en el
    módulo. Todo sale de `BuildConfig` o del servidor de configuración.
13. **Prohibido descargar o ejecutar código desde el servidor.** El servidor
    elige entre motores que ya vienen en el APK; nunca entrega motores. Google
    Play prohíbe cargar código ejecutable externo.

---

## 6. Decisiones cerradas (no se re-discuten)

### Transporte
- **HLS con segmentos TS**, endpoint `mono.m3u8` (media playlist directo, no el
  master `video.m3u8`). Segmentos de 2 a 3 s, ya configurados en Flussonic.
- Conserva el contenedor MPEG-TS sin conversión, que es lo que permite llevar
  MPEG-2 y MP2 tal cual.
- **Prohibido implementar ABR.** Una sola calidad por canal.

### Motor
- **Media3/ExoPlayer** como motor principal para todos los canales.
- **libVLC** como backend de respaldo para dos casos concretos: MPEG-2 sin
  decoder en la caja, y desentrelazado por software de SD.
- La elección es **automática, por canal y por dispositivo**, mediante la
  cascada descrita en `LIVETV_ENGINE.md`.
- `MediaPlayer` del sistema y `libmpv` quedan descartados.

### Prioridad de decisión
1. Ajuste por modelo desde el servidor (lo más específico, gana siempre)
2. Preferencia por canal desde el servidor, **si la caja puede cumplirla**
3. Detección propia de la caja

**El servidor sugiere, la caja verifica.** Nunca obedecer una orden del
servidor que el hardware no puede cumplir.

### Códecs
- **Sin extensión FFmpeg.** Al normalizarse el audio a AAC en Flussonic
  (§2), el módulo `livetv` no necesita decoders de `mp2`, `mp3` ni `ac3`.
  Caen con ella el NDK, la compilación nativa por cada versión de Media3 y el
  cumplimiento LGPL-2.1 de FFmpeg.
- **`EXTENSION_RENDERER_MODE_ON` deja de aplicar:** sin extensión no hay nada
  a lo que caer. El módulo usa los decoders de la plataforma.
- **No existe decodificación de video por FFmpeg.** La extensión FFmpeg de
  Media3 es **solo de audio**; no hay renderer de video. Decodificar
  `mpeg2video` por FFmpeg habría exigido escribir un renderer propio. Queda
  escrito para que nadie lo reintroduzca: el respaldo de video es libVLC.
- **PROHIBIDO usar nextlib o jellyfin-androidx-media** (GPL-3.0), y **ya
  retirado del fork** el 2026-09-22.

  El motivo que este documento daba antes — "incompatibles con la distribución
  en Play" — **era incorrecto**: en Google Play sí se distribuyen apps
  GPL-3.0; la tienda con ese conflicto es la de Apple. El motivo real es que
  **este fork es GPL-2.0 sin cláusula "or later"**, y GPL-2.0 y GPL-3.0 se
  consideran mutuamente incompatibles. Venía heredado del upstream.

  Se retiró porque **no costaba nada**: la librería solo aportaba decoders de
  **audio** (`flac alac pcm_* mp3 aac ac3 eac3 dca mlp truehd`, ninguno de
  video), y de ellos únicamente DTS, TrueHD, MLP y ALAC dependían de ella.
  Medido sobre la biblioteca de pruebas: 582 títulos, 1075 pistas, **100 %
  AAC**. Cero títulos afectados.

  Al quitarla hubo que recortar `supportedAudioCodecs` en `deviceProfile.kt`,
  que declaraba DTS y TrueHD **incondicionalmente**, sin derivarlo de lo que
  la caja puede. Dejar esa promesa habría dado películas sin sonido en vez de
  transcodificación en el servidor.

  Nota para quien reintroduzca el tema: esos decoders **no necesitan GPL**.
  El build de Jellyfin no pasa `--enable-gpl`, así que su FFmpeg es LGPL; el
  GPL-3.0 sale de la licencia de su repositorio envoltorio. Si algún día hace
  falta DTS o TrueHD, la salida limpia es compilar la extensión LGPL propia.
- **Prohibido nombrar marcas Dolby** en UI, ficha de Play o marketing.

### Render
- **SurfaceView**, nunca TextureView.
- **Respetar el SAR** (`pixelWidthHeightRatio`): **dos de los seis canales**
  son anamórficos — `estrellashd-mp4` (4:3) y `estrellassd-mp2v` (20:17) — e
  ignorarlo deforma la imagen. Los otros cuatro son 1:1.
- Modos de ajuste manual: Fit / Fill / Zoom / 4:3 / 16:9.
- **Tunneled playback activado por defecto** (01-oct, decisión de Edgar, que
  invierte la regla anterior). Es lo único medido que elimina los microcortes
  del cine en H.264: el decodificador AVC de Amlogic ignora el pulldown 3:2 y
  da 2 campos a cada imagen en vez de 3 y 2 alternos, lo que deja al
  reproductor 333 ms corto cada 1.6 s —el audio sigue, el vídeo se para—.
  Validado en pantalla dos veces y sin penalización de zapping (1.1 a 1.5 s en
  arranque limpio).

  **El riesgo sigue vigente y se asume:** su fallo produce pantalla negra
  silenciosa, y es la función más dependiente del fabricante de toda la pila.
  Una caja que lo implemente mal arranca sin imagen.

  **La exclusión va por `deviceOverrides` del `config.json`**, no por
  recompilación: `"tunneling": false` para los modelos que no deban usarlo. El
  modelo por defecto es de exclusión, no de inclusión.

  **Consecuencia para el diagnóstico:** en modo tunneled el decodificador
  escribe directo al hardware de pantalla, así que el contador de fotogramas y
  `SurfaceFlinger --latency` **no ven nada**. Se pierden fps, huecos de entrega
  y entregas tardías. Antes de concluir que algo no ocurre en ese modo, hay que
  comprobar que el instrumento podría verlo si ocurriera.

### Red
- `connectTimeoutMs = 8000`, `readTimeoutMs ≥ 30000`.
- `LoadErrorHandlingPolicy` propia con backoff exponencial y reintentos
  efectivamente infinitos.
- **Prohibido cerrar el player ante un error de red.** Un canal caído por
  lluvia es un estado temporal: overlay discreto y seguir reintentando.
- **Prohibido vetar un decoder por errores ocurridos durante corrupción de
  señal.** En banda Ku, la lluvia hace fallar decoders sanos.

### Backends del sistema
Son **dos, separados**:

| | VOD | Canales en vivo |
|---|---|---|
| Servidor | Jellyfin del ISP | Servidor propio de configuración |
| URL | la configura el usuario | cableada por flavor |
| Auth del contenido | usuario Jellyfin | ninguna (v1) |
| Acceso a la sección | sesión Jellyfin | **sesión Jellyfin** |

**La sección de canales queda detrás del inicio de sesión.** El suscriptor
entra a Jellyfin y desde ahí llega a TV en vivo, igual que a Inicio o Buscar.

Los dos backends siguen siendo independientes: los canales se sirven desde el
servidor de configuración propio y no requieren credenciales, y el módulo
`livetv` no consulta al servidor Jellyfin para nada. Lo que la sesión controla
es **quién ve la sección**, no de dónde salen los canales.

Consecuencia práctica: la sesión de Jellyfin es la puerta única de la app, así
que el enganche natural es la barra principal, junto a Inicio y Buscar, y no
el destino raíz de la navegación.

---

## 7. Fuera de alcance

- ABR, DRM, transcodificación en cliente
- Transcodificación de **video** en el headend
- Subtítulos **de pista aparte**. Los *closed captions* **sí entran** desde el
  01-oct: `estrellashd` los emite incrustados en el vídeo como CEA-608 y con
  texto real —medido: 22 entradas con diálogo en 40 s—, así que «no se emiten»
  era falso para ese canal. Se exponen pidiéndole al extractor HLS que los
  busque (`exposeCea608WhenMissingDeclarations`) y se eligen desde los
  controles de la barra. De los seis canales, **solo ese los trae**
- Grabación, DVR y catch-up en la app (el servidor lo soporta, la app aún no)
- Autenticación de suscriptor (versión futura)
- Filtros de imagen, post-procesado, "mejoras" de video
- TvInputService / Android TV Input Framework (es OEM-only en la práctica)
- Auto-actualización del APK fuera de Play (Play lo prohíbe)
- Refactorizar código existente fuera del alcance

---

## 8. Marca blanca

Cada ISP cliente es un **product flavor** de Gradle:

```kotlin
productFlavors {
    create("garratv") {
        applicationId = "mx.garratv.tv"
        resValue("string", "app_name", "GarraTV")
        buildConfigField("String", "CONFIG_URL", "\"https://.../garratv/config.json\"")
        buildConfigField("boolean", "VOD_ENABLED", "true")
    }
}
```

Icono, banner de TV y colores en `src/<flavor>/res/`.

**Ningún flavor usa el nombre ni el logo de Jellyfin.** Cada uno lleva su
marca, y la pantalla de información incluye "no afiliado al proyecto Jellyfin"
junto con los avisos de licencia GPL-2.0 (fork) y LGPL-2.1 (FFmpeg, libVLC).

---

## 9. Entorno de pruebas

- **Jellyfin (VOD):** `http://192.168.20.249:8096/` — usuario `test`, clave `test`
- **Canales:** `http://192.168.11.250:8181/<stream>/mono.m3u8`
- **Caja de pruebas:** Skyworth YDA "4K Google TV Box", Amlogic Cortex-A35,
  Android 11 (API 30), en `192.168.20.132`

```bash
adb connect 192.168.20.132:5555
adb -s 192.168.20.132:5555 install -r app-<flavor>-debug.apk
adb -s 192.168.20.132:5555 logcat -v time | grep -iE "livetv|ExoPlayer|MediaCodec"
```

**Advertencia:** esta caja es el mejor caso posible (MPEG-2, MP2 y AC-3 por
hardware). No desarrolles solo contra ella. Usa el menú de degradación
descrito en `LIVETV_ENGINE.md` para simular cajas peores.

---

## 10. Objetivos numéricos

| Métrica | Objetivo | Aceptable |
|---|---|---|
| Zapping p50 | < 2 s | < 3 s |
| Zapping p95 | < 3 s | < 4 s |
| Arranque al primer canal | < 4 s | < 6 s |
| Rebuffer ratio | < 0.3 % | < 1 % |
| Rebuffers por hora | < 1 | < 3 |
| Recuperación tras corte | < 8 s * | < 15 s |
| Crash-free sessions | > 99.5 % | > 99 % |
| Uptime continuo | > 48 h | > 24 h |

\* **El objetivo de < 8 s no es alcanzable hoy.** Medido: cuando la fuente
cae, Flussonic tarda ~15 s en reconstruir el playlist, y el cliente no puede
recuperarse antes que él. Queda condicionado a los pendientes 6 y 7 de
`LIVETV_LOG.md` (bajar `source_timeout` y poner el letrero de respaldo en el
headend). Mientras tanto, el objetivo real es el de la columna "Aceptable".
