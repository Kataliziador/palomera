package org.jellyfin.androidtv.auth.model

import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException

/**
 * Por qué el Jellyfin del ISP no aceptó una sesión ya guardada.
 *
 * Existe porque los tres casos **no se parecen en nada** para el abonado y la
 * app los trataba igual: `SessionRepository` atrapaba `ApiClientException` a
 * secas y los tres acababan en la pantalla del código.
 *
 * Medido el 28-oct contra Jellyfin **10.11.11 y 12.0.0**, tres llamadas por
 * estado y cuatro rutas, reutilizando el token de una caja ya autenticada
 * —o sea sin volver a entrar, que es lo que prueba que la política se evalúa
 * en cada petición y no sólo al autenticarse—:
 *
 * | Estado del abonado en Jellyfin | `GET /Users/Me` | su dispositivo |
 * |---|---|---|
 * | activo | 200 | registrado |
 * | suspendido (horario de acceso que no cubre ninguna hora) | **403** | sigue registrado |
 * | reactivado, con el mismo token | 200 | registrado |
 * | dado de baja (`IsDisabled`) | **401**, también tras reactivar | borrado |
 *
 * Así que **403 quiere decir «vuelve sola en cuanto pague»** y 401 quiere decir
 * «hay que dar de alta la caja otra vez». Enseñar la pantalla del código en el
 * primer caso manda al abonado a pedirle un código a su proveedor cuando lo que
 * tiene que hacer es pagar; y si se lo dan, la caja entra con otra sesión y el
 * panel de abonados ve un dispositivo de más.
 *
 * **Al administrador no le aplica** (una muestra, 12.0.0): un token suyo que ya
 * existía siguió contestando 200 con la suspensión puesta, y sólo el inicio de
 * sesión nuevo dio 403. Sólo importaría si un site le diera permisos de
 * administrador a un abonado, que es justo lo que evita dar de alta desde el
 * panel (`panel-abonados/docs/DISENO.md` §6.1).
 */
enum class SessionFailure {
	/** 403: el contrato está suspendido. El token sigue vivo y volverá a valer. */
	SUSPENDED,

	/** Nadie contestó. **No es un corte**: puede ser el site apagado o sin red. */
	UNREACHABLE,

	/** 401 y todo lo demás: la credencial ya no sirve y hay que activar la caja. */
	INVALID,
}

/**
 * Clasifica lo que lanzó el SDK.
 *
 * El mapeo está **leído del bytecode** de `jellyfin-api-okhttp-jvm` 1.7.1, no
 * supuesto: `ConnectException`, `InterruptedIOException` y
 * `UnknownHostException` se convierten **las tres** en [TimeoutException], y
 * cualquier respuesta que no sea 2xx en [InvalidStatusException], que es la
 * única que trae el código.
 *
 * Por eso [SessionFailure.UNREACHABLE] cubre también «conexión rechazada» y
 * «sin DNS», que es el caso de una caja encendida antes que su router: hasta
 * hoy perdía la cuenta igual que una dada de baja.
 */
fun ApiClientException.asSessionFailure(): SessionFailure = when {
	this is TimeoutException -> SessionFailure.UNREACHABLE
	this is InvalidStatusException && status == 403 -> SessionFailure.SUSPENDED
	else -> SessionFailure.INVALID
}
