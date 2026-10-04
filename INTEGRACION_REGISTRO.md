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

El override del menú de desarrollador que ya existe apunta la URL del
**`config.json`**, no la del registro. El equivalente para el registro **sigue
sin implementarse**: va en la segunda mitad de B9, con el cliente de red.

Lo que **sí** está hecho desde el 04-oct, y se puede copiar como pruebas tuyas:

| Qué | Dónde |
|---|---|
| Normalización del host | `ServiceUrl.hostDeServidor` + 6 casos en `ServiceUrlTest` |
| Interpretación de tu respuesta | `RegistryParser` + 11 casos en `RegistryParserTest` |
| La escalera de degradación entera | `DecisorDeAcceso` + 9 casos en `DecisorDeAccesoTest` |

Los dos últimos son la especificación ejecutable de lo que esta página
describe. Si quieres saber qué hace la app con una respuesta tuya concreta,
ese es el sitio donde mirarlo sin preguntarme.

Cuando el cliente de red exista, este documento se actualiza con el
procedimiento contra una caja y las líneas de log que sirven de veredicto.

---

## 9. Si el contrato se te queda corto

No lo amplíes por tu cuenta: la app ignoraría el campo nuevo. Pídelo en el
proyecto de la APK con el caso de uso concreto.

Y antes de pedirlo, comprueba que no choca con la regla 6 de tu propio
`CLAUDE.md`: **nada más puede depender de este servicio.** Toda su virtud es que
puede estar caído sin que se note.
