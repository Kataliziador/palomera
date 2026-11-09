package mx.kataliziador.palomera.movil.arranque

import android.content.res.Configuration

/** Qué interfaz toca enseñar. */
enum class Aparato {
	/** Diez pies y un mando: la interfaz de `app/`. */
	TELEVISOR,

	/** Treinta centímetros y un dedo: la de `movil/`. */
	TELEFONO,
}

/**
 * Decide qué interfaz enseña la app, **sin preguntarle nada al abonado**.
 *
 * ## Por qué no se reusa `isTvDevice()` de la app de TV
 *
 * Existe, en `util/ContextExtensions.kt:27`, y está bien **para lo que se
 * escribió**: avisar de que «esto no parece una tele». Ahí equivocarse hacia
 * «es TV» no cuesta nada.
 *
 * Colgarle la interfaz entera cambia el sesgo, y esa regla tiene dos cosas que
 * aquí hacen daño: cuenta `UI_MODE_TYPE_UNDEFINED` como televisión, y une sus
 * señales con `or`, así que basta una para decidir. Un aparato que no sepa
 * decir qué es acabaría con una interfaz de cruceta sobre una pantalla táctil.
 *
 * ## Hacia dónde se equivoca ésta, y por qué
 *
 * **Hacia el teléfono.** El motivo es asimétrico y no estético: una tele de
 * verdad **casi siempre se declara bien** —`UI_MODE_TYPE_TELEVISION` o
 * `leanback`, que es como funciona Android TV y lo que Play exige para el
 * formato—, mientras que el aparato raro que no se declara es casi siempre un
 * teléfono. Dudar hacia el teléfono arregla el caso que ocurre y no cuesta
 * nada en el que no.
 *
 * Y para el aparato que miente está [forzadoPorAjustes], que es la misma
 * filosofía que `deviceOverrides` en el motor: decide la caja, y nosotros
 * tenemos palanca.
 *
 * @param modoDeInterfaz lo que devuelve `UiModeManager.getCurrentModeType()`.
 * @param declaraLeanback si el sistema trae `android.software.leanback`. Es
 *   del **aparato**, no de nuestro manifiesto.
 * @param tienePantallaTactil si el sistema trae `android.hardware.touchscreen`.
 * @param forzadoPorAjustes lo que el abonado o un técnico eligió a mano, o
 *   `null` si nadie ha tocado nada, que es lo normal.
 */
fun decidirAparato(
	modoDeInterfaz: Int,
	declaraLeanback: Boolean,
	tienePantallaTactil: Boolean,
	forzadoPorAjustes: Aparato?,
): Aparato {
	// La palanca manda, y manda en los dos sentidos.
	if (forzadoPorAjustes != null) return forzadoPorAjustes

	// La señal más fiable: la declara el sistema, no se deduce.
	if (modoDeInterfaz == Configuration.UI_MODE_TYPE_TELEVISION) return Aparato.TELEVISOR

	// El lanzador de TV sólo está en un aparato de TV.
	if (declaraLeanback) return Aparato.TELEVISOR

	// Sin pantalla táctil no hay interfaz táctil posible, declare lo que declare.
	if (!tienePantallaTactil) return Aparato.TELEVISOR

	return Aparato.TELEFONO
}
