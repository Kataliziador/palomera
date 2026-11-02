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
para el descriptor, y «TV de tu proveedor» son 18.

**La búsqueda del IMPI ya está hecha —01-nov— y hay un hallazgo** que decide
Edgar antes del primer envío: «LA PALOMERA» es una **solicitud en trámite** en
clase 38 sobre «servicios de telecomunicaciones», presentada el 16/07/2026 por
CONSORICIO GARLAV S.A. de C.V. No hay nada concedido que impida publicar hoy,
pero el nombre probablemente **no es registrable** en esa clase. El detalle, los
expedientes y los dos riesgos separados están en `PLAY.md` §6.

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

**El campo admite 500 caracteres.** Lo de abajo son **496**, contados; si se
le añade una frase hay que quitar otra, y la que no se quita es ninguna de las
dos primeras.

```
Palomera is a client for TV services run by internet providers; all channels and catalogue belong to each ISP, who holds the rights. Our demo server carries only test-card channels and two generated videos.

Server: http://xtsrt01.ddns.net:8096 - HTTP because ports 80/443 serve other production traffic. Online 24/7.
Sign in: choose the account "revisor", password above.
Live TV: open "TV en vivo"; D-pad up/down changes channel.
Movies: the home screen library row.
Contact: avhelmc@icloud.com
```

**Recortadas el 02-nov para caber en el campo.** La versión anterior pasaba de
900 caracteres. Lo que se fue: la aclaración «no activation code is involved»,
que sobraba desde que QuickConnect está apagado —la app ya no enseña ningún
código por esta ruta, así que no hay nada que desmentir—, y «It has no content
of its own», que repetía la frase siguiente. Lo que **no** se toca al recortar
son las dos primeras afirmaciones: que el contenido es de cada ISP y que el
demo sólo lleva material propio. Eso es §8 del `CLAUDE.md` y es lo que un
revisor de una app de televisión busca; el resto son instrucciones de uso y se
pueden comprimir.

Y se añadió una que no estaba: **el porqué del HTTP**. En la lista de pasos no
hacía falta, pero el revisor que mira la dirección antes de teclearla ve
`http://` y no ve el motivo. Está en el mismo renglón del servidor para que lo
lea ahí y no tenga que buscarlo.

El guion de `Server:` es un guion normal, no una raya: no vale la pena
arriesgar caracteres en un campo medido al límite.

**Reescritas el 01-nov, porque describían una pantalla que ya no sale.** Decían
que la primera pantalla enseña un código de activación y que hay que pulsar
«Usar contraseña». Ese día se **apagó QuickConnect** en la demo y se
**publicaron las cuentas**, así que lo que el revisor ve ahora es la lista de
cuentas del servidor y, al elegir una, el formulario de usuario y contraseña:
con `quickConnectState == UnavailableQuickConnectState` la app deshabilita el
botón del código y llama a `setLoginMethod<UserLoginCredentialsFragment>()`
(`UserLoginFragment.kt:96-97`). Unas instrucciones que mandan pulsar un botón
que no está son un rechazo con otro nombre.

**Comprobado el 01-nov, ya con los dos cambios aplicados:** `/Users/Public`
devuelve `probador` y `revisor`, `/QuickConnect/Enabled` devuelve `false`, y el
`revisor` entra y ve la biblioteca «Películas» con las dos «Demostracion
Palomera». El código del selector **no** hace falta por esta ruta: la dirección
a mano no se esconde nunca.

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
  Resuelto el 30-oct, ya corriendo en la caja de OVH: ocho canales en tres
  grupos, y **con logo**, porque sin `logoUrl` las tarjetas caen a las
  iniciales y ocho canales que empiezan por «Palomera» salen idénticos;
- **el velo de la barra de canal subía encima del texto** y sobre la carta de
  ajuste el nombre del canal quedaba en 2,03:1. Arreglado el 30-oct
  (`LIVETV_LOG.md`); la captura 2 lleva esa corrección dentro.

**Las cuatro están sacadas** el 30-oct, 1920×1080, de un release 0.1.0-rc.1 en
es-MX sobre `tv34`, contra la demo. En este orden:

| | qué enseña |
|---|---|
| 1 | la sección de canales: tres grupos, ocho tarjetas con logo, y la cabecera con lo que está al aire y lo que sigue |
| 2 | un canal en marcha con la barra encima: número, nombre, HD, idioma, progreso y los controles |
| 3 | «Activa tu televisión» con el código, que es lo que cuenta de qué va el producto |
| 4 | el inicio con la biblioteca y la fila de películas |

La 4 es la más floja porque la carátula de la demo es otra carta de ajuste.
Es honesto —no hay ni un fotograma de un tercero— y no se arregla sin meter
material que no es nuestro.

**Dónde están, y por qué no en este repositorio.** Las cuatro viven en
`/home/proyectos/palomera-ficha/`, junto al icono, el banner y el gráfico
destacado:

```
1-seccion-de-canales.png   2-barra-de-canal.png
3-activacion.png           4-inicio.png
palomera-icono-512-ficha.png   palomera-banner-1280.png
palomera-destacado-1024.png
```

Hasta el 02-nov sólo existían en el directorio temporal de una sesión, que se
borra. Los tres últimos se regeneran con `herramientas/marca/generar.sh`; las
cuatro capturas **no**, porque haría falta levantar otra vez el emulador
`tv34`, la demo y el release 0.1.0-rc.1 de aquel día. Son el único archivo de
toda la ficha que no se puede rehacer con una orden.

## 6. Otros campos

| campo | valor |
|---|---|
| categoría | Entretenimiento |
| nombre del desarrollador | **Kataliziador** — puesto el 30-oct. Es un nombre comercial sin registrar, y Console lo acepta en una cuenta personal aunque el nombre legal del titular sea otro (`PLAY.md` §1) |
| correo de contacto | `avhelmc@icloud.com` — **se publica en la ficha**, así que lo encuentran los recolectores de direcciones |
| política de privacidad | `https://kataliziador.github.io/palomera/privacidad` — comprobada el 02-nov: 200 |
| icono de la ficha | `palomera-icono-512-ficha.png`, 512×512 RGB sin alfa |
| banner de TV | `palomera-banner-1280.png`, 1280×720 RGB sin alfa |
| gráfico destacado | `palomera-destacado-1024.png`, 1024×500 RGB sin alfa. **Play no publica una ficha sin él**, y hasta el 02-nov no teníamos ninguno nuestro |
| países | México |
| público objetivo | 18+ — el contenido lo pone el ISP y la app no puede garantizar que sea para menores |
| anuncios | no |
| clasificación de contenido | el cuestionario de IARC; la app no tiene contenido propio, pero da acceso a televisión abierta y de paga, así que se contesta como app de *streaming* de vídeo |
| factores de forma | Android TV. El manifiesto declara también `LAUNCHER` y no exige pantalla táctil, así que Play la dejaría instalar en teléfonos: si no se quiere, se excluyen en *Catálogo de dispositivos* y no se suben capturas de teléfono. **Al revisar ese catálogo, comprobar que las cajas sin WiFi aparecen**: hasta el 29-oct `ACCESS_WIFI_STATE` las excluía en silencio (`LIVETV_LOG.md`, entrada del 29-oct) |

**La trampa de los tres gráficos: el árbol ya traía dos, y son del upstream.**
`fastlane/metadata/android/en-US/images/featureGraphic.png` y `tvBanner.png`
vienen del repositorio de Jellyfin —último commit suyo, «Recreate icon/banner
resources»— y **dicen Jellyfin**. Tienen exactamente las medidas que Play pide,
así que son lo primero que encuentra quien busque «el gráfico destacado» en
este árbol, y subirlos pondría la marca de otro proyecto en la cabecera de
nuestra ficha. Los que se suben son los tres de `/home/proyectos/palomera-ficha/`.

**Y una costura medida en el logotipo, que no bloquea nada.** En el banner y en
el destacado hay una línea de **un píxel** donde el asta de la «P» se junta con
su panza: 221 contra 244 en el destacado, 194 contra 244 en el banner. Son dos
formas que se tocan en vez de solaparse, y el antialias deja el borde entre
ellas. No se ve a los tamaños a los que la tienda los enseña y no es motivo
para rehacer nada ahora; la salida, el día que se toque el dibujo, es solapar
las dos formas en `app_logo.xml` —lo que arregla también el logotipo dentro de
la app, donde la costura es la misma—.

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
| ¿Todos los datos de usuario recopilados por tu app se cifran en tránsito? | **No** — y ver abajo, porque esta respuesta decía «sí» hasta el 02-nov |
| ¿Proporcionas una forma de que los usuarios soliciten que se eliminen sus datos? | **Sí** — por correo, y desinstalar la app borra el identificador. Está en el aviso §6 |

**Por qué «no», si el registro va por HTTPS.** La pregunta es por **todos**
los datos declarados, y son dos flujos, no uno:

| qué sale de la caja | a dónde | cifrado |
|---|---|---|
| el identificador y el diagnóstico | nuestro registro | **sí**, HTTPS, y además con las raíces de ISRG dentro de la app para las cajas anteriores a Android 7.1.1 (`RegistryTrust`) |
| el informe de fallo | el Jellyfin del propio ISP | **no necesariamente** |

El informe de fallo se manda a `crashReportUrl`, que es **la dirección del
servidor al que el abonado entró** (`TelemetryPreferences`), y esa dirección es
`http://` en todo lo que hemos desplegado: la demo va por HTTP porque el
Flussonic de producción ocupa el 80 y el 443, y en el site de un ISP el
Jellyfin vive en su LAN. El `network_security_config` permite tráfico en claro
de forma global **a propósito** —lo necesitan el Jellyfin y los streams— y sólo
lo prohíbe hacia el registro.

Así que hay un dato declarado que puede viajar sin cifrar, y la respuesta
honesta es «no». **Decir «sí» sería falso en una declaración jurada**, que es
causa de retirada, y la ficha enseñará una línea menos bonita a cambio de ser
cierta.

La salida, el día que se quiera cambiar la respuesta, **no es tocar el
formulario**: es que el informe de fallo no salga por HTTP, o que no salga.

### Pantalla 2 — Tipos de datos

Se marcan **tres**, en dos grupos. Esta sección decía «un solo tipo» hasta el
02-nov y le faltaba el segundo, que es el que más datos manda.

| grupo | tipo | qué es, de verdad |
|---|---|---|
| IDs de dispositivo o de otro tipo | **ID de dispositivo o de otro tipo** | el `installId` |
| Información y rendimiento de la app | **Diagnósticos** | el `device.decoders` y el bloque `diagnostico` de `/v1/resolve`: sesiones, ms al primer fotograma, rebuffers, minutos reproducidos, contadores de tunneled, fallos por causa y por canal |
| Información y rendimiento de la app | **Registros de fallos** | el informe de ACRA |

**Por qué faltaban los diagnósticos, y por qué no es opinable.** El artículo
10787469 del centro de ayuda lo dice con todas las letras: *si envías
diagnósticos asociados a un identificador de dispositivo, debes declarar
también los diagnósticos*. Los nuestros viajan en la misma petición que el
`installId`, así que no hay forma de leerlo de otra manera.

**«Otros datos de rendimiento» no se marca**: todo lo que mandamos cabe en
diagnósticos.

**Nada más.** Ni ubicación, ni información personal, ni financiera, ni de
salud, ni mensajes, ni fotos, ni archivos, ni calendario, ni contactos, ni
navegación web —no hay un solo `WebView` en el árbol—.

**Tres que sorprenden y hay que contestar que no, con su razón:**

| Tipo | Respuesta | Por qué |
|---|---|---|
| Grabaciones de audio | **no** | `RECORD_AUDIO` existe para la búsqueda por voz del upstream; el audio va al reconocedor del sistema y no llega ni a nosotros ni al servidor del ISP |
| Actividad en la app | **no** | el diagnóstico no lleva marcas de tiempo, ni orden, ni duraciones por título. Lo más cerca que está de la conducta es `fallosPorCanal`, que es un mapa de *qué canales fallaron* y cuántas veces: deja ver que esa caja sintonizó ese canal alguna vez, y nada más. Es telemetría de error, no de uso — y la descripción de la ficha ya lo dice en voz alta: «los canales que fallaron» |
| Nombre de usuario y contraseña | **no** | los teclea el abonado para entrar **al Jellyfin de su propio proveedor**; no llegan a nosotros, y en la caja se quedan en el dispositivo. El centro de ayuda no tiene una exención con ese nombre: la más parecida es la de subir un archivo a Drive o Dropbox —*si la app nunca recoge ni accede a esos datos, no hay que declararlos*— y es el mismo caso. **Inferido por analogía, no leído literalmente** |

**Y la diferencia con el informe de fallo, que va al mismo servidor y sí se
declara:** ése lo manda **nuestro SDK, solo, de fábrica y sin que nadie lo
pida**, y es justo lo que la pantalla de introducción obliga a revelar
—«datos enviados fuera del dispositivo mediante bibliotecas o SDKs,
independientemente de si esa información se te transmite a ti o a un
tercero»—. La contraseña la escribe el abonado para entrar donde él eligió.
Una es telemetría automática; la otra, una acción suya.

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

**Y la única respuesta de todo el formulario que es un juicio, no un hecho:
¿se «comparte»?** Play llama compartir a transferir a un tercero. El informe
va al Jellyfin **al que el propio abonado entró**, con **su** token de sesión,
y no llega ni a nosotros ni a nadie nuestro: el servidor del ISP es la otra
mitad del servicio que el abonado contrató, no un tercero al que le pasamos
sus datos. Se declara **recogido y no compartido**.

Queda escrito porque es discutible. Si alguna vez Google lee al ISP como un
tercero, la corrección es marcar también «compartido» —cuesta una línea en la
ficha— y no hay nada en el código que cambiar.

## 8. Clasificación de contenido — el cuestionario de IARC

**La pregunta que hay que contestar antes de abrirlo: ¿de qué contenido habla
el cuestionario?** La tentación es decir «de ninguno, el contenido lo pone el
ISP», y es la respuesta que hunde el trámite. **IARC no clasifica quién
produce el contenido, sino qué puede encontrarse quien usa la app.** Un cliente
de televisión por el que se ven películas, noticias y deporte da acceso a todo
eso aunque no lo emita ni lo elija.

Contestar «no» a todo porque el contenido es de otro no evita la clasificación:
la retrasa. Una autoridad de clasificación puede **anular** la calificación
después de revisarla, y entonces hay que rehacer el cuestionario entero con el
envío ya en marcha (Play Console Help, artículo 9859655).

### La decisión, y lo que cuesta

**Se declara que por la app se llega a contenido de televisión abierta y de
paga que nosotros no controlamos**, y se acepta la clasificación alta que salga
de ahí.

Cuesta poco y aquí no cuesta nada: el público objetivo de §6 ya es **18+** por
esta misma razón, y quien instala esta app es el abonado adulto que contrató
el servicio con su ISP. No hay un público infantil que perder.

Lo que sí cuesta la respuesta contraria es el envío.

### Las respuestas

El cuestionario cambia de redacción y se ramifica según lo que se va
contestando, así que esto es **por tema, no por pantalla**. Lo que no cambia es
el criterio: **se mira lo que el abonado puede ver, no quién lo emite**.

| tema | respuesta | por qué |
|---|---|---|
| categoría de la app | **Entretenimiento** | la misma de §6. **No** «noticias»: que haya canales de noticias en el lineup de un ISP no convierte esto en una app de noticias, y esa categoría abre una declaración aparte |
| violencia, sexo, lenguaje, drogas, juegos de azar **en el contenido al que se accede** | **sí**, al nivel de la televisión abierta y de paga | es lo que hay en un lineup real: películas, series, noticieros, deporte. Que no lo programemos nosotros no cambia lo que se ve |
| ¿contenido **generado por usuarios**? | **no** | nadie sube nada: no hay chat, comentarios, perfiles ni subida de archivos |
| ¿los usuarios interactúan o intercambian contenido? | **no** | lo mismo |
| ¿comparte la ubicación? | **no** | la app no pide ningún permiso de ubicación |
| ¿compras digitales? | **no** | el servicio lo contrata el abonado con su proveedor, fuera de la app |
| ¿navegador o acceso abierto a internet? | **no** | la lista de canales la sirve el site del ISP; la app no abre URLs arbitrarias que el usuario escriba como si fuera un navegador |

**Las dos que no pueden salir mal, y van en direcciones opuestas:** decir que
*no* hay contenido maduro accesible es lo que hace que te anulen la
clasificación; decir que *sí* hay contenido generado por usuarios es falso y
clava la app en la clasificación más alta sin motivo. Una habla del contenido,
la otra de quién lo pone ahí.

**Y si una pregunta no se parece a ninguna de estas, no se adivina.** La
redacción exacta no es pública —no está en el centro de ayuda, sólo dentro del
formulario—, así que lo escrito aquí es el criterio, no una transcripción.
