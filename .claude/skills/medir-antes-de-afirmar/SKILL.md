---
name: medir-antes-de-afirmar
description: Usar antes de escribir un número, una causa o un «funciona / no funciona» en LIVETV_LOG.md, CHANNELS.md, un mensaje de commit o un mensaje a Edgar. Obliga a decir cuántas muestras, con qué instrumento, y si el dato es medido o inferido.
---

# Medir antes de afirmar

En este proyecto las conclusiones se convierten en pendientes, los pendientes
en commits y los commits en pruebas que Edgar hace delante de una tele. Una
afirmación mal fundada no cuesta una frase: cuesta una tarde suya.

## La regla

**Antes de que un número o una causa salgan de tu cabeza hacia un documento,
un commit o un mensaje, contesta las tres por escrito:**

1. **¿Cuántas muestras?** Una no es ninguna. Si es una, se escribe «una
   muestra» en el propio texto.
2. **¿Con qué instrumento, y podría ese instrumento ver lo contrario?** Un
   instrumento ciego al fenómeno no respalda nada. `CLAUDE.md` §6 lo dice para
   el caso que más duele: en modo tunneled el contador de fotogramas y
   `SurfaceFlinger --latency` **no ven nada**, así que «no ocurre» y «no lo
   veo» son la misma lectura.
3. **¿Medido o inferido?** Si es inferido, la palabra «inferido» va en el
   texto. No es una disculpa: es lo que permite que alguien lo tumbe.

## Y si lo que afirmas es una causa, léela

Una causa no se deduce de una correlación con dos puntos. Si el código que la
explica está a mano, se abre:

- **Media3 1.8.0** → `/home/proyectos/referencia/media-1.8.0`
- la fuente del proyecto, que suele contestar antes que cualquier medida

Cuesta diez minutos y ha tumbado afirmaciones que llevaban semanas escritas.

## Al cerrar un pendiente

El enunciado del pendiente **también** es una afirmación. Si al cerrarlo
resulta que su título era falso, se dice en el propio pendiente y se corrige el
título. Un pendiente cerrado con el enunciado equivocado reaparece con otro
número tres semanas después.

## Lo que ha costado no hacerlo

Cinco casos, todos de la misma semana de octubre de 2026:

| Afirmé | De dónde salió | Qué era |
|---|---|---|
| el error de sintonía costaba 400-800 ms (pendiente 68) | **una** muestra, y era un reinicio en caliente | 1 784 contra 1 794 ms en 17 sintonías: cero |
| Flussonic omite `field_order` en MPEG-2 (pendiente 53) | supuesto, sin preguntarle a la API | afirma `is_progressive: true` sobre un 480i |
| un reinicio deja el reproductor en la cola (pendiente 71) | dos offsets, sin ver la posición | el offset mentía: la posición estaba a 6,4 s del final |
| «sin `EXT-X-SERVER-CONTROL` el suelo es 3×TARGETDURATION» | deducido de dos medidas | es la rama de reserva de Media3; nunca se ejecuta en nuestro caso |
| la columna `sueloMs` mide el suelo | la puse yo una hora antes | medía el objetivo aplicado: salió el valor pedido clavado |

Los cinco se descubrieron después, y tres de ellos los descubrió Edgar o una
prueba suya.

## Lo que esto NO es

No es pedir certeza antes de hablar. Es pedir que la frase lleve dentro de qué
pie cojea, para que el siguiente que la lea —tú incluido, dentro de una
semana— sepa cuánto peso puede poner encima.
