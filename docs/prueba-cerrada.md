# La prueba cerrada: qué recibe cada probador y qué medimos

**Escrito el 2026-10-31.** Google exige, para una cuenta personal creada
después del 13-nov-2023, **12 probadores apuntados de forma continua durante
14 días** antes de dar acceso a producción (`PLAY.md` §2). Este documento es la
otra mitad: qué hace falta para que esos 12 puedan de verdad usar la app, y qué
sabremos al acabar.

Comprobar que el site está listo **no se hace a ojo**:

```bash
./herramientas/comprobar-demo.py
```

Catorce comprobaciones desde fuera. Mientras salga algo en rojo, no se invita a
nadie: un probador que se atasca en la primera pantalla no vuelve, y los 14 días
corren igual.

---

## 1. Los tres datos que recibe cada probador

Ninguno de los tres está en este repositorio, y **ninguno debe llegar aquí**
(§7).

| dato | de dónde sale | se le manda por |
|---|---|---|
| el enlace de participación de Play | Play Console, al crear la prueba cerrada | el mismo medio por el que se le invita |
| el **código del proveedor** (`DEMO-####`) | lo genera el panel del autorizador al dar de alta el site | mensaje, o dictado por teléfono |
| **usuario y contraseña** del Jellyfin del demo | los creas tú en el panel de ese Jellyfin | mensaje |

**Ninguno de los cuatro roces que detecta `comprobar-demo.py` le cierra la
puerta a un probador**, y conviene no confundirlos con bloqueos: la dirección a
mano nunca se esconde, «Usar contraseña» nunca se quita y la marca neutra está
diseñada para esto. Lo que hacen es gastar la paciencia de alguien que está
haciendo un favor, así que se quitan antes de invitar a nadie —
`comprobar-demo.py --estricto` falla mientras quede uno.

**Su correo de Google tiene que estar en la lista de probadores de la Console
antes de que el enlace funcione.** Un probador que abre el enlace sin estar en
la lista ve un error de Play y no un error nuestro, y va a reportarlo como si
fuera nuestro.

**Una sola cuenta de Jellyfin para los doce**, no doce. Nuestro diagnóstico se
distingue por `installId`, que es por instalación, así que no perdemos nada al
compartirla; y doce credenciales son doce llamadas de soporte. El precio es que
hay que **quitarle el límite de intentos fallidos** a esa cuenta: compartida,
un probador torpe la bloquearía para los otros once.

---

## 2. Lo que va a ver, pantalla por pantalla

Las respuestas de los servicios están **medidas** el 31-oct contra el site de
demostración. Que las pantallas se encadenen así está **leído en el código**, y
es lo que Edgar tiene que validar en pantalla una vez (`CLAUDE.md` §4.7: el
selector toca la puerta compartida con el VOD).

1. **«Conecta con tu proveedor»** → «Buscando tu proveedor…». La app pregunta
   al registro por la red desde la que se llama.
2. **«No reconocimos tu red. Escribe el código que te dio tu proveedor.»** Esto
   es lo normal y no es un fallo: cada probador está en la red de su propio
   ISP, y el registro sólo conoce los rangos de los nuestros. Por eso la ruta
   del código no es un parche para las pruebas
   (`docs/selector-de-proveedor.md` §3).
3. Teclea el código → **«¿Eres cliente de Palomera Demo?»** → «Sí, conectar».
   La app rellena la dirección y sigue por el mismo camino que si la hubiera
   escrito a mano.
4. **«Este servidor no muestra ninguna cuenta»** y un botón para añadirla.
   **Esto es lo normal, no un fallo del demo**: medido el 31-oct,
   `/Users/Public` devuelve `[]` tanto en el demo como en el Jellyfin de la
   oficina —el que usamos todos los días y donde `test` entra sin problema—,
   así que es el valor por defecto. El probador pulsa el botón y teclea el
   usuario. Si se consigue hacer visible una cuenta desde el panel, este paso
   se convierte en elegirla con el mando; **no está verificado** que baste con
   desmarcar la casilla en esta versión.
5. **La contraseña.** Con QuickConnect apagado en el demo, la app va directa
   aquí y no ofrece un código de activación que nadie podría aprobar.
6. **Inicio**, con el VOD del site y «TV en vivo» en la barra.
7. **TV en vivo**: 3 grupos, 8 canales y la guía con 5 576 programas.

**Dos cosas que pueden fallar y no son de la app.** La caja del probador tiene
que poder salir a internet por los puertos **8096, 8097 y 8181**; alguna red
los filtra. Y si su red ya estuviera dada de alta como rango de un ISP nuestro,
le ofrecería ese proveedor en el paso 2 en vez de pedirle el código — no
debería pasar con el demo, pero si pasa, es información y no un fallo.

---

## 3. Qué les pido que reporten

Sin esto, catorce días devuelven «a mí sí me funcionó».

- **Marca y modelo exactos de la caja o del teléfono**, y si es Android TV o
  Google TV. Es el dato más valioso de toda la prueba: hoy todo lo que hemos
  medido viene de **un solo modelo** (`CLAUDE.md` §9).
- **En qué paso del 1 al 7 se quedaron**, si se quedaron.
- **Si la imagen se vio y cómo**: cortes, audio adelantado, imagen deformada,
  pantalla negra con sonido.
- **Cuánto tardó en aparecer el primer canal** a ojo: «casi al instante», «unos
  segundos», «mucho».
- **Qué hizo el mando** donde no esperaban que hiciera eso.

No hace falta que instalen nada ni que miren registros: el inventario y el
diagnóstico viajan solos (§5).

---

## 4. Lo que esta prueba mide, y lo que no

**Decidido el 31-oct por Edgar, y conviene que esté escrito**: los ocho canales
del demo se quedan como están. Son cartas de ajuste **H.264 720p con AAC y una
sola pista de audio** —medido: cero `EXT-X-MEDIA` en los ocho masters—.

Así que la prueba cerrada **ejercita el alta, la sesión, la guía, el zapeo y la
estabilidad**, y **no ejercita nada del Modo A**: ni MPEG-2, ni el respaldo de
libVLC, ni el cambio de pista de audio, ni el SAR anamórfico, ni el
desentrelazado, ni los closed captions. Eso se sigue validando aquí, contra el
headend de pruebas y la Skyworth.

Se descartó meter un canal MPEG-2 en el demo porque exige configurar un stream
nuevo en la máquina que sirve a ~850 espectadores en producción. **El riesgo de
tocar eso es mayor que el valor de la medición**, y la medición no se pierde:
se posterga a cuando haya un site de ISP de verdad.

Lo que sí perdemos, y hay que saberlo al leer los resultados: **doce cajas
desconocidas durante catorce días no nos dirán nada sobre MPEG-2.** Si la
prueba sale limpia, eso no es evidencia de que el motor esté bien; es evidencia
de que la mitad fácil está bien.

---

## 5. Lo que se cosecha solo

| qué | cómo llega | qué desbloquea |
|---|---|---|
| **el inventario de decoders** de cada caja | con la primera `/v1/resolve`, en `device.decoders` | el **pendiente 4**: saber qué decodifica la ZTE B866V2 sin tenerla |
| **diagnóstico de reproducción** — sesiones, rebuffers, ms al primer fotograma, canales que fallaron | con las `/v1/resolve` siguientes | la línea base de §10 en hardware que no es nuestro |

**El inventario llega pase lo que pase**, porque no depende de lo que se
reproduzca. El diagnóstico **sólo llega si la ventana del registro es corta**:
con los 21 días de fábrica, la caja no vuelve a preguntar hasta una semana
**después** de acabar la prueba, y `Diagnostico.resumen()` devuelve `null` en la
primera llamada porque todavía no se ha reproducido nada.

Por eso `AUTORIZADOR_VENTANA_DIAS=7` mientras dure la prueba, y 21 otra vez al
acabar (`docs/DESPLIEGUE_SELECTOR.md` §1). `comprobar-demo.py` lo avisa.

---

## 6. Lo que no entra en este repositorio

**El repositorio es público.** No entran:

- los **correos** de los probadores — son datos personales de terceros;
- la **contraseña** de la cuenta del demo;
- el **código del proveedor** una vez generado. No es un secreto —es un tope de
  velocidad (`docs/selector-de-proveedor.md` §4)—, pero publicarlo invita a
  cualquiera a apuntar su app al site de demostración.

`comprobar-demo.py` los lee del entorno, nunca de un archivo:

```bash
DEMO_CODIGO=… DEMO_USUARIO=… DEMO_CLAVE=… ./herramientas/comprobar-demo.py
```

Con ellos comprueba dos cosas más: que el código resuelve **al mismo Jellyfin**
que acaba de verificar —una errata ahí manda la caja a ninguna parte y el
síntoma sale semanas después— y que la biblioteca VOD no está vacía. La clave
no se imprime nunca, ni en los errores.

---

## 7. El bloque de la marca del demo, para pegar

Hoy el `config.json` del demo trae `brand: null`, así que los doce verían la
marca neutra y **las tres ranuras de §8 no se probarían**. Es el hueco más
barato de los cinco: un bloque en el generador de ese site.

```json
"brand": {
  "displayName": "Palomera Demo",
  "accent": "#3F8EFC"
}
```

**El acento está medido, no elegido a ojo:** `#3F8EFC` contrasta **5,88:1**
contra el `#101010` del fondo de la sección, con la misma fórmula de WCAG que
usa `BrandParser` y muy por encima del mínimo de 3,0. Un color que no llegara
caería al gris neutro y se registraría — que es un fallo discreto y difícil de
ver, así que mejor comprobarlo antes: `comprobar-demo.py` lo calcula.

**Y es azul a propósito.** Nuestro propio icono es cálido, así que si la
sección se pone azul se sabe **de un vistazo** que la ranura surtió efecto. Un
ámbar parecido al nuestro habría dejado la duda de si se aplicó algo.

**`logoUrl` se omite.** El contrato dice que el logo *sustituye* al nombre, y
en el demo sólo hay logos de canal servidos (`logos/demo1.png` → 200); no hay
ninguno de marca (`logos/demo-logo.png` → 404). Apuntar a un 404 dejaría la
cabecera cayendo al nombre en cada carga y registrando un error cada vez. Con
el campo ausente se ve el nombre y no se registra nada, que es el mismo
resultado sin el ruido. Si quieres logo, en `herramientas/marca/png/` está
`palomera-logo.png`; hace falta subirlo al site y poner su URL.

---

## 8. Dos sites, dos públicos — decidido el 31-oct

**Decisión de Edgar.** La prueba cerrada usa **dos sites**, no uno, porque los
dos públicos quieren cosas opuestas y porque de paso se prueba lo que una sola
APK para varios ISPs tiene que saber hacer y nunca se ha probado.

| | el revisor de Google | nuestros 12 probadores |
|---|---|---|
| site | **el demo**, sin tocar | **un segundo site**, con contenido real |
| contenido | cartas de ajuste y dos vídeos generados | el que cargue ese servidor |
| cómo entra | **tecleando la dirección**, como dice `FICHA.md` §4 | **con un código**, el de ese site |
| cuenta | `revisor` | una `probador`, distinta |
| derechos | **ningún material de terceros**, y eso se le dice por escrito | **de Edgar**, que responde por lo que ese servidor emita |

**Por qué el revisor sigue viendo cartas de ajuste.** No es lo que hay a falta
de algo mejor: es la respuesta correcta para ese público. Un revisor de una app
de televisión busca una cosa, si retransmites señal ajena, y las notas de
acceso pueden decir *«no third-party material»* **porque es verdad**. Un
servidor nuestro con canales reales convierte esa frase en una mentira
comprobable. Por el mismo motivo `PLAY.md` §6 prohíbe que las capturas de la
ficha muestren un canal reproduciendo.

**Y el revisor nunca verá el segundo site**, porque entra tecleando la
dirección del demo y no necesita ningún código. El segundo site existe sólo
para quien tiene su código.

**La cuenta del probador no es la del revisor.** Si los doce comparten
`revisor` y uno la bloquea o le cambia algo, el camino por el que entra Google
se rompe y nadie se entera hasta el rechazo.

### Lo que hace falta para el segundo site

| | qué | de quién |
|---|---|---|
| 1 | **un nombre DNS público y único** | de Edgar |
| 2 | un **Jellyfin nuevo** ahí, con una cuenta `probador` no administradora y con acceso a la biblioteca | de Edgar |
| 3 | un **servicio de canales** en el **puerto 8097** de esa máquina | se instala igual que el del demo |
| 4 | su `config.json` con sus canales y **su propio bloque `brand`** | panel de ese servicio |
| 5 | una **fila en el registro**: alias = ese nombre DNS, `servidor_url`, y el código que genere el panel | autorizador, después del despliegue |

**Dos avisos que pueden arruinar la prueba en silencio:**

- **El segundo Jellyfin tiene que ser una instalación nueva, no una copia.** La
  caché del lineup se indexa por el **identificador del servidor**, y un
  contenedor clonado conserva el del original: con el mismo `Id`, los dos sites
  comparten archivo de caché y cada uno sobrescribe la lista del otro. El
  síntoma sería «me salen los canales del otro proveedor», que es exactamente
  el fallo que se quiere descartar.
- **`alias.host` es clave primaria del registro.** Ese nombre DNS no puede estar
  dado de alta en ningún otro site, y por eso no sirve una dirección de LAN.

**Y la marca de cada site, distinta a propósito.** Es lo que convierte «parece
que funciona» en una prueba: si al cambiar de site cambia el nombre y el color
de la cabecera, las ranuras de §8 funcionan; si no cambia, hay una caché
compartida donde no debería.

### Lo que esta prueba demuestra, y que no se ha probado nunca

La arquitectura entera —una APK, N ISPs— descansa en que nada de un site se
cuele en otro. Hoy eso está **leído en el código** y no visto:

| qué | cómo está hoy |
|---|---|
| caché del `config.json` | `config-<id de servidor>.json` |
| caché de la guía | la misma clave |
| marca | dentro de ese archivo, así que indexada con él |
| autorización del registro | guardada **por host** |

Con dos sites de verdad y una caja que salta de uno a otro, eso pasa de leído a
medido. Y se mide el caso que ningún ISP va a provocar pero un técnico sí: la
caja que estaba autorizada en un site y aparece en otro.

**Lo que el probador tiene que hacer**, además de lo de §3: dar de alta **los
dos** servidores y cambiar de uno a otro desde la lista, mirando si la cabecera
cambia de marca y si los canales son los que tocan.
