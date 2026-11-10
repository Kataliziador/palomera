package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kataliziador.palomera.movil.catalogo.EnProgreso
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.kataliziador.palomera.movil.catalogo.Forma
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.Titulo
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import mx.kataliziador.palomera.movil.catalogo.EstadoDelCatalogo
import mx.kataliziador.palomera.movil.catalogo.estadoDelCatalogo

/**
 * Inicio con el catálogo del ISP de verdad.
 *
 * Separado de [PantallaInicio] a propósito: aquélla **no sabe de red**, recibe
 * listas y las pinta, y por eso se puede mirar sin servidor. Esto es la mitad
 * que habla con Jellyfin.
 */
@Composable
fun InicioConDatos(
	proveedor: String,
	acento: Color,
	jf: Jellyfin,
	alAbrir: (String) -> Unit,
	alAbrirAjustes: () -> Unit = {},
) {
	val datos by produceState<Datos?>(initialValue = null, jf) {
		// Las bibliotecas primero: son las que deciden qué carriles hay, y el
		// ISP ya eligió por nosotros al crearlas. El site de pruebas sirve 21.
		val bibliotecas = jf.bibliotecas().take(CARRILES_MAXIMOS)
		value = coroutineScope {
			// En paralelo: en serie, seis peticiones sobre una red de abonado
			// son seis esperas encadenadas en la primera pantalla.
			val progreso = async { jf.enProgreso() }
			val rieles = bibliotecas.map { b -> b.nombre to async { jf.ultimosDe(b.id) } }
			Datos(
				enProgreso = progreso.await(),
				carriles = rieles.mapNotNull { (nombre, pendiente) -> carril(nombre, pendiente.await(), jf) },
			)
		}
	}

	val d = datos

	// **Una pantalla vacía no explica nada, y eso es lo que había.** Con el
	// token revocado se veía negro entre la cabecera y la barra; el registro
	// decía `401` y el abonado no (medido el 10-nov en el emulador).
	if (d != null && d.enProgreso.isEmpty() && d.carriles.isEmpty()) {
		SinCatalogo(estadoDelCatalogo(hayContenido = false, ultimoCodigo = jf.ultimoCodigo))
		return
	}

	if (d == null) {
		Box(
			Modifier.fillMaxSize().background(Paleta.fondo).padding(32.dp),
			contentAlignment = Alignment.Center,
		) {
			BasicText(
				"Buscando tu catálogo…",
				style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
			)
		}
		return
	}

	PantallaInicio(
		proveedor = proveedor,
		acento = acento,
		enProgreso = d.enProgreso,
		carriles = d.carriles,
		alAbrirAjustes = alAbrirAjustes,
		urlDeProgreso = { id ->
			d.enProgreso.firstOrNull { it.id == id }
				?.let { jf.cartel(it.id, it.cartel, Forma.FONDO, 480) }
		},
		alAbrir = alAbrir,
	)
}

private data class Datos(
	val enProgreso: List<EnProgreso>,
	val carriles: List<Carril>,
)

/**
 * Cuántas bibliotecas se traen a Inicio.
 *
 * El site de pruebas sirve **21** —«Películas», «Series» y diecinueve
 * colecciones «Top …» del ISP—, y una petición por cada una es una primera
 * pantalla que tarda. Seis llenan de sobra el alto de un teléfono, y el resto
 * vive en Catálogo, que es donde se va a buscar algo concreto.
 */
private const val CARRILES_MAXIMOS = 6

/** Un carril, o nada si no hay qué poner: una fila vacía es peor que ninguna. */
private fun carril(nombre: String, titulos: List<Titulo>, jf: Jellyfin): Carril? =
	titulos.takeIf { it.isNotEmpty() }?.let { lista ->
		Carril(
			nombre,
			lista.map {
				Portada(
					id = it.id,
					titulo = it.nombre,
					tono = tonoDeCartel(it.id),
					// 336 px es el doble de los 168 dp de alto de la tarjeta:
					// a 2x o 3x de densidad, pedir 168 se vería blando.
					url = jf.cartel(it.id, it.cartel, Forma.CARTEL, 336),
				)
			},
		)
	}

/**
 * Lo que se enseña cuando no hay catálogo, y **por qué** no lo hay.
 *
 * Cada caso lleva a una acción distinta del abonado, así que no comparten
 * texto: mandar a reactivar a quien está suspendido le deja entrar sin pagar,
 * y mandar a mirar el router a quien tiene el token caducado le hace perder la
 * tarde.
 */
@Composable
private fun SinCatalogo(estado: EstadoDelCatalogo) {
	val (titulo, detalle) = when (estado) {
		EstadoDelCatalogo.SesionCaducada ->
			"Hay que activar este teléfono otra vez" to
				"Tu sesión ya no es válida. Cierra sesión desde Ajustes y vuelve a " +
					"activarlo con el código de tu proveedor."

		EstadoDelCatalogo.Suspendido ->
			"Tu servicio está suspendido" to
				"Llama a tu proveedor para reactivarlo. Volver a activar el teléfono " +
					"no lo soluciona."

		EstadoDelCatalogo.SinConexion ->
			"No podemos contactar con tu proveedor" to
				"Comprueba que tienes conexión y que estás en la red de tu proveedor. " +
					"Lo volveremos a intentar solo."

		EstadoDelCatalogo.Vacio ->
			"Todavía no hay nada que ver" to
				"Tu proveedor aún no ha publicado películas ni series. Los canales en " +
					"vivo sí pueden estar disponibles."

		// No se llega: con contenido no se pinta esta pantalla. Está por el
		// `when` exhaustivo, que es lo que avisa si mañana se añade un estado.
		EstadoDelCatalogo.Bien -> return
	}

	Column(
		Modifier.fillMaxSize().background(Paleta.fondo).padding(32.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		BasicText(
			titulo,
			style = Tipografia.titulo.copy(color = Paleta.tinta),
		)
		Spacer(Modifier.height(10.dp))
		BasicText(
			detalle,
			style = Tipografia.cuerpo.copy(color = Paleta.tintaApagada),
		)
	}
}
