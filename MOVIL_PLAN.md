# Palomera para teléfonos Android

Plan del port a teléfono. Hermano de `LIVETV_PLAN.md`, que es el de la caja, y
del plan de Palomera-TV, que es el de los televisores.

Escrito el 2026-11-08. Vive en la rama `movil` de este mismo repositorio.

---

## 0. Las tres decisiones que ya están tomadas

Decididas por Edgar el 08-nov, y ordenan todo lo demás:

1. **La app sirve dentro de casa**, por el Wi-Fi del abonado. No se promete ni
   se prueba el uso por datos móviles en la v1.
2. **Lleva canales y VOD desde la primera versión.** Se propuso empezar sólo
   con canales y se descartó.
3. **El código vive en este repositorio**, en un módulo `movil/` sobre la rama
   `movil`, trabajada desde un `git worktree` en `/home/proyectos/palomera-movil`.
4. **Una sola APK y una sola ficha**, que detecta el aparato y enseña la
   interfaz que toca — **añadida el 08-nov**, y reemplaza a la de dos
   aplicaciones con dos fichas que tenía este plan. Ver §3.

La primera es la que más ahorra, y conviene entender por qué: por el Wi-Fi del
site, un teléfono es **exactamente** la misma arquitectura que la caja —LAN,
registro, URL derivada, HLS—, así que todo lo medido en este repositorio sigue
valiendo y no hay que tocar ni un despliegue. Lo que quedó fuera de la v1 no es
un lujo: salir de casa obliga al ISP a publicar su site a internet, le pone el
tránsito de salida a su costa donde la LAN era gratis, y abre el problema de
derechos —una licencia satelital suele ser para el hogar—.

**La segunda es la que más cuesta, y es la que hay que vigilar.** Ver §5.

---

## 1. Qué se hereda y qué se escribe

Medido el 08-nov con `wc -l` y `grep`, no estimado:

```
livetv/src/main/kotlin                      9 226 líneas
  grep -rn "androidx.leanback|org.jellyfin.androidtv"  →  0 coincidencias
app/src/main/java/.../ui/livetv              6 721 líneas   (no viaja)
```

**Cero coincidencias.** El módulo del motor no importa ni una línea de Leanback
ni de la app. Y un teléfono es el **mismo Android, el mismo Media3, el mismo
ExoPlayer**: esto no es un port como el de webOS, donde Palomera-TV está
pagando por traducir 3 600 líneas a TypeScript. Aquí el motor compila y corre
tal cual.

Lo que se hereda gratis, por tanto:

| | |
|---|---|
| la cascada de decisión (`PlanBuilder`) | con sus capacidades y sus `deviceOverrides` |
| el vigilante, el margen de buffer, la vuelta al borde | los tres mecanismos que ganaron los números de rebuffer |
| el parser del `config.json` y del registro | y sus contratos, que viven aquí |
| el EPG (`XmltvParser`) | con su tope de 24 MB |
| el diagnóstico y el latido | que es el único instrumento que no necesita pantalla |
| las 220 pruebas del módulo | que siguen corriendo sin tocarlas |
| **el SDK de Jellyfin** | ya es dependencia: la capa de API del VOD viene hecha y tipada |

Lo que se escribe desde cero: **toda la interfaz**, y en el caso del VOD también
la navegación de biblioteca. Las 6 721 líneas de la sección de TV están escritas
para diez pies de distancia y una cruceta; no se adaptan, se reemplazan.

**Y la guía es rediseño, no reescalado.** Una rejilla de EPG pensada para 55
pulgadas a tres metros no funciona en seis pulgadas a treinta centímetros. Es
trabajo de diseño nuevo, y cae fuera de la congelación de interfaz de
`CLAUDE.md` §7 porque es una pantalla nueva, no un retoque de una existente.

---

## 2. Por qué no se forkea el cliente móvil de Jellyfin

Existe, se llama `jellyfin-android`, y **no sirve**. Es un envoltorio de WebView
alrededor de `jellyfin-web`, con ExoPlayer para el vídeo; lo dice su propio
README: «the client is only a web wrapper».

Eso significa que la pantalla que vería el abonado **es el cliente web de
Jellyfin**, servido por el Jellyfin del ISP: marca Jellyfin a la vista, que §8
prohíbe, cero control del aspecto, y nuestro motor hablando con la interfaz por
un puente de JavaScript. Es el mismo análisis que descartó el envoltorio
oficial de webOS, por la misma razón.

Los otros dos clientes de teléfono que existen tampoco son base:

- **Findroid**, el único nativo, es **GPL-3.0-only** → incompatible con este
  fork por el mismo argumento que tumbó nextlib (`CLAUDE.md` §6);
- **Streamyfin** es MPL-2.0 y React Native: es la app de otra persona, no un
  punto de partida.

**No hay nada que forkear. Esta vez la base somos nosotros** — y es mejor así,
porque la base que tenemos es el motor.

---

## 3. Arquitectura: **una sola aplicación que se adapta al aparato**

**Cambiado el 08-nov, por decisión de Edgar.** Este apartado decía dos
aplicaciones con dos `applicationId` y dos fichas. Ahora es **una**, que al
arrancar mira en qué aparato está y enseña la interfaz que toca.

```
jellyfin-livetv-v1/
├── livetv/   el motor. 9 226 líneas. **Compartido por ruta de Gradle**
├── movil/    la interfaz táctil. **Biblioteca, no aplicación**
└── app/      la única aplicación   → mx.kataliziador.palomera
             en el arranque: si no es TV, entra por `movil/`
```

### Por qué una sola, y es el mismo argumento de §8

`CLAUDE.md` §8 descartó los flavors por ISP con una frase que vale igual aquí:
«el costo de los flavors nunca fue programarlos — era **publicar**: N fichas, N
firmas, N revisiones de Google y N despliegues por cada corrección». Dos fichas
por formato es el mismo coste en pequeño.

**Y lo que de verdad lo hace barato ya estaba escrito, por upstream.** Medido
el 08-nov: `app/src/main/java/.../util/ContextExtensions.kt:27` define

```kotlin
fun Context.isTvDevice(): Boolean =
    supportedUiModes.contains(uiModeManager?.currentModeType) or
        packageManager.hasSystemFeature("android.hardware.hdmi.cec") or
        !packageManager.hasSystemFeature("android.hardware.touchscreen")
```

Tres señales, y **ya se usa**: `NotificationsRepository.kt:56` avisa al usuario
cuando no está en una televisión. O sea que la app ya sabe dónde está; lo único
que falta es que haga algo distinto con esa respuesta.

### Se detecta, **no se pregunta**

Se propuso una pantalla donde el abonado eligiera «móvil» o «TV». **Se
descarta**, y por tres razones:

- el sistema ya lo sabe (arriba). Preguntar algo que se puede leer es añadir
  una forma de equivocarse donde no había ninguna;
- **una respuesta equivocada deja la app inservible**: cruceta en un táctil, o
  dedo en una caja sin pantalla táctil. Y quien se equivoca es quien menos
  puede arreglarlo;
- es un paso más en la primera pantalla, que es donde más gente se cae.

**Hay interruptor manual, en Ajustes**, para el aparato que mienta. Misma
filosofía que `deviceOverrides`: decide la caja, y nosotros tenemos palanca.
Pero **no puede quedar al alcance de un descuido** — ver R3.

### Por qué `movil/` es biblioteca y no aplicación

Meter una segunda interfaz **dentro** de `app/` haría crecer el diff contra
upstream en miles de líneas, que es justo el dolor que §4 existe para evitar.
Como biblioteca, `app/` sólo gana **unas diez líneas de enrutado** en el
arranque, y la interfaz entera vive en su módulo — el mismo truco que hizo
viable `livetv/`.

**Y nace como aplicación a propósito, hasta la fase 1.** Hoy `:movil` es un
módulo de aplicación porque es el instrumento con el que se mide R1 en un
teléfono: tiene que instalarse solo. Se convierte en biblioteca cuando `app/`
pueda alojarlo, no antes. Convertirlo ya dejaría la fase 0.2 sin aparato donde
correr.

`movil/build.gradle.kts` declara `implementation(projects.livetv)` y con eso
acabó. Ni submódulo, ni AAR, ni copia.

Por qué este reparto y no un repositorio aparte:

- **el motor no se copia, se comparte.** Dos copias divergen; es la misma razón
  por la que `destino()` tiene una sola definición para los dos mecanismos que
  la usan;
- **los contratos ya viven aquí** (`CLAUDE.md` §1) y su regla de sincronía
  también. Un cuarto cliente que los copiara con su procedencia sería friction
  sin ganancia;
- **el rebase no empeora.** `movil/` es código nuevo en un directorio nuevo y no
  toca `app/`. Entra en la lista de territorio permitido de §4.7 exactamente
  como entró `livetv/`;
- `herramientas/comprobar.py` crece para cubrirlo, en vez de nacer un segundo
  comprobador que diga cosas distintas.

### El precio: un solo `minSdk`, y es 21

La caja de un ISP necesita `minSdk 21`, y el esqueleto del teléfono se escribió
con 24. **Una sola APK obliga a un solo número, y manda el más bajo.** Así que
el teléfono baja a 21: Compose y la interfaz táctil tienen que funcionar en
Android 5.

No es gratis y no se descubre solo. Queda escrito aquí para que quien escriba
la fase 1 lo sepa antes, y no después de elegir una biblioteca que pide 24.

### La regla que no se negocia

**El trabajo del teléfono no cambia el comportamiento de `:livetv`.** Lo que
necesite entra aditivo, o detrás de un interruptor de Modo A con el valor de
fábrica igual a lo anterior.

No es purismo: `rc.2` está compilado, etiquetado y esperando a publicarse, y su
comportamiento lo validó Edgar en pantalla. Un cambio «de paso» en el motor
para que el teléfono vaya mejor cambia también lo que hace esa caja.

### Qué NO se extrae de `app/` todavía

Hay lógica en `app/` que el teléfono necesita: la puerta de la sesión del
abonado —la tabla que distingue 401, 403 y «no contesta», con sus pruebas en
`app/src/test/kotlin/auth/`— y el `DeviceProfile` derivado de `MediaCodec`.

La tentación es extraerla a un módulo compartido. **En la v1 no se hace**:
extraer obliga a cambiar `app/`, que es el camino de entrada compartido con el
VOD de la caja que está esperando a publicarse. El teléfono reimplementa esas
~200 líneas, y **la fuente de verdad compartida es la tabla documentada y su
juego de pruebas**, no el código. Si la duplicación duele, se extrae después,
con `app/` ya publicado.

---

## 4. Lo que es nuevo de verdad, y no es la interfaz

La interfaz táctil es trabajo, no incógnita. Las incógnitas son éstas, y tres
de las cinco están **sin medir**.

### 4.1 El H.264 entrelazado en un teléfono — **la peor, y nadie la ha mirado**

**Cuatro de los seis canales de referencia vienen entrelazados, los tres HD
incluidos**, y no todos con el mismo orden de campos (`CHANNELS.md`). En la caja
eso se resolvió mirando la pantalla durante semanas: fue el trabajo más caro del
proyecto.

En un teléfono, el panel es progresivo y no hay pipeline de broadcast que
desentrelace por construcción como en un televisor. Lo que hace MediaCodec con
un 1080i H.264 en un SoC de teléfono **no lo sabemos**, y las dos salidas
posibles son muy distintas: o el decoder lo entrega desentrelazado, o sale con
peine y hace falta libVLC con `yadif` en los tres canales HD.

**Esto no se simula y no se infiere: hay que verlo en un teléfono.** Es la fase
0.2 y es la que decide el calendario.

### 4.2 MPEG-2 — **medido el 08-nov: no está**

Se decía aquí que «probablemente» no estaría, inferido de la documentación de
los fabricantes. Ya está medido, y es un no rotundo.

**Aparato:** Xiaomi `2511FPC34G` (`klee_global`), MediaTek Dimensity `mt6899`,
Android 16 / API 36, `arm64-v8a` como único ABI, 11,3 GB de RAM, 1268×2756 a
520 dpi. **Instrumento:** `MediaCodecList` a través de
`DeviceCapabilities.of()`, que es exactamente lo que consulta la cascada — no
un `grep` a los XML del fabricante, que fue la primera lectura y podría haber
sido ciega.

| mime | resultado |
|---|---|
| `video/avc` | **hardware** · `c2.mtk.avc.decoder` |
| `video/mpeg2` | **no hay** |
| `video/hevc` | hardware · `c2.mtk.hevc.decoder` |
| `audio/mp4a-latm` | software · `c2.android.aac.decoder` |
| `audio/mpeg` | software · `c2.android.mp3.decoder` |
| `audio/ac3` | software |

Ni de hardware ni de software: MPEG-2 no existe en este teléfono. Para
`estrellassd-mp2v` —el único MPEG-2 real de los 53— hace falta libVLC o no se
ve. **Una muestra, y del mejor caso posible**: es un teléfono nuevo de gama
alta, así que vale la advertencia de `CLAUDE.md` §9 palabra por palabra.

**Y hay una trampa de §2 reaparecida.** Este teléfono decodifica AAC, MP3 y
AC-3 por software, así que **un canal mal normalizado se oye perfecto aquí** —
exactamente el modo de fallo silencioso contra el que avisa §2, sólo que ahora
el aparato engañoso es el de pruebas del desarrollador.

### 4.2.bis El borde a borde es obligatorio, y ya mordió

Con `targetSdk 36` Android dibuja de borde a borde y **deja de reservar sitio**
para la barra de estado y la de navegación. Medido el 08-nov: la primera
captura del esqueleto tenía el título pintado **encima del reloj del sistema**.

Se arregla con `enableEdgeToEdge()` en la actividad y
`windowInsetsPadding(WindowInsets.safeDrawing)` en la raíz de cada pantalla.
Está escrito aquí porque **afecta a todas las pantallas de la fase 1**, no sólo
a la que lo enseñó, y porque en la caja este problema no existe.

### 4.3 libVLC pesa 170 MB de los 217 del APK

Medido el 08-nov sobre el `movil-debug.apk` que produce este esqueleto:

```
lib/x86_64/libvlc.so      46,9 MB
lib/x86/libvlc.so         45,1 MB
lib/arm64-v8a/libvlc.so   41,1 MB
lib/armeabi-v7a/libvlc.so 37,3 MB
                        ─────────
nativo total             189,4 MB   (de 217,2 MB de APK)
```

**Y el número que de verdad importa es otro: 67,6 MB.** Medido el 08-nov
compilando con `-Pmovil.abi=arm64-v8a`: es lo que pesa el APK para **un**
teléfono, que es lo que un AAB reparte. El Xiaomi de pruebas declara
`arm64-v8a` y nada más. Los 217 MB son los cuatro ABIs juntos, que no le
llegan a ningún aparato.

**Y aun ese número engaña, medido el 08-nov en la propia Play Console.** El AAB
de la app de TV —que también lleva libVLC, en los cuatro ABIs— declara un
**«tamaño de nuevas descargas» de 38,9 MB** frente a sus 99,6 MiB de bundle:
Play reparte por ABI, densidad e idioma y entrega el 38 %. 38,9 MB es un tamaño
de app corriente.

Así que la frase que había aquí —«sigue siendo mucho para un teléfono»— **se
apoyaba en el peso de un APK gordo, que no es lo que nadie descarga**. Lo que
pesa la descarga del teléfono sigue **sin medir**, porque todavía no hay AAB de
`:movil`; lo medido es su hermana, y dice que el argumento para quitar libVLC
por tamaño es mucho más débil de lo que parecía.

**Lo que no cambia** es que libVLC sólo compra un canal de 53 mientras R1 no se
mida, y que compila, descarga y se instala más lento en cada iteración de
desarrollo — que es para lo que sirve `-Pmovil.abi`.

En una caja de ISP el tamaño no le importa a nadie. En un teléfono mexicano sí,
y un AAB reparte por ABI pero sigue dejando ~50 MB de nativo en un arm64.

Y el precio compra poco: libVLC existe en el motor para (a) MPEG-2 sin decoder
de hardware y (b) desentrelazado por software de SD — o sea **un canal de 53**,
salvo que §4.1 salga mal, en cuyo caso compra los tres HD y pasa a ser
imprescindible.

**Decisión de la fase 0, y depende de §4.1:** si el entrelazado sale bien,
`:livetv` crece una variante sin libVLC para que el teléfono no lo cargue. Es
cambio de compilación, no de comportamiento, así que no choca con la regla de
§3.

### 4.4 Salir del Wi-Fi

La v1 sirve dentro de casa, y eso **no** significa que salir de casa pueda
quedar sin definir: es el caso más frecuente de todos. Un abonado que cruza la
puerta tiene que ver «no estás en tu red», no una rueda girando ni el error
equivocado, y tiene que recuperarse solo al volver.

Es la regla de `CLAUDE.md` §6 aplicada a un caso nuevo: el error que distingue
«el servidor no contesta» de «tú no estás donde él está» ahorra la llamada a
soporte.

### 4.5 Lo que un teléfono tiene y una caja no

Batería y calor, llamadas entrantes, foco de audio, auriculares que se
desconectan, rotación, PiP, segundo plano, pantallas de 5 a 7 pulgadas y
tablets. Nada de esto se ha pensado nunca en este repositorio. Es trabajo
conocido, pero es una fase entera (fase 4).

---

## 5. El VOD, que es la mitad difícil

Edgar decidió llevarlo en la v1. Queda escrito aquí qué significa eso, porque es
donde se va a ir el calendario.

**En la TV el VOD es gratis**: la app hereda toda la interfaz de películas y
series de jellyfin-androidtv, y el invariante de §4 existe justamente para no
romperla. **En un teléfono no hay nada que heredar**: esa interfaz es Leanback.

Lo que hay que escribir:

| | |
|---|---|
| navegación de biblioteca | bibliotecas, películas, series → temporadas → episodios, búsqueda, «seguir viendo», «a continuación» |
| reproducción | Media3 con el `DeviceProfile` que decide direct-play contra transcodificación |
| informe a Jellyfin | posiciones de reanudación y progreso (`/Sessions/Playing`) |
| pistas y subtítulos | selección de audio y de subtítulos, que aquí sí son de pista aparte |
| carátulas | con su caché |

Lo que **sí** viene hecho es la capa de API: el SDK de Jellyfin es Kotlin,
tipado y ya es dependencia del proyecto.

**Y el riesgo que hay que mirar es el perfil de dispositivo.** Si exagera lo que
el teléfono puede, el abonado ve una película en negro; si se queda corto, el
servidor del ISP transcodifica para veinte casas. En webOS ese riesgo era grave
porque no hay `MediaCodecList`; **aquí sí la hay**, y `deviceProfile.kt` ya la
usa en este repositorio. Es el riesgo más barato de los tres ports.

**Fuera de alcance en la v1 del teléfono**, y se dice ahora: descargas para ver
sin red, Chromecast, DVR y grabación (que ya están fuera de alcance en §7).

---

## 6. Fases

Cada fase cierra con algo que se ve, no con código escrito.

### Fase 0 — El esqueleto y las dos medidas que deciden el proyecto

**0.1 — hecho el 08-nov, y corriendo en un teléfono de verdad.** El worktree,
el módulo `:movil`, el cableado de Gradle y la pantalla de capacidades,
instalada y ejecutándose en el Xiaomi de §4.2. Cierra §4.2 y deja dos lecciones
que no se buscaban: el borde a borde (§4.2.bis) y los 67,6 MB por ABI (§4.3).

**0.2 — lo siguiente, y es lo único que importa ahora.** Un canal real
reproduciéndose en un teléfono real, con el overlay de diagnóstico a la vista.
**Cierre: la respuesta a §4.1 —¿sale con peine un 1080i?— y la tabla de decoders
de un teléfono de verdad, en una foto.** Con eso se decide libVLC (§4.3) y se
sabe si el calendario es el que parece.

Hasta aquí no hay interfaz que diseñar. Es deliberado: es lo mismo que la sonda
de la fase 0 de Palomera-TV.

### Fase 1 — La puerta y la marca
Arranque, selector de proveedor, código de activación, acceso por contraseña y
por QuickConnect, la tabla de decisiones de sesión (401 / 403 / no contesta), las
tres ranuras de marca y la marca neutra. **Cierre: entrar a un site real desde
un teléfono, con la marca del ISP puesta, y la pantalla de suspensión correcta
cuando el contrato está suspendido.**

### Fase 2 — Los canales en táctil
Lista y grupos, favoritos, la guía rediseñada para seis pulgadas, la barra de
canal sobre el vídeo, selección de audio y de subtítulos, pantalla completa y
rotación. **Cierre: 20 zapeos con p50 y p95 contra los objetivos de `CLAUDE.md`
§10, medidos en el teléfono y no en el emulador.**

### Fase 3 — El VOD
Lo de §5. **Cierre: una matriz de direct-play con 20 elementos reales de la
biblioteca de un ISP —cuántos reproducen directo, cuántos exigen transcodificar,
cuántos fallan— y una película vista entera con reanudación correcta.**

### Fase 4 — Teléfono de verdad
Lo de §4.5, más §4.4. **Cierre: un remojo de 12 h con la app en segundo plano y
el teléfono sin cargador, con batería y temperatura apuntadas; y la secuencia
«salir de casa, volver» sin que haga falta reiniciar la app.**

### Fase 5 — La ficha
**Mucho más corta desde la decisión de §3:** no hay ficha nueva que crear. Se
habilita el formato de teléfono y tablet en la que ya existe, se suben sus
capturas y se revisa si el cuestionario de Seguridad de los datos cambia —no
debería: el teléfono manda lo mismo que la caja—. **Cierre: la ficha
distribuyendo a los dos formatos, y un teléfono y una caja bajándose la misma
versión desde Play y enseñando cada uno su interfaz.**

---

## 7. Riesgos

**R1 · El entrelazado en un SoC de teléfono.** §4.1. Es el único que puede
cambiar el proyecto de «tres meses de interfaz» a «el problema más caro de la
caja, otra vez». *Detección:* fase 0.2, mirando una pantalla. *Salida:* libVLC
con `yadif`, que ya está en el motor — al precio de §4.3 y de batería.

**R2 · El perfil de dispositivo del VOD.** §5. Falla en los dos sentidos y el
caro es el silencioso: el servidor del ISP transcodificando para veinte casas
sin que nadie se entere hasta que su CPU llega al techo. *Detección:* la matriz
de la fase 3, con biblioteca real. *Mitigado* por tener `MediaCodecList`.

**R3 · Una sola APK acopla las dos cadencias, y ésa es la factura.** Con una
ficha no hay segundo `applicationId` que fijar para siempre ni segunda clave
—eso desaparece de §8—, pero **cada iteración del teléfono vuelve a pasar por
revisión y vuelve a enviarse a las cajas de los abonados**. La app de la caja
está pensada para quedarse quieta en producción; la del teléfono va a cambiar
cada semana.

*Se gestiona* con la regla de arriba —lo del teléfono no toca `livetv/` ni el
camino de la TV— y con el despliegue escalonado de `PLAY.md` §7. *No se elimina*:
el binario cambia aunque el código de la TV no, así que cada versión del
teléfono es una versión nueva para un parque de cajas que nadie va a mirar.

**Y un riesgo de revisión que las dos fichas no tenían:** el formato Android TV
de Play exige que **todo** sea navegable con cruceta. Si un revisor con un mando
aterriza alguna vez en la interfaz táctil —detección fallida, o el interruptor
manual pulsado sin querer— es rechazo. De ahí que el interruptor de §3 no pueda
estar al alcance de un descuido.

**R4 · El tamaño del APK.** §4.3. 170 MB de libVLC para un canal de 53.

**R5 · Batería y calor.** 1080i H.264 por hardware ya calienta; con
desentrelazado por software encima, un teléfono se acalora y baja de frecuencia.
Si R1 sale mal, R5 deja de ser molestia y pasa a ser producto.

**R6 · El VOD se come el calendario.** Es la mitad difícil (§5) y va en la v1
por decisión tomada. El aviso queda escrito: si la fase 3 se alarga, lo que se
puede entregar antes es canales, porque `Acceso.kt` ya separa las dos puertas.

**R7 · Dos sesiones en el mismo repositorio.** El worktree tiene índice y HEAD
propios, así que no se pisan los árboles de trabajo; comparten el almacén de
objetos. En la práctica: **no hacer `git gc` ni `push --force` sobre una rama que
la otra sesión tenga sacada.**

**R8 · La licencia, que sigue sin mirar un abogado.** `movil/` no contiene ni una
línea de jellyfin-androidtv: depende de `:livetv`, que es código nuestro escrito
desde cero. Eso hace su posición **más limpia** que la de la app de TV, no más
turbia. Pero sigue viajando libVLC (LGPL-2.1, con su aviso obligatorio en la
pantalla de Información) y sigue siendo el mismo repositorio GPL-2.0. R10 del
plan de webOS vale aquí palabra por palabra: la línea entre «código propio
aislado» y «obra derivada» la decide un abogado, y el aislamiento de §4 es el
argumento que la sostiene.

---

## 8. Lo irreversible, y lo que es de Edgar

**Con una sola ficha, esta lista casi desaparece — y ése es el mejor argumento
a favor de la decisión de §3.** Ya no hay un segundo `applicationId` que Play
fije para siempre, ni un segundo título, ni una segunda clave de firma que
guardar y no perder. El teléfono hereda las tres de la caja, que ya están
decididas y ya están en Play.

Lo que queda es **una sola cosa, y es de configuración, no de código**:

- **habilitar el formato de teléfono y tablet en la ficha existente**, con sus
  capturas propias. Play las exige por formato. Es reversible y se hace cuando
  la interfaz táctil esté lista — **no antes**, porque habilitarlo hoy pondría
  la interfaz de cruceta en los celulares de los abonados.

**Y una comprobación que hay que hacer en la Play Console antes de publicar
cualquiera de las dos.** Medido el 08-nov en `app/src/main/AndroidManifest.xml`:
la app de TV **ya declara `LAUNCHER` además de `LEANBACK_LAUNCHER`**, y
`touchscreen` como no obligatoria. Viene del upstream. Es lo que la hace
elegible para teléfonos, donde su interfaz de cruceta es inservible. Hay que
confirmar que su ficha no se distribuye a ese formato.

El manifiesto de `movil/` hace el espejo: exige pantalla táctil y **no** declara
`LEANBACK_LAUNCHER`, que es lo que la mantiene fuera de los televisores.

**Y dejó de ser teórico el 08-nov.** `pm list packages` en el teléfono de
pruebas devolvió **`mx.kataliziador.palomera`** —la app de la caja— instalada
junto a la de teléfono. O sea que no es que *pueda* instalarse en un teléfono:
ya está en uno.

---

## 9. Verificación

| capa | instrumento | cobertura honesta |
|---|---|---|
| `:livetv` | las 220 pruebas que ya existen | total, y **no cambian**: es el mismo módulo |
| lógica nueva de `movil/` | kotest en el módulo | total |
| la interfaz | ninguna prueba automática | **nula** |
| **entrelazado, peine, calor** | **un teléfono, mirándolo** | **cero sin aparato** |
| zapeo, rebuffers, offset | el latido y `herramientas/analizar-log.py` | alta, y sin pantalla |
| direct-play del VOD | la biblioteca real de un ISP | nula contra una sintética |

La fila del entrelazado es la que hay que decir en voz alta, y es la misma frase
que cierra el plan de webOS: **lo que hace difícil este proyecto es inobservable
sin hardware.** Por eso la fase 0 termina en una foto y no en código.

---

## 10. Lo que hace falta antes de la fase 0.2

| | |
|---|---|
| **un teléfono Android de verdad**, con `adb` | no el emulador: lo que se juzga es lo que hace su silicio |
| preferiblemente **dos**: uno moderno y uno de gama baja de hace 4-5 años | el segundo es el parque real de un abonado de ISP, y es donde falla |
| estar en la red de un site con canales | lo mismo que ya usa la caja |
| confirmar §8 (paquete, título, firma) | antes del primer envío, no antes de la fase 0 |

**Lo que no hace falta todavía:** decidir iOS. Un teléfono Android reusa el
motor que ya existe; iOS no reusa nada de esto y su camino natural sería el
`core/` en TypeScript que Palomera-TV está escribiendo. Son dos decisiones
distintas y atarlas retrasaría la barata.
