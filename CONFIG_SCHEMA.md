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
  "epg": { … },
  "extras": [ { "label": "Plan", "value": "Digital Total" } ]
}
```

| Campo | Tipo | Estado | Obligatorio |
|---|---|---|---|
| `version` | entero | implementado | **sí** |
| `generatedAt` | texto ISO-8601 | implementado | no |
| `groups` | lista | implementado | no |
| `channels` | lista | implementado | **sí**, con al menos uno utilizable |
| `deviceOverrides` | lista | implementado | no |
| `brand` | objeto | **implementado** | no |
| `epg` | objeto | acordado | no |
| `message` | objeto | **implementado** | no |
| `defaultChannel` | texto | **implementado** | no |
| `extras` | lista | **implementado** | no |

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

### `message` — el aviso del operador

```json
"message": {
  "text": "Mantenimiento el martes de 2:00 a 4:00",
  "until": "2026-10-08T04:00:00Z"
}
```

| Campo | Obligatorio |
|---|---|
| `text` | **sí**, si existe el objeto |
| `until` | no; pasado ese instante la app deja de enseñarlo |

Un texto que el ISP quiere que vean sus abonados. **Lo lee alguien en su sala**,
así que se escribe para esa persona.

Se dibuja bajo la marca, en la sección de canales — **no sobre el vídeo**: es
información del ISP, no del canal, y taparle la imagen al abonado con el recado
de su proveedor no sería un intercambio justo. Y discreto: un aviso que grita se
lee una vez y molesta las cien siguientes.

`until` existe porque el fallo previsible de esto es **el aviso que nadie quitó**:
un mantenimiento de hace tres meses sigue en pantalla porque quien lo puso se
olvidó. Con fecha, se retira solo.

Solo texto. Sin enlaces ni formato, por la misma razón que los `extras`.

### `defaultChannel` — qué se sintoniza la primera vez

```json
"defaultChannel": "estrellashd"
```

El `id` del canal que abre una caja **recién instalada**, antes de que haya un
«último canal visto». Sin esto la app abre el primero de la lista, que puede ser
cualquier cosa según cómo esté numerado el lineup.

Es el primer momento en que el abonado se forma una opinión del servicio, y
cuesta un campo. Un `id` que no exista se ignora y se registra.

**Solo decide la primera vez.** La app recuerda el último canal sintonizado en
esa caja y le da el foco al volver a la guía; `defaultChannel` es el respaldo
cuando no hay historia o cuando el último ya no está en el lineup.

No sirve, por tanto, para empujar a nadie a un canal: elígelo pensando en el
primer arranque.

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
  "url": "http://10.0.0.5:8181/estrellashd/index.m3u8",
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
| `url` | texto | implementado | **el canal se descarta**, salvo con `unavailable: true`; debe empezar por `http://` o `https://` |

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
| `logoUrl` | texto | **implementado** | iniciales del nombre |
| `hd` | booleano | implementado | se deduce de la altura medida |
| `extras` | lista | **implementado** | no se dibuja nada |
| `aliases` | lista de texto | **implementado** | solo se busca por el nombre |
| `unavailable` | booleano | **implementado** | el canal se trata como normal |
| `unavailableMessage` | texto | **implementado** | mensaje genérico |
| `rating` | texto | **interpretado** | — |
| `backupUrl` | texto | **interpretado** | — |

**`logoUrl` se dibuja en la guía y en la barra del canal.** Si falta, o si la
imagen no carga, se enseñan las iniciales del nombre — nunca un hueco. Una URL
rota por tanto **no se nota en pantalla**: se nota mirando que el logo no sale.

Formatos de imagen estándar. **Nombres inmutables**: la app cachea por URL y no
invalida, así que reemplazar un logo conservando el nombre deja a las cajas
enseñando el viejo durante días.

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

**Van en el canal y también en la raíz**, y no se dibujan en el mismo sitio:
los del canal salen en la barra, bajo «Luego»; los de la raíz salen en la
cabecera de la sección, bajo el nombre del proveedor. Los primeros hablan del
canal; los segundos, del servicio.

Las reglas que lo hacen seguro, y que conviene conocer al generarlos:

- **Solo texto.** Sin enlaces, sin acciones, sin formato. En cuanto algo
  necesita comportamiento, deja de ser dato y vuelve a ser código.
- **Acotado**: **6 entradas**, **24 caracteres de etiqueta** y **40 de valor**.
  Lo que sobre se recorta en silencio y se registra
  `config.extrasRecortados`. Un `config.json` no puede empujar la barra fuera
  de pantalla.
- **Una entrada mal formada se descarta, no tira el canal.** Sin `label` o sin
  `value` utilizable, esa entrada no existe; las demás siguen.

**El recorte no avisa en pantalla.** Una etiqueta de 40 caracteres se publica
cortada a 24 y el abonado lee media palabra. Es un descuido del generador, no
un fallo de la app, y la única pista está en el log.

**No se dibujan en las tarjetas de la guía**, solo en la barra y en la
cabecera. Con 120 canales, la tarjeta tiene que leerse de un vistazo.

### Un canal que está pero no se puede ver

```json
"unavailable": true,
"unavailableMessage": "Fuera de servicio por mantenimiento del headend"
```

**Hay dos formas de que un canal no se vea, y no son lo mismo:**

- **No publicarlo.** El servicio sencillamente no lo emite. Es lo correcto
  mientras se da de alta, o cuando se retira del lineup. La app no se entera.
- **Publicarlo como no disponible.** El canal sigue en la guía, con su número y
  su sitio, pero al sintonizarlo se enseña el motivo en vez de intentar
  reproducir.

La segunda existe porque **un canal que desaparece genera una llamada a
soporte** y uno que dice «fuera de servicio por mantenimiento» no. Si alguien ve
el 207 todos los días y un martes no está, llama.

**Un stream que deja de emitir va por la segunda, no por la primera.** Retirarlo
del lineup no llega antes al abonado —usa los mismos dos relojes: 30 s para que
el generador lo note, hasta 15 min para que la app recargue— y en pantalla se ve
como un hueco en la numeración. Medido el 07-oct con `estrellashd`, que cayó en
origen y desapareció del listado: la app hizo lo correcto y aun así desconcierta.

La primera forma es para dar de alta y para retirar de verdad, que son
decisiones de alguien, no accidentes de la señal.

**Un canal marcado no necesita `url`.** No se va a abrir nunca: la app enseña el
motivo sin tocar la red. Exigírsela obligaría a inventar una dirección que no
lleva a ninguna parte —el caso real es un stream que ya no existe en
Flussonic—, o a retirar el canal, que es justo lo que se quiere evitar. Un canal
**sin** marcar sigue necesitándola: ahí la falta de `url` es una errata, y
publicarlo daría pantalla negra sin motivo.

**Y una advertencia sobre no publicar: no borres el canal del servicio.** El
`id` es de donde cuelgan los favoritos y el último canal visto del abonado.
Quítalo de lo publicado, consérvalo en tu base.

### `aliases` — para que la búsqueda encuentre el canal

```json
"aliases": ["canal 2", "las estrellas", "xew"]
```

Con 120 canales nadie navega con flechas: busca. Y busca por lo que llama al
canal, no por su nombre comercial exacto. Sin alias, «canal 2» no encuentra
«Estrellas HD».

**La búsqueda de la sección ya los usa.** Están en la misma bolsa que el
nombre, así que basta con que uno de los dos case.

Cómo busca, para que los alias se escriban sabiendo qué hacen:

- **Se ignoran acentos, mayúsculas y signos**, y también los espacios: «canal
  5» encuentra «Canal-5» y «tnt» encuentra «T.N.T.». No hace falta duplicar un
  nombre solo por la puntuación.
- **Lo exacto gana a lo que empieza igual.** Si «Estrellas HD» lleva el alias
  `canal 2`, quien teclea «canal 2» lo ve **antes** que «Canal 22».
- **Una consulta de solo dígitos busca el número de canal**, no los nombres que
  lo contengan.
- **No hay corrección de erratas ni parecidos.** Un resultado aproximado, en una
  lista de 120, esconde el que sí casaba. Si un canal se conoce por un nombre
  mal escrito, el sitio de ese nombre es un alias.

### `rating` y `backupUrl` — datos sin comportamiento, todavía

| Campo | Para qué, el día que se use |
|---|---|
| `rating` | clasificación de contenido, para un control parental con PIN |
| `backupUrl` | segunda fuente del canal, para conmutar si la primera cae |

**Ninguno de los dos hace nada hoy**, y conmutar de fuente exige trabajo de
motor con validación en pantalla. Están en el contrato porque el sitio natural
de ese dato es el `config.json`, y meterlos después obligaría a volver a pasar
por todos los sites.

### Lo que necesita el motor

| Campo | Tipo | Estado | Si falta |
|---|---|---|---|
| `videoCodec` | texto | implementado | la cascada asume hardware y se corrige al abrir el stream |
| `audioCodec` | texto | implementado | solo sirve para contrastar con lo recibido |
| `interlaced` | booleano | implementado | `false` |
| `audioDefault` | texto | **implementado** | la pista que elija Media3 |
| `targetOffsetMs` | entero | implementado | 8000 |
| `enginesAllowed` | lista de texto | implementado | sin filtro |

**Los códecs son una pista, no la verdad.** Si el stream los desmiente, manda
el stream: la app rehace el plan de reproducción y registra
`plan.codecDiscrepante`. Emítelos igual — la cascada los necesita **antes** de
abrir el stream, cuando aún no hay pistas que inspeccionar.

**`interlaced` quiere decir «este canal necesita desentrelazado»**, no el
`field_order` del flujo; son cosas distintas y está medido que discrepan en tres
de los seis canales de referencia (`CHANNELS.md` §3.quinquies). Es el único
campo de esta tabla que la app no puede medir ni corregir por su cuenta, y la
única entrada que manda un canal al reproductor de respaldo en las cajas cuyo
chip no desentrelaza. **No se puede derivar de la API de Flussonic**, que
declara progresivo todo el MPEG-2: tiene que poder corregirlo quien ve el canal
en una tele (`INTEGRACION_CANALES.md` §4).

Valores que la app reconoce en `videoCodec`: `h264`/`avc`, `hevc`/`h265`,
`mpeg2video`/`mpeg2`, `mpeg4`, `vp9`, `av1`. También acepta el MIME directo
(`video/avc`). Lo que no reconoce se trata como «sin declarar».

**`audioDefault` elige la pista al sintonizar**, activo de fábrica desde el
02-oct. El idioma es la **única** clave que sirve, porque Flussonic renumera los
PIDs al remuxar (`CHANNELS.md` §2.bis), y la comparación usa **nuestra**
normalización —`spa`, `esp`, `esl`, `es`, `spanish`, `castellano`— y no la de
Media3, que no casaría `esl` con `spa`.

**Es un valor por defecto, no una imposición.** En algunos satélites la etiqueta
miente: una pista marcada `spa` trae inglés. El abonado puede cambiarla, y su
elección manda para el resto de la sintonía. Emítelo en **todos** los canales.

`targetOffsetMs` tiene **un suelo que no se puede bajar y un techo**, y el que
manda casi siempre es el suelo.

- **Suelo: `3 × EXT-X-TARGETDURATION` del playlist de ese canal.** Es la
  especificación HLS, no una decisión de Media3: sin `EXT-X-SERVER-CONTROL`, el
  margen seguro son tres duraciones-objetivo, y pedir menos no hace nada.
  Emitir la etiqueta **no ayuda**, porque su `HOLD-BACK` tampoco puede valer
  menos que eso.
- **Techo: la mitad de la ventana DVR**, que con `segment_count 12` ronda los
  30 s y rara vez aprieta.

**Calcúlalo por canal desde su `TARGETDURATION` real**, no desde el
`segment_duration` que pediste: Flussonic corta en keyframes, así que el
segmento sale más largo que lo configurado y `TARGETDURATION` redondea hacia
arriba. Medido el 06-oct sobre los seis de desarrollo con `segment_duration 2`:

| `TARGETDURATION` | Suelo | Canales |
|---|---|---|
| 3 | 9 s | `beisbol`, `mileniosd`, `discoveryhd`, `hbohd` |
| 4 | 12 s | `estrellashd` |
| 5 | 15 s | `estrellassd` |

Pedir 8000 en un canal cuyo suelo son 9000 no rompe nada —la app registra
`offset.noSeAplica` y sigue—, pero deja un aviso que nadie puede atender. Pide
el suelo o más.

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

## 5. `epg` *(implementado)*

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
de guía de ese site no esté lista. Lo mismo vale para un canal que está en
`channels` y no en el XMLTV, y para un objeto `epg` sin `url`: se registra y se
sigue sin guía, nunca se tira la configuración.

**`refreshMinutes` manda sobre la descarga, y se respeta.** La caja guarda el
XMLTV y no vuelve a pedirlo hasta que caduque: una petición al día con el valor
de 1440, no una por entrada a la sección. Si falta, se usan 6 h.

**La correspondencia es por `id`, exacta.** El `channel id` del XMLTV tiene que
ser igual que el `id` de `channels`. Lo que no case se descarta, y si **nada**
casa se registra `epg.canalesSinCorrespondencia`, que es la pista de que el
generador está usando otra clave.

**Se acepta `Content-Encoding: gzip`** y se pide siempre. Un XMLTV comprime a
una décima parte, y es tráfico que cruza la LAN del ISP en cada refresco de
cada caja.

Detalles que el generador conviene que sepa:

| | |
|---|---|
| Formato de las horas | `YYYYMMDDHHMMSS ±HHMM`. Sin desfase se asume UTC y la guía saldría corrida: **emítelo siempre** |
| `stop` | **obligatorio en la práctica**: sin él no se sabe cuándo acaba y la barra de progreso mentiría, así que ese programa se descarta |
| `title` | se usa el primero; los demás idiomas se ignoran |
| `desc`, `icon`, `category` | se ignoran hoy. No estorban |
| Tope de descarga | 24 MB **descomprimidos** |
| Qué se conserva | una ventana de unas horas alrededor de ahora, acotada por canal. El fichero entero se guarda en disco |

---

## 6. `brand`

```json
"brand": {
  "displayName": "GarraTV",
  "accent": "#E8A33D",
  "logoUrl": "http://10.0.0.5:8097/marca/logo.png"
}
```

| Campo | Tipo | Estado | Si falta |
|---|---|---|---|
| `displayName` | texto | **implementado** | «TV en vivo», la marca neutra |
| `accent` | texto `#RRGGBB` | **implementado** | gris neutro |
| `logoUrl` | texto | **implementado** | se enseña `displayName` |

**El logo sustituye al nombre, no lo acompaña.** Si hay logo se ve el logo; si
no carga o no hay, se ve el nombre. Nunca los dos: un logo con su nombre al lado
es una cabecera de ISP, y esta es la cabecera de la sección.

**Dónde se ve el acento:** en el nombre de la marca y en los **títulos de grupo**
de la guía. No repinta la app entera: el VOD conserva el tema de siempre
(`CLAUDE.md` §4, regla 2).

**Ranuras fijas, no tema libre** (`CLAUDE.md` §8). No hay forma de enviar un
tema, una hoja de estilos ni una disposición: el servicio **elige entre** lo que
la APK ya sabe hacer, nunca entrega capacidades nuevas (regla 13).

**El contraste se valida al cargar.** Se exige **3:1 contra el negro de la
sección** —el umbral de WCAG para texto grande y elementos de interfaz, que es
lo que el acento va a ser—. Un color que no llegue cae al neutro y se registra
con su medición:

```
marca.colorIlegible accent=#101840 contraste=1.23 minimo=3.0
```

**El resto de la marca se conserva**: un acento malo no tira el nombre ni el
logo. Y pedir 4.5 descartaría colores de marca perfectamente legibles a tres
metros, así que no se pide.

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
6. **No borrar un canal para ocultarlo**: dejar de publicarlo. El `id` sostiene
   los favoritos del abonado.
