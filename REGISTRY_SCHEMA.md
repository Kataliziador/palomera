# REGISTRY_SCHEMA.md

**Contrato entre el registro de sites y la APK.**

El registro es un servicio nuestro, en la nube, con una fila por site. Hace dos
cosas a la vez, y conviene no confundirlas:

1. **Autoriza.** Dice si el servidor que el abonado escribió pertenece a un ISP
   con contrato vigente.
2. **Liga.** Dice dónde está el servicio de canales de ese site y con qué marca
   se viste la app.

Documentos hermanos: `CONFIG_SCHEMA.md` (lo que sirve el site), `CLAUDE.md` §6
(cómo encaja con lo demás) y §8 (marca).

---

## 1. Por qué existe, y por qué no es lo que parece

**Lo que de verdad compra** no es el control de acceso, es **quitar la
convención del puerto**. Sin registro, la APK encuentra el servicio de canales
cambiando el puerto de Jellyfin por una constante compilada: moverlo obliga a
tocar todos los sites a la vez que una versión de la app. Con registro, cada
site dice dónde está el suyo y cambiarlo es editar una fila.

| | Sin registro | Con registro |
|---|---|---|
| Dar de alta un ISP | instalarle el servicio | **+ una fila** |
| Quitarle el acceso | ir a su site | una fila |
| Mover el servicio de canales | imposible sin publicar versión | una fila |

**Lo que no es: un candado técnico.** Este fork es GPL-2.0, el código es público
por obligación, la comprobación se ve a simple vista y cualquiera puede compilar
una versión sin ella. Una APK de Play se decompila en minutos.

Lo que sí es: **un interruptor operativo sobre gente que coopera.** Un ISP con
contrato al que se deja de autorizar queda fuera sin visitar su site, y evita
que alguien apunte la app a un Jellyfin cualquiera por descuido. Para eso basta.
Lo que no hará es impedir que alguien decidido la use sin pagar; si eso llega a
importar, el control tiene que estar donde está el valor —el acceso al
contenido— y eso es autenticación de abonado, hoy fuera de alcance (§7).

**No escribas este documento como si fuera seguridad.** Es logística.

---

## 2. La pregunta

```
POST https://<registro>/v1/resolve
Content-Type: application/json

{
  "host": "redtv.ispjelly.net",
  "appVersion": "1.4.2",
  "installId": "…",
  "device": {
    "manufacturer": "SkyworthDigital", "model": "4K Google TV Box",
    "device": "YDA", "sdkInt": 30,
    "abis": ["armeabi-v7a", "armeabi"], "ramMb": 1990,
    "decoders": ["OMX.amlogic.avc.decoder.awesome2|video/avc|hw", "…"]
  },
  "diagnostico": {
    "sesiones": 128, "conImagen": 126,
    "msPrimerFotogramaMedia": 1480, "msPrimerFotogramaMax": 4100,
    "rebuffers": 9, "minutosReproducidos": 740,
    "tunneledPedido": 128, "tunneledRescatado": 0,
    "fallosPorCausa": { "SENAL": 2 },
    "fallosPorCanal": { "estrellassd": 2 }
  }
}
```

| Campo | Para qué |
|---|---|
| `host` | **la clave.** El host que el abonado escribió, normalizado |
| `appVersion` | para saber qué versiones hay en campo antes de cambiar el esquema |
| `installId` | identificador opaco por instalación, para contar cajas |
| `device` | soporte: saber qué cajas hay en cada ISP sin preguntar |
| `device.decoders` | **el inventario de vídeo**, `nombre\|mime\|hw o sw` |
| `diagnostico` | cómo se ha reproducido en esa caja, acumulado |

**`device.decoders` y `diagnostico` se añadieron el 05-oct**, y son campos
nuevos: un registro que los ignore sigue funcionando igual.

### Por qué el inventario de decoders va aquí

Porque es el único sitio donde cabe. La caja mayoritaria del cliente **solo
admite instalaciones desde la ficha de Play**, así que la primera vez que
sepamos qué decoders tiene será cuando una nos lo cuente por esta vía. De ahí
sale el `deviceOverrides` que el generador del `config.json` publica a todos
los sites **sin tocar la APK**.

Van solo los de vídeo: son los que deciden la cascada, y la lista completa de
una caja moderna pasa de cien entradas.

### `diagnostico` es técnico, y está construido para no dejar de serlo

Es la capa de **diagnóstico técnico** de `CLAUDE.md` §8, no la de datos de
abonado, y la frontera está en el diseño, no en la intención:

- Se cuenta **cuántas sesiones fallaron en un canal**, nunca qué se vio ni
  cuándo. Un canal que no falla no aparece.
- No hay marcas de tiempo por sesión, ni orden, ni duraciones individuales.
  **De aquí no se reconstruye un historial de visionado**, que es lo que lo
  movería bajo la LFPDPPP.
- Es acumulativo desde la instalación y **no se vacía al enviarse**: el total
  llega cada vez y la diferencia la calculas tú. Así una caja con mala
  conexión no pierde lo acumulado cuando un envío falla.
- Las listas van acotadas —12 causas, 40 canales—: pasado el tope se dejan de
  descubrir claves nuevas, pero las que ya están siguen contando.

**`host` se normaliza con `ServiceUrl.hostDeServidor`:** se descartan esquema,
puerto, ruta, consulta, ancla y credenciales; se baja a minúsculas; y **se quita
el punto final** del FQDN absoluto, así que `tv.isp.mx.` y `tv.isp.mx` son el
mismo nombre. Un IPv6 literal conserva sus corchetes. `https://RedTV.ispjelly.net:8096/web`
y `redtv.ispjelly.net` son el mismo site.

El esquema aquí es **opcional**, a diferencia de la derivación de la URL de
canales: ahí hace falta para saber si el site va por TLS, y aquí solo hay un
nombre que comparar.

**Un site puede tener varios alias.** El abonado puede escribir el DNS público,
la IP local o un dominio alternativo, y las tres tienen que resolver al mismo
site. Esto es del registro, no de la app: la app pregunta por lo que le
escribieron.

### `installId` y datos personales

Es un identificador **opaco y por instalación**, no del abonado ni de la caja.
Sirve para contar, no para identificar. Aun así sale del dispositivo hacia un
servidor nuestro, así que entra en el aviso de privacidad y en el formulario de
Seguridad de los datos de Play. Es la capa de **diagnóstico técnico** de
`CLAUDE.md` §8, no la de datos de abonado.

---

## 3. La respuesta

```json
{
  "authorized": true,
  "until": "2026-11-15T00:00:00Z",
  "site": {
    "id": "redtv",
    "channelsUrl": "http://redtv.ispjelly.net:8097/config.json"
  },
  "brand": {
    "displayName": "RedTV",
    "accent": "#E8A33D",
    "logoUrl": "https://<registro>/marca/redtv.png"
  }
}
```

| Campo | Obligatorio | Si falta |
|---|---|---|
| `authorized` | **sí** | la respuesta se descarta |
| `until` | **sí** si `authorized` | se descarta: una autorización sin caducidad no se guarda |
| `site.channelsUrl` | no | la app deriva por convención (§5) |
| `brand` | no | la marca sale del site, y si no, la neutra |
| `message` | no | — |

**`until` es obligatorio a propósito.** Una autorización sin fecha es una
autorización eterna: basta un fallo del registro para que una caja quede
autorizada para siempre sin que nadie pueda retirarlo.

### Denegar

```json
{
  "authorized": false,
  "message": "Este servidor no está dado de alta. Contacta a tu proveedor."
}
```

El `message` lo escribe quien opera el registro y **se enseña tal cual**. Sin él
la app pone un texto genérico, que es peor: el abonado llama a soporte a
preguntar qué hizo mal.

**Denegar no puede parecer que la app está rota.** Es una pantalla que explica,
no un error.

### Lo que el registro nunca envía

Ni código, ni plantillas, ni hojas de estilo, ni disposiciones. `CLAUDE.md`
regla 13: el servidor **elige entre** lo que ya trae el APK, nunca entrega
capacidades. El registro envía texto, fechas y URLs.

---

## 4. Qué hace la app con la respuesta

### Se guarda, y mientras no caduque no se vuelve a preguntar

Una consulta por site, no por arranque. La respuesta se guarda indexada por host
junto con su `until`.

### Si el registro no contesta y la autorización sigue viva, **se entra**

No negociable. **Un fallo nuestro no puede apagar televisiones**, y hay
despliegues donde la caja llega a la red del ISP pero no a internet.

### Si caducó y el registro no contesta, hay periodo de gracia

Cortarle a un cliente que paga por un problema de red nuestro es peor que
tardar en cortarle a uno que no paga. Pasada la gracia sin respuesta, se cierra
la sección y se dice por qué.

```
registro contesta  → se usa y se guarda
no contesta + vigente  → se entra
no contesta + caducada + dentro de gracia  → se entra, y se avisa
no contesta + caducada + fuera de gracia   → se cierra, explicando
no contesta + nunca contestó               → se entra, y se avisa
contesta «no autorizado»  → se cierra con su mensaje
```

**La última fila se añadió el 04-oct, al implementarlo.** Faltaba, y es el caso
de una caja recién instalada que alcanza la red de su ISP pero no la nube —el
mismo despliegue que §6 nombra como el que algún día exigiría firmar la
autorización dentro del `config.json`—. Si ahí se cerrara, ese escenario
estaría muerto desde el primer día y un corte de internet del ISP dejaría sin
televisión a cajas que nunca llegaron a preguntar.

Se entra, se avisa y se registra. Encaja con lo que esto es: un interruptor
operativo sobre gente que coopera, no un candado.

**La gracia son 14 días** (`LiveTuning.REGISTRY_GRACE_MS`), sobre la ventana
que ya emite el registro. Generoso a propósito.

**La caducidad se cuenta en semanas, no en horas.** Es el compromiso entre
velocidad de corte y daño por fallo propio, y el daño de cortar de más es mucho
peor. Empezar generoso.

### La puerta cubre **solo la sección de canales**

El VOD no se toca. Bloquear el login de Jellyfin bloquearía también las
películas y series que se sirven por Katalog, y convertiría cualquier fallo del
registro en un ISP entero sin nada. El corte tiene que ser quirúrgico y caer
donde está el producto diferencial.

Consecuencia: la comprobación vive donde se entra a TV en vivo, no en el
arranque de la app.

---

## 5. Dónde encaja con lo que ya hay

El registro **no sustituye** la derivación por convención: la precede.

```
1. El registro dice dónde está el servicio de canales   → se usa eso
2. No contestó, o no lo dice                            → derivar: host de Jellyfin, puerto 8097
3. Tampoco                                              → caché de ese site
4. Tampoco                                              → lista de arranque del BuildConfig
```

Cada escalón es más viejo y menos exacto que el anterior, y ninguno deja la
sección vacía. La marca sigue el mismo orden: registro, site, neutra.

---

## 6. Lo que queda fuera, y por qué está escrito

**Cajas que nunca alcanzan internet.** Si ese escenario existe, la autorización
tiene que poder viajar **dentro del `config.json` del site, firmada**, porque la
caja sí alcanza su site aunque no alcance la nube. Exige firmar y verificar, que
es bastante más trabajo.

**No se construye hasta que el escenario sea real**, pero el diseño no lo
impide: es un campo más en el documento del site y una verificación en la app.
Queda escrito para que nadie cierre esa puerta sin darse cuenta.

**Firmar la respuesta del registro.** Hoy basta HTTPS. Haría falta el día que la
autorización viaje por el site.
