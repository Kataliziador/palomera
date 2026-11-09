# Diseño de Palomera para teléfonos

**Escrito el 2026-11-09.** Decisiones de Edgar en la conversación de ese día,
más las que tomé yo y van marcadas como tales. Lo técnico —módulos, fases,
riesgos— está en `MOVIL_PLAN.md`; esto es **qué se ve y por qué**.

Maquetas: <https://claude.ai/artifact/1BQyZqBfFKESFdtyNtj8bt>

---

## 1. Qué es, y para quién

Palomera en el celular del abonado, **dentro de su casa**, por el Wi-Fi del
site de su proveedor.

**El catálogo manda.** Al abrir, lo primero es «Seguir viendo» y los carriles de
películas y series. La TV en vivo es una pestaña más. **Es al revés que en la
caja**, donde el canal es el centro, y es decisión de Edgar: a la pregunta de
qué busca primero quien saca el teléfono, contestó «que vea el catálogo de VOD
que tenemos».

**Quien lo usa no es quien lo instala.** El abonado adulto que ya tiene el
servicio contratado y la caja en la sala. No es un usuario nuevo descubriendo
el producto: es alguien que ya lo conoce en la tele y lo quiere en la mano.

### Lo que haría fracasar este diseño aunque funcione

- que parezca **otra app**. Si el abonado no reconoce Palomera al abrirla, la
  marca del ISP se ha roto en el aparato donde más se mira;
- que el catálogo se vea **vacío o desordenado**. Hay 22 bibliotecas, 248
  películas y 237 series: el fallo no es tener poco, es no saber enseñarlo;
- que haya que **pensar** para cambiar de canal o reanudar una película.

---

## 2. Lo que se conserva del diseño de TV, y no se discute

El sistema «TV en Vivo ISP» y `LiveTvPaleta.kt` ya resolvieron esto con
contrastes medidos. **Viaja entero**, porque el color no depende del aparato:

| papel | valor | contraste sobre `#101010` |
|---|---|---|
| fondo | `#101010` | — |
| mosaico (tarjeta, celda) | `#1C1C1E` | — |
| elevado (menú, pastilla) | `#2E2E31` | — |
| tinta principal | `#FFFFFF` | 19,03 |
| tinta apagada | `#BDBDBD` | 10,13 |
| tinta sutil | `#9DA1A6` | 7,32 |
| aviso | `#E2B33C` | 9,74 |
| en vivo | `#E53935` | marca, no fondo de texto |
| velo de esquina | `#8C000000` | — |

Y las dos tintas **sobre vídeo**, que son blanco con alfa (0,78 y 0,62) y no
grises fijos, porque un canal puede emitir blanco a pantalla completa.

También se conserva:

- **el acento es del ISP, no nuestro.** Entra por `Brand.accent` desde el
  servidor y aparece **poco y con intención**: la barra de progreso, la pestaña
  activa y **un** botón principal por pantalla;
- **nunca la marca en el estado de interacción.** En la TV era «el foco nunca
  depende de la marca»; aquí es lo mismo con el dedo (§3);
- **Roboto**, la del sistema Android;
- **español de México, tuteo, mayúscula sólo inicial, sin emojis.** Horas en
  24 h con cifras tabulares y guion largo: `20:00 – 21:00`. Número de canal
  antes del nombre: `103 · HBO HD`.

---

## 3. Lo que cambia, y es el diseño de verdad

| | caja (TV) | teléfono |
|---|---|---|
| lienzo | 960×540 dp | **390×844** |
| margen lateral | `space3xl` 48 | **16** |
| suelo de lectura | 16 sp | **13 sp** |
| interacción | anillo de foco 3 px, escala 1,04 en 160 ms | **sin foco: blanco mínimo de 44 dp** |
| navegación | cruceta | **barra inferior de cuatro** |

**Por qué baja el suelo de lectura.** El sistema dice que 16 sp es «lo que la
app aplica a todo lo que hay que leer desde el sofá». A treinta centímetros eso
es enorme y caben tres cosas donde deberían caber seis. Lo que **no** se encoge
es el blanco del dedo: 44 dp es el suelo, y manda sobre la tipografía.

**La barra inferior lleva cuatro: Inicio · Catálogo · En vivo · Buscar.**
Decisión mía. Buscar va como pestaña y no como lupa escondida porque con 485
títulos es la forma más rápida de llegar a uno concreto.

**Las pantallas de detalle no llevan barra inferior.** Se abren encima y se
sale con atrás. Decisión mía: una ficha es un destino, no una sección.

---

## 4. Las tres cosas que el contenido real obligó a resolver

Medido el 09-nov contra la biblioteca del servidor, no supuesto.

### 4.1 Veintidós bibliotecas no caben como una lista

El servidor sirve `Películas`, `Series` y **veinte colecciones por proveedor**
—`Top Películas Netflix`, `Top Series HBO Max`, `Top Películas Niños`…—. En una
tele caben como filas; en un teléfono, no.

**Se doblan en dos ejes:** tipo arriba (`Películas 248` / `Series 237`) y
proveedor como chips debajo. Dos toques llegan a cualquier combinación, y la
pantalla nunca enseña 22 de nada.

### 4.2 «Seguir viendo» trae duplicados

El mismo título vive en varias bibliotecas, así que el servidor devuelve
**«Top Gun: Maverick» tres veces** y «¡Canta! 2» y «Mayday» dos. En la tele
pasa desapercibido; en la primera pantalla de un teléfono parece un error.

**Se quita el duplicado en el cliente**, agrupando por título y quedándose con
el de posición más avanzada.

### 4.3 Los títulos en español son largos

`Transformers: El despertar de las bestias` son 41 caracteres;
`Tomb Raider: Las aventuras de Lara Croft`, 40. A ancho de celular eso se
corta.

**Dos líneas bajo el cartel, y se corta con puntos suspensivos.** No una: con
una, la mitad del catálogo queda sin distinguirse. No tres: la rejilla pierde
una fila entera.

---

## 5. La TV en vivo, en un teléfono

**Lista vertical, no rejilla.** En la caja la rejilla de tarjetas funciona
porque se navega con cruceta y se mira de lejos. En un teléfono una lista se
lee de un vistazo y cae bajo el pulgar.

Cada renglón: logo (48 dp), número y nombre, **qué hay ahora**, barra de avance
y horario. Debajo no hay arte, y es a propósito: el sistema de diseño ya dejó
escrito que **el XMLTV de los sites llega sin imágenes**, así que diseñar con
arte de programa sería diseñar para un caso que no ocurre.

Dos estados feos que la lista tiene que enseñar bien, porque son los que
generan llamadas: **«sin señal»** —el renglón se atenúa, con etiqueta neutra, no
roja— y **«sin guía disponible»**, que no es un fallo del canal.

---

## 6. Lo que NO entra en la v1

| | por qué |
|---|---|
| **transmitir a la tele** | aplazado por Edgar el 09-nov. Ver §7 |
| **descargas** | ver el aviso de §8 |
| **Chromecast** | el SDK de Cast es Google Play Services, propietario, y este fork es GPL-2.0. Es una decisión de licencia, no técnica |
| **AirPlay** | es de Apple. No existe forma de emitir desde Android. No es una decisión |
| DVR, grabación, catch-up | fuera de alcance en `CLAUDE.md` §7 |

---

## 7. Transmitir a la tele: aplazado, pero medido

Se aplazó el 09-nov. Queda escrito lo que se midió ese día, porque cuando se
retome el trabajo ya está hecho a medias:

- **la caja YA escucha órdenes remotas.** `SocketHandler.kt:106` y `:110` se
  suscriben a `PlayMessage` y `PlaystateMessage` por el websocket de Jellyfin.
  Es código de upstream;
- **el SDK YA trae con qué mandarlas:** `getSessions()`, `play()` y
  `sendPlaystateCommand()` en `SessionApi` 1.7.1.

O sea que **mandar una película del teléfono a la caja es cablear, no
inventar**, y no necesita Google ni dependencias propietarias.

**Lo que sí hay que inventar son los canales.** `PlayMessage` lleva
identificadores de Jellyfin, y nuestros canales vienen del servicio del ISP, no
de Jellyfin (`CLAUDE.md` §6: «el módulo `livetv` no consulta al servidor
Jellyfin para nada»). Eso pide un mecanismo nuestro.

---

## 8. El aviso que hay que leer antes de empezar

**La v1 sirve sólo dentro de casa y no lleva descargas, así que el teléfono
deja de servir en cuanto el abonado cruza la puerta.**

Es defendible —es «la tele en la cocina y en la cama»— y es la decisión de
Edgar, tomada dos veces. Pero conviene que esté escrita junta, porque las dos
mitades se decidieron por separado y el efecto es de las dos: **las descargas
serían la única forma de que el teléfono sirviera fuera sin que el ISP tenga
que publicar su site a internet.**

No se reabre aquí. Se deja dicho.

---

## 9. Lo que queda para quien lo construya

Las maquetas cubren **seis** pantallas: Inicio, Catálogo, Ficha de película,
**Ficha de serie**, En vivo y **Reproductor**.

Dos decisiones de las dos últimas, por si alguien las quiere cambiar a
sabiendas:

- **la ficha de serie abre en el episodio que toca**, no en «reproducir».
  El botón dice `Reanudar T1 E3 · faltan 28 min`, que es lo que el abonado
  necesita saber antes de tocar nada. Las temporadas van como pastillas y no
  como desplegable: con tres o cuatro es más rápido, y en este catálogo
  ninguna serie pasa de siete;
- **el reproductor es la única pantalla horizontal**, y la única donde las
  tintas no son grises fijos: sobre vídeo son blanco con alfa (0,78 y 0,62),
  porque una película puede ser blanca a pantalla completa. El transporte va
  al centro, que es donde ya está el pulgar cuando el teléfono se sostiene
  apaisado.

**Y falta el reproductor de TV en vivo, que no es éste.** No lleva la misma
barra de avance —lo que hay detrás es una ventana DVR de segundos, no una
película— y sí lleva zapeo y barra de canal.

### La puerta y la búsqueda — hechas el 09-nov

**La puerta son dos pantallas, no una**, y usa la **marca neutra**: cuando se
ve, todavía no sabemos de quién es cliente el abonado. Los textos son los
reales de la app de TV (`livetv_servidor_titulo`, `livetv_proveedor_*`,
`livetv_acceso_codigo_paso_*`).

1. **Qué proveedor es.** Enseña las **tres rutas a la vez**: la red reconocida
   («¿Eres cliente de RedTV?» — cero teclado), el código que dio el proveedor,
   y **escribir la dirección**, que no se esconde nunca porque es la única que
   funciona sin internet y es por donde entra el revisor de Google.
2. **Activar este aparato.** Los tres pasos y el código, que es **lo único
   grande de la pantalla** porque es lo que se dicta por teléfono. Debajo,
   «Usar contraseña», que tampoco se quita nunca.

**Un texto que hubo que cambiar, y conviene arreglarlo también en la app de
TV cuando se toque:** `livetv_acceso_titulo` dice «Activa tu televisión». En un
teléfono eso es falso. Aquí dice «Activa este aparato», que vale para los dos.

**La búsqueda devuelve una lista mezclada, no agrupada por tipo.** Agrupar
gasta alto de pantalla, que es lo que escasea; en su lugar, cada renglón lleva
su tipo —`Película · 2023 · 2 h 8 min`— y hay chips para filtrar. La maqueta
usa el peor caso real del catálogo: **los seis Transformers**, seis títulos que
sólo se distinguen por el subtítulo. Es lo que prueba que el título necesita
dos líneas.

### El reproductor de TV en vivo y los estados — hechos el 09-nov

**El reproductor de canales no es el de VOD, y la diferencia es una sola:** su
barra **no es de búsqueda**, es el avance del programa según la guía. Detrás no
hay una película sino una ventana DVR de segundos, así que arrastrarla no
tendría dónde ir. Lleva además el distintivo **EN VIVO** con su punto rojo, el
zapeo arriba/abajo —que es como se cambia de canal con el pulgar— y **«Volver
al directo»**, que sólo aparece cuando el reproductor se ha quedado atrás. Ese
botón es la cara visible de lo que el vigilante del motor arregla solo.

**Los estados vacíos se juzgan por lo que dicen, no por cómo se ven.** Por eso
están los cuatro juntos en una hoja: para leer la copia en paralelo. Tres usan
los textos reales de la app de TV. Las cuatro reglas que siguen:

- **decir qué pasa, no que algo falló.** «Este canal no está llegando», no
  «error de reproducción»;
- **descartar en voz alta la causa que el abonado va a suponer**: «no es tu
  internet», «no hace falta un código nuevo», «puede estar reiniciándose»;
- **decir qué sigue funcionando**, cuando algo sigue: con el servicio
  suspendido, las películas y series siguen en Inicio — que es la regla de
  `CLAUDE.md` §6 dicha donde el abonado la lee;
- **una acción, y que sea la correcta.** «Ver los canales» cuando falta la
  guía, no «volver a probar»: no hay nada que reintentar.

**El estado de fuera de casa lleva pantalla propia**, porque es el único que no
existe en la caja y el que más fácil se lee mal. Su icono es una casa, no un
triángulo de aviso: **no se ha roto nada**. Y lo dice explícito —«no es una
falla ni se te acabaron los datos»— porque sin eso un abonado llama a soporte
por algo que funciona.

### Ajustes e Información — hechas el 09-nov, y con esto está el juego completo

**Ajustes es corto a propósito.** Sólo lo que un abonado puede necesitar
cambiar: su proveedor y cerrar sesión, el idioma de audio y los subtítulos, la
interfaz, y un enlace a Información. Nada de calidad ni de red: la v1 es dentro
de casa y por Wi-Fi.

Dos renglones llevan una línea de explicación debajo, y los dos la necesitan:

- **el idioma de audio** aclara que es una preferencia y no una imposición,
  porque `CLAUDE.md` §4.6 lo exige: en algunos satélites la etiqueta miente, y
  poder cambiar de pista **no es un lujo, es el único remedio**;
- **«Interfaz»** es la palanca manual de la detección de aparato. Nace en
  «Automática», va **al final de la lista** y dice «cámbialo sólo si se
  equivocó». Es a propósito: un abonado que la toque sin querer se queda con la
  interfaz de cruceta, y eso es justo lo que R3 del plan avisa que es rechazo
  en la revisión del formato TV.

**Información es casi toda obligación**, no diseño: lleva lo que `CLAUDE.md` §8
manda —la no afiliación a Jellyfin, de quién es el contenido, y las dos
licencias con la dirección del código— más el **identificador de esta
instalación**, que es lo que el aviso de privacidad pide para ejercer derechos
y por eso va arriba y en monoespaciada, no escondido al final.

Enlaza además el **aviso de privacidad** y los **términos del servicio**, los
dos publicados en `kataliziador.github.io/palomera`.

**Con esto, las catorce pantallas cubren el recorrido completo**: alta,
catálogo, ficha, reproducción, canales, búsqueda, ajustes y los estados que
fallan.

### De la maqueta al teléfono — 09-nov

**Inicio existe ya en Compose**, con la escala de §3 y los colores probados de
§2, y corre en el teléfono. No es la pantalla terminada: no hay cliente de
Jellyfin todavía, así que el contenido son los títulos **reales del servidor**
puestos a mano. Mentir con texto inventado habría escondido justo lo que hay
que mirar — dónde se corta «Transformers: El despertar de las bestias» a ancho
de celular.

Dos cosas del diseño pasaron a ser código con pruebas:

- **`sinDuplicados()`**, con siete casos. Es el defecto medido: `Items/Resume`
  devolvió **once elementos para ocho títulos**. Se queda la copia **más
  avanzada** —es la que el abonado vio de verdad— y **en el sitio de la
  primera**, porque si el superviviente saltara al final la fila se
  reordenaría sola entre dos aperturas;
- **`Tipografia`**, con el suelo en 13 sp en vez de los 16 de la caja, y
  `etiqueta` de 11 sp reservada a marcas que se reconocen sin leerse. Al lado,
  `BLANCO_TACTIL_DP = 44`, que **manda sobre la tipografía**: un renglón
  pequeño se acompaña del relleno que haga falta, no se encoge su zona táctil.

### Dos cosas que sólo se vieron en el teléfono

Las dos estaban bien en la maqueta y mal en el código, y las dos se vieron
mirando la pantalla. Es el argumento de `PLAY.md` §7 otra vez: **mirar es un
instrumento de medida**.

- **«69 % por ver» en vez de «Quedan 1 h 38 min».** Cierto e inútil: nadie
  decide si le da tiempo a terminar una película en porcentajes. La maqueta ya
  lo decía bien; se estropeó al pasarlo a Compose. Ahora vive en
  `tiempoRestante()`, con pruebas, y «Casi terminada» en vez de «Quedan 0 min»,
  que se leería como un fallo;
- **el título pintado dos veces**, dentro del cartel y debajo. En la maqueta el
  bloque de color *representaba* el cartel; en el aparato parece un error. El
  bloque se queda sin texto: ahí va la imagen del servidor.

### Vertical y horizontal: una decisión que falta

Al girar el teléfono, Inicio **se aprieta**: la barra inferior se come casi
todo el primer carril. No se rompe —se desliza igual— pero está claro que las
pantallas de catálogo se pensaron en vertical.

Hay dos salidas y es decisión de Edgar: **fijar las pantallas de catálogo en
vertical** y dejar el apaisado sólo al reproductor, que es lo que hacen casi
todas las apps de vídeo; o **diseñar el apaisado** de catálogo, que son carriles
más bajos y una barra lateral en vez de inferior. Lo primero es gratis; lo
segundo son pantallas nuevas.

### Una deuda que esto crea, y conviene no olvidar

`LiveTvPaleta` vive en `app/`, y `movil/` no puede depender de `app/`. Así que
el módulo del teléfono **se escribe su propia copia de la paleta**, con una
prueba que fija los valores exactos para que no deriven.

**Cuando `movil/` pase a ser biblioteca** (`MOVIL_PLAN.md` §3), las dos copias
se consolidan en un módulo de diseño del que dependan `app/` y `movil/`. No se
hace antes porque hacerlo ahora obliga a tocar `app/`, que está en revisión.
