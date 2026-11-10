package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import mx.kataliziador.palomera.movil.catalogo.EstadoDelCatalogo
import mx.kataliziador.palomera.movil.catalogo.estadoDelCatalogo

/**
 * Qué se le dice al abonado cuando no hay nada que enseñar.
 *
 * ## Lo medido el 10-nov en el emulador
 *
 * Con el token revocado, la app enseñaba **una pantalla completamente vacía**:
 * la cabecera, la barra de abajo y negro en medio. El registro sí lo sabía
 * —`catalogo.noContesta codigo=401`—, pero el abonado no, y lo único que puede
 * hacer con eso es llamar a soporte. Con el teléfono sin red, exactamente la
 * misma pantalla.
 *
 * ## Son tres cosas distintas y llevan a tres acciones distintas
 *
 * | | qué pasó | qué puede hacer |
 * |---|---|---|
 * | **401** | el token ya no vale | volver a activar el teléfono |
 * | **403** | el contrato está suspendido | llamar a pagar |
 * | **sin respuesta** | sin red, o el site caído | esperar y reintentar |
 * | **vacío de verdad** | el servidor contestó y no hay nada | nada, y está bien |
 *
 * Es la misma tabla que la de [Entrada], y existe aquí porque **entrar y
 * seguir dentro son momentos distintos**: la de la puerta no se aplicaba
 * después de entrar, que es justo donde falló.
 */
class EstadoDelCatalogoTest : FunSpec({

	test("con contenido, no hay nada que explicar") {
		estadoDelCatalogo(hayContenido = true, ultimoCodigo = null) shouldBe EstadoDelCatalogo.Bien
		// Un 401 suelto no tapa un catálogo que sí llegó: puede ser una
		// petición secundaria que falló mientras el resto funcionaba.
		estadoDelCatalogo(hayContenido = true, ultimoCodigo = 401) shouldBe EstadoDelCatalogo.Bien
	}

	test("401 es volver a activar, no un error generico") {
		estadoDelCatalogo(hayContenido = false, ultimoCodigo = 401) shouldBe
			EstadoDelCatalogo.SesionCaducada
	}

	test("403 es el servicio suspendido, y no manda a reactivar") {
		// Reactivar con un código nuevo deja entrar a un suspendido. Es el
		// mismo daño que en la puerta, por la otra punta.
		estadoDelCatalogo(hayContenido = false, ultimoCodigo = 403) shouldBe
			EstadoDelCatalogo.Suspendido
	}

	test("sin respuesta no se acusa a nadie") {
		// `null` es «ni siquiera hubo respuesta»: el teléfono sin red o el
		// site caído. Decirle que su sesión caducó le haría volver a activar
		// un teléfono que no tiene nada roto.
		estadoDelCatalogo(hayContenido = false, ultimoCodigo = null) shouldBe
			EstadoDelCatalogo.SinConexion
		estadoDelCatalogo(hayContenido = false, ultimoCodigo = 500) shouldBe
			EstadoDelCatalogo.SinConexion
	}

	test("un catalogo vacio de verdad se distingue de un fallo") {
		// El servidor contestó bien y no hay títulos. Pasa en un site recién
		// montado, y enseñar ahí «comprueba tu conexión» mandaría al abonado a
		// mirar su router por algo que no es suyo.
		estadoDelCatalogo(hayContenido = false, ultimoCodigo = 200) shouldBe
			EstadoDelCatalogo.Vacio
	}
})
