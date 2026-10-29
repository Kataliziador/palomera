# FICHA.md

**Los textos de la ficha de Google Play y del formulario de envío, listos para
pegar.** `PLAY.md` dice qué hace falta y por qué; esto es lo que se escribe en
cada campo. Los límites de longitud están contados con `len()`, no a ojo.

Lo que va entre `⟨así⟩` lo pone Edgar.

---

## 1. Título — máximo 30

```
Palomera — TV de tu proveedor
```

29 caracteres. Sigue la cuenta de `PLAY.md` §6: con un nombre de 8, quedan 19
para el descriptor, y «TV de tu proveedor» son 18. **Falta la búsqueda de
marca en el IMPI** (clases 38 y 41) antes del primer envío.

## 2. Descripción breve — máximo 80

```
La TV en vivo y las películas de tu proveedor de internet, en tu Android TV.
```

## 3. Descripción completa — máximo 4 000

```
Palomera es la aplicación de televisión de tu proveedor de internet. Con ella ves en tu caja o pantalla Android TV los canales en vivo y las películas y series que tu proveedor incluye en tu servicio.

Necesitas un servicio de televisión contratado con un proveedor que use Palomera. La aplicación no incluye contenido propio: lo que ves lo pone tu proveedor.

TELEVISIÓN EN VIVO
• Cambia de canal con las flechas del control, como en una tele de siempre.
• Mira qué programa está al aire y cuál sigue, con su horario.
• Si la señal de un canal se corta, la aplicación espera y vuelve sola, sin que tengas que tocar nada.

PELÍCULAS Y SERIES
• El catálogo que tu proveedor tiene para ti, con búsqueda, también por voz si tu control tiene micrófono.
• Retoma lo que dejaste a medias.

FÁCIL DE ACTIVAR
La primera vez, la pantalla te muestra un código. Llama a tu proveedor, díctaselo y listo: no hace falta crear cuentas ni recordar contraseñas.

Si tu servicio se suspende, la aplicación te lo dice con claridad. En cuanto se reanude, tu televisión vuelve a entrar sola, sin pedir un código nuevo.

PRIVACIDAD
Palomera no registra lo que ves. Solo envía datos técnicos del aparato y de los canales que fallaron, para que tu proveedor pueda resolver problemas. El detalle está en el aviso de privacidad.

Palomera es software libre, derivado de un proyecto de código abierto, bajo licencia GPL-2.0. No está afiliada al proyecto Jellyfin.
```

**Cada frase está sostenida por el código, y no se añade una que no lo esté.**
Una ficha que promete lo que la app no hace es causa de rechazo y de reseñas
de una estrella:

| frase | dónde está |
|---|---|
| flechas cambian de canal | el zapeo de B-4, también desde una pantalla de aviso (pendiente 82) |
| ahora y luego, con horario | B8 |
| vuelve sola si se corta la señal | B6, la escalera del `LiveWatchdog` |
| búsqueda por voz | `SearchVoiceInput.kt` del upstream |
| activación con código | la pantalla de acceso, `livetv_acceso_*` |
| suspendido, sin código nuevo | `ServiceSuspendedState`, 28-oct |
| no registra lo que ves | `PLAY.md` §4, `AVISO_PRIVACIDAD.md` |

**Lo que no se dice a propósito:** marcas de canales, «gratis», «miles de
canales», ni nada del contenido de un ISP concreto. La ficha es una sola para
todos los proveedores.

## 4. Acceso a la aplicación — para el revisor

Play Console → *Contenido de la aplicación* → *Acceso a la aplicación* →
«Toda la funcionalidad, o parte, está restringida». **En inglés**: lo lee un
revisor que no habla español, y la caja de pruebas de Google puede estar en
inglés, así que el botón se nombra en los dos idiomas.

```
Palomera is a client for a TV service provided by internet service providers (ISPs). It has no content of its own. We provide a demo server with our own test content (test-card channels and two generated videos, no third-party material).

1. Open the app. On the "Add server" screen enter: http://xtsrt01.ddns.net:8096
2. The first screen shows an activation code, because real subscribers call their provider to activate. As a reviewer, press "Use a password" ("Usar contraseña" in Spanish).
3. Username: revisor   Password: ⟨PALCO_DEMO_REVISOR_CLAVE del .env de la demo⟩
4. Live TV: open the "Live TV" section ("TV en vivo" in Spanish) and use the D-pad up/down to change channels. Movies: open the library row on the home screen.

The demo server is available 24/7. Contact: ⟨correo⟩
```

**Comprobado el 29-oct contra la demo de OVH** (`xtsrt01.ddns.net`, la misma
máquina que el Flussonic de producción, que ocupa el 80 y el 443 — por eso la
demo va por HTTP y en sus puertos):

- Jellyfin 10.11.11 «Palomera Demo», canales «Palomera Uno/Dos/Tres» con guía
  al día, dos películas «Demostracion Palomera»;
- `revisor` entra y **no es administrador**: `/System/Logs`, `/Devices`,
  `/Auth/Keys` y `/Library/VirtualFolders` le dan 403. La clave de fábrica del
  administrador ya no entra (401);
- **el host está dado de alta**, comprobado contra el registro de producción
  (`https://authjelly.satmx.net`) y **sin escribir nada en él**: la consulta se
  mandó sin `installId`, y sin ese campo `apuntar()` no registra caja ni
  diagnóstico (`autorizador/app.py:149`). Respuesta: `authorized: true`, site
  `demo`, `channelsUrl` correcta.

**Y un fallo que saldría en las capturas:** el registro devuelve

```json
"brand": { "displayName": "xtsrt01.ddns.net" }
```

El `config.json` de la demo **no trae bloque `brand`**, así que gana el del
registro (`CLAUDE.md` §8: registro → site → neutra) y la cabecera de la sección
de TV en vivo se dibuja con ese texto (`LogoDeCanal.kt:157`, el nombre sólo
aparece porque tampoco hay logo). Un revisor —y la ficha para siempre— vería
una dirección DDNS cruda donde va el nombre del proveedor.

Se arregla en **«Sites» del autorizador**, campo *marca_nombre* del site `demo`,
y son dos salidas:

- **dejarlo en blanco** → el registro omite `brand` y la app usa la **marca
  neutra**, que dice «TV en vivo» y está diseñada exactamente para un site sin
  marca configurada. Es la opción sin riesgo;
- **poner un nombre de demostración**, p. ej. «TV Demostración». Enseña la
  ranura de marca funcionando, que es más vistoso en una captura. **No un
  nombre de ISP real ni inventado con pinta de serlo:** la ficha es una sola
  para todos los proveedores y no puede dar a entender que uno concreto está
  detrás.

**Un segundo dato del mismo sondeo:** `until` vino a exactamente 21 días
(2026-11-19T12:48:52Z), que es la ventana de caché del registro. Eso sólo
prueba que `fin_contrato` del site `demo` es **igual o posterior** a esa fecha
—la respuesta se recorta al contrato si éste cae antes—, así que **no descarta**
que el contrato de la demo venza el 19-nov, en plena revisión de Google.
Conviene mirarlo en el panel y ponerlo lejos.

## 5. Capturas de TV — mínimo 1, 1920×1080

De **la demo**, nunca del site de un cliente (`PLAY.md` §6). Cuatro bastan:

1. la sección de TV en vivo con la guía y el canal enfocado;
2. un canal de carta de ajuste con la barra de canal encima (ahora y luego);
3. la pantalla «Activa tu televisión» con el código;
4. la fila de películas en el inicio.

Con tunneled puesto, `screencap` sale **negro** sobre un canal (§6); la 2 se
saca en el emulador de TV o con el interruptor de diagnóstico apagado en una
compilación de depuración.

## 6. Otros campos

| campo | valor |
|---|---|
| categoría | Entretenimiento |
| correo de contacto | ⟨correo⟩ — se publica en la ficha |
| política de privacidad | ⟨URL de `AVISO_PRIVACIDAD.md` publicado⟩ |
| países | México |
| público objetivo | 18+ — el contenido lo pone el ISP y la app no puede garantizar que sea para menores |
| anuncios | no |
| clasificación de contenido | el cuestionario de IARC; la app no tiene contenido propio, pero da acceso a televisión abierta y de paga, así que se contesta como app de *streaming* de vídeo |
| factores de forma | Android TV. El manifiesto declara también `LAUNCHER` y no exige pantalla táctil, así que Play la dejaría instalar en teléfonos: si no se quiere, se excluyen en *Catálogo de dispositivos* y no se suben capturas de teléfono. **Al revisar ese catálogo, comprobar que las cajas sin WiFi aparecen**: hasta el 29-oct `ACCESS_WIFI_STATE` las excluía en silencio (`LIVETV_LOG.md`, entrada del 29-oct) |
