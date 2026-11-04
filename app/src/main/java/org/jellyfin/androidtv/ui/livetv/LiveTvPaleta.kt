package org.jellyfin.androidtv.ui.livetv

import androidx.compose.ui.graphics.Color

/**
 * Los colores de la sección de canales, con nombre.
 *
 * **Por qué existe:** hasta este commit la sección tenía **46 literales
 * `Color(0x…)` repartidos en diez archivos, con 18 valores distintos**, y entre
 * ellos **diez grises para tres papeles de texto**. Nada se alineaba porque no
 * había una escala: cada pantalla elegía su gris, y dos textos del mismo rango
 * salían distintos en dos archivos vecinos.
 *
 * La estructura es la del sistema de diseño «TV en Vivo ISP» —superficies,
 * tres tintas, estados— pero **con los valores neutros de esta app, no con los
 * del sistema**: §4.2 manda que la sección se vea como el resto de Palomera, y el
 * fondo del tema es `#101010`
 * ([org.jellyfin.androidtv.ui.base.colorScheme]). Adoptar el `#05070a` azulado
 * del sistema habría puesto la sección sobre un fondo distinto al de Inicio y
 * Buscar.
 *
 * **Dos grises se fueron porque no pasaban el suelo de contraste**, medido
 * sobre los fondos reales: `#7D8187` daba 4,86:1 sobre la sección y **4,34:1
 * sobre una tarjeta**, y `#6E7278` daba 3,93 y 3,52. El mínimo para texto es
 * 4,5:1, y es la propia lista de comprobación del sistema de diseño la que lo
 * pide. [tintaSutil] sube a 7,32 y 6,55.
 *
 * **Lo que no nombra este archivo:** el acento del ISP, que no es un color de
 * la app sino un dato del servidor —`Brand.accent`, §5.12— y entra como
 * parámetro donde haga falta; y `Color.White`, que se queda tal cual donde
 * significa blanco de verdad (el anillo de foco, el texto encima de vídeo).
 * [tinta] es ese mismo blanco con nombre, para usarlo donde es «la tinta
 * principal» y no «blanco».
 *
 * Contrastes medidos (WCAG 2.1) sobre los tres fondos de la sección:
 *
 * | | `#101010` | `#1C1C1E` | `#2E2E31` |
 * |---|---|---|---|
 * | [tinta] | 19,03 | 17,01 | 13,54 |
 * | [tintaApagada] | 10,13 | 9,06 | 7,21 |
 * | [tintaSutil] | 7,32 | 6,55 | 5,21 |
 * | [aviso] | 9,74 | 8,71 | 6,93 |
 */
object LiveTvPaleta {
	/**
	 * El fondo de la sección, y el de cualquier panel que se abra encima.
	 *
	 * Es exactamente el `background` del tema. Antes había dos negros y uno
	 * —`#0D0D0D`, el panel de controles— no era este; la diferencia no se ve de
	 * frente, pero sí donde el panel se recorta contra la pantalla.
	 */
	val fondo = Color(0xFF101010)

	/**
	 * El hueco de algo que todavía no está: la fila fantasma mientras llega la
	 * primera lista.
	 *
	 * Más apagado que el mosaico de una tarjeta de verdad, para que se lea como
	 * sitio reservado y no como una tarjeta negra.
	 */
	val hueco = Color(0xFF161618)

	/** El mosaico de una tarjeta en reposo, y la celda de la guía. */
	val mosaico = Color(0xFF1C1C1E)

	/** Lo que sube: tarjeta enfocada, pastilla, menú, distintivo. */
	val elevado = Color(0xFF2E2E31)

	/** Tinta principal: nombre del canal enfocado, título del programa, cifras. */
	val tinta = Color(0xFFFFFFFF)

	/**
	 * Tinta secundaria: nombre del canal sin foco, lo que viene luego, el
	 * cuerpo de los avisos.
	 *
	 * Coincide con [org.jellyfin.livetv.config.ACENTO_NEUTRO] a propósito: un
	 * site sin marca configurada pinta su progreso con esta misma tinta, así que
	 * la sección neutra queda monocroma en vez de medio teñida.
	 */
	val tintaApagada = Color(0xFFBDBDBD)

	/** Tinta terciaria: horas, etiquetas, pistas de tecla, detalle técnico. */
	val tintaSutil = Color(0xFF9DA1A6)

	/**
	 * Las mismas dos tintas secundarias **cuando lo que hay detrás es vídeo**.
	 *
	 * **[tintaApagada] y [tintaSutil] no sirven ahí, y está medido.** Se
	 * eligieron contra los tres fondos de la sección —el más claro, `#2E2E31`—
	 * y la tabla de arriba se cumple. Pero la barra de canal no vive sobre un
	 * fondo nuestro: vive sobre lo que emita el ISP, y eso puede ser blanco a
	 * pantalla completa. Medido el 30-oct sobre la captura de la carta de
	 * ajuste, con el velo que había: `#BDBDBD` daba **1,66:1** y `#9DA1A6`
	 * **1,58:1** donde van las horas. Un gris fijo no se arregla con más velo
	 * sin convertir la barra en una caja negra.
	 *
	 * La salida es que sobre vídeo la tinta sea **blanco con alfa**: se compone
	 * con lo que haya detrás, así que sube cuando el fondo sube y baja cuando
	 * baja. Con el velo nuevo (0,72 arriba, 0,90 abajo) y vídeo blanco debajo
	 * dan **6,28:1** y **4,68:1**, los dos por encima del 4,5 que pide la lista
	 * de comprobación para texto normal.
	 *
	 * **No sustituyen a las de arriba**, que siguen siendo las correctas en la
	 * rejilla, la guía y los paneles: ahí el fondo sí lo ponemos nosotros y un
	 * gris fijo es más estable que un alfa.
	 */
	val tintaApagadaSobreVideo = Color.White.copy(alpha = 0.78f)

	/** Ver [tintaApagadaSobreVideo]. */
	val tintaSutilSobreVideo = Color.White.copy(alpha = 0.62f)

	/**
	 * Algo no está como debería y el abonado puede entenderlo: lista guardada,
	 * degradación activa, aviso del operador.
	 *
	 * Había tres ámbares (`#E2B33C`, `#FFB300`, `#FFC74A`) para este mismo
	 * papel. Se queda el más usado; los tres pasaban el contraste, así que lo
	 * decidió la frecuencia y no la medida.
	 */
	val aviso = Color(0xFFE2B33C)

	/** El punto del directo. Es una marca, no un fondo de texto. */
	val enVivo = Color(0xFFE53935)

	/**
	 * El semáforo del programa en curso: verde hasta el 30 %, naranja hasta el
	 * 70 %, rojo a partir de ahí.
	 *
	 * **Petición de Edgar del 04-nov.** Lo que dice no es «esto va bien o va
	 * mal» —un programa avanzado no es un fallo— sino **cuánto te queda si
	 * entras ahora**: en rojo, lo que estés mirando se acaba enseguida. Es la
	 * decisión que alguien toma recorriendo una guía.
	 *
	 * **Los tres colores están medidos contra los tres surcos reales**, que es
	 * el fondo contra el que hay que distinguirlos, no contra el de la pantalla
	 * (WCAG 1.4.11 pide 3:1 para un elemento gráfico contra lo que tiene al
	 * lado). El surco de una ficha en reposo es `#333334`, el de una enfocada
	 * `#434346` y el de la barra `#383838`:
	 *
	 * | | ficha | ficha con foco | barra |
	 * |---|---|---|---|
	 * | [progresoTemprano] `#35C26A` | 5,45 | 4,26 | 5,07 |
	 * | [progresoMedio] `#F0883E` | 4,99 | 3,90 | 4,63 |
	 * | [progresoTarde] `#FF6B5E` | 4,52 | 3,53 | 4,20 |
	 *
	 * **El rojo no es [enVivo], y no por gusto: `#E53935` no pasa.** Da 2,99
	 * sobre el surco de una ficha en reposo y **2,33** sobre el de una
	 * enfocada, los dos por debajo del 3:1. Hizo falta subirlo. Que además no
	 * coincida con el punto del directo es conveniente: los dos salen juntos en
	 * la barra y significan cosas distintas.
	 *
	 * **El color no es el único canal**, y eso es lo que hace que esto no sea
	 * un problema de accesibilidad: lo largo de la barra ya dice lo mismo, así
	 * que quien no distinga rojo de verde no pierde información. El color la
	 * repite de un vistazo, que es para lo que sirve a tres metros.
	 */
	val progresoTemprano = Color(0xFF35C26A)

	/** Ver [progresoTemprano]. */
	val progresoMedio = Color(0xFFF0883E)

	/** Ver [progresoTemprano]. */
	val progresoTarde = Color(0xFFFF6B5E)

	/**
	 * El color que le toca a un avance de 0 a 1.
	 *
	 * Los cortes son los que pidió Edgar —30 y 70— y están aquí y no repartidos
	 * por la ficha y la barra: dos semáforos con umbrales distintos serían dos
	 * respuestas a la misma pregunta en la misma pantalla.
	 */
	fun colorDeProgreso(progreso: Float): Color = when {
		progreso <= CORTE_TEMPRANO -> progresoTemprano
		progreso <= CORTE_MEDIO -> progresoMedio
		else -> progresoTarde
	}

	private const val CORTE_TEMPRANO = 0.30f
	private const val CORTE_MEDIO = 0.70f

	/**
	 * El velo bajo las marcas de esquina de una tarjeta: número, HD, favorito
	 * y «sin señal».
	 *
	 * Caen encima del logo del canal, que puede ser casi blanco, y sin velo el
	 * número desaparece. Cuesta menos que recortar el logo.
	 *
	 * **Las cuatro marcas comparten fondo a propósito.** Antes «sin señal» iba
	 * sobre un rojo de error, y era el único sitio de la sección que trataba un
	 * canal caído como una avería: lo que distingue ese estado es que la
	 * tarjeta entera se atenúa, no que la etiqueta grite.
	 */
	val velo = Color(0x8C000000)
}
