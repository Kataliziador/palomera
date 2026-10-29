# Palomera

Aplicación de televisión para Android TV que los proveedores de internet
instalan en la caja de sus abonados: los canales en vivo y el catálogo de
películas y series que cada proveedor sirve desde su propia red.

**Este repositorio existe para cumplir la GPL-2.0.** La aplicación se
distribuye en Google Play, se deriva de software libre y su licencia obliga a
ofrecer el código fuente completo. Aquí está.

---

## No está afiliada al proyecto Jellyfin

Palomera es un **fork** de
[jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv), del
proyecto Jellyfin, bajo licencia GPL-2.0. El proyecto Jellyfin no la publica,
no la respalda y no es responsable de ella.

Lo que **sí** viene de Jellyfin es el servidor que cada proveedor opera para su
catálogo, y por eso la aplicación lo nombra cuando habla de ese servidor.

## Qué le añade este fork

Un **módulo de televisión en vivo** (`livetv/`), escrito desde cero y aislado
del resto: lista de canales, guía de programación, y un motor de reproducción
que elige por canal y por modelo de caja entre Media3/ExoPlayer y libVLC. El
camino de películas y series es el del upstream y no se modifica.

El resto de los cambios son de marca y de texto: la aplicación no usa el
nombre ni el logotipo de Jellyfin.

## Licencias

- Este fork y el upstream del que deriva: **GPL-2.0**, en [`LICENSE`](LICENSE).
- [libVLC](https://www.videolan.org/vlc/libvlc.html), de VideoLAN, que viaja
  dentro del APK: **LGPL-2.1**.

## Compilar

Hace falta el SDK de Android y una JDK 21. El envoltorio de Gradle se encarga
del resto:

```bash
./gradlew :app:assembleDebug
```

Para una compilación de publicación hay que dar una versión semántica y las
credenciales de firma, que no viven en el repositorio:

```bash
JELLYFIN_VERSION=0.1.0 ./gradlew :app:bundleRelease
```

## Sobre la documentación

Los documentos de este repositorio están **en español** y son los de trabajo
del proyecto: `CLAUDE.md` recoge las reglas, `LIVETV_ENGINE.md` la
especificación del motor y `LIVETV_LOG.md` la bitácora con las mediciones.
Están publicados tal cual, sin limpiar, porque forman parte del código fuente
que la licencia obliga a ofrecer.
