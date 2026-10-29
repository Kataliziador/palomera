# PLAY.md

**Lo que hace falta para publicar Palomera en Google Play, y quién lo hace.**

Este documento existe porque el primer envío tiene una propiedad incómoda: la
caja mayoritaria del cliente —la ZTE B866V2— **solo admite instalaciones desde
la ficha pública**, así que publicar no es el final del trabajo sino el
instrumento para empezar a medirla (`LIVETV_LOG.md`, entrada de B6).

---

## 1. Lo irreversible, antes de nada

Dos cosas no se pueden deshacer, y las dos se fijan en el primer envío.

**El nombre de paquete: `mx.kataliziador.palomera`.** Play lo fija para siempre.
Cambiarlo después es publicar otra app y perder instalaciones, reseñas y
posición. Ya está puesto (`app/build.gradle.kts`), y ya no es el del upstream
—con aquél el envío se rechazaba, porque `org.jellyfin.androidtv` está
publicado por el proyecto Jellyfin.

**La clave de firma.** Perderla es perder la ficha: no se puede volver a
publicar una actualización nunca más. Dos decisiones:

- **Dónde se guarda**, con copia en un segundo sitio que no sea la misma
  máquina ni la misma cuenta.
- **Si se usa Play App Signing**, que es lo recomendable: Google guarda la
  clave de firma y tú conservas solo la de subida, que sí se puede sustituir
  si se pierde. Sin esto, una clave perdida es una ficha muerta.

El repositorio **no lleva ninguna clave** y no debe llevarla. La compilación
las lee de cuatro propiedades, y **estos son los nombres exactos que el código
busca** (`app/build.gradle.kts:82`, verificado el 14-oct; este documento daba
dos de los cuatro mal —`keystore.alias` y `keystore.alias.password`— y con esos
nombres la firma no se aplica):

| propiedad | variable de entorno equivalente |
|---|---|
| `keystore.file` | `KEYSTORE_FILE` |
| `keystore.password` | `KEYSTORE_PASSWORD` |
| `signing.key.alias` | `SIGNING_KEY_ALIAS` |
| `signing.key.password` | `SIGNING_KEY_PASSWORD` |

Van en `local.properties`, que está en `.gitignore`, o en el entorno
(`Properties.kt` convierte el punto en subrayado y pasa a mayúsculas).

**Y si falta alguna, la compilación de release ahora se detiene** con los cuatro
nombres en el mensaje. Antes no: `signingConfigs` solo crea la configuración
cuando están las cuatro, `findByName("release")` devolvía `null` sin decir nada
y salía un AAB de 100 MB sin firmar que parecía bueno y moría en la consola de
Play media hora después.

**La clave se genera con 25 años de validez como mínimo**, que es lo que
recomienda la documentación oficial de Android: con la misma clave se firman
todas las actualizaciones durante la vida de la app, y una clave caducada no se
puede sustituir si no hay Play App Signing. El comando lo ejecuta Edgar, para
que la contraseña no pase por aquí:

```bash
keytool -genkeypair -v -keystore palomera-subida.jks -storetype PKCS12 \
  -alias palomera -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=<razón social>, O=<razón social>, C=MX"
```

**Lo que hay hoy no sirve para publicar, y lo dice en su propio certificado.**
Las compilaciones de verificación se firman con un keystore desechable
—`CN=solo-para-validar-local, O=NO-PUBLICAR`, un año de validez— que vive en el
directorio temporal de la sesión y desaparece con ella. Está así a propósito:
sirve para comprobar el empaquetado y es imposible confundirlo con la de
producción.

---

## 2. Lo que ya está hecho en el código

No hay que volver a tocarlo. Está aquí para no repetir trabajo ni dudar.

| | Estado |
|---|---|
| `applicationId` propio | `mx.kataliziador.palomera` |
| Nombre e icono propios | «Palomera», logotipo, icono y banner en vector, sin rastro de Jellyfin (§8) |
| PNG de icono, banner y logo de marca | generados desde esos vectores (`herramientas/marca/`) |
| `LEANBACK_LAUNCHER` | declarado, junto al `LAUNCHER` normal |
| `leanback`, `touchscreen`, `faketouch`, `microphone` | los cuatro `required="false"` |
| `targetSdk` | 36 |
| `minSdk` | 21 |
| 64 bits | el AAB trae las cuatro ABIs, `arm64-v8a` entre ellas |
| `debuggable` en release | ausente |
| Avisos legales | «no afiliado al proyecto Jellyfin» y licencia GPL-2.0, en Información |

**Sobre las ABIs:** no hay NDK ni compilación nativa **propia** —se fue con la
extensión FFmpeg (§6)—, así que el bundle reparte las cuatro arquitecturas solo
y el requisito de 64 bits se cumple por construcción. No hay nada que
configurar.

**Pero sí viaja código nativo ajeno, y pesa.** Este documento decía hasta el
14-oct que «la única nativa es `libandroidx.graphics.path`», y eso dejó de ser
cierto el 09-oct, cuando **libVLC** entró con M6. Medido sobre el AAB del
14-oct, contando el tamaño comprimido de cada entrada del bundle:

| | comprimido |
|---|---|
| AAB entero (las cuatro ABIs) | 104,2 MB |
| común, sin `lib/` | 17,2 MB |
| `lib/armeabi-v7a/` | 19,8 MB — de los cuales **19,5 son `libvlc.so`** |
| `lib/arm64-v8a/` | 22,6 MB |
| `lib/x86` / `lib/x86_64` | 20,7 / 23,9 MB |

**Lo que se descarga una caja es común + su ABI**, porque Play entrega un solo
split de arquitectura: **37,0 MB** en la Skyworth y en cualquier `armeabi-v7a`,
**39,8 MB** en una de 64 bits. El AAB de 104 MB no es lo que nadie baja.

Conviene tenerlo escrito porque el número asusta al verlo en la consola y
porque, si algún día se discute quitar libVLC, **ese es el ahorro real: 19,5 MB
de los 37** — y a cambio se perdería el MPEG-2 sin decoder de plataforma y el
desentrelazado por software, que es justo lo que hace funcionar `estrellassd`
en esta caja.

---

## 3. La versión, antes del primer envío

Hoy el proyecto compila como `0.0.0-dev.1`, `versionCode 1`. **Eso no se
publica.** Hay que fijarlo antes del primer AAB, porque el `versionCode` solo
puede subir: un número alto por accidente quema todos los menores para siempre.

**Y el peligro era el contrario del que parece.** Un AAB sin versión no falla
al construirse ni al subirse: Play acepta el `versionCode 1` tan contento, y lo
que se rompe es el envío *siguiente*, cuando ya no hay forma de volver a los
números por debajo de 100 ni de usar la convención `MAMIPAPR`. Desde el 16-oct
**el build se niega**: cualquier tarea con «Release» en el nombre y
`versionCode < 100` para la compilación con el mensaje que dice cómo fijarla
(`app/build.gradle.kts`, al lado de la guarda de la clave de firma, que lleva
ahí desde el 14-oct por el mismo motivo). Comprobado las dos ramas: sin la
variable el `bundleRelease` se detiene; con `-Pjellyfin.version=0.1.0-rc.1`
pasa de largo y llega a la guarda de firma.

**El esquema no hay que inventarlo: ya está escrito y comprobado.**
`buildSrc/.../VersionUtils.kt` deriva `versionCode = MAMIPAPR` de un semver, y
el semver sale de la propiedad `jellyfin.version` o de la variable de entorno
`JELLYFIN_VERSION` (en ese orden; `Properties.kt` mapea una a otra). Medido el
14-oct compilando de verdad y leyendo el manifiesto con `aapt2`:

```
JELLYFIN_VERSION=0.1.0-rc.1  ->  versionCode='10001' versionName='0.1.0-rc.1'
```

| candidato | `versionCode` |
|---|---|
| `0.1.0-rc.1` | 10001 |
| `0.1.0` | 10099 |
| `1.0.0-rc.1` | 1000001 |
| `1.0.0` | 1000099 |

**Lo único que decide Edgar es el número.** La recomendación es `0.1.0-rc.1`:
deja sitio por debajo para candidatas y por encima para todo lo demás.

**Y la trampa que parecía haber no existe.** `app/build.gradle.kts:142` define
`DEVELOPMENT = versionCode < 100`, así que al fijar una versión real se apaga
**también en depuración**, y durante un tiempo se temió perder con él el menú
de Diagnóstico en la caja de Edgar. Comprobado leyendo los tres usos que
quedan:

- el menú de Diagnóstico ya no depende de él, sino de `BuildConfig.DEBUG`
  (`LiveTvFragment.kt:334`, con el comentario que lo explica);
- `TelemetryService.kt:142` deja de añadir el volcado JSON crudo a los informes
  de fallo, que además van al Jellyfin del ISP y no a nosotros;
- `DeveloperPreferencesScreen.kt:43` esconde la casilla que activa el **módulo
  de reproducción nuevo**, que es justo lo que `CLAUDE.md` §4.4 prohíbe usar.

O sea que fijar la versión no cuesta nada y el tercer efecto es deseable.
Verificado en el `BuildConfig` generado: con `0.1.0-rc.1` sale `DEBUG = true`
y `DEVELOPMENT = false`, que es exactamente lo que se quiere.

---

## 4. Formulario de Seguridad de los datos

**Esto no es trámite: es una declaración y es nuestra responsabilidad**, porque
el editor somos nosotros (`CLAUDE.md` §8). Lo que la app manda hoy, y a dónde:

| Qué | A dónde | Para qué |
|---|---|---|
| `installId` — identificador opaco **por instalación** | registro propio | contar cajas |
| Informe de fallo si la app se cierra sola | **el Jellyfin del ISP**, no nosotros | diagnóstico |
| Modelo, fabricante, ABIs, RAM, versión de Android | registro propio | soporte |
| Inventario de decoders de vídeo | registro propio | elegir el motor por modelo |
| Diagnóstico de reproducción acumulado | registro propio | operar: qué falla y dónde |
| Canales cuyo audio no llegó normalizado, con su códec | registro propio | avisar al ISP de que ese canal se va a quedar mudo en algunas cajas (§2 del `CLAUDE.md`) |
| Host del servidor que escribió el abonado | registro propio | saber de qué ISP es |

Cómo se declara, y por qué:

- **Se recoge, no se comparte.** Va a un servicio nuestro y a nadie más —con
  una excepción que hay que declarar y que no habíamos mirado: **los informes
  de fallo del upstream van al servidor Jellyfin del propio ISP**
  (`clientLogApi.logFileUrl()`), activados de fábrica y desactivables en
  Ajustes. No llegan a nosotros, pero salen de la caja.
- Es **«Identificadores del dispositivo u otros»** por el `installId`, con
  finalidad de **funcionalidad de la app** y **análisis**.
- **No es opcional**: va siempre, así que se declara como obligatorio.
- **No hay datos personales ni actividad del abonado.** Está construido para
  que no los haya: se cuentan los canales que **fallaron**, sin marcas de
  tiempo, sin orden y sin duraciones individuales. De ahí no se reconstruye un
  historial de visionado. Esa frontera está en el diseño, no en la intención, y
  conviene no cruzarla sin volver a mirar este formulario.

### Los permisos que el formulario va a preguntar, y la tabla de arriba no cubre

La tabla dice lo que la app **manda**. El formulario pregunta además por lo que
los **permisos** permiten, y el manifiesto declara tres que no estaban escritos
aquí. Medidos el 14-oct sobre el AAB de `0.1.0-rc.1` con `aapt2 dump badging`, y
los tres **se usan de verdad**, así que ninguno se quita:

| permiso | quién lo usa | qué contestar |
|---|---|---|
| `RECORD_AUDIO` | la búsqueda por voz del upstream (`SearchVoiceInput.kt`, vía `SpeechRecognizer`) | **no se recoge**: el audio va al reconocedor del sistema y no llega a nosotros ni al Jellyfin del ISP |
| `WRITE_EPG_DATA` | `LeanbackChannelWorker.kt`, que escribe las filas «Siguiente» y los canales de vista previa del inicio de Android TV | **no se recoge**: se escribe en el proveedor de TV **de la propia caja** y no sale de ella |
| `READ_EPG_DATA` | lo añade `androidx.tvprovider` al fusionar manifiestos, para leer lo que acaba de escribir | igual que el anterior |

**Ninguno se puede quitar**, y la razón es el invariante de `CLAUDE.md` §4: los
tres sirven a funciones de VOD del upstream —buscar por voz y aparecer en el
inicio de Android TV—, así que retirarlos cambiaría el comportamiento de
películas y series. Que `android.hardware.microphone` esté declarado
`required="false"` es coherente: la voz es opcional, y en una caja sin micrófono
la app funciona igual.

Los otros permisos son de infraestructura y no tocan datos personales:
`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`RECEIVE_BOOT_COMPLETED` y el `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que
genera AndroidX.

**La política de privacidad es obligatoria** y necesita una URL pública
permanente. **Redactada el 08-oct en `AVISO_PRIVACIDAD.md`**, verificada campo
por campo contra el código y con un apéndice que liga cada frase a la línea que
la sostiene. Le faltan cinco datos que solo puede poner Edgar —razón social,
domicilio, plazo de conservación, correo de contacto y la fecha de
publicación— y publicarla. Tiene que decir, en español y sin rodeos, qué se recoge de lo de
arriba, para qué, cuánto se conserva y a quién escribir para pedir su
eliminación — eso último lo exige la LFPDPPP, y el `installId` basta para
identificar un envío.

Si algún día entra la capa de **datos de abonado** —quién vio qué—, este
formulario y esa política cambian, y hace falta contrato con el ISP. Hoy no
existe.

---

## 5. GPL-2.0: la oferta de código fuente

El fork es GPL-2.0, así que distribuirlo obliga a ofrecer el código. Lo más
barato y lo que ya hace todo el mundo: **un repositorio público** y su
dirección en la pantalla de Información y en la ficha.

No es opcional ni es un detalle legal menor: es la condición bajo la que se
puede distribuir esto.

**Cumplido el 29-oct.** Hasta ese día la pantalla de Información decía que el
código **existe** sin decir **dónde** —«disponible bajo sus respectivos
términos»—, y una oferta sin dirección no es una oferta.

El repositorio público es **<https://github.com/Kataliziador/palomera>**, con
el historial completo y el README reescrito: el que venía del upstream decía
«Jellyfin Android TV / Part of the Jellyfin Project» con su logotipo, y ésa
iba a ser la primera página que viera quien siguiera el enlace desde la app.

La dirección **no se escribió en la cadena**. Vive en
`BuildConfig.CLIENT_REPOSITORY` (`app/build.gradle.kts`) y la leen los tres
sitios que la necesitan: el aviso de licencias y la cabecera de los dos
informes que se suben al servidor del ISP, que hasta ese día apuntaban al
upstream. Ver `CLAUDE.md` §8.

**Está bajo una organización y no bajo una cuenta personal** porque la oferta
escrita de la GPL-2.0 debe seguir siendo válida **tres años** por cada binario
distribuido, y la dirección queda impresa dentro de cada APK instalado en una
caja que nadie va a actualizar a mano.

Lo que **sigue pendiente** de este apartado: etiquetar en el repositorio el
commit exacto de cada versión que se publique en Play. La oferta es del código
*correspondiente al binario*, y sin etiqueta nadie puede saber cuál es.

---

## 6. La ficha, y una decisión que no es técnica

- **Título: 30 caracteres, y el que este documento proponía no cabía.**
  «Palco — TV en vivo de tu proveedor» son **34**, y aquí ponía que cabe
  (corregido el 27-oct, contados con `len()` y el límite confirmado contra la
  [ayuda de Play Console](https://support.google.com/googleplay/android-developer/answer/113469);
  bajó de 50 a 30 en 2021, así que una guía de ASO que diga 50 está vieja).

  La cuenta que hay que hacer es: con un nombre de N caracteres y « — » de
  separador, quedan **27 − N** para el descriptor. Con un nombre de 8, eso son
  19: «TV de tu proveedor» entra, «TV en vivo de tu proveedor» no.

  **Y el descriptor va aquí, no en el nombre de la app.** El título de la ficha
  es donde se explica qué es esto; la etiqueta del lanzador es solo el nombre,
  y es la que no puede truncarse (§8).
- **Icono** 512×512 y **banner de TV** 1280×720: **ya generados**, en
  `herramientas/marca/png/`. Salen de los mismos vectores del APK con
  `herramientas/marca/generar.sh`, así que no hay una copia del dibujo que se
  pueda quedar atrás.

  Del icono hay **dos**, y hay que elegir. `palomera-icono-512.png` es el
  adaptativo tal cual: su símbolo vive en la zona segura **circular** de 66 dp
  sobre 108, porque un lanzador puede recortarlo en círculo. Play no recorta
  así, y ese icono se ve encogido en la tienda. `palomera-icono-512-ficha.png`
  es el mismo con el dibujo escalado hasta el cuadrado que Play respeta, y **es
  el que conviene subir**. El del APK no se toca: ahí la zona segura es real.
- **Capturas de TV**, mínimo una, 16:9. **No pueden ser de un canal
  reproduciendo.** Lo que se vería es emisión de un tercero —Milenio, Estrellas,
  HBO— y publicarla en una ficha de tienda es usar contenido ajeno en nuestro
  material comercial. Es el mismo motivo por el que las grabaciones de pruebas
  viven fuera del repositorio. Las capturas tienen que ser de **la interfaz**:
  la lista de canales con sus nombres y logos, la guía, la barra de canal sobre
  un fondo propio. Eso además se hace con la caja libre, no durante un remojo.
- **Clasificación de contenido** y **público objetivo**: la app no tiene
  contenido propio, lo sirve el ISP.
- **Países**: México.
- **Instrucciones de acceso para el revisor — obligatorias, y ahora más.**
  Desde el 27-oct la primera pantalla pide **llamar al proveedor y dictarle un
  código** (§8 de `CLAUDE.md`). Un revisor de Google no puede hacer eso, así
  que si no se le dice cómo entrar, rechaza la app por no poder probarla.

  En el campo de instrucciones del envío va, literalmente: la dirección del
  servidor de pruebas, y que **pulse «Usar contraseña»** en la pantalla de
  activación para escribir usuario y clave. Ese botón está ahí a propósito y no
  se quita (§8): mientras un site no tenga panel de abonados instalado, es
  también el único camino de alta que no mete la caja como administrador.

**La decisión que no es técnica:** el nombre «Palomera» está **verificado solo
contra el buscador de las tiendas** —Play México y App Store México, las dos
limpias—, y eso **no es una búsqueda de marcas**. Falta comprobarlo en **IMPI,
clases 38 y 41**, que el título esté libre en la Play Console, y a mano en
Samsung, LG y Roku, que no tienen buscador público alcanzable. Si estuviera
tomado se cambia el nombre visible sin tocar el paquete, pero el paquete ya
lleva la palabra: `mx.kataliziador.palomera` es irreversible desde el primer
envío (§1).

---

## 7. Cómo publicar sin exponer a nadie

La ficha es pública, pero **nadie instala lo que nadie conoce**. El primer
envío puede funcionar como prueba privada: se publica, se instala en una ZTE,
se mira lo que reporta, se corrige —por `deviceOverrides` si basta, o con otra
versión— y solo entonces el ISP empieza a mandar gente.

Dos cosas a tener presentes:

- **Cada iteración pasa por revisión**, días en vez de segundos por `adb`. Eso
  cambia cómo hay que trabajar: conviene llegar al primer envío con el motor
  afinado, no iterar contra Play.
- Cuando el ISP empiece a repartir, el **despliegue escalonado** limita la
  exposición.

---

## 8. Lista de comprobación de cada envío

```bash
JELLYFIN_VERSION=0.1.0-rc.1 ./gradlew :app:bundleRelease
```

La variable es obligatoria y **el build la exige**: sin ella el
`bundleRelease` se detiene antes de compilar nada, en vez de entregar un
`versionCode 1` publicable (§3).

1. El AAB sale en `app/build/outputs/bundle/release/` y se llama
   `palomera-v<versión>-release.aab`. **Si hay otro AAB más viejo al lado, se
   borra**: el riesgo real de este paso es subir el archivo equivocado
2. `versionCode` mayor que el del envío anterior
3. Las dos comprobaciones de aislamiento de `CLAUDE.md` §4.7 salen limpias
4. Las pruebas pasan: `./gradlew :livetv:testDebugUnitTest`
5. La entrada correspondiente está escrita en `LIVETV_LOG.md`

### Lo verificado el 14-oct sobre `0.1.0-rc.1`

**Esta tabla es un registro de lo que se leyó entonces, no el estado de hoy:**
el 27-oct el paquete pasó a `mx.kataliziador.palomera` y la etiqueta a
«Palomera» (§8), así que esas dos filas ya no describen lo que saldría de un
`bundleRelease` ahora. Lo demás sigue valiendo.

Construido de verdad —`bundleRelease` y `assembleRelease`— y leído con
`aapt2 dump badging` y `apksigner verify`, no supuesto:

| | leído |
|---|---|
| paquete | `mx.kataliziador.palco` |
| versión | `versionCode='10001' versionName='0.1.0-rc.1'` |
| etiqueta | `Palco` |
| `minSdk` / `targetSdk` | 21 / 36 |
| lanzadores | `LAUNCHER` y `leanback-launchable-activity`, los dos |
| `uses-feature-not-required` | `leanback`, `touchscreen`, `faketouch`, `microphone` |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| `debuggable` | ausente |
| `BuildConfig` | `DEBUG = false`, `DEVELOPMENT = false` |
| firma | aplicada (con el keystore desechable de §1) |
| AAB | 104,4 MB; los repartos de descarga de §2 siguen valiendo |

Lo que **no** está verificado y no se puede verificar aquí: que esta compilación
corra en una ZTE B866V2, que es justo para lo que se publica (§7).
