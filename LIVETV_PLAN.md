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

**Prueba de Edgar:** 30 minutos de `beisbol` y 30 de `hbohd-70w` sin cortes ni
saltos. Se usa `hbohd` y no `discoveryhd` porque tiene la ventana DVR más
corta (13.88 s): es el canal donde primero aparecería un
`BEHIND_LIVE_WINDOW`.

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
**Aún no hay EPG generado**: este bloque empieza por producir el XMLTV desde
el EIT de Astra. Se deja para el final por decisión del cliente; la prioridad
es el motor y que la app funcione completa sin guía.

## B4 — Overlays e interfaz
Barra de info de canal, selector de pista de audio, selector de aspecto,
mensajes de estado. La **interfaz** es vía B; cómo el motor aplica esos
cambios es vía M.

**Ojo con el selector de audio:** la normalización de `spa`/`esp`/`esl`/`es`
**no vive aquí**. Subió a M3 al descubrirse que Flussonic normaliza los PIDs y
el idioma es la única clave de selección que queda. B4 dibuja el selector; M3
decide qué pista es cuál.

## B5 — Marca blanca
Product flavors, recursos por ISP, renombrado completo, avisos de licencia y
de no afiliación a Jellyfin.

## B6 — Telemetría
Reporte por sesión: modelo, SoC, canal, plan de reproducción, resultado, error,
ms hasta el primer frame, rebuffers. Sin dependencia de Google Play Services,
porque Fire TV y muchas cajas genéricas no lo traen.

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
