# Aviso de privacidad — Palomera

> **Lo escrito aquí está verificado contra el código:** cada dato de la lista
> se puede rastrear hasta la línea que lo envía.
>
> Publicado en <https://kataliziador.github.io/palomera/privacidad>. Esa es la
> dirección que va en la ficha de Google Play, que no acepta el envío sin ella.
>
> Esto no es asesoría legal. Está redactado siguiendo lo que pide la LFPDPPP
> para un aviso simplificado. **Dos cosas que conviene que revise quien lleve
> lo legal:** que «LLC» sea la figura correcta para una responsable con
> domicilio en México, y el plazo de conservación.

---

## 1. Quién es responsable de tus datos

**Kataliziador LLC**, con domicilio en Calle Ignacio Allende SN, C.P. 86927,
Tenosique, Tabasco, México, es responsable del tratamiento de los datos que se
describen aquí.

Palomera es una aplicación de televisión que instala tu proveedor de internet en
tu caja. **Tu proveedor no es el responsable de este aviso**: lo somos
nosotros, que publicamos la aplicación.

## 2. Qué recoge la aplicación

Palomera envía a un servicio nuestro, cada vez que comprueba que tu proveedor
tiene el servicio contratado, lo siguiente:

| Dato | Qué es |
|---|---|
| Identificador de instalación | Un número aleatorio que se crea al instalar la app y **se borra al desinstalarla**. No viene de tu caja, no es su número de serie y no nos lo da nadie: lo inventa la propia aplicación |
| Versión de la aplicación | Para saber qué versión tiene cada caja |
| Dirección del servidor de tu proveedor | La que se escribió al configurar la app. Nos dice de qué proveedor eres, no quién eres |
| Datos técnicos de la caja | Fabricante, modelo, versión de Android, arquitectura, memoria y la lista de decodificadores de vídeo que trae |
| Resumen de reproducción | Cuántos canales se abrieron, cuántos dieron imagen, cuánto tardó en aparecer, cuántos cortes hubo, cuántos minutos se reprodujeron en total, y **qué canales fallaron** |
| Canales con el audio mal preparado | Si un canal de tu proveedor llega con un formato de audio que no es el que debería, el nombre de ese canal y el formato que llegó. Es un dato sobre **el canal**, no sobre ti: sirve para avisar a tu proveedor de que ese canal se va a quedar sin sonido en algunas cajas |

**Y nada más.** No hay nombre, correo, teléfono, dirección, ubicación,
contactos, fotos, micrófono ni cámara. La aplicación no los pide y no los
puede leer.

### Lo que deliberadamente *no* se recoge

El resumen de reproducción dice **qué canales fallaron**, no qué canales viste.
No lleva marcas de tiempo, ni el orden en que se vieron, ni cuánto duró cada
uno. Un canal que funciona bien y se ve todos los días no aparece en ningún
sitio.

**No se puede reconstruir qué viste ni cuándo**, y eso no es una promesa: es
cómo está construido. Si algún día quisiéramos saberlo haría falta cambiar la
aplicación, y entonces este aviso cambiaría antes.

## 3. Para qué

Tres cosas, y solo tres:

- **Comprobar que tu proveedor tiene el servicio contratado**, que es lo que
  permite que la sección de televisión funcione.
- **Hacer que la televisión se vea bien en tu caja.** Saber qué
  decodificadores trae un modelo concreto es lo que nos deja corregir, sin
  tener que ir a tu casa, un problema de imagen que afecte a ese modelo.
- **Encontrar canales que fallan.** Si un canal falla en muchas cajas del
  mismo proveedor, el problema está en su equipo y hay que avisarle.

**No se usa para publicidad, no se cruza con nada y no se vende.**

## 4. Con quién se comparte

**Con nadie.** Los datos van a un servicio nuestro y no salen de ahí. No hay
terceros, ni servicios de análisis, ni redes publicitarias.

### Informes de fallo: van a tu proveedor, no a nosotros

Si la aplicación se cierra inesperadamente, envía un informe del fallo **al
servidor de tu propio proveedor**, no a nosotros. Ese informe lleva datos
técnicos de la caja y el detalle del error. Es una función que viene del
programa sobre el que está construida la aplicación.

**Se puede desactivar** en Ajustes → Informes de fallo. Viene activada.

## 5. Cuánto tiempo se conserva

**12 meses**, y después se borra. Ese plazo cubre un ciclo estacional
completo, que es lo que permite comparar un problema con el mismo mes del año
anterior — en televisión por satélite eso importa, porque la lluvia es
estacional y degrada la señal.

## 6. Tus derechos

Puedes pedirnos **acceder** a tus datos, **rectificarlos** si son incorrectos,
**cancelarlos** u **oponerte** a que los tratemos. También puedes revocar tu
consentimiento.

Escribe a **avhelmc@icloud.com** indicando **el identificador de
instalación**,
que puedes ver en la aplicación en **Ajustes → Información → Identificador de
esta instalación**. Sin él no podemos localizar tus datos, porque es lo único
que los identifica: no sabemos tu nombre.

**La forma más rápida de borrarlo todo** es desinstalar la aplicación. El
identificador se borra con ella y lo que quedase deja de poder asociarse a
nada.

Responderemos en un plazo máximo de 20 días hábiles.

## 7. Si esto cambia

Cualquier cambio se publica en esta misma dirección, con la fecha de la última
actualización al pie. Si el cambio fuera sustancial —por ejemplo, si algún día
se recogieran datos de qué se ve— se avisará dentro de la propia aplicación
antes de que entre en vigor.

---

*Última actualización: 29 de octubre de 2026*

---

## Apéndice para nosotros — no publicar

Esto no va en la página. Es la correspondencia entre cada frase del aviso y el
código, para que el día que alguien cambie lo que se envía sepa que este
documento también cambia.

| Lo que dice el aviso | Dónde está en el código |
|---|---|
| Identificador de instalación | `RegistryRepository.installId`, un `UUID` aleatorio en `SharedPreferences` |
| Versión, host, datos de la caja | `RegistryRepository.peticion()` y `dispositivo()` |
| Resumen de reproducción | `Diagnostico.resumen()` |
| Canales con el audio mal preparado | `Diagnostico.resumen()` → `audioSinNormalizar`, alimentado desde `Media3LiveEngine.revisarNormalizacionDeAudio()`. Clave `canal:códec`, acotado a 20 canales |
| «qué canales fallaron, no qué viste» | `Diagnostico.anotarSesion()`: solo suma cuando `causaDeFallo != null` |
| Destino | `BuildConfig.REGISTRY_URL`, hoy `https://authjelly.satmx.net` |
| Identificador a la vista | `about.kt`, fila «Identificador de esta instalación» |
| Informes de fallo al ISP | `SessionRepository` fija la URL con `clientLogApi.logFileUrl()`; activados de fábrica en `TelemetryPreferences.crashReportEnabled` |

**Tres cosas que hay que recordar:**

1. **Si se añade un campo a la petición del registro, este aviso cambia.** Es
   el acoplamiento que más fácil se olvida, porque añadir un campo es una línea
   y el aviso vive en otro sitio.
2. **El formulario de Seguridad de los datos de Play tiene que decir lo
   mismo** (`PLAY.md` §4). Dos documentos que se contradicen es peor que uno
   mal.
3. **El día que entre la capa de datos de abonado** —quién vio qué, `CLAUDE.md`
   §8— esto se reescribe entero y hace falta contrato con el ISP. No es un
   añadido a la lista: es otro aviso.
