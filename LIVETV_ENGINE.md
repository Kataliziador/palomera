# LIVETV_ENGINE.md

Especificación del motor de reproducción. Este documento define **cómo decide**
el módulo qué usar para cada canal en cada caja.

Todo lo de aquí es **Modo A**: paso a paso, con validación en pantalla.

---

## 1. Estructura del módulo

```
livetv/
  engine/
    LiveEngine.kt            interfaz común
    Media3LiveEngine.kt      motor principal
    VlcLiveEngine.kt         respaldo (fase M6)
  capabilities/
    DeviceCapabilities.kt    qué puede esta caja
    BackendDecider.kt        canal + caja + servidor → decisión
    CodecBlacklist.kt        decoders vetados (del servidor)
  source/
    ConfigRepository.kt      cliente del config.json
    ChannelProfile.kt        perfil por canal
  resilience/
    LiveWatchdog.kt          supervisor y escalera de recuperación
    LiveErrorPolicy.kt       LoadErrorHandlingPolicy propia
  diagnostics/
    LiveStatsCollector.kt    AnalyticsListener + buffer circular
    DevMenu.kt               toggles y degradación forzada
  LiveTuning.kt              TODAS las constantes
```

**La interfaz no está aquí.** Vive en `app/.../ui/livetv/`, reescrita desde
cero sobre `JellyfinTheme` y `ui/base/`, porque la sección tiene que verse como
el resto de la app y el sistema de diseño está dentro de `app` (`CLAUDE.md`
§4). El módulo `livetv` **no depende de `app`**: la dependencia va en un solo
sentido.

```
app/src/main/java/org/jellyfin/androidtv/ui/livetv/   ← reescrito (Modo B)
    LiveTvFragment.kt        la sección: grupos, favoritos, número directo
    LiveTvPlayerFragment.kt  pantalla de reproducción + overlays
    LiveTvChannelCard.kt     tarjeta de canal, al estilo del resto
```

El límite entre los dos es la interfaz `LiveEngine`: la UI habla con ella y
con nada más.

---

## 2. La interfaz `LiveEngine`

```kotlin
interface LiveEngine {
    val id: String                               // "media3" | "libvlc"
    fun attach(surface: SurfaceView)
    fun prepare(channel: ChannelProfile, plan: PlaybackPlan)
    fun play()
    fun stop()
    fun release()
    fun selectAudioTrack(track: AudioTrackRef)
    fun setAspectMode(mode: AspectMode)
    fun addListener(l: EngineListener)
    fun stats(): EngineStats
}
```

La UI habla **solo** con esta interfaz. Nunca con `ExoPlayer` ni con
`MediaPlayer` de libVLC directamente.

`selectAudioTrack` **no recibe un PID**. Flussonic los normaliza y entrega los
seis canales con audio en `0xdd` (+`0xde` el segundo), así que el PID no
identifica una pista (ver `CHANNELS.md` §2.bis):

```kotlin
data class AudioTrackRef(
    val language: String?,   // normalizado: spa|esp|esl|es → "spa"
    val pid: Int,            // desempate dentro de una misma sesión
    val channels: Int
)
```

La normalización de idioma es **parte del motor**, no de la interfaz: sin ella
no hay forma de distinguir el español del inglés en los canales de 70W.

---

## 3. Capacidades del dispositivo

Se calculan **una vez** al arranque y se cachean con `Build.FINGERPRINT` como
clave: si la caja recibe una actualización de firmware, se recalculan.

```kotlin
data class DeviceCapabilities(
    val decoders: List<DecoderInfo>,   // de MediaCodecList(ALL_CODECS)
    val abis: List<String>,
    val sdkInt: Int,
    val totalRamMb: Int,
    val manufacturer: String,
    val model: String,
    val device: String,
    val hasVlcBackend: Boolean
) {
    fun decoderFor(mime: String): DecoderInfo?
    fun hasHardwareFor(mime: String): Boolean
    fun hasAnyFor(mime: String): Boolean
}
```

MIMEs relevantes para estos canales:

| MIME | Para qué |
|---|---|
| `video/avc` | cinco canales |
| `video/mpeg2` | canal `estrellassd-mp2v` — **el único caso difícil real** |
| `audio/mp4a-latm` | AAC — el único audio, tras la normalización en Flussonic |

`audio/mpeg-L2`, `audio/ac3` y `audio/eac3` **ya no son responsabilidad de la
app**: el headend normaliza todo el audio a AAC, que decodifica cualquier caja
Android. Esto reduce la cascada de audio a un caso trivial y deja el trabajo
difícil donde de verdad está: el **video** MPEG-2.

**Trampas conocidas:**
- Enumerar con `ALL_CODECS`, no `REGULAR_CODECS`: los decoders tunneled-only
  no aparecen en el segundo.
- `isHardwareAccelerated()` existe desde API 29. En versiones anteriores hay
  que inferir por prefijo: `c2.android.*` y `OMX.google.*` son software.
- **Declarado no es funcionando.** El XML puede listar un decoder que falla
  con streams reales. La cascada debe poder reaccionar en caliente.

---

## 4. La cascada

Se evalúa **por canal**, al preparar la reproducción.

### 4.1 Prioridad de las fuentes de decisión

```
1. deviceOverrides del servidor   ← lo más específico, gana siempre
2. preferencia del canal          ← si la caja puede cumplirla
3. detección propia de la caja    ← el respaldo que nunca falla
```

Si el servidor pide algo imposible en esa caja, se ignora, se registra en el
log y se continúa con la detección propia. **Nunca** obedecer a ciegas.

### 4.2 Cascada de video

| Paso | Condición | Resultado |
|---|---|---|
| 1 | Hay decoder de **hardware** para el MIME | Media3, hardware |
| 2 | Hay decoder de **software** de plataforma | Media3, software |
| 3 | Existe el backend libVLC | libVLC |
| 4 | Nada de lo anterior | error claro al usuario + telemetría |

**El paso de FFmpeg se eliminó porque nunca existió.** La extensión FFmpeg de
Media3 es **solo de audio**: no hay renderer de video. Decodificar
`mpeg2video` por esa vía habría exigido escribir un renderer propio. El único
respaldo de video por software es libVLC.

En la práctica solo `estrellassd-mp2v` puede llegar al paso 3, y solo en una
caja sin decoder MPEG-2. Los cinco canales H.264 se resuelven en el paso 1 en
cualquier caja Android.

### 4.3 Cascada de audio

**Ya no hay cascada de audio.** Con el audio normalizado a AAC en Flussonic
(`CLAUDE.md` §2), todo canal llega en `audio/mp4a-latm`, que decodifica
cualquier caja Android desde API 16. No hay caso de respaldo que ejercitar.

Lo que esta decisión eliminó: la extensión FFmpeg, el NDK, la compilación
nativa por cada versión de Media3, el cumplimiento LGPL-2.1 de FFmpeg, y la
preocupación por AC-3/E-AC-3 en canales futuros.

**Lo que queda por hacer en audio** no es decodificar, sino *elegir*: los dos
canales de 70W traen `spa` y `eng`, y la selección va por idioma normalizado
(§2), nunca por PID ni por índice.

### 4.4 Cascada de entrelazado

**Cuatro** de los seis canales llegan entrelazados — `estrellashd` y
`estrellassd` de 77W con orden `tt`, `discoveryhd` con `tt` y `hbohd` con
**`bb`**. Media3 **no desentrelaza** y el headend no transcodifica video, así
que se resuelve por niveles:

| Nivel | Caso | Solución | Viabilidad |
|---|---|---|---|
| 1 | El VPU de la caja desentrelaza | Media3 normal | ideal |
| 2 | SD entrelazado, sin VPU | libVLC con decodificación **por software** + yadif | viable |
| 3 | HD 1080i, sin VPU | shader de desentrelazado en GPU (Media3 effects) | **no implementado** |
| 4 | Nivel 3 no viable en ese modelo | limitación documentada del dispositivo | último recurso |

**El nivel 3 no existe en el código, y su hueco se retiró del `enum`** el
09-oct (pendiente 54): `DeinterlaceMode` tenía un `GPU_SHADER` que ninguna rama
de `PlanBuilder` podía devolver, y un valor así se lee como una capacidad que la
app tiene. Si se escribe algún día, se añade entonces.

**Y el nivel 2 cubre más de lo que dice esta tabla.** Pone «SD entrelazado»
porque se escribió pensando en el coste de desentrelazar por software, pero
medido el 09-oct en la Skyworth, el 1080i por software cuesta **63 % de CPU**
frente al 14 % del 480i. La regla implementada no distingue resolución: la
gobierna `vpuDeinterlaces` por modelo, y lo que hace inviable el HD es el
precio, no el código. Para un decoder que existe y entrega mal —el caso real de
esa caja— la llave es `blockedDecoders`, no esta tabla.

**Trampa:** un backend que decodifica por MediaCodec con render directo a
superficie **no aplica filtros**. Para desentrelazar con libVLC hay que
forzar decodificación por software.

**El nivel 1 no se puede consultar por API.** Se valida mirando la pantalla,
una vez por modelo, y el resultado se publica en `deviceOverrides`.

### 4.5 Esqueleto

```kotlin
data class PlaybackPlan(
    val engineId: String,
    val videoPath: DecodePath,     // HW | SW_PLATFORM | SW_VLC
    val audioPath: DecodePath,
    val deinterlace: DeinterlaceMode,  // NONE | VPU | SOFTWARE
    val targetOffsetMs: Long,
    val tsFlags: Int,
    val reason: String             // se muestra en el overlay de stats
)

fun buildPlan(ch: ChannelProfile, caps: DeviceCapabilities,
              cfg: RemoteConfig): PlaybackPlan {

    cfg.overrideFor(caps, ch)?.let { return it.toPlan(ch, "override del servidor") }

    val allowed = ch.enginesAllowed ?: cfg.defaults.enginesAllowed

    val videoPath = when {
        caps.hasHardwareFor(ch.videoMime)  -> DecodePath.HW
        caps.hasAnyFor(ch.videoMime)       -> DecodePath.SW_PLATFORM
        caps.hasVlcBackend && "libvlc" in allowed -> DecodePath.SW_VLC
        else -> return PlaybackPlan.unsupported(ch)
    }
    // audio: no hay cascada, todo llega en AAC (§4.3)
    // entrelazado: nivel según ch.isInterlaced, ch.isHd y cfg.vpuDeinterlaces(caps)
    // ...
}
```

Cada decisión guarda su **motivo** en `reason`, visible en el overlay. Sin
esto, depurar en una caja ajena es imposible.

### 4.6 De dónde sale `ch.videoMime`

`buildPlan()` necesita el códec **antes** de preparar la reproducción, y el
stream todavía no se ha abierto. De ahí que el `config.json` lleve
`videoCodec` y `audioCodec` por canal (§8).

**Pero el config es una pista, no la verdad.** Si el headend recodifica un
canal y nadie actualiza el JSON, el plan se habrá construido sobre un dato
falso. Por eso:

> El plan se **reevalúa** al descubrirse las pistas reales. Si el códec
> observado no coincide con el declarado, se reconstruye el plan, se registra
> la discrepancia con canal y códecs, y **manda el stream**.

`enginesAllowed` es un **filtro duro**: un motor ausente de esa lista no se
elige nunca, ni siquiera como último recurso. Estaba en el esquema sin usarse.

---

## 5. Sintonía de Media3

Todos estos valores viven en `LiveTuning.kt` y son sobrescribibles por canal
desde el `config.json`.

### Buffer y live offset

```kotlin
DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs */ 15_000,
        /* maxBufferMs */ 30_000,
        /* bufferForPlaybackMs */ 2_000,
        /* bufferForPlaybackAfterRebufferMs */ 4_000)
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()
```

`targetOffsetMs` por canal: **12000 para los satelitales** (banda Ku, lluvia
frecuente) y **8000 para los de TotalPlay**. Más colchón solo donde compra
resiliencia.

**Pero hay un suelo que no controlamos, y manda sobre lo pedido.** Medido el
2026-09-24: pidiendo 8000 ms el offset real se estabiliza en **12380**, y
pidiendo 4000 se **aleja** hasta 11752. La causa es que el playlist no emite
`EXT-X-SERVER-CONTROL`, y sin esa etiqueta la especificación HLS fija el margen
seguro en **3 × TARGETDURATION**. Media3 no baja de ahí.

> **Corregido el 2026-10-10, leyendo la fuente de Media3 1.8.0 en vez de
> deducirlo de las medidas. El párrafo de arriba atribuye el desajuste a quien
> no es.**
>
> `HlsMediaSource.getLiveWindowDefaultStartPositionUs` coloca el arranque en
> `duración − objetivo` y después lo **redondea hacia atrás al principio del
> segmento que lo contiene**. Ahí está todo el desajuste: con segmentos de
> 2,8 s, pedir 14 000 da hasta 2,8 s de más. Medido, 16 674 y 16 707.
>
> Y `3 × TARGETDURATION` **no recorta lo que pedimos**: en
> `getTargetLiveOffsetUs` es la rama de reserva —«Fallback, see RFC 8216»— que
> se usa solo cuando no hay `startOffset`, ni `HOLD-BACK`, ni objetivo en el
> `MediaItem`. Nosotros sí mandamos objetivo, así que esa rama ni se toca.
>
> Lo que sí sigue en pie del 06-oct: emitir `EXT-X-SERVER-CONTROL` no mejora
> nada. Pero la razón no es un suelo que nos clava, sino que no hay nada que
> desclavar; lo que compra `HOLD-BACK` es **otra cosa**, y está en el
> pendiente 74.

#### Qué mide `currentLiveOffset`, y por qué miente cuando el playlist se para

Costó dos pendientes (70 y 71) y una noche de diagnóstico equivocado, así que
queda escrito. `currentLiveOffset` es **reloj de pared contra
`EXT-X-PROGRAM-DATE-TIME`**: no mide la distancia a lo último que hay
descargable, mide cuánto se ha retrasado la reproducción respecto a la hora en
que se emitió lo que se está viendo.

Mientras el origen publica, las dos cosas coinciden. Cuando el playlist deja de
crecer, **el offset sigue subiendo** —medido el 10-oct: 39 → 53 → 72 → 81 s—
aunque el reproductor esté pegado al final de todos los datos que existen, con
la posición a 6,4 s del final de una ventana de 33,3.

Consecuencia para cualquiera que decida algo con ese número: **un offset grande
no significa que haya a dónde saltar.** Para eso hay que mirar la posición
contra la duración de la ventana, que es lo que hace `VueltaAlBorde`.

Consecuencia: **el `targetOffsetMs` por canal del `config.json` no se aplica**
mientras el headend no emita un `HOLD-BACK` explícito (pendiente 21). El valor
real lo decide el `TARGETDURATION` de cada canal — 3 o 4, o sea 9 o 12 s.

No es catastrófico: para los satelitales este documento pedía 12000 ms por
resiliencia ante lluvia, y 12380 es justo eso. Lo que no tenemos es el control.
El motor **avisa en el log y en el overlay** cuando lo pedido no se aplica, en
vez de dejar que parezca que sí.

**Y además hay un techo.** Medida la ventana DVR real (`CHANNELS.md` §2.bis),
los dos canales de 70W eran los más cortos — `hbohd` 13.88 s — justo los que
llevan 12000. Eso dejaba **1.9 s** antes de `BEHIND_LIVE_WINDOW`: el colchón
contra la lluvia era lo que rompía el canal.

> **`targetOffsetMs` nunca por encima de la mitad de la ventana medida del
> canal.** Con `segment_count 12` en Flussonic (~28 s de ventana), los 12000
> quedan holgados. Si un canal futuro trae un GOP que acorte su ventana, esta
> regla lo detecta antes que el suscriptor.

El motor debe **verificar la ventana al preparar** y recortar el
`targetOffsetMs` si el config pide más de lo que el playlist puede sostener,
registrando el ajuste. Es el mismo principio de §4.1: el servidor sugiere, la
caja verifica.

### Playlist detenido

Media3 declara el playlist atascado a las **3.5** duraciones-objetivo y lanza
error. Con `TARGETDURATION 4` eso son ~14 s, y está medido que cuando la fuente
cae **Flussonic tarda ~15 s en reconstruir el playlist**. El umbral por defecto
salta justo antes de que el servidor se recupere: convierte un chubasco en un
error de reproducción, que es lo que §6 de `CLAUDE.md` prohíbe.

**Implementado en M2:** coeficiente **10** (unos 40 s de margen), vía
`DefaultHlsPlaylistTracker`, con interruptor en el menú para volver al 3.5 de
Media3 y poder comparar en pantalla.

### El margen de buffer, que es lo que de verdad protege

Implementado el 11-oct (pendiente 75), **apagado de fábrica hasta que Edgar lo
valide**: interruptor «Alejar del borde si el buffer no aguanta».

El `targetOffsetMs` decide dónde arranca la reproducción, y hasta aquí todo lo
de esta sección habla de eso. Lo que ninguna de esas palancas cubre es **qué
pasa con la posición después**, cuando el origen se para y vuelve.

Medido el 10-oct, dos veces en cinco horas: al pararse el origen el reproductor
consume hasta el final de los datos y se queda ahí, a 1,5 s del borde. Cuando el
origen vuelve, publica más despacio que el reloj durante medio minuto, y el
reproductor —que ya está pegado al borde— se queda sin nada cada tres segundos:

```
19:57:42  listo        true   buffer 3.3     19:57:46  almacenando  false  buffer 2.7  → corte
19:57:44  listo        true   buffer 4.4     19:57:48  listo        true   buffer 3.7  → corte
```

**12 cortes en 33 s y 13 en 38 s: 25 de los 35 de la sesión.** El resto de la
sesión da 2,0 cortes/h y cumple §10.

**Ninguno de los dos mecanismos que ya existen puede verlo:**

- el **vigilante** pregunta si la posición avanza, y avanza: se reproducen dos
  segundos de cada tres;
- la **vuelta al borde** vive en `onTimelineChanged`, y con la lista del origen
  congelada ese método no se llama. Medido, 38 segundos sin una sola evaluación.

Por eso se mira **en el latido**, que es el único reloj que no depende del
origen: si el buffer lleva cuatro latidos seguidos por debajo de
`MARGEN_MINIMO_MS`, la posición se mueve a `ventana − objetivo`.

**Y se mueve hacia atrás, que es lo que cuesta de aceptar.** En el latido que lo
motivó la ventana tenía 33,6 s de datos y el reproductor no tenía nada por
delante, porque solo almacena hacia adelante y estaba al final. Moverse a 19,6 s
le pone **12,5 s ya publicados delante**, por un corte. Es lo que el
comportamiento viejo hacía por accidente y el pendiente 73 quitó creyéndolo
desperdicio: medido, ese salto llevó el buffer de 3,9 a 12,9 s y compró treinta
segundos seguidos sin un corte.

Tres cosas que vienen de la medida y conviene no deshacer:

- **No se exige estar reproduciendo.** La guarda «solo reproduciendo» de la
  vuelta al borde existe porque saltar *hacia adelante* mientras se almacena no
  adelanta nada. Aquí es al revés: los cinco momentos medidos en que esto debía
  actuar tenían `reproduciendo=false`, que es exactamente cuando hay datos
  detrás sin consumir.
- **El umbral no es delicado**, al contrario que el disparador por fracción de
  ventana (pendiente 72). Probado contra los dos volcados con 5, 6 y 7 s
  cruzado con 3, 4 y 5 latidos: las nueve combinaciones dan los mismos disparos,
  y ninguna dispara en las 4 h 10 min limpias. Esta señal no tiene zona
  intermedia.
- **Comparte el enfriamiento con la vuelta al borde**, mismo reloj: son dos
  mecanismos sobre la misma posición y con relojes separados se pelearían.

**Cuando la ventana no da para apartarse** —`oncesd` y `canal22sd` sirven 22 s—
no se salta: se registra `offset.sinSitioDetras` una vez por sesión. Ese no es
un problema del cliente sino del `segment_count` del site, y el servicio de
canales ya lo señala en su `/health`.

### Errores y su respuesta

| Error | Respuesta |
|---|---|
| `ERROR_CODE_BEHIND_LIVE_WINDOW` | `seekToDefaultPosition()` + `prepare()` |
| `ERROR_CODE_IO_NETWORK_*` | reintento con backoff, no cerrar el player |
| `ERROR_CODE_PARSING_CONTAINER_MALFORMED` | recrear MediaSource |
| `ERROR_CODE_DECODING_FAILED` | siguiente paso de la cascada |
| `ERROR_CODE_DECODER_INIT_FAILED` | siguiente paso de la cascada |

### Flags del extractor TS

Valores base: `FLAG_DETECT_ACCESS_UNITS = OFF` y
`FLAG_ALLOW_NON_IDR_KEYFRAMES = OFF` (los canales traen AUDs e IDR).

Deben ser **configurables en runtime**: en Amlogic la experiencia de campo
sugiere lo contrario y hay que validarlo en hardware.

**En HLS los flags no se heredan** de `DefaultMediaSourceFactory`: hay que
pasarlos vía `DefaultHlsExtractorFactory`.

---

## 6. Watchdog y recuperación

Supervisor que corre cada segundo y observa: posición estancada con
`playWhenReady=true`, frames perdidos, underruns de audio, tiempo desde el
último frame renderizado, tiempo desde la última actualización del playlist.

Escalera, cada escalón se dispara solo si el anterior no funcionó:

1. Esperar (puede ser un hueco normal)
2. `seekToDefaultPosition()`
3. `prepare()`
4. Recrear el MediaSource
5. Recrear el player
6. Siguiente paso de la cascada

**Regla dura:** los errores ocurridos durante una ráfaga de corrupción de
señal **no cuentan** contra el decoder. En banda Ku la lluvia hace fallar
decoders sanos, y vetarlos degradaría la caja permanentemente por una tormenta.

### Mensajes al usuario

| Situación | Mensaje |
|---|---|
| El playlist responde pero no avanza | "Señal débil por condiciones climáticas" |
| El playlist no responde | "Revisa tu conexión a internet" |
| El canal no es compatible con la caja | "Este canal no es compatible con tu dispositivo" |

Distinguir las dos primeras evita llamadas a soporte por algo que el
suscriptor no puede arreglar.

---

## 7. Menú de degradación (obligatorio)

La caja de pruebas es el mejor caso posible. Sin poder degradarla, los caminos
de respaldo llegarían a producción sin haberse probado nunca.

El menú de desarrollador debe permitir:

- Vetar manualmente cualquier decoder por nombre
- Desactivar el decoder MPEG-2 de hardware — **el más importante de todos**:
  es lo que ejercita el único camino de respaldo que queda (libVLC)
- Forzar un backend concreto
- Forzar decodificación por software
- Forzar cada nivel de la cascada de entrelazado
- Cambiar flags de TS, buffers y `targetOffsetMs` en caliente
- Forzar un `targetOffsetMs` mayor que la ventana, para provocar
  `BEHIND_LIVE_WINDOW` a voluntad y probar la recuperación
- Ver el plan de reproducción activo con su motivo
- Exportar el log a archivo

### Y solo en compilaciones de depuración

Decidido el 11-oct. Hasta entonces el menú viajaba en el APK de release, así
que cualquier abonado tenía a un toque forzar software, vetar decoders y, sobre
todo, `registryUrlOverride`, que **salta la puerta del registro entera** desde
la interfaz. `CLAUDE.md` §8 ya acepta que esa puerta no es un candado técnico
—el fork es GPL-2.0 y cualquiera puede compilar una versión sin ella—, pero
heredar la exposición por descuido no es lo mismo que decidirla.

Tres detalles que conviene no perder:

- **La puerta es `BuildConfig.DEBUG`, no `DEVELOPMENT`.** Lo segundo se define
  en `app/build.gradle.kts` como `versionCode < 100`, así que en cuanto se fije
  el esquema de versiones real para Play se apagaría **también en depuración** y
  perderíamos el menú en la caja de pruebas, que es donde hace falta.
- **Se comprueba en los dos sitios**, el botón de la sección y el contenido del
  destino. Esconder solo el botón deja el destino alcanzable por navegación.
- **El caso de campo no se queda sin cubrir.** Una caja de un abonado que falla
  informa por la telemetría anónima de B6 —modelo, decoders, fallos por canal,
  y desde el 11-oct el códec de audio recibido—, que existe precisamente porque
  a esas cajas no se puede entrar.

El release **sigue conteniendo el código y sus cadenas**, porque upstream
compila con `isMinifyEnabled = false` y nada se elimina. Lo que desaparece es
el camino para llegar: el menú no se dibuja. Y buscar `registryUrlOverride` en
el APK nunca fue una comprobación válida, porque esa misma cadena es la clave
de preferencias que `DevOptions` lee en producción.

---

## 8. El `config.json`

Un archivo por ISP. Fuente de verdad de la lista, la sintonía y los overrides.

```json
{
  "schema": 1,
  "epgUrl": "https://servidor/garratv/epg.xml",
  "defaults": {
    "engine": "media3",
    "targetOffsetMs": 8000
  },
  "channels": [
    {
      "id": "discoveryhd-70w",
      "name": "Discovery HD",
      "group": "Documentales",
      "logo": "https://servidor/logos/discovery.png",
      "url": "http://192.168.11.250:8181/test-sat70w-discoveryhd-mp4/mono.m3u8",
      "epgId": "discovery.mx",
      "engine": "media3",
      "enginesAllowed": ["media3", "libvlc"],
      "targetOffsetMs": 12000,
      "videoCodec": "h264",
      "audioCodec": "aac",
      "audioDefault": "spa",
      "interlaced": true,
      "fieldOrder": "tt",
      "hd": true
    }
  ],
  "deviceOverrides": [
    {
      "match": { "manufacturer": "SkyworthDigital", "device": "YDA" },
      "vpuDeinterlaces": true,
      "channels": {
        "estrellassd-mp2v": { "engine": "libvlc", "deinterlace": "software" }
      }
    }
  ],
  "decoderBlacklist": []
}
```

Reglas del cliente de configuración:

- **`videoCodec` y `audioCodec` son obligatorios.** La cascada los necesita
  antes de abrir el stream (§4.6). Son una pista: si el stream real difiere,
  manda el stream y se registra la discrepancia.
- **`fieldOrder` es informativo.** El orden de campo se lee del stream, nunca
  se fija por configuración (`CHANNELS.md` §3). Está en el JSON solo para
  detectar discrepancias, no para aplicarlo.
- **Caché local.** Si el servidor no responde, usa la última configuración
  válida. El APK trae una de fábrica para el primer arranque.
- **Versión de esquema.** Las apps viejas ignoran campos que no conocen.
- **Nunca código.** El servidor elige entre motores que ya vienen en el APK.
- **Historial en git.** Un cambio que rompa algo se revierte en un minuto.

---

## 9. La decisión que queda abierta

### El desentrelazado en la caja de pruebas

Pendiente de validar en pantalla. Determina si el nivel 1 aplica y si libVLC
entra desde el principio o solo para otras cajas.

### ~~El audio MP2~~ — cerrada el 2026-09-22

Se convierte a AAC en Flussonic, y se elevó a política: **todo el audio se
normaliza a AAC**. Con ~120 canales la excepción caso por caso no escalaba.

Desaparecen la extensión FFmpeg, el NDK, la compilación nativa y el
cumplimiento LGPL-2.1. Queda una tarea de headend: aplicar la conversión a los
canales MP2 del lineup, empezando por `estrellassd-mp2v`.
