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
 * del sistema**: §4.2 manda que la sección se vea como el resto de Palco, y el
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

	/** Fondo del distintivo «sin señal» de una tarjeta. */
	val noDisponible = Color(0xCC8A1C1C)
}
