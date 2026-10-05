package org.jellyfin.androidtv.ui.livetv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.livetv.LiveTuning
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ShapeDefaults
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.livetv.config.Acceso
import org.jellyfin.livetv.config.ConfigOrigin
import org.jellyfin.livetv.diagnostics.DevOptions
import org.jellyfin.livetv.source.ChannelProfile
import org.jellyfin.livetv.source.ChannelSearch
import org.koin.android.ext.android.inject

/**
 * La sección de canales en vivo, reescrita desde cero.
 *
 * Reemplaza la sección LiveTV de Jellyfin (`CLAUDE.md` §4, regla 5): esta se
 * alimenta del servidor de configuración propio y de Flussonic, no del servidor
 * Jellyfin.
 *
 * Queda **detrás del inicio de sesión**, como Inicio y Buscar (`CLAUDE.md` §6).
 * Los canales en sí no piden credenciales; lo que la sesión controla es quién
 * llega a la sección.
 *
 * Usa `JellyfinTheme` y los componentes de `ui/base/` a propósito: Inicio,
 * Buscar y TV en vivo tienen que verse como una sola aplicación.
 *
 * **M0:** una fila plana con los canales de prueba de `BuildConfig`. La lista
 * real —grupos, favoritos, último canal, entrada por número y búsqueda, pensada
 * para ~120 canales— es B2.
 */
class LiveTvFragment : Fragment() {
	private val navigationRepository by inject<NavigationRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			// Entrar y salir de la sección no puede convertirse en una descarga
			// por visita, pero **tampoco puede ser una sola por proceso**: una
			// caja de salón tiene la app abierta días, y lo que el operador
			// publique tiene que llegarle sin que nadie reinicie nada.
			val catalogoFuente = remember { LiveTvCatalog.of(requireContext()) }
			val catalogo by remember { catalogoFuente.also { it.refreshSiHaceFalta() }.state }
				.collectAsState()

			// Y mientras la sección está abierta, se vuelve a mirar cada tanto.
			// Quien deja la guía puesta es justo quien no va a reiniciar.
			LaunchedEffect(catalogoFuente) {
				while (true) {
					delay(LiveTuning.CONFIG_REFRESH_INTERVAL_MS)
					catalogoFuente.refreshSiHaceFalta()
				}
			}

			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(MainToolbarActiveButton.LiveTv)

				ChannelRow(
					catalog = catalogo,
					onChannelClick = { channel ->
						navigationRepository.navigate(Destinations.liveTvPlayer(channel.id))
					},
					onDiagnosticsClick = {
						navigationRepository.navigate(Destinations.liveTvDevMenu)
					},
				)
			}
		}
	}
}

@Composable
private fun ChannelRow(
	catalog: LiveCatalog,
	onChannelClick: (ChannelProfile) -> Unit,
	onDiagnosticsClick: () -> Unit,
) {
	// El foco inicial lo pide la guía, que es quien sabe cuándo existe la
	// tarjeta a la que debe ir (ver `LiveTvChannelGrid`).
	//
	// Las medidas automáticas por `adb` entran a la sección con un toque, que
	// no recorre el mismo camino de foco que el mando: **confirmar siempre con
	// el control**.
	val context = LocalContext.current

	// A qué canal mirar al entrar: lo último que vio esta persona, lo que el
	// ISP puso por defecto, y si no, el primero. Con ~120 canales, volver a la
	// sección y tener que buscar otra vez el canal de siempre es la diferencia
	// entre una guía usable y una lista.
	val ultimo = remember { LiveTvUltimoCanal.of(context) }
	val idInicial = remember(catalog.channels, catalog.defaultChannel) {
		ultimo.inicial(catalog.channels.map { it.id }, catalog.defaultChannel)
	}
	val dev = remember { DevOptions.of(context) }
	val revision by dev.revision.collectAsState()
	val degradada = remember(revision) { dev.isDegraded }
	val degradacion = remember(revision) { dev.describe() }

	val favoritos = remember { LiveTvFavorites.of(context) }
	val favoritosIds by favoritos.ids.collectAsState()

	val guia = remember(catalog, favoritosIds) {
		agruparCanales(catalog.channels, catalog.groups, favoritosIds)
	}

	// --- Búsqueda -----------------------------------------------------------
	//
	// Con ~120 canales el número sirve a quien ya se lo sabe; el resto busca
	// por el nombre. Y busca por **cómo llama al canal**, que es para lo que
	// están los `aliases` del `config.json`.
	//
	// Mientras hay consulta, la guía entera se sustituye por una sola fila de
	// resultados: ver los grupos vacíos debajo haría parecer que la búsqueda
	// no filtró nada.
	var buscando by remember { mutableStateOf(false) }
	var consulta by remember { mutableStateOf("") }
	// Sube cuando el abonado pulsa ABAJO en el campo: es la señal de que ahora
	// sí quiere el foco en los resultados.
	var bajarAResultados by remember { mutableIntStateOf(0) }

	val resultados = remember(catalog.channels, consulta) {
		if (consulta.isBlank()) emptyList() else ChannelSearch.buscar(catalog.channels, consulta)
	}
	val secciones = when {
		!buscando || consulta.isBlank() -> guia
		resultados.isEmpty() -> emptyList()
		else -> listOf(ChannelSection("__busqueda", "Resultados", resultados))
	}

	// El destino de ABAJO desde la cabecera. Se ata a la guía más abajo.
	val focoGuia = remember { FocusRequester() }
	val hayGuia = catalog.acceso !is Acceso.Denegado && secciones.isNotEmpty()

	fun cerrarBusqueda() {
		buscando = false
		consulta = ""
		bajarAResultados = 0
	}

	// ATRÁS desde los resultados vuelve a la guía, no sale de la sección.
	//
	// El campo de texto ya trata ATRÁS por su cuenta —primero baja el teclado—,
	// pero en cuanto el foco está en una tarjeta de resultados ese camino no
	// existe. Sin esto, la tecla obvia echa al abonado de TV en vivo entera.
	BackHandler(enabled = buscando) { cerrarBusqueda() }

	// --- Marcado por número -------------------------------------------------
	//
	// Con ~120 canales nadie navega con flechas hasta el 87: teclea 8 y 7. Los
	// dígitos se acumulan y, tras una pausa, se sintoniza. La pausa es lo que
	// permite marcar números de dos y tres cifras sin pedir confirmación.
	var digitos by remember { mutableStateOf("") }
	val marcado = remember(digitos, catalog) {
		digitos.toIntOrNull()?.let { n -> catalog.channels.firstOrNull { it.number == n } }
	}

	LaunchedEffect(digitos) {
		if (digitos.isEmpty()) return@LaunchedEffect
		delay(NUMBER_ENTRY_TIMEOUT_MS)
		val destino = marcado
		digitos = ""
		if (destino != null) onChannelClick(destino)
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.padding(horizontal = 48.dp, vertical = 24.dp)
				.focusGroup()
				.onKeyEvent { evento ->
					if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
					// Mientras se busca, los dígitos son texto, no un canal.
					if (buscando) return@onKeyEvent false
					// Y con la sección cerrada no hay canal que marcar.
					if (catalog.acceso is Acceso.Denegado) return@onKeyEvent false
					val digito = digitoDe(evento.key) ?: return@onKeyEvent false
					// Se acota a cuatro cifras: más no existe en ningún lineup y
					// evita que una pulsación pegada del mando acumule sin fin.
					if (digitos.length < 4) digitos += digito
					true
				}
		) {
			// La cabecera: marca a la izquierda, estado a la derecha.
			//
			// Antes todo se apilaba —marca, aviso, degradación y un botón de
			// Diagnóstico del tamaño de una tarjeta— y empujaba los canales
			// media pantalla hacia abajo. **Lo que la gente viene a ver son los
			// canales**, así que el resto se aparta a una sola línea.
			// **ABAJO desde la cabecera vuelve a la guía, y hay que decirlo a
			// mano.** La búsqueda de foco de Compose es geométrica, y los
			// botones de la cabecera están a la derecha del todo, más allá de
			// donde acaba la fila de tarjetas más ancha: debajo de ellos no hay
			// nada que encontrar. El resultado era un atasco — se subía a
			// «Diagnóstico» y no se podía volver sin ir primero a la izquierda.
			//
			// `focusProperties` se hereda, así que ponerlo en la fila cubre los
			// dos botones sin repetirlo.
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.focusProperties {
						// Dos casos se dejan a Compose. Sin guía —sección
						// cerrada o sin canales— porque apuntar a un
						// `FocusRequester` que no está atado a nada lanza. Y
						// buscando, porque entre la cabecera y la guía está el
						// campo de texto y saltárselo sería peor que el atasco
						// que esto viene a arreglar. Medido en la caja: hoy
						// ARRIBA desde el campo no sube a la cabecera, así que
						// esa segunda rama no se alcanza con el mando; se deja
						// escrita para que siga siendo correcta si algún día
						// sube.
						down = if (hayGuia && !buscando) focoGuia else FocusRequester.Default
					},
				verticalAlignment = Alignment.CenterVertically,
			) {
				LogoDeMarca(catalog.brand)

				Spacer(modifier = Modifier.weight(1f))

				// De dónde salió la lista. Una caja sirviendo una lista vieja
				// porque el servidor no responde se comporta igual que una al
				// día, y sin este aviso nadie las distingue.
				when {
					catalog.loading -> Pastilla("actualizando…", Color(0xFF9E9E9E))
					catalog.origin == ConfigOrigin.CACHE ->
						Pastilla("lista guardada — el servidor no responde", Color(0xFFE2B33C))
					catalog.origin == ConfigOrigin.NINGUNO ->
						Pastilla("canales de prueba", Color(0xFF9E9E9E))
					else -> Unit
				}

				// La degradación persiste en `SharedPreferences`: sobrevive al
				// cierre de la app y al reinicio de la caja. Se avisa antes de
				// sintonizar, porque una sesión con toggles pegados se mide
				// como si fuera de fábrica y eso ya costó dos conclusiones
				// falsas.
				if (degradada) {
					Spacer(modifier = Modifier.width(8.dp))
					Pastilla(degradacion, Color(0xFFE2B33C))
				}

				Spacer(modifier = Modifier.width(12.dp))

				// Con la sección cerrada no se ofrece buscar: no hay nada que
				// buscar, y un botón que no lleva a ningún sitio hace parecer
				// rota una pantalla que está explicando algo.
				if (catalog.acceso !is Acceso.Denegado) {
				// Buscar va antes que Diagnóstico porque es del abonado y el
				// otro es nuestro. Es un botón y no un icono suelto para que se
				// vea qué hace: en una caja de TV nadie descubre una función
				// que no se nombra.
				Button(
					onClick = { buscando = true },
					contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
				) {
					Text(text = "Buscar canal", fontSize = 13.sp)
				}

				Spacer(modifier = Modifier.width(8.dp))
				}

				// Diagnóstico sigue a mano —se veta un decoder y se entra al
				// canal sin salir de la sección— pero deja de ocupar el sitio
				// del primer canal.
				Button(
					onClick = onDiagnosticsClick,
					contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
				) {
					Text(text = "Diagnóstico", fontSize = 13.sp)
				}
			}

			// El aviso del operador. Va aquí y no sobre el vídeo: es
			// información del ISP, no del canal, y tapar la imagen con ella
			// sería cobrarle al abonado el recado de su proveedor.
			catalog.message?.let { aviso ->
				Spacer(modifier = Modifier.height(14.dp))
				Row(
					modifier = Modifier
						.clip(ShapeDefaults.Small)
						.background(Color.White.copy(alpha = 0.06f))
						.padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(text = "ⓘ", color = Color(catalog.brand.accent))
					Spacer(modifier = Modifier.width(10.dp))
					Text(text = aviso.text, color = Color(0xFFD5D5D5), fontSize = 14.sp)
				}
			}

			// El aviso del registro: no se ha podido verificar el servicio pero
			// se sigue entrando. Va arriba del todo y en tono de aviso, no de
			// error, porque para el abonado no ha cambiado nada todavía.
			(catalog.acceso as? Acceso.Permitido)?.aviso?.let { aviso ->
				Spacer(modifier = Modifier.height(14.dp))
				Row(
					modifier = Modifier
						.clip(ShapeDefaults.Small)
						.background(Color(0xFFE2B33C).copy(alpha = 0.12f))
						.padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(text = "ⓘ", color = Color(0xFFE2B33C))
					Spacer(modifier = Modifier.width(10.dp))
					Text(text = aviso, color = Color(0xFFD5D5D5), fontSize = 14.sp)
				}
			}

			// Los extras de la raíz: información del ISP sobre su servicio, no
			// sobre un canal. Van junto al aviso del operador por la misma
			// razón —son suyos, no nuestros— pero sin recuadro: un aviso pide
			// que lo leas, esto solo está ahí.
			if (catalog.extras.isNotEmpty()) {
				Spacer(modifier = Modifier.height(14.dp))
				DatosExtra(catalog.extras, tamano = 14.sp)
			}

			if (buscando) {
				Spacer(modifier = Modifier.height(16.dp))
				BarraDeBusqueda(
					consulta = consulta,
					onConsultaChange = { consulta = it },
					onCerrar = { cerrarBusqueda() },
					onBajar = { bajarAResultados++ },
					acento = Color(catalog.brand.accent),
				)
			}

			Spacer(modifier = Modifier.height(26.dp))

			val denegado = catalog.acceso as? Acceso.Denegado
			if (denegado != null) {
				SeccionCerrada(denegado.mensaje)
			} else if (secciones.isEmpty()) {
				// Sin canales hay dos situaciones distintas y conviene no
				// confundirlas: el servidor contestó una lista vacía —raro,
				// pero suyo— o no se pudo hablar con él. Lo segundo es lo que
				// va a pasar en un site mal configurado, y decir «no hay
				// canales» ahí manda a buscar donde no es.
				Text(
					text = when {
						// Buscando, la lista vacía no habla del servidor: habla
						// de lo que se acaba de teclear. Decir «no se pudo
						// cargar la lista» aquí mandaría a buscar donde no es.
						buscando && consulta.isNotBlank() ->
							"Ningún canal coincide con «$consulta»."
						catalog.origin == ConfigOrigin.NINGUNO ->
							"No se pudo cargar la lista de canales de este servidor."
						else -> "No hay canales que mostrar."
					},
					color = Color(0xFF9E9E9E),
				)
			} else {
				LiveTvChannelGrid(
					sections = secciones,
					favorites = favoritosIds,
					accent = Color(catalog.brand.accent),
					onChannelClick = onChannelClick,
					onChannelLongClick = { favoritos.toggle(it.id) },
					idConFoco = if (buscando) resultados.firstOrNull()?.id else idInicial,
					tomarFoco = !buscando,
					enfocar = bajarAResultados,
					modifier = Modifier.focusRequester(focoGuia),
				)
			}
		}

		if (digitos.isNotEmpty()) {
			NumberEntryOverlay(
				digits = digitos,
				resolved = marcado?.name,
				modifier = Modifier.align(Alignment.Center),
			)
		}
	}
}

/**
 * Lo que se ve cuando el registro dice que este servidor no está autorizado.
 *
 * **No puede parecer que la app está rota** (`REGISTRY_SCHEMA.md` §3). El
 * abonado no ha hecho nada mal y no puede arreglar nada: lo único útil que esta
 * pantalla hace es decirle a quién preguntar. De ahí las decisiones:
 *
 * - **Nada de rojo, ni icono de error, ni «algo salió mal».** Es un estado del
 *   servicio, no un fallo del aparato.
 * - **El mensaje lo escribe quien opera el registro y se enseña tal cual**, sin
 *   envolverlo en texto nuestro que lo contradiga. Si no mandó ninguno, hay uno
 *   genérico, que es peor y por eso el contrato insiste tanto.
 * - **El VOD sigue ahí.** La puerta cubre solo esta sección, así que se dice:
 *   es la diferencia entre «se me rompió la aplicación» y «esta parte no está
 *   disponible».
 */
@Composable
private fun SeccionCerrada(mensaje: String) {
	Column(modifier = Modifier.fillMaxWidth(0.6f)) {
		Text(
			text = "TV en vivo no está disponible",
			color = Color.White,
			fontWeight = FontWeight.SemiBold,
			fontSize = 24.sp,
		)
		Spacer(modifier = Modifier.height(14.dp))
		Text(text = mensaje, color = Color(0xFFBDBDBD), fontSize = 17.sp)
		Spacer(modifier = Modifier.height(22.dp))
		Text(
			text = "Tus películas y series siguen disponibles en Inicio.",
			color = Color(0xFF7D8187),
			fontSize = 15.sp,
		)
	}
}

/** Segundo y medio: da tiempo a marcar tres cifras sin hacer esperar. */
private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L

/** Acepta tanto la fila superior como el teclado numérico del mando. */
private fun digitoDe(key: Key): Char? = when (key) {
	Key.Zero, Key.NumPad0 -> '0'
	Key.One, Key.NumPad1 -> '1'
	Key.Two, Key.NumPad2 -> '2'
	Key.Three, Key.NumPad3 -> '3'
	Key.Four, Key.NumPad4 -> '4'
	Key.Five, Key.NumPad5 -> '5'
	Key.Six, Key.NumPad6 -> '6'
	Key.Seven, Key.NumPad7 -> '7'
	Key.Eight, Key.NumPad8 -> '8'
	Key.Nine, Key.NumPad9 -> '9'
	else -> null
}
