package org.jellyfin.androidtv.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.model.LiberarTele
import org.jellyfin.androidtv.auth.model.SalidaDeCuenta
import org.jellyfin.androidtv.auth.model.decidirLiberarTele

/**
 * Qué salidas devuelven la tele al cupo de la cuenta, y cuáles no.
 *
 * ## Por qué esto es una función y no un `if` dentro del repositorio
 *
 * El fallo que arregla se midió en producción el 09-nov con una caja Roku: al
 * cerrar sesión, la app olvidaba el token **sólo en la caja** y nunca avisaba a
 * Jellyfin, así que la sesión seguía ocupando una de las teles que permite
 * `MaxActiveSessions`. Al reactivar esa misma caja, Jellyfin contestaba 403
 * —cuenta antes de limpiar— y el panel lo traducía como «la clave no tiene
 * permiso». **La misma caja chocaba contra su propio fantasma.**
 *
 * Lo que hay que decidir es corto —qué acciones liberan— pero equivocarse tiene
 * dos precios distintos y los dos son caros:
 *
 * - **liberar de menos** deja al abonado sin poder reactivar su propia caja;
 * - **liberar de más** echa de Jellyfin a una caja que seguía en uso.
 *
 * Por eso vive aparte y con pruebas, igual que [SessionFailureTests]: lo que
 * protege no es el código, es la tabla.
 */
class LiberarTeleTests : FunSpec({
	test("cerrar sesión libera la tele: es lo que faltaba y causó el 403") {
		decidirLiberarTele(SalidaDeCuenta.CERRAR_SESION, tieneToken = true) shouldBe
			LiberarTele.Libera
	}

	test("borrar el servidor libera la tele de cada usuario que tuviera token") {
		decidirLiberarTele(SalidaDeCuenta.BORRAR_SERVIDOR, tieneToken = true) shouldBe
			LiberarTele.Libera
	}

	test("cambiar de usuario NO libera: la caja conserva el perfil y sigue siendo una tele de la cuenta") {
		// Hoy ninguna ruta de cambio de usuario llama a `logout()`, así que esto
		// se cumple por construcción. La prueba existe para que siga siendo
		// verdad: cablear la liberación a esa ruta echaría de Jellyfin a una
		// caja que no se ha ido a ninguna parte.
		decidirLiberarTele(SalidaDeCuenta.CAMBIAR_DE_USUARIO, tieneToken = true) shouldBe
			LiberarTele.NoLibera("la caja conserva el perfil")
	}

	test("sin token no hay nada que liberar, haga lo que haga el abonado") {
		// El `POST /Sessions/Logout` se autentica **con el token de esa misma
		// sesión**. Sin él no hay a quién avisar ni con qué, y un intento a
		// ciegas sería una llamada que no puede funcionar.
		for (salida in SalidaDeCuenta.entries) {
			decidirLiberarTele(salida, tieneToken = false) shouldBe
				LiberarTele.NoLibera("sin token que liberar")
		}
	}

	test("los dos motivos de no liberar se distinguen en el log") {
		// Sin motivos distintos, una serie de «no liberada» no dice si fue la
		// regla del perfil o una sesión que ya estaba vacía — y son cosas
		// opuestas al leer por qué una caja no puede reactivarse.
		val porPerfil = decidirLiberarTele(SalidaDeCuenta.CAMBIAR_DE_USUARIO, tieneToken = true)
		val porFaltaDeToken = decidirLiberarTele(SalidaDeCuenta.CERRAR_SESION, tieneToken = false)

		(porPerfil as LiberarTele.NoLibera).motivo shouldBe "la caja conserva el perfil"
		(porFaltaDeToken as LiberarTele.NoLibera).motivo shouldBe "sin token que liberar"
		(porPerfil.motivo == porFaltaDeToken.motivo) shouldBe false
	}
})
