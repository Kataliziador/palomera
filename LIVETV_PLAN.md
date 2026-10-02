# LIVETV_PLAN.md

Plan de trabajo. Dos vías con reglas distintas, según `CLAUDE.md` sección 3.

- **Vía M (motor):** una fase, una falla, un commit, una APK. Edgar valida en
  pantalla y declara el cierre. No se avanza sin su visto bueno.
- **Vía B (todo lo demás):** Claude Code trabaja de corrido, detecta fallas y
  las corrige solo.

Las dos vías avanzan en paralelo. La vía B nunca bloquea a la M ni al revés,
salvo las dependencias señaladas.

---

# VÍA M — Motor de reproducción

## M0 — Andamiaje

**Objetivo:** que exista el módulo y el enrutador sin romper nada.

- Fork de `jellyfin-androidtv` fijado en un **tag de release**, marcado
  `upstream-base`. Sin esa etiqueta, la verificación de la regla 6 no tiene
  contra qué comparar
- **APK de referencia del upstream sin tocar**, instalada y probada en la
  caja **antes** de escribir una línea. Es la línea base sin la cual "VOD sin
  regresión" no es verificable: hace falta un *antes*
- Módulo `livetv` con la interfaz `LiveEngine`
- `Media3LiveEngine` mínimo que reproduce una URL HLS fija
- Enrutador de una decisión en el código existente
- `LiveTuning.kt` con las constantes iniciales

**Prueba de Edgar:** reproducir `beisbol`; abrir una película y una serie de
Jellyfin y confirmar que se comportan igual **que en la APK de referencia**.

**Cierre:** canal reproduce, VOD sin regresión, versiones fijadas y anotadas
en `LIVETV_LOG.md`, y `git diff --stat upstream-base..HEAD -- ':!livetv/'`
muestra solo el enrutador.

---

## M1 — Diagnóstico y degradación

**Objetivo:** poder ver qué está pasando y poder simular cajas peores. Sin
esto, las fases siguientes son adivinanza.

- Overlay de stats: canal, motor, decoder activo, **motivo de la decisión**,
  buffer, bitrate, frames perdidos, tiempo desde el último frame
- Logging estructurado con buffer circular exportable
- `DeviceCapabilities` con `MediaCodecList(ALL_CODECS)`, cacheado por
  `Build.FINGERPRINT`
- **Menú de degradación completo** (ver `LIVETV_ENGINE.md` §7)

**Prueba de Edgar:** el overlay muestra datos correctos; vetar el decoder
MPEG-2 desde el menú cambia visiblemente el comportamiento; el log se exporta.

**Cierre:** se puede describir una falla con datos, no con impresiones.

---

## M2 — Estabilidad base

**Objetivo:** que un canal sano corra sin microcortes.

- `DefaultLoadControl` con los valores de `LIVETV_ENGINE.md` §5
- `connectTimeoutMs`, `readTimeoutMs`
- `LoadErrorHandlingPolicy` con backoff y reintentos casi infinitos
- `targetOffsetMs` por canal, **recortado en runtime** si excede la mitad de
  la ventana DVR medida del playlist (ver `CHANNELS.md` §2.bis)
- Umbral de playlist detenido ampliado en satelitales

**Prueba de Edgar:** 30 minutos de **`estrellassd-mp2v` (77W)** y 30 de
`hbohd-70w`, sin cortes ni saltos.

El orden sale de la prioridad de estabilidad (`CHANNELS.md` §0): **77W primero,
70W después, el resto al final**. `estrellassd` además es hoy el peor medido
—15 % de ratio de rebuffer— así que es el que de verdad pone a prueba la fase.

`beisbol` se sigue usando como **línea base de diagnóstico**, para separar lo
que es del motor de lo que es del canal, pero **no cuenta como criterio de
cierre**.

**Cierre:** rebuffers por hora < 1 en canal sano.

---

## M3 — Cascada de códecs

**Objetivo:** los seis canales con video y audio correctos.

Esta fase se redujo mucho al decidirse la normalización de audio a AAC: **no
hay extensión FFmpeg, ni NDK, ni cascada de audio.** Queda solo el video.

- `BackendDecider` con la cascada de video (HW → SW plataforma → libVLC)
- Normalización de idioma `spa`/`esp`/`esl`/`es` y selección de audio por
  idioma, nunca por PID ni por índice — es lo único que hay que resolver en
  audio, y es de motor, no de interfaz
- `videoCodec`/`audioCodec` desde el `config.json`, con reevaluación del plan
  si el stream desmiente al config
- Motivo de cada decisión visible en el overlay

**Prueba de Edgar:** los seis canales reproducen con audio, y en
`discoveryhd-70w` y `hbohd-70w` el selector distingue español de inglés.
Luego, con el menú de degradación, vetar el MPEG-2 de hardware y confirmar que
`estrellassd-mp2v` sigue funcionando por el camino de respaldo.

**Cierre:** los seis canales funcionan en modo normal **y** en modo degradado.

> **02-oct: la mitad normal está cerrada y la degradada está bloqueada por
> M6.** La caja de pruebas no tiene decoder de vídeo por software para H.264
> ni para MPEG-2 —solo `c2.android` de hevc, vp8, vp9, mpeg4 y h263—, así que
> vetar el decoder de hardware no cae al paso 2 de la cascada: se queda sin
> camino. **libVLC es el único respaldo que existe en esta caja.** Ver
> pendiente #26 de `LIVETV_LOG.md`.

**Dependencia de headend:** el audio AAC del canal mp2v. Mientras no se
aplique, ese canal sigue en MP2 y depende del decoder de hardware de la caja
de pruebas — que lo tiene, así que no bloquea, pero tampoco prueba nada.

---

## M4 — Flags de extractor en Amlogic

**Objetivo:** resolver la contradicción entre la medición y la experiencia de
campo.

- Flags configurables en caliente desde el menú
- Detección de SoC con override manual
- Para HLS, pasarlos vía `DefaultHlsExtractorFactory`

**Prueba de Edgar:** las cuatro combinaciones de los dos flags, con los cuatro
canales H.264, en la caja de pruebas.

**Cierre:** ninguna combinación produce artefactos ni impide el arranque. Se
documenta en `LIVETV_LOG.md` cuál queda por defecto para Amlogic.

---

## M5 — Render: aspecto y entrelazado

**Objetivo:** que la imagen se vea bien, no solo que reproduzca.

- Respetar el `pixelWidthHeightRatio`
- Modos de ajuste manual
- Recalcular el resize mode en `onVideoSizeChanged`
- Implementar los niveles de la cascada de entrelazado según lo que se observe

**Prueba de Edgar:** los **dos** canales anamórficos con proporciones
correctas — `estrellashd` (4:3) y `estrellassd` (20:17); en los otros cuatro,
1:1, respetar el SAR no cambia nada. Los **cuatro** entrelazados sin peine en
escenas con movimiento lateral, cubriendo los dos órdenes de campo
(`discoveryhd` es `tt`, `hbohd` es `bb`) y los dos tipos de codificación.

**Cierre:** sin peine visible en la caja de pruebas, o con la limitación
documentada por modelo y el nivel correspondiente implementado.

---

## M6 — Backend libVLC

**Solo si M3 o M5 lo requieren.** Si la caja de pruebas cubre todo por
hardware y desentrelaza, esta fase se pospone para cuando aparezca la primera
caja que la necesite.

- `VlcLiveEngine` implementando la misma interfaz
- Decodificación por software forzada cuando haya que desentrelazar
- Selección automática según la cascada
- Verificar `armeabi-v7a` y `arm64-v8a` en el AAB

**Prueba de Edgar:** con el MPEG-2 de hardware vetado, el canal mp2v
reproduce por libVLC con audio y sin peine. El cambio de backend es invisible.

**Cierre:** el camino de respaldo funciona de verdad, no solo en teoría.

---

## M7 — Resiliencia ante lluvia

**Objetivo:** que un corte no requiera intervención del suscriptor.

- `LiveWatchdog` con la escalera de recuperación
- **Tratar el playlist vacío como estado de espera, nunca como error.**
  Medido: cuando la fuente cae, Flussonic devuelve un playlist sin segmentos
  y deja las peticiones colgadas
- Sobrevivir a cambios en el número de pistas a mitad de stream
- Mapeo de `PlaybackException` por código
- Mensajes que distinguen señal de conexión
- Regla de no vetar decoders por errores durante corrupción

**Pruebas de Edgar:**
- Desconectar la red 10 s, 60 s, 5 min
- Cortar la entrada en Astra
- `tc qdisc add dev eth0 root netem loss 5% delay 100ms 20ms`
- Reproducir en bucle un golden sample con lluvia simulada

**Cierre:** el cliente se recupera solo en todos los casos, sin tocar el
control, y nunca queda en estado de error permanente.

**Nota medida:** cuando la fuente cae en Flussonic, el playlist tarda ~15 s en
reconstruirse. El cliente no puede ser más rápido que eso. El objetivo de
< 8 s depende de bajar el `source_timeout` y poner el letrero de respaldo en
el headend (pendientes 6 y 7 de `LIVETV_LOG.md`).

---

## M8 — Maratón y zapping

**Objetivo:** sobrevivir a la operación real.

- Reconexión programada antes del rollover de PTS (~26.5 h)
- Sin fugas de memoria en zapping repetido
- Medir el zapping real

**Pruebas de Edgar:** un canal 48 h continuas; 50 cambios de canal seguidos.

**Cierre:** vivo a las 48 h, memoria estable, zapping p50 < 2 s.

---

# VÍA B — Trabajo autónomo

Claude Code avanza solo, corrige lo que encuentre y reporta al final de cada
bloque. Sin esperas.

## B1 — Cliente del servidor de configuración
`ConfigRepository` que consume el `config.json`, con caché local,
configuración de fábrica y versión de esquema. **Dependencia:** M0.

## B2 — Lista de canales y navegación
**Diseñar para ~120 canales**, no para los 6 de prueba. Grupos, navegación por
D-pad, último canal, favoritos, entrada directa por número y búsqueda. Una
lista plana de 120 no es navegable con control remoto. **Dependencia:** B1.

## B3 — Sección LiveTV, reescrita desde cero
La sección de canales en vivo se **reescribe entera**, y **reemplaza** la
sección LiveTV de Jellyfin: la fila del inicio apunta a la nueva y la vieja
queda inalcanzable (`CLAUDE.md` §4, regla 5).

**Lleva el mismo diseño que el resto de la app**, no uno propio: `JellyfinTheme`
y los componentes de `ui/base/`, para que Inicio, Buscar y TV en vivo se vean
como una sola aplicación. Por eso su código vive en `app/.../ui/livetv/` y no
en el módulo.

La navegación de películas y series se conserva tal cual. **Dependencia:** B2.

## B8 — EPG *(fase final, no ahora)*
Consumo del XMLTV, guía por canal, programa actual y siguiente.

**Reescrita el 02-oct.** El muro de este bloque no era consumir la guía sino
producirla, y con el servidor de canales+EPG en cada site (`CLAUDE.md` §1) esa
mitad **cambia de lado**: generar el XMLTV desde el EIT de Astra es trabajo del
headend y de la tubería de distribución, como Katalog con el VOD. No es trabajo
de la app.

Lo que queda aquí es tratable y ya tiene su hueco esperando —hoy la barra de
canal lleva `DummyEpg.kt`—:

- El `config.json` lleva **puntero** al XMLTV y su cadencia, no la guía dentro
- Descarga, interpretación y caché propios, con su propio ritmo
- Programa actual y siguiente en la barra; guía por canal
- **Un site sin EPG es normal**, no un error: la barra se degrada sin guía

Sigue siendo la última por decisión del cliente: la prioridad es el motor y que
la app funcione completa sin guía.

## B4 — Overlays e interfaz
Barra de info de canal, selector de pista de audio, selector de aspecto,
mensajes de estado. La **interfaz** es vía B; cómo el motor aplica esos
cambios es vía M.

**Ojo con el selector de audio:** la normalización de `spa`/`esp`/`esl`/`es`
**no vive aquí**. Subió a M3 al descubrirse que Flussonic normaliza los PIDs y
el idioma es la única clave de selección que queda. B4 dibuja el selector; M3
decide qué pista es cuál.

**Los `extras` entran aquí** (`CLAUDE.md` §6): la barra pinta los pares
etiqueta/valor que traiga el canal, sin saber qué son, recortados y validados.
Es lo que permite que un ISP añada información mañana sin una versión nueva, y
hay que hacerlo antes de que llegue el primer dato extra, no después.

## B5 — Marca dinámica
**Reescrita el 02-oct: ya no hay flavors.** Una sola APK y una sola ficha de
Play nuestra; la marca sale del servidor de canales del site (`CLAUDE.md` §8).

- ~~Marca desde el servicio del site: `displayName`, `accent`, `logoUrl`~~
  **hecho el 02-oct**, con validación de contraste 3:1 y respaldo por ranura
- `BrandResolver`: la marca del **registro** pisa a la del site (B9)
- Ranuras fijas (`displayName`, `accent`, `logo`) con validación
  de contraste y caída al valor neutro
- Marca neutra **diseñada**, que es lo que se ve antes de entrar y sin red
- Caché de marca y de logo, indexada por servidor
- Renombrado de la app: fuera el nombre y el logo de Jellyfin
- Pantalla de información: "no afiliado al proyecto Jellyfin" + GPL-2.0 y
  LGPL-2.1

**Cierre:** dos servidores de canales distintos visten la app distinto sin
recompilar, y sin ninguno la app arranca con la marca neutra y la lista de
`BuildConfig`.

## B9 — Registro de sites
Consulta al registro antes de derivar por convención (`REGISTRY_SCHEMA.md`).

- Normalización del host igual que en `ServiceUrl`
- Autorización guardada por host, con su caducidad
- **Degradación:** sin respuesta y vigente se entra; caducada hay gracia;
  pasada la gracia se cierra **explicando**, no con un error genérico
- La puerta cubre **solo** la sección de canales; el VOD no se toca
- `channelsUrl` y `brand` del registro pisan a la convención y al site

**Dependencia:** B1. **Criterio de cierre:** dar de alta un host en el registro
y que una caja entre sin tocar la app; quitarlo y que se cierre con su mensaje
al caducar. Y con el registro apagado, una caja ya autorizada sigue entrando.

### Lo que pide el proyecto del autorizador

Entregado el 02-oct en `/home/proyectos/autorizador/docs/PETICION_APK.md`. El
contrato no cambia; esto es lo que `INTEGRACION_REGISTRO.md` §7 dejaba abierto.

- `REGISTRY_URL` en `BuildConfig`: **dominio, no IP** —mudar el servidor tiene
  que ser un registro DNS, no una APK—, HTTPS sin puerto, y el código compone
  `+ "/v1/resolve"`. Más el override del menú para apuntar a un registro de
  pruebas.
- Tiempos de ~10 s: si no contesta, la app ya sabe degradarse y esperar más solo
  retrasa la entrada a TV en vivo.
- **Confianza en ISRG Root X1/X2 incluida en la app, solo para el registro.**
  `minSdk` es **21** y Android no trae esa raíz hasta la 7.1.1, así que una caja
  de Android 5.0–7.1.0 **nunca conseguiría autorización** — y no daría error
  visible: simplemente no entraría nunca a TV en vivo, que es el reporte más
  caro de diagnosticar. `network_security_config.xml` no vale: Android lo ignora
  por debajo de API 24, justo donde está el problema. Tiene que ser un
  `X509TrustManager` que confíe en **el sistema o en las incluidas**, nunca uno
  que acepte todo.
- Lo mismo para el cliente OkHttp de Coil, o la autorización funcionaría y el
  logo de la marca no.

**Añadido desde este lado, que allí no podían ver:** `ConfigRepository` usa
`HttpURLConnection`, así que **el mismo fallo alcanza al `config.json`** si un
site lo sirve por HTTPS con Let's Encrypt. En la LAN lo normal será `http`, pero
un ISP que publique su Jellyfin hacia fuera se lleva el problema entero. El
arreglo debe cubrir las dos conexiones, no solo la del registro.

## B6 — Telemetría
**Dos capas que no se mezclan** (`CLAUDE.md` §8):

- **Diagnóstico técnico**, anónimo y siempre activo: modelo, SoC, canal, plan
  de reproducción, resultado, error, ms hasta el primer frame, rebuffers. Y el
  **códec de audio recibido**, que es como se detecta un site con el AAC mal
  configurado (§2).
- **Datos de abonado**, detrás de un interruptor por proveedor y con contrato:
  identidad y qué vio quién. Exige aviso de privacidad y el formulario de
  Seguridad de los datos de Play, y el editor somos nosotros.

Sin dependencia de Google Play Services, porque Fire TV y muchas cajas
genéricas no lo traen.

## B7 — Preparación para Play Store
Manifiesto con `leanback`, `touchscreen` y `faketouch` en `required="false"`;
banner 320x180; `LEANBACK_LAUNCHER`; AAB con `armeabi-v7a` y `arm64-v8a`;
target API vigente; política de privacidad; repositorio público del fork.

---

# Simulación de fallas

```bash
# Pérdida y jitter
tc qdisc add dev eth0 root netem loss 5% delay 100ms 20ms
# Corte total
tc qdisc change dev eth0 root netem loss 100%
# Quitar
tc qdisc del dev eth0 root
```

Además: cortar la entrada en Astra, y reproducir en bucle un golden sample
capturado durante lluvia real. **Esa última es la mejor prueba que existe.**

---

# Definición de "hecho" en la vía M

1. Edgar instaló la APK y probó en pantalla
2. Verificó el criterio de cierre
3. Confirmó que **el VOD de Jellyfin sigue igual** que en la APK de
   referencia de M0
4. `git diff --stat upstream-base..HEAD -- ':!livetv/'` sigue limpio
5. La entrada quedó escrita en `LIVETV_LOG.md`
