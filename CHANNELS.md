# CHANNELS.md

Caracterización medida de los canales y de la caja de pruebas.
**Fuente de verdad del proyecto. No asumas nada: está aquí.**

> **02-oct: esto es el lineup de referencia, no el de producción.** Al quedar
> un headend por ISP (`CLAUDE.md` §1), los canales que verá un abonado son
> otros, con otros códecs, otros entrelazados y otras rarezas que nadie habrá
> medido. Estos seis siguen siendo contra lo que se afina el motor y contra lo
> que se juzga una regresión — pero el motor **no puede suponerlos**. Para eso
> está la cascada de M3, que decide por canal y por caja sin saber de antemano
> qué hay, y rehace el plan si el stream desmiente al `config.json`.

> **23-oct: los dos canales de 77W están apagados, y es temporal.** Edgar
> retiró ese servidor y lo repondrá en unos días. Mientras tanto
> `estrellashd-mp4` y `estrellassd-mp2v` llegan marcados `unavailable` en el
> `config.json` y la sección los enseña con «Sin señal en este momento» —eso es
> la app funcionando, no un fallo—.
>
> **Lo que bloquea, para no perder una mañana descubriéndolo:**
>
> - **todo lo que necesite MPEG-2 de verdad.** `estrellassd-mp2v` es el único
>   del conjunto, y con él se cae el único camino que ejercita libVLC por
>   necesidad, el desentrelazado `yadif2x` por software y el SAR 20:17;
> - **los *closed captions*.** `estrellashd` es el único que los emite
>   (CEA-608), así que §7 queda sin canal de prueba;
> - **repetir el experimento de zapping del 20-oct separando motores**
>   (pendiente 81): su pata de libVLC eran 7 zapeos de `estrellassd`.
>
> **Lo que NO bloquea:** forzar libVLC desde el menú (`forcedEngine`) sobre
> cualquier canal, que es como se midió el 81 el 22-oct, y toda la degradación
> por capacidades de §9 —`forceSoftwareVideo`, decoders vetados—, que no
> depende de qué emita el satélite.

Medido el 2026-09-20 y 2026-09-21 con `scan-canales.sh` y `adb`.
**Reverificado contra Flussonic el 2026-09-22** (PIDs, SAR, orden de campo
y ventana DVR): ver §2.bis. Esa revisión corrigió varias cifras de aquí.

---

## 0. Prioridad de estabilidad

**Decidido por Edgar el 2026-09-24.** Cuando haya que elegir a qué canal
dedicarle el esfuerzo, el orden es:

```
1º  77W        2º  70W        3º  el resto (TotalPlay)
```

Concuerda con la escala real del lineup: de los ~120 canales de producción **la
mayoría son de 77W** (§3.bis). Un fallo en 77W afecta a más suscriptores que
uno en TotalPlay.

**Consecuencia incómoda:** los dos canales de 77W son hoy **los más rotos** del
conjunto.

| Canal 77W | Estado |
|---|---|
| `estrellashd-mp4` | **no arranca** — almacena 31 s sin reportar formato de video (M4) |
| `estrellassd-mp2v` | reproduce, pero con **15 % de ratio de rebuffer** a los 30 s (M2) |

De modo que la prioridad no es un matiz de planificación: dice que lo peor del
lineup es justo lo que más pesa. Las pruebas de cierre de cada fase de motor se
juzgan **primero sobre 77W**, y solo después sobre 70W y TotalPlay.

TotalPlay sigue sirviendo como **línea base de diagnóstico** —es la señal sin
particularidades, y comparar contra ella separa los fallos del motor de los del
canal— pero **no es criterio de cierre**.

---

## 1. Conjunto de referencia

**El motor se desarrolla y afina contra los canales de satélite 77W y 70W,
tal cual llegan, sin transcodificar video.** Son los que traen todas las
dificultades reales. Los de TotalPlay son H.264 progresivo sin
particularidades y sirven de línea base.

---

## 2. Tabla maestra

URL: `http://192.168.11.250:8181/<canal>/mono.m3u8`

Las columnas de PID se eliminaron a propósito: **Flussonic los normaliza y son
idénticos en todos los canales** (§2.bis). La ventana DVR ocupa su lugar porque
sí varía por canal y gobierna el `targetOffsetMs`.

| Canal | Origen | Video | Resolución | SAR | Barrido | Audio | GOP | Segmento | Ventana |
|---|---|---|---|---|---|---|---|---|---|
| `beisbol` | TotalPlay | h264 | 1280x720 | 1:1 | progresivo | aac (`spa`) | 1.40 s | 2.80 s | 18.59 s |
| `mileniosd` | TotalPlay | h264 | 1024x576 | 1:1 | progresivo | aac (`spa`) | 1.40 s | 2.80 s | 16.80 s |
| `test-sat77w-estrellashd-mp4` | **77W** | h264 High | 1440x1080 | **4:3** | **cine 23.976 + 3:2** | aac **intermitente**, `esl` | 1.07 s | 2.7–3.0 s | 17.13 s |
| `test-sat77w-estrellassd-mp2v` | **77W** | **mpeg2video** | 544x480 | **20:17** | **480i (tt)** | **mp2** (`esl`) → AAC | 1.50 s | 2.3–3.4 s | 16.92 s |
| `test-sat70w-discoveryhd-mp4` | **70W** | h264 Main@4.0 | 1920x1080 | 1:1 | **1080i (tt)** | aac ×2 (`spa`, `eng`) | 1.07 s | 2.0–2.5 s | 14.71 s |
| `test-sat70w-hbohd-mp4` | **70W** | h264 Main@4.0 | 1920x1080 | 1:1 | **1080i (`tt`)** · **cine 23.976 con cadencia 3:2** | aac ×2 (`spa`, `eng`) | ? | 2.3–2.8 s | 30.0 s |

El `→ AAC` del canal mp2v es el cambio de headend pendiente, no el estado
actual. Hasta que se aplique, ese canal sigue entregando MP2.

Lista vigente en Flussonic (2026-09-22): `beisbol`, `mileniosd`,
`test-sat77w-estrellassd-mp2v`, `test-sat77w-estrellashd-mp4`,
`test-sat70w-discoveryhd-mp4`, `test-sat70w-hbohd-mp4`. Todos "Always
started (Static)" y en línea.

**Usar `mono.m3u8`, no `video.m3u8`.** El segundo es un master playlist con una
sola variante: obliga a un round-trip extra por zapping.

---

## 2.bis Lo que corrigió la revisión del 22-sep

### Flussonic renumera los PIDs

Medido sobre el segmento `.ts` crudo de los seis canales. **Todos** llegan así:

| | PID |
|---|---|
| Video | `0xd3` |
| Primer audio | `0xdd` |
| Segundo audio (solo `discoveryhd` y `hbohd`) | `0xde` |

Los PIDs que este documento registraba antes — `0x3fd`/`0x3fe` en el mp2v,
`0x101`/`0x102` en hbohd — son **del lado Astra**, no de lo que recibe la app.

Tres consecuencias:

1. **El PID no identifica nada.** Es un alias de la posición. `0xdd` es "el
   primer audio" en los seis canales.
2. **La clave real de selección de audio es el idioma.** En `discoveryhd-70w`
   y `hbohd-70w`, español e inglés se separan solo por la etiqueta. Esto
   convierte la normalización de `spa`/`esp`/`esl`/`es` en pieza del motor, no
   en detalle de interfaz.
3. **No hay que medir los PIDs de los ~120 canales de producción.** Flussonic
   los normaliza. Eso borra una tarea de caracterización a escala.

### La ventana DVR gobierna el `targetOffsetMs`

Suma de los `EXTINF` del playlist, con `segment_count 6`:

```
beisbol        18.59 s      estrellassd    16.92 s
mileniosd      16.80 s      discoveryhd    14.71 s   ← satelital
estrellashd    17.13 s      hbohd          13.88 s   ← satelital
```

Los dos canales de 70W tienen la ventana **más corta**, y son justo los que
`LIVETV_ENGINE.md` marcaba con `targetOffsetMs = 12000`. En `hbohd` eso dejaba
**1.9 s** antes de `BEHIND_LIVE_WINDOW`: el colchón pensado para la lluvia era
lo que rompía el canal.

**Regla:** `targetOffsetMs` nunca por encima de la **mitad** de la ventana
medida del canal.

**Corrección aplicada:** subir `segment_count` de 6 a 12 en Flussonic → ventana
de ~28 s. No cuesta CPU, no toca la señal y **no aumenta la latencia**: el live
edge no se mueve, solo se alarga el histórico.

### Formato del playlist

`#EXT-X-VERSION:3`, media playlist directo, con `EXT-X-PROGRAM-DATE-TIME` y
`EXT-X-MEDIA-SEQUENCE`. Sin low-latency HLS: el `targetOffsetMs` es
enteramente decisión del cliente.

---

### Medidos antes, directo de Astra

| Canal | Video | Resolución | PID V | Audio | PID A | GOP |
|---|---|---|---|---|---|---|
| `a095` | h264 | 1920x1080 | 0x469 | aac ×2 | 0x461, 0x462 | ~1.07 s, IDR extra por cambio de escena |
| `a093` | h264 | 544x480 | 0x45f | aac | 0x457 | 1.07 s |

`a095` coincide en resolución, pistas y GOP con `discoveryhd-70w`:
probablemente es el mismo canal. **Confirmar.**
`a093` es el único SD H.264 satelital y aún no pasa por Flussonic: conviene
agregarlo como canal de prueba, porque probablemente también viene entrelazado.

---

## 3. Lo que cada canal le exige al motor

| Canal | Dificultades |
|---|---|
| `beisbol`, `mileniosd` | ninguna en el stream; la **fuente** de `mileniosd` para (ver abajo) |
| `estrellashd-mp4` | 1080i `tt` + anamórfico 4:3 + audio intermitente + lang `esl` |
| `estrellassd-mp2v` | MPEG-2 + MP2 + 480i `tt` + anamórfico 20:17 + lang `esl` |
| `discoveryhd-70w` | 1080i `tt` + dos pistas de audio spa/eng |
| `hbohd-70w` | 1080i **`bb`** + dos pistas de audio spa/eng + la ventana DVR más corta |

### La fuente de `mileniosd` se para, y no es del cliente

Medido el 10-oct en dos sesiones del mismo día. `mileniosd` y `beisbol` son los
únicos que llegan por **multicast de un proveedor de fibra** en vez de la antena,
y eso se nota en el log:

| Sesión | Duración | Paradas de la fuente |
|---|---|---|
| noche (00:38 → 10:00) | 9,3 h | continuas; 579 cortes, la red del proveedor con problemas |
| día (11:02 → 14:26) | 3,4 h | **tres**, a las 11:57, 12:12 y 12:29, y nada en los 115 minutos siguientes |

La firma en el log es inconfundible y sirve para reconocerlas en cualquier canal:
el buffer **cae 2 s por cada 2 s de reloj** durante los diez segundos previos al
corte —en marcha normal oscila entre 11 y 15 s— y unos 50 s después Media3 lanza
`PlaylistStuckException`. O sea que el origen dejó de entregar segmentos.

Importa al elegir canal para una prueba larga: **una sesión de `mileniosd` no
distingue un fallo del motor de un bache del proveedor** si no se vigila el
playlist en paralelo (`herramientas/vigilar-playlist.py`). Los satelitales no
tienen este problema, pero tienen los suyos.

### Alerta abierta: pistas que aparecen y desaparecen

En `test-sat77w-estrellashd-mp4` la pista de audio es **intermitente**: en una
captura del 22-sep no existía ninguna, y minutos después volvía a estar.

Dos consecuencias:

1. **Headend:** revisar ese canal en Astra. Un descrambling intermitente de la
   pista de audio encaja con el síntoma y aparecería como errores PES.
2. **Motor (vía M):** ExoPlayer tolera mal que **cambie el número de pistas**
   a mitad de stream y puede detener la reproducción con error. El módulo debe
   sobrevivir a ese caso sin cerrar el player, y recuperar el audio cuando
   vuelva. Es un caso real y reproducible de este lineup.

### El orden de pistas no es fijo

En `estrellashd-mp4` el audio es el stream 0 y el video el 1; en los demás
canales es al revés. La regla que confirma es **nunca por índice** — pero
tampoco por PID, que Flussonic normaliza (§2.bis). La clave es el **idioma**,
con el PID solo como desempate dentro de una misma sesión.

### Códigos de idioma no estándar

`estrellashd-mp4` etiqueta su audio como `esl`, que no es un código ISO
válido; `discoveryhd-70w` y `hbohd-70w` usan `spa` correctamente. El selector
de audio debe aceptar `spa`, `esp`, `esl` y `es` como español.

### Campos entrelazados: ojo con el orden

Cuatro de seis canales vienen entrelazados, y **no todos con el mismo orden de
campo**: `tt` (campo superior primero) en los de 77W y en `discoveryhd-70w`,
pero `bb` (campo inferior primero) en `hbohd-70w`. Un desentrelazador que
asuma el orden equivocado produce movimiento a saltos, distinto del peine.
El orden debe leerse del stream, nunca fijarse por configuración.

`estrellashd-mp4` reporta 59.94 fps con entrelazado `tt`: viene codificado por
campos, como es habitual en 1080i de broadcast. Un desentrelazado bob debería
entregar 59.94 fps de salida.

Resumen del trabajo del motor, sobre **seis** canales:
- **Entrelazado en cuatro de seis**, incluidos los tres HD, y **no todos con
  el mismo orden de campo**
- **Anamórfico en dos de seis**: solo `estrellashd` (4:3) y `estrellassd`
  (20:17). Los otros cuatro son 1:1 — ahí respetar el SAR no cambia nada
- **Un canal con códecs de broadcast** (MPEG-2 + MP2), a la espera de que el
  headend le normalice el audio a AAC
- **Dos canales con dos audios** etiquetados, por defecto `spa`

> Estas cifras decían "tres de cinco" y "cuatro de cinco" hasta el 22-sep.
> `hbohd-70w` entró al cerrar el pendiente #10 y nadie las propagó, y el
> conteo de anamórficos estaba mal de origen. Al corregirlas cambia el
> criterio de cierre de M5.

---

## 3.bis Escala real del lineup

Las pruebas usan 6 canales. **El lineup de producción tendrá ~120 canales**, la
mayoría de 77W, mezclando HD y SD.

Consecuencias de diseño:
- La lista de canales necesita grupos, favoritos, último canal y entrada
  directa por número. Una lista plana de 120 no es navegable con D-pad.
- Es muy probable que entre los SD de 77W haya **más canales MPEG-2 + MP2**,
  como `estrellassd-mp2v`. La cascada de códecs no es un caso excepcional:
  va a aplicar a decenas de canales.
- Precargar canales para acelerar el zapping es inviable a esta escala en
  cajas de 2 GB. La respuesta al zapping es segmento corto y entrada directa
  por número, no precarga.
- El `config.json` con 120 canales pesa unas decenas de kB: sin problema.

## 3.quater `hbohd-70w` transporta cine, no vídeo

**Medido el 2026-09-25, y corrige dos errores de este documento.**

Edgar vio microcortes periódicos en HBO y en ningún otro canal. Los contadores
del motor no veían nada: **cero rebuffers, cero frames perdidos**. La causa
estaba en la fuente.

Distribución de intervalos entre cuadros, capturas de 30 s:

```
hbohd        358 intervalos de 50 ms  +  357 de 33 ms   (alternando)
discoveryhd  894 intervalos de 33 ms                    (uniforme)
```

50 ms son **3** tiempos de campo y 33 ms son **2**: es una **cadencia 3:2**. El
promedio da 41.7 ms por cuadro, o sea **23.976 fps**. HBO transporta **cine a
24 fps** dentro de un contenedor de 29.97; `discoveryhd` lleva vídeo nativo
uniforme. Los dos declaran exactamente los mismos parámetros —Main@4.0,
1920x1080, `tt`, `r_frame_rate` 59.94, `avg_frame_rate` 29.97—, así que **la
diferencia no se ve en la cabecera, solo midiendo**.

**Sobre la tabla maestra:**

1. El orden de campo **no es estable en este canal**. Una captura dio `tt` y
   otra, horas después, `bb`. El documento no estaba mal: **varía con el
   contenido**. Anotar propiedades fijas para este canal es el error.
2. Decía **59.94 por campos**; son **23.976 con cadencia 3:2**. Los 59.94 son
   campos por segundo, no cuadros.

**Corrección del 26-sep:** aquí se dio por hecho que la cadencia venía
**aplicada** en los campos. Medido sobre el flujo, no: 285 de 286 imágenes son
**progresivas** y `repeat_pict` alterna `0,1,0,1` sin excepción. Es **telecine
suave**, igual que `estrellashd` y `estrellassd`. Lo que se midió como
alternancia 50/33 ms es el decodificador **aplicando bien** la cadencia
señalizada.

**Lo que queda sin explicar.** La cadencia 3:2 produce un tirón característico,
pero de periodo ~83 ms, no de 2 segundos como describe Edgar. Y si el
decodificador la está aplicando correctamente, ese tirón es **inherente al cine
24p en un panel de 59.94 Hz de modo único**, no un fallo. Hay que separar las
dos cosas en pantalla antes de seguir persiguiéndolo. **No se da por cerrado.**

**Descartado:** `FLAG_DETECT_ACCESS_UNITS`. Con el flag en ON el fps medido
pasó de 50.40 a 49.85, es decir, nada. No es un problema de detección de
unidades de acceso.

**Va a M5**, que es la fase de render y cadencia. No es de M2 ni del cliente:
el stream llega así.

---

## 3.quinquies **Tres de seis canales transportan cine, y solo uno es entrelazado de verdad** — 2026-09-26

Medido sobre 12 s capturados de cada canal en vivo, el mismo día y con el mismo
procedimiento. **Esta tabla sustituye a lo que este documento decía sobre
entrelazado**, que era incorrecto en tres canales:

| Canal | Imágenes/s reales | `interlaced_frame` | `repeat_pict` | Qué es |
|---|---|---|---|---|
| `beisbol` | 29.845 | 0 en 361 | 0 en 361 | **vídeo progresivo**, sin pulldown |
| `mileniosd` | 29.937 | 0 en 361 | 0 en 361 | **vídeo progresivo**, sin pulldown |
| `discoveryhd-70w` | 29.431 | **1 en 357** | 0 en 357 | **el único entrelazado real** (1080i `tt`) |
| `estrellashd-mp4` | 24.010 | 0 en 286 | `1,0,1,0…` | **cine 23.976 + 3:2 señalizado** |
| `hbohd-70w` | 23.386 | 0 en 285 de 286 | `0,1,0,1…` | **cine 23.976 + 3:2 señalizado** |
| `estrellassd-mp2v` | 23.894 | 0 en 289 | `1,0,1,0…` | **cine 23.976 + 3:2 señalizado**, en MPEG-2 |

Los tres canales de cine usan **telecine suave**: mandan imágenes progresivas a
23.976/s y marcan con `repeat_first_field` cuáles deben durar tres campos en
vez de dos. Los seis piden entre 58.4 y 59.9 campos/s, que es lo que el panel
espera.

**Lo que esto cambia en el plan:** el trabajo de render no es principalmente
desentrelazar —solo un canal lo necesita— sino **respetar la cadencia del
pulldown**. Un reproductor que ignore `repeat_first_field` saca 48 fps en tres
de los seis canales, y 48 no divide a 59.94.

**Ojo con el contenido.** Un canal generalista alterna cine y vídeo a lo largo
del día: `estrellashd` midió 29.91 fps en el barrido del 26-sep por la mañana y
24.01 esa misma tarde. **La propiedad es del programa que esté al aire, no del
canal.** El motor no puede fijarla por configuración; tiene que seguirla.

### El caso de `estrellashd-mp4` en detalle

Medido sobre 12 s capturados del canal en vivo:

| Dato | Valor |
|---|---|
| Imágenes | 286 en 11.912 s → **24.010 fps** (23.976 nominal) |
| `interlaced_frame` | **0 en las 286**: todas progresivas |
| `repeat_pict` | alterna `1,0,1,0,…` sin una sola excepción |
| `top_field_first` | alterna 143 / 143 |
| Campos que pide el flujo | 2+3+2+3… = **715 en 11.912 s → 60.02 campos/s** |
| SAR / DAR | 4:3 → **16:9** (anamórfico, sin cambio) |
| `field_order` declarado | `bb` |

Es **telecine suave**: el codificador manda 24 imágenes progresivas por segundo
y marca con `repeat_first_field` cuáles deben mostrarse durante tres campos en
vez de dos. El decodificador reconstruye 59.94 campos/s. Nada está entrelazado
en origen.

**Consecuencia para el motor.** Un reproductor que ignore `repeat_first_field`
saca **2 campos por imagen = 48 fps**, y 48 no divide a 59.94: el compositor
muestra unas imágenes un refresco y otras dos, con un batido de ~1 s. Se ve
como tirón irregular. Medido en VLC sobre esta misma caja: **47.99 fps reales**
sostenidos, con el patrón de pausas correspondiente. No es falta de CPU —VLC
usa el decodificador por hardware y consume 1.1 de 4 núcleos.

La salida correcta en un panel de 59.94 Hz es la cadencia 3:2: cada imagen
alternando **3 y 2 refrescos**, es decir intervalos de 50 y 33 ms. Es
exactamente la firma que ya se midió en `hbohd-70w` (358 intervalos de 50 ms
alternando con 357 de 33 ms).

**Dos canales de seis transportan cine a 23.976**, no uno. Lo que los distingue
es cómo llega el pulldown: en `hbohd` la cadencia viene aplicada en los campos,
en `estrellashd` viene solo señalizada. El motor tiene que tratar los dos.

**Aviso de método.** El ritmo de presentación **no** se mide promediando los
intervalos que devuelve `dumpsys SurfaceFlinger --latency`: ese búfer guarda
128 entradas y conserva restos antiguos que envenenan la media. Hay que contar
fotogramas **nuevos** muestreando más rápido de lo que el búfer da la vuelta
(~2 s a 60 fps). Con el método malo este canal daba «46–52 fps irregulares»;
con el bueno da 47.99 estables.

## 3.ter Tipos de codificación entrelazada

No basta con saber que un canal es entrelazado. Hay dos codificaciones
distintas y los decoders las tratan diferente:

| Canal | Orden | fps | Codificación |
|---|---|---|---|
| `discoveryhd-70w` | tt | 29.97 | **por frames** (dos campos en una imagen) |
| `estrellassd-mp2v` | tt | 29.97 | **por frames** |
| `estrellashd-mp4` | **bb** | **23.976** | **cine progresivo con 3:2 señalizado** — ver §3.quinquies |
| `hbohd-70w` | **tt** | **23.976** | **cine con cadencia 3:2** — ver §3.quater |

El comportamiento de MediaCodec con contenido codificado por campos es más
irregular. **La prueba visual debe cubrir los dos tipos**, no solo los dos
órdenes de campo: con `discoveryhd-70w` y `hbohd-70w` se cubren las cuatro
combinaciones. Esos dos bastan, y es una suerte, porque el tercero está en
duda.

**La contradicción de `estrellashd-mp4` queda resuelta (26-sep).** Ni 60000/1001
ni 30000/1001: los dos eran valores nominales del contenedor. Ver
§3.quinquies — el canal es cine a 23.976 **progresivo** con pulldown 3:2
señalizado, y con él cae el pendiente #14.

`mileniosd` va a **30.00 fps exactos**, no 29.97: TotalPlay lo recodifica. En
una pantalla a 59.94 Hz eso produce un tirón sutil cada ~33 s. No es un fallo
del reproductor.

## 4. Otros datos medidos

- Todos los H.264 traen **AUDs** (151 a 300 por muestra de 30 s) e **IDR**
- Ningún canal devolvía `Content-Length` en MPEG-TS progresivo
- Bitrates de 1.5 a 2.75 Mbps
- `discoveryhd-70w` trae Closed Captions CEA-608 embebidos (no se usan)
- El DVR de Flussonic está activo: timeshift disponible cuando se quiera

### Referencia de estabilidad

`beisbol` mantiene segmentos de 2.803 s con variación de ±33 ms (un frame).
Ese es el comportamiento sano. Si un canal empieza a oscilar cientos de
milisegundos de forma sostenida, es jitter real en la recepción satelital.

### Comportamiento de Flussonic cuando la fuente cae

Medido el 22-sep deteniendo un stream desde la interfaz:

```
18:12:56   6 segmentos en el playlist
18:12:58   0          ← fuente detenida
18:13:08   0
18:13:13   3          ← vuelve, el playlist se reconstruye
18:13:19   6
```

Tres conclusiones para el motor:

1. **Flussonic devuelve un playlist vacío**, no un 404 ni un playlist
   congelado. El cliente debe tratar "playlist sin segmentos" como estado de
   espera, **nunca como error terminal**.
2. **Las peticiones se quedan colgadas.** Los intentos agotaron el límite de
   3 s en lugar de fallar rápido. Refuerza el `readTimeout` largo.
3. **La recuperación tardó ~15 s.** Ese es el piso: el cliente no puede
   recuperarse antes que el playlist. **El objetivo de recuperación < 8 s no
   es alcanzable hoy sin tocar el headend** (bajar `source_timeout` y poner
   el letrero de respaldo).

### Segmentación (histórico)

| | Antes | Después |
|---|---|---|
| Configuración | default de Flussonic | `segment_duration 2`, `segment_count 6` |
| Segmentos | 5.3 a 6.5 s | 2.30 a 3.44 s |
| Latencia estimada | ~16 s | ~8 s |
| Zapping estimado | 3 a 6 s | 2 a 3 s |

Los segmentos no dan 2.00 exactos porque Flussonic corta en keyframe: el valor
real es el múltiplo del GOP más cercano por encima de 2 s. Remedido el 22-sep:
**2.30 a 3.44 s**, algo por encima del rango que este documento registraba. **No bajar de 2 s**:
con GOP de 1.50 s en el canal mp2v, un segmento de 1 s obligaría a cortar
fuera de keyframe.

El `TARGETDURATION` varía por canal (3, 4, 5) según el GOP de cada cadena. Por
eso `targetOffsetMs` va **por canal** en el `config.json`, no global.

---

## 5. Caja de pruebas

| Dato | Valor |
|---|---|
| Marca y modelo | Skyworth "4K Google TV Box", device `YDA`, `ro.product.model` = `4K Google TV Box` |
| Plataforma | Amlogic, `HP46A` |
| CPU | 4× Cortex-A35 (`CPU part 0xd04`), probable S905Y4 |
| Android | 11 (API 30) |
| IP | `192.168.20.132` |
| ABI | **`armeabi-v7a,armeabi`** — solo 32 bits, medido 2026-09-22 |

### Decoders relevantes

| Formato | Decoder | Tipo |
|---|---|---|
| H.264 | `OMX.amlogic.avc.decoder.awesome2` | **hardware** |
| MPEG-2 | `OMX.amlogic.mpeg2.decoder.awesome2` | **hardware** |
| MP2 | `OMX.amlogic.audio.decoder.mp2` | **hardware, activo** |
| AC-3 / E-AC-3 | `OMX.amlogic.audio.decoder.ac3` / `.eac3` | hardware |
| AAC | `c2.android.aac.decoder` | software |

El decoder MP2 está en `media_codecs_ext.xml` línea 146, dentro del bloque
`<Decoders>` y fuera de comentario: está habilitado. Deja de importar en cuanto
el headend normalice el audio a AAC.

**Confirmado en ejecución el 2026-09-22**, reproduciendo `beisbol` con el motor
de M0: video por `OMX.amlogic.avc.decoder.awesome2` (hardware, 122 ms de
inicialización) y audio por `c2.android.aac.decoder` (software, 191 ms). El
AAC por software no preocupa: cuesta poca CPU y es el camino de toda caja
Android. Este documento decía `OMX.google.aac.decoder`, el nombre anterior a
Codec2; en Android 11 es `c2.android.*`.

### Pantalla: un solo modo

`dumpsys display` en la caja conectada al televisor de pruebas (Samsung, 2020)
reporta **un único modo**: `1920x1080 @ 59.94006 Hz`, `modes [{id=1, ...}]`.

Dos consecuencias que no son ajustables desde el cliente:

- **Nadie puede dejar el televisor en otra frecuencia.** El cambio automático de
  frecuencia por contenido no tiene a qué cambiar, ni el nuestro ni el del VOD
  del upstream.
- **El cine 23.976 de `hbohd` siempre se convierte de cadencia** contra 59.94
  Hz. Es la raíz del pendiente 22, y no tiene solución en el reproductor.

### Desentrelazado: **sí**, medido por contraste — 2026-09-26

`/sys/class/deinterlace/di0` existe (driver DI de Amlogic cargado), y ahora hay
medición del camino MediaCodec real. Ritmo de presentación en el compositor
(`dumpsys SurfaceFlinger --latency`) con el motor **de fábrica**:

| Canal | Contenido | Presentación | Relación |
|---|---|---|---|
| `beisbol` | 720p **progresivo** 29.97 | 29.97 / 29.73 / 28.83 | **1:1** |
| `estrellassd-mp2v` | 480**i** 29.97 MPEG-2 | 59.94 / 59.94 / 59.94 | **2×** |
| `hbohd-mp4` | 1080**i**, cine 23.976 | 45.69 / 52.09 / 46.05 | 2× irregular |

Lo progresivo sale 1:1 y lo entrelazado al **ritmo de campo**. Un decodificador
que no desentrelazara daría un cuadro por cuadro codificado en los tres casos.

Falta solo juzgar **la calidad** del desentrelazado en pantalla, que es de M5 y
de Edgar.

**Aviso sobre las mediciones de ritmo:** las lecturas de ~15 fps en
`estrellassd` que estuvieron en circulación el 26-sep salieron de sesiones con
interruptores del menú de degradación pegados. Cinco sintonías frescas de
fábrica dan 57–60. Antes de anotar un ritmo, comprobar que el overlay diga
`degradación de fábrica`.

### Advertencia

**Esta caja es el mejor caso posible.** Trae MPEG-2, MP2 y AC-3 por hardware.
Desarrollar y probar solo contra ella dejaría los caminos de respaldo sin
ejercitar. De ahí el menú de degradación obligatorio de la fase M1.

Tampoco es la caja del cliente: la mayoritaria es una ZTE ZXV10 B866V2, que
comparte fabricante de SoC y probablemente el mismo chip, pero no está medida.

---

## 6. Tabla de cajas

| Caja | SoC | MPEG-2 | H.264 | MP2 | Desentrelaza | Android | Notas |
|---|---|---|---|---|---|---|---|
| Skyworth YDA | Amlogic A35 | HW | HW | HW | **pendiente** | 11 | caja de pruebas |
| ZTE ZXV10 B866V2 | Amlogic | ? | ? | ? | ? | ? | mayoritaria del primer cliente |

**Informativa. La app no depende de ella:** la detección ocurre en runtime en
cada dispositivo y los casos problemáticos se corrigen con overrides remotos.

---

## 7. Cómo regenerar estos datos

`herramientas/scan-canales.sh`, dentro del repositorio desde el 05-oct. Vivía
suelto en `/home/`, que es mal sitio para la herramienta que produce la fuente
de verdad.

```bash
herramientas/scan-canales.sh -t 30 -k
```

**Mide `index.m3u8`, no `mono.m3u8`.** Se corrigió al traerlo: el media
playlist multiplexa los audios en un solo TS y Media3 sólo expone uno, así que
medir sobre él daba un inventario de pistas distinto del que ve la app.

**El `CHANNELS.md` que escribe sale en `out/` y es un borrador.** Este de aquí
lleva análisis escrito a mano que el script no reproduce: se copian datos de
uno a otro, nunca el archivo entero.

Las muestras `.ts` quedan en `out/samples/` y sirven como golden samples para
regresión. Capturar una durante lluvia real y guardarla: es la mejor prueba
de resiliencia que existe.
