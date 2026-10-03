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

**Una vez por entrada a la sección de canales**, no por arranque ni por canal.
La app recuerda de qué URL cargó y no repite mientras no cambie.

- Tiempo de espera: **5 s para conectar, 10 s para leer**. Un servicio que tarde
  más se trata como caído.
- **No hay reintentos.** Si falla, se usa la caché y se vuelve a intentar la
  próxima vez que alguien entre a la sección.
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

### Un canal no disponible sí hace algo

`unavailable: true` **está implementado**: el canal sigue en la guía, y al
sintonizarlo se enseña su nombre y `unavailableMessage` en vez de intentar
reproducir. No se abre decodificador ni se toca la red.

Lo que **todavía no** hace: atenuar la tarjeta en la guía. El abonado se entera
al entrar al canal, no antes.

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
| `aliases` | se lee; **no hay búsqueda** en la sección | B2 |
| `rating`, `backupUrl` | se leen; sin comportamiento por diseño | — |

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
