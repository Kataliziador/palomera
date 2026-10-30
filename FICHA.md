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

Necesitas un servicio de televisión contratado con un proveedor que use Palomera. La aplicación no incluye contenido propio: los canales y el catálogo los emite tu proveedor de internet, que es el responsable de ese contenido y de los derechos para transmitirlo. Si algo de lo que ves te parece mal, la reclamación va a tu proveedor.

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
| el contenido es del proveedor | la fila «De quién es el contenido» de Información, `livetv_legal_content` |

**Lo que no se dice a propósito:** marcas de canales, «gratis», «miles de
canales», ni nada del contenido de un ISP concreto. La ficha es una sola para
todos los proveedores.

## 4. Acceso a la aplicación — para el revisor

Play Console → *Contenido de la aplicación* → *Acceso a la aplicación* →
«Toda la funcionalidad, o parte, está restringida». **En inglés**: lo lee un
revisor que no habla español, y la caja de pruebas de Google puede estar en
inglés, así que el botón se nombra en los dos idiomas.

```
Palomera is a client for a TV service provided by internet service providers (ISPs). It has no content of its own: channels and catalogue are broadcast by each ISP, who holds the rights and is responsible for them. We provide a demo server with our own test content (test-card channels and two generated videos, no third-party material).

1. Open the app. On the "Add server" screen enter: http://xtsrt01.ddns.net:8096
2. The first screen shows an activation code, because real subscribers call their provider to activate. As a reviewer, press "Use a password" ("Usar contraseña" in Spanish).
3. Username: revisor   Password: ⟨la de `PALCO_DEMO_REVISOR_CLAVE` en el `.env` de la demo — se escribe al pegar en la Console, no se guarda aquí⟩
4. Live TV: open the "Live TV" section ("TV en vivo" in Spanish) and use the D-pad up/down to change channels. Movies: open the library row on the home screen.

The demo server is available 24/7. Contact: avhelmc@icloud.com
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

**Resuelto el 30-oct: el registro devolvía el host como nombre de marca.**
Decía `"brand": {"displayName": "xtsrt01.ddns.net"}`, y como el `config.json`
de la demo no trae bloque `brand` ese texto ganaba (§8: registro → site →
neutra) y la cabecera de la sección se dibujaba con una dirección DDNS cruda.
Edgar vació el campo en el panel; comprobado desde fuera, el `brand` ya no
viene y la app usa la **marca neutra**, que dice «TV en vivo» y está diseñada
exactamente para un site sin marca configurada.

Se eligió esa salida y no un nombre de demostración a propósito: la ficha es
una sola para todos los proveedores y no puede dar a entender que hay uno
concreto detrás.

**Lo que no se puede comprobar desde fuera** es el `fin_contrato`: la
respuesta trae `until` recortado a la ventana de caché de 21 días, así que
sólo prueba que el contrato es igual o posterior a esa fecha.

## 5. Capturas de TV — mínimo 1, 1920×1080

De **la demo**, nunca del site de un cliente (`PLAY.md` §6). Cuatro bastan:

1. la sección de TV en vivo con la guía y el canal enfocado;
2. un canal de carta de ajuste con la barra de canal encima (ahora y luego);
3. la pantalla «Activa tu televisión» con el código;
4. la fila de películas en el inicio.

Con tunneled puesto, `screencap` sale **negro** sobre un canal (§6); la 2 se
saca en el emulador de TV o con el interruptor de diagnóstico apagado en una
compilación de depuración.

**Lo aprendido sacándolas**, que cambia cómo hay que hacerlas:

- **tienen que salir de una compilación de `release`**, no de depuración. En
  depuración la cabecera de la sección lleva el botón **«Diagnóstico»**
  (`LiveTvFragment.kt:396`, detrás de `BuildConfig.DEBUG`) y, si se usa un
  override del menú, la píldora ámbar **`registro=manual`**. Ninguna de las
  dos existe en el APK que se publica, pero las dos saldrían en la foto;
- **y por tanto contra un host dado de alta de verdad**: el menú de
  desarrollador no existe en `release`, así que no hay forma de desviar el
  registro. Sirven la demo de OVH y la copia local, porque **las dos son
  alias del site `demo`**;
- **el emulador `api22tv` no sirve para la captura del canal.** Decodifica por
  software en x86 y da `ERROR_CODE_DECODING_FAILED`: la pantalla muestra «Sin
  señal / Reconectando…». **`tv34` —Android TV 14, 1920×1080— sí**, y además
  no tiene tunneled activo, así que `screencap` captura el vídeo en vez de
  salir negro (§6). Es el que se usa;
- **el idioma se pone por app, no por sistema**: la imagen de API 34 es un
  build de usuario y `setprop persist.sys.locale` falla.
  `adb shell cmd locale set-app-locales <pkg> --locales es-MX` sí;
- **la demo tenía tres canales y la rejilla salía con dos tercios en negro.**
  Resuelto en `palco-demo` el 30-oct: ocho canales en tres grupos, y **con
  logo**, porque sin `logoUrl` las tarjetas caen a las iniciales y ocho
  canales que empiezan por «Palomera» salen idénticos. Falta correr el
  instalador en la caja de OVH, que es la que ve un revisor;
- **el velo de la barra de canal subía encima del texto** y sobre la carta de
  ajuste el nombre del canal quedaba en 2,03:1. Arreglado el 30-oct
  (`LIVETV_LOG.md`); la captura 2 se saca con esa corrección dentro.

## 6. Otros campos

| campo | valor |
|---|---|
| categoría | Entretenimiento |
| correo de contacto | `avhelmc@icloud.com` — **se publica en la ficha**, así que lo encuentran los recolectores de direcciones |
| política de privacidad | `https://kataliziador.github.io/palomera/privacidad` |
| países | México |
| público objetivo | 18+ — el contenido lo pone el ISP y la app no puede garantizar que sea para menores |
| anuncios | no |
| clasificación de contenido | el cuestionario de IARC; la app no tiene contenido propio, pero da acceso a televisión abierta y de paga, así que se contesta como app de *streaming* de vídeo |
| factores de forma | Android TV. El manifiesto declara también `LAUNCHER` y no exige pantalla táctil, así que Play la dejaría instalar en teléfonos: si no se quiere, se excluyen en *Catálogo de dispositivos* y no se suben capturas de teléfono. **Al revisar ese catálogo, comprobar que las cajas sin WiFi aparecen**: hasta el 29-oct `ACCESS_WIFI_STATE` las excluía en silencio (`LIVETV_LOG.md`, entrada del 29-oct) |

## 7. Seguridad de los datos — qué se marca en cada pantalla

Play Console → *Contenido de la aplicación* → *Seguridad de los datos*. El
análisis de por qué cada respuesta es la que es está en `PLAY.md` §4; esto es
lo que hay que marcar, pantalla por pantalla.

**No es un trámite: es una declaración jurada y el editor somos nosotros.** Si
no coincide con lo que la app hace de verdad, es causa de retirada.

### Pantalla 1 — «Recopilación y seguridad de los datos»

| Pregunta | Respuesta |
|---|---|
| ¿Tu app recopila o comparte alguno de los tipos de datos de usuario obligatorios? | **Sí** |
| ¿Todos los datos de usuario recopilados por tu app se cifran en tránsito? | **Sí** — el registro va por HTTPS y la app lleva dentro las raíces de ISRG para que también lo sea en cajas anteriores a Android 7.1.1 (`RegistryTrust`) |
| ¿Proporcionas una forma de que los usuarios soliciten que se eliminen sus datos? | **Sí** — por correo, y desinstalar la app borra el identificador. Está en el aviso §6 |

### Pantalla 2 — Tipos de datos

De toda la lista de Play, se marca **un solo tipo**:

- **Identificadores del dispositivo o de otro tipo** → sí.

**Nada más.** Ni nombre, ni correo, ni ubicación aproximada o precisa, ni
contactos, ni fotos, ni archivos, ni audio, ni actividad de la app, ni
historial de navegación, ni información financiera, ni de salud. La app no los
pide y no los puede leer.

**Dos que sorprenden y hay que contestar que no, con su razón:**

| Tipo | Respuesta | Por qué |
|---|---|---|
| Grabaciones de audio | **no** | `RECORD_AUDIO` existe para la búsqueda por voz del upstream; el audio va al reconocedor del sistema y no llega ni a nosotros ni al servidor del ISP |
| Actividad en la app | **no** | el diagnóstico cuenta **qué canales fallaron**, sin marcas de tiempo, sin orden y sin duraciones individuales. De ahí no se reconstruye qué vio nadie |

### Pantalla 3 — Detalle del identificador

| Pregunta | Respuesta |
|---|---|
| ¿Se recopila o se comparte? | **Se recopila** (no se comparte) |
| ¿Es obligatorio o puede el usuario elegir? | **Obligatorio** — va siempre |
| Finalidades | **Funciones de la app** y **Estadísticas** |
| ¿Está vinculado a la identidad del usuario? | **No** — es un UUID aleatorio que crea la propia app y se borra al desinstalar |

### Y lo que casi se queda fuera: los informes de fallo

El upstream sube el informe de fallo **al servidor Jellyfin del propio ISP**,
no a nosotros (`clientLogApi.logFileUrl()`), y viene activado de fábrica.

Sale de la caja hacia un tercero respecto del usuario, así que **se declara**:
tipo **«Registros de fallos»**, *se recopila*, **opcional** —se apaga en
Ajustes → Informes de fallo— y finalidad **Estadísticas**.

Es el único dato que no va a un servicio nuestro, y es el que más fácil se
olvida porque no lo escribimos nosotros.

## 8. Clasificación de contenido — el cuestionario de IARC

Se contesta como **app de reproducción de vídeo en continuo**, no como app con
contenido propio. Las respuestas que importan:

| Pregunta | Respuesta |
|---|---|
| ¿La app permite a los usuarios interactuar o intercambiar contenido? | **No** — no hay chat, comentarios, perfiles ni subida de nada |
| ¿Comparte la ubicación del usuario? | **No** |
| ¿Permite compras? | **No** — el servicio lo contrata el abonado con su proveedor, fuera de la app |
| ¿Contiene violencia, sexo, lenguaje, drogas o juegos de azar propios? | **No propios.** La app da acceso a televisión abierta y de paga que programa cada proveedor |

**Esa última es la que hay que contestar con cuidado**, y conviene declarar en
el campo de texto libre que el contenido lo programa un tercero —el proveedor—
y que la app no lo produce ni lo selecciona. Es lo mismo que dicen la pantalla
de Información, la descripción y la página pública: **los cuatro textos tienen
que decir lo mismo**, porque una contradicción entre el cuestionario y la ficha
es lo que dispara una revisión manual.
