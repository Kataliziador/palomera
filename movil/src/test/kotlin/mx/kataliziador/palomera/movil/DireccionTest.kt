package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.acceso.normalizarDireccion

/**
 * Lo que el abonado escribe, convertido en una dirección que funciona.
 *
 * ## Por qué esto tiene pruebas y no es un `trim()`
 *
 * Es la ruta 3 del selector: la que usa **el revisor de Google** y la única
 * que funciona sin llegar a internet (`docs/selector-de-proveedor.md` §3). Si
 * falla, falla para el revisor, y el síntoma que él ve es «la app no conecta».
 *
 * Y lo que la gente escribe de verdad no lleva esquema ni puerto: teclean
 * `192.168.1.10` porque es lo que les dictaron por teléfono. Rechazar eso con
 * «dirección no válida» sería culpar al abonado de que falta algo que nosotros
 * sabemos poner.
 */
class DireccionTest : FunSpec({

	test("una direccion completa se respeta tal cual") {
		normalizarDireccion("http://192.168.1.10:8096") shouldBe "http://192.168.1.10:8096"
		normalizarDireccion("https://tv.miproveedor.mx:8920") shouldBe "https://tv.miproveedor.mx:8920"
	}

	test("sin esquema se asume http, que es lo que hay en una LAN") {
		// **No https.** El Jellyfin de un site vive en la LAN del ISP y casi
		// nunca tiene certificado; asumir https dejaría la ruta 3 inservible
		// justo para quien la necesita.
		normalizarDireccion("192.168.1.10:8096") shouldBe "http://192.168.1.10:8096"
	}

	test("sin puerto se asume el 8096, que es el de Jellyfin") {
		normalizarDireccion("192.168.1.10") shouldBe "http://192.168.1.10:8096"
		normalizarDireccion("http://tv.miproveedor.mx") shouldBe "http://tv.miproveedor.mx:8096"
	}

	test("pero un https sin puerto se deja en el 443") {
		// Un site con TLS está detrás de un proxy en el 443. Meterle el 8096
		// lo mandaría a un puerto cerrado.
		normalizarDireccion("https://tv.miproveedor.mx") shouldBe "https://tv.miproveedor.mx"
	}

	test("se perdonan los espacios y la barra final") {
		normalizarDireccion("  192.168.1.10:8096/  ") shouldBe "http://192.168.1.10:8096"
	}

	test("una ruta escrita a mano se conserva") {
		// Jellyfin puede vivir detrás de un proxy en un subdirectorio
		// (`CLAUDE.md` §6 lo nombra). Tirar la ruta dejaría la app pidiendo a
		// la raíz del dominio, que contesta otra cosa.
		normalizarDireccion("https://tv.isp.mx/jellyfin") shouldBe "https://tv.isp.mx/jellyfin"
	}

	test("lo que no puede ser una direccion se rechaza") {
		// Devolver `null` deja que la pantalla lo diga antes de pedir la
		// contraseña, en vez de que el fallo salga después de teclearla.
		normalizarDireccion("") shouldBe null
		normalizarDireccion("   ") shouldBe null
		normalizarDireccion("no es una dirección") shouldBe null
		normalizarDireccion("ftp://192.168.1.10") shouldBe null
	}
})
