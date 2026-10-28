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

1. Open the app. On the "Add server" screen enter: ⟨https://dirección-pública-de-la-demo⟩
2. The first screen shows an activation code, because real subscribers call their provider to activate. As a reviewer, press "Use a password" ("Usar contraseña" in Spanish).
3. Username: ⟨usuario⟩   Password: ⟨contraseña⟩
4. Live TV: open the "Live TV" section ("TV en vivo" in Spanish) and use the D-pad up/down to change channels. Movies: open the library row on the home screen.

The demo server is available 24/7. Contact: ⟨correo⟩
```

**Antes de pegarlo hay que comprobar tres cosas contra la demo de OVH**, que
son las que hacen que un revisor vea algo o no vea nada:

- que la dirección resuelve desde fuera y lleva TLS;
- que el host está dado de alta en el registro del autorizador — si no, la
  sección se **deniega**, no se degrada (`/home/proyectos/palco-demo/README.md`);
- que el usuario `⟨usuario⟩` **no es administrador** — con el panel de abonados
  el 403 de suspensión no le aplica a un administrador (commit `b3569d4`), y
  además un revisor no tiene por qué ver el menú de administración de Jellyfin.

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
| factores de forma | Android TV. El manifiesto declara también `LAUNCHER` y no exige pantalla táctil, así que Play la dejaría instalar en teléfonos: si no se quiere, se excluyen en *Catálogo de dispositivos* y no se suben capturas de teléfono |
