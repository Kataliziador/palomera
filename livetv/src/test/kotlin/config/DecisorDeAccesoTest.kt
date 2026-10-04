package org.jellyfin.livetv.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jellyfin.livetv.LiveTuning

/**
 * La escalera de degradación del registro, entera y sin red.
 *
 * Es la parte de B9 que decide si alguien ve televisión, y la que no se puede
 * probar cómodamente contra un servicio de verdad: haría falta un registro
 * caído a voluntad y relojes movidos. Aquí son datos.
 */
class DecisorDeAccesoTest : StringSpec({

	val ahora = java.time.Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
	val dia = 24L * 60 * 60 * 1000

	fun Acceso.permitido(): Acceso.Permitido {
		(this is Acceso.Permitido) shouldBe true
		return this as Acceso.Permitido
	}

	fun Acceso.denegado(): Acceso.Denegado {
		(this is Acceso.Denegado) shouldBe true
		return this as Acceso.Denegado
	}

	fun guardada(vigenteHastaMs: Long, autorizada: Boolean = true) = SiteAuthorization(
		host = "redtv.ispjelly.net",
		authorized = autorizada,
		untilEpochMs = vigenteHastaMs,
		obtenidaEnMs = ahora - 30 * dia,
	)

	"si contesta autorizando, se entra sin aviso" {
		val a = DecisorDeAcceso.decidir(null, guardada(ahora + 20 * dia), ahora)
		a.permitido().aviso.shouldBeNull()
	}

	"si contesta denegando, se cierra con su mensaje tal cual" {
		val respuesta = SiteAuthorization(
			host = "x", authorized = false, untilEpochMs = null,
			message = "Contacta a tu proveedor, no estás dado de alta.",
		)
		DecisorDeAcceso.decidir(null, respuesta, ahora)
			.denegado().mensaje shouldBe "Contacta a tu proveedor, no estás dado de alta."
	}

	"si deniega sin mensaje, hay uno genérico: nunca un error en blanco" {
		val respuesta = SiteAuthorization(host = "x", authorized = false, untilEpochMs = null)
		DecisorDeAcceso.decidir(null, respuesta, ahora)
			.denegado().mensaje shouldBe DecisorDeAcceso.DENEGADO_GENERICO
	}

	"no contesta y la autorización sigue viva: se entra, y sin avisar de nada" {
		// No negociable: un fallo nuestro no puede apagar televisiones.
		val a = DecisorDeAcceso.decidir(guardada(ahora + 5 * dia), null, ahora)
		a.permitido().aviso.shouldBeNull()
	}

	"no contesta y caducó hace poco: se entra avisando" {
		val a = DecisorDeAcceso.decidir(guardada(ahora - 3 * dia), null, ahora)
		a.permitido().aviso shouldBe
			"No se ha podido verificar el servicio. Sigue funcionando por ahora."
	}

	"el último día de gracia todavía se entra" {
		val justoDentro = ahora - LiveTuning.REGISTRY_GRACE_MS + 1
		DecisorDeAcceso.decidir(guardada(justoDentro), null, ahora)
			.permitido()
	}

	"pasada la gracia se cierra, y se explica" {
		val justoFuera = ahora - LiveTuning.REGISTRY_GRACE_MS
		val a = DecisorDeAcceso.decidir(guardada(justoFuera), null, ahora)
		// El texto habla de la conexión de la caja, no de una licencia: es lo
		// que de verdad pasa, y es lo que el abonado puede comprobar.
		a.denegado().mensaje.contains("internet") shouldBe true
	}

	"no contesta y nunca hubo respuesta: se entra avisando" {
		// Una caja recién instalada que alcanza la red del ISP pero no la nube.
		// Cerrar aquí dejaría muerto ese despliegue desde el primer día.
		val a = DecisorDeAcceso.decidir(null, null, ahora)
		a.permitido().motivo shouldBe "sin respuesta y sin autorización previa"
	}

	"una denegación guardada no autoriza aunque no haya caducado" {
		// `vigente` exige `authorized`; sin esto, un «no» antiguo se leería
		// como un «sí» mientras durase su fecha.
		val a = DecisorDeAcceso.decidir(guardada(ahora + 5 * dia, autorizada = false), null, ahora)
		a.permitido().motivo shouldBe "sin respuesta y sin autorización previa"
	}
})
