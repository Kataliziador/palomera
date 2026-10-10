# INTEGRACION_CANALES.md

**Para quien desarrolla el servicio de canales** (`/home/proyectos/servidor-canales`).

`CONFIG_SCHEMA.md` dice **qué** emitir. Este documento dice **cómo se comporta
la APK de verdad** cuando lo recibe: cuándo pregunta, qué hace con cada cosa,
cómo se degrada y qué no va a hacer nunca.

Leerlo antes de implementar algo que toque la salida.

---

## 1. Si el contrato cambió

```bash
git -C /home/proyectos/jellyfin-livetv-v1 log --oneline -1 -- CONFIG_SCHEMA.md
```

Si no coincide con el commit de la cabecera de tu `docs/CONTRATO.md`, cambió.
Mira el diff antes de copiar:

```bash
git -C /home/proyectos/jellyfin-livetv-v1 log -p <tu-commit>..HEAD -- CONFIG_SCHEMA.md
```

**Los cambios de contrato son siempre compatibles hacia atrás**: campos nuevos,
nunca significados distintos para campos existentes. Si alguna vez hace falta lo
segundo, sube `version` y se avisa aquí.

---

## 2. Cuándo pregunta la APK, y cuántas veces

**Al entrar a la sección, y después cada 15 minutos mientras siga abierta.**
También cuando la caja cambia de servidor.

**Esto cambió el 03-oct y antes aquí decía otra cosa.** Decía «una vez por
entrada a la sección», y era falso: se bajaba **una sola vez por proceso**. Una
caja de salón tiene la app viva días, así que lo que publicabas no llegaba a
nadie que no la reiniciara, y un `message` con `until` podía caducar sin que lo
viera nadie. Lo encontró este proyecto probándolo en un site; está corregido en
la app, no solo en el documento.

Consecuencia para la interfaz del operador: **un cambio tarda hasta 15 minutos
en verse en una caja encendida**, o lo que tarde en reabrirse la app. Conviene
decirlo donde se publica.

- Tiempo de espera: **5 s para conectar, 10 s para leer**. Un servicio que tarde
  más se trata como caído.
- **No hay reintentos inmediatos.** Si falla, se usa la caché y se vuelve a
  intentar en el siguiente ciclo de 15 minutos, o cuando alguien vuelva a
  entrar a la sección.
- **Un documento idéntico no se vuelve a dibujar.** La app compara lo que baja
  con lo que ya tenía y, si no cambió, no toca la pantalla: a quien esté
  navegando la guía no se le mueve el foco cada cuarto de hora. Para ti esto
  solo significa que servir el mismo archivo no cuesta nada.
- Límite de **2 MB**, comprobado **dos veces**: contra la longitud declarada
  antes de leer, y contra lo leído de verdad. Una respuesta troceada ya no se lo
  salta. Pasado el tope **se descarta la descarga entera**, no se trunca: un JSON
  cortado por la mitad daría un error que no diría la causa.
- No se envía ninguna cabecera especial. No hay autenticación (v1).

**Consecuencia para el diseño:** generar el `config.json` **a demanda** es mala
idea. Si tardas 12 s en consultar Flussonic, la app ya se rindió. Genera por tu
cuenta, sirve un archivo ya hecho.

---

## 3. La escalera de respaldo

La app nunca se queda sin lista. Por orden:

```
1. Registro de sites (channelsUrl)   → si dice dónde estás
2. Derivar: host de Jellyfin, :8097  → la convención
3. Caché de ese servidor             → lo último bueno que bajó
4. Lista de arranque del BuildConfig → los seis canales de desarrollo
```

Dos cosas que esto implica y conviene tener presentes:

- **Una lista vieja gana a ninguna.** Si tu servicio responde con un documento
  roto, la app **conserva** la caché y sigue con ella. No se "limpia".
- **Si la app acaba en el escalón 4, está enseñando canales de desarrollo**, con
  URLs del Flussonic de pruebas. Sale en el log como `catalogo.desdeArranque`.
  Si lo ves en un site de cliente, algo va muy mal.

La caché se guarda **por servidor** (`config-<uuid>.json`). Una caja que cambie
de site no hereda la lista de la anterior.

---

## 4. Qué hace la app con cada cosa, de verdad

### Lo que descarta en silencio y lo que tira entero

| Situación | Qué pasa |
|---|---|
| Falta `version` | **se rechaza el documento entero**, se conserva la caché |
| No hay ni un canal utilizable | **se rechaza el documento entero** |
| Un canal sin `id`, `name` o `url` | se descarta ese canal, el resto sigue |
| URL que no empieza por `http`/`https` | se descarta ese canal |
| `id` repetido | se descarta el segundo |
| Grupo no declarado | el canal queda suelto, bajo «Otros» |
| `version` mayor de la que entiende | **se usa igual**, ignorando lo que no reconoce |
| Campo desconocido | se ignora en silencio |

**Que un canal se descarte no es visible en pantalla.** Solo en el log
(`config.canalDescartado indice=N motivo=…`). Un lineup con tres canales mal
formados se ve como un lineup de tres canales menos, sin más.

### El orden de la guía

`number`, luego `order`, luego el nombre. Un canal sin `number` va después de
todos los que sí lo traen.

«Favoritos» no se declara: la app lo construye con lo que marque el abonado y va
siempre primero. Un canal favorito aparece **dos veces**, arriba y en su grupo.

### `defaultChannel` solo decide la **primera** vez

La app recuerda el último canal que se sintonizó en esa caja y le da el foco al
volver a la guía. `defaultChannel` es el respaldo para cuando **no hay
historia** —una caja recién instalada— y para cuando el último canal ya no está
en el lineup.

O sea: no sirve para «empujar» a los abonados a un canal. Elígelo pensando en el
primer arranque, que es cuando alguien se forma una opinión del servicio.

### El aviso del operador se dibuja en la sección, no sobre el vídeo

Es información del ISP, no del canal, y taparle la imagen al abonado con el
recado de su proveedor no sería un intercambio justo. Va bajo la marca, discreto.

**Pon siempre `until`.** El fallo previsible no es el texto: es el aviso que
nadie quitó.

### Un canal caído: márcalo, no lo quites

`unavailable: true` **está implementado y completo**:

- **En la guía**, la tarjeta lleva una etiqueta roja de «sin señal» en lugar
  del distintivo HD. El abonado lo ve **antes** de entrar, que es cuando
  sirve.
- **Al sintonizarlo**, se enseña su nombre y `unavailableMessage` en vez de
  intentar reproducir. No se abre decodificador ni se toca la red.

**Hecho el 07-oct en vuestro proyecto** (`9c13b50`): los cinco motivos que
venían de Flussonic publican ahora el canal marcado en vez de retirarlo.

Lo que la app pone de su parte: **a un canal marcado no se le exige `url`.** No
se va a abrir nunca, así que inventarle una dirección que no lleva a ninguna
parte —el caso del stream que ya no existe— sobraba. Un canal **sin** marcar
sigue necesitándola.

Hoy un stream apagado desaparece del `config.json`, y eso en la caja se ve como
**un canal que se esfuma del listado sin explicación**. Pasó de verdad el
07-oct: `estrellashd` cayó en origen y la app pasó de 10 canales a 9 en el
siguiente refresco. La app hizo lo correcto —enseña lo que publicáis— y aun así
desconcierta a quien lo mira. Un hueco en la numeración es peor que un canal
marcado: el abonado cree que lo perdió.

**El motivo por el que lo descartasteis ya no existe.** Vuestra bitácora dice
que un canal marcado «no disponible» seguiría sin poder verse días después de
volver, porque *«la APK baja la lista una vez por arranque»*. Eso era cierto y
dejó de serlo: **la lista se recarga cada 15 minutos** mientras la sección está
abierta, y entera al arrancar la app. Un canal que vuelve se destapa solo en el
siguiente ciclo, igual de rápido que se marcaría.

Los dos relojes, para que el cálculo esté a la vista:

| | Cadencia |
|---|---|
| Vosotros leéis Flussonic | cada 30 s |
| La app recarga la lista | cada 15 min, con la sección abierta |

O sea: **como mucho ~15 min desde que el stream vuelve hasta que el abonado lo
ve normal**, y lo mismo para marcarlo cuando cae. Retirarlo no es más rápido
—usa exactamente los mismos dos relojes—, solo es más desconcertante.

**Si aun así preferís retirarlo** en algún caso, lo que pedimos es histéresis:
no retirar hasta que lleve caído un buen rato. Un corte de un minuto no debería
borrar un canal del listado durante quince.

### El endpoint es `index.m3u8`, y no es un detalle

**`mono.m3u8` solo expone una pista de audio a Media3.** Medido el 02-oct sobre
un segmento de `discoveryhd` con dos PIDs (`0xdd` spa, `0xde` eng): por el media
playlist la app veía **una** pista; por el master, **tres**.

La causa es cómo se declaran: el master publica los audios como `EXT-X-MEDIA`
separadas y Media3 los trata como renditions; multiplexados en el mismo TS, el
extractor solo saca uno.

**Importa más de lo que parece:** en algunos satélites la etiqueta de idioma
miente —una pista marcada `spa` trae inglés— y si la app solo expone una, el
abonado no tiene salida.

**Emite siempre `audioDefault`**, en todos los canales. La app lo usa como valor
por defecto y el abonado puede cambiarlo; su elección manda para el resto de la
sintonía.

### Lo que la app mide y prefiere sobre lo que digas

- **La insignia HD** es `hd || alturaMedida >= 720`. Tu campo ayuda antes de
  abrir el canal; después manda la medición.
- **El códec** es una pista. Si el stream lo desmiente, manda el stream, la app
  rehace su plan de decodificación y registra `plan.codecDiscrepante`. **Emítelo
  igual**: la cascada decide antes de abrir el stream.
- **El aspecto** sale del SAR del stream, nunca del `config.json`.

### `targetOffsetMs`: no mandéis 8000 a ciegas, calculadlo de la ventana

Nuestro valor de fábrica es 8000 y **es un último recurso, no una
recomendación**. Medido en la caja el 09-oct sobre un canal con ventana DVR de
32 s, misma caja y mismo origen en las dos columnas:

| `targetOffsetMs` | Dónde se asienta la app | Buffer | Cortes |
|---|---|---|---|
| 8000 | **11,3 s** del directo | 3,6 a 10 s | **15 por hora** |
| 14000 | 15,9 s | 6,9 a 13,5 s | **0 en 38 minutos** |

Dos cosas que explican la tabla:

1. **Lo que pidáis no es donde se asienta.** Medido dos veces —8000 → 11,3 s el
   09-oct, 14000 → 16,7 s el 10-oct—, lo que encaja es:

   ```
   real ≈ max(pedido, 3 × TARGETDURATION) + un segmento
   ```

   El suelo está ahí porque sin `EXT-X-SERVER-CONTROL` la especificación HLS lo
   fija así, pero **no siempre es el que manda**: en `mileniosd`
   (`TARGETDURATION 3`) ese suelo son 9 s y el offset real fue 16,7, o sea los
   14 000 pedidos más un segmento de 2,8. La reproducción solo puede empezar en
   un límite de segmento. Lo veréis en `offset.noSeAplica`, que desde el 10-oct
   publica también el suelo real para que se sepa cuál de los dos pesa.
2. **El techo del buffer es la distancia al directo**, no la configuración de la
   app: no se puede tener cargado más de lo que hay publicado por delante. Un
   offset pequeño es un colchón pequeño, y entonces cualquier jitter del origen
   —medido: uno de cada cinco segmentos llega ~1 s tarde— se nota.

Así que el generador debería publicarlo **por canal, calculado de la ventana que
mida**. El servicio de canales lo hace desde el 09-oct: el 45 % de la ventana,
con suelo en 8000 y techo en 14000. El 45 % y no la mitad porque **la app vuelve
al borde por encima de media ventana más tres segundos**: un valor pegado a ese
límite lo dispara cualquier parpadeo, y entonces sale `offset.vuelveAlBorde`.

> **Y ese 45 % no basta, por el segmento de más.** Medido el 10-oct en
> `mileniosd`: ventana 33,6 s, pedido 14 000 —el techo—, real **19,0-19,5 s**, y
> el umbral de la vuelta al borde en 16,8 + 3 = 19,8. Quedan entre 300 y 800 ms.
> **No bajéis el offset por esto:** subirlo a 14 000 es lo que llevó los cortes
> de 15 por hora a cero, y estrecharlo para esquivar nuestro umbral cambiaría un
> problema real por uno nuestro. **El arreglo es de la app** y está apuntado
> (pendiente 72 de `LIVETV_LOG.md`): el umbral debe medirse contra el offset
> alcanzable, no contra una fracción de la ventana.

### Un decoder que existe y entrega mal: `blockedDecoders`

Nuevo el 09-oct, en los `deviceOverrides` (`CONFIG_SCHEMA.md` §7). Es **nuestro**,
no del ISP, y lo inyecta el generador como el resto de esa tabla.

Sale de medir la Skyworth: su único decoder de MPEG-2 declara estar ahí y
entrega **la mitad de la cadencia** —15,0 fps sobre una fuente de 29,97, con
cero fotogramas perdidos—, que en pantalla se ve como peine e imagen lenta.
Nada en `MediaCodecList` lo delata, así que la caja no lo puede descubrir sola.

Vetado por nombre, ese códec se queda sin decoder de plataforma y la cascada
cae al reproductor de respaldo por el camino que ya existía. **Lo que no se usa
para esto es `vpuDeinterlaces: false`**, y conviene entender por qué, porque es
el error que parecía la solución: el chip de esa caja **sí** desentrelaza el
H.264 1080i, y declararlo al revés se llevaría a software tres canales que van
perfectos por hardware — medido, del 14 % de CPU al 63 %.

La regla general: **`vpuDeinterlaces` habla de una capacidad del modelo;
`blockedDecoders` habla de una pieza concreta que funciona mal.** Un defecto de
un códec se nombra por el decoder.

### `interlaced` es el único campo que no podéis calcular

Y es el único que la app **no puede medir ni corregir por su cuenta**, al
contrario que los tres de arriba. Si llega mal, se queda mal.

**Qué significa, exactamente:** «este canal necesita que alguien lo
desentrelace». No es el `field_order` del flujo. Lo decimos así porque son
cosas distintas y lo tenemos medido: de los seis canales de desarrollo, tres
declaran entrelazado y llevan cine progresivo con cadencia 3:2 —`CHANNELS.md`
§3.quinquies, 286 imágenes analizadas por canal—. El flujo dice una cosa y el
contenido es otra.

**Para qué lo usa la app.** Es la única entrada que hace que la cascada pida
desentrelazado, y con ella que un canal vaya al reproductor de respaldo
(libVLC, por software) en las cajas cuyo chip no desentrelaza —nivel 2 de
`LIVETV_ENGINE.md` §4.4, gobernado por `vpuDeinterlaces: false` en
`deviceOverrides`—. **Publicado como progresivo, ese respaldo no se activa
nunca**, y en esas cajas el abonado ve peine en los movimientos.

**No lo saquéis solo de la API de Flussonic.** Medido el 09-oct contra el
headend de pruebas, con `select=stats.media_info`:

```
test-sat77w-estrellassd-mp2v  mp2v 480   is_progressive=true    ← medido 480i tt
test-sat77w-estrellashd-mp4   h264 1080  is_progressive=false   ← cine progresivo 3:2
```

No es que el dato falte y se pueda tratar como «no se sabe»: **afirma lo
contrario de lo que hay**, y en MPEG-2 pasa siempre. `field_order` no viene en
la respuesta de la API v3.

**Lo que pedimos, y ya está hecho:** que el operador pueda corregir ese campo a
mano en el lineup de su site, aunque el canal salga de un `stream`. El servicio
de canales lo implementó el 09-oct —en el panel es «Barrido», dentro de «La
señal»— y lo escrito gana sobre Flussonic; vacío sigue siendo lo que diga la
API. Los otros tres campos calculados (`url`, `videoCodec`, `audioCodec`) siguen
siendo un error escritos a mano, y ahí tienen razón: Flussonic sí los sabe.

**La regla para decidirlo es de pantalla:** si en la tele se ve peine, va
entrelazado. No hay forma de contestarlo desde una API, y por eso es la primera
pregunta del `config.json` que exige mirar un canal.

Si falta, sigue siendo `false` y no cambia nada de lo que ya funciona: la app
lee el campo igual que antes y no hay que subir `version`.

---

## 4.bis La guía (EPG): sí, y con gzip

**Implementado el 05-oct.** Preguntabais en `PETICION_APK` §3.2 si la app
acepta `Content-Encoding: gzip`: **sí, y lo pide siempre**. Podéis activarlo.

Lo que la app hace con `epg: { url, refreshMinutes }`:

- **Descarga el XMLTV a disco, no a memoria.** Por eso el tope es alto —24 MB
  ya descomprimidos— y no el de 2 MB del `config.json`. Ese límite no os afecta
  porque la guía **no viaja dentro** del `config.json`, que era el motivo de
  separarlos.
- **Respeta `refreshMinutes`.** Con 1440 es una descarga al día, no una por
  entrada a la sección. Lo pedíais en §3.1 y está así.
- **La caché se indexa por servidor.** Una caja que cambia de site no hereda la
  parrilla del anterior.
- **Relee lo guardado cuando el tiempo sale de la ventana** que tiene en
  memoria, sin volver a la red. Una caja encendida toda la noche sigue
  enseñando programación por la mañana aunque la guía del servidor no haya
  cambiado.

### Lo que se descarta, y lo que eso significa para vosotros

Nada de esto tira la guía entera: se descarta la entrada y se cuenta.

| Qué | Por qué |
|---|---|
| `<programme>` sin `stop` | sin fin no se sabe cuánto queda, y la barra de progreso mentiría. **Emitidlo siempre** |
| `<programme>` sin título, sin `start` o con fechas ilegibles | no hay nada que enseñar |
| Un `channel` que no está en `channels` | normal si servís la guía de un lineup mayor. Si **no casa ninguno**, se registra `epg.canalesSinCorrespondencia` y eso significa que la clave no es la que creemos |
| Lo que cae fuera de la ventana | se cuenta como `fueraDeVentana`, no es un error |

**Las horas sin desfase se toman como UTC.** La especificación de XMLTV dice
que sin él son hora local del generador, y eso no se puede saber desde la caja:
puede estar en otro huso que el site. Vuestro `+0000` es lo correcto —**seguid
emitiéndolo siempre**—; sin él, la guía saldría corrida unas horas, que es
visible pero molesto.

**`desc`, `icon` y `category` se ignoran** por ahora y no estorban. Emitidlos si
ya los tenéis.

### Cómo comprobar que llegó

```
epg.descargada bytes=90078 comprimido=false
epg.interpretada canales=7 programas=59 descartados=0 fueraDeVentana=142 canalesDesconocidos=0
epg.lista origen=RED canales=7 programas=59 antiguedadMin=0
```

`descartados=0` y `canalesDesconocidos=0` son lo que queréis ver. Si
`canales=0` con `canalesDesconocidos` alto, los `id` no casan.

---

## 5. Lo que se interpreta pero todavía no se usa

Emítelo de todas formas: el día que se implemente, los sites ya lo traerán y no
habrá que volver a pasar por ellos.

| Campo | Estado | Pendiente |
|---|---|---|
| `logoUrl` | **implementado**: guía y barra, con iniciales de respaldo | — |
| `audioDefault` | **implementado**: elige la pista al sintonizar | — |
| `message` | **implementado**: se dibuja en la sección y caduca solo | — |
| `brand` | **implementado**: nombre, logo y acento | — |
| `defaultChannel` | **implementado**: decide el foco la primera vez | — |
| `extras` | **implementado**: barra del canal y cabecera de la sección | — |
| `aliases` | **implementado**: la búsqueda de la sección los usa | — |
| `rating`, `backupUrl` | se leen; sin comportamiento por diseño | — |
| `epg` | **implementado**: programa actual y siguiente en la barra (§4.bis) | la guía por canal en rejilla, no |
| `unavailable` | **implementado y completo**: etiqueta «sin señal» en la tarjeta y aviso al entrar | — |

**Los `aliases` ya se buscan.** La sección tiene búsqueda: se abre con «Buscar
canal», filtra mientras se escribe y casa contra el nombre **y** los alias,
ignorando acentos, mayúsculas, signos y espacios. Un alias **exacto** gana a un
nombre que solo empieza igual —«canal 2» enseña «Estrellas HD» antes que «Canal
22»—, y una consulta de solo dígitos busca el número.

No hay corrección de erratas: si a un canal se le llama de una forma concreta,
esa forma va en `aliases`. Son el único sitio donde cabe el nombre que usa el
abonado y no el comercial.

**`audioDefault` ya elige la pista al sintonizar.** Emítelo en **todos** los
canales: es la única clave que sirve, porque los PIDs los renumera Flussonic. Y
es un valor por defecto — si la etiqueta del satélite miente, el abonado lo
cambia y su elección manda.

**Los `extras` ya se dibujan, y en dos sitios distintos.** Los del canal, en la
barra, bajo «Luego»; los de la raíz, en la cabecera de la sección, bajo el
nombre del proveedor. Los primeros hablan del canal, los segundos del servicio,
y **no hay un tercer sitio**: las tarjetas de la guía no los enseñan, porque con
120 canales una tarjeta tiene que leerse de un vistazo.

Los topes son **6 entradas, 24 caracteres de etiqueta y 40 de valor**, y lo que
sobra **se recorta en silencio**: sin `label` o sin `value` la entrada
desaparece, y una etiqueta larga se publica cortada. El abonado no ve ningún
aviso —leería media palabra y nada más—, así que el único rastro es
`config.extrasRecortados de=<canal|raíz> recibidos=N publicados=M`. **Si lo ves
en tu log, el texto que escribiste no es el que sale en pantalla.**

**`logoUrl` se dibuja en la guía y en la barra**, con las iniciales del canal
como respaldo cuando falta **o cuando la imagen no carga**. La imagen se ajusta
dentro sin recortarse, así que un logo apaisado se ve entero.

Una URL rota no deja un hueco: cae a las iniciales. **Una errata en el logo no
se nota en pantalla** — se nota mirando que el logo no sale. Y los nombres de
archivo deben ser inmutables: la app cachea por URL y no invalida.

---

## 6. Lo que la APK no va a hacer

No lo pidas ni lo diseñes esperándolo:

- **No transcodifica nada.** Ni vídeo ni audio. Si llega en un códec que la caja
  no decodifica, no suena.
- **No lleva decoders de MP2, MP3 ni AC-3**, y no los va a llevar. Por eso la
  regla del AAC no es una preferencia.
- **No hace ABR**, pero desde el 02-oct **sí lee el master**: el endpoint es
  `index.m3u8`. Emite un master con **un solo `EXT-X-STREAM-INF`**.

  **La mayoría del lineup real no cumple eso por defecto:** las plantillas
  `*-ABR` de Flussonic dan 576p y 720p. Publica
  `index.m3u8?filter=tracks:v<N>a<M>…` y el master sale con una calidad y todos
  los audios, sin tocar el headend.

  Si se te cuela un master con varias, **la app no puede impedirlo**: registra
  `abr.variasVariantes` y Media3 se pone a conmutar calidad, que §6 prohíbe. Ese
  aviso es el único rastro que queda.
- **No ejecuta nada que venga del servidor.** Ni código, ni plantillas, ni hojas
  de estilo. Texto, números, listas e imágenes.
- **No graba, no hace catch-up, no tiene DVR.**
- **No consulta a Jellyfin para nada** relacionado con canales.

---

## 7. Cómo se prueba contra una caja

### Apuntar una caja a tu máquina

```bash
D=192.168.20.132:5555
adb connect $D

cat > /tmp/livetv.dev.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="configUrlOverride">http://TU_IP:8097/config.json</string>
    <int name="overlayMode" value="1" />
</map>
XML

adb -s $D shell am force-stop org.jellyfin.androidtv.debug
adb -s $D push /tmp/livetv.dev.xml /data/local/tmp/d.xml
adb -s $D shell run-as org.jellyfin.androidtv.debug sh -c \
  "'cat /data/local/tmp/d.xml > /data/data/org.jellyfin.androidtv.debug/shared_prefs/livetv.dev.xml'"
```

`overlayMode 1` enciende el overlay compacto, que enseña decodificadores y
códecs en pantalla. Quitar el override es borrar ese archivo.

El override **usa su propia clave de caché**, así que apuntar una caja a tu
portátil no le envenena la caché del site real.

### El veredicto está en el log

```bash
adb -s $D logcat -v time | grep -i livetv
```

| Línea | Qué significa |
|---|---|
| `catalogo.destino url=… procedencia=…` | de dónde está leyendo |
| `config.interpretada version=1 canales=6 grupos=4` | **lo entendió** |
| `config.desdeRed canales=6` | lo bajó de ti, no de su caché |
| `catalogo.listo origen=RED` | lo está enseñando |
| `config.canalDescartado indice=N motivo=…` | un canal mal formado |
| `config.grupoDesconocido canal=X grupo=Y` | grupo no declarado |
| `config.malformada` | no se pudo interpretar; se quedó con la caché |
| `config.desdeCache antiguedadHoras=N` | **no te llegó a leer** |
| `audio.noNormalizado canal=X observado=…` | **se coló un canal sin AAC** |
| `config.extrasRecortados de=… recibidos=N publicados=M` | tus `extras` no salen enteros |

**Si no aparece `config.interpretada`, tu documento no sirve**, por bien que se
vea en un navegador.

### Sin caja

El parser tiene pruebas y el esquema es estable, así que casi todo se caza
validando el JSON contra el contrato en tus propias pruebas. La caja es para
confirmar, no para iterar.

---

## 8. Si el contrato se te queda corto

No lo amplíes por tu cuenta: la app ignoraría el campo nuevo y acabaríais con
dos contratos.

Pídelo en el proyecto de la APK, con el caso de uso concreto. Lo que se añade
fácil: campos nuevos opcionales, `extras` para texto que solo se muestra. Lo que
cuesta: cualquier cosa que necesite comportamiento nuevo en la app, que es una
versión — una, para todos los ISPs.
