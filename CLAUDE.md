# CLAUDE.md

Reglas de este repositorio. Léelas completas antes de tocar código.

---

## 1. Qué es este proyecto

Fork de `jellyfin-androidtv` (GPL-2.0) con **un módulo nuevo y aislado para
canales de TV en vivo**. Todo el VOD de Jellyfin queda **intacto**.

El producto se vende a varios ISPs mexicanos. **Una sola APK, una sola ficha
en Google Play**, publicada por nosotros; la marca de cada ISP se aplica en
caliente al conectar (§8).

**Todo lo que la app consume vive en el site del ISP**, en su LAN:

```
EN EL SITE DE CADA ISP
┌───────────────────────────────────────────────┐
│ Satélite Ku ─→ Cesbo Astra ─→ Flussonic ─→ HLS│  ← headend propio del ISP
│                                               │
│ Jellyfin            ← VOD, lo alimentamos      │
│                       nosotros con Katalog     │
│ Servidor de canales ← lista + marca + EPG      │
└───────────────────────────────────────────────┘
                      ↑ LAN
              APK en la caja del abonado
```

Consecuencias que mandan sobre todo lo demás:

- **Cada ISP tiene su propio headend y su propio lineup.** Los canales de
  producción no son los de `CHANNELS.md` y nadie los habrá medido. El motor
  tiene que decidir solo, por canal y por caja (`LIVETV_ENGINE.md` §4).
- **El tramo app → contenido es LAN.** Lo medido en este repositorio es
  representativo de producción, y los objetivos de §10 son exigibles.
- **Nosotros entramos al site** —ya lo hacemos con Katalog—, así que la
  configuración del headend y del servidor de canales es trabajo nuestro, no
  del ISP. Lo que no es nuestro es la antena y lo que el ISP decida emitir.

Documentos hermanos:
- `CONFIG_SCHEMA.md` — contrato entre el servicio de canales de cada site y la
  APK. Lo que el miniservicio tiene que emitir.
- `REGISTRY_SCHEMA.md` — contrato entre el registro de sites y la APK. Qué
  autoriza, qué liga y qué pasa cuando no contesta.
- `INTEGRACION_CANALES.md` — cómo se comporta esta app, para quien escribe el
  servicio de canales.
- `INTEGRACION_REGISTRO.md` — lo mismo, para quien escribe el autorizador.

### Tres proyectos, y este escribe los contratos

| Proyecto | Ruta | Qué hace |
|---|---|---|
| APK | aquí | la app. **Escribe los contratos y los documentos de integración** |
| Servicio de canales | `/home/proyectos/servidor-canales` | uno por ISP, en su site: lista, EPG y marca |
| Autorizador | `/home/proyectos/autorizador` | uno en total, en internet: autoriza y liga |

Los otros dos llevan una **copia** del contrato que les toca, con el commit del
que salió. Comprueban si se quedaron atrás mirando el log de este repositorio.

**Regla de sincronía:** si cambia `CONFIG_SCHEMA.md` o `REGISTRY_SCHEMA.md`, o
cambia el comportamiento que describen los `INTEGRACION_*.md` —tiempos de
espera, escalera de respaldo, qué se descarta, qué se mide— **se actualiza el
`INTEGRACION_*.md` correspondiente en el mismo commit**. Ahí es donde el otro
proyecto va a mirar, y un documento de integración desfasado es peor que no
tenerlo: se cree.

**Los contratos solo crecen hacia atrás compatible:** campos nuevos, nunca
significados distintos para campos existentes. Si hiciera falta lo segundo, se
sube `version` y se dice en el documento de integración.
- `CHANNELS.md` — datos medidos de cada canal. Fuente de verdad, no adivines.
- `LIVETV_ENGINE.md` — especificación del motor: cascada, capacidades, sintonía.
- `LIVETV_PLAN.md` — fases de trabajo y criterios de cierre.
- `LIVETV_LOG.md` — bitácora. Se actualiza al cerrar cada fase.
- `PLAY.md` — qué hace falta para publicar y **quién lo hace**. Lo irreversible
  —nombre de paquete y clave de firma—, el formulario de Seguridad de los datos
  con lo que la app manda de verdad, y la oferta de código que exige la GPL-2.0.

---

## 2. Principio rector

**Los canales se reproducen tal cual llegan del satélite.**

El motor se desarrolla y afina para funcionar con la señal como se recibe:
MPEG-2, audio MP2, entrelazado, anamórfico, varias pistas de audio. Cuando un
canal presente un problema, la solución se busca **en el cliente**.

Los canales de referencia son los de **satélite 77W y 70W** (ver `CHANNELS.md`).
Los de TotalPlay son H.264 progresivo sin particularidades y sirven de línea base.

**Única excepción permitida, y ya decidida:** el **audio se normaliza a AAC
en Flussonic**. Cuesta muy poca CPU en el servidor y no toca el video. Se elevó
de "opción disponible" a política el 2026-09-22, porque con ~120 canales la
excepción caso por caso no escala. El video **nunca** se transcodifica.

Consecuencia: la app no necesita decoders de MP2, MP3 ni AC-3, y con ellos
desaparecen la extensión FFmpeg, el NDK y toda la compilación nativa.

### El AAC ya no está garantizado, y así es como falla

Esa decisión se tomó cuando había **un** headend y era nuestro. Con uno por ISP
(§1), el AAC pasa de garantía a **requisito de despliegue**: se cumple en los
sites donde lo configuramos bien, y no se cumple en los demás.

**La forma en que falla es silenciosa y cara.** La caja de pruebas decodifica
`audio/mpeg`, `audio/ac3` y `audio/eac3` por hardware, así que un canal mal
normalizado **se oye perfecto aquí** y es silencio en una caja barata sin esos
decoders. Nadie se entera hasta que llama un abonado de un site al que hay que
ir. Es exactamente contra lo que avisa §9.

Tres defensas, y las tres se aplican:

1. **El generador del `config.json` no publica un canal cuyo audio no sea
   AAC**, o lo marca. Es el momento más barato de detectarlo, porque el dato lo
   da la API de Flussonic.
2. **La app registra siempre el códec de audio recibido** y lo eleva a aviso
   cuando no es AAC. Una línea —`audio.noNormalizado canal=X codec=mp2`—
   convierte «un abonado no tiene sonido» en «el canal 207 de este site no pasó
   por la normalización».
3. **La guía de Flussonic es el manual de despliegue de cada site**, no una
   ayuda interna: `segment_duration 2`, `segment_count 12`, origen por
   `tshttp://`, `source_timeout` y `EXT-X-SERVER-CONTROL`.

Lo que **no** se hace es reintroducir decoders de MP2 o AC-3 en la app. La
salida es arreglar el site, no cargar en ~120 cajas el peso de un headend mal
configurado.

---

## 3. Dos modos de trabajo

Este proyecto tiene dos tipos de trabajo con reglas distintas. Identifica
siempre en cuál estás antes de empezar.

**Edgar** es quien valida en pantalla y declara el cierre de cada fase de
Modo A. Ningún criterio de Modo A se da por cumplido sin su visto bueno.

### Modo A — Motor de reproducción: paso a paso, con validación humana

Aplica a todo lo que afecte **qué se ve y cómo se ve**: decodificación,
selección de backend, buffers, red, recuperación, flags de extractor, render,
aspecto, entrelazado, sincronía, zapping.

Reglas del Modo A:
- **Una fase = una falla = un commit = una APK.**
- Edgar la prueba **en pantalla** y declara si está resuelta.
- **No se avanza** a la siguiente fase sin su visto bueno explícito.
- Prohibido arreglar dos cosas de motor en el mismo commit.
- Cada cambio de comportamiento va detrás de un toggle en el menú de
  desarrollador, con el valor por defecto igual al comportamiento anterior.
- Al cerrar, se escribe la entrada en `LIVETV_LOG.md`.

**Por qué:** los fallos de reproducción se juzgan mirando la pantalla. Ninguna
prueba automática distingue "se ve bien" de "se ve con peine" o "el audio va
medio segundo adelantado".

### Modo B — Todo lo demás: autónomo

Aplica a lista de canales, EPG, navegación, guía, favoritos, overlays,
estética, marca dinámica, cliente del servidor de configuración, telemetría,
build, empaquetado y preparación para Play Store.

Reglas del Modo B:
- Claude Code trabaja de corrido: detecta fallas, las corrige y sigue.
- Puede agrupar varios cambios en un commit si son del mismo tema.
- No hace falta esperar validación por cada arreglo.
- Se reporta al final del bloque lo que se hizo y lo que quedó pendiente.

### La frontera

Si un cambio altera **decodificación, red, buffers, render o recuperación**,
es Modo A aunque parezca de interfaz.

Ejemplo: el botón para cambiar de pista de audio es Modo B; cómo el motor
aplica ese cambio sin cortar el video es Modo A. Ante la duda, trátalo como
Modo A y pregunta.

---

## 4. Aislamiento del módulo

### El invariante

**Películas y series se comportan exactamente como en el upstream.** Ese es el
fin. Todo lo demás de esta sección son medios, y un medio que estorbe se
cambia; el invariante no.

No es "no toques estos archivos". Es "no cambies ese comportamiento". Tocar un
archivo del camino de VOD está permitido si se demuestra que el VOD no cambia;
no tocarlo pero alterar su comportamiento desde fuera está prohibido.

### Las reglas

1. **El motor vive en el módulo Gradle `livetv`.** Cascada, capacidades,
   buffers, resiliencia, diagnóstico y cliente de configuración. El módulo
   **no depende de `app`**. Es la parte difícil y volátil, y la que debe
   sobrevivir intacta a los rebases del upstream.
2. **La interfaz de LiveTV vive en `app/.../ui/livetv/`, reescrita desde
   cero.** Usa `JellyfinTheme` y los componentes de `ui/base/`, porque la
   sección tiene que verse como el resto de la app. El sistema de diseño no se
   extrae a un módulo aparte: moverlo haría chocar cada cambio de upstream al
   tema en cada rebase, que es justo el dolor que esta sección evita.
3. **El camino de VOD no se modifica** — `PlaybackController`, `VideoManager`,
   `CustomPlaybackOverlayFragment`, `PlaybackManager`. No porque el directorio
   sea sagrado, sino porque es el camino de películas y series y el invariante
   manda. Si alguna vez hiciera falta tocarlo, es Modo A con validación en
   pantalla del VOD, no solo del canal.
4. **PROHIBIDO construir sobre `playback/core`** (el playback rewrite del
   upstream): está incompleto y va a cambiar.
5. **La sección LiveTV de Jellyfin se reemplaza, no convive.** La fila del
   inicio apunta a la sección nueva. El código viejo (`ui/livetv/`
   `LiveTvGuideFragment`, `TvManager`, `GuideFilters`) se queda en el árbol
   pero inalcanzable: borrarlo agrandaría el diff contra upstream sin ganar
   nada. Grabación y DVR ya están fuera de alcance (§7).
6. **Selección de pistas propia, por identidad de pista del stream:**
   idioma normalizado como clave primaria, PID como desempate estable dentro
   de una misma sesión. Nunca por índice de Jellyfin. Prohibido reusar
   `offsetStreamIndex` ni `getExoPlayerTrack`/`setExoPlayerTrack`.

   **Y la etiqueta puede mentir.** En algunos satélites una pista marcada `spa`
   trae inglés. Por eso la preferencia por idioma es un **valor por defecto**,
   nunca una imposición: el abonado tiene que poder cambiarla, y su elección
   manda sobre lo que diga el `config.json` para el resto de la sintonía.

   **Por qué no basta el PID** (medido 2026-09-22): Flussonic renumera los
   PIDs al remuxar y entrega los seis canales con video `0xd3` y audio `0xdd`
   (+`0xde` el segundo). El PID es un alias de la posición y no distingue
   nada; en `discoveryhd-70w` y `hbohd-70w` el español y el inglés se separan
   **solo por la etiqueta de idioma**. Esa etiqueta debe normalizarse
   aceptando `spa`, `esp`, `esl` y `es` como español (ver `CHANNELS.md`).
7. **Verificación obligatoria antes de cada commit.** El diff ya no sirve
   como prueba única, porque ahora hay código nuevo legítimo fuera de
   `livetv/`. Son dos comprobaciones:

   ```bash
   # a) nada tocado fuera del territorio permitido
   git diff --stat upstream-base..HEAD -- \
     ':!livetv/' ':!app/src/main/java/org/jellyfin/androidtv/ui/livetv/' \
     ':!*/flavors/*' ':!*.md'
   ```

   Lo que aparezca aquí son **ocho cosas y ninguna más**, y se justifican una
   a una en el commit:

   - **enganches de navegación** — `Destinations.kt`, `MainToolbar.kt`,
     `HomeFragmentLiveTVRow.kt`, `HomeRowsFragment.kt`;
   - **archivos de compilación** — `app/build.gradle.kts`,
     `settings.gradle.kts`, `.gitignore` y `gradle/libs.versions.toml`. El
     primero lleva además el `applicationId`, que es identidad publicada y no
     configuración (§8); el último es donde §5.11 obliga a fijar las versiones,
     así que añadir libVLC o Media3 pasa por ahí **(añadido a esta lista el
     10-oct: llevaba en el diff desde el principio y la lista no lo nombraba)**;
   - **la excepción registrada de `deviceProfile.kt`** (§6, retirada de
     nextlib);
   - **marca y avisos legales** — `ui/preference/category/about.kt`,
     `ui/startup/fragment/ConnectHelpAlertFragment.kt`,
     `ui/startup/fragment/SelectServerFragment.kt` **(añadido el 14-oct: su
     pie de versión llamaba «jellyfin-androidtv» a la app en la primera
     pantalla de toda instalación, que es justo donde §8 no lo permite)**,
     los `res/values*/strings.xml`, **los dos layouts de la pantalla de acceso
     —`layout/fragment_user_login.xml` y
     `layout/fragment_user_login_quick_connect.xml`— (añadidos el 27-oct)**,
     más `drawable/velo_acceso.xml` y
     `ui/startup/fragment/UserLoginFragment.kt` **(añadidos el 30-oct: el
     subtítulo de esa pantalla caía a la cadena literal «Jellyfin» cuando el
     servidor no traía nombre o todavía no se había resuelto —la app
     llamándose Jellyfin donde §8 menos lo permite—, y el velo de su columna
     de texto dejaba un borde duro a la mitad exacta de la pantalla)**,
     y `layout/fragment_server_add.xml` **(añadido el 30-oct: su título
     decía «Introduzca la dirección del servidor» y su ayuda, dos líneas más
     abajo en la misma pantalla, «Por favor, introduce…». Usted y tú a la
     vez, en la primera pantalla de toda instalación y en el único mercado
     donde se publica. Van a cadenas `livetv_` por lo mismo que los layouts
     de acceso: corregirlas en `values-es-rMX` las perdería en el próximo
     rebase y nada avisaría)**,
     y `auth/repository/ServerRepository.kt` **(añadido el 30-oct: el
     nombre con el que se guarda un site cuyo Jellyfin no declara
     `ServerName` era la cadena literal «Jellyfin Server», y ese nombre es el
     que se pinta en la lista de servidores y en el «Conectar a …» de la
     pantalla de acceso. Es el defecto del subtítulo entrando por otra
     puerta: el arreglo de `UserLoginFragment` no lo tapaba, porque con ese
     respaldo el nombre no llega vacío sino relleno con la marca equivocada.
     Cae a la dirección. Es texto, no lógica, y **no se ha visto ocurrir**:
     Jellyfin rellena ese campo con el nombre de la máquina, así que es una
     defensa sin medida detrás)**,
     y `ui/startup/fragment/ServerAddFragment.kt` **(añadido el 30-oct: el
     selector de proveedor. Esa pantalla pedía «escribe la dirección del
     servidor» y lo que hay que escribir es `http://192.168.20.249:8096`, que
     ningún abonado sabe. Ahora pregunta al registro por la red desde la que
     se llama y, si no la reconoce, pide el código que el ISP le dio al
     abonado; **la dirección a mano no se esconde**, porque es la única que
     funciona sin internet y es por donde entra el revisor de Google. El
     diseño está en `docs/selector-de-proveedor.md`. **Esto es lógica en una
     pantalla compartida con el VOD**, como la puerta de la sesión: añade
     caminos y no quita ninguno, pero exige que Edgar valide la entrada en
     pantalla)**,
     y `layout/fragment_select_server.xml` **(añadido el 30-oct: el botón que
     lleva a esa pantalla decía «Introducir dirección del servidor» —de
     upstream, en usted mientras el resto tutea— y desde que detrás hay un
     selector ya ni describe lo que pasa: el abonado que no sabe ninguna
     dirección no pulsa un botón que le pide una)**,
     **los tres sitios donde la app dice su propio nombre al servidor del ISP
     —`di/AppModule.kt`, `telemetry/TelemetryService.kt` y
     `util/profile/deviceProfileReport.kt`— (añadidos el 27-oct)**,
     los recursos de marca (`app_logo`, `app_icon*`,
     `app_banner*`, `mipmap/`) y los atributos `icon`, `banner` y `label` del
     `AndroidManifest.xml`.

     **Por qué tres archivos para un nombre:** la app se presentaba al Jellyfin
     del ISP como «Jellyfin Android TV», y eso sale en su lista de sesiones y de
     dispositivos — justo lo que el panel de abonados enseña al operador al
     confirmar un alta. El valor estaba escrito a mano en tres sitios que mandan
     al **mismo** servidor: el `ClientInfo` del SDK, la cabecera de autorización
     con la que se sube un informe de fallo y el encabezado `client:` de los dos
     informes que se suben (`clientLogApi.logFile`). Arreglar uno y dejar los
     otros deja el nombre viejo en los registros del site, que es donde alguien
     lo encontraría dentro de un mes. El nombre vive ahora en
     `BuildConfig.CLIENT_NAME`, declarado en `app/build.gradle.kts`, que ya es
     territorio permitido. Son cambios de texto; ninguno de lógica.

     **Por qué los layouts y no solo las cadenas:** las de upstream vienen
     traducidas a cuarenta idiomas, así que sobrescribir
     `login_quickconnect_step_*` en `values/` habría dejado el texto viejo en
     los otros treinta y nueve. Con cadenas de nombre propio —el prefijo
     `livetv_` que ya usan los avisos legales— cualquier idioma cae en la
     nuestra, y el precio es cambiar cuatro atributos `android:text`. Son
     cambios de texto y de color, ninguno de lógica. Es §8 hecho código: la app no usa el nombre ni el
     logo de Jellyfin, y la pantalla de información lleva el «no afiliado» y
     la licencia GPL-2.0. **No es opcional ni es estética**: es lo que hace
     legal el envío a Play, y vive por fuerza en las pantallas del upstream
     —no existe una sección de información propia y hacerla costaría más
     diff que reusar la suya—;
   - **la puerta de la sesión del abonado** — `auth/model/LoginState.kt`,
     `auth/model/SessionFailure.kt`, `auth/repository/SessionRepository.kt`,
     `auth/repository/AuthenticationRepository.kt`,
     `ui/startup/fragment/ServerFragment.kt` y los dos
     `ui/startup/fragment/UserLogin{Credentials,QuickConnect}Fragment.kt`, más
     su prueba en `app/src/test/kotlin/auth/` **(añadidos el 28-oct)**.

     **Qué arregla:** el servidor del ISP distingue tres cosas y la app las
     trataba como una. `SessionRepository` atrapaba `ApiClientException` a
     secas, así que un **403** —contrato suspendido, el token sigue vivo— y un
     **«no contesta»** acababan los dos donde acaba un 401: en la pantalla del
     código. Medido el 28-oct contra Jellyfin 10.11.11 **y** 12.0.0, tres
     llamadas por estado y cuatro rutas. El daño del primero lo vio el panel de
     abonados en la caja: un abonado suspendido llama a pedir un código en vez
     de a pagar, y con uno nuevo entra con otra sesión. El del segundo no lo
     había pedido nadie: una caja encendida antes que su router perdía la
     cuenta igual que una dada de baja, porque el SDK convierte
     `ConnectException`, `InterruptedIOException` y `UnknownHostException` en
     `TimeoutException`, que hereda de `ApiClientException`.

     **Esto no es Modo B, y es la primera vez que esta lista admite lógica.**
     Las seis categorías de arriba son texto, recursos o enganches; ésta cambia
     el comportamiento de una pantalla **compartida con el VOD**, que es lo que
     el párrafo del final de esta sección manda tratar aparte. El invariante no
     se rompe —películas y series se siguen reproduciendo igual—, pero el
     camino de entrada cambia en dos casos para **todos** los usuarios, VOD
     incluido: con un 403 se ve «Servicio suspendido» en vez de la pantalla del
     código, y con el servidor mudo se ve el aviso de siempre sin salir de la
     lista de usuarios. Los dos son mejores que lo anterior y los dos **exigen
     que Edgar valide la entrada al VOD en pantalla**, no sólo la del canal;
   - **utilería fuera de la app** — `herramientas/`, `headend/` y `.claude/`,
     que no se compilan ni viajan en el APK. `.claude/` son las skills y
     agentes del repositorio **(añadido el 10-oct)**: viven aquí y no en la
     configuración de la máquina porque son conocimiento del proyecto —qué hay
     que comprobar antes de afirmar algo, por dónde pasa el cable de un
     interruptor— y se pierde si vive fuera;
   - **la conexión con el registro** — `res/xml/network_security_config.xml`.
     Android obliga a que esa configuración viva ahí, fuera del módulo. Lo
     único que se le añade es un `domain-config` que **prohíbe el tráfico en
     claro hacia el registro**, sin tocar la base: el resto de la app sigue
     pudiendo hablar por HTTP con el Jellyfin y los streams de la LAN del ISP,
     que es de lo que vive. No afecta al VOD.
   - **el alcance de la ficha de Play** — los `uses-feature` del
     `AndroidManifest.xml` **(añadido el 29-oct)**. Un permiso puede *implicar*
     un requisito de hardware que nadie escribió: `ACCESS_WIFI_STATE` implica
     `android.hardware.wifi` como obligatorio, y Play lo usa para **filtrar el
     catálogo de dispositivos**. Una caja de ISP sólo-Ethernet no habría visto
     la ficha, que es justo nuestro cliente. El upstream lleva ahí un
     `tools:ignore="LeanbackUsesWifi"`: eso calla a lint, no a la tienda.
     Declarar la característica con `required="false"` deshace la implicación
     y **no cambia una línea de comportamiento** — ningún `uses-feature` se
     consulta en tiempo de ejecución; sólo decide a quién le aparece la app.
     Medido con `aapt2 dump badging`, que antes imprimía
     `uses-implied-feature … reason='requested android.permission.ACCESS_WIFI_STATE permission'`
     y ahora imprime `uses-feature-not-required: name='android.hardware.wifi'`.

   Cualquier otra cosa exige justificarse en el commit **y** decirlo aquí.

   **Nada de esto puede cambiar el comportamiento del VOD**, que es el
   invariante. Tocar `about.kt` cambia texto; si alguna vez hiciera falta
   tocar lógica en una pantalla compartida, deja de ser Modo B.

   ```bash
   # b) el camino de VOD, intacto
   git diff --stat upstream-base..HEAD -- \
     'app/src/main/java/org/jellyfin/androidtv/ui/playback/' 'playback/'
   ```

   **Esta segunda debe salir vacía**, con **una excepción registrada**: la
   retirada de `media3-ffmpeg-decoder` de
   `playback/media3/exoplayer/build.gradle.kts` (2026-09-22, ver arriba). No
   hay ninguna más, y añadir otra exige la misma validación en pantalla.

   Si aparece algo fuera de esa excepción, la APK no se entrega sin que Edgar
   valide películas y series contra la APK de referencia de M0.

**Razón:** el upstream está migrando su reproductor a Media3. Si el módulo no
lo toca, los rebases son triviales y las mejoras del VOD llegan gratis.

---

## 5. Reglas de ingeniería

7. **Cero números mágicos.** Toda la sintonía en `LiveTuning.kt`.
8. **Cero `try/catch` silenciosos.** Todo error se registra con canal, URL,
   códec, modelo de caja, decoder activo y estado del buffer.
9. **Logging estructurado desde el commit #1**, con buffer circular
   exportable a archivo desde la app.
10. **`LiveEngine` es una interfaz desde el primer commit**, aunque solo
    exista la implementación Media3.
11. **Prohibido actualizar Gradle, Kotlin, Compose o Media3 "de paso".**
    Versiones fijadas en `libs.versions.toml`.
12. **Prohibido hardcodear marca, color, logo, URL o nombre de canal** en el
    módulo. Todo sale de `BuildConfig` o del servidor de configuración.
13. **Prohibido descargar o ejecutar código desde el servidor.** El servidor
    elige entre motores que ya vienen en el APK; nunca entrega motores. Google
    Play prohíbe cargar código ejecutable externo.

---

## 6. Decisiones cerradas (no se re-discuten)

### Transporte
- **HLS con segmentos TS**, endpoint **`index.m3u8`** — el master.

  **Segmentos: 2–3 s solo en los seis de desarrollo.** Medido el 03-oct sobre
  producción: los streams sin `segment_count` propio —que son **todos** menos
  `mileniosd` y `beisbol`— sirven **4 segmentos de ~5,5 s**, o sea una ventana
  DVR de **~22 s**, no de ~30. Con eso, `targetOffsetMs` no puede pasar de
  ~11000; el valor de fábrica de 8000 va bien y **12000 ya no**.

  **Revisado el 02-oct; antes era `mono.m3u8`.** El media playlist multiplexa
  los audios en un solo TS y **Media3 solo expone uno**: medido sobre un
  segmento de `discoveryhd` con dos PIDs (`0xdd` spa, `0xde` eng), la app veía
  una sola pista. El master los declara como `EXT-X-MEDIA` separadas y la app
  ve las tres.

  **Y la mayoría del lineup real SÍ es ABR.** Los seis de desarrollo declaran un
  solo `EXT-X-STREAM-INF`, pero medido el 03-oct, la mayoría de los 44 streams
  vivos usan plantillas `*-ABR` de Flussonic y su master trae **dos calidades**,
  576p y 720p. Publicados tal cual, la app haría ABR.

  **La salida es la URL, no recompilar ni tocar Flussonic:** el generador publica
  `index.m3u8?filter=tracks:v2a1a2`, que devuelve un master de una sola calidad
  con todos los audios. Está en `INTEGRACION_CANALES.md` como condición.

  La app **no puede impedirlo**, solo verlo: un master con varias calidades
  registra `abr.variasVariantes` en vez de ponerse a conmutar en silencio. Si
  el generador se olvida del filtro, ese aviso es el único rastro.

  **Por qué importa más de lo que parece:** en algunos satélites **la etiqueta
  de idioma miente** —una pista marcada `spa` trae inglés—. Si la app solo
  expone una pista, el abonado no tiene salida. Poder cambiar de audio no es un
  lujo del canal de cine: es el único remedio cuando el headend etiqueta mal.

  Costo medido: ~180 ms más al primer fotograma y una conexión más.
- Conserva el contenedor MPEG-TS sin conversión, que es lo que permite llevar
  MPEG-2 y MP2 tal cual.
- **Prohibido implementar ABR.** Una sola calidad por canal.

### Motor
- **Media3/ExoPlayer** como motor principal para todos los canales.
- **libVLC** como backend de respaldo para dos casos concretos: MPEG-2 sin
  decoder en la caja, y desentrelazado por software de SD.
- La elección es **automática, por canal y por dispositivo**, mediante la
  cascada descrita en `LIVETV_ENGINE.md`.
- `MediaPlayer` del sistema y `libmpv` quedan descartados.

### Prioridad de decisión
1. Ajuste por modelo desde el servidor (lo más específico, gana siempre)
2. Preferencia por canal desde el servidor, **si la caja puede cumplirla**
3. Detección propia de la caja

**El servidor sugiere, la caja verifica.** Nunca obedecer una orden del
servidor que el hardware no puede cumplir.

### Códecs
- **Sin extensión FFmpeg.** Al normalizarse el audio a AAC en Flussonic
  (§2), el módulo `livetv` no necesita decoders de `mp2`, `mp3` ni `ac3`.
  Caen con ella el NDK, la compilación nativa por cada versión de Media3 y el
  cumplimiento LGPL-2.1 de FFmpeg.
- **`EXTENSION_RENDERER_MODE_ON` deja de aplicar:** sin extensión no hay nada
  a lo que caer. El módulo usa los decoders de la plataforma.
- **No existe decodificación de video por FFmpeg.** La extensión FFmpeg de
  Media3 es **solo de audio**; no hay renderer de video. Decodificar
  `mpeg2video` por FFmpeg habría exigido escribir un renderer propio. Queda
  escrito para que nadie lo reintroduzca: el respaldo de video es libVLC.
- **PROHIBIDO usar nextlib o jellyfin-androidx-media** (GPL-3.0), y **ya
  retirado del fork** el 2026-09-22.

  El motivo que este documento daba antes — "incompatibles con la distribución
  en Play" — **era incorrecto**: en Google Play sí se distribuyen apps
  GPL-3.0; la tienda con ese conflicto es la de Apple. El motivo real es que
  **este fork es GPL-2.0 sin cláusula "or later"**, y GPL-2.0 y GPL-3.0 se
  consideran mutuamente incompatibles. Venía heredado del upstream.

  Se retiró porque **no costaba nada**: la librería solo aportaba decoders de
  **audio** (`flac alac pcm_* mp3 aac ac3 eac3 dca mlp truehd`, ninguno de
  video), y de ellos únicamente DTS, TrueHD, MLP y ALAC dependían de ella.
  Medido sobre la biblioteca de pruebas: 582 títulos, 1075 pistas, **100 %
  AAC**. Cero títulos afectados.

  Al quitarla hubo que recortar `supportedAudioCodecs` en `deviceProfile.kt`,
  que declaraba DTS y TrueHD **incondicionalmente**, sin derivarlo de lo que
  la caja puede. Dejar esa promesa habría dado películas sin sonido en vez de
  transcodificación en el servidor.

  Nota para quien reintroduzca el tema: esos decoders **no necesitan GPL**.
  El build de Jellyfin no pasa `--enable-gpl`, así que su FFmpeg es LGPL; el
  GPL-3.0 sale de la licencia de su repositorio envoltorio. Si algún día hace
  falta DTS o TrueHD, la salida limpia es compilar la extensión LGPL propia.
- **Prohibido nombrar marcas Dolby** en UI, ficha de Play o marketing.

### Render
- **SurfaceView**, nunca TextureView.
- **Respetar el SAR** (`pixelWidthHeightRatio`): **dos de los seis canales**
  son anamórficos — `estrellashd-mp4` (4:3) y `estrellassd-mp2v` (20:17) — e
  ignorarlo deforma la imagen. Los otros cuatro son 1:1.
- Modos de ajuste manual: Fit / Fill / Zoom / 4:3 / 16:9.
- **Tunneled playback activado por defecto** (01-oct, decisión de Edgar, que
  invierte la regla anterior). Es lo único medido que elimina los microcortes
  del cine en H.264: el decodificador AVC de Amlogic ignora el pulldown 3:2 y
  da 2 campos a cada imagen en vez de 3 y 2 alternos, lo que deja al
  reproductor 333 ms corto cada 1.6 s —el audio sigue, el vídeo se para—.
  Validado en pantalla dos veces y sin penalización de zapping (1.1 a 1.5 s en
  arranque limpio).

  **Matizado el 27-oct: en zapeo en caliente la penalización existe y está
  medida.** Aquella frase salió de comparar arranques en frío, que no podían
  verla. Sobre 94 zapeos en cuatro corridas de la misma ventana y la misma APK
  —debug—, Media3 con tunneled da **612 ms de p50** al primer fotograma pintado
  y **374 ms** con el tunneled apagado: **~240 ms de p50 y ~480 de p95 por
  zapeo**. **No es un motivo para apagarlo**, y conviene leer las dos cifras
  juntas: 1,6 min sin tunneled dieron 15 rebuffers y el offset trepando a 20,7 s
  (08-oct), y las cuatro condiciones quedan muy por debajo del objetivo de §10.
  240 ms al zapear es barato frente a un canal que se corta cada cuatro
  segundos.

  **El riesgo era pantalla negra silenciosa**, y es la función más dependiente
  del fabricante de toda la pila. Desde el 05-oct hay red: si a los 8 s no ha
  llegado el primer fotograma estando el reproductor en marcha y la posición
  avanzando, **el motor apaga el tunneled para esa sesión y rehace la
  preparación** (`LiveTuning.RESCATE_TUNNELED_DEFAULT`, activo de fábrica,
  validado en pantalla).

  Eso convierte el fallo de permanente y mudo en un corte de unos segundos que
  se cura solo, **sin esperar a saber qué modelo falla**. Sigue siendo mejor
  excluir el modelo por `deviceOverrides` en cuanto se sepa: el rescate cuesta
  un rebuffer en cada sintonía de esa caja.

  **La exclusión va por `deviceOverrides` del `config.json`**, no por
  recompilación: `"tunneling": false` para los modelos que no deban usarlo. El
  modelo por defecto es de exclusión, no de inclusión.

  **Consecuencia para el diagnóstico:** en modo tunneled el decodificador
  escribe directo al hardware de pantalla, así que el contador de fotogramas y
  `SurfaceFlinger --latency` **no ven nada**. Se pierden fps, huecos de entrega
  y entregas tardías. Antes de concluir que algo no ocurre en ese modo, hay que
  comprobar que el instrumento podría verlo si ocurriera.

### Red
- `connectTimeoutMs = 8000`, `readTimeoutMs ≥ 30000`.
- `LoadErrorHandlingPolicy` propia con backoff exponencial y reintentos
  efectivamente infinitos.
- **Prohibido cerrar el player ante un error de red.** Un canal caído por
  lluvia es un estado temporal: overlay discreto y seguir reintentando.
- **Prohibido vetar un decoder por errores ocurridos durante corrupción de
  señal.** En banda Ku, la lluvia hace fallar decoders sanos.

### Backends del sistema
Son **dos, separados, y los dos viven en el site del ISP** (§1):

| | VOD | Canales en vivo |
|---|---|---|
| Servidor | Jellyfin del ISP | Servidor de canales del ISP |
| URL | la escribe el usuario | **derivada del host de Jellyfin** |
| Auth del contenido | usuario Jellyfin | ninguna (v1) |
| Acceso a la sección | sesión Jellyfin | **sesión Jellyfin** |

**La sección de canales queda detrás del inicio de sesión.** El suscriptor
entra a Jellyfin y desde ahí llega a TV en vivo, igual que a Inicio o Buscar.

**La URL de canales se deriva, no se configura.** El abonado ya tiene que
escribir la dirección de Jellyfin; pedirle una segunda sería pedirle algo que
no sabe. La app toma el host de `ServerRepository.currentServer` y busca ahí el
servidor de canales. Una sola app, cero configuración añadida, y dar de alta un
ISP es instalarle el servidor de canales en su site.

**Y antes de derivar, se pregunta al registro.** Un servicio nuestro en la nube,
con una fila por site, que dice si ese host pertenece a un ISP con contrato
vigente y **dónde está su servicio de canales** (`REGISTRY_SCHEMA.md`). Lo que
de verdad compra no es el control de acceso: es que mover el servicio de canales
de un site deje de exigir una versión nueva de la app.

```
1. El registro dice dónde está        → se usa eso
2. No contestó, o no lo dice          → derivar por convención
3. Tampoco                            → caché de ese site
4. Tampoco                            → lista de arranque del BuildConfig
```

Tres reglas del registro que no se negocian, y están ahí porque el fallo de un
servicio nuestro no puede apagar televisiones:

- **La autorización caduca, y mientras no caduque no se vuelve a preguntar.**
  En semanas, no en horas: el daño de cortar de más es peor que el de cortar
  tarde.
- **Si el registro no contesta y la autorización sigue viva, se entra.** Hay
  despliegues donde la caja llega a la red del ISP pero no a internet.
- **La puerta cubre solo la sección de canales.** Bloquear el login de Jellyfin
  bloquearía también el VOD servido por Katalog y convertiría cualquier fallo
  del registro en un ISP entero sin nada.

**No es un candado técnico y no se escribe como si lo fuera.** Este fork es
GPL-2.0: el código es público, la comprobación se ve y cualquiera puede
compilar una versión sin ella. Es un interruptor operativo sobre gente que
coopera, y para eso basta.

**La convención es el puerto, no una ruta** (`LiveTuning.CHANNEL_SERVICE_PORT`,
hoy **8097**), y es lo que se usa cuando el registro no dice nada. Un puerto solo lo puede ocupar un proceso, así que el servicio no
puede escuchar en el de Jellyfin: o va detrás de un proxy en una ruta —lo que
obliga a montar nginx en cada site— o va en el suyo. El puerto funciona haya
proxy o no.

```
Jellyfin   http://192.168.20.249:8096/        ← lo escribe el abonado
Canales    http://192.168.20.249:8097/config.json   ← derivado
```

Se conserva el esquema (un site con TLS no se degrada a `http`) y se descartan
ruta, consulta y credenciales: Jellyfin puede estar en `https://tv.isp.mx/jellyfin`
y el servicio sigue en `https://tv.isp.mx:8097`, porque es la misma máquina pero
otro proceso. Sin esquema no se adivina: asumir mal deja la sección muerta.

**Mover ese puerto una vez desplegado obliga a tocar todos los sites a la vez
que la APK.** Se cambia antes del primer despliegue o no se cambia.

Los dos backends siguen siendo independientes en lo que importa: **el módulo
`livetv` no consulta al servidor Jellyfin para nada**. Recibe una URL ya
resuelta y no sabe de dónde salió — quien la deriva es `app/.../ui/livetv/`,
que sí puede leer la sesión. El módulo no depende de `app` (§4.1).

Lo que la sesión controla es **quién ve la sección**, no de dónde salen los
canales.

Consecuencia práctica: la sesión de Jellyfin es la puerta única de la app, así
que el enganche natural es la barra principal, junto a Inicio y Buscar, y no
el destino raíz de la navegación.

#### Qué sirve el servidor de canales, y qué no

| Contenido | De quién es | Cadencia |
|---|---|---|
| Lista de canales y grupos | del ISP | cuando cambia su lineup |
| Marca (§8) | del ISP | casi nunca |
| `deviceOverrides` | **nuestro** | cuando aprendemos algo de un modelo |
| EPG | del ISP | cada día, y pesa |

**Los `deviceOverrides` no son del ISP y no deben editarse por site.** «Este
modelo de caja no soporta tunneled» es conocimiento que se gana una vez,
mirando una pantalla, y vale para todos. Si vive en el archivo de cada site hay
que copiarlo a N sitios a mano, y el día que se salte uno ese ISP arranca sin
imagen en un modelo que ya sabíamos que fallaba.

**La salida es generar el archivo, no editarlo.** La herramienta que produce el
`config.json` de cada site —la misma que lee la API de Flussonic para la lista—
inyecta nuestra tabla de modelos al generarlo. El ISP pone sus canales,
nosotros ponemos el conocimiento de hardware, sale un solo archivo y nadie
copia nada.

**El EPG va por puntero, no dentro del `config.json`.** La lista cambia cuando
alguien la cambia; la guía cambia cada día y pesa megas. Juntos, cada caja se
baja la guía entera para comprobar si cambió un canal. El `config.json` lleva
la URL del XMLTV y cada cuánto refrescarlo, y la guía viaja aparte con su
propio cacheo. Eso además permite que un site sirva canales sin EPG mientras su
tubería de guía no esté lista.

#### Contenedores genéricos: datos nuevos sin versión nueva

La regla de §13 —**el servidor elige entre lo que ya trae el APK, nunca entrega
capacidades**— se aplica igual al diseño. El servidor recombina; no inventa. Una
pantalla que no existe o un componente que nadie escribió son código, y eso es
una versión de la APK. La diferencia con los flavors es que esa versión se hace
**una vez y llega a todos**.

Pero buena parte de lo que un ISP va a querer añadir no es comportamiento: es
**texto que se muestra**. Para eso se definen contenedores genéricos, y se
definen **ahora**: meterlos después significa que los primeros datos extra sí
costaron una versión.

Un canal —y la configuración en su raíz— puede llevar una lista de pares
etiqueta/valor que la app pinta **sin saber qué son**:

```json
"extras": [
  { "label": "Calidad", "value": "HD" },
  { "label": "Región",  "value": "Norte" }
]
```

Un ISP pone unos, otro pone otros, y nadie toca la app.

Las reglas que lo hacen seguro:

- **Solo texto.** Sin enlaces, sin acciones, sin formato. En cuanto algo
  necesita comportamiento, deja de ser dato y vuelve a ser código.
- **Acotado.** Número máximo de entradas y longitud máxima por campo, recortando
  en silencio. Un `config.json` no puede empujar la barra fuera de pantalla.
- **Una entrada mal formada se descarta, no tira la lista.** Misma regla que ya
  rige para los canales: perder un canal por una errata sería desproporcionado.
- **Lo desconocido se ignora y se registra.** Es lo que permite que un servidor
  nuevo hable con una APK vieja.

Lo que esto **no** cubre: información que necesite lógica propia. Eso sigue
siendo una versión — una, para todos.

#### El esquema tiene que sobrevivir a sus propios servidores

La APK se actualiza para todos a la vez por Play. Los servidores de canales se
actualizan site por site. **Una APK nueva va a convivir con servidores viejos
durante meses**, así que:

- Un campo que no está se resuelve con el valor de fábrica, nunca con un fallo.
- Un `version` mayor del que la app conoce se usa igual, ignorando lo que no
  entienda, y se registra.
- El cache se indexa **por servidor**. Hoy es un solo `files/livetv/config.json`
  y una caja que cambie de servidor hereda la lista del anterior.

---

## 7. Fuera de alcance

- ABR, DRM, transcodificación en cliente
- Transcodificación de **video** en el headend
- Subtítulos **de pista aparte**. Los *closed captions* **sí entran** desde el
  01-oct: `estrellashd` los emite incrustados en el vídeo como CEA-608 y con
  texto real —medido: 22 entradas con diálogo en 40 s—, así que «no se emiten»
  era falso para ese canal. Se exponen pidiéndole al extractor HLS que los
  busque (`exposeCea608WhenMissingDeclarations`) y se eligen desde los
  controles de la barra. De los seis canales, **solo ese los trae**
- Grabación, DVR y catch-up en la app (el servidor lo soporta, la app aún no)
- Autenticación de suscriptor (versión futura)
- Filtros de imagen, post-procesado, "mejoras" de video
- TvInputService / Android TV Input Framework (es OEM-only en la práctica)
- Auto-actualización del APK fuera de Play (Play lo prohíbe)
- Refactorizar código existente fuera del alcance

---

## 8. Marca

**Una sola APK y una sola ficha en Google Play, publicada por nosotros.** No
hay `productFlavors` por ISP. La marca del proveedor la sirve **el registro o su
propio servidor de canales** (§6) y la app se viste al conectar. El orden es el
mismo que para todo lo demás: registro, site, marca neutra.

Los ISPs no piden ficha propia: piden que funcione y un poco de
personalización. Y el costo de los flavors nunca fue programarlos — era
publicar: N fichas, N firmas, N revisiones de Google y N despliegues por cada
corrección.

### Ranuras fijas, no tema libre

La personalización es un **conjunto cerrado de ranuras**:

| Ranura | Para qué |
|---|---|
| `displayName` | el nombre que se ve dentro de la app |
| `accent` | color de acento |
| `logo` | URL, cacheada; con iniciales de respaldo |

Nada más, y **el contraste se valida al cargar**. Con colores libres, tarde o
temprano un ISP elige un acento que sobre el negro de la sección de canales no
se lee, y la llamada de soporte es nuestra. Una ranura que no pasa la
validación cae al valor neutro y se registra.

Lo que **no** cambia en caliente: el nombre y el icono de la ficha de Play, y
el icono del lanzador. Son estáticos por construcción. Se puede intercambiar el
icono con `activity-alias`, pero todas las variantes viajan dentro del APK, el
lanzador pierde el acceso directo a veces y en Android TV es frágil: **no se
hace**.

### La marca neutra se diseña, no se improvisa

Es lo que ve quien aún no ha entrado, quien apunta a un servidor sin marca
configurada y quien arranca sin red. No es un caso raro: **es la primera
pantalla de toda instalación**. No puede decir Jellyfin ni parecer un error.

Y la marca **se cachea**. Si no sobrevive a un arranque sin red, la app se ve
genérica en cada corte — que es exactamente el fallo del `config.json`
congelado del 02-oct.

### Quién tiene derecho a pintar la app

La marca sale del servidor de canales del site, **no del servidor Jellyfin**.
Si saliera de Jellyfin, cualquiera que apunte la app a un Jellyfin propio
podría vestirla.

Con la APK abierta en Play, alguien puede levantar su propio servidor de
canales y tener «nuestra» app con la marca que quiera. El daño es bajo —es su
instalación— pero el editor de registro somos nosotros. **En v1 se acepta.** Si
algún día importa, la salida es verificar la marca contra una lista nuestra, y
queda escrito aquí para no reinventarlo.

### La app se llama Palomera

Decidido el 2026-10-27, **y antes se llamó Palco**. El criterio no cambió: el
nombre nombra **el aparato, no una empresa**, porque dentro de la app la marca
es la del ISP y un nombre que suene a marca competiría con la de nuestro propio
cliente en su propia caja. Tiene que ser un sustantivo, caber en el lanzador sin
truncarse y aceptar el posesivo del ISP sin forzarlo: «la Palomera de
TotalPlay».

**Palco se cayó porque estaba tomado**, y eso se comprobó contra el buscador de
cada tienda, no por búsqueda web: en Play México hay `RTP Palco`, `Palco 90` y
`Palco – Control de Acceso`, y en la App Store cinco más. Peor todavía, **en
México «palco» significa palco de estadio**: las dos apps mexicanas que salen
son de renta de palcos. Cayeron por lo mismo `Platea` (`PlateaTV`), `Butaca`
(`Butaca TV`, que además está en Roku y Fire TV), `Tramoya`, `Atalaya`,
`Sintonía`, `Cartelera` y `Zapeo` — ésta con un `Zapeo – Reproductor IPTV`
encima, que es nuestra categoría exacta.

**Palomera está limpia en Play México y en la App Store México**, verificado
contra el buscador de las dos. Y como marca es lo contrario de un nombre
descriptivo: arbitraria para un servicio de televisión, que es la clase más
fácil de registrar y de defender.

**Lo que sigue sin verificar, y hay que verificar:** IMPI clases 38 y 41, el
título en la Play Console, y Samsung, LG y Roku — estas tres no tienen buscador
público al que se pueda llegar desde aquí, así que se miran a mano. Una
búsqueda en tiendas **no es una búsqueda de marcas**.

**Nunca se añade «Player» ni ningún apellido genérico.** `‹Marca› Player` es la
firma de la categoría de reproductores IPTV en Play —diez de los trece primeros
resultados se llaman así—, no aporta nada registrable porque es genérico, y
pone la app en la estantería que un revisor de Google examina buscando señales
sin licencia. El descriptor va en el título de la ficha, no en el nombre.

**El `applicationId` es `mx.kataliziador.palomera`, y es lo único irreversible
de todo el proyecto.** Play lo fija en el primer envío. Veníamos arrastrando
`org.jellyfin.androidtv`, que es el paquete del upstream y ya está publicado por
el proyecto Jellyfin: con él el envío se rechaza y la app choca con la Jellyfin
de verdad en la misma caja.

El `namespace` de Gradle **sigue siendo `org.jellyfin.androidtv`** y no se toca:
es el paquete Kotlin de ~2.000 archivos, sólo existe en tiempo de compilación y
renombrarlo haría ingobernable el rebase contra upstream (§4) sin que nadie
llegue a verlo. Lo que se publica es el `applicationId`.

**El nombre visible se puede cambiar; el paquete no** — pero sólo hasta el
primer envío, y por eso el cambio del 27-oct se hizo entero: `applicationId`,
`app_name` y el nombre del artefacto a la vez. El id de paquete es **público**,
va en la URL de la ficha, y dejarlo diciendo `palco` habría sido dejar dentro
la palabra de otro para siempre.

**El dibujo se rehízo el mismo día.** El símbolo es una palomera: el cubo
troncocónico con su doblez y el montón de palomitas asomando. Dos detalles que
costaron seis variantes y que conviene no deshacer: **sin el doblez del borde
la silueta se lee como una magdalena**, y **si los valles entre los bultos de
arriba bajan hasta el borde del cubo, sale una corona** — tienen que solaparse
entre sí. La palabra sigue construida con círculos y rectángulos, con una
excepción: la `e` lleva bola de 11.5 y barra de 7 porque con el radio 10 y el
trazo 9 del resto su ojo mide una unidad y se cierra.

### La primera pantalla pide un código, no manda a instalarse Jellyfin

Cambiado el 27-oct. Lo que el upstream enseñaba al estrenar una caja era
*«1. Abra la aplicación Jellyfin en su teléfono o navegador e inicie sesión con
su cuenta»*. Eso nombra a Jellyfin como el sitio al que el abonado tiene que
ir —lo que §8 no permite— y además describe algo que ningún abonado va a
hacer: no tiene esa app, no sabe que tiene una cuenta y está estrenando una
caja, no iniciando sesión.

Ahora dice **llama a tu proveedor, dile que quieres activar este aparato,
dale este código**. Quien autoriza el código es el proveedor desde el panel de
abonados (`/home/proyectos/panel-abonados`), que es el único sitio donde se
puede hacer sin meter la caja como administrador — el panel web de Jellyfin no
expone el `userId` de `POST /QuickConnect/Authorize`, así que autorizar desde
ahí entrega la caja con permisos de administrador.

**El botón «Usar contraseña» no se quita.** Mientras un site no tenga el panel
instalado, es el único camino de alta que existe, y es también por donde entra
el revisor de Google (`PLAY.md` §6). Lo mismo vale para el título compartido de
esa pantalla, que pasó de «Por favor inicie sesión» a «Activa tu televisión»:
el abonado no está iniciando sesión, está estrenando un aparato.

### Lo que sigue siendo obligatorio

**La app no usa el nombre ni el logo de Jellyfin.** La pantalla de información
incluye "no afiliado al proyecto Jellyfin" junto con los avisos de licencia
GPL-2.0 (fork) y LGPL-2.1 (libVLC, **puesto el 08-oct con M6**). Con una sola app esto es
**una pantalla**, no N.

### El contenido es del ISP, y eso se dice en tres sitios

**Decidido el 29-oct, a petición de Edgar.** Una sola APK sirve a varios ISPs
y lo que se ve en ella es de cada uno: nosotros no lo producimos, no lo
elegimos y no tenemos sus derechos. Eso tiene que estar escrito donde se lea,
y está en tres sitios porque cada uno sirve a alguien distinto:

| dónde | para quién |
|---|---|
| fila «De quién es el contenido» en Información (`livetv_legal_content`) | el abonado, y viaja con cada instalación para siempre |
| descripción completa y notas al revisor (`FICHA.md` §3 y §4) | quien revisa el envío en Google |
| <https://kataliziador.github.io/palomera/> | cualquiera que llegue de fuera |

**Y lo que un aviso no hace:** transferir la responsabilidad. Describe el
acuerdo, no lo crea. Lo que protege de verdad es la cláusula del contrato con
cada ISP en la que él declara tener los derechos de lo que emite y responde
por ello. El aviso es necesario —es lo que un revisor de una app de televisión
busca, y es lo que le dice al abonado a quién reclamar— y **no es suficiente**.
Queda escrito aquí para que nadie confunda una cosa con la otra.

**Lo que sí sigue diciendo Jellyfin, y debe seguir diciéndolo:** todo lo que
habla del **servidor** del ISP —«este servidor usa Jellyfin %1$s», «abra
Jellyfin en un navegador para terminar la configuración», el cuerpo del informe
de perfil de dispositivo, que describe lo que ese servidor va a recibir—. §8
prohíbe usar el nombre **como marca nuestra**, no nombrar el software que el
abonado tiene enfrente. Cambiarlo haría los mensajes de error inservibles para
quien instala el site.

**Y esto se comprueba solo, porque la primera vez no bastó con escribirlo.**
Las cadenas se corrigieron en `values/`, que es el idioma por defecto, y las
**cuarenta traducciones del upstream seguían diciendo Jellyfin**. En una caja
mexicana manda `values-es-rMX`, así que la primera pantalla de toda
instalación decía «¡Bienvenido a Jellyfin!» — en el único mercado donde se
publica. Se vio el 29-oct poniendo el emulador en es-MX; en inglés la misma
pantalla decía «Te damos la bienvenida a Palomera», que es lo que había
tranquilizado a todo el mundo.

La comprobación `f)` de `herramientas/comprobar.py` lo cierra sin lista que
mantener: **si `values/` ya no dice Jellyfin para una clave, ningún idioma
puede decirlo**. Lo que nombra al servidor del ISP lo dice también `values/`,
así que queda fuera por construcción. Importa sobre todo después de un rebase,
que es cuando el upstream vuelve a traer sus traducciones.

**Y la `f)` tenía dos rendijas, que la `g)` cierra desde el 30-oct.** Su regla
—«si `values/` ya no lo dice, ningún idioma puede decirlo»— deja fuera por
construcción lo que dice `values/` mismo y lo que no es una cadena. Las dos se
usaron: el subtítulo de la pantalla de acceso caía a un `?: "Jellyfin"`
escrito a mano en Kotlin y el nombre de un servidor sin nombre se guardaba
como «Jellyfin Server», y las seis comprobaciones salían en verde las dos
veces. **Los dos defectos los encontró una captura de pantalla, no el
comprobador**, que es el argumento de `PLAY.md` §7 otra vez: publicar —o
mirar— es un instrumento de medida.

La `g)` barre los literales con `Jellyfin` de `app/`, `livetv/` y
`app/build.gradle.kts`, y las cadenas de `values/` **que alguien usa**, contra
una lista en la que cada excepción lleva escrito **de quién habla**. Las
cadenas huérfanas pasan a propósito —hay dos, y borrarlas agrandaría el diff
contra upstream sin que nadie las vea—, pero si un rebase las vuelve a
enganchar, salta.

### La app se presenta al servidor como «Palomera»

Corregido el 27-oct. Decía **«Jellyfin Android TV»**, que es el caso contrario
al párrafo de arriba: ahí el nombre no describe el software del ISP, somos
nosotros llamándonos Jellyfin. Y no es una cadena escondida: es lo que el
servidor guarda por dispositivo y enseña en su lista de sesiones, o sea **lo
primero que el panel de abonados le muestra al operador** al confirmar un alta
(`DISENO.md` §6.2). Un operador que ve «Jellyfin Android TV» no sabe si lo que
acaba de entrar es la caja que vendimos o el teléfono del abonado.

El valor es `BuildConfig.CLIENT_NAME`, y **es «Palomera», no la marca del ISP**.
Identifica el aparato, no al proveedor: las ranuras de marca de arriba visten la
app por dentro, pero quien pregunta «qué cliente es esta sesión» necesita saber
que es nuestra app. Por eso tampoco se traduce y no cambia en caliente, igual
que el `applicationId`.

Los tres sitios que lo usan están en §4.7.

### Y la dirección del código vive junto al nombre, por lo mismo

**Arreglado el 29-oct.** La línea vecina de esos informes decía
`client_repository: https://github.com/jellyfin/jellyfin-androidtv` —el
upstream, no nuestro fork—, así que un informe de fallo de esta app mandaba a
quien lo leyera al repositorio equivocado. Y el aviso de licencias de la
pantalla de Información decía que el código existe sin decir dónde, que es una
oferta sin dirección y no cumple la GPL-2.0 (`PLAY.md` §5).

Las dos cosas pedían el **mismo** dato, y eran **tres** sitios a mano: justo la
forma en que el nombre del cliente acabó mal durante un mes. Así que la
dirección vive donde vive el nombre, en
**`BuildConfig.CLIENT_REPOSITORY`** (`app/build.gradle.kts`), y los tres la
leen de ahí. `livetv_legal_licenses` pasa a llevar un `%1$s` que rellena
`about.kt`. Si el repositorio se mueve, se cambia una línea.

El repositorio público es **`https://github.com/Kataliziador/palomera`**, y
está bajo una organización y no bajo una cuenta personal por una razón
concreta: la oferta escrita de la GPL-2.0 tiene que seguir siendo válida
**tres años** por cada binario distribuido, y esa dirección queda impresa
dentro de cada APK instalado en una caja que nadie va a actualizar a mano. Una
organización sobrevive a que una persona se vaya.

### Datos de abonados: dos capas, y no se mezclan

Eventualmente se quiere saber quién se conecta. Para eso **no hace falta
acceso al site**: la app es nuestra y es la que se conecta. Acceso a su Jellyfin
solo haría falta para ver sesiones que no pasan por la app, o para cruzar con
su base de abonados.

Se separan desde el principio, porque mezcladas no se desenredan:

- **Diagnóstico técnico** — modelo de caja, canal, plan de reproducción,
  errores, ms al primer fotograma, rebuffers. Anónimo, necesario para operar.
- **Datos de abonado** — identidad, qué vio quién y cuándo. Es dato personal,
  cae bajo la LFPDPPP, y como el editor somos nosotros, la responsabilidad de
  declararlo en el aviso de privacidad y en el formulario de Seguridad de los
  datos de Play también es nuestra.

El primero va solo. El segundo va **detrás de un interruptor por proveedor**,
con contrato. Ver B6.

---

## 9. Entorno de pruebas

- **Jellyfin (VOD):** `http://192.168.20.249:8096/` — usuario `test`, clave `test`
- **Canales:** `http://192.168.11.250:8181/<stream>/mono.m3u8`
- **Caja de pruebas:** Skyworth YDA "4K Google TV Box", Amlogic Cortex-A35,
  Android 11 (API 30), en `192.168.20.132`

```bash
adb connect 192.168.20.132:5555
adb -s 192.168.20.132:5555 install -r app-<flavor>-debug.apk
adb -s 192.168.20.132:5555 logcat -v time | grep -iE "livetv|ExoPlayer|MediaCodec"
```

**Advertencia:** esta caja es el mejor caso posible (MPEG-2, MP2 y AC-3 por
hardware). No desarrolles solo contra ella. Usa el menú de degradación
descrito en `LIVETV_ENGINE.md` para simular cajas peores.

**Y es la única.** Hubo una Mecool KM9 Pro prestada entre el 12 y el 14 de
octubre —contestó tres preguntas que esta no podía y está en la bitácora—, pero
se devolvió. Así que la advertencia de arriba dejó de ser una precaución y es
la situación: **todo lo que se mida viene de un solo modelo, y eso se escribe
al afirmarlo.**

Los dos sustitutos, y lo que cada uno no cubre:

- **el menú de degradación** vale para lo que depende de las *capacidades* —qué
  decoders hay y qué declaran—, no para lo que depende del *comportamiento* del
  silicio. Que un decoder entregue mal no se simula: hay que verlo;
- **la ZTE B866V2 por Play**, que es la caja mayoritaria del cliente y solo
  instala desde la ficha pública. Es el único camino que queda a un segundo
  dato de hardware real, y por eso publicar es un instrumento de medida y no
  el final del trabajo (`PLAY.md`).

### Emulador de Android TV API 22, para lo que la caja no puede probar

```bash
/opt/android-sdk/emulator/emulator -avd api22tv -no-window -no-audio \
  -no-snapshot -no-boot-anim -gpu swiftshader_indirect -port 5558
```

Android 5.1.1, en `emulator-5558`. Existe porque **la caja de pruebas es
moderna y hay fallos que solo aparecen en las viejas** — el primero, que su
almacén de certificados no trae ninguna raíz de ISRG (pendiente 35).

Corre a velocidad casi nativa porque el contenedor tiene `/dev/kvm` expuesto
desde el Proxmox: dos líneas en `/etc/pve/lxc/103.conf` y `chmod 0666` en el
host, esto último obligatorio por ser un contenedor **no privilegiado**.

**Desactiva el teclado en pantalla antes de automatizar nada** (`adb -s
emulator-5558 shell ime disable <id>`): el de Leanback se traga ATRÁS y las
flechas, y `input text` funciona igual sin él.

---

## 10. Objetivos numéricos

| Métrica | Objetivo | Aceptable |
|---|---|---|
| Zapping p50 | < 2 s | < 3 s |
| Zapping p95 | < 3 s | < 4 s |
| Arranque al primer canal | < 4 s | < 6 s |
| Rebuffer ratio | < 0.3 % | < 1 % |
| Rebuffers por hora | < 1 | < 3 |
| Recuperación tras corte | < 8 s * | < 15 s |
| Crash-free sessions | > 99.5 % | > 99 % |
| Uptime continuo | > 48 h | > 24 h |

\* **El objetivo de < 8 s no es alcanzable hoy.** Medido: cuando la fuente
cae, Flussonic tarda ~15 s en reconstruir el playlist, y el cliente no puede
recuperarse antes que él. Queda condicionado a los pendientes 6 y 7 de
`LIVETV_LOG.md` (bajar `source_timeout` y poner el letrero de respaldo en el
headend). Mientras tanto, el objetivo real es el de la columna "Aceptable".
