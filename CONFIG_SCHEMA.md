# CONFIG_SCHEMA.md

**Contrato entre el servicio de canales de cada site y la APK.**

Este documento es la frontera entre dos desarrollos separados. El servicio de
canales es el que **produce** este archivo; la APK solo lo consume y obedece.
Nada de lo que hay aquí se codifica en la app (`CLAUDE.md` regla 12).

Documentos hermanos: `CLAUDE.md` §6 (de dónde sale la URL y quién es dueño de
qué), `CHANNELS.md` (lineup de referencia), `LIVETV_ENGINE.md` (qué hace la
cascada con estos campos).

---

## 0. Cómo leer este documento

Cada campo lleva su estado:

| Marca | Significado |
|---|---|
| **implementado** | la app lo lee **y lo usa** hoy |
| **interpretado** | el parser lo lee, pero nada lo consume todavía. Emítelo igual: el día que se use, los sites ya lo traen |
| **acordado** | decidido en `CLAUDE.md`, aún no está ni en el parser |

Un servicio que emita solo lo **implementado** funciona completo. Lo
**acordado** se puede emitir desde ya: la app ignora lo que no entiende (§6) y
lo aprovechará cuando llegue la versión que lo lee.

---

## 1. Dónde se publica

```
Jellyfin        http://<host>:<puerto>/              ← lo escribe el abonado
config.json     http://<host>:8097/config.json       ← derivado por la app
```

La app toma el host de la dirección de Jellyfin y cambia el puerto a
`LiveTuning.CHANNEL_SERVICE_PORT` (hoy **8097**). Conserva el esquema: un site
servido por `https` pedirá el `config.json` por `https`.

Conviene servirlo con `Content-Type: application/json`, aunque **la app no lo
exige**: interpreta el cuerpo venga con el tipo que venga.

Límite de **2 MB** (`CONFIG_MAX_BYTES`), comprobado sobre la **longitud
declarada** en la respuesta; por encima, la app descarta la descarga y se queda
con su caché. Un servidor que responda sin declarar longitud —troceado— se salta
esa comprobación, así que **declara `Content-Length`**. Tiempos de espera: 5 s
para conectar, 10 s para leer.

**El EPG no va aquí** (§5). Va por puntero, con su propia URL y su propia
cadencia.

---

## 2. Raíz del documento

```json
{
  "version": 1,
  "generatedAt": "2026-10-02T12:00:00Z",
  "groups": [ … ],
  "channels": [ … ],
  "deviceOverrides": [ … ],
  "brand": { … },
  "epg": { … }
}
```

| Campo | Tipo | Estado | Obligatorio |
|---|---|---|---|
| `version` | entero | implementado | **sí** |
| `generatedAt` | texto ISO-8601 | implementado | no |
| `groups` | lista | implementado | no |
| `channels` | lista | implementado | **sí**, con al menos uno utilizable |
| `deviceOverrides` | lista | implementado | no |
| `brand` | objeto | acordado | no |
| `epg` | objeto | acordado | no |

**Si falta `version`, el documento se rechaza entero** y la app se queda con su
caché. Es el único campo de la raíz cuya ausencia es fatal, junto con no tener
ni un canal utilizable.

### `version` y la convivencia entre versiones

La APK se actualiza para todos a la vez por Play; los servicios de canales,
site por site. **Una APK nueva va a convivir con servicios viejos durante
meses**, y al revés.

- Una `version` **mayor** de la que la app entiende **no se rechaza**: se usa
  igual, ignorando lo que no reconoce, y se registra
  (`config.versionMasNueva`). El abonado no puede quedarse sin televisión
  porque el servidor vaya por delante de su app.
- Campos desconocidos se ignoran en silencio. Por eso se puede emitir lo
  **acordado** antes de que la app lo lea.
- Campos ausentes se resuelven con el valor de fábrica, **nunca** con un fallo.

Sube `version` cuando cambies el significado de un campo existente. Añadir
campos nuevos no lo requiere.

---

## 3. `groups`

```json
{ "id": "nacionales", "name": "Nacionales", "order": 1 }
```

| Campo | Tipo | Estado | Obligatorio |
|---|---|---|---|
| `id` | texto | implementado | **sí** |
| `name` | texto | implementado | **sí** |
| `order` | entero | implementado | no (0 por defecto) |

Son las filas de la guía, en el orden que fije `order`. **«Favoritos» no se
declara**: lo construye la app con lo que el abonado marque, y va siempre
primero.

Un canal que apunte a un grupo que no está declarado **no se pierde**: queda
suelto, se agrupa bajo «Otros» y se registra `config.grupoDesconocido`. Perder
un canal por una errata en la guía sería desproporcionado.

---

## 4. `channels`

```json
{
  "id": "estrellashd",
  "number": 101,
  "name": "Estrellas HD",
  "group": "nacionales",
  "url": "http://10.0.0.5:8181/estrellashd/mono.m3u8",
  "logoUrl": "http://10.0.0.5:8097/logos/estrellashd.png",
  "videoCodec": "h264",
  "audioCodec": "aac",
  "interlaced": false,
  "hd": true,
  "audioDefault": "spa",
  "targetOffsetMs": 8000,
  "enginesAllowed": ["media3"],
  "extras": [ { "label": "Región", "value": "Norte" } ]
}
```

### Obligatorios

| Campo | Tipo | Estado | Qué pasa si falta o está mal |
|---|---|---|---|
| `id` | texto | implementado | **el canal se descarta** |
| `name` | texto | implementado | **el canal se descarta** |
| `url` | texto | implementado | **el canal se descarta**; debe empezar por `http://` o `https://` |

**El orden de la guía es `number`, luego `order`, luego el nombre.** El número
manda: `order` solo desempata entre canales con el mismo número, o entre los que
no traen ninguno. Un canal sin `number` va después de todos los que sí lo traen.

Un `id` repetido descarta el segundo canal. **Un canal mal formado se descarta
solo, no tira la lista**, y se registra `config.canalDescartado` con el índice
y el motivo. Si no queda ni uno utilizable, el documento entero se rechaza.

El `id` es la identidad del canal para la app: favoritos, último canal visto y
diagnóstico cuelgan de él. **No lo reutilices para otro canal** ni lo cambies
al reorganizar el lineup, o los favoritos del abonado apuntarán a otra cosa.

### Presentación

| Campo | Tipo | Estado | Si falta |
|---|---|---|---|
| `number` | entero | implementado | el canal va al final de todo |
| `order` | entero | implementado | desempata por nombre |
| `group` | texto | implementado | queda suelto, bajo «Otros» |
| `logoUrl` | texto | **interpretado** | — |
| `hd` | booleano | implementado | se deduce de la altura medida |
| `extras` | lista | acordado | no se dibuja nada |

**`logoUrl` todavía no se dibuja.** La app enseña siempre las iniciales del
nombre. Emítelo igual, y ten los archivos servidos: cuando se implemente, los
sites ya estarán listos sin volver a pasar por ellos.

**`hd` se combina con la medición, no la sustituye:** la insignia dice HD si
este campo es `true` **o** si la altura medida llega a 720. Así un canal que
cambió de resolución no queda mal etiquetado por un `config.json` viejo, y uno
que aún no ha abierto se etiqueta con lo que diga el archivo.

### `extras`: información libre sin versión nueva

```json
"extras": [
  { "label": "Calidad", "value": "HD" },
  { "label": "Región",  "value": "Norte" }
]
```

Pares etiqueta/valor que la app pinta **sin saber qué son**. Es lo que permite
que un ISP añada información sin que haya que tocar la APK.

Las reglas que lo hacen seguro, y que conviene conocer al generarlos:

- **Solo texto.** Sin enlaces, sin acciones, sin formato. En cuanto algo
  necesita comportamiento, deja de ser dato y vuelve a ser código.
- **Acotado**: número máximo de entradas y longitud máxima por campo. Lo que
  sobre se recorta en silencio; un `config.json` no puede empujar la barra
  fuera de pantalla.
- **Una entrada mal formada se descarta, no tira el canal.**

### Lo que necesita el motor

| Campo | Tipo | Estado | Si falta |
|---|---|---|---|
| `videoCodec` | texto | implementado | la cascada asume hardware y se corrige al abrir el stream |
| `audioCodec` | texto | implementado | solo sirve para contrastar con lo recibido |
| `interlaced` | booleano | implementado | `false` |
| `audioDefault` | texto | **interpretado** | la app elige la primera pista |
| `targetOffsetMs` | entero | implementado | 8000 |
| `enginesAllowed` | lista de texto | implementado | sin filtro |

**Los códecs son una pista, no la verdad.** Si el stream los desmiente, manda
el stream: la app rehace el plan de reproducción y registra
`plan.codecDiscrepante`. Emítelos igual — la cascada los necesita **antes** de
abrir el stream, cuando aún no hay pistas que inspeccionar.

Valores que la app reconoce en `videoCodec`: `h264`/`avc`, `hevc`/`h265`,
`mpeg2video`/`mpeg2`, `mpeg4`, `vp9`, `av1`. También acepta el MIME directo
(`video/avc`). Lo que no reconoce se trata como «sin declarar».

**`audioDefault` todavía no elige nada.** La app arranca con la pista que elija
Media3 y el abonado puede cambiarla desde los controles; el campo se lee pero no
se aplica. Emítelo: el idioma es la **única** clave de selección que queda,
porque Flussonic renumera los PIDs al remuxar (`CHANNELS.md` §2.bis), y la
normalización que lo hará posible ya existe —`spa`, `esp`, `esl`, `es`,
`spanish` y `castellano` son todos español—.

`targetOffsetMs` **nunca por encima de la mitad de la ventana DVR del canal**.
Con `segment_count 12` la ventana ronda los 30 s, así que 8000–12000 va holgado.

`enginesAllowed` es un **filtro duro**: un motor ausente de la lista no se elige
nunca, ni como último recurso, ni aunque lo pida un `deviceOverride`. Úsalo solo
cuando sepas que un canal concreto no funciona con un motor concreto.

### El audio tiene que llegar en AAC

`CLAUDE.md` §2: **todo el audio se normaliza a AAC en Flussonic.** La app no
lleva decoders de MP2, MP3 ni AC-3, y no los va a llevar.

**El generador debe comprobarlo y no publicar un canal cuyo audio no sea AAC**,
o marcarlo. El dato lo da la API de Flussonic y es el momento más barato de
detectarlo.

Si se cuela uno, la app lo dice —`audio.noNormalizado`, o `audio.sinDecoder` si
además está mudo en esa caja— pero para entonces el canal ya está desplegado. Y
falla en silencio: una caja con decoders de sobra lo reproduce perfecto y una
caja barata se queda sin sonido.

---

## 5. `epg` *(acordado)*

```json
"epg": {
  "url": "http://10.0.0.5:8097/epg.xml",
  "refreshMinutes": 360
}
```

| Campo | Tipo | Obligatorio |
|---|---|---|
| `url` | texto | **sí**, si existe el objeto |
| `refreshMinutes` | entero | no |

**Puntero, no contenido.** La lista de canales cambia cuando alguien la cambia;
la guía cambia cada día y pesa megas. Metidas en el mismo archivo, cada caja se
bajaría la guía entera para comprobar si cambió un canal.

Formato **XMLTV**. La correspondencia con los canales va por el `id` del canal:
el `channel id` del XMLTV debe coincidir con el `id` de `channels`.

**Un site sin EPG es normal, no un error.** Si `epg` falta, la barra se degrada
y enseña el canal sin programación. Es el estado esperado mientras la tubería
de guía de ese site no esté lista.

---

## 6. `brand` *(acordado)*

```json
"brand": {
  "displayName": "GarraTV",
  "accent": "#E8A33D",
  "logoUrl": "http://10.0.0.5:8097/marca/logo.png"
}
```

| Campo | Tipo | Si falta |
|---|---|---|
| `displayName` | texto | marca neutra |
| `accent` | texto `#RRGGBB` | color neutro |
| `logoUrl` | texto | iniciales de `displayName` |

**Ranuras fijas, no tema libre** (`CLAUDE.md` §8). No hay forma de enviar un
tema, una hoja de estilos ni una disposición: el servicio **elige entre** lo que
la APK ya sabe hacer, nunca entrega capacidades nuevas (regla 13).

**El contraste se valida al cargar.** Un `accent` que no se lea sobre el fondo
de la sección cae al color neutro y se registra. Es deliberado: con colores
libres, tarde o temprano un acento ilegible se convierte en una llamada de
soporte que no se puede reproducir.

El logo se cachea. Formatos de imagen estándar; nada que pueda ejecutar algo.

---

## 7. `deviceOverrides` — **esto no lo pone el ISP**

```json
{
  "match": { "manufacturer": "Skyworth", "model": "4K Google TV Box" },
  "tunneling": false
}
```

**Son conocimiento nuestro, no del ISP.** «Este modelo de caja no soporta
tunneled» se gana una vez, mirando una pantalla, y vale para todos los sites.

**El generador los inyecta; nadie los edita por site.** Si viven en el archivo
de cada ISP hay que copiarlos a N sitios a mano, y el día que se salte uno ese
ISP arranca sin imagen en un modelo que ya sabíamos que fallaba.

### `match`

| Campo | Compara contra |
|---|---|
| `manufacturer` | `Build.MANUFACTURER` |
| `model` | `Build.MODEL` |
| `device` | `Build.DEVICE` |
| `sdkInt` | `Build.VERSION.SDK_INT` |

Igualdad exacta, insensible a mayúsculas, **sin comodines**. Un patrón que casa
de más es peor que uno que no casa, porque rompe cajas que nadie estaba mirando.
Un `match` vacío no casa con nada y se registra `config.ajusteSinCriterios`.

**Gana el más específico**: el que imponga más criterios. Los ajustes se funden
campo a campo, así que se puede decir «todas las cajas de este fabricante sin
tunneled» y además «y este modelo tampoco con nonIDR», sin repetir en el
segundo lo que ya decía el primero.

### Ajustes disponibles

| Campo | Tipo | Para qué |
|---|---|---|
| `tunneling` | booleano | **modelo de exclusión**: viene activo de fábrica; aquí se apaga en los modelos que no deban usarlo |
| `engine` | texto | `"media3"` o `"libvlc"` |
| `forceSoftwareVideo` | booleano | descarta los decoders de hardware |
| `tsAllowNonIdrKeyframes` | booleano | flag del extractor TS |
| `tsDetectAccessUnits` | booleano | flag del extractor TS |
| `targetOffsetMs` | entero | pisa el del canal |
| `vpuDeinterlaces` | booleano | si el VPU del modelo desentrelaza; se valida en pantalla, no por API |

**El servidor sugiere, la caja verifica.** Nada de esto se obedece a ciegas:
pedir `libvlc` donde no está compilado se registra y se ignora, y la cascada
sigue pudiendo descartar lo que el hardware no pueda cumplir. Obedecer a ciegas
convertiría una errata en un canal muerto en miles de casas.

El menú de desarrollador de una caja concreta **pisa** lo que diga el servidor,
pero solo si alguien lo tocó. Un valor sin tocar no es una opinión.

---

## 8. Resumen para quien escribe el generador

Lo mínimo para que un site funcione completo:

```json
{
  "version": 1,
  "generatedAt": "<ahora, ISO-8601>",
  "groups":   [ { "id": …, "name": …, "order": … } ],
  "channels": [ { "id": …, "name": …, "url": …, "number": …, "group": …,
                  "videoCodec": …, "audioCodec": "aac" } ],
  "deviceOverrides": [ … inyectados por la herramienta … ]
}
```

Y las cinco cosas que más fácil se hacen mal:

1. **Comprobar que el audio es AAC** antes de publicar un canal. Es la defensa
   más barata contra la avería que no se ve.
2. **No reutilizar un `id`** para otro canal: los favoritos del abonado cuelgan
   de él.
3. **Emitir `videoCodec`**, aunque el stream mande: la cascada decide antes de
   abrirlo.
4. **`targetOffsetMs` por debajo de la mitad de la ventana DVR** del canal.
5. **No editar `deviceOverrides` a mano por site**: inyectarlos al generar.
