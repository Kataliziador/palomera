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

Las maquetas cubren cuatro pantallas: Inicio, Catálogo, Ficha y En vivo. **No
están diseñadas todavía**, y quien las haga decide dentro de las reglas de §2
y §3:

- **el reproductor** —controles sobre vídeo, con las tintas de alfa de §2—,
  pantalla completa y giro;
- **la ficha de serie**: temporadas y episodios;
- **la búsqueda**;
- **la puerta del abonado**: código de activación, acceso y marca neutra;
- **los estados vacíos**: sin red, site sin guía, contrato suspendido, y el
  caso de §8 —fuera de casa—, que tiene que decir eso y no «no hay internet».

### Una deuda que esto crea, y conviene no olvidar

`LiveTvPaleta` vive en `app/`, y `movil/` no puede depender de `app/`. Así que
el módulo del teléfono **se escribe su propia copia de la paleta**, con una
prueba que fija los valores exactos para que no deriven.

**Cuando `movil/` pase a ser biblioteca** (`MOVIL_PLAN.md` §3), las dos copias
se consolidan en un módulo de diseño del que dependan `app/` y `movil/`. No se
hace antes porque hacerlo ahora obliga a tocar `app/`, que está en revisión.
