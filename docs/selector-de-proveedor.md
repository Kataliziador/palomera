# Selector de proveedor — diseño

**Estado: aprobado e implementado el 2026-10-30.** Falta la validación en
pantalla de Edgar (§7) y, en el autorizador, renumerar la migración cuando
aterricen las dos ramas en vuelo (§5.1).

Toca los tres proyectos, así que vive aquí: `CLAUDE.md` §1 dice que este
repositorio es el que escribe los contratos.

---

## 1. El problema

El abonado estrena la caja y lo primero que ve es **«Escribe la dirección del
servidor»**. Lo que hay que escribir hoy es `http://192.168.20.249:8096`.
Ningún abonado sabe eso, y el técnico que lo teclea por él se equivoca.

La derivación de §6 ya evita la **segunda** dirección —la del servicio de
canales sale del host de Jellyfin—, pero la primera sigue ahí.

---

## 2. Lo que no se hace, y por qué

La petición original era un **desplegable con todos los proveedores**. No se
hace así, por dos razones y la segunda es de arquitectura, no de opinión.

**Publicaría la cartera de clientes.** Un endpoint que enumere los sites deja
que cualquiera que instale la app —la competencia incluida— vea qué ISPs usan
Palomera, cuántos son y cuándo uno desaparece de la lista. Y de paso entrega un
inventario de servidores Jellyfin a quien quiera probarlos. El repositorio es
público: la dirección del endpoint se sabe.

**Y las direcciones de hoy no sirven para elegir.** Son de LAN
—`192.168.20.249`, `192.168.11.102`— y sólo funcionan dentro de esa red. Peor:
en el registro, `alias.host` es **clave primaria**, así que un host pertenece a
un site y sólo a uno en todo el sistema. El día que dos ISPs tengan su Jellyfin
en `192.168.1.10`, **el segundo no se puede dar de alta**. Un selector de
proveedor exige que cada site tenga **un nombre DNS público y único**; eso es
una condición de despliegue, y conviene saber que esta función la impone.

---

## 3. La cascada

Misma forma que la de §6, y por el mismo motivo: ninguna de las tres cubre
todos los casos, y la última nunca puede fallar.

| | cuándo entra | a quién sirve |
|---|---|---|
| **1. por IP pública** | el registro reconoce la red desde la que se llama | el abonado en su casa. Cero teclado |
| **2. por código** | no la reconoce, o no hay rangos de ese ISP | los probadores de la prueba cerrada, el técnico que aprovisiona desde la oficina, quien está en datos móviles, el ISP bajo CGNAT |
| **3. escribir la dirección** | **siempre disponible** | la caja que llega a la red del ISP pero no a internet (§6 dice que pasa), y el revisor de Google (`PLAY.md` §6) |

**La 2 no es un parche para las pruebas.** Es la ruta permanente de todo lo que
la IP no puede resolver; que sea también la que necesitan los 12 probadores es
una coincidencia afortunada, no su razón de ser.

**La 3 no se quita nunca.** Es la única que funciona sin internet, y quitarla
dejaría la app sin camino de alta el día que el registro no conteste.

---

## 4. El contrato: un endpoint nuevo, que nunca lista

```
POST https://<registro>/v1/proveedor
{
  "appVersion": "0.1.0-rc.1",
  "installId": "…",
  "codigo": "REDTV-4821"        // opcional
}
```

- **Sin `codigo`** → el registro mira la **IP de origen** de la petición y
  busca el site cuyos rangos la contengan.
- **Con `codigo`** → busca por código, y la IP no se mira.

```json
{
  "proveedor": {
    "id": "redtv",
    "nombre": "RedTV",
    "serverUrl": "https://tv.redtv.mx:8096"
  },
  "brand": { "displayName": "RedTV", "accent": "#E8A33D", "logoUrl": "…" }
}
```

Sin coincidencia:

```json
{ "proveedor": null }
```

**Tres reglas que definen la seguridad de esto:**

1. **Nunca devuelve más de un proveedor**, y no existe ningún endpoint que
   liste. Lo que no se puede enumerar no se puede filtrar.
2. **Un código no es un secreto**, y se escribe aquí para que nadie lo trate
   como si lo fuera: con códigos cortos se puede probar a lo bruto, y eso
   recupera parte de lo que la decisión de no listar evitaba. Las defensas son
   **límite de peticiones por IP** y **códigos no correlativos**
   (`REDTV-4821`, no `001`). Es un tope de velocidad, igual que el registro
   entero es «un interruptor operativo sobre gente que coopera» (§6) y no un
   candado.
3. **`serverUrl` lleva esquema y puerto.** Es lo que la app le pasa tal cual a
   la pantalla de alta, así que una errata aquí manda la caja a ninguna parte y
   el síntoma sale semanas después, en la sala de un abonado. Se valida igual
   que `channelsUrl`: lo que no es `http://` ni `https://` se descarta y se
   registra.
4. **Y es un solo campo con dos contenidos posibles** —añadido el 31-oct—. El
   registro guarda la dirección pública y la de dentro de la red del ISP, y
   entrega la que toca: **por IP, la de LAN**, porque acertar por IP significa
   que la caja está dentro; **por código, la pública**, porque puede estar en
   cualquier parte. La app no sabe que había dos.

   Sin esto, la ruta 1 entrega a una caja interna una dirección que sólo
   funciona con hairpin NAT o DNS de horizonte partido: «no se pudo conectar»
   en todas las cajas de ese ISP a la vez. **Y la prueba cerrada no lo habría
   visto**, porque sus doce probadores están fuera y entran por la ruta 2.

**Por qué no se reutiliza `/v1/resolve`:** esa pregunta es «¿este host está
autorizado?», y aquí **todavía no hay host**. Son preguntas distintas y
mezclarlas haría que el campo obligatorio de una fuera opcional en la otra.

---

## 5. Los cambios, por proyecto

### 5.1 Autorizador (`/home/proyectos/autorizador`)

| | |
|---|---|
| migración | `sites` gana `codigo TEXT` (índice único) y `servidor_url TEXT`; tabla nueva `rangos (site_id, cidr)` |
| `db.py` | `buscar_site_por_codigo`, `buscar_site_por_ip`, `rangos_de`, `fijar_rangos` |
| `app.py` | `POST /v1/proveedor`, con el mismo trato que `resolve` ante `BaseNoDisponible`: **503, nunca «no hay proveedor»** por un fallo nuestro |
| límite | reaprovecha la tabla `intentos`, que ya existe para el panel |
| panel | en la ficha del site: código (autogenerado al crear, visible para dictarlo por teléfono), `servidor_url` y la lista de CIDR |
| auditoría | los tres campos nuevos pasan por `registrar`, como el resto |
| pruebas | por código, por IP, sin coincidencia, con la base caída, y el límite |

**Aviso de numeración, y no es menor:** `main` lleva **4** migraciones,
`recuento-de-abonados` añade la 5 y `guardar-el-inventario-de-las-cajas` llega
a **6**. Las dos ramas en vuelo ya chocan entre sí. Ésta se escribe **desde
`main`** y se renumera a lo que quede libre cuando aterricen las otras dos; el
número no se elige hoy.

### 5.2 APK — módulo `livetv`

| | |
|---|---|
| ~~`config/ProveedorRepository.kt`~~ → `RegistryRepository.proveedor()` | **cambiado al escribirlo.** El plan decía un repositorio hermano, y habría duplicado la conexión, los tiempos de espera, el `installId` y —lo que importa— la llamada a `RegistryTrust`. Es el mismo registro contestando otra pregunta, así que va en el mismo cliente, con `abrirConexion()` compartida: dos sitios donde poner la confianza son dos sitios donde olvidarla |
| `config/Proveedor.kt` | el modelo y su parser, con la validación de `serverUrl` |
| pruebas | parser con respuesta buena, con `proveedor: null`, con URL ilegible y con JSON roto |

No depende de `app` (§4.1).

### 5.3 APK — módulo `app`

La pantalla **no es nueva**: es un bloque encima del campo de dirección en
`fragment_server_add.xml`, que ya es territorio permitido desde el 30-oct.

- al abrirse, pregunta **sin código**. Si hay coincidencia, enseña
  **«¿Eres cliente de RedTV?»** con un botón de confirmar;
- si no la hay, enseña el **campo del código**;
- debajo, siempre, **«Escribir la dirección»**, que es lo que hay hoy.

Y lo que hace al confirmar es **lo que ya existe**:
`ServerAddViewModel.addServer(serverUrl)`, el mismo camino que el campo de
texto. El selector produce una dirección; no inventa un camino de alta nuevo.

| | |
|---|---|
| `ServerAddFragment.kt` | la lógica. **Entra en §4.7** con su motivo |
| `fragment_server_add.xml` | el bloque nuevo |
| `values/strings.xml` | cadenas `livetv_*`, nunca claves de upstream: las de upstream vienen traducidas a 40 idiomas y sobrescribirlas en `values/` dejaría el texto viejo en 39 |
| `comprobar.py` | el archivo nuevo en `PERMITIDO`, con el comentario de por qué |

### 5.4 Contratos

`REGISTRY_SCHEMA.md` gana la sección del endpoint y `INTEGRACION_REGISTRO.md`
el comportamiento de la app, **en el mismo commit** — la regla de sincronía de
`CLAUDE.md` §1. El autorizador lleva su copia y se actualiza con él.

---

## 6. Para la prueba cerrada

Los 12 probadores estarán en redes cualesquiera, así que ninguno entra por la
ruta 1: **todos usan la 2, con el mismo código**, apuntando al site `demo`.

Comprobado desde fuera el 30 y el 31-oct, no supuesto:

| | |
|---|---|
| `xtsrt01.ddns.net:8096` | `ServerName: Palomera Demo`, Jellyfin 10.11.11 |
| `:8097/config.json` | HTTP 200, 3 grupos y 8 canales, `brand: null` |
| `:8097/epg.xml` | HTTP 200, 1,2 MB, 8 canales y 5 576 programas |
| los **ocho** `:8181/demoN/index.m3u8` | HTTP 200, una variante, `avc1.64001f,mp4a.40.2` |
| `/v1/resolve host=xtsrt01.ddns.net` | `authorized: true`, `until` a 21 días, `channelsUrl` público |
| `/v1/proveedor` | **404** — la rama no está desplegada |

**El alias público ya estaba.** Este documento decía que el site «hoy tiene el
de la LAN» y era falso; lo que falta son `servidor_url` y `codigo`, y faltan
porque la migración no está desplegada.

**Y los ocho canales del demo son cartas de ajuste H.264/AAC con una sola
pista de audio.** Cero `EXT-X-MEDIA` en los ocho masters. Así que por esta
ruta la prueba cerrada ejercita el alta, la sesión, la guía y el zapeo, y **no
ejercita nada del Modo A**: ni MPEG-2, ni libVLC, ni cambio de pista, ni SAR
anamórfico, ni entrelazado, ni CEA-608. Lo que sí se cosecha es el inventario
de decoders de cada caja, porque viaja con `/v1/resolve` y no depende de lo
que se reproduzca — el pendiente 4 sigue en pie.

---

## 7. Lo que esto no resuelve

- **La IP falla** con CGNAT, con datos móviles y con el ISP que no nos dé sus
  rangos. Por eso la ruta 2 no es opcional.
- **Cada site de producción necesita un nombre DNS público**, por lo de la
  clave primaria de §2.
- **No es Modo B puro.** Toca la pantalla de alta, que es la puerta de entrada
  **compartida con el VOD**: añade un camino y no quita el que hay, pero
  `CLAUDE.md` §4.7 ya establece que un cambio de comportamiento en una pantalla
  compartida **exige validación en pantalla de Edgar**, y ésta la exige.

---

## 8. Orden

**Esto y la subida a la prueba cerrada no compiten: van en paralelo.** La clave
de firma la genera Edgar y el selector lo escribo yo; no se tocan. Y una prueba
cerrada **se actualiza durante los 14 días**, así que si la build sube antes
que el selector, el selector les llega como actualización sin reiniciar el
reloj.

Lo único que no admite espera es el reloj.
