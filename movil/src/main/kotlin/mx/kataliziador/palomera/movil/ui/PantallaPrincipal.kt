package mx.kataliziador.palomera.movil.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import mx.kataliziador.palomera.movil.acceso.Sesion
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.Reproduccion
import mx.kataliziador.palomera.movil.catalogo.seReporta
import mx.kataliziador.palomera.movil.catalogo.idDeInstalacion
import mx.kataliziador.palomera.movil.catalogo.perfilDeDispositivo
import timber.log.Timber
import org.jellyfin.livetv.config.Brand
import org.jellyfin.livetv.config.ConfigRepository
import org.jellyfin.livetv.config.LiveConfig
import org.jellyfin.livetv.capabilities.DeviceCapabilities
import mx.kataliziador.palomera.movil.ui.tema.Paleta
import mx.kataliziador.palomera.movil.ui.tema.Tipografia

/**
 * La app: las cuatro secciones y la ficha que se abre encima.
 *
 * El estado vive aquí y no en cada pantalla, porque **una ficha es un destino
 * y no una sección**: se abre sobre lo que hubiera y al volver deja la sección
 * donde estaba. Sin esto, salir de una ficha devolvería siempre a Inicio y el
 * abonado perdería la fila por la que iba.
 *
 * No hay librería de navegación: con cuatro destinos y una ficha, un `when`
 * sobre dos estados es menos código y menos cosas que puedan fallar.
 */
@Composable
fun PantallaPrincipal(
	proveedor: String,
	acento: Color,
	jf: Jellyfin,
	configUrl: String?,
	/** Con qué nombre se guarda la lista de este site. Ver `destinoDeCanales`. */
	claveDeCache: String = "sin-servidor",
	/** La sesión abierta, para los ajustes. `null` con un token de `adb`. */
	sesion: Sesion? = null,
	alCerrarSesion: () -> Unit = {},
	// Para abrir una sección directamente desde `adb`. Existe porque el
	// teléfono de pruebas tiene denegada la inyección de eventos, así que no se
	// puede tocar una pestaña desde aquí; sin esto, sólo se puede mirar Inicio.
	seccionInicial: Seccion = Seccion.INICIO,
	fichaInicial: String? = null,
	// Reproduce un título directamente, sin pasar por la ficha. Es para `adb`:
	// el teléfono de pruebas no deja inyectar toques, así que sin esto no hay
	// forma de llegar al reproductor desde aquí.
	reproducirAlArrancar: String? = null,
	/**
	 * Sólo para desarrollo: cerrar el vídeo solo a los N segundos.
	 *
	 * Existe porque **en el teléfono de pruebas `INJECT_EVENTS` está denegado**
	 * por HyperOS, así que no hay forma de pulsar «atrás» desde `adb`. Sin esto,
	 * el único camino de salida del reproductor —y con él el aviso de fin que
	 * guarda la posición— no se puede ejercitar sin una persona delante.
	 */
	cerrarVideoEnS: Int? = null,
) {
	// **`rememberSaveable` y no `remember`.** Android recrea la actividad por
	// su cuenta —poca memoria, el abonado cambia de app y vuelve, un ajuste
	// del sistema— y con `remember` todo esto se pierde: la sección, la ficha
	// abierta. El abonado volvía a Inicio sin haber tocado nada.
	var seccion by rememberSaveable { mutableStateOf(seccionInicial) }
	var fichaAbierta by rememberSaveable { mutableStateOf(fichaInicial) }
	var video by remember { mutableStateOf<Video?>(null) }
	var enAjustes by rememberSaveable { mutableStateOf(false) }

	// **El botón atrás de Android tiene que hacer algo aquí.** Sin esto sale
	// de la app desde cualquier pantalla —ficha, reproductor, ajustes—, y en
	// un teléfono atrás es el gesto que más se usa: nadie busca la flecha de
	// la esquina, se desliza desde el borde. Medido el 10-nov en el emulador:
	// dos «atrás» desde el reproductor dejaban al abonado en el marcador del
	// teléfono.
	//
	// El orden deshace lo último que se abrió, que es lo que la pantalla
	// enseña encima. Sólo en Inicio se deja pasar, porque ahí salir **es** lo
	// correcto y atraparlo dejaría una app de la que no se puede salir.
	BackHandler(enabled = video != null) { video = null }
	BackHandler(enabled = video == null && enAjustes) { enAjustes = false }
	BackHandler(enabled = video == null && !enAjustes && fichaAbierta != null) {
		fichaAbierta = null
	}
	BackHandler(
		enabled = video == null && !enAjustes && fichaAbierta == null &&
			seccion != Seccion.INICIO,
	) {
		seccion = Seccion.INICIO
	}

	// El perfil del aparato se calcula una vez: leer `MediaCodecList` en cada
	// reproducción sería preguntar lo mismo al sistema una y otra vez.
	val contexto = LocalContext.current
	val perfil = remember { perfilDeDispositivo(DeviceCapabilities.of(contexto)) }

	// **La marca del ISP, leída una vez y aplicada a toda la app.**
	// Sale del servicio de canales del site y **no del Jellyfin**: si saliera
	// de ahí, cualquiera que apunte la app a un Jellyfin propio podría vestirla
	// (`CLAUDE.md` §8). Mientras no llega, se usa lo que vino de la sesión:
	// una pantalla medio teñida que luego cambia de color se ve como un fallo.
	// Una sola carga para las dos cosas que salen de ahí: la marca y la lista
	// de canales. Dos lecturas del mismo archivo serían dos cachés y dos
	// momentos distintos en que la pantalla cambia de aspecto.
	val config by produceState(initialValue = null as LiveConfig?, configUrl, claveDeCache) {
		value = ConfigRepository(contexto).load(configUrl, claveDeCache).config
	}
	val acentoVivo = config?.brand?.accent?.let { Color(it) } ?: acento
	// **El nombre del proveedor gana al neutro, y ésa es la corrección.**
	// `BrandParser` nunca devuelve `null`: si el site no configura
	// `displayName`, entrega el de `Brand.NEUTRA` —«TV en vivo»—, así que
	// tomarlo sin mirar hacía que el nombre que el registro ya nos había dado
	// no se usara jamás. Medido el 10-nov: el site de los probadores trae
	// `accent` y no `displayName`, y la cabecera decía «TV en vivo» teniendo
	// «Palomera Probadores» a mano.
	val delSite = config?.brand?.displayName?.takeIf { it != Brand.NEUTRA.displayName }
	val nombreVivo = delSite ?: proveedor

	// El vídeo va encima de todo, y al salir devuelve donde estaba.
	LaunchedEffect(reproducirAlArrancar) {
		val id = reproducirAlArrancar ?: return@LaunchedEffect
		val d = jf.detalle(id)
		when (val r = jf.comoReproducir(id, perfil)) {
			is Reproduccion.Directo -> video = Video(r.url, d?.nombre.orEmpty(), false, id)
			is Reproduccion.Transcodificado -> {
				Timber.i("vod.transcodifica titulo=%s motivo=%s", d?.nombre, r.motivo)
				video = Video(r.url, d?.nombre.orEmpty(), false, id)
			}
			Reproduccion.NoSePuede -> Timber.w("vod.noSePuede id=%s", id)
		}
	}

	// Ver arriba: es el sustituto de un dedo.
	LaunchedEffect(video, cerrarVideoEnS) {
		val segundos = cerrarVideoEnS ?: return@LaunchedEffect
		if (video == null) return@LaunchedEffect
		delay(segundos * 1_000L)
		video = null
	}

	video?.let { v ->
		PantallaVideo(
			url = v.url,
			titulo = v.titulo,
			enDirecto = v.enDirecto,
			avisar = { aviso, posicion, enPausa ->
				if (seReporta(v.enDirecto, v.itemId)) {
					jf.avisar(aviso, v.itemId!!, posicion, enPausa)
				}
			},
			alVolver = { video = null },
		)
		return
	}

	if (enAjustes && sesion != null) {
		PantallaAjustes(
			sesion = sesion,
			acento = acentoVivo,
			idDeInstalacion = contexto.idDeInstalacion(),
			alCerrarSesion = alCerrarSesion,
			alVolver = { enAjustes = false },
		)
		return
	}

	val abierta = fichaAbierta
	if (abierta != null) {
		PantallaFicha(
			jf = jf,
			id = abierta,
			acento = acentoVivo,
			perfil = perfil,
			alVolver = { fichaAbierta = null },
			alReproducir = { url, titulo -> video = Video(url, titulo, enDirecto = false, itemId = abierta) },
		)
		return
	}

	Column(
		Modifier
			.fillMaxSize()
			.background(Paleta.fondo)
			.windowInsetsPadding(WindowInsets.safeDrawing),
	) {
		Box(Modifier.weight(1f)) {
			when (seccion) {
				Seccion.INICIO -> InicioConDatos(
					proveedor = nombreVivo,
					acento = acentoVivo,
					jf = jf,
					alAbrir = { fichaAbierta = it },
					alAbrirAjustes = { enAjustes = true },
				)
				Seccion.CATALOGO -> PantallaCatalogo(jf, acentoVivo) { fichaAbierta = it }
				Seccion.EN_VIVO ->
					if (configUrl.isNullOrBlank()) Pendiente("Falta la dirección del servicio de canales.")
					else PantallaEnVivo(configUrl) { url, nombre ->
						video = Video(url, nombre, enDirecto = true)
					}
				Seccion.BUSCAR -> PantallaBuscar(
					jf = jf,
					acento = acentoVivo,
					// Si el site no trae lista, la búsqueda sigue sirviendo
					// para el catálogo: media búsqueda es mejor que ninguna.
					canales = config?.channels.orEmpty(),
					alAbrirTitulo = { fichaAbierta = it },
					alAbrirCanal = { canal ->
						video = Video(canal.url, canal.name, enDirecto = true)
					},
				)
			}
		}
		BarraInferior(seccion, acentoVivo) { seccion = it }
	}
}

/**
 * Lo que se está viendo.
 *
 * [itemId] es lo que permite avisar al servidor; un canal en vivo no lo tiene y
 * no se reporta ([seReporta]).
 */
private data class Video(
	val url: String,
	val titulo: String,
	val enDirecto: Boolean,
	val itemId: String? = null,
)

@Composable
private fun Pendiente(texto: String) {
	Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
		BasicText(texto, style = Tipografia.cuerpo.copy(color = Paleta.tintaSutil))
	}
}
