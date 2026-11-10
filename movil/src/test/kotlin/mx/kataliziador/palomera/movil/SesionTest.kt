package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import mx.kataliziador.palomera.movil.acceso.Sesion
import mx.kataliziador.palomera.movil.acceso.aTexto
import mx.kataliziador.palomera.movil.acceso.leerSesion

/**
 * Lo que la app recuerda entre arranques.
 *
 * ## La regla que no se negocia
 *
 * **La contraseña no se guarda nunca, ni cifrada.** No hace falta: el token
 * sirve para todo y se puede revocar desde el servidor; una contraseña
 * guardada no se revoca, vale en todos los sites donde el abonado reusó la
 * misma, y convierte un teléfono perdido en un problema del proveedor.
 *
 * El tipo [Sesion] no tiene campo donde ponerla, que es la única forma de que
 * no acabe ahí dentro por descuido dentro de seis meses.
 */
class SesionTest : FunSpec({

	val s = Sesion(
		servidor = "http://192.168.1.10:8096",
		token = "tok-999",
		usuarioId = "abc123",
		nombre = "probador",
		proveedor = "RedTV",
		servidorId = "447eb6f8",
	)

	test("una sesion guardada se vuelve a leer igual") {
		leerSesion(aTexto(s)) shouldBe s
	}

	test("lo guardado no contiene ninguna contraseña") {
		// Es una prueba sobre el texto que de verdad toca el disco, no sobre
		// la intención: si alguien añade el campo, esto se cae.
		val texto = aTexto(s)
		texto shouldNotContain "assword"
		texto shouldNotContain "clave"
	}

	test("sin sesion guardada no se inventa una") {
		leerSesion(null) shouldBe null
		leerSesion("") shouldBe null
		leerSesion("no es json") shouldBe null
	}

	test("una sesion a medias no se usa") {
		// Un token sin servidor no lleva a ninguna parte, y entrar con él
		// dejaría a la app pidiendo a `null`. Vale más volver a la puerta.
		leerSesion("""{"token":"t","usuarioId":"u"}""") shouldBe null
		leerSesion("""{"servidor":"http://x","usuarioId":"u"}""") shouldBe null
		leerSesion("""{"servidor":"http://x","token":"t"}""") shouldBe null
	}

	test("el id del servidor se guarda, porque es la clave de la cache") {
		// **No se indexa por dirección** (`CLAUDE.md` §6): un site que cambia
		// de dominio no debe perder su lista, y uno que cambia de site no debe
		// heredar la del anterior. La dirección sirve para las dos cosas mal.
		leerSesion(aTexto(s))?.servidorId shouldBe "447eb6f8"
	}

	test("el nombre del proveedor puede faltar, y no impide entrar") {
		// La marca es un adorno; la sesión es el servicio. Un site sin marca
		// configurada no puede dejar al abonado fuera.
		val sinMarca = """{"servidor":"http://x","token":"t","usuarioId":"u"}"""
		leerSesion(sinMarca)?.proveedor shouldBe null
		leerSesion(sinMarca)?.token shouldBe "t"
	}
})
