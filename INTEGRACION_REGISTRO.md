# INTEGRACION_REGISTRO.md

**Para quien desarrolla el autorizador** (`/home/proyectos/autorizador`).

`REGISTRY_SCHEMA.md` dice **qué** responder. Este documento dice **cómo se
comporta la APK de verdad**: cuándo pregunta, qué guarda, cómo se degrada
cuando no contestas y qué pasa exactamente al denegar.

Leerlo antes de implementar algo que toque la respuesta.

---

## 1. Si el contrato cambió

```bash
git -C /home/proyectos/jellyfin-livetv-v1 log --oneline -1 -- REGISTRY_SCHEMA.md
```

Si no coincide con el commit de la cabecera de tu `docs/CONTRATO.md`, cambió.
Mira el diff antes de copiar:

```bash
git -C /home/proyectos/jellyfin-livetv-v1 log -p <tu-commit>..HEAD -- REGISTRY_SCHEMA.md
```

---

## 2. La normalización del host: no la inventes

Es el punto donde más fácil se rompe la integración, porque falla **para unos
clientes y no para otros**, que es el síntoma más caro de diagnosticar.

La APK normaliza el host que escribió el abonado **antes** de preguntarte, con
`livetv/src/main/kotlin/config/ServiceUrl.kt`. Las reglas:

- Se descartan **esquema, puerto, ruta, consulta, ancla y credenciales**
- Se baja **a minúsculas**
- **Se quita el punto final** del FQDN absoluto: `tv.isp.mx.` → `tv.isp.mx`.
  Lo pediste tú en tu `HALLAZGOS.md` y está hecho desde el 04-oct, en
  `ServiceUrl.hostDeServidor`
- **El esquema es opcional**: la app lo acepta con o sin él
- `https://RedTV.ispjelly.net:8096/web?x=1` y `redtv.ispjelly.net` son **el
  mismo site**
- Un IPv6 literal conserva sus corchetes: `[fd00::1]`

**La especificación ejecutable son las doce pruebas de
`livetv/src/test/kotlin/config/ServiceUrlTest.kt`.** Cópialas como pruebas tuyas:
barra final, sin puerto, TLS, Jellyfin bajo una ruta, credenciales, IPv6, sin
esquema, espacios alrededor.

**Si normalizas distinto, habrá hosts que la app pregunte y tú no encuentres.**
El reporte que te llegará es «a este cliente no le funciona y a los demás sí».

### Un site tiene varios alias, y eso es cosa tuya

El abonado puede escribir el DNS público, la IP local de su caja o un dominio
alternativo. Los tres tienen que resolver al mismo site. **La app pregunta por
lo que le escribieron**, sin inventar variantes.

---

## 3. Cuándo pregunta, y cuántas veces

**Una vez por site, no por arranque.** La respuesta se guarda indexada por host
junto con su `until`, y mientras no caduque no se vuelve a preguntar.

Eso fija la carga: con 10 ISPs de 5.000 cajas y ventana de tres semanas, son
**0,03 peticiones por segundo**. Dos por minuto. No dimensiones para tráfico.

Y fija también tu única métrica de disponibilidad real: **un fin de semana caído
es invisible**. Lo que duele es estar caído muchos días seguidos, cuando empiecen
a caducar autorizaciones.

---

## 4. Cómo se degrada la app cuando no contestas

Esto es lo que te permite ser un servicio modesto:

```
contestas                                  → se usa y se guarda
no contestas + autorización vigente        → se entra
no contestas + caducada + dentro de gracia → se entra, avisando
no contestas + caducada + fuera de gracia  → se cierra, explicando
no contestas + nunca contestaste           → se entra, avisando
contestas «no autorizado»                  → se cierra con tu mensaje
```

**La penúltima fila es nueva del 04-oct** y faltaba en el contrato: una caja
recién instalada que alcanza la red de su ISP pero no la nube. Cerrar ahí
dejaría muerto ese despliegue desde el primer día. **La gracia son 14 días**
sobre tu ventana.

**La puerta cubre solo la sección de canales.** El VOD de Jellyfin no se toca:
bloquear el login dejaría a un ISP sin las películas que Katalog le sirve, y
convertiría un fallo tuyo en un cliente sin nada.

Consecuencia operativa que conviene decir en voz alta y poner en la
administración: **dar de baja a un ISP tarda lo que dure la ventana.** Si alguien
pulsa «desautorizar» esperando efecto inmediato, se va a llevar una sorpresa.

---

## 5. Qué hace la app con cada campo

| Campo | Qué hace la app |
|---|---|
| `authorized: false` | cierra la sección y **enseña tu `message` tal cual** |
| `until` ausente en respuesta autorizada | **descarta la respuesta entera** |
| `until` en el pasado | se trata como caducada |
| `site.channelsUrl` | **pisa** a la convención del puerto 8097 |
| `site.channelsUrl` ausente | se deriva por convención |
| `brand` | pisa a la marca que sirva el site |
| `brand` ausente | marca del site; si tampoco, la neutra |
| campo desconocido | se ignora en silencio |

### `until` es obligatorio, y por qué

Una autorización sin caducidad es **eterna**. Bastaría un fallo tuyo —un campo
que se olvida, un valor nulo— para dejar una caja autorizada para siempre sin
forma de retirarlo. Por eso la app prefiere descartar la respuesta entera.

### `message` lo lee un abonado en su sala

No el operador del registro. «Este servidor no está dado de alta. Contacta a tu
proveedor» sirve; «site_not_found» o «403» no. Sin `message`, la app pone un
texto genérico, que es peor: el abonado llama a soporte a preguntar qué hizo mal.

**Denegar no puede parecer que la app está rota.**

### `channelsUrl`: valídalo al guardarlo

Es tu oportunidad de cazar la errata. Si apuntas mal un site, el síntoma no
aparece aquí: aparece en la sala de un abonado, semanas después, como una
sección sin canales. Comprueba al guardar que responde y que parece un
`config.json`.

---

## 6. Lo que la APK no va a hacer

- **No reintenta.** Un fallo es un fallo hasta la próxima vez que alguien entre
  a la sección.
- **No sigue pidiendo mientras la autorización esté viva.** No esperes un
  latido ni poder empujar cambios: lo que digas vale hasta que caduque.
- **No ejecuta nada que venga de ti.** Ni código, ni plantillas, ni estilos.
- **No manda datos de abonado.** `installId` es opaco y por instalación.
- **No verifica firmas** (todavía). Hoy basta HTTPS. Haría falta el día que la
  autorización viaje dentro del `config.json` del site, para cajas sin internet.

---

## 7. La dirección del registro va compilada en la APK

La app tiene que saber a quién preguntar antes de preguntar nada, así que **tu
dirección no puede venir de ti**. Va en el APK.

**Cambiarla después del primer despliegue exige una versión nueva para todos los
ISPs a la vez.** Elige el dominio antes de salir a producción, y uno que no te
ate a un proveedor concreto.

---

## 8. Cómo se prueba contra una caja

**Ya se puede apuntar la app a un registro de pruebas.** Se empuja el archivo
de preferencias con `adb`, igual que el override del `config.json`:

```bash
D=192.168.20.132:5555
cat > /tmp/livetv.dev.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="registryUrlOverride">http://TU_IP:8098</string>
</map>
XML
adb -s $D shell am force-stop mx.kataliziador.palco.debug
adb -s $D push /tmp/livetv.dev.xml /data/local/tmp/d.xml
adb -s $D shell run-as mx.kataliziador.palco.debug sh -c \
  "'cat /data/local/tmp/d.xml > /data/data/mx.kataliziador.palco.debug/shared_prefs/livetv.dev.xml'"
```

La app compone `/v1/resolve` sobre lo que pongas ahí. Una caja apuntada a un
registro de pruebas **lo anuncia en pantalla** (`degradación: registro=manual`),
porque una caja con ese estado pegado y olvidada ya costó dos conclusiones
falsas en este proyecto.

**Y hay que saber esto para probar:** mientras la autorización guardada siga
viva, la app **no vuelve a preguntar**, así que apuntarla a otro registro no
hace nada. En el menú de diagnóstico hay «Olvidar la autorización guardada»,
que la tira y obliga a preguntar de nuevo. Sirve también en operación: un
técnico que acaba de dar de alta un site no tiene por qué esperar semanas.

### El veredicto está en el log

```bash
adb -s $D logcat -v time | grep -i livetv
```

| Línea | Qué significa |
|---|---|
| `registro.respuesta host=… autorizado=… hasta=… channelsUrl=… marca=…` | **te contestó y se entendió** |
| `registro.acceso host=… resultado=permitido motivo=…` | la decisión que se tomó |
| `registro.vigente host=… hasta=…` | **no te preguntó**: la guardada sigue viva |
| `registro.sinRespuesta host=… motivo=…` | no se pudo hablar contigo |
| `registro.fechaIlegible` / `registro.channelsUrlIlegible` | algo de tu respuesta no sirvió |
| `catalogo.destino … procedencia=registro` | **tu `channelsUrl` ganó** a la convención |
| `catalogo.listo … marcaOrigen=registro` | tu marca ganó a la del site |

Medido contra el registro de producción el 2026-10-04 desde la caja de pruebas:
las tres primeras salen como deben, y el TLS de `authjelly.satmx.net` funciona
en Android 11 sin tocar nada.

Lo que se puede copiar como pruebas tuyas:

| Qué | Dónde |
|---|---|
| Normalización del host | `ServiceUrl.hostDeServidor` + 6 casos en `ServiceUrlTest` |
| Interpretación de tu respuesta | `RegistryParser` + 11 casos en `RegistryParserTest` |
| La escalera de degradación entera | `DecisorDeAcceso` + 9 casos en `DecisorDeAccesoTest` |

Los dos últimos son la especificación ejecutable de lo que esta página
describe. Si quieres saber qué hace la app con una respuesta tuya concreta,
ese es el sitio donde mirarlo sin preguntarme.

### Desde el 05-oct recibes el inventario de la caja y su diagnóstico

Dos campos nuevos en la petición, compatibles hacia atrás: ignóralos y todo
sigue igual.

**`device.decoders`** es la lista de decoders de vídeo, `nombre|mime|hw o sw`.
Existe porque la caja mayoritaria del cliente solo se instala desde la ficha de
Play: la primera vez que sepamos qué trae será cuando una te lo cuente. **Si
guardas una fila por modelo, con eso se alimenta la tabla de
`deviceOverrides`** que el generador del `config.json` reparte a todos los
sites.

**`diagnostico`** es cómo se ha reproducido esa caja, acumulado desde la
instalación: sesiones, cuántas dieron imagen, ms al primer fotograma,
rebuffers, si el tunneled hizo falta rescatarlo, y los fallos por causa y por
canal.

**Y desde el 11-oct trae `audioSinNormalizar`**, con claves `canal:códec` y
cuántas sesiones cada una. **Es el campo que más te conviene mirar**, porque
delata un fallo de despliegue que nadie puede ver de otra forma: el audio se
normaliza a AAC en el Flussonic de cada site, y un canal que se quedó sin
normalizar **se oye perfecto en una caja buena** —decodifica MP2 y AC-3 por
hardware— y es silencio en una barata. Si ves `canal207:mp2` subiendo en un
site, ese canal hay que renormalizarlo, y te has enterado sin que llame nadie.
Llevaba desde entonces en el código sin estar en el contrato; se corrigió el
15-oct.

**No se vacía al enviarlo.** Recibes el total cada vez y la diferencia la
calculas tú. Es deliberado: vaciar en la caja significaría perder lo acumulado
cada vez que un envío falla, justo en las cajas con mala conexión, que son las
que más interesa medir.

**Y no es un historial de visionado, por construcción.** Solo se cuentan los
canales que **fallaron**, sin marcas de tiempo, sin orden y sin duraciones
individuales. Es la capa de diagnóstico técnico de `CLAUDE.md` §8, no la de
datos de abonado, y conviene que al guardarlo no lo conviertas en la otra.

### Las cajas viejas ya validan tu certificado

**Hecho el 05-oct y medido en un emulador de Android TV API 22.** La app
empaqueta ISRG Root X1, X2, Root YR e YE y las usa **solo** para hablar
contigo, nunca para el resto de su tráfico.

Antes, en esa caja: `CertPathValidatorException: Trust anchor for certification
path not found`, y la sección entraba por la escalera de degradación sin saber
nada de ti. Ahora: `registro.respuesta autorizado=true`.

**Lo que esto te permite:** cambiar de autoridad de certificados algún día sin
dejar fuera a nadie, porque la app acepta por el almacén del sistema **o** por
las raíces que lleva. Lo que **no** te permite es servir un certificado que no
encadene a una raíz pública: un autofirmado se sigue rechazando, y hay una
prueba que lo comprueba con tu propio nombre de dominio.

**Lo que sigue roto en esas cajas, y es cosmético:** el `logoUrl` de tu
respuesta **no carga** por debajo de Android 7.1.1, porque lo pide otro cliente
HTTP. La marca cae al `displayName`, que es su respaldo. Si te importa que el
logo se vea en cajas viejas, la salida más barata es servirlo desde el site por
HTTP, no desde el registro.

---

Falta por ver en pantalla la pantalla de denegación; su lógica está cubierta
por las pruebas, pero nadie la ha mirado en una tele todavía.

---

## 9. Si el contrato se te queda corto

No lo amplíes por tu cuenta: la app ignoraría el campo nuevo. Pídelo en el
proyecto de la APK con el caso de uso concreto.

Y antes de pedirlo, comprueba que no choca con la regla 6 de tu propio
`CLAUDE.md`: **nada más puede depender de este servicio.** Toda su virtud es que
puede estar caído sin que se note.
