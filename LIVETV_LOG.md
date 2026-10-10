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
| ~~5~~ | ~~Flags de extractor en Amlogic~~ | — | **cerrado 07-oct: se queda la de fábrica**, `nonIDR=ON` y `AUD=off`. Lo decide un número: con `FLAG_ALLOW_NON_IDR_KEYFRAMES` apagado, `discoveryhd` tardó **6953 ms** en dar imagen contra 1966 ms con él puesto. `FLAG_DETECT_ACCESS_UNITS` no mejora nada medible |
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
| ~~22~~ | ~~**`hbohd` transporta cine 23.976 con cadencia 3:2**~~ | — | microcortes que Edgar ve y los contadores no. Medido: 358 intervalos de 50 ms alternando con 357 de 33 ms, frente a 894 uniformes de 33 en `discoveryhd`. Descartado `FLAG_DETECT_ACCESS_UNITS`. Ver `CHANNELS.md` §3.quater | **Cerrado 08-oct con el 24**: es el mismo fenómeno visto desde el canal. Validado en pantalla, se acepta |
| ~~16~~ | ~~**Validar desentrelazado con los dos tipos de codificación**~~ | — | **revisar:** se justificaba en que `hbohd` era `bb`, y medido es `tt`. Ya no hay canal con orden invertido en el conjunto | `discoveryhd-70w` por frames, `hbohd-70w` por campos. Estos dos bastan: cubren las cuatro combinaciones sin depender de `estrellashd`, que está en disputa (#14) | **Cerrado 08-oct, validado por Edgar: «no hay peines en ninguno, la imagen se ve bien».** Cubiertas las dos codificaciones y las dos resoluciones con `discoveryhd` (1080i H.264) y `hbohd` (1080i H.264 + cine). El orden de campo invertido no se pudo cubrir porque **no existe** en el conjunto: la premisa de que `hbohd` era `bb` resultó falsa al medirlo. `estrellassd` queda fuera por el 55 |
| 17 | ~~Subir `segment_count` de 6 a 12~~ | — | **cerrado 23-sep: hecho.** Ventana medida de 29–36 s en todos los canales. Antes:| ventana DVR medida de 13.88 s en `hbohd` contra `targetOffsetMs` de 12000: 1.9 s de margen. Sin costo de CPU ni de latencia |
| 18 | ~~Aplicar MP2 → AAC a los canales MP2 del lineup~~ | — | **cerrado 26-sep: hecho y verificado.** Los seis canales entregan `aac 48000 Hz 2ch`, incluido `estrellassd-mp2v`, que era el único MP2. Con ello el módulo no necesita decoder de MP2 en ninguna caja |
| 19 | ~~El fork base trae `media3-ffmpeg-decoder` (GPL-3.0)~~ | — | **cerrado 22-sep: retirado.** Ver entrada abajo |
| ~~21~~ | ~~**Emitir `EXT-X-SERVER-CONTROL` con `HOLD-BACK` en Flussonic**~~ | — | **cerrado 06-oct: no procede, la premisa era falsa.** `HOLD-BACK` **no puede valer menos que `3 × TARGETDURATION`** (RFC 8216bis), que es exactamente el suelo que ya hay sin la etiqueta: emitirla no baja nada. Lo que fija el suelo es `TARGETDURATION`, y ese lo decide el GOP de la fuente, que no se puede acortar sin transcodificar vídeo (§2). La acción real pasa al generador: que calcule `targetOffsetMs` desde el `TARGETDURATION` de cada canal |
| ~~20~~ | ~~Traer `scan-canales.sh` al repositorio~~ | — | **cerrado 05-oct:** en `herramientas/`, que no se compila ni viaja en el APK (§4.7). Al traerlo se corrigieron dos desfases que lo habrían hecho medir mal: apuntaba a `mono.m3u8` en vez de al master, y su cabecera seguía recomendando decoders de MP2 nativos. Su salida se ignora en git |
| 23 | ~~`tunneled` deja sin imagen a los canales probados~~ | — | **RETIRADO 30-sep: era falso.** Salía de dos instrumentos que en modo tunneled **no ven nada**: el decodificador escribe directo al hardware de pantalla, así que ni `setVideoFrameMetadataListener` ni `SurfaceFlinger --latency` reciben fotogramas. Edgar miró `estrellassd` con tunneled activo y **se ve bien**. En ese modo solo valen los ojos |
| ~~34~~ | ~~**`mono.m3u8` solo expone una pista de audio a Media3**~~ | — | **cerrado 02-oct: se pasa a `index.m3u8`.** Decisión de Edgar, con un argumento que faltaba: en algunos satélites la etiqueta de idioma miente, así que ver la segunda pista no es un extra sino el único remedio del abonado |
| ~~37~~ | ~~**El modo tunneled falla una aserción de Media3 al cambiar de canal**~~ | — | `ERROR_CODE_FAILED_RUNTIME_CHECK` en `MediaCodecVideoRenderer.onEnabled`, la aserción del tunneling. **Medido el 07-oct durante M4: 16 fallos en 20 sintonizaciones, un 80 %** —muy por encima del 5–30 % que se había estimado antes, y casi uno por cambio de canal—. Sigue recuperándose: 19 de 20 dieron imagen, y la que no fue un canal caído en origen. **Y el 08-oct salió el otro lado de la balanza, que cambia la pregunta:** apagar el tunneled no sale gratis. En `hbohd`, 1,6 min sin tunneled dieron **15 rebuffers** —uno cada 4 s—, el offset trepó de 10,7 a 20,7 s, saltó la vuelta al borde y se perdieron 74 fotogramas; la misma caja con tunneled puesto, 5,6 min con **1 rebuffer** y el offset clavado en 10,8. Ni red ni CPU: 77 Mbps de media para un canal de 4 Mbps, CPU al 9 %, cero reintentos. Así que la decisión ya no es «¿molesta el fallo del tunneled?» sino «¿molesta más que un canal que se corta cada cuatro segundos?». Antes se midió entre el 5 % y el 30 % de los zapeos; **0 de 18 con tunneled apagado**. Siempre se recupera y el canal acaba reproduciendo. La pantalla ya no miente sobre la causa; la causa raíz es Modo A y toca la función más dependiente del fabricante de toda la pila | **Cerrado 08-oct: el tunneled se queda, activo de fábrica.** La decisión la zanjó medir el otro platillo — 20,4 min con tunneled dieron **0 rebuffers** y 1,6 min sin él dieron **15**, con el offset trepando a 20,7 s hasta disparar la vuelta al borde. El fallo de la aserción cuesta un tropiezo al zapear; apagarlo cuesta el canal. El interruptor se queda para excluir modelos por `deviceOverrides` |
| 36 | **El master duplica el audio 1: la variante lo lleva multiplexado y además lo declara como rendition** | headend | HBO y Discovery ofrecen «Español, Español, Inglés». Lo limpio es que la variante no lleve audio cuando hay grupo `AUDIO`; en la app sería adivinar, y una regla que se equivoque esconde una pista que el abonado necesitaba |
| ~~35~~ | ~~**Cajas con Android < 7.1.1 no validan el certificado del registro**~~ | — | **cerrado 05-oct, medido en un emulador de API 22.** `RegistryTrust` empaqueta ISRG Root X1, X2, YR e YE y las usa **solo** para el registro. Sin el arreglo: `CertPathValidatorException: Trust anchor for certification path not found`. Con él: `registro.respuesta autorizado=true` |
| ~~33~~ | ~~**`defaultChannel` se interpreta pero ningún camino lo usa**~~ | — | **cerrado 02-oct** con la memoria del último canal: decide el foco de la guía cuando la caja no tiene historia |
| ~~32~~ | ~~**El aviso del operador se interpreta pero no se dibuja**~~ | — | **cerrado 02-oct:** se dibuja bajo la marca, en la sección y no sobre el vídeo |
| ~~31~~ | ~~**La fila de inicio depende de un permiso de Jellyfin**~~ | — | **cerrado 03-oct:** `HomeSectionType.LIVE_TV` ya no se condiciona a `enableLiveTvAccess`. Ese permiso es del Jellyfin del ISP y habla de *su* LiveTV. **La caja de pruebas no lo demuestra** —su usuario tiene el permiso—, pero el cambio solo quita una condición: lo que antes salía sigue saliendo |
| ~~30~~ | ~~**El límite de 2 MB del `config.json` solo mira la longitud declarada**~~ | — | **cerrado 02-oct:** la lectura va acotada y falla en vez de truncar. Probado con una respuesta troceada de 3 MB | una respuesta troceada, sin `Content-Length`, se salta la comprobación y se lee entera en memoria. Con un servicio propio en LAN el riesgo es bajo, pero el límite no es el que dice ser |
| ~~29~~ | ~~**`audioDefault` se interpreta pero no se aplica**~~ | — | **cerrado 02-oct, validado por Edgar.** Activo de fábrica. La normalización es nuestra, no de Media3: `esl` no es código ISO y Media3 no lo casaría con `spa` |
| ~~28~~ | ~~**`logoUrl` se interpreta pero no se dibuja**~~ | — | **cerrado 02-oct** en la barra, con respaldo a iniciales cuando falta o falla. **Queda pendiente en la guía**: las tarjetas siguen sin logo, y con 120 canales es como se reconoce un canal de un vistazo |
| ~~27~~ | ~~**El inicio sigue consultando la LiveTV vieja de Jellyfin**~~ | — | **cerrado 02-oct:** fuera la fila de «en emisión» y los tres botones de DVR que llevaban de vuelta a la sección vieja. Cero llamadas |
| ~~38~~ | ~~**La primera pantalla lleva el logo y el nombre de Jellyfin**~~ | — | **cerrado 03-oct: la app se llama Palco.** Logotipo, icono y banner propios en vector, `applicationId` `mx.kataliziador.palco`. Queda **comprobar el nombre en IMPI (clases 38 y 41) y en la Play Console**: está elegido, no verificado |
| ~~39~~ | ~~**De la cabecera de la sección no se puede bajar a la guía**~~ | — | **cerrado 05-oct.** Era la búsqueda de foco geométrica de Compose: los dos botones viven a la derecha de donde acaba la fila de tarjetas, así que debajo no hay nada que encontrar. `focusProperties { down = … }` en la fila de cabecera, que los hijos heredan. Medido por `uiautomator` en la caja: desde «Buscar canal» y desde «Diagnóstico», ABAJO deja el foco en la tarjeta 101 |
| ~~40~~ | ~~**`REGISTRY_URL` ya se puede fijar**~~ | — | **cerrado 04-oct:** compilada en `BuildConfig`, con override por preferencias para apuntar a un registro de pruebas |
| ~~41~~ | ~~**El `host` que se mande al registro hay que normalizarlo aquí**~~ | — | **cerrado 04-oct:** `ServiceUrl.hostDeServidor` baja a minúsculas, quita el punto final del FQDN y acepta el esquema como opcional. Seis casos nuevos en `ServiceUrlTest` |
| ~~42~~ | ~~**La caja tarda en enterarse de que un canal cayó, y al revés**~~ | — | medido en el proyecto del servidor de canales: retira de la lista un stream que no emite, y una caída de un minuto vista por una caja que arranca justo entonces deja ese canal ausente durante **un ciclo de refresco**. Con los 15 minutos de hoy ya no son días, pero hay que decidir con datos de un site si el servidor debe esperar antes de retirar un canal caído. **Ocurrió de verdad durante M4:** `estrellashd` cayó en origen, el generador dejó de publicarlo y a las 01:15 la app pasó de 10 canales a 9 (`config.interpretada canales=9`). Volvió solo cuando el stream volvió. **Cerrado el 07-oct por el otro lado: el canal ya no desaparece.** El servidor publica el stream caído con `unavailable: true` (`servidor-canales@9c13b50`) y la app lo deja en la guía con su etiqueta «sin señal». La pregunta de cuánto esperar antes de retirarlo deja de tener sentido: no se retira. El retraso de hasta 15 min sigue ahí, pero ahora solo decide cuándo cambia una etiqueta, no si el canal existe |
| ~~43~~ | ~~**`TARGET_OFFSET_SATELLITE_MS` está muerto**~~ | — | **cerrado 05-oct: retirado.** No lo usaba nadie y además el valor era malo: con la ventana de ~22 s que sirve producción, 12 s pasa del techo de media ventana. Lo que queda en `LiveTuning` es el de fábrica, con la escalera `servidor ?: canal ?: 8000` escrita al lado para que no haga falta abrir `PlanBuilder` para saber quién manda |
| ~~44~~ | ~~**`TARGET_OFFSET_MAX_WINDOW_FRACTION` dice «nunca puede pasar» y solo avisa**~~ | — | **cerrado 05-oct en su mitad de Modo B:** pasa a llamarse `TARGET_OFFSET_WINDOW_WARN_FRACTION`, y el local y la clave del registro dejan de decir «techo». El comportamiento no cambia y no tenía que cambiar: quien protege de verdad es la vuelta al borde, sobre el offset **real**. **Recortar lo pedido sigue sin hacerse y sería Modo A.** Para saber si hace falta, el instrumento ya está puesto: que un site empiece a escribir `offset.superaLaVentana` es la señal, y lo primero que diría es que el `config.json` de ese site pide un offset que su ventana no da |
| ~~45~~ | ~~**Rescate del tunneled sin imagen**~~ | — | **cerrado 05-oct, validado en pantalla por Edgar y activo de fábrica.** Medido con la simulación: `tunRescate=1`, `conImagen=7` de 7 sesiones. El interruptor del menú se queda para reproducir el comportamiento anterior |
| ~~46~~ | ~~**La pantalla de denegación no se ha visto en una tele**~~ | — | su lógica tiene nueve pruebas y el camino autorizado está medido contra el registro de producción, pero el `SeccionCerrada` no lo ha mirado nadie. Para verlo: apuntar a un registro que deniegue y usar «Olvidar la autorización guardada» del menú. **Desde el 10-oct basta un interruptor del menú** —«Fingir que el registro deniega»—, con los dos textos posibles: el genérico y el de caducada, que es el largo y el que puede desbordar. **Cerrado el 10-oct: Edgar lo miró en la tele** con los dos textos, el genérico y el largo de caducada, y los dos se ven bien. Era lo último que le faltaba a B9: su lógica ya tenía nueve comprobaciones y el camino autorizado estaba medido contra el registro de producción |
| ~~47~~ | ~~**En Android < 7.1.1 el logo de marca del registro no carga**~~ | — | **cerrado 05-oct con un cargador propio** (`LogoLoader`), tal como decía la nota: el `ImageLoader` global no se toca, porque es el del VOD. Verificado en el emulador de API 22 contra el registro de producción: `marcaOrigen=registro` y el logo **en pantalla**, sin `marca.logoSinConfianzaAmpliada` y sin error de TLS |
| ~~48~~ | ~~**Con la búsqueda abierta, ARRIBA no vuelve a la cabecera**~~ | — | **cerrado 05-oct.** No era geométrico: `BasicTextField` se queda las flechas, igual que ya pasaba con ABAJO. Al arreglarlo salió un fallo mayor —el campo actuaba sobre teclas que no se habían pulsado en él— que es lo que de verdad cerró este pendiente. Medido: el bucle cabecera ↔ campo ↔ guía funciona en los dos sentidos |
| ~~49~~ | ~~**El logo de marca del site `pruebas` es el de Jellyfin**~~ | — | **cerrado 06-oct.** Edgar subió `palco-logo.png` desde el panel. Verificado de punta a punta: el registro sirve `marca/9fe751e3e75e6dc6.png`, cuyo SHA-256 coincide con el nuestro —ni recompresión ni recorte por el camino—, y la caja dibuja el logo en vez del nombre con `marcaOrigen=registro` |
| ~~50~~ | ~~**Los modos de imagen no se ven cambiar — y el SAR no se respetaba**~~ | — | Edgar, 06-oct: recorre Ajustar / Llenar / Zoom / 4:3 / 16:9 y la imagen no varía. **Parte es por diseño y conviene no confundirla:** cinco de los seis canales se presentan 16:9 en una pantalla 16:9 —incluido `estrellashd`, que es 1440×1080 con SAR 4:3—, y ahí Ajustar, Llenar y 16:9 calculan escala 1.000, o sea lo mismo. Solo Zoom (×1.12) y 4:3 (×1.33 en Y) deberían verse, y el único canal donde los cinco difieren es `estrellassd`. Lo que falta por saber es si Zoom y 4:3 tampoco hacen nada: si el log dice escalas distintas y la pantalla no cambia, el problema es que la superficie no respeta la transformación de la vista; si dice 1.000 en todo, es el cálculo. **Diagnosticado y arreglado el 06-oct detrás de «Dimensionar la superficie», apagado de fábrica.** El log zanjó la duda: las escalas salían bien y distintas —1.333, 1.493, 1.000— y la pantalla no cambiaba, así que la transformación de la vista no llega a la superficie. Y al mirar por qué, salió lo gordo: **nadie fija el modo de escalado del códec**, así que MediaCodec estira el vídeo hasta las dimensiones de la superficie, que siempre era la pantalla entera — los dos anamórficos llevaban saliendo deformados. Ahora se dimensiona la superficie. Medido por `SurfaceFlinger`: `estrellassd` pasa de 1920×1080 a **1440×1080**. **Cerrado y activo de fábrica el 06-oct**, validado en pantalla por Edgar: `estrellassd` con sus franjas y sin deformar, `estrellashd` a pantalla completa, el resto igual que antes. **Lo único que queda sin confirmar es si `FILL` y `ZOOM` recortan limpiamente** al salirse la superficie de la pantalla; va al pendiente 51 |
| ~~51~~ | ~~**`FILL` y `ZOOM`: la superficie se sale de la pantalla**~~ | — | con la superficie dimensionada, esos dos modos la hacen mayor que la pantalla a propósito — es lo que quita las franjas. Que el recorte salga limpio no lo puede responder `SurfaceFlinger` y no se ve en `screencap`, que devuelve el búfer vacío sobre el reproductor. Los demás modos están validados; estos dos solo se han mirado de pasada. Riesgo bajo —el borde de la pantalla recorta solo— pero no medido | **Cerrado 08-oct, validado por Edgar: «llenar y zoom quitan las franjas».** El recorte sale limpio; la superficie puede exceder la pantalla sin artefactos. Nota: en un 4:3 de verdad los dos modos se parecen —`FILL` recorta el 25 % del alto y `ZOOM` el 33 %— porque `FILL` ya quita las franjas él solo. `ZOOM` gana sentido en lo contrario: un 16:9 con las barras negras incrustadas en la propia imagen, donde `FILL` no tiene nada que recortar |
| 52 | ~~**La barra de canal salía sola con cada parpadeo del reproductor**~~ | — | **cerrado 08-oct.** El efecto que la muestra estaba atado a `enVivo`, y un rebuffer corriente son tres cambios de estado seguidos: medido en `estrellassd`, `buffering → ready → playing` en **371 ms**. Invisible en pantalla, y aun así sacaba la barra encima del vídeo. Ahora la saca la pulsación, y vuelve sola solo si el canal lleva parado más de 2 s. **Pendiente de validación en pantalla** |
| ~~53~~ | ~~**`estrellassd` se publica como progresivo y es 480i**~~ | B / headend | el `config.json` del site trae `interlaced: false` para un canal medido 480i (`CHANNELS.md`). El generador lo saca de la API de Flussonic, que para MPEG-2 no lo declara. **Hoy no cambia nada** —ver 54— pero es un dato falso en el contrato, y el día que algo lo use decidirá mal. Al revés también: `estrellashd` sale `interlaced: true` y es cine progresivo con cadencia 3:2. **Dejó de no cambiar nada con M6:** es lo único que elegiría libVLC solo. **Y la premisa era peor de lo escrito aquí**, medido el 09-oct contra el headend: Flussonic no omite el dato, responde `is_progressive: true` sobre el 480i. No había «no se sabe» que propagar, había un dato falso, así que ninguna heurística del generador podía distinguirlo de un progresivo de verdad. **Arreglado por el lado del servidor el 09-oct** (`servidor-canales`): `interlaced` se escribe a mano en el lineup aunque el canal salga de un `stream`, lo escrito gana, y el panel enseña el barrido que publica en vez del que dice la API. **Cerrado el 09-oct, validado en pantalla por Edgar.** Corrigió los cuatro canales en el panel —`estrellassd` a entrelazado, `estrellashd` y `hbohd` a progresivo— y la cascada eligió libVLC sola, con «Forzar desentrelazado» apagado: `plan.construido canal=estrellassd motor=libvlc motivo=el VPU de este modelo no desentrelaza`. La imagen, «fluida». Es la primera vez que el respaldo se elige por un dato del `config.json` y no por un interruptor |
| ~~54~~ | ~~**El modo de desentrelazado se calcula, se registra y no lo usa nadie**~~ | — | `PlanBuilder` resuelve `deinterlace` en cuatro valores y el único consumidor es la línea del log. El motor nunca le dice nada a Media3 ni al VPU. O sea: **la app no tiene hoy ninguna palanca sobre el desentrelazado**, y `deint=NONE` en un canal con peine no es la causa, es una etiqueta. Descubierto el 08-oct al investigar el peine de `estrellassd` | **Cerrado el 10-oct, en dos mitades y sin cambiar comportamiento.** `SOFTWARE` dejó de ser una etiqueta con M6: libVLC lo aplica de verdad. De los otros dos, `GPU_SHADER` se retira del `enum` —ninguna rama podía devolverlo, y un valor así se lee como una capacidad que la app tiene— y `VPU` pasa a decir en el motivo del plan «se espera que lo desentrelace el VPU (sin verificar)», que es lo que significa |
| ~~55~~ | ~~**`estrellassd` se presenta a 15 fps, la mitad de su cadencia**~~ | — | medido el 08-oct con el tunneled **apagado**, que es la única forma de que los contadores vean algo: `fps=15.0` estable en una fuente de 29.97, `huecoMaxMs=100`, **`perdidos=0`** —o sea el decodificador no entrega más, no es que se tiren—. Explica los dos síntomas de Edgar a la vez: el peine y «la imagen está lenta». La caja declara **un solo** decodificador para `video/mpeg2`, `OMX.amlogic.mpeg2.decoder.awesome2`, así que no hay alternativa que probar (confirma el 26). **Los dos descartes, hechos el 08-oct.** La superficie no es: con ella a 1920×1080 —verificado por `SurfaceFlinger`— siguen 15.0 fps. El canal tampoco está solo: `discoveryhd`, 1080i H.264 en las mismas condiciones, da **29.9 fps** limpios en 170 latidos. Y el stream entrega lo que declara: **600 fotogramas en 20 s medidos con `ffprobe`**, 30000/1001. Así que es el decodificador MPEG-2 de la caja, y no hay otro. **Es literalmente el caso que `CLAUDE.md` §6 le reserva a libVLC** —«MPEG-2 sin decoder en la caja, y desentrelazado por software de SD»—, o sea M6 | **Cerrado 09-oct con M6:** por libVLC se ve bien, sin peine y a cadencia normal, con menos del 16 % de CPU. **Con la salvedad del 53:** la regla que lo elegiría sola necesita que el canal llegue marcado como entrelazado, y no llega |
| 56 | ~~**El veto de decoders del menú no lo leía nadie**~~ | — | **cerrado 08-oct.** Se guardaba, se contaba en la línea de degradación y se enseñaba en el menú, y **ninguna línea lo aplicaba**. Descubierto al intentar probar M6 con él, que es exactamente para lo que existe. Ahora `DeviceCapabilities.excluyendo()` lo aplica, y las capacidades se recalculan con `revision` |
| 57 | ~~**`hasVlcBackend` dependía de quién preguntara primero**~~ | — | **cerrado 08-oct.** Era un parámetro de `DeviceCapabilities.of()`, y la caché devuelve lo primero que se construyó ignorando los argumentos siguientes: `RegistryRepository` pregunta al arrancar, antes que el reproductor, y fijaba `false` para todo el proceso. Con el motor ya escrito, la cascada se negaba a elegirlo. El fondo del error es que **no es una propiedad de la caja sino del APK**: ahora se mira por reflexión, una vez |
| ~~58~~ | ~~**El idioma de las pistas de audio de libVLC sale vacío**~~ | — | `MediaPlayer.audioTracks` incluye la pseudo-pista «Disable» de libVLC, que la barra pintaba como un idioma llamado «DI». Filtrada. Pero leyendo el idioma del medio en su lugar, viene `null`: la barra ya no miente, pero tampoco dice. Afecta poco hoy —el único canal que usa libVLC tiene una sola pista— y afectaría de verdad a un MPEG-2 con doble audio | **Arreglado el 10-oct.** El idioma está en el manifiesto —medido con `ffprobe`: `spa`, `eng`, `spa`— pero libVLC no lo propaga en HLS; sí da la descripción, y Flussonic la escribe con el código delante (`NAME="spa a1"`). Cuando el declarado viene vacío se rescata del texto con una regla estrecha —palabra suelta de dos o tres letras, sin dígitos—, con nueve comprobaciones. Se añade `vlc.pistasCrudas` para poder ver qué devuelve libVLC de verdad. **Y el volcado lo desmintió, el 10-oct: el diagnóstico de este pendiente era otro.** libVLC sí da el idioma de la pista buena (`esl`); lo que daba además era **una pista fantasma** sin idioma, sin descripción y sin canales, y de ahí venían el `cuantas=2` de un canal con un audio y la opción vacía de la barra. Se descartan las pistas sin idioma y sin descripción. El rescate del texto se queda para los masters con `EXT-X-MEDIA` separados, donde el idioma viaja en `NAME`. **Cerrado el 10-oct, validado en pantalla:** Estrellas SD enseña una sola pista con su idioma |
| 59 | ~~**La barra de canal no se escondía con libVLC**~~ | — | **cerrado 09-oct.** `Buffering` no significa lo mismo en los dos motores: libVLC lo emite de continuo con el porcentaje de llenado —26 veces al 100 % en una sesión normal— y se traducía a `onReady()`, que deja el estado en «Listo» y no en «En vivo». La barra se mostraba una y otra vez y nunca se escondía. Ahora se mira el **flanco**: bajar de 100 con imagen es un rebuffer, volver a 100 es salir de él |
| 60 | ~~**Una sesión con libVLC no dejaba rastro de diagnóstico**~~ | — | **cerrado 09-oct.** El latido vivía solo en `Media3LiveEngine`: cero líneas en el volcado de una sesión entera con el respaldo. Todo el método de este proyecto se apoya en ese rastro. `VlcLiveEngine` lo escribe ahora con las columnas que libVLC sabe de verdad, y **sin las que no** |
| 61 | ~~**El overlay enseñaba un «⚠AAC» falso y ceros que parecían medidas**~~ | — | **cerrado 09-oct.** El motor publicaba como `audioMime` el códec **declarado** en el `config.json`, así que el aviso de audio sin normalizar de §2 se comparaba consigo mismo: encendía la alarma en un canal correcto y, peor, habría callado en uno que declarase AAC y entregase MP2. Ahora es `null`. Y el overlay deja de escribir `buf 0.0s dir 0.0s` cuando no hay medida: un hueco se lee «no lo sé», un cero se lee «se acabó el buffer» |
| 62 | ~~**Con libVLC, el ajuste de imagen salía mal en los cinco modos**~~ | — | **cerrado 09-oct.** Los dos motores tienen geometrías distintas y tratarlas como una produjo el fallo: MediaCodec **estira** hasta llenar la caja que se le da —en eso se apoya el dimensionado de superficie de M5— y libVLC **mantiene la proporción dentro** de su ventana, así que agrandar la superficie solo centraba la imagen con franjas más anchas. Ahora el modo se le dice al motor: libVLC usa su propio modelo de proporción y escala, y con libVLC la superficie no se redimensiona. Medido: «Llenar» pide escala 3.00, lo exacto para que un 640×480 cubra 1920×1080.

**Y debajo había un segundo fallo que el primero tapaba.** Con la proporción en «la del origen», libVLC renderizaba `estrellassd` a 1695×1080 — proporción **1.569**, que es 4:3 × 1.176: el SAR **aplicado dos veces**, sobre un tamaño que ya lo traía incorporado. La imagen salía estirada a lo ancho y en «Zoom» se salía de la pantalla, que es lo que Edgar vio. Se le pasa ahora la proporción explícita —la que el propio libVLC declara ya corregida— y se reaplica el modo al conocerse el tamaño, porque el primero llega antes. Medido después: franjas de **240 px** a cada lado y **1440 px** de imagen, que es el 4:3 correcto al píxel |
| ~~63~~ | ~~**`vpuDeinterlaces` es por modelo y esta caja lo necesita por códec**~~ | — | es la llave del nivel 2 de `LIVETV_ENGINE.md` §4.4, y en la Skyworth no se puede girar. Su VPU **sí** desentrelaza H.264 1080i —M5: «sin peine en `discoveryhd` ni `hbohd`»— y **no** entrega bien el MPEG-2 480i (55). Con un solo booleano por modelo, ponerlo en `false` para que `estrellassd` llegue a libVLC arrastra a software los tres canales 1080i que hoy funcionan por hardware. O sea: el respaldo de M6 sigue sin poder dispararse solo en la única caja que tenemos, y ahora se sabe por qué. **La salida más limpia no es afinar este campo sino reusar el veto de decoders** (56), que ya existe y ya desemboca en la rama «sin decoder de plataforma para $mime, libVLC», probada: haría falta poder publicarlo por modelo desde el `config.json`, que es un campo nuevo en `deviceOverrides` y Modo A en la app. Lo de hoy —el barrido corregible (53)— era condición necesaria y no suficiente. **Y ya no es sospecha: medido el 09-oct**, `discoveryhd` por software cuesta el **63 % de CPU** (mediana de 41 latidos) contra el 14 % de `estrellassd`, para llegar a lo que por hardware ya sale perfecto. Girar el booleano por modelo arrastraría a software un canal que no lo necesita, con el margen justo para que cualquier otra carga del sistema se vea en pantalla. **Implementada la salida el 09-oct: `blockedDecoders` en los `deviceOverrides`**, que nombra el decoder que entrega mal en vez de hablar de desentrelazado. La Skyworth ya está en `nuestro.json` con su porqué y el site de pruebas lo sirve. **Cerrado el 09-oct, validado en pantalla con todo de fábrica:** `caps.decodersVetados cuantos=1`, `estrellassd` a libVLC con 0 cortes en su sesión y `discoveryhd` por hardware. Las dos mitades, incluida la que importaba: el veto no casa de más |
| ~~64~~ | ~~**`enginesAllowed` no se respeta contra `media3`**~~ | — | el documento dice que un motor fuera de esa lista «no se elige nunca, ni como último recurso», y para libVLC es verdad —tres comprobaciones de `permitido()`—. Para Media3 no hay ninguna: con `enginesAllowed: ["libvlc"]` y decoder de hardware disponible, `videoPath` sale `HW` y el motor derivado es Media3, justo el que el canal prohíbe. Las pruebas solo cubren el sentido contrario (`["media3"]`). Descubierto el 09-oct buscando una palanca por canal para el 63; no hay ninguna hasta que esto se arregle | **Cerrado el 10-oct.** Las tres ramas que implican Media3 —hardware, software de plataforma y «códec sin declarar»— pasan a exigir que el canal lo permita; con Media3 prohibido el plan cae al respaldo y, si no hay, el canal sale no reproducible en vez de usar el motor vetado. Cinco comprobaciones nuevas, incluida la del menú: forzar software a mano tampoco salta por encima. Lo que más conviene recordar es que **la prueba que debía cubrirlo existía y solo cubría el sentido que funcionaba** |
| ~~65~~ | ~~**Con libVLC, `plan.codecDiscrepante` saltaba en toda sesión**~~ | — | **cerrado 09-oct.** `VlcLiveEngine` publicaba como `videoMime` el códec **declarado** en el `config.json`, así que la comprobación se comparaba consigo misma: `declarado=video/mpeg2 observado=mpeg2video` es el mismo códec escrito de dos maneras, y el aviso salía en las dos sesiones del volcado de hoy. Peor que el ruido: así **no podría detectar una discrepancia de verdad**, que es para lo que existe. Es el mismo fallo que el 61 con el audio, dos líneas más arriba en el mismo bloque, y la misma solución: `null`. La ausencia de `plan.codecObservado` en una sesión de libVLC ya significa «no se llegó a comprobar», que es la verdad |
| ~~66~~ | ~~**`vpuDeinterlaces` del `config.json` no llegaba al motor**~~ | — | **cerrado 09-oct.** El campo se documentaba, se interpretaba y se fundía por especificidad, y la conversión de `DeviceOverride` a `ServerDeviceSettings` en `LiveTvCatalog` **no lo copiaba**: se quedaba ahí. O sea que el nivel 2 de la cascada no se habría disparado en producción ni publicándolo, y la validación de hoy con el interruptor de simulación habría dado una falsa sensación de que el camino estaba completo. Es el tercero de la misma familia en dos días —el 56 con el veto, el 57 con `hasVlcBackend`—: código escrito, probado por unidades y sin conectar al final del cable |
| ~~67~~ | ~~**El `targetOffsetMs` de fábrica no se usa: el suelo lo pisa y sobra ventana**~~ | — | medido el 09-oct en los cuatro canales: las ventanas DVR son de 29,9 a 38,6 s —14 segmentos—, el contrato permite sentarse a media ventana (15-19 s) y el cliente se asienta a **11,3 s**, porque pide 8 000 y el suelo de `3 × TARGETDURATION` lo sube a 10,2 (`offset.noSeAplica`, seis veces en el volcado). El techo del buffer **es** esa distancia: p50 6,2 s, máximo 10,0 s. Con el origen publicando ~1 s tarde en el 22 % de los segmentos, ese margen se agota 15 veces por hora. Subir el offset a ~14 000 es la mitad barata del arreglo y no necesita headend; la otra mitad es más `segment_count`. **Cerrado el 09-oct, y no en la app.** Medido con 14 000: buffer p5 de 4,3 a 9,1 s y **cero cortes en 38 minutos**, con ratio y rebuffers/hora cumpliendo §10 por primera vez en una sesión larga. El valor de fábrica de la app no se toca —sería Modo A y es el sitio equivocado—: lo calcula el generador del 45 % de la ventana que mide, con suelo 8000 y techo 14000 (`servidor-canales@e29f980`), así que cada canal de cada site recibe lo que su segmentación permite |
| ~~68~~ | ~~**Cada cambio de canal registra un `ERROR_CODE_FAILED_RUNTIME_CHECK`**~~ | — | en el volcado del 09-oct, cinco de cinco zapeos: `engine.prepare`, y **1 ms después** `engine.release canal=<el nuevo> habiaPlayer=false`, `engine.play`, el error de ExoPlayer con `videoDecoder=null bufferedMs=0`, `player.idle` y una preparación nueva que sí funciona. Se recupera solo, así que no se ve en pantalla, pero explica parte del primer fotograma: 1 725-1 942 ms frente a los 1,1-1,5 s de §6. **No es de hoy** —el volcado del 08-oct trae el mismo error en su única sesión—. **Diagnosticado y arreglado el 10-oct, y la mitad de su descripción era falsa.** El `engine.release` de 1 ms después no es un fallo: lo hace `prepare()` para construir sobre limpio. El fallo es que `exo.prepare()` corre **antes** de que exista la superficie, que la crea la vista unos milisegundos después; aceptarla en caliente reconfigura el renderer con la reproducción ya pedida y dispara `Assertions.checkState`. Medido con 17 sintonías: con el arreglo, 0 errores de 6 frente a 9 de 11. **Y no cuesta lo que dije:** el primer fotograma es el mismo (1 794 contra 1 784 ms de mediana); los «400-800 ms» los deduje de una sola muestra que era un reinicio en caliente. **Cerrado el 10-oct y activo de fábrica**, validado en pantalla por Edgar: «sí vi bien todo sin problemas». El interruptor se queda para reproducir el comportamiento anterior |
| ~~69~~ | ~~**Un canal podía quedarse muerto para siempre sin que nada lo rescatara**~~ | — | **el peor fallo encontrado hasta hoy, y cerrado el 09-oct.** 98 minutos de `estado=almacenando reproduciendo=false` con 35,8 s de buffer sin consumir y la posición cayendo hacia atrás, tras un `Source error`. El vigilante no lo vio porque medía el progreso como «la posición cambió», y en ese estado cambiaba sola: la ventana corría y el reproductor no. La vuelta al borde tampoco, porque exige `STATE_READY`. Arreglado en `RelojDeAvance`: progresar es **reproducir y moverse**. Lo que más conviene recordar es que el primer arreglo —«que la posición suba»— era falso y lo tumbó su propia prueba con los números de una sesión sana, donde la posición no sube nunca. **Cerrado y validado en pantalla el 09-oct con los dos cortes provocados por Edgar:** reiniciando el canal en Flussonic se curó en 16 s por el escalón 2, y desconectando el cable en 26 s por el escalón 3 —11 s desde que la red volvió—, las dos veces con el aviso en pantalla |
| ~~70~~ | ~~**La vuelta al borde no vuelve al borde, y cada intento cuesta un corte**~~ | — | medido en 9,3 h de `mileniosd` la noche del 10-oct: **547 saltos** y después de cada uno el offset seguía en 37 s con una ventana de 33,6. **549 de los 579 cortes de la noche ocurren en el segundo siguiente a un salto**, o sea que el 95 % los provoca el mecanismo de recuperación, no la red. Mediana de 142 ms —invisible, Edgar vio la imagen perfecta— pero el ratio sube a 1,12 % cuando una sesión limpia da 0 %. Descartado el origen: publica a 0,997× y 1,002× tiempo real, y el reloj de la caja va a 2 s. **Cerrado el 10-oct y activo de fábrica**, medido con 203 minutos de día: **de 547 saltos en 9,3 h a 9 en 3,4 h**, y `offset.trasElSalto` —la columna que se añadió para poder juzgarlo— deja el directo en 17-19 s en seis de los nueve. Saltar a `ventana − offset objetivo` en vez de a la «posición por defecto» del reproductor es lo que lo arregla. Los tres saltos que no sirven **van hacia atrás**, y de eso se ocupa el 73. La hipótesis de la posición —«los reinicios dejan el reproductor en la cola»— era falsa; ver el 71 |
| ~~71~~ | ~~**Un reinicio puede dejar el reproductor en la cola de la ventana**~~ | — | **cerrado el 10-oct, y el enunciado era falso.** Se dedujo de los offsets de 64 y 66 s de dos de las cuatro reconstrucciones de la noche, sin ver la posición. Con la columna `posAntesMs` puesta, el mismo síntoma —offsets de 72 y 80 s— aparece **con la posición a 26,8 s de una ventana de 33,3**: el reproductor no estaba en la cola de nada. `currentLiveOffset` es reloj de pared contra `EXT-X-PROGRAM-DATE-TIME`, así que con el playlist congelado ese número crece sin límite aunque el reproductor esté pegado al final de todos los datos que existen. **No hay defecto en `prepare()`**; lo que había era un instrumento mal leído |
| 72 | **El disparador de la vuelta al borde está a 300 ms de dispararse en un canal sano** | **A** | medido el 10-oct: el disparador compara el offset real con **la mitad de la ventana** —16,8 s de 33,6— y exige 3 s de exceso, mientras el directo sano de `mileniosd` se asienta en **19,0-19,5 s**. Son 300-800 ms de margen: cualquier canal con la ventana un poco más corta resincronizaría cada 30 segundos para siempre sin que nada esté mal. Y el offset al que se asienta no lo elegimos: medido contra 8 000 → 10 200 (09-oct) y 14 000 → 16 674 (10-oct), el modelo que encaja es `real ≈ max(pedido, 3 × TARGETDURATION) + un segmento`, porque la reproducción empieza en un límite de segmento. El arreglo del 73 lo neutraliza de refilón —ese salto iría hacia atrás— pero el disparador sigue mal elegido: debería medirse contra el offset **alcanzable**, no contra una fracción de la ventana. **Sin arreglar**, y es el candidato siguiente |
| 73 | **Tres de cada nueve vueltas al borde saltan hacia atrás** | **A** | la mitad que queda del 70. Medido el 10-oct, el primer salto de cada ráfaga va de 26 843 a 19 267 —**7,6 s hacia atrás**, alejándose del directo— porque el offset está inflado por una lista congelada y la posición ya está a 6,4 s del final de la ventana. No hay a dónde saltar y el salto cuesta un corte: 3 de los 19 de la sesión. **Arreglo del 10-oct:** no saltar si el destino calculado no adelanta al menos un segmento, registrar `offset.saltoNoAdelanta` y volver a evaluar al terminar el enfriamiento —que es exactamente cuando llega el salto que sí sirve, así que la recuperación no se retrasa—. Detrás de «Volver al borde solo si el salto adelanta», apagado de fábrica. **Pendiente de medir en pantalla** |
| ~~26~~ | ~~**La Skyworth no tiene decoder por software para MPEG-2**~~ | — | **corregido el 05-oct con el inventario que mandó la propia caja:** sí tiene `OMX.google.h264.decoder` para `video/avc`, declarado software por `isHardwareAccelerated()` —la API, no una heurística de nombres—. Lo que **no** tiene es software para `video/mpeg2`: ahí el único es `OMX.amlogic.mpeg2.decoder.awesome2`, por hardware. O sea: el paso 2 de la cascada **sí es alcanzable para H.264** y no para MPEG-2. Queda por ver si ese decoder funciona de verdad: declarado no es funcionando | **Cerrado 09-oct con M6:** el camino que faltaba es libVLC, y está probado en pantalla. No era «falta un decoder de plataforma» sino «el de hardware decodifica mal», que es peor porque la cascada no lo distingue sola |
| ~~25~~ | ~~**`engine.attach` se llama ~2 veces por segundo mientras reproduce**~~ | — | el `update` del `AndroidView` corre en cada recomposición y las estadísticas recomponen cada 500 ms. 206 llamadas en 115 s. Rota el buffer de logcat en menos de un minuto. Solo `AspectoDeVideo.aplicar` necesita las estadísticas; `attach` debería correr cuando cambia la vista. **cerrado 06-oct, validado en pantalla por Edgar y activo de fábrica.** De 115 llamadas en 60 s a **0**, y el log de la sección de 259 líneas a 28. Tres cambios de canal dan tres `attach` y tres `frames.first` a 1.5–1.7 s. El interruptor se queda para reproducir el comportamiento anterior |
| ~~24~~ | ~~**El decodificador AVC de Amlogic ignora el pulldown de H.264**~~ | — | saca 2 campos por imagen = 48 fps en contenido de cine, −17.5 % del ideal. El de MPEG-2 lo respeta (−0.2 %). Afecta igual a libVLC (47.99), así que **no se arregla en el cliente**. Decisión de Edgar: aceptarlo o tratar el canal en el headend **Cerrado 08-oct: se acepta, y el tunneled es la mitigación.** Decisión de Edgar tras 24 min de `hbohd` en pantalla: «el stream se ve limpio mientras está de corrido». No se arregla en el cliente —libVLC falla igual, 47.99— y el tunneled, que es de fábrica, se adoptó precisamente porque elimina esos microcortes. **No es medible en la configuración que se publica** y eso no tiene arreglo de método: hace falta ver la entrega de fotogramas, y con tunneled no la ven ni el contador propio ni `SurfaceFlinger --latency` (§6); sin tunneled el canal se cae cada 4 s. Volverá a importar el día que una caja obligue a apagar el tunneled |

---

## Entradas

### El salto calculado sirve, y el offset que lo dispara miente — 2026-10-10

**Síntoma:** la noche anterior, 547 vueltas al borde en 9,3 h que no devolvían
al directo y 549 de los 579 cortes provocados por ellas (pendiente 70).

**Prueba:** 203 minutos de `mileniosd` de día, con `resyncCalculado=ON`,
volcado `livetv-20261010-142635.txt`. Es la primera medición del arreglo.

**Resultado: de 547 saltos en 9,3 h a 9 en 3,4 h**, y la columna
`offset.trasElSalto` —añadida para esto— dice cuáles sirven:

| salto | pos antes | ventana | destino | ganancia | offset después |
|---|---|---|---|---|---|
| 1, 4, 7 | 26 843 | 33 267 | 19 267 | **−7 576** | 80 s — falló |
| 2, 5, 8 | −41 844 | 33 600 | 19 600 | +61 444 | 19,3 s ✓ |
| 3, 6, 9 | 326 | 33 600 | 19 600 | +19 274 | 18,5 s ✓ |

Seis de nueve dejan el directo donde debe. **Cerrado el 70 y el valor de
fábrica invertido**, que es lo único que cambia este commit en el motor.

**Los tres que fallan saltan hacia atrás**, y eso da la causa que faltaba.
`currentLiveOffset` es reloj de pared contra `EXT-X-PROGRAM-DATE-TIME`: cuando
el playlist deja de crecer, ese número sube sin límite —39 → 53 → 72 → 81 s—
**aunque el reproductor ya esté pegado al final de todo lo que existe**. En el
salto 1 la posición está a 6,4 s del final de una ventana de 33,3 s: no hay a
dónde ir y el salto cuesta un corte.

#### Lo que esto le hace al pendiente 71

Lo cierra, y desmiente su enunciado. «Un reinicio deja el reproductor en la
cola de la ventana» era una inferencia de los offsets de 64 y 66 s de la noche
anterior, y hoy se ve el mismo número —72 y 80 s— **con la posición a 26,8 s de
una ventana de 33,3**. No había nada en la cola: era este offset inflado por
una lista congelada. `prepare()` no tiene ningún defecto que arreglar.

#### Las tres ráfagas de cortes son de aguas arriba de la caja

Los 19 cortes están todos dentro de tres ráfagas de dos minutos —11:57, 12:12
y 12:29—, con **169 de los 203 minutos sin un solo corte, incluidos los últimos
115 seguidos**. La firma de cada ráfaga es inconfundible:

```
12:12:01  bufferS=10.8  directoS=18.9
12:12:03  bufferS=8.8   directoS=18.9
12:12:05  bufferS=6.8   directoS=18.9
12:12:07  bufferS=4.8   directoS=18.9
12:12:09  bufferS=2.9   → corte
```

En marcha normal el buffer oscila entre 11 y 15 s; aquí **cae 2 s por cada 2 s
de reloj** hasta vaciarse, y ~50 s después Media3 lanza
`PlaylistStuckException`. O sea que la fuente dejó de entregar. `mileniosd`
llega por multicast de un proveedor de fibra, y Edgar ya sospechaba de su red
tras la noche. Desde el cliente no hay nada que hacer; lo que sí se puede es
**distinguir «Flussonic dejó de publicar» de «la caja perdió la red»**, y hoy
no se distingue: de ahí `herramientas/vigilar-playlist.py`.

#### Ratio, CPU y memoria

Ratio de rebuffer **1,00 %** en la sesión completa y **0,03 %** en el tramo
limpio de 115 minutos. CPU mediana 7 %, p95 8 %. **RAM libre 758 → 845 MB**:
sube, así que la pregunta de la fuga queda contestada por segunda vez y ahora
de día. Primer fotograma 2 550 ms.

#### Y el dato que destapa un pendiente nuevo

`offset.noSeAplica pedidoMs=14000 realMs=16674`. El canal tiene
`TARGETDURATION 3` y 12 segmentos de 2,8 s, así que el suelo de
`3 × TARGETDURATION` son **9 s, no 16,7**: no es el suelo lo que manda aquí.
Medido contra las dos observaciones anteriores —8 000 → 10 200 el 09-oct,
14 000 → 16 674 hoy—, el modelo que encaja es
`real ≈ max(pedido, 3 × TARGETDURATION) + un segmento`, porque la
reproducción solo puede empezar en un límite de segmento. **La causa que
escribe hoy el motor en esa línea es una atribución, no una medición**, y por
eso se corrige.

Lo grave es la consecuencia. El disparador de la vuelta al borde compara con
**la mitad de la ventana**: 16,8 s. El directo sano se asienta en **19,0-19,5
s**, y el margen mínimo son 3 s. O sea que un canal **perfectamente sano** está
a 300-800 ms de resincronizar cada 30 segundos para siempre. No es una trampa
latente: es el margen en el que llevamos midiendo. Va como pendiente 72.

### El respaldo ya se elige solo, y el 1080i por software cuesta 4,5× — 2026-10-09

Primera vez que libVLC se elige **sin ningún interruptor de motor**: el canal
llega marcado entrelazado desde el `config.json` del site y la cascada decide.
Cierra la cadena del pendiente 53 y la salvedad con la que se cerró M6.

Edgar corrigió el barrido en el panel del servidor de canales —el control nuevo
del 09-oct— y el site publica ahora:

| Canal | `interlaced` | Lo que es |
|---|---|---|
| `estrellassd` | **true** (corregido) | Flussonic decía `false` sobre un 480i |
| `estrellashd` | **false** (corregido) | cine 3:2; Flussonic decía `true` |
| `hbohd` | **false** (corregido) | igual |
| `discoveryhd` | true | el único entrelazado de verdad |

En la caja, de fábrica salvo «Simular VPU que no desentrelaza». «Forzar
desentrelazado» **apagado**, que es lo que hace significativa la prueba:

```
plan.construido canal=estrellassd motor=libvlc video=SW_VLC deint=SOFTWARE
  motivo=el VPU de este modelo no desentrelaza, libVLC por software · deint por software
degradacion=VPU sin deint (simulado)
```

#### El precio del nivel 2, medido en los dos tamaños

| Canal | Qué es | Latidos | CPU (mín/mediana/máx) | Rebuffers | 1.er fotograma |
|---|---|---|---|---|---|
| `estrellassd` | 480i MPEG-2 | 18 | 13 / **14** / 20 % | 0 | 868 ms |
| `discoveryhd` | 1080i H.264 | 41 | 58 / **63** / 71 % | 0 | 326 ms |

**4,5 veces más CPU por el mismo trabajo en HD**, y el juicio de Edgar lo
acompaña: `estrellassd` «fluida», `discoveryhd` «se ve bien pero ligeramente
forzada». Ninguno perdió el directo —la posición avanza 2,0 s por latido de 2 s
en los dos— ni rebufferizó, y la RAM libre se mantuvo plana.

#### Lo que esto decide del pendiente 63

Que el camino está probado en HD **y que no se debe usar ahí**. `discoveryhd`
por hardware va perfecto —M5: sin peine, 29,9 fps— y por software cuesta el
63 % de la caja para llegar a lo mismo, con el margen justo para que cualquier
otra cosa que pase en el sistema se note en pantalla.

Así que `vpuDeinterlaces: false` por modelo queda descartado para esta caja no
por sospecha sino por medición: arrastraría a software un canal que no lo
necesita. **La granularidad tiene que ser por códec**, y la vía limpia sigue
siendo publicar el veto de decoders por modelo (63): vetar
`OMX.amlogic.mpeg2.decoder.awesome2` deja el MPEG-2 sin decoder de plataforma y
desemboca en la rama que ya existe y está probada, sin tocar el H.264.

#### Y un aviso que salía siempre

En el mismo volcado, `plan.codecDiscrepante` en las dos sesiones:
`declarado=video/mpeg2 observado=mpeg2video`, que es el mismo códec escrito de
dos maneras. `VlcLiveEngine` publicaba como `videoMime` el códec **declarado**
en el `config.json`, o sea comparaba el dato consigo mismo — el fallo del 61
con el audio, dos líneas más arriba en el mismo bloque de código. Corregido
(65). Lo que importa no es el ruido: así nunca habría detectado una
discrepancia real.

### El veto funciona, y los cortes de los 1080i tienen fuente medida — 2026-10-09

Dos cosas en el mismo volcado: 275 minutos, ocho sesiones, la más larga de 2,5 h
—la más larga del proyecto—.

#### El veto por modelo, validado

Con **todo de fábrica**, sin un solo interruptor:

```
caps.decodersVetados cuantos=1 nombres=OMX.amlogic.mpeg2.decoder.awesome2
plan.construido canal=estrellassd motor=libvlc video=SW_VLC deint=SOFTWARE
plan.construido canal=discoveryhd motor=media3 video=HW
```

Las dos mitades: `estrellassd` se va a libVLC sola —0 cortes, 16 % de CPU— y
`discoveryhd` sigue por hardware. El veto **no casa de más**, que era la mitad
que había que comprobar. Cierra el pendiente 63.

#### Y los cortes que Edgar juzgó no viables

| Canal | Motor | Minutos | Cortes | Por hora | Mediana | Máx | Ratio |
|---|---|---|---|---|---|---|---|
| `discoveryhd` | media3 | 106,7 | 26 | 14,6 | 194 ms | 1434 ms | 0,14 % |
| `discoveryhd` | media3 | 149,7 | 39 | 15,6 | 289 ms | 1656 ms | 0,17 % |
| `hbohd` | media3 | 15,2 | 6 | 23,6 | 416 ms | 1623 ms | 0,37 % |
| `estrellassd` | libvlc | 2,1 | 0 | — | — | — | 0 % |

**No es una regresión de nada de hoy**, y conviene decirlo con el número al
lado: la misma caja y el mismo canal dieron **24,9 cortes/hora** el 08-oct, con
ratio 0,24 %. Lo que cambió es la duración de la sesión —de 24 minutos a 150—,
así que el recuento absoluto pasó de 10 a 39. El ratio bajó.

Pero cinco de esos cortes pasan del segundo, y eso sí se ve. El juicio de «no
viable» es sobre el recuento, y el recuento es real.

#### La forma del fallo, medida por los dos lados

**En la caja**, 7 681 latidos de la sesión larga:

| | buffer |
|---|---|
| p1 | 3,6 s |
| p50 | **6,2 s** |
| p95 | 8,1 s |
| máximo | **10,0 s** |

Y la distancia al directo, mediana **11,3 s**. El techo del buffer **es la
distancia al directo**, no nuestro `LoadControl`: el cliente carga hasta el
borde de emisión y ahí se queda. Justo antes de cada corte el buffer vale 4,0 s
de mediana, frente a 6,2 de la sesión entera, o sea que **se vacía
progresivamente**: es falta de datos, no un tropiezo del decodificador.

**En el origen**, medido desde esta máquina sin tocar la caja: pidiendo el media
playlist cada segundo durante 5,5 minutos, 156 segmentos nuevos.

```
segmentos que llegaron >0,5 s tarde: 35 de 156   (22 %)
el peor: +1,27 s   (uno de 2,04 s llegó 3,01 s después del anterior)
```

**El origen publica con ~1 s de retraso en uno de cada cinco segmentos.** Un
cliente con 6 s de margen absorbe uno; tres seguidos se comen la mitad del
colchón, y el p1 de 3,6 s dice que eso pasa.

#### Por qué el margen es de 6 s y no de 15

Porque sentarse más atrás del directo es lo único que lo agranda, y hoy no se
está usando el sitio que hay:

| | medido hoy |
|---|---|
| Ventana DVR de `discoveryhd` | **34,7 s** (14 segmentos) |
| Mitad de ventana, el techo del contrato | 17,3 s |
| `targetOffsetMs` que publica el site | 8 000 |
| Suelo que impone la falta de `EXT-X-SERVER-CONTROL` | 3 × TARGETDURATION ≈ 10,2 s |
| Dónde se asienta de verdad | **11,3 s** |

O sea que el 8 000 del `config.json` **es una ficción**: el suelo lo sube a 10,2
y ahí se queda, a menos de dos tercios de lo que la ventana permite. Subirlo a
14 000 —que cabe en la mitad de ventana de los cuatro canales medidos— casi
dobla el colchón sin tocar el headend y sin que el abonado note nada: ya va
~11 s por detrás del aire.

La otra mitad es del headend y es la de siempre: más segmentos en la ventana
(`segment_count`) da sitio para sentarse más atrás todavía.

### 98 minutos de pantalla muerta, y el rescate que nunca empezó — 2026-10-09

Edgar: *«solo visualicé un canal durante un largo tiempo, parece que se quedó
detenido»*. Se quedó detenido, y el log dice exactamente eso durante **98
minutos seguidos**:

```
19:19:49  estado=almacenando reproduciendo=false posS=-37.9    sinAvanzarMs=1637 bufferS=35.8 directoS=73.6
20:57:27  estado=almacenando reproduciendo=false posS=-5895.1  sinAvanzarMs=1312 bufferS=35.8 directoS=5932.0
```

2 927 latidos iguales. El reproductor parado en un punto que la ventana DVR ya
había dejado atrás —de ahí la posición negativa—, **con 35,8 s de datos delante
sin consumir**, y a 99 minutos del directo. Ni un error, ni un reintento, ni un
escalón del vigilante. La escalera de rescate de tres escalones, escrita,
probada y activa de fábrica, **no llegó a empezar**.

#### Por qué no empezó

`atascado` se definía como `msSinceProgress > 10 s`, y el progreso se medía así:

```kotlin
if (pos != lastPositionMs) { lastPositionMs = pos; lastProgressAtMs = ahora }
```

**Cualquier cambio de la posición contaba como avance.** Y en ese estado la
posición cambiaba sin parar: caía dos segundos por cada dos segundos de reloj,
porque la ventana corría y el reproductor no. `sinAvanzarMs` nunca pasó de
2 636 ms. Visto desde el vigilante, ese canal estaba progresando.

El otro mecanismo que podría haberlo salvado tampoco podía: la vuelta al borde
exige `STATE_READY`, y el reproductor estaba en `STATE_BUFFERING` todo el
tiempo. Disparó **una vez** a los 42 s de offset —cuando aún estaba listo— y
nunca más.

#### El arreglo, y el que estuvo a punto de salir mal

Lo primero que escribí fue «progresar es que la posición **suba**». La prueba
de la clase nueva lo tumbó antes de salir del repositorio, con los números de
la propia sesión sana: en un directo la posición se mide desde el principio de
la ventana y la ventana corre al mismo ritmo que la reproducción, así que **la
posición no sube: se queda quieta oscilando** —banda de 19,1 a 23,6 s durante
minutos—. Ese arreglo habría despertado al vigilante cada veinte segundos en
todos los canales.

La definición que sí distingue los dos fallos conocidos es **progresar es estar
reproduciendo y que la posición cambie**:

| | reproduciendo | posición | ¿atasco? |
|---|---|---|---|
| Directo sano | sí | oscila | no |
| Rebuffer corriente (194 ms de mediana) | no, un instante | cualquiera | no, no llega a 10 s |
| **El fallo del 09-oct** | **no, para siempre** | cambia | **sí** |
| **El fallo del 30-sep** | sí | clavada | **sí** |

Cada mitad de la frase viene de uno de los dos, y ninguna sola los ve los dos.
Está en `RelojDeAvance`, con siete comprobaciones que llevan los números
medidos de las dos sesiones.

#### Y lo que la misma sesión dice del pendiente 67: el offset funcionó

Esa sesión era la prueba de subir `targetOffsetMs` a 14 000 desde el panel, y
en sus 16 minutos sanos salió exactamente lo previsto:

| | 8 000 (antes) | 14 000 |
|---|---|---|
| Buffer p5 | 4,3 s | **9,1 s** |
| Buffer mediana | 6,2 s | **11,1 s** |
| Distancia al directo | 11,3 s | 16,0 s |
| Cortes | ~15/hora | **0 en 16 min** |

El colchón se dobla y los cortes desaparecen. Los 12 rebuffers de la sesión
están **todos** dentro de una ventana de 63 s que abre un
`ERROR_CODE_IO_UNSPECIFIED ... Source error`: no son el goteo de antes, es el
origen cayéndose. Y 16,0 s de offset quedan 3 s por debajo del punto en que la
vuelta al borde se dispararía (media ventana + 3 s = 19,0 s), así que el valor
no está pegado a su límite.

Queda medir una sesión larga **sin** caída de origen antes de tocar el valor de
fábrica, que es Modo A.

### El rescate, validado en pantalla con dos cortes provocados — 2026-10-09

Edgar provocó los dos fallos a propósito y **los dos se curaron solos**, con el
cartel de «sin señal, reconectando» en pantalla mientras duraba. Es la
validación del pendiente 69 y la primera vez que la escalera del vigilante se
ve funcionar entera.

#### A1 — reiniciar el canal en Flussonic

```
21:14:44  player.error ERROR_CODE_IO_UNSPECIFIED Source error  bufferedMs=0
21:14:44  offset.superaLaVentana ventanaMs=6506     ← Flussonic reconstruyendo su playlist
21:14:53  offset.superaLaVentana ventanaMs=13046
21:14:54  vigilante.atascado estado=almacenando posS=28.2 bufferS=0.0 directoS=-9.4 sinAvanzarMs=10958
21:14:54  vigilante.saltaAlBorde intento=1
21:15:05  offset.superaLaVentana ventanaMs=26357
21:15:06  vigilante.rehacePreparacion intento=2
21:15:08  player.playing liveOffsetMs=13048
21:15:10  vigilante.recuperado segundosAtascado=16 escalonAlcanzado=2
```

**16 s de atasco, curado en el escalón 2.** Y el límite no es nuestro: las
líneas de `offset.superaLaVentana` miden a Flussonic rehaciendo su ventana de
6,5 a 26,4 s, o sea **21 s para volver a tener DVR**. El cliente no puede
recuperarse antes que el origen, que es justo lo que advierte la nota de §10.

#### A2 — cable de red desconectado hasta que paró

```
21:16:11  load.retry attempt=1 delayMs=1000 cause=HttpDataSourceException
21:16:18  load.retry attempt=4 delayMs=8000
21:16:28  vigilante.atascado estado=almacenando posS=29.8 bufferS=2.8 directoS=25.2 sinAvanzarMs=10905
21:16:28  vigilante.saltaAlBorde intento=1
21:16:40  vigilante.rehacePreparacion intento=2
21:16:43  load.retry attempt=5 cause=InvalidResponseCodeException   ← la red ya contesta
21:16:52  vigilante.reconstruye intento=3
21:16:54  frames.first msDesdePrepare=1575
21:16:54  vigilante.recuperado segundosAtascado=26 escalonAlcanzado=3
```

**26 s de atasco, y hubo que llegar al escalón 3** —reconstruir el reproductor
desde dentro—, que nunca se había visto hacer falta de verdad. Los dos primeros
escalones fracasaron porque la red seguía caída; el tercero cayó justo después
de volver.

El cambio de excepción marca el instante del regreso: `HttpDataSourceException`
es «no hay red» y `InvalidResponseCodeException` es «hay red y el servidor
contesta». **De ahí a la imagen, 11 s**, que sí entra en el «aceptable» de §10.

#### Lo que se aprende de los dos

- **La política de reintentos funciona y es visible**: 1, 2, 4, 8 s de espera
  creciente, sin cerrar el reproductor, que es lo que exige §6.
- **Los tres escalones sirven y están bien espaciados.** No hubo saltos
  encadenados ni rescates en falso.
- **Detectar cuesta 10 s por diseño** (`WATCHDOG_STALL_MS`) y cada escalón otros
  12. Para un corte corto no importa —de eso se encargan los reintentos de
  ExoPlayer— pero significa que el vigilante nunca va a recuperar en menos de
  diez segundos. Con el objetivo «aceptable» de §10 en 15 s, el margen es de
  cinco: conviene no tocar esos dos números sin volver a medir esto.
- **El aviso en pantalla llegó**, que es la otra mitad de §6: el abonado vio
  «sin señal, reconectando» y no una pantalla negra muda.

#### Y una confirmación del pendiente 36, de paso

```
tracks.crudo grupos=7 detalle=video[?/avc*] | audio[es/mp4a-latm] | audio[es/mp4a-latm*] | audio[en/mp4a-latm] | …
audio.pistas cuantas=3 idiomas=[spa, spa, eng]
```

`discoveryhd` entrega **tres** pistas de audio y dos son español. El audio
duplicado del master está medido y le llega al abonado como una opción de más
en la barra. Es del headend.

### 38 minutos sin un corte, y los objetivos de §10 cumplidos — 2026-10-09

La prueba B, y la primera sesión larga limpia del proyecto. Con todo de fábrica
y `targetOffsetMs: 14000` publicado por el site:

```
latidos=1152  (38 min)
buffer    mín=6.9  p1=7.9  p5=8.8  p50=10.5  p95=12.6  máx=13.5
directo   15.9 s, constante
posición  16.6 a 27.1   (nunca negativa: nunca salió de la ventana)
cpu       mediana 8 %
```

**Cero `player.rebuffer`, cero `load.retry`, cero escalones del vigilante.** El
único `ERROR` del volcado es el del cambio de canal al arrancar la sesión, que
es el pendiente 68 y no afecta a la reproducción.

| Métrica de §10 | Objetivo | Medido |
|---|---|---|
| Rebuffer ratio | < 0,3 % | **0 %** |
| Rebuffers por hora | < 1 | **0** |
| Uptime continuo | > 48 h | 38 min limpios (sin medir más) |

#### Lo que esto zanja

**La pregunta abierta del 08-oct sobre §10 ya no hace falta contestarla.** Ese
día «rebuffers por hora» incumplía ocho veces el objetivo mientras el ratio lo
cumplía, y se propuso contar solo los cortes de más de 500 ms. No hay que
cambiar la definición: lo que fallaba era el colchón, y arreglado el colchón la
métrica pasa tal como está escrita. Una métrica que incomoda suele estar
midiendo algo.

#### Y la validación del arreglo del vigilante, por su lado seguro

```
sinAvanzarMs  mediana=0  p95=0  MÁXIMO=8 ms     (umbral: 10 000)
```

Ocho milisegundos en 1 152 latidos. La definición nueva de progreso —reproducir
**y** moverse— no produce ni un falso positivo en 38 minutos, que era el riesgo
del arreglo y la razón de que el primer intento se descartara.

#### El arreglo vive en el servidor, no en la app

Con esto, **no hace falta subir el valor de fábrica de la app**, que era lo que
iba a proponer y es Modo A. El sitio correcto es el generador, porque la ventana
DVR la mide él y es distinta en cada site: desde `servidor-canales@e29f980`
publica el 45 % de la ventana medida, con suelo en 8000 y techo en 14000. Lo que
sirve hoy el site de pruebas:

```
discoveryhd 14000   hbohd 14000   estrellassd 14000   beisbol 14000
espnsur4abr 13000   once 10000    canal22sd 10000     espn 9400
```

Los cuatro últimos son los streams de producción con ventanas cortas: cada canal
recibe lo que su propia segmentación permite, sin que nadie escriba un número.
El 8000 de `LiveTuning` se queda como lo que debe ser, el último recurso para un
canal que no lo declare.

### Bloque B: tres etiquetas que mentían — 2026-10-10

Las tres piezas de este bloque son lo mismo visto tres veces: **un dato que la
app enseña y que no es verdad**. Ninguna cambia cómo se reproduce nada.

#### 58 — el idioma de las pistas de libVLC

La barra decía «sin idioma» en los canales de doble audio, que son justo los que
necesitan que el abonado pueda elegir: en algunos satélites la etiqueta miente y
cambiar de pista es el único remedio (`CLAUDE.md` §6).

El dato **sí está en el manifiesto**. Medido con `ffprobe` sobre el master de
`discoveryhd`:

```
stream|index=0|codec_name=aac|tag:language=spa
stream|index=1|codec_name=aac|tag:language=eng
stream|index=2|codec_name=aac|tag:language=spa
```

Lo que pasa es que libVLC no lo propaga a `IMedia.AudioTrack.language` en HLS.
Lo que sí da es la descripción, y los masters de Flussonic la escriben con el
código delante — `NAME="spa a1"`, `NAME="eng a2"`—. Así que cuando el idioma
declarado viene vacío, se rescata del texto: `LanguageNormalizer.enTexto()`.

**La regla es a propósito estrecha**: una palabra suelta de dos o tres letras,
sin dígitos. Lo que no se podía hacer es pasar el texto entero por `normalize`,
que devuelve tal cual lo que no conoce y habría convertido «audio» en un idioma.
Así `spa a1` da `spa`, y `Track 1`, `Disable`, `a1` y `5.1 surround` no dan nada.
Nueve comprobaciones, incluido el límite conocido: `xyz pista` da `xyz`, y se
acepta porque el daño es una etiqueta rara frente a dejar fuera idiomas de
verdad.

Y se añade `vlc.pistasCrudas`, el volcado en bruto de las dos fuentes que
libVLC ofrece, para que «la barra no dice el idioma» y «libVLC no lo sabe» dejen
de parecerse en el log. Es el equivalente del `tracks.crudo` de Media3.

#### 46 — la sección cerrada, por fin mirable

Su lógica tiene nueve comprobaciones y el camino autorizado está medido contra
el registro de producción, pero **la pantalla no la había visto nadie en una
tele**, y es la que ve un abonado al que le acaban de cortar el servicio. Para
llegar a ella había que levantar un registro que denegase.

Ahora hay un interruptor en el menú, apagado de fábrica, con **dos textos**: el
genérico, corto, y el de la autorización caducada, que son dos frases largas y
es el que puede desbordar a 10 pies de distancia. Lo que hay que mirar es la
maquetación, no el camino.

#### 54 — el modo de desentrelazado

Dos arreglos, ninguno de comportamiento:

- **`GPU_SHADER` retirado del `enum`.** Era el nivel 3 de la cascada y
  `PlanBuilder` no lo devolvía en ninguna rama. Un valor que nadie puede
  producir, puesto en un `enum`, se lee como una capacidad que la app tiene.
- **`VPU` dice ahora lo que significa.** El plan añade el motivo «se espera que
  lo desentrelace el VPU (sin verificar)», porque `deint=VPU` a secas se lee
  como «hay desentrelazado» cuando lo que quiere decir es «la app no ha hecho
  nada y confía». Esa lectura es la que confundió el diagnóstico del peine de
  `estrellassd` durante M5.

De paso, `LIVETV_ENGINE.md` §4.4 deja escrito que el nivel 2 cubre también HD
pero que **el precio lo hace inviable** —63 % de CPU contra 14 %, medido—, y que
para un decoder que existe y entrega mal la llave es `blockedDecoders` y no esa
tabla.

### El volcado en bruto encontró una pista fantasma, y el simulador no simulaba — 2026-10-10

Dos correcciones del bloque B, las dos del mismo volcado de la caja.

#### La pista de audio que no existía

Esto es lo que el instrumento nuevo —`vlc.pistasCrudas`, añadido horas antes—
enseñó en `estrellassd`, un canal con **un solo** audio:

```
vlc.pistasCrudas delMedio=/ | esl/?  descripciones=-1:Disable | 1:Track 2 - [esl]
audio.pistas cuantas=2 idiomas=?,esl motor=libvlc
```

**El pendiente 58 estaba mal diagnosticado.** No era que libVLC devolviera el
idioma nulo: devuelve `esl` perfectamente en la pista buena —y por eso el
rescate del texto, que también se escribió ayer, no hacía falta aquí—. Lo que
devuelve además es **una pista fantasma sin idioma, sin descripción y sin
canales**, y eso explica de una vez las dos cosas raras que se arrastraban:
el `cuantas=2` sobre un canal de un audio, y la opción vacía que la barra
ofrecía y nadie podía nombrar.

Se descartan las pistas sin idioma **y** sin descripción, que es lo que las hace
inservibles: no se pueden etiquetar ni elegir. Queda `vlc.pistasFantasma` para
que se vea cuántas se tiran.

El rescate del texto se queda, porque el caso al que apunta sigue siendo real
—los masters con `EXT-X-MEDIA` separados traen el idioma en `NAME`— y la
descripción de esta caja (`Track 2 - [esl]`) confirma que ahí viene.

#### Y el simulador de denegación no simulaba nada

Edgar activó el interruptor y la sección siguió abriendo. El log lo dice sin
ambigüedad:

```
22:28:26  dev.changed opciones=FINGE DENEGADO=1
22:28:52  plan.construido canal=estrellassd …      ← reproduciendo, con el simulador puesto
```

La causa es que el acceso se decide dentro de `refresh()`, y
`refreshSiHaceFalta()` **no refresca**: vuelve en cuanto ve que no cambió de
servidor y que no han pasado quince minutos. Una decisión correcta para la red y
equivocada para una pregunta que no necesita red.

Ahora el catálogo escucha la revisión del menú y, cuando ese interruptor cambia,
recalcula **solo el acceso** y lo emite. Sin red y sin rehacer la lista.

**Lección del par:** las dos cosas que se arreglan aquí las encontró el log, no
el razonamiento. La fantasma porque se añadió el volcado en bruto antes de
tocar el código, y el simulador porque Edgar probó el interruptor en vez de
creerse que funcionaba.

### Bloque B cerrado — 2026-10-10

Validado en pantalla por Edgar:

- **46**, la sección cerrada: los dos textos, el genérico y el largo de
  caducada, se ven bien en la tele. Era lo último que le faltaba a B9.
- **58**, el audio de libVLC: `estrellassd` enseña **una sola pista** con su
  idioma, no dos con una vacía.
- **54** no necesitaba pantalla: no cambia comportamiento, solo retira un valor
  del `enum` que nadie podía producir y hace que `VPU` diga lo que significa.

Quedan abiertos de la app dos de motor, los dos encontrados leyendo logs de
otras pruebas y ninguno visible para el abonado todavía: el **68** —un
`ERROR_CODE_FAILED_RUNTIME_CHECK` en cada cambio de canal, que cuesta entre 400
y 800 ms de zapeo— y el **64** —`enginesAllowed` no se respeta contra Media3—.
Los dos son Modo A, uno por APK.

### El error de cada sintonía: la superficie llega tarde — 2026-10-10

El pendiente 68, diagnosticado **sin tocar la caja**, con dos lecturas del
volcado del 09-oct y una del código.

#### Lo que no era

La secuencia sospechosa era esta, y la primera lectura la leyó mal:

```
engine.prepare engine=media3 …
engine.release canal=discoveryhd habiaPlayer=false    ← 2 ms después
engine.play
```

Ese `release` **no es un fallo**: lo hace `prepare()` como primera línea, para
construir sobre limpio, y está documentado en el propio método —«salen dos por
sintonía y eso es correcto»—. Conviene dejarlo escrito porque esa lectura
equivocada es la que hay que no repetir.

#### Lo que es

```
engine.attach                                              (.388)
player.error FAILED_RUNTIME_CHECK videoDecoder=null bufferedMs=0   (.397)
player.idle
player.buffering                                           ← se prepara otra vez
```

**El error cae siempre entre 3 y 9 ms después de `engine.attach`**, en las cinco
sintonías del volcado. Y el experimento natural lo confirma sin necesidad de
provocar nada: la **única** sintonía del volcado que no llamó a `attach` —la del
escalón 3 del vigilante, que reconstruye el motor reusando la superficie que ya
tenía— es también la única **sin** error. Y la más rápida:

| | `engine.attach` | Error | 1.er fotograma |
|---|---|---|---|
| Sintonías normales (5) | sí | sí, siempre | 1 725 – 2 054 ms |
| Reconstrucción del vigilante | no | **no** | **1 575 ms** |

La causa está en el orden. `prepare()` construye el reproductor, le da el
`MediaItem` y llama a `exo.prepare()`; la superficie no existe todavía —la crea
la vista de Compose unos milisegundos después— y llega luego por `attach()`. El
comentario del código decía «Media3 la acepta en caliente», y la acepta: a
cambio de reconfigurar el renderer de vídeo con la reproducción ya pedida, lo
que dispara una aserción interna y obliga a preparar entero otra vez.

#### El arreglo

`prepare()` deja el `exo.prepare()` para cuando llegue la superficie, y
`attach()` lo dispara. Detrás de **«Preparar tras tener la superficie»**,
apagado de fábrica, que reproduce el comportamiento anterior.

Con una red: si la superficie no llega en 1,5 s se prepara igual
(`engine.superficieNoLlego`). Sin ella, un cambio futuro en la interfaz que
dejara de entregarla daría pantalla negra permanente en vez del arranque de
siempre, y nadie lo relacionaría con esta espera.

#### Y un instrumento que faltaba

El volcado decía `error=ExoPlaybackException:Unexpected runtime error`, que es
el envoltorio con el que Media3 tapa cualquier aserción: con eso no se puede
diagnosticar nada sin reproducirlo con `logcat` delante, y eso exige la caja de
alguien. Ahora el log escribe **la cadena de causas**, tres niveles, con la
clase y la línea donde nació cada una. Sirve para este error y para el próximo.

### El error se va, el zapeo no mejora, y yo me había inventado el precio — 2026-10-10

Diecisiete sintonías de Edgar, once con el interruptor apagado y seis con él
encendido, cambiando entre los mismos canales.

| | Sintonías por Media3 | Con error | 1.er fotograma (mediana) |
|---|---|---|---|
| Apagado (lo de siempre) | 11 | **9 de 11** | 1 784 ms |
| **Encendido** | 6 | **0 de 6** | 1 794 ms |

**El arreglo funciona: el error desaparece.** Y **mi predicción era falsa**: dije
que costaba entre 400 y 800 ms de zapeo y no cuesta nada medible. Los mismos
canales, uno a uno, dan `+131, +27, −169, +25, +164, −134` ms: ruido.

#### De dónde salió el número que me inventé

De **una** muestra. La reconstrucción del vigilante del 09-oct dio 1 575 ms sin
`attach` y de ahí concluí que el resto perdía ese tiempo por el error. Pero esa
sintonía era un reinicio en caliente —reproductor ya construido, superficie ya
entregada, playlist en caché— y no se podía comparar con un arranque normal. La
comparación buena, la de hoy, dice que no hay diferencia.

**Lo que queda escrito:** el error no era el precio del zapeo. Los ~1,8 s se los
come otra cosa —descarga del primer segmento, inicialización del decodificador y
el punto de arranque que impone `3 × TARGETDURATION`— y eso sigue sin
desglosarse. Que `LIVETV_ENGINE.md` §6 hable de «1,1 a 1,5 s» viene de otra
medición, en otras condiciones, y no es el objetivo de esta.

Para §10 da igual: el zapeo p50 sale en **1,8 s**, por debajo del objetivo de
2 s, con el interruptor y sin él.

#### Lo que sí se gana, y la causa exacta

Nueve de cada once sintonías dejan de lanzar una aserción interna de Media3 y de
preparar el reproductor una segunda vez entera. No es velocidad: es que la caja
deje de hacer un trabajo que no sirve, y que el log deje de tener un `ERROR` por
canal que tapa los de verdad.

Y la cadena de causas, añadida ayer, nombra el sitio exacto en la primera
lectura:

```
ExoPlaybackException:Unexpected runtime error en
  androidx.media3.exoplayer.ExoPlayerImplInternal.handleMessage(ExoPlayerImplInternal.java:911)
  <- IllegalStateException:null en androidx.media3.common.util.Assertions.checkState(Assertions.java:85)
```

**El error era intermitente ya apagado** —9 de 11, no 11 de 11—, que es lo que
cabe esperar de una carrera: cuando la superficie llega lo bastante pronto, no
salta. Encendido, el orden deja de ser una carrera.

#### Y el instrumento costó una línea

La espera de la superficie se ve y se mide: `engine.esperaSuperficie` y
`engine.preparaTrasSuperficie` salen separados por **140 a 190 ms** en las seis
sintonías. La red de 1,5 s no entró ni una vez, y `engine.superficieNoLlego` no
aparece.

### `enginesAllowed` era duro en un sentido y blando en el otro — 2026-10-10

El pendiente 64, el último de la app. `CONFIG_SCHEMA.md` lo describe como un
filtro duro —«un motor ausente de la lista no se elige nunca, ni como último
recurso»— y para `libvlc` lo era: tres comprobaciones de `permitido()` en la
cascada. Para `media3` **no había ninguna**.

Así que un canal con `enginesAllowed: ["libvlc"]` recibía Media3 en cuanto la
caja tenía decoder de hardware: justo el motor que estaba prohibiendo. El camino
de vídeo salía `HW` y el motor se deriva de él.

**Las pruebas tampoco lo veían**, y eso es lo que más conviene recordar: había
una que se llamaba «un motor fuera de enginesAllowed no se elige ni como último
recurso» y solo cubría el sentido que funcionaba (`["media3"]` en una caja sin
hardware). La simetría no estaba probada, y por eso el agujero sobrevivió a una
prueba con ese nombre.

#### El arreglo

Las tres ramas que implican Media3 —hardware, software de plataforma y «códec
sin declarar, se asume hardware»— pasan a exigir que el canal lo permita. Con
Media3 prohibido, el plan cae al respaldo; si tampoco está disponible, el canal
sale no reproducible en vez de usar el motor vetado.

Cinco comprobaciones nuevas, una por rama y una para el menú de degradación:
forzar software a mano tampoco salta por encima de la lista del canal.

Y `plan.sinCamino` registra ahora **la lista**, porque «esta caja no puede» y
«este canal no lo permite» se leían igual y se arreglan en sitios distintos: una
es el hardware y la otra una línea del `config.json`.

#### Lo que esto no cambia hoy

Ningún canal del lineup usa ese campo, así que no hay diferencia en pantalla. Lo
que se arregla es una promesa del contrato: el día que alguien escriba «este
canal con Media3 no, pase lo que pase» —y ese día llega con la ZTE, cuando se
sepa qué no sabe hacer— va a funcionar.

### La noche: sin fugas de memoria, y 549 de 579 cortes eran nuestros — 2026-10-10

Nueve horas y veinte minutos de `mileniosd` —el canal de fibra, el más estable—
con todo de fábrica. 16 820 latidos. Edgar: *«la imagen se veía perfecta y
fluida»*, y tenía razón: la mediana de los cortes fue de **142 ms**.

#### Lo que M8 preguntaba: no hay fuga

```
RAM libre por tramos:  737 → 735 → 728 → 747 → 734 → 732 → 705 → 715 → 731 MB
cpu mediana 7 %, máximo 41 %
```

Plana en nueve horas. **La app no pierde memoria**, que era la pregunta de M8 y
la que no se podía contestar con sesiones de veinte minutos.

#### Y lo que nadie preguntaba

| | |
|---|---|
| Cortes | **579** (62 por hora) |
| Mediana | 142 ms · p95 400 ms · máximo 36 s |
| Cortes de más de 2 s | 20 |
| Ratio de rebuffer | **1,12 %** (objetivo 0,3 %, aceptable 1 %) |
| `offset.vuelveAlBorde` | **547** |
| Atascos del vigilante | 12, recuperados 12 |

**549 de los 579 cortes ocurren en el segundo siguiente a un
`offset.vuelveAlBorde`.** O sea: el 95 % de los rebuffers de la noche **los
provocó nuestra propia vuelta al borde**, no la red. Y no sirvió para nada:

```
05:00:48  offset.vuelveAlBorde offsetMs=37970 ventanaMs=33600 limiteMs=16800
05:00:48  player.rebuffer numero=10
05:00:48  player.playing liveOffsetMs=36924      ← tras el salto, sigue a 37 s
```

El salto cuesta un corte y deja el offset donde estaba. A los 30 s —el
enfriamiento— vuelve a dispararse. Cincuenta y siete veces por hora en las
horas malas.

#### Descartado el origen, con medición

Lo primero era saber si el origen se quedaba atrás, porque eso explicaría la
deriva sin más. **No es eso.** Pidiendo el playlist cada segundo durante cinco
minutos, lo que publica cada stream por cada segundo de reloj:

```
mileniosd  ritmo=0.997x      beisbol  ritmo=1.002x       (1.000 = tiempo real)
```

Y el reloj de la caja va a 2 s del real, con `EXT-X-PROGRAM-DATE-TIME` en el
playlist, así que `directoS` no miente: el reproductor **estaba** 38 s detrás,
pegado al segmento más viejo de una ventana de 33,6 s. Desde ahí, a 1× no se
recupera nunca sin saltar.

#### De dónde sale esa posición

De los reinicios. Mirando el offset en los primeros latidos de cada
reconstrucción de la noche:

```
02:17  tras arrancar  15,8 s   ← bien
07:58  tras arrancar  15,8 s   ← bien
04:57  tras arrancar  64,4 s   ← pegado a la cola
09:26  tras arrancar  65,7 s   ← pegado a la cola
```

Dos de cuatro reconstrucciones dejan el reproductor en la cola de la ventana. Y
el mecanismo que debería corregirlo es justo el que no corrige.

#### Lo que se cambia

La vuelta al borde deja de pedirle al reproductor su «posición por defecto» y
salta a **duración de la ventana − retraso objetivo**, que es una posición
comprobable. Detrás de «Volver al borde calculando la posición», apagado de
fábrica.

Y el aviso pasa a decir **de dónde, a dónde y qué pasó después**
(`posAntesMs`, `destinoMs`, y un `offset.trasElSalto` inmediato). Sin esas tres
columnas, «el salto no sirve» solo se deduce de que el aviso vuelve a salir
treinta segundos más tarde — una conclusión que ha costado una noche entera.

#### Nota de método

El dato que lo resolvió no fue ninguna lectura del código: fue que **los cortes
y los saltos coincidían 549 veces de 579**. Dos columnas contadas por hora. Y lo
que impidió perseguir al origen fue medirlo desde fuera en cinco minutos.

### M6 — Cerrado — 2026-10-09

Declarado por Edgar: **«probé los cinco modos, todos se ven correctos»**, sin
peine y con cadencia normal.

#### Entró por un número, no por si acaso

El plan decía «solo si M3 o M5 lo requieren». M5 lo requirió: `estrellassd` se
presentaba a **15 fps con peine**, acotado hasta el final —no el render, no la
app, no el stream— y la caja no tiene otro decodificador de MPEG-2. Con libVLC,
software forzado y yadif2x, se ve bien y cuesta **menos del 16 % de CPU**, que
era la duda razonable.

Primer fotograma en 443 ms, más rápido que Media3.

#### Lo que costó no fue el motor

Fueron seis cosas que existían y no hacían lo que decían:

| | |
|---|---|
| El veto de decoders del menú | se guardaba y nadie lo leía — y es la palanca con la que el plan dice que se prueba M6 |
| `hasVlcBackend` | dependía de quién llamara primero a una caché que ignora argumentos |
| El canal | publicado como progresivo siendo 480i medido |
| El latido | vivía solo en el otro motor: una sesión con libVLC no dejaba rastro |
| `audioMime` | devolvía el códec declarado, así que el aviso de §2 se comparaba consigo mismo |
| El SAR | libVLC lo aplicaba dos veces: 1695×1080 donde tocaban 1440×1080 |

Tres de las seis se descubrieron **intentando probar**, no escribiendo. Y dos
—el latido y el `audioMime`— no las habría encontrado nadie mirando la
pantalla: la primera porque su síntoma es la ausencia de un log, y la segunda
porque su síntoma era un aviso encendido donde todo estaba bien, que es más
fácil de creer que de dudar.

#### Dos geometrías, no una

Lo que más tiempo llevó fue entender que **los dos motores no se parecen**.
MediaCodec estira el vídeo hasta llenar la caja que se le da —en eso se apoya
todo el dimensionado de superficie de M5— y libVLC mantiene la proporción
dentro de su ventana. Tratarlos igual producía franjas donde no debía haberlas
y recortes donde no tocaba.

Ahora el modo de imagen se le dice al motor y cada uno lo resuelve a su manera.
Medido tras el arreglo: franjas de 240 px y 1440 px de imagen, el 4:3 exacto.

#### Lo que queda abierto, dicho sin adornos

**El respaldo todavía no se dispara solo en casa de un abonado.** La regla que
lo activaría —nivel 2 de `LIVETV_ENGINE.md` §4.4— quedó implementada en esta
fase y gobernada por `vpuDeinterlaces: false` en los `deviceOverrides`, con
tres pruebas. Pero necesita el otro dato, y ese llega mal: **`interlaced` viene
`false` para MPEG-2** porque Flussonic responde `field_order=unknown`
(pendiente 53).

Hoy se suple con «Forzar desentrelazado», que es un interruptor de diagnóstico.
La solución de verdad es que el operador pueda corregir ese campo en el lineup
de su site, y eso es trabajo del otro proyecto.

**Así que M6 entrega una capacidad demostrada y una palanca manual, no un
arreglo automático.** Conviene que quede escrito así y no como «libVLC
resuelve los MPEG-2», que es lo que se leería dentro de tres meses.

#### Precio

La descarga por caja pasa de ~20 MB a ~47 MB. VideoLAN solo publica el
artefacto con las cuatro arquitecturas; con el AAB cada caja se baja la suya.

### M5 — Cerrado — 2026-10-08

Declarado por Edgar: **«no hay peines en ninguno, la imagen se ve bien»** y
**«llenar y zoom quitan las franjas»**.

#### Lo que de verdad costó esta fase

No fue el entrelazado. Fue descubrir que **el SAR nunca se había aplicado**.

Los cinco modos de imagen no hacían nada, y la primera hipótesis —que el
cálculo estuviera mal— era falsa: las escalas salían correctas y distintas.
Lo que no llegaba era la transformación, porque un `SurfaceView` lo compone el
sistema aparte del árbol de vistas.

Y debajo había algo peor: nadie fijaba el modo de escalado del códec, así que
MediaCodec estiraba el vídeo hasta una superficie que siempre era la pantalla
entera. Los dos canales anamórficos llevaban saliendo deformados **desde M2**,
con el dato correcto en el log todo el tiempo. Leer un valor y usarlo son cosas
distintas, y aquí solo pasaba la primera.

#### Lo que cierra y lo que no

| | |
|---|---|
| Aspecto y SAR | ✅ validado: `estrellassd` con sus franjas, `estrellashd` a pantalla completa |
| Modos manuales | ✅ los cinco hacen algo, y el recorte de `FILL`/`ZOOM` sale limpio |
| Entrelazado 1080i | ✅ sin peine en `discoveryhd` ni `hbohd` |
| Entrelazado 480i | ❌ **`estrellassd` a 15 fps con peine → M6** |
| #37 tunneled | ✅ se queda |
| #24 pulldown | ✅ se acepta; el tunneled lo mitiga |

La excepción está acotada hasta el final: no es el render —probado con la
superficie a 1920×1080—, no es la app —`discoveryhd` da 29,9 fps en las mismas
condiciones— y no es el stream —600 fotogramas en 20 s medidos con `ffprobe`—.
Es el único decodificador MPEG-2 que tiene la caja.

El plan contemplaba cerrar así: *«o con la limitación documentada por modelo y
el nivel correspondiente implementado»*. El nivel es libVLC, que es M6.

#### Lo que esta fase deja escrito para las siguientes

**Tres veces hubo que descartar que el culpable fuéramos nosotros antes de
señalar a la caja**, y las tres veces valió la pena hacerlo: el dimensionado de
la superficie era cambio nuestro de dos días antes y tocaba el render, así que
la coincidencia con el único canal redimensionado pedía una medición, no una
explicación.

**Y dos instrumentos engañaron.** El latido muestrea cada 2 s y los valles del
buffer duran menos, lo que llevó a decir que un rebuffer «no era por falta de
datos» cuando sí lo era. Y «rebuffers por hora» de §10 cuenta eventos sin
pesarlos, lo que hace que una sesión que un humano juzga buena incumpla el
objetivo ocho veces.

### Dos métricas de §10 que se contradicen, y la que acierta — 2026-10-08

Cuarta sesión sobre `hbohd`, 24,1 minutos con tunneled. Edgar la juzgó en
pantalla: *«hubo varios rebuffers pero creo que son viables, la imagen dura muy
poco cortada y el stream se ve limpio mientras está de corrido»*.

Los números dicen las dos cosas a la vez:

| Métrica de §10 | Medido | Objetivo | |
|---|---|---|---|
| **Rebuffer ratio** | **0,260 %** | < 0,3 % | **pasa el objetivo** |
| **Rebuffers por hora** | **24,9** | < 1, aceptable < 3 | **ocho veces el aceptable** |

Las dos están bien calculadas. Lo que pasa es que **miden cosas distintas**, y
aquí se separan:

```
11 cortes   mediana 313 ms   mínimo 58 ms   máximo 930 ms   total 3,76 s
```

#### La que acierta es el ratio, y conviene entender por qué

«Rebuffers por hora» **cuenta eventos sin pesarlos**: un parpadeo de 58 ms vale
lo mismo que una congelación de quince segundos. Ese objetivo se escribió
pensando en paradas que se ven; aquí la mediana son 313 ms.

Y que 313 ms no se ven no es una opinión: **está demostrado hoy mismo**. La
barra de canal salía con cada uno de esos parpadeos, y Edgar la describió como
molesta sin haber notado el corte que la provocaba. El aviso era más visible
que el fallo.

El ratio sí los pesa, y por eso coincide con lo que vio una persona mirando la
pantalla.

**Propuesta, que es decisión de Edgar:** que «rebuffers por hora» cuente solo
los que pasan de un umbral —medio segundo parece el sitio— y que el ratio siga
como está. Un objetivo que una sesión buena incumple ocho veces no se puede
usar para decidir nada: o se afina o se ignora, y lo segundo es peor.

#### Y una variación que no es del cliente

Mismo canal, misma configuración, mismo día:

| | Duración | Rebuffers |
|---|---|---|
| Tercera sesión | 20,4 min | **0** |
| Cuarta sesión | 24,1 min | **10** |

Cero y diez en condiciones idénticas. Eso no lo decide la app: lo decide lo que
llega del satélite y cómo lo empaqueta Flussonic. Refuerza que lo que queda de
`hbohd` es trabajo de headend —el `source_timeout` del pendiente 6 y el audio
duplicado del 36— y no de cliente.

El resto de la sesión, sano: 723 latidos **todos** en `listo`, buffer mínimo
3,8 s, sin vuelta al borde, y el offset entre 9,7 y 11,9.

### Veinte minutos de HBO con tunneled: cero rebuffers — 2026-10-08

La tercera sesión del día sobre `hbohd`, y la que decide.

| | con tunneled | sin tunneled | **con tunneled, largo** |
|---|---|---|---|
| Duración | 5,6 min | 1,6 min | **20,4 min** |
| Rebuffers | 1 | 15 | **0** |
| Reb/hora | 10,7 | 574 | **0,0** |
| `directo` | 10,5 → 10,8 | 10,7 → **20,7** | **11,8 → 11,8** |
| Vuelta al borde | no | **sí** | no |
| Buffer mínimo | 2,6 s | — | **4,1 s** |
| `sinAvanzar` máximo | 0 ms | — | **13 ms** |

612 latidos, **todos** en `listo`. El offset no se movió de 11,8–11,9 en veinte
minutos. El buffer no bajó de 4,1 s ni una vez.

Contra los objetivos de §10: rebuffers por hora **0,0** con objetivo < 1, y
rebuffer ratio **0 %** con objetivo < 0,3 %. Los dos mejor que el objetivo, no
que el «aceptable».

Sumando las dos sesiones con tunneled —26 minutos, 1 rebuffer— salen 2,3 por
hora, que entra en el «aceptable» de §10 pero no en el objetivo. La muestra
larga sale a cero, así que el que manda es el tamaño de la muestra: hacen falta
horas, no minutos, y eso es M7.

#### Qué zanja

**El pendiente 37.** La pregunta era si el fallo de la aserción del tunneled
—80 % de los zapeos, siempre recuperado— justificaba apagarlo. La respuesta
está en la tabla: apagarlo multiplica los rebuffers por un factor que no admite
discusión. El fallo del tunneled cuesta un tropiezo al cambiar de canal;
apagarlo cuesta el canal entero.

El interruptor se queda para los modelos que lo necesiten —se excluyen por
`deviceOverrides`, no recompilando (§6)— pero **el valor de fábrica no se
toca**.

#### Qué no zanja, y por qué no se puede

**El pulldown de #24 no es medible en la configuración que se publica.** Hace
falta ver la entrega de fotogramas, y con tunneled ni el contador propio ni
`SurfaceFlinger --latency` ven nada (§6). Sin tunneled el canal se cae cada
cuatro segundos, así que tampoco sirve.

No es un hueco de método: es que **el instrumento y el objeto son
incompatibles**. Y conviene recordar por qué importa menos de lo que parece:
el tunneled se adoptó el 01-oct precisamente porque es lo único medido que
elimina esos microcortes. Con él puesto, #24 ya está mitigado; lo que queda es
saber si alguna caja futura obliga a apagarlo, y ahí volverá la pregunta.

Lo único que puede decir si quedan microcortes en HBO son los ojos de Edgar
sobre estos veinte minutos.

### Apagar el tunneled no sale gratis — 2026-10-08

Edgar probó `hbohd` en los dos modos para confirmar si el decodificador de
Amlogic ignora la cadencia 3:2 (#24). Lo que salió fue otra cosa, y pesa más.

| `hbohd` | con tunneled | sin tunneled |
|---|---|---|
| Duración | 5,6 min | 1,6 min |
| Rebuffers | **1** | **15** |
| `directo` | 10,5 → 10,8, y plano | 10,7 → **20,7** |
| Vuelta al borde | no saltó | **saltó** |
| Fotogramas perdidos | no medibles | 74 |
| Hueco máximo entre fotogramas | no medible | **1401 ms** |

Quince rebuffers en 70 segundos es uno cada cuatro. Y no es la red ni la caja:
77 Mbps de media para un canal de 4, CPU al 9 %, **cero reintentos**. Con
tunneled, en una sesión tres veces más larga, hubo uno.

**Eso explica lo que Edgar describió** —«el vídeo va adelantado al audio y
parece ir más rápido»—: cada rebuffer dejaba el canal más atrás del directo,
el offset trepó hasta 20,7 s, cruzó la media ventana (16,7 s de 33,4) y la
vuelta al borde hizo su trabajo: saltar al directo. Ese salto es el vídeo
adelantándose de golpe. Los 74 fotogramas perdidos hacen el resto.

#### Lo que esto le hace al pendiente 37

La pregunta era si el fallo de la aserción del tunneled —80 % de los zapeos,
siempre recuperado— molesta lo bastante como para apagarlo. Ahora hay que
ponerle precio al otro lado: apagarlo convierte `hbohd` en un canal que se
corta cada cuatro segundos.

No cambia que el fallo exista. Cambia que ya no hay una opción cómoda.

#### Lo que esto **no** zanja

El pulldown de #24 sigue sin medirse en esta versión. Se esperaba ver ~48 fps
si el decodificador ignoraba la cadencia, y lo medido fue ~30 — que es la
cadencia de fotograma del stream, no la de campo. Con 15 rebuffers de por
medio, esa medición no vale: hay que repetirla en un rato sin cortes.

Y es **una sola sesión de 1,6 minutos**. El tamaño del efecto es tan grande que
cuesta atribuirlo al ruido —1 contra 15, con los controles limpios— pero antes
de darlo por cerrado conviene repetirla.

#### Una corrección

El mismo día, con el log anterior, se dijo que los rebuffers de `hbohd` «no
eran por falta de datos» porque el buffer nunca bajaba de 3,6 s. **Era falso, y
el error estaba en el instrumento:** el latido muestrea cada 2 s y los valles
duran menos. En la sesión siguiente se cazó el valle —`bufferS=2.6` en el
latido inmediatamente anterior al rebuffer, la lectura más baja de toda la
sesión— y el umbral para seguir reproduciendo son 2 s. Sí era falta de datos.

### M4 — Los flags del extractor, y dos cosas que salieron de paso — 2026-10-07

Edgar recorrió las cuatro combinaciones de los dos flags sobre los canales
H.264 y volcó el log en cada vuelta. Su veredicto en pantalla fue que **la de
fábrica es la que mejor se ve**, y los números lo sostienen.

| Flags | Canal | 1er fotograma | Rebuffers |
|---|---|---|---|
| **A — fábrica** | `estrellashd` | 2547 ms | 1 |
| | `hbohd` | 2132 ms | 0 |
| | `discoveryhd` | 1966 ms | 0 |
| | `mileniosd` | 1741 ms | 0 |
| | `beisbol` | 1740 ms | 0 |
| **B — AUD on** | `estrellashd` | 2002 ms | 3 |
| | `hbohd` | 1861 ms | 0 |
| | `discoveryhd` | 2042 ms | 0 |
| | `beisbol` | 1765 ms | 0 |
| **C — nonIDR off** | `discoveryhd` | **6953 ms** | 0 |
| | `hbohd` | 2599 ms | 0 |
| | `beisbol` | 1633 ms | 2 |
| **D — ambos** | `hbohd` | 2716 ms | 0 |
| | `discoveryhd` | 1752 ms | 0 |

**Los 6953 ms son la decisión.** Con `FLAG_ALLOW_NON_IDR_KEYFRAMES` apagado,
`discoveryhd` tardó siete segundos en dar imagen contra 1966 ms con el flag
puesto: más del triple, y por encima del objetivo de arranque de §10. Es
exactamente para lo que existe ese flag — sin él el extractor espera un IDR de
verdad, y donde los IDR escasean el arranque se desmorona.

`FLAG_DETECT_ACCESS_UNITS` no aporta nada medible: tiempos equivalentes y algún
rebuffer de más. Se queda apagado.

**Lo que no está completo, y conviene decirlo:** en C y D faltan `estrellashd`
—murió en origen a mitad de sesión— y `mileniosd`. La conclusión no depende de
ellos, porque la penalización de C ya es inequívoca, pero el barrido no se
terminó.

#### De paso salió el dato que cambia el pendiente 37

**16 fallos de la aserción del tunneled en 20 sintonizaciones. Un 80 %.**

La estimación anterior era «entre el 5 % y el 30 % de los zapeos». Esto es casi
uno por cada cambio de canal. Sigue recuperándose —19 de 20 dieron imagen, y la
que no fue el canal caído— pero una cosa es un fallo que aparece a veces y otra
uno que aparece casi siempre.

Eso no cambia lo que hay que hacer, que sigue siendo decisión de Edgar y de
Modo A, pero sí el peso de la decisión: el tunneled es lo único medido que quita
los microcortes del cine en H.264, y ahora se sabe que su contrapartida es
prácticamente constante.

#### Y se vio en vivo el pendiente 42

`estrellashd` cayó en origen durante la segunda vuelta. A las 01:15 la app pasó
de 10 canales a 9:

```
01:15:28  config.interpretada  version=1 canales=9 grupos=4
01:15:28  catalogo.listo       origen=RED canales=9 ...
```

**La app no quitó nada: el generador dejó de publicarlo.** No publica un stream
que no emite, y la caja enseña lo que el site publica. Volvió solo cuando volvió
el stream.

Es el comportamiento correcto y aun así desconcierta a quien lo mira —un canal
desaparece del listado sin explicación—, que es justo lo que el pendiente 42
pedía decidir. Ahora hay un caso real en vez de una suposición.

### #50 — El SAR se leía bien y no llegaba a la pantalla — 2026-10-06

Edgar recorrió los cinco modos de imagen y no vio cambiar nada. La primera
hipótesis —que gran parte fuera por diseño— era cierta pero no era el fallo:
cinco de los seis canales se presentan 16:9 en una pantalla 16:9, y ahí
Ajustar, Llenar y 16:9 calculan lo mismo. Lo que había que saber era si Zoom y
4:3 tampoco hacían nada.

**No se podía mirar.** En esta caja `screencap` devuelve el búfer vacío sobre el
reproductor, con tunneled y sin él. Así que se instrumentó el cálculo, y el log
zanjó la pregunta en una pasada sobre `estrellassd`:

```
FILL        escalaX=1.333  escalaY=1.333
ZOOM        escalaX=1.493  escalaY=1.493
RATIO_4_3   escalaX=1.000  escalaY=1.000
RATIO_16_9  escalaX=1.333  escalaY=1.000
```

Escalas correctas, distintas entre sí, y un 33 % de estiramiento horizontal que
es imposible no ver. **La transformación de la vista no llega a la superficie.**
Un `SurfaceView` lo compone el sistema aparte del árbol de vistas, y `scaleX` /
`scaleY` no lo alcanzan.

#### Y debajo había algo peor

Al mirar por qué, salió lo que de verdad importa: **nadie fija el modo de
escalado del códec**. MediaCodec usa el suyo de fábrica, `SCALE_TO_FIT`, que
estira el vídeo hasta las dimensiones de la superficie **sin respetar la
proporción**. Con la superficie siempre a pantalla completa, eso significa que
`estrellassd` llevaba saliendo deformada —4:3 estirado a 16:9— y que «Ajustar»
no corregía nada.

El comentario de `AspectoDeVideo` decía «el punto de partida ya es correcto:
con el SAR respetado, los seis canales salen con su proporción real en modo
FIT». Era una suposición que nadie había comprobado, y era falsa. El SAR se
leía bien —`sar=1.1764706` en el log— y no se aplicaba a nada.

Vale la pena ver cómo se escondió: el dato correcto estaba en el log desde
siempre, y eso daba la sensación de que el trabajo estaba hecho. Leer un valor
y usarlo son cosas distintas, y aquí solo pasaba la primera.

#### El arreglo

No escalar la vista: **dimensionar la superficie**. Es lo que hace el
`AspectRatioFrameLayout` del `PlayerView` de Media3, que es justo la pieza que
este módulo no usa. El códec sigue estirando hasta llenar su caja, pero la caja
ya tiene la forma buena.

`FIT`, `4:3` y `16:9` **caben dentro** de la pantalla; `FILL` y `ZOOM`
**cubren**, saliéndose por el lado corto, que es lo que pide quien no quiere
franjas.

Medido con `SurfaceFlinger`, que es el único instrumento que ve la superficie
en esta caja:

| Canal | Proporción | Superficie antes | Ahora |
|---|---|---|---|
| `mileniosd` | 1.778 | 1920×1080 | 1920×1080 |
| `once` | 1.814 | 1920×1080 | **1920×1058** |
| `estrellassd` | 1.333 | 1920×1080 | **1440×1080** |

El primero no cambia y es correcto que no cambie: un 16:9 en una pantalla 16:9
no tiene nada que corregir. Los otros dos son la diferencia entre la imagen
real y la deformada.

**Queda la validación en pantalla**, que es lo único que distingue «la
superficie tiene la forma buena» de «la imagen se ve bien»: en particular si
`FILL` y `ZOOM` se recortan limpiamente al salirse de la pantalla, que es la
parte que `SurfaceFlinger` no responde.

### #21 — La etiqueta que no servía para lo que creíamos — 2026-10-06

El pendiente 21 pedía que Flussonic emitiera `EXT-X-SERVER-CONTROL` con un
`HOLD-BACK` explícito, para recuperar el control del offset que hoy fija
`3 × TARGETDURATION`. **No sirve, y conviene que quede escrito por qué.**

La especificación (RFC 8216bis, `EXT-X-SERVER-CONTROL`) dice que el valor de
`HOLD-BACK` **tiene que ser al menos tres veces la duración objetivo**, y que su
ausencia implica exactamente ese mismo valor. O sea: la etiqueta sólo puede
**subir** el suelo, nunca bajarlo. Lo que teníamos sin ella era ya el mínimo
legal.

**Lo que de verdad fija el suelo es `TARGETDURATION`.** Y ese no se configura:
Flussonic lo deriva del segmento más largo del playlist, redondeando hacia
arriba. Los segmentos salen más largos que el `segment_duration 2` pedido porque
se corta en keyframes, así que acaban siendo dos o tres GOP de la fuente.

Medido el 06-oct sobre los seis, con `segment_duration 2` ya aplicado:

| Canal | `TARGETDURATION` | Suelo | Ventana | Margen |
|---|---|---|---|---|
| `beisbol` | 3 | 9 s | 33.6 s | 24.6 s |
| `mileniosd` | 3 | 9 s | 33.6 s | 24.6 s |
| `discoveryhd` | 3 | 9 s | 31.3 s | 22.3 s |
| `hbohd` | 3 | 9 s | 33.2 s | 24.2 s |
| `estrellashd` | 4 | 12 s | 36.0 s | 24.0 s |
| `estrellassd` | 5 | 15 s | 46.3 s | 31.3 s |

**Acortar el segmento exigiría acortar el GOP, y eso es transcodificar vídeo**,
que es justo lo que §2 prohíbe. Así que el suelo es el que es.

**La buena noticia la dan las dos últimas columnas.** El miedo del 22-sep —un
offset que se come la ventana y provoca `BEHIND_LIVE_WINDOW`— ya no existe: con
`segment_count 12` las ventanas van de 31 a 46 s y el margen sobre el suelo
nunca baja de 22 s. No hay nada roto que arreglar.

**La acción se mueve de sitio: del headend al generador del `config.json`.** Que
calcule `targetOffsetMs` desde el `TARGETDURATION` real de cada canal en vez de
publicar 8000 a todos. Pedir 8000 donde el suelo son 9000 no rompe nada, pero
deja a la app escribiendo `offset.noSeAplica` por algo que nadie puede atender,
y un aviso que no se puede atender acaba ignorándose — incluidos los que sí
importan. Queda en `CONFIG_SCHEMA.md` §4 con la tabla.

**Lo que la etiqueta sí podría hacer, por si algún día hace falta:** subir el
suelo a propósito. §6 quería 12 s en los satelitales por resiliencia ante la
lluvia, y hoy eso sale por accidente del `TARGETDURATION` de cada canal. Si
alguna vez se quiere determinista, `HOLD-BACK` es la forma — hacia arriba.

**La otra salida, descartada:** Flussonic trae LL-HLS de fábrica, y ese
manifiesto sí emite `EXT-X-SERVER-CONTROL` con `PART-HOLD-BACK`, que puede ser
mucho menor. Pero trocea los segmentos en chunks y es otro transporte: más
peticiones, más carga y una pila que nadie ha medido aquí. Este proyecto no
persigue latencia baja —persigue que no se corte—, así que queda fuera.

### #48 — El campo de texto actuaba sobre teclas que no se habían pulsado en él — 2026-10-05

Empezó como el simétrico del 39: con la búsqueda abierta, ARRIBA no volvía a la
cabecera. La causa era la que ya estaba documentada para ABAJO —
`BasicTextField` se queda las teclas de dirección aunque sea de una sola
línea—, así que se resolvió igual, interceptando ARRIBA y devolviendo el foco
al botón «Buscar canal».

**Lo interesante vino después.** Con ARRIBA funcionando, ABAJO desde la
cabecera seguía saltándose el campo y aterrizando en la guía, aunque el campo
esté justo debajo y ocupe todo el ancho. Apuntar `down` explícitamente al campo
**tampoco** cambiaba nada, lo que parecía decir que `focusProperties` se estaba
ignorando.

No se ignoraba. Se comprobó apuntando `down` **al propio botón**: el foco se
quedó donde estaba, así que `down` se respetaba perfectamente.

**Lo que pasaba es que la tecla sobrevivía al salto de foco.** El campo actúa al
**soltar** la tecla, no al pulsarla —así lo pedía ATRÁS, que tiene que cerrar el
teclado la primera vez y la búsqueda la segunda—. Cuando la cabecera mandaba el
foco al campo con ABAJO, el foco llegaba con la tecla **todavía pulsada**, y el
«soltar» aterrizaba en el campo recién enfocado. El campo lo leía como su propio
ABAJO y se iba a la guía. El mismo ABAJO hacía dos saltos.

La corrección es una guarda de tres líneas: **solo se actúa sobre teclas cuyo
«pulsar» se vio aquí**. Se apunta la tecla en el `KeyDown` y se exige que
coincida en el `KeyUp`.

Un mando de verdad manda el mismo par pulsar/soltar que `adb`, así que esto no
era un artefacto de la prueba: le habría pasado a cualquiera. Y es una clase de
fallo que conviene recordar porque **no se ve**: lo que se observa es «el foco
va donde no debe», que manda a mirar la búsqueda de foco, que es justo donde no
está el problema.

Medido en la caja, el bucle entero:

```
tarjeta  → ARRIBA → Buscar canal    [1410,230][1614,326]
         → OK     → campo de texto  [204,350][1788,446]
         → ARRIBA → Buscar canal    [1410,230][1614,326]
         → ABAJO  → campo de texto  [204,350][1788,446]   ← antes saltaba aquí
         → ABAJO  → tarjeta 101     [86,551][442,751]
```

Y lo que no se rompió al tocar el camino de teclas: ATRÁS sigue cerrando la
búsqueda, y teclear sigue filtrando —«hbo» deja HBO HD solo—.

### B8 — El EPG, y el fallo que la JVM no podía enseñar — 2026-10-05

La barra llevaba desde M2 con `DummyEpg`: una parrilla inventada, anclada al
reloj de verdad para que la barra de progreso avanzara mientras se miraba. Ya
no hace falta — el site sirve `epg.xml` y el `config.json` lo anuncia.

**Lo que hay ahora, en el módulo:** `XmltvParser` recorre el documento con SAX,
`Guia` guarda lo que se conserva y `EpgRepository` descarga, cachea y relee.
`DummyEpg` desaparece y `Programa`/`FranjaEpg` se mudan de `app/` al módulo,
que es donde vive el cliente del servidor del site.

**El fichero no se lee a memoria, se descarga a disco.** Es la diferencia de
fondo con el `config.json` y la razón de que el tope sea 24 MB en vez de 2: lo
que nunca cabe en una caja de 2 GB mientras decodifica vídeo es el documento
entero como `String`. SAX lo recorre y solo retiene la ventana.

**Se pide `gzip` y se descomprime.** El proyecto del servidor preguntaba en
`PETICION_APK` §3.2 si la app lo acepta y no iba a activarlo hasta tener
respuesta. La respuesta está ahora en `INTEGRACION_CANALES.md` §4.bis: sí. Un
XMLTV comprime a una décima parte, y eso cruza la LAN del ISP en cada refresco
de cada caja.

#### Dos relojes, y no son el mismo

Costó verlo y es la parte menos obvia del diseño. Una guía se vuelve a
**descargar** cuando caduca `refreshMinutes`. Una guía se vuelve a **leer**
cuando el tiempo sale de la ventana que hay en memoria — y eso pasa sin que el
servidor publique nada.

Una caja de salón encendida toda la noche necesita lo segundo y no lo primero:
a las seis de la mañana la ventana que se leyó a medianoche no tiene nada que
decir, y con un solo reloj la barra se habría quedado muda hasta que alguien
reiniciara la app. Por eso `guiaSiHaceFalta()` pregunta si la ventana **cubre
el presente**, no cuánto hace que se descargó.

#### La guía va en un flujo aparte del catálogo, y no es organización

`LiveCatalog` se compara consigo mismo antes de reemitir, porque reemitirlo
rehace las tarjetas de la guía y al rehacerlas el foco salta al canal inicial
—el fallo del 04-oct—. Meter la programación dentro habría obligado a [Guia] a
participar en esa comparación, y una guía nueva cada pocas horas habría hecho
saltar el foco de quien estuviera mirando.

Además tienen urgencias distintas: la lista pesa 40 kB y tiene que aparecer ya;
la guía pesa megas y puede tardar. Quien espera a la segunda para enseñar la
primera ha elegido mal.

#### El fallo que la JVM no podía enseñar

El parser llevaba, como blindaje contra entidades externas:

```kotlin
setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
```

Catorce pruebas en verde en la JVM. **En la caja, la guía entera se perdía:**

```
epg.cacheIlegible … no se pudo leer el XMLTV:
  http://apache.org/xml/features/disallow-doctype-decl
```

Esa bandera es de Xerces. La JVM la acepta; el SAX de Android **lanza**. Y el
`runCatching` que envolvía el análisis la convertía en «XMLTV ilegible», así
que el síntoma —una barra sin programación— no se parecía en nada a la causa.

**La corrección no fue quitarla, fue cambiar en qué se confía.** Las tres
banderas de Xerces van ahora cada una en su propio intento y ninguna puede
tumbar al resto. Lo que de verdad cierra el agujero es el **resolvedor de
entidades**: sea cual sea el analizador, si decide resolver una entidad lo
pregunta, y la respuesta es siempre un documento vacío. Eso funciona igual en
las dos plataformas.

La prueba se reescribió para comprobar **lo que importa y es portable** —que
una entidad externa no acaba en el título— y no la implementación, que es
distinta en cada sitio. Es la lección general: una prueba que fija la bandera
que se usó habría seguido en verde con la caja rota.

#### Medido en la caja

```
config.epg url=http://192.168.20.249:8097/epg.xml refrescoMin=1440
epg.descargada bytes=90078 comprimido=false
epg.interpretada canales=7 programas=59 descartados=0 fueraDeVentana=142 canalesDesconocidos=0
epg.lista origen=RED canales=7 programas=59 antiguedadMin=0
```

Y en pantalla: HBO HD 201, «Lluvia de hamburguesas 2: La venganza de las
sobras», 16:47 – 18:26, «quedan 31 min», barra de progreso y «Luego Kung Fu
Panda 2». El servidor emite ese programa como `21:47–23:26 +0000`; la caja está
en UTC-5. **El huso funciona de punta a punta**, que era el riesgo silencioso:
una guía corrida cinco horas se ve plausible.

También se comprobó la degradación, sin buscarla: `espnsur4abr` está publicado
y **no** está en el XMLTV, y su barra sale con «En vivo» y sin programación. Un
canal sin guía es normal (`CONFIG_SCHEMA.md` §5).

#### Lo que no entra

La guía **en rejilla** —varias horas por varios canales— no se ha hecho. La
barra enseña ahora y luego, que es lo que pedía el plan; una rejilla es otra
pantalla y otra decisión.

### Los PNG de marca salen del vector, no de una copia — 2026-10-05

Fuera del APK hacen falta mapas de bits en tres sitios, y los tres los pide
alguien que no compila: la ranura `logo` del registro, y el icono y el banner
de la ficha de Play.

**Lo caro no era dibujarlos, era mantenerlos.** Un PNG exportado a mano y
guardado al lado del vector se queda atrás en cuanto alguien retoca el
logotipo, y nadie se entera hasta verlo en la tienda. Así que no hay ninguna
copia del dibujo: `herramientas/marca/vector-a-svg.py` traduce el
VectorDrawable a SVG y `generar.sh` lo rasteriza.

El conversor cubre **sólo el subconjunto que usan nuestros vectores**: `path`
con `pathData`, colores, grosor y extremos, y `group` con traslación. Ante
cualquier otra cosa —`scale`, `rotation`, degradados, `clip-path`— aborta
diciendo qué le falta, en vez de dibujar algo distinto en silencio. Un
logotipo que sale mal sin avisar es peor que uno que no sale.

**Del icono salen dos, y es una decisión, no un descuido.** El icono adaptativo
encoge su símbolo a la zona segura **circular** de 66 dp sobre 108, porque un
lanzador puede recortarlo en círculo. Play no recorta así: aplica una esquina
redondeada suave, y ese mismo icono se ve encogido en la ficha. La variante
`-ficha` escala el dibujo por 89/66 —de la zona segura circular a la
rectangular—, que es geometría del formato y no retoque de gusto. El del APK
no se toca, porque ahí la zona segura es real.

Se escala el dibujo **entero**, fondo incluido: el fondo es un rectángulo que
llena el lienzo, así que al crecer lo sigue llenando. Eso evita tener que
componer dos SVG con transformaciones distintas.

Los PNG se guardan en el repositorio aunque sean generados. Pesan 36 KB entre
los cuatro, y son los bytes exactos que se suben a Play y al registro: saber
qué se publicó vale más que la pureza de no versionar artefactos.

### #47 — El logo de marca, ahora también en las cajas viejas — 2026-10-05

El registro sirve el logo del ISP por HTTPS desde su propio host. Una caja
anterior a Android 7.1.1 no trae la raíz ISRG, así que la descarga fallaba:
medido el 05-oct en API 22, **la autorización entraba y el logo no**. La
autorización entra porque `RegistryRepository` ya usa la confianza ampliada de
`RegistryTrust`; el logo lo pide Coil, y Coil usaba la del sistema.

**Lo que no se hizo, y es la mitad de la decisión:** ampliar el `ImageLoader`
global. Es el mismo que carga las carátulas del VOD. Ampliar la confianza de
todas las imágenes de la app para arreglar un logo es mal negocio, y además
sería meter mano en el camino que `CLAUDE.md` §4 declara invariante.

Así que el cargador es **propio y pequeño**: un `OkHttpClient` con la misma
confianza que ya usa el registro, el decodificador SVG porque un logo de marca
es tan probable en vector como en mapa de bits, y nada más. Lo usa sólo
`LogoDeMarca`.

Para dárselo a OkHttp hizo falta exponer también el `X509TrustManager`:
`HttpURLConnection` se conforma con la fábrica de sockets, OkHttp exige las dos
piezas. De ahí `RegistryTrust.Confianza`, con `socketFactory(context)`
delegando en ella para no duplicar la caché.

**Medido en el emulador de API 22 contra el registro de producción:**

```
catalogo.listo origen=RED canales=10 grupos=4 marca=pruebas marcaOrigen=registro
```

y el logo dibujado en la cabecera, donde antes salía el nombre. Ni
`marca.logoSinConfianzaAmpliada` ni error de TLS en el registro. El «antes» es
la medición del 05-oct anotada en el pendiente, no una repetición de hoy: entre
las dos no hay más cambio que este cargador.

**Si aun así falla, no se ve como un error.** Sin confianza ampliada se usa la
del sistema —en una caja moderna es la que hacía falta—, y si el logo no carga
`LogoDeMarca` cae al nombre del proveedor, que es lo diseñado para eso (§8).

**Una observación que no es del código, para Edgar:** el logo que sirve hoy el
site `pruebas` es el de Jellyfin. Para probar vale, pero §8 prohíbe usar esa
marca como nuestra, y una ranura de marca es justo donde no debería estar.
Conviene cambiar esa imagen en el registro antes de que ningún ISP la vea.

### #43 y #44 — Dos constantes de sintonía que mentían — 2026-10-05

Las dos salieron de releer `LiveTuning.kt` con la regla 7 delante —«cero
números mágicos, toda la sintonía aquí»—. El problema es que una constante
que nadie usa, o que no hace lo que dice, es **peor** que un número mágico:
el número mágico al menos no promete nada.

**43 — `TARGET_OFFSET_SATELLITE_MS = 12_000L` no lo leía nadie.**
`PlanBuilder.offsetDe()` resuelve `servidor ?: canal ?: TARGET_OFFSET_DEFAULT_MS`,
y ahí no aparece. Quien abriera el archivo se iba creyendo que los canales
satelitales van a 12 s cuando van a 8.

Y el valor, además, es **el equivocado para producción**. Medido el 03-oct:
los streams sin `segment_count` propio —todos menos `mileniosd` y `beisbol`—
sirven 4 segmentos de ~5,5 s, o sea ~22 s de ventana. Media ventana son 11 s.
Se retira.

Lo que ocupa su sitio es la escalera escrita en el KDoc del valor de fábrica,
para no tener que abrir `PlanBuilder` para saber quién manda, y el motivo por
el que una constante no podía ganar nunca: **la ventana depende del
`segment_count` de cada stream**, así que el valor bueno lo calcula el
generador del `config.json` canal por canal desde lo que le dice Flussonic.

**44 — `TARGET_OFFSET_MAX_WINDOW_FRACTION` decía «nunca puede pasar de».**
Pasa perfectamente: el código registra `offset.superaLaVentana` y le entrega a
Media3 lo pedido sin tocarlo.

Lo interesante es que **el código estaba bien y la documentación mal**, no al
revés. Recortar el offset pedido no habría arreglado nada, porque el que rompe
el canal no es el pedido sino el **real**: tras un corte el reproductor retoma
donde estaba, el offset crece solo y no vuelve. De eso ya protege la vuelta al
borde (`LIVE_EDGE_RESYNC_WINDOW_FRACTION`, la misma fracción), que mira el
offset real y salta al directo antes de salirse de la ventana.

Así que el aviso no sobra: lo que compra es **saber de quién es la culpa**. Un
offset pedido por encima del umbral señala un `config.json` mal generado para
ese stream, y eso se arregla en el generador, no en la caja.

Pasa a llamarse `TARGET_OFFSET_WINDOW_WARN_FRACTION`, y con él el local
`ceiling` → `umbral` y la clave `techoMs` → `umbralMs`. Nada de comportamiento
cambia; aplicar el recorte de verdad sería Modo A y hoy nada lo pide.

**La mención histórica del 23-sep no se reescribe**, que es una bitácora: se le
añade entre guiones el nombre de hoy y la aclaración de que no fija nada.

### #39 — El foco no bajaba de la cabecera a la guía — 2026-10-05

Con el foco en «Buscar canal» o en «Diagnóstico», ABAJO no hacía nada. Para
volver a los canales había que ir primero a la izquierda. Es el camino que se
recorre cada vez que se sale del menú de diagnóstico, así que se sufría a
diario.

**La causa no era el orden de los nodos, era la geometría.** La búsqueda de
foco de Compose mira hacia dónde apunta la tecla y busca un candidato **en esa
dirección**. Los dos botones de cabecera están pegados al borde derecho; la
fila de tarjetas más ancha acaba mucho antes. Debajo de ellos no hay nada, así
que no hay a qué ir, y no es un fallo: es la regla funcionando sobre una
disposición que la deja sin candidatos.

**El arreglo es una línea, y aprovecha que las `FocusProperties` se heredan:**
se declara en la fila que contiene a los dos botones, no en cada uno.

```kotlin
.focusProperties { down = if (hayGuia && !buscando) focoGuia else FocusRequester.Default }
```

`hayGuia` no es decoración. Apuntar a un `FocusRequester` que no está atado a
ningún nodo lanza, y la guía no existe cuando la sección está denegada o el
catálogo llegó vacío — que son exactamente los dos estados donde la cabecera
sigue en pantalla.

**Medido en la caja con `uiautomator`, no a ojo**, porque un anillo de foco en
una captura es fácil de leer mal:

```
Buscar canal   [1410,230][1614,326]  → ABAJO → [86,424][442,624]   ← tarjeta 101
Diagnóstico    [1630,230][1824,326]  → ABAJO → [86,424][442,624]   ← tarjeta 101
```

**Lo que se intentó verificar y no se pudo:** la rama `!buscando`. Con la
búsqueda abierta, ARRIBA desde el campo de texto no sube a la cabecera —se
queda en el campo—, así que hoy no hay forma de llegar a esa rama con el mando.
Se deja escrita porque sigue siendo la respuesta correcta si algún día sube, y
queda anotada como pendiente 48.

**Y una trampa del entorno que cuesta media hora cada vez que se olvida:** el
teclado en pantalla de Leanback se traga ARRIBA y ABAJO. La primera tanda de
pulsaciones no llegó nunca a la app; se las comió el IME. `CLAUDE.md` §9 ya lo
avisa para el emulador y vale igual para la caja: `adb shell ime disable <id>`
antes de automatizar nada.

### B7 — La parte de Play que es código, y la que no — 2026-10-05

**Casi todo estaba ya bien**, por ser un fork de una app que se publica. La
auditoría antes de tocar nada:

| | |
|---|---|
| `LEANBACK_LAUNCHER` | ya venía |
| `leanback`, `touchscreen`, `microphone` en `required="false"` | ya venían |
| `targetSdk` 36 | ya estaba |
| `debuggable` en release | ausente, correcto |
| Firma por propiedades, sin clave en el repositorio | correcto |

**Lo que faltaba y se hizo:** `faketouch` como no obligatorio —lo que mantiene
la app elegible para la ficha de TV y para cajas sin digitalizador— y el
`domain-config` que **prohíbe el tráfico en claro hacia el registro**, que
pedía `PETICION_APK` §1. Eso último solo surte efecto desde API 24, así que no
sustituye a `RegistryTrust`: lo complementa justo en las cajas donde Android sí
lee ese archivo.

**Un punto del plan que resultó no existir.** Decía «AAB con `armeabi-v7a` y
`arm64-v8a`», y **no hay nada que configurar**: al irse la extensión FFmpeg se
fue el NDK, y la única biblioteca nativa que queda es
`libandroidx.graphics.path`, que AndroidX reparte en las cuatro arquitecturas.
Verificado sobre el bundle: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`. El
requisito de 64 bits de Play se cumple por construcción.

**El AAB de release compila y es correcto:** 17 MB,
`package=mx.kataliziador.palco`, `targetSdk 36`, sin `debuggable`, con el
icono, el banner y el nombre propios. Sin firmar, que es lo que corresponde:
la clave la pone Edgar.

**Lo que no es código va a `PLAY.md`**, nuevo, y es donde está el trabajo de
verdad que queda: la clave de firma y su custodia —perderla es perder la ficha
para siempre—, la versión real, el formulario de Seguridad de los datos y el
repositorio público que exige la GPL-2.0.

**El formulario de Seguridad de los datos dejó de ser trámite al cerrar B6.**
La app manda `installId`, modelo, inventario de decoders y diagnóstico de
reproducción a un servicio nuestro. Eso se declara, y obliga a una política de
privacidad con URL permanente. `PLAY.md` lleva la tabla de qué se manda y cómo
se declara cada cosa, incluido por qué **no** es actividad del abonado — y que
esa frontera está en el diseño, no en la intención.

**§4.7 gana una sexta categoría:** `res/xml/network_security_config.xml`.
Android obliga a que esa configuración viva fuera del módulo, y lo único que se
le añade es la prohibición de tráfico en claro hacia el registro. La base no se
toca: el resto de la app sigue hablando por HTTP con el Jellyfin y los streams
de la LAN, que es de lo que vive.

**Estado:** cerrada la parte de código. El resto está en `PLAY.md` y es de
Edgar.

### M — El rescate del tunneled pasa a activo de fábrica — 2026-10-05

**Validado en pantalla por Edgar**, que lo miró un buen rato en `estrellashd`
con la simulación puesta y dio el visto bueno. Con eso, `RESCATE_TUNNELED_DEFAULT`
sube a `true`.

**La primera pasada no valió, y conviene que quede escrito por qué.** Edgar
encendió «Fingir tunneled sin imagen» pero dejó «Rescate» apagado. Con esa
combinación **no pasa nada** —`vigilarImagen()` sale en su primera línea— y en
pantalla eso se ve exactamente igual que «funciona bien»: reproducción normal,
sin cortes. Dijo que todo iba bien, y era cierto; lo que no era cierto es que
hubiera probado algo.

Lo cazaron los interruptores guardados y el log:
`degradacion=FINGE SIN IMAGEN rescateTunneled=OFF`, y `tunneled.sinImagen`
cero veces. **Dos estados distintos que se ven igual en la tele es justo lo que
una validación visual no puede distinguir**, y por eso hay que mirar el
instrumento antes de dar por buena la vista. Se le dejaron los dos puestos por
`adb` y repitió.

**La evidencia de la buena**, del acumulador de diagnóstico y no del logcat
—que se había rotado, pendiente 25—:

```
sesiones=7  conImagen=7
tunPedido=7  tunRescate=1
minutos=6   media al primer fotograma 2044 ms   rebuffers=14
fallos={"INTERNO":2}
```

Saltó una vez —la única sesión que tuvo el rescate armado— y **las siete
sesiones acabaron con imagen**.

**Dos cosas de regalo en esos números.** La primera: `INTERNO: 2` en siete
sesiones es un 29 %, que cae dentro del 5–30 % que el pendiente 37 tiene
medido para la aserción del tunneled; es corroboración independiente, recogida
sin querer. La segunda: el acumulador de B6 sirvió para cerrar una fase de
Modo A **el día después de escribirlo**, y lo hizo porque sobrevive a la
rotación del logcat, que es lo que el instrumento de siempre no hizo.

**Qué cambia en producción:** el fallo más caro que tenía el primer envío a
Play —una ZTE con el tunneled roto, pantalla negra con sonido, en todos los
canales— pasa de permanente y mudo a un corte de ocho segundos que se cura
solo, sin esperar a saber qué modelo falla. Sigue siendo mejor excluir el
modelo por `deviceOverrides` en cuanto se sepa: el rescate cuesta un rebuffer
en cada sintonía de esa caja.

**Estado:** cerrada.

### B6: la caja cuenta lo que es, montado en la petición que ya hacía — 2026-10-05

**El problema que resuelve es de secuencia, no de telemetría.** La ZTE B866V2
solo se instala desde la ficha de Play, así que la primera vez que la veamos
será con la app publicada. Si ese primer APK no sabe informar, lo que llega es
una llamada diciendo «no funciona» y seguimos ciegos: el envío se gasta sin
aprender nada.

**Dónde viaja, y por qué ahí.** Montado en la consulta al registro, **no en un
servicio nuevo**. Eso respeta la regla 6 del autorizador —nada más puede
depender de él— porque no añade una dependencia: es un campo más en una
conversación que ya existía, y si se pierde no pasa nada. Y la cadencia encaja
con lo que hace falta, que no es telemetría continua sino **un informe por
modelo de caja**.

**Qué manda:** el inventario de decoders de vídeo con `hw`/`sw`, las ABIs, la
RAM, el modelo; y un resumen acumulado de reproducción —sesiones, cuántas
dieron imagen, ms al primer fotograma, rebuffers, si el tunneled hizo falta
rescatarlo, fallos por causa y por canal.

**La frontera con los datos de abonado está en el diseño, no en la intención**
(`CLAUDE.md` §8): se cuentan los canales que **fallaron**, sin marcas de
tiempo, sin orden y sin duraciones individuales. De ahí no se reconstruye un
historial de visionado. Acumulativo y **nunca se vacía al enviarlo**: vaciar en
la caja perdería lo acumulado cada vez que falle un envío, justo en las cajas
con mala conexión, que son las que más interesa medir. Y acotado —12 causas, 40
canales—, porque sin tope una caja con el headend roto acabaría mandando
kilobytes en cada consulta.

**Medido en la caja**, con un registro de pruebas que enseña lo que recibe. La
Skyworth mandó 27 decoders de vídeo y `sesiones=1 conImagen=1
msPrimerFotogramaMedia=2599 rebuffers=2 tunneledPedido=1 tunneledRescatado=0`.

**Y en la primera corrida ya corrigió un pendiente registrado.** El 26 decía
que esta caja no tiene decoder de vídeo por software para H.264 **ni** MPEG-2.
El inventario enseña `OMX.google.h264.decoder` para `video/avc` marcado como
software —y en API 30 eso lo dice `isHardwareAccelerated()`, que es la API y no
una heurística de nombres—. Para MPEG-2 el pendiente sigue siendo cierto: ahí
el único decoder es el de Amlogic, por hardware.

Consecuencia: **el paso 2 de la cascada sí es alcanzable para H.264** en esta
caja, y el cierre degradado de M3 no depende de libVLC para los cinco canales
H.264, solo para el mp2v. Con la salvedad que el propio código ya advierte:
declarado no es funcionando.

**Eso es exactamente para lo que se hizo B6**, y lo hizo el primer día, sobre
la caja que sí podemos medir. El valor real llega cuando lo haga una ZTE.

**Lo que queda fuera de este bloque:** la capa de datos de abonado, que va
detrás de un interruptor por proveedor y con contrato. No se toca hasta que
haya contrato.

**Resultado:** el contrato del registro gana dos campos, compatibles hacia
atrás, y `INTEGRACION_REGISTRO.md` explica qué hacer con ellos — guardar una
fila por modelo es lo que alimenta la tabla de `deviceOverrides`.

**Estado:** cerrada la parte de diagnóstico técnico.

### Los certificados, medidos en una caja de API 22 — 2026-10-05

**Primero se comprobó la premisa, que llevaba meses escrita sin verificar.** Un
emulador de Android TV API 22 —Android 5.1.1— tiene **162 certificados en su
almacén y ninguno de ISRG**. Sí tiene DST Root CA X3, que es la raíz vieja que
Let's Encrypt dejó de usar. O sea: el pendiente 35 era cierto.

**Cómo se pudo medir:** el contenedor LXC no tenía `/dev/kvm`. Edgar lo expuso
desde el Proxmox —dos líneas en `103.conf` y `chmod 0666` en el host, que hacía
falta por ser un contenedor no privilegiado— y a partir de ahí el emulador corre
a velocidad casi nativa. Queda anotado en `CLAUDE.md` §9.

**Cambio:** `RegistryTrust` empaqueta cuatro raíces —X1, X2, Root YR e YE— y
construye un `TrustManager` que acepta si valida **el del sistema o el de las
incluidas**, y rechaza si ninguno. Se aplica **solo a la conexión con el
registro**, nunca a la confianza global de la app.

Las cuatro y no solo X1: hoy la cadena del registro acaba en X1
(`YR2 → Root YR → ISRG Root X1`), pero el día que Let's Encrypt deje de servir
esa firma cruzada X1 ya no bastará, y hasta cajas modernas sin Root YR
fallarían. Lo avisó el proyecto del autorizador tras su primer despliegue.

**La prueba A/B, en el mismo emulador y contra el registro de producción:**

```
SIN el arreglo
  registro.sinRespuesta  motivo=CertPathValidatorException:
                                Trust anchor for certification path not found.
  catalogo.destino  procedencia=derivada de Jellyfin
  catalogo.listo    marcaOrigen=neutra

CON el arreglo
  registro.respuesta  autorizado=true  channelsUrl=…  marca=pruebas
  registro.acceso     resultado=permitido motivo=autorizado por el registro
  catalogo.destino    procedencia=registro
  catalogo.listo      marcaOrigen=registro
```

**Y lo que más vale de esta medición no es el arreglo.** Mírese la corrida sin
él: la caja **entró igual**, por `motivo=sin respuesta y sin autorización
previa`. Esa fila de la escalera de degradación se añadió ayer, al implementar
B9, porque el contrato no la tenía. Sin ella, esta caja de API 22 se habría
quedado fuera de TV en vivo en vez de degradarse. La decisión de diseño se
validó sola un día después.

**Cinco pruebas de JVM** cubren lo que no necesita dispositivo, que es donde más
fácil se rompe: que las raíces sean las correctas, que la cadena real valide, y
—la que importa— que **un autofirmado con el nombre del registro siga siendo
rechazado**. Si esa deja de fallar, es que el `TrustManager` se volvió
permisivo, y eso abriría la puerta a que cualquiera en la red del ISP cambie el
`channelsUrl` de un site.

Dos notas de herramientas: `shouldThrow` y `shouldBeInstanceOf` de kotest no se
pueden usar —vienen compiladas para JVM 11 y el módulo va a 1.8—, y generar un
autofirmado con las clases internas del JDK dejó de ser posible en Java 17, así
que es un PEM capturado.

**Lo que queda, y es cosmético:** el logo de marca del registro **no carga** en
API 22, porque lo pide Coil con OkHttp. La autorización entra y el logo no —cae
al nombre, que es su respaldo diseñado—. **No se arregla tocando el
`ImageLoader` global**, que es el mismo del VOD: ampliar la confianza de todas
las imágenes para arreglar un logo es mal negocio. Pendiente 47.

**Resultado:** pendiente 35 cerrado con medición, no con teoría.

**Estado:** cerrada.

### B9, segunda mitad: contra el registro de verdad — 2026-10-04

**Lo primero fue mirar qué contesta.** El registro lleva en producción desde
ayer, y resultó que **ya tiene dado de alta el Jellyfin de pruebas**, así que
esto no se probó contra una imitación: se probó contra el servicio.

La respuesta real está metida tal cual como prueba de regresión. Una prueba
escrita a mano comprueba lo que yo creo que manda el registro; ésa comprueba lo
que manda.

**Hecho:** `RegistryRepository` con `HttpURLConnection` y sin dependencias
nuevas, caché por host en disco, `installId` opaco por instalación,
`BuildConfig.REGISTRY_URL`, override por preferencias, la pantalla de sección
cerrada y el enganche en el catálogo.

**El registro precede a la convención del puerto, no la sustituye.** El orden
del destino queda: menú → registro → derivación → `BuildConfig`. Y la marca del
registro pisa a la del site.

**Medido en la caja, contra producción:**

```
registro.respuesta host=192.168.20.249 autorizado=true hasta=… channelsUrl=… marca=pruebas
registro.acceso    host=192.168.20.249 resultado=permitido motivo=autorizado por el registro
catalogo.destino   url=http://192.168.20.249:8097/config.json procedencia=registro
catalogo.listo     canales=10 marca=pruebas marcaOrigen=registro
```

En pantalla: el logotipo del registro sustituye al nombre y los títulos de grupo
salen en su ámbar. El TLS de `authjelly.satmx.net` funciona en Android 11 sin
tocar nada —lo que **no** dice nada de las cajas por debajo de 7.1.1, que es el
pendiente 35.

**Y a la segunda entrada no volvió a preguntar:** `registro.vigente`. Una
consulta por site, no por arranque.

**Un fallo pequeño del tipo que cuesta horas.** `catalogo.listo` registraba la
marca **del site** mientras la pantalla pintaba la **del registro**: decía
`marcaOrigen=neutra` con el logotipo del registro delante. Un log que informa de
otra cosa que la pantalla manda a buscar donde no es. Ahora registra la
efectiva.

**Algo que hizo falta y no estaba previsto:** «Olvidar la autorización
guardada», en el menú. Mientras la guardada siga viva la app no vuelve a
preguntar —que es lo correcto—, pero eso hace imposible probar la denegación, y
en operación obliga a un técnico que acaba de dar de alta un site a esperar
semanas. Apareció al intentar probar; se queda porque sirve para las dos cosas.

**Lo que no se vio:** la pantalla de denegación. Su lógica tiene nueve pruebas y
el camino autorizado está medido, pero nadie ha mirado el `SeccionCerrada` en
una tele. Pendiente 46.

**De regalo:** se vio el icono de Palco en el lanzador de Google TV, que era lo
que quedó sin comprobar al renombrar la app. El aro con el antepecho se lee a
ese tamaño.

**Resultado:** pendiente 40 cerrado. Queda el trabajo de certificados (35) y la
pantalla por mirar (46).

**Estado:** cerrada, salvo lo que exige una tele y un emulador.

### B9, primera mitad: la decisión, antes que la red — 2026-10-04

**Por qué B9 y por qué ahora.** B6 —el diagnóstico, que tiene que viajar en el
primer APK que pise una ZTE— va montado sobre la petición al registro, que es
la conversación que la caja ya tiene con nosotros. Así que el registro va
primero.

**Qué se hizo, y qué deliberadamente no.** Solo lo que se puede probar sin red
y sin caja: la normalización del host, la interpretación de la respuesta y la
escalera de degradación. El cliente HTTP, la caché y el enganche en la sección
quedan para la segunda mitad.

No es por prudencia: **es que la parte peligrosa es ésta**. Un cliente HTTP que
falla se ve enseguida; una escalera de degradación mal hecha deja entrar a quien
no debe, o —mucho peor— apaga televisiones de quien sí paga, y eso no se nota
hasta que alguien llama.

**Un hueco del contrato que salió al implementarlo.** La tabla de degradación no
tenía fila para «nunca contestó y no hay autorización previa»: una caja recién
instalada que alcanza la red de su ISP pero no la nube. Es exactamente el
despliegue que `REGISTRY_SCHEMA.md` §6 nombra como el que algún día exigiría
firmar la autorización dentro del `config.json`. Si ahí se cerrara, ese
escenario estaría muerto desde el primer día, y un corte de internet del ISP
dejaría sin televisión a cajas que nunca llegaron a preguntar.

**Se entra, se avisa y se registra.** Encaja con lo que esto es: un interruptor
operativo sobre gente que coopera, no un candado. La fila está añadida al
contrato y al documento de integración, en este mismo commit.

**Decisiones de parser, todas en la misma dirección:** prefiere descartar a
guardar algo a medias. Sin `authorized`, se descarta; autorizada sin `until`
utilizable, se descarta. Una autorización sin caducidad es eterna, y bastaría
un campo que el registro se deje para dejar una caja autorizada para siempre.
Descartar es recuperable; guardar, no.

Es la regla opuesta a la del aviso del operador, donde una fecha ilegible se
trata como «sin caducidad» porque perder el aviso es peor. La diferencia es que
uno es un texto y el otro es una llave.

**La gracia: 14 días**, sobre la ventana de semanas que ya emite el registro.
Generoso a propósito — cortarle a un cliente que paga por un problema de red
nuestro es peor que tardar en cortarle a uno que no paga.

**Prueba:** 38 casos en tres archivos, sin levantar nada. Incluye que una
denegación guardada no autorice mientras dure su fecha, que es el error que
`vigente()` evitaría mal si solo mirase `untilEpochMs`.

Las pruebas de kotest con `shouldThrow` y `shouldBeInstanceOf` no se pueden
usar: vienen compiladas para JVM 11 y el módulo va a 1.8. El archivo del parser
del `config.json` ya llevaba esa nota desde antes; no se toca el `jvmTarget` por
esto.

**Resultado:** pendiente 41 cerrado. Queda la segunda mitad: cliente HTTP con
los 10 s pedidos, caché por host, override del menú, y el enganche en la entrada
a la sección.

**Estado:** cerrada la primera mitad.

### El único fallo que el vigilante no podía ver — 2026-10-04

**Por qué ahora.** La ZTE B866V2 —la caja mayoritaria del cliente— **solo
admite instalaciones desde la ficha pública de Play**. O sea: la primera vez
que la veamos será con la app ya publicada. Y el tunneled está activo de
fábrica, con un modelo de exclusión por modelo, o sea por conocimiento que de
esa caja todavía no tenemos.

**Síntoma que eso puede producir:** si la ZTE implementa mal el tunneled, el
audio suena, la posición avanza y la pantalla está negra. En todos los canales
y en la caja que más abonados tiene.

**Lo que se encontró revisando:** el vigilante de recuperación **no lo ve**. Su
condición de «atascado» es que la posición no avance; aquí avanza con
normalidad. Desde dentro del reproductor todo va bien.

**Lo que sí lo ve:** `onRenderedFirstFrame`, que **llega en modo tunneled** —lo
que se queda ciego ahí es el contador de fotogramas y `SurfaceFlinger`, que es
otra cosa (§6)—. Reproduciendo, con la posición avanzando y sin primer
fotograma a los 8 s, el decodificador nunca dio una imagen.

**Cambio:** `vigilarImagen()`, junto al vigilante de progreso. Apaga el
tunneled **para esa sesión** y rehace la preparación, una sola vez. El tunneled
efectivo pasa a ser una variable de sesión (`tunnelingSesion`), no
`dev.tunnelingEnabled`, porque a partir del rescate ya no coinciden.

Dos interruptores nuevos en el menú: el rescate —**apagado de fábrica**, como
pide §3 para todo cambio de comportamiento del motor— y «Fingir tunneled sin
imagen», que es la única forma de verlo en una caja que sí da imagen (§9,
simular cajas peores).

**Medido en la caja**, con la simulación encendida:

```
frames.first msDesdePrepare=2478
latido n=2 … frames=0  fps=0.0      ← tunneled: el contador no ve nada
latido n=3 … frames=0  fps=0.0
tunneled.sinImagen modelo=SkyworthDigital 4K Google TV Box msSinImagen=8179 simulado=true
latido n=4 … estado=almacenando     ← la nueva preparación
latido n=5 … frames=37              ← el contador empieza a ver
latido n=7 … frames=121 fps=57.8
```

**Que el contador pase de 0 a ~60 fps es la prueba independiente de que el
tunneled se apagó de verdad**, no solo de que lo registramos: en modo tunneled
ese contador lee cero, y en esta misma sesión leyó cero durante los primeros
ocho segundos. Costo: un rebuffer.

**Lo que no se puede dar por bueno todavía.** La captura de pantalla sale en
blanco antes y después, así que **no demuestra que la imagen esté ahí**. Es
justo el aviso de §6: antes de concluir algo de un modo, hay que comprobar que
el instrumento podría verlo. Esto es Modo A y se juzga mirando la tele.

Y un hueco de la simulación: `tunneled.rescatado` no se registra con la
simulación encendida, porque `firstFrameAtMs` ya era mayor que cero. En el caso
real sí saldría. Lo que se pierde es la confirmación de que el rescate sirvió,
no el rescate.

**Pendiente 45:** validarlo en pantalla y, si vale, **subir el valor de fábrica
a activo antes del primer envío a Play**.

**Estado:** abierta, a la espera de Edgar.

### La lista se quedaba congelada hasta reiniciar la app — 2026-10-04

**Síntoma, encontrado por el proyecto del servidor de canales** probándolo en un
site: se edita el lineup, el servicio publica el nuevo, y la caja sigue
enseñando el anterior — incluso saliendo de «TV en vivo» y volviendo a entrar.
Solo al cerrar y abrir la app aparece.

**Causa:** `LiveTvCatalog.refreshOnce()` salía antes si la URL no había
cambiado, y el catálogo es una instancia única. O sea: **una descarga por
proceso**. Una caja de salón tiene la app viva días.

**Y `INTEGRACION_CANALES.md` decía «una vez por entrada a la sección».** No era
una simplificación: era falso, y es exactamente el fallo contra el que avisa
`CLAUDE.md` §1 —un documento de integración desfasado se cree—. El otro proyecto
lo leyó, lo probó y se dio con ello.

**Cambio:** `refreshSiHaceFalta()` baja la lista si la caja cambió de servidor o
si la que tiene lleva más de `CONFIG_REFRESH_INTERVAL_MS` (15 min), y la sección
abierta reintenta con esa misma cadencia. El refresco por tiempo **no enseña
«actualizando…»**: quien mira la guía no pidió nada.

**Dos intentos equivocados antes del bueno**, y los dos los cazó la caja:

1. **Tomar el foco una sola vez.** La idea era que un refresco no le moviera el
   foco a quien estuviera mirando el canal 214. Resultado: la sección se abría
   con el foco en la barra de arriba. El catálogo llega en **dos pasos**
   —arranque o caché, y después servidor—, y al sustituirse las tarjetas el
   foco se pierde; la petición repetida era lo que lo recuperaba.
2. **Marcar el foco como tomado solo si `requestFocus()` no lanzaba.** Mismo
   resultado: el intento tiene éxito sobre la lista de arranque, y el foco se
   pierde después, cuando llega la del servidor.

   Se comprobó contra `8ffaf90` que en HEAD el foco sí entraba: **era una
   regresión mía**, no un defecto viejo.

**Lo que funciona:** no re-emitir un catálogo idéntico. `LiveCatalog` es `data
class` hasta el fondo, así que comparar es barato, y el caso normal —«no cambió
nada»— pasa a costar cero recomposiciones. El foco solo se mueve cuando la
lista cambió de verdad, que es cuando tiene sentido.

**Prueba**, con el intervalo bajado a 20 s para no esperar un cuarto de hora, y
restituido a 15 min después: con el foco en el canal 103, dos refrescos sin
cambios lo dejan donde estaba; al quitar un canal y publicar un aviso desde el
servidor, la caja los enseña **sin tocarla** y el foco vuelve al primer canal.

**Resultado:** pendiente 42 abierto —cuánto debe esperar el servidor antes de
retirar un canal caído— y pendiente 39, un atasco de foco anterior que salió al
probar: de «Buscar canal» o «Diagnóstico» no se baja a la guía.

**Estado:** cerrada.

### B5 — La app se llama Palco, y el paquete era lo urgente — 2026-10-03

**Síntoma:** la primera pantalla de toda instalación —selección de servidor y
usuario— llevaba el logotipo y el nombre de Jellyfin, que §8 prohíbe. Edgar
eligió el nombre: **Palco**.

**Pero el hallazgo que importaba no era el nombre.** `app/build.gradle.kts`
tenía `applicationId = namespace`, o sea `org.jellyfin.androidtv`: el paquete
del upstream, **ya publicado en Play por el proyecto Jellyfin**. Los nombres de
paquete son únicos en toda la tienda y se fijan para siempre en el primer
envío, así que la APK no se podía subir — y en una caja que tuviera la Jellyfin
de verdad, las dos chocan.

Pasa a `mx.kataliziador.palco`. El `namespace` de Gradle **no** cambia: es el
paquete Kotlin de ~2.000 archivos, sólo vive en compilación, y renombrarlo
habría hecho ingobernable el rebase contra upstream sin que nadie lo viera.

**Una trampa que se vio leyendo, no fallando:** las `authorities` de los tres
`provider` del manifest se declaran con `${applicationId}`, pero los
`resValue` de `app_search_suggest_authority` se derivaban del `namespace`. Con
el paquete cambiado, las sugerencias de búsqueda habrían consultado un
proveedor inexistente. Ahora todos salen de `appId`.

**Cambio de marca**, todo en vector y sin un solo PNG:

- `app_logo`, `app_icon_*`, `app_banner_*` y un `mipmap/app_icon.xml` para las
  cajas de API 21–25, que no entienden `adaptive-icon`;
- fuera los diez PNG de la marca anterior, más `ic_jellyfin` y
  `qr_jellyfin_docs`, que ya no usaba nadie;
- el símbolo es un palco: arco de la boca del escenario y antepecho. La palabra
  se construye con círculos y rectángulos, así que **no hace falta licenciar ni
  empaquetar una tipografía**;
- monocromo a propósito: es la marca *neutra*, y tiene que convivir con el
  acento que cada ISP ponga encima sin competir con él.

**El primer intento se leía como un candado.** El barandal no sobresalía del
arco, así que los dos trazos formaban una silueta cerrada. Se alargó hasta volar
por fuera en ambos lados y pasó a leerse como un antepecho.

**Y los textos en español no estaban donde los busqué:** cambié `values/` y la
caja siguió diciendo «¡Bienvenido a Jellyfin!», porque está en español y lo que
se ve son `values-es/` y `values-b+es+419/`. Cuatro cadenas por archivo.

**Lo que NO se cambió, y es deliberado:** todo lo que habla del **servidor**
—«este servidor usa Jellyfin %1$s», «abra Jellyfin en un navegador para
terminar la configuración», el informe de perfil—. §8 prohíbe el nombre como
marca nuestra, no nombrar el software que el abonado tiene delante. Cambiarlo
dejaría los mensajes de error inservibles para quien instala el site.

**Prueba:** en la caja, desinstalado el paquete viejo e instalado el nuevo.
Arranque con el logotipo de Palco, «Te damos la bienvenida a Palco», la ficha
del sistema diciendo «Palco Debug / mx.kataliziador.palco.debug», y sesión
iniciada de nuevo contra el Jellyfin de pruebas sin incidencias.

**Lo que no pude ver:** el icono enmascarado en el lanzador. Google TV no lista
las apps instaladas al margen de Play en «Tus apps», así que el icono adaptativo
está verificado como vector y como recurso, no en pantalla.

**Resultado:** pendiente 38 cerrado. **Queda comprobar que «Palco» esté libre**
en IMPI (clases 38 y 41) y en la Play Console: está elegido, no verificado.

**Estado:** cerrada, salvo la comprobación del nombre.

### B2 — Buscar un canal entre ciento veinte — 2026-10-03

**Síntoma:** la sección tenía marcado por número y nada más. `aliases` llevaba
en el contrato desde el 02-oct, se interpretaba, y **no lo usaba nadie**: el
campo existía para una búsqueda que no existía.

El número sirve a quien ya se lo sabe. Quien no, con 120 canales y un mando,
no tiene salida.

**Cambio:**

- `ChannelSearch` en el módulo, no en la pantalla. Es la pieza que **se puede
  equivocar en silencio** —un orden mal elegido deja el acierto en tercer
  lugar y nadie lo nota— y ahí se prueba sin caja: 11 pruebas;
- plegado agresivo: acentos, mayúsculas, signos **y espacios** fuera. Así
  «tnt» encuentra «T.N.T.» y «canal 5» encuentra «Canal-5», sin que el
  operador tenga que duplicar nombres por la puntuación;
- `BarraDeBusqueda` en `ui/livetv/`, que filtra mientras se escribe y sustituye
  la guía entera por una fila de «Resultados».

**Tres cosas que solo se vieron en la caja**, y ninguna se habría visto
compilando:

1. **«canal 2» enseñaba primero «Canal 22».** El alias de «Estrellas HD» *es*
   «canal 2», y perdía contra un nombre que solo empieza igual. Se añadió un
   escalón: **lo exacto gana a lo que empieza igual**, aunque sea un alias.
2. **ABAJO no salía del campo de texto.** `BasicTextField` se queda las teclas
   de dirección aunque sea de una sola línea. Los resultados se veían y no se
   podían tocar, que es peor que no tener búsqueda.
3. **ATRÁS cerraba la búsqueda entera a la primera**, con el teclado del
   sistema tapando media pantalla: no había forma de llegar a los resultados.
   Ahora la primera baja el teclado y la segunda cierra. Y desde una tarjeta de
   resultados, un `BackHandler` devuelve a la guía en vez de echar al abonado
   de TV en vivo.

**Un detalle de foco que costó una vuelta:** con `moveFocus(Down)` el foco
aterrizaba en la **segunda** tarjeta, porque el campo ocupa el ancho entero y
su centro geométrico cae ahí. Se resolvió con un contador —`enfocar`— que la
guía observa: mientras se escribe cede el foco, y al pulsar ABAJO lo recupera
en el primer resultado.

**Prueba:** en la caja, con diez canales y alias. «hbo» → 301 y 302 en ese
orden; «canal 2» → «Estrellas HD» antes que «Canal 22»; «milenio» sin tilde
encuentra «Milenio Televisión»; «futbol» enseña «Ningún canal coincide con
«futbol»»; ATRÁS desde los resultados devuelve la guía con el foco en el primer
canal.

**Resultado:** `aliases` pasa de «interpretado» a «implementado» en
`CONFIG_SCHEMA.md`, con las reglas de casado escritas para quien los genera.

**Estado:** cerrada.

### B4 — Los `extras`, antes de que llegue el primer dato extra — 2026-10-03

**Síntoma:** `CONFIG_SCHEMA.md` llevaba los `extras` como «acordado» desde que
se escribió, y el parser no los miraba. Un ISP podía emitirlos y no pasaba nada.

**Por qué ahora y no cuando hagan falta:** porque entonces ya es tarde. El
contrato solo crece hacia atrás compatible, así que el día que un ISP mande su
primer `extras` la APK tiene que saber leerlo **ya instalada**; implementarlo
después significa que ese primer dato costó una versión y una ronda de Play
para todos los sites.

**Cambio:**

- `ChannelExtra` en el módulo, con `extras` en el canal **y en la raíz**;
- topes en `LiveTuning` —`EXTRAS_MAX_ENTRIES` 6, `EXTRAS_MAX_LABEL_CHARS` 24,
  `EXTRAS_MAX_VALUE_CHARS` 40— porque sin ellos **un `config.json` decide
  cuánto ocupa la barra**, y la barra vive encima del vídeo;
- recorte silencioso con `config.extrasRecortados`, y la entrada sin `label` o
  sin `value` descartada sola. Igual que un canal mal formado: no tira nada;
- `DatosExtra`, que reusa el idioma visual de «Luego …» en vez de inventar un
  componente. La app no entiende estos datos, así que **no puede
  jerarquizarlos**: no le toca darles un estilo propio.

**Dónde se dibujan, y dónde no.** Los del canal, en la barra bajo «Luego»; los
de la raíz, en la cabecera de la sección bajo el nombre del proveedor. **Las
tarjetas de la guía no los enseñan**: con 120 canales una tarjeta tiene que
leerse de un vistazo, y ahí es donde un `extras` descuidado haría más daño.

**Prueba:** en la caja, con un `config.json` propio que incluye una entrada sin
etiqueta y otra con 200 caracteres en los dos campos. Log:
`config.extrasRecortados de=estrellashd recibidos=5 publicados=4`. En pantalla,
la cabecera enseña «Plan Digital Total — Soporte 800 123 4567» y la barra
«Calidad HD — Región Norte — Satélite 77W», con el par abusivo cortado a 24/40
y pasando a la segunda línea sin empujar nada.

**Resultado:** 7 pruebas nuevas del parser. `CONFIG_SCHEMA.md` e
`INTEGRACION_CANALES.md` actualizados en el mismo commit, como pide la regla de
sincronía. De paso, `brand` estaba marcado «acordado» en el esquema y lleva
implementado desde el 02-oct.

**Estado:** cerrada.

### B5 — Avisos legales, y la app deja de mandar al abonado a Jellyfin — 2026-10-03

**Síntoma:** tres cosas que un abonado de un ISP se encontraba y no debería.

La pantalla de información decía «Jellyfin app version» con el logo del
proyecto, y no decía en ninguna parte que esto es un fork GPL-2.0 ni que el
proyecto original no lo respalda. §8 pide las dos cosas y **no son estética:
son lo que hace legal el envío a Play.**

La ayuda del inicio de sesión mostraba un QR a `docs.jellyfin.org` y el texto
«para usar esta app necesitas un servidor». A quien contrató televisión por
cable eso no le dice nada: no va a montarse un servidor, va a pedirle la
dirección a su proveedor.

Y la fila de TV en vivo del inicio solo se añadía si el usuario tenía
`enableLiveTvAccess` (#31) — un permiso del Jellyfin del ISP, sobre *su* LiveTV,
que no usamos.

**Cambio**, todo Modo B:

- `about.kt`: dos entradas nuevas —«Avisos legales» con el «no afiliado» y
  «Licencias del software» con la GPL-2.0—; la fila de versión pasa a
  «Versión de la aplicación» con icono neutro, **conservando el identificador
  técnico `jellyfin-androidtv <versión>` en el contenido**, que es lo que hace
  falta para diagnosticar y lo que la GPL-2.0 exige reconocer;
- `ConnectHelpAlertFragment.kt`: fuera el QR, y el texto reescrito a «Para ver,
  esta aplicación necesita la dirección del servidor de tu proveedor. Pídesela
  a él.» en los tres `values*/`;
- `HomeRowsFragment.kt`: `HomeSectionType.LIVE_TV -> rows.add(liveTVRow)`, sin
  condición.

Esto abre una **cuarta categoría permitida** en la verificación de §4.7 —marca
y avisos legales— y una quinta para `herramientas/` y `headend/`, que no se
compilan. Las dos quedan escritas ahí, como pide la regla. Ninguna toca lógica:
son texto e iconos en pantallas del upstream, y la comprobación (b) sigue
saliendo con la única excepción registrada.

**Prueba:** en la caja, las cinco filas de «Acerca de» se leen enteras —la de
licencias hubo que acortarla porque el resumen se truncaba a cuatro líneas—, la
pantalla de ayuda sale sin QR con el texto nuevo, y la fila «TV en vivo» del
inicio aparece con su botón de guía.

**Lo que no se puede dar por probado:** el usuario de la caja **tiene**
`enableLiveTvAccess`, así que la fila habría salido igual. El cambio solo
retira una condición.

**Resultado:** la app ya no usa el nombre ni el logo de Jellyfin en la
información ni manda al abonado a la documentación del proyecto. **Sigue
usándolos en la primera pantalla de todas** —selección de usuario y servidor—,
que es el pendiente 38 y está bloqueado en el nombre de producto.

**Estado:** cerrada en lo que no depende de la marca.

### Tres hallazgos del servicio de canales que desmienten este repositorio — 2026-10-03

Medidos allí contra el Flussonic de producción —44 streams vivos, no los seis de
desarrollo— y corregidos aquí, porque los contratos los escribe este proyecto.

**La mayoría del lineup real es ABR.** Los seis de desarrollo declaran un solo
`EXT-X-STREAM-INF`; la mayoría de los de producción usan plantillas `*-ABR` de
Flussonic y traen **dos calidades**, 576p y 720p. Publicados tal cual, la app
haría ABR, que §6 prohíbe. La salida no es recompilar ni tocar el headend: el
generador publica `index.m3u8?filter=tracks:v2a1a2`, que devuelve un master de
una calidad con todos los audios.

**Esto sube el valor del guardia que se añadió el 02-oct.** La app no puede
impedir el ABR, solo verlo: `abr.variasVariantes` es el único rastro si el
generador se olvida del filtro. Cuando se escribió, los seis canales lo hacían
parecer teórico.

**La ventana DVR de producción es de ~22 s, no de ~30.** Los streams sin
`segment_count` propio —todos menos `mileniosd` y `beisbol`— sirven 4 segmentos
de ~5,5 s. `CLAUDE.md` decía que con `segment_count 12` la ventana rondaba los
30 s y que «8000–12000 va holgado»: **12000 pasaría de la mitad de la ventana**
y rompería el canal al primer chubasco. El valor de fábrica, 8000, sigue bien.

**`foxny` entrega audio MP2 en la salida**, sin plantilla de transcodificación.
Es el primer caso real de lo que §2 describe como la avería silenciosa: perfecto
en la caja de pruebas, mudo en una caja sin decoder de MP2. El aviso
`audio.noNormalizado` existe justo para esto.

Corregidos `CLAUDE.md` §6, `CONFIG_SCHEMA.md` e `INTEGRACION_CANALES.md`.

**Estado:** cerrada.

### Auditoría: 73 pruebas, un fallo real del motor y dos derivas de documento — 2026-10-03

Edgar pidió verificar que todo esté documentado y que todo funcione. Lo que
salió:

#### El mecanismo de sincronía se ganó el sueldo

La copia del contrato en `servidor-canales` **se había quedado atrás**: decía
`ed0999e` y `CONFIG_SCHEMA.md` iba por `28a831d`. Se habría desarrollado el
miniservicio contra un contrato viejo sin que nadie lo notara.

Se vuelven a copiar las dos, y la operación pasa a ser un script en el
repositorio —`herramientas/sincronizar-contratos.sh`—, porque **la deriva
ocurrió justo por ser manual**.

#### Documentos que afirmaban lo que el código ya desmiente

Verificado campo a campo contra el parser, no de memoria:

| Decía | Es |
|---|---|
| `logoUrl` «todavía no se dibuja» | se dibuja en guía y barra |
| `audioDefault` «no elige nada» | elige al sintonizar, activo de fábrica |
| §4.7 «lo que aparezca aquí son los enganches de navegación» | también archivos de compilación y la excepción de `deviceProfile.kt` |

Corregidos en `CONFIG_SCHEMA.md`, `INTEGRACION_CANALES.md` y `CLAUDE.md`, y
cerrados los pendientes 32 y 33, que ya estaban hechos.

#### Un fallo real del motor, y lo que se pudo arreglar de él

```
player.error errorCode=ERROR_CODE_FAILED_RUNTIME_CHECK
Caused by: java.lang.IllegalStateException
  at MediaCodecVideoRenderer.onEnabled(MediaCodecVideoRenderer.java:845)
```

Esa aserción es la del **modo tunneled**. Medido:

| | sintonías | errores |
|---|---|---|
| tunneled activo | 21 · 17 · 6 | 1 · 1 · 2 |
| tunneled apagado | 18 | **0** |

**Siempre se recupera**: el canal acaba reproduciendo, y en todas las corridas
el número de `player.playing` cubre todas las sintonías.

**Lo que sí se arregló, porque era nuestro:** la pantalla decía «Señal débil por
condiciones climáticas» ante **cualquier** error recuperable, incluida una
aserción interna de Media3. Eso manda a un técnico a revisar una antena que está
perfecta. Ahora el motor clasifica la causa —entrada/salida y analizado son
señal; decodificador y aserciones, no— y lo interno dice «Reconectando…». Con
pruebas de la regla.

**Lo que no se tocó:** la causa raíz está dentro de la configuración de tunneled
de Media3, y es Modo A sobre la función que `CLAUDE.md` §6 describe como «la más
dependiente del fabricante de toda la pila», validada en pantalla dos veces por
Edgar. No se cambia en remoto. Pendiente #37.

#### Dos conclusiones mías que tuve que retirar sobre la marcha

**«Falla siempre en `beisbol`»** — 3 de 3 en las primeras corridas. Era **sesgo
de muestreo**: el zapeo automático llegaba a beisbol siempre en el mismo punto.
Con transiciones variadas falla en canales distintos y en las dos direcciones de
resolución. Y beisbol **en frío**, sin canal anterior, no falla nunca.

**«Hay un `release` espurio por sintonía»** — salía de mi propia línea de log
recién añadida. `prepare()` llama a `release()` antes de construir, así que
salen dos por sintonía y es correcto. Lo comprobé antes de escribirlo como
hallazgo; el comentario del código ahora dice cuál es el patrón sano, para que
el siguiente no repita la persecución.

**Sin fuga de reproductores:** 16 `prepare`, 31 `release`, y la memoria sube
**3 MB en 15 zapeos**.

#### Estado general

73 pruebas pasan. Compilan depuración y release. Las dos verificaciones de §4.7
salen como deben. Los seis canales resuelven `media3 / HW`, arrancan en español
y ninguno dispara avisos de audio sin normalizar.

**Estado:** cerrada.

### [B4] La sección se parece al resto de la app — 2026-10-03

Edgar pidió «un diseño moderno y acorde a la demás interfaz». Eso tiene una
respuesta medible, no de gusto: `CLAUDE.md` §4 regla 2 dice que la sección usa
`JellyfinTheme` y los componentes de `ui/base/` **porque tiene que verse como el
resto de la app**. Se había cumplido a medias.

#### El diagnóstico: se estaba usando el componente equivocado

```kotlin
object ButtonDefaults { val Shape: Shape = CircleShape }
```

Las tarjetas de canal eran `Button`. En este sistema de diseño **una píldora
significa acción**, y por eso salían ovaladas: no era un descuido de radio, era
usar un botón para representar contenido. Las tarjetas del inicio son
rectángulos con imagen y el nombre debajo.

#### Lo que cambió

**La tarjeta de canal es un mosaico con el logo al frente**, rectangular
(`ShapeDefaults.Medium`), con el nombre **debajo** como en las filas de Inicio.
A tres metros, con 120 canales y el pulgar en una flecha, un canal se reconoce
por su imagen mucho antes que leyendo «Estrellas HD». El número va arriba a la
izquierda en monoespaciada, para que la columna quede alineada entre tarjetas;
HD abajo a la derecha; el favorito como estrella; y **«sin señal» se dice en la
tarjeta**, no al entrar — enterarse después de pulsar es enterarse tarde.

**Los controles del reproductor sí son píldoras**, y por el mismo motivo por el
que la tarjeta no lo es: son actos. Ahora usan `ButtonColors` y
`ButtonDefaults.Shape` del sistema de diseño en vez de grises inventados, así
que son los mismos botones que los de la barra principal.

**La cabecera dejó de empujar los canales media pantalla.** Marca a la
izquierda; estado, degradación y Diagnóstico a la derecha en una sola línea. El
botón de Diagnóstico ocupaba el sitio del primer canal.

#### Tres defectos que salieron al reescribir

**La pulsación larga para marcar favoritos no funcionaba.** La tarjeta recibía
`onLongClick` y **nunca se lo pasaba al botón** —que sí lo admite—. Nadie lo
notó porque no hay forma de distinguir «no funciona» de «no lo mantuve
bastante». Verificado ahora: `favoritos.cambio canal=estrellashd ahora=favorito`.

**Una caja dentro de otra.** `LogoDeCanal` dibuja su propio recuadro, que tiene
sentido suelto en la barra —flota sobre el vídeo— y dentro de una tarjeta se ve
como un marco sobrante. Ahora el fondo es un parámetro.

**El HD seguía anunciándose en un canal sin señal**, que no dice nada útil.

#### Lo que esto no juzga

La fluidez con el mando. Las capturas por `adb` dicen que la jerarquía y el foco
están donde deben, no cómo se siente recorrer la guía con el control. Eso es de
Edgar.

**Estado:** cerrada. Pendiente la validación en pantalla con mando.

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
fija `LiveTuning.TARGET_OFFSET_MAX_WINDOW_FRACTION` —hoy
`TARGET_OFFSET_WINDOW_WARN_FRACTION`, y no fija nada: avisa (pendiente 44)—.
En `beisbol` no molesta;
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
