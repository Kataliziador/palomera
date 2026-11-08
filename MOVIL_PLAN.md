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

## 3. Arquitectura: un módulo más, no un proyecto más

```
jellyfin-livetv-v1/
├── livetv/   el motor. 9 226 líneas. **Compartido por ruta de Gradle**
├── app/      la de TV (el fork, Leanback)     → mx.kataliziador.palomera
└── movil/    NUEVA. Compose táctil, nuestra   → mx.kataliziador.palomera.movil
```

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

### 4.2 MPEG-2, que probablemente no está

Muchos SoC de Qualcomm y MediaTek no traen decoder de MPEG-2. Mitigado por el
lineup real: de los 53 flujos, el único MPEG-2 de verdad es `estrellassd-mp2v`;
el resto es H.264 + AAC. **Inferido de la documentación de los fabricantes, no
medido en ningún teléfono** — y la pantalla del esqueleto lo contesta en una
tabla, sin `adb`.

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

**0.1 — hecho el 08-nov.** El worktree, el módulo `:movil`, el cableado de
Gradle y una pantalla que enseña lo que el teléfono declara. `assembleDebug` y
la prueba de humo en verde (medido: 1 prueba, 0 fallos).

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
Segunda ficha, segundo paquete, capturas de teléfono, formulario de Seguridad de
los datos. **Cierre: la lista de `PLAY.md` en verde para este paquete, y la
comprobación de que la ficha de TV no se distribuye a teléfonos** (ver §8).

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

**R3 · Dos fichas, dos paquetes, dos claves.** El `applicationId` es
irreversible al primer envío, igual que el de la caja. Y ahora son dos
identidades que mantener, dos revisiones de Google y dos despliegues por cada
corrección — exactamente el coste que §8 evitó al descartar los flavors por ISP.
Se acepta porque la alternativa es peor: una sola ficha obligaría a meter la
interfaz táctil dentro de `app/`, y con ella el lastre de Leanback y el dolor de
rebase que §4 existe para evitar.

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

**Irreversible, y hay que confirmarlo antes del primer envío:**

- **`mx.kataliziador.palomera.movil`**, provisional en
  `movil/build.gradle.kts`. Play lo fija para siempre. Mientras sólo se compile
  en depuración no compromete nada, porque el sufijo `.debug` hace que el id
  instalado no sea ése;
- **el título de la segunda ficha.** Las dos se llamarían «Palomera» y Play
  filtra por aparato, pero conviene decidirlo, no heredarlo;
- **la clave de firma.** No se reutiliza la de la caja: son dos fichas y
  mezclarlas ata el destino de una a la otra.

**Y una comprobación que hay que hacer en la Play Console antes de publicar
cualquiera de las dos.** Medido el 08-nov en `app/src/main/AndroidManifest.xml`:
la app de TV **ya declara `LAUNCHER` además de `LEANBACK_LAUNCHER`**, y
`touchscreen` como no obligatoria. Viene del upstream. Es lo que la hace
elegible para teléfonos, donde su interfaz de cruceta es inservible. Hay que
confirmar que su ficha no se distribuye a ese formato.

El manifiesto de `movil/` hace el espejo: exige pantalla táctil y **no** declara
`LEANBACK_LAUNCHER`, que es lo que la mantiene fuera de los televisores.

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
