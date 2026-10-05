# PLAY.md

**Lo que hace falta para publicar Palco en Google Play, y quién lo hace.**

Este documento existe porque el primer envío tiene una propiedad incómoda: la
caja mayoritaria del cliente —la ZTE B866V2— **solo admite instalaciones desde
la ficha pública**, así que publicar no es el final del trabajo sino el
instrumento para empezar a medirla (`LIVETV_LOG.md`, entrada de B6).

---

## 1. Lo irreversible, antes de nada

Dos cosas no se pueden deshacer, y las dos se fijan en el primer envío.

**El nombre de paquete: `mx.kataliziador.palco`.** Play lo fija para siempre.
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
las lee de propiedades (`keystore.file`, `keystore.password`,
`keystore.alias`, `keystore.alias.password`).

---

## 2. Lo que ya está hecho en el código

No hay que volver a tocarlo. Está aquí para no repetir trabajo ni dudar.

| | Estado |
|---|---|
| `applicationId` propio | `mx.kataliziador.palco` |
| Nombre e icono propios | «Palco», icono y banner en vector, sin rastro de Jellyfin (§8) |
| `LEANBACK_LAUNCHER` | declarado, junto al `LAUNCHER` normal |
| `leanback`, `touchscreen`, `faketouch`, `microphone` | los cuatro `required="false"` |
| `targetSdk` | 36 |
| `minSdk` | 21 |
| 64 bits | el AAB trae `arm64-v8a`; la única nativa es `libandroidx.graphics.path` |
| `debuggable` en release | ausente |
| Avisos legales | «no afiliado al proyecto Jellyfin» y licencia GPL-2.0, en Información |

**Sobre las ABIs:** no hay NDK ni compilación nativa propia —se fueron con la
extensión FFmpeg (§6)—, así que el bundle reparte las cuatro arquitecturas
solo y el requisito de 64 bits se cumple por construcción. No hay nada que
configurar.

---

## 3. La versión, antes del primer envío

Hoy el proyecto compila como `0.0.0-dev.1`, `versionCode 1`. **Eso no se
publica.** Hay que decidir el esquema de versiones y fijarlo antes del primer
AAB, porque el `versionCode` solo puede subir: un número alto por accidente
quema todos los menores para siempre.

---

## 4. Formulario de Seguridad de los datos

**Esto no es trámite: es una declaración y es nuestra responsabilidad**, porque
el editor somos nosotros (`CLAUDE.md` §8). Lo que la app manda hoy, y a dónde:

| Qué | A dónde | Para qué |
|---|---|---|
| `installId` — identificador opaco **por instalación** | registro propio | contar cajas |
| Modelo, fabricante, ABIs, RAM, versión de Android | registro propio | soporte |
| Inventario de decoders de vídeo | registro propio | elegir el motor por modelo |
| Diagnóstico de reproducción acumulado | registro propio | operar: qué falla y dónde |
| Host del servidor que escribió el abonado | registro propio | saber de qué ISP es |

Cómo se declara, y por qué:

- **Se recoge, no se comparte.** Va a un servicio nuestro y a nadie más.
- Es **«Identificadores del dispositivo u otros»** por el `installId`, con
  finalidad de **funcionalidad de la app** y **análisis**.
- **No es opcional**: va siempre, así que se declara como obligatorio.
- **No hay datos personales ni actividad del abonado.** Está construido para
  que no los haya: se cuentan los canales que **fallaron**, sin marcas de
  tiempo, sin orden y sin duraciones individuales. De ahí no se reconstruye un
  historial de visionado. Esa frontera está en el diseño, no en la intención, y
  conviene no cruzarla sin volver a mirar este formulario.

**La política de privacidad es obligatoria** y necesita una URL pública
permanente. Tiene que decir, en español y sin rodeos, qué se recoge de lo de
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

---

## 6. La ficha, y una decisión que no es técnica

- **Título** (30 caracteres): «Palco — TV en vivo de tu proveedor» cabe.
- **Icono** 512×512 y **banner de TV** 1280×720. El del APK es vectorial; el de
  la ficha es un PNG aparte que hay que generar.
- **Capturas de TV**, mínimo una, 16:9.
- **Clasificación de contenido** y **público objetivo**: la app no tiene
  contenido propio, lo sirve el ISP.
- **Países**: México.

**La decisión que no es técnica:** el nombre «Palco» está **elegido, no
verificado**. Falta comprobarlo en **IMPI, clases 38 y 41**, y que el título
esté libre en la Play Console. Si está tomado, se cambia el nombre visible sin
tocar el paquete — por eso el paquete no lleva la palabra «palco» como marca
sino como nombre propio nuestro.

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
./gradlew :app:bundleRelease
```

1. El AAB sale en `app/build/outputs/bundle/release/`
2. `versionCode` mayor que el del envío anterior
3. Las dos comprobaciones de aislamiento de `CLAUDE.md` §4.7 salen limpias
4. Las pruebas pasan: `./gradlew :livetv:testDebugUnitTest`
5. La entrada correspondiente está escrita en `LIVETV_LOG.md`
