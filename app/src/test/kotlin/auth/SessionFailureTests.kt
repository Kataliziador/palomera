package org.jellyfin.androidtv.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.model.SessionFailure
import org.jellyfin.androidtv.auth.model.asSessionFailure
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException

/**
 * Las tres respuestas que no se podían distinguir.
 *
 * Esto prueba la clasificación, que es donde estaba el fallo: la app atrapaba
 * `ApiClientException` a secas y mandaba los tres casos a la pantalla del
 * código. Los códigos de la tabla están medidos el 28-oct contra Jellyfin
 * 10.11.11 y 12.0.0 (ver `SessionFailure`).
 */
class SessionFailureTests : FunSpec({
	test("403 es una suspensión: el token sigue valiendo y la caja volverá sola") {
		InvalidStatusException(403, null).asSessionFailure() shouldBe SessionFailure.SUSPENDED
	}

	test("401 es una baja: hay que activar la caja otra vez") {
		InvalidStatusException(401, null).asSessionFailure() shouldBe SessionFailure.INVALID
	}

	test("el resto de códigos no se confunden con una suspensión") {
		for (codigo in listOf(400, 404, 429, 500, 502, 503)) {
			InvalidStatusException(codigo, null).asSessionFailure() shouldBe SessionFailure.INVALID
		}
	}

	test("que no conteste nadie no es un corte") {
		// El SDK convierte `ConnectException`, `InterruptedIOException` y
		// `UnknownHostException` en esta misma excepción —leído del bytecode de
		// `jellyfin-api-okhttp-jvm` 1.7.1—, así que este caso cubre también la caja
		// encendida antes que su router, que hasta hoy perdía la cuenta.
		TimeoutException("agotado", null).asSessionFailure() shouldBe SessionFailure.UNREACHABLE
	}

	test("un fallo de TLS tampoco se toma por una suspensión") {
		SecureConnectionException("tls", null).asSessionFailure() shouldBe SessionFailure.INVALID
	}

	test("una excepción genérica cae en el caso de siempre") {
		ApiClientException("vacío", null).asSessionFailure() shouldBe SessionFailure.INVALID
	}
})
