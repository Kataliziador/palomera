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
| 19 | El fork base ya trae `media3-ffmpeg-decoder` (GPL-3.0) | **B7** | en `app/build.gradle.kts:161` y en `playback/media3/exoplayer`. Quitarlo choca con "VOD intacto" y con el límite de un archivo tocado. Se decide antes de Play Store, no antes |
| 20 | Traer `scan-canales.sh` al repositorio | B | vive suelto en `/home/scan-canales.sh` y produce la fuente de verdad |

---

## Entradas

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
