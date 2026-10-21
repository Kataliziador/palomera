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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.BuildConfig
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
import org.jellyfin.livetv.epg.FranjaEpg
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

	// --- Programación -------------------------------------------------------
	//
	// La guía se recoge **suelta**, no dentro del catálogo, y por el mismo
	// motivo que explica `LiveTvCatalog`: reemitir el catálogo rehace las
	// tarjetas y el foco salta. Aquí solo alimenta texto y una línea de
	// progreso, así que puede cambiar sin tocar la lista.
	val fuente = remember { LiveTvCatalog.of(context) }
	val epg by fuente.guia.collectAsState()

	// El reloj de la sección. Mueve el progreso de las tarjetas y los minutos
	// que quedan. Medio minuto es de sobra: la barra más larga de la pantalla
	// mide 168 dp, así que un minuto de programa no llega a mover un píxel en
	// casi ningún caso, y un reloj por segundo recompondría 120 tarjetas.
	var ahoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(GUIDE_CLOCK_REFRESH_MS)
			ahoraMs = System.currentTimeMillis()
		}
	}

	// Releer la guía es un efecto, no parte de componer. Cada minuto, y la
	// llamada no hace nada si la ventana guardada ya cubre el presente: lo que
	// se vigila es salirse de ella, que es cosa de horas. Quien deja la sección
	// abierta toda la tarde es justo quien se saldría.
	LaunchedEffect(ahoraMs / 60_000) { fuente.guiaSiHaceFalta(ahoraMs) }

	// El canal que tiene el foco, para la línea de programación. Arranca en el
	// que va a recibirlo, o la línea saldría vacía hasta la primera flecha.
	var enfocado by remember(idInicial, catalog.channels) {
		mutableStateOf(catalog.channels.firstOrNull { it.id == idInicial })
	}

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
	val focoBuscar = remember { FocusRequester() }
	val focoCampo = remember { FocusRequester() }
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
						// Buscando, ABAJO va al campo de texto, que está entre
						// la cabecera y la guía. **Hay que decirlo**: medido en
						// la caja el 05-oct, dejándolo a Compose se salta el
						// campo y aterriza en la guía, aunque el campo esté
						// justo debajo y ocupe todo el ancho.
						//
						// Sin guía —sección cerrada o sin canales— se deja a
						// Compose de verdad, porque apuntar a un
						// `FocusRequester` que no está atado a nada lanza.
						down = when {
							buscando -> focoCampo
							hayGuia -> focoGuia
							else -> FocusRequester.Default
						}
					},
				verticalAlignment = Alignment.CenterVertically,
			) {
				LogoDeMarca(catalog.brand)

				Spacer(modifier = Modifier.weight(1f))

				// De dónde salió la lista. Una caja sirviendo una lista vieja
				// porque el servidor no responde se comporta igual que una al
				// día, y sin este aviso nadie las distingue.
				when {
					catalog.loading -> Pastilla("actualizando…", LiveTvPaleta.tintaSutil)
					catalog.origin == ConfigOrigin.CACHE ->
						Pastilla("lista guardada — el servidor no responde", LiveTvPaleta.aviso)
					catalog.origin == ConfigOrigin.NINGUNO ->
						Pastilla("canales de prueba", LiveTvPaleta.tintaSutil)
					else -> Unit
				}

				// La degradación persiste en `SharedPreferences`: sobrevive al
				// cierre de la app y al reinicio de la caja. Se avisa antes de
				// sintonizar, porque una sesión con toggles pegados se mide
				// como si fuera de fábrica y eso ya costó dos conclusiones
				// falsas.
				if (degradada) {
					Spacer(modifier = Modifier.width(8.dp))
					Pastilla(degradacion, LiveTvPaleta.aviso)
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
					modifier = Modifier.focusRequester(focoBuscar),
				) {
					Text(text = "Buscar canal", fontSize = 13.sp)
				}

				Spacer(modifier = Modifier.width(8.dp))
				}

				// Diagnóstico sigue a mano —se veta un decoder y se entra al
				// canal sin salir de la sección— pero deja de ocupar el sitio
				// del primer canal.
				//
				// **Solo en compilaciones de depuración** (decisión de Edgar,
				// 11-oct). En el APK de Play cualquier abonado tenía a un toque
				// forzar software, vetar decoders y, sobre todo,
				// `registryUrlOverride`, que salta la puerta del registro
				// entera. §8 ya acepta que esa puerta no es un candado técnico,
				// pero heredarlo por descuido no es lo mismo que decidirlo.
				//
				// La puerta es `DEBUG` y no `DEVELOPMENT`: lo segundo se define
				// como `versionCode < 100` (`app/build.gradle.kts`), así que en
				// cuanto se fije el esquema de versiones real se apagaría
				// **también en depuración** y perderíamos el menú en la caja de
				// pruebas.
				//
				// El caso de campo —una caja de un abonado que falla— lo cubre
				// la telemetría anónima de B6, que para eso existe.
				if (BuildConfig.DEBUG) {
					Button(
						onClick = onDiagnosticsClick,
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
					) {
						Text(text = "Diagnóstico", fontSize = 13.sp)
					}
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
					Text(text = aviso.text, color = LiveTvPaleta.tintaApagada, fontSize = 14.sp)
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
						.background(LiveTvPaleta.aviso.copy(alpha = 0.12f))
						.padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(text = "ⓘ", color = LiveTvPaleta.aviso)
					Spacer(modifier = Modifier.width(10.dp))
					Text(text = aviso, color = LiveTvPaleta.tintaApagada, fontSize = 14.sp)
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
					// Vuelve al botón que abrió la búsqueda, que es de donde se
					// venía. Desde ahí ABAJO baja otra vez al campo, porque la
					// cabecera deja ese caso a Compose y el campo está
					// justo debajo y ocupa todo el ancho.
					onSubir = { focoBuscar.requestFocus() },
					foco = focoCampo,
					acento = Color(catalog.brand.accent),
				)
			}

			// Qué dan en el canal que tiene el foco.
			//
			// Solo si este site sirve guía: cuesta ~52 dp —media fila de
			// tarjetas— y donde no hay programación la sección se queda como
			// estaba, que es el caso normal mientras un ISP monta su tubería
			// (`INTEGRACION_CANALES.md` §2).
			if (epg.canales > 0) {
				QueDanAhora(
					franja = enfocado?.let { epg.franja(it.id, ahoraMs) },
					ahoraMs = ahoraMs,
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
					color = LiveTvPaleta.tintaSutil,
				)
			} else {
				LiveTvChannelGrid(
					sections = secciones,
					favorites = favoritosIds,
					franja = { canal -> epg.franja(canal.id, ahoraMs) },
					ahoraMs = ahoraMs,
					accent = Color(catalog.brand.accent),
					onChannelClick = onChannelClick,
					onChannelLongClick = { favoritos.toggle(it.id) },
					onChannelFocus = { enfocado = it },
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
		Text(text = mensaje, color = LiveTvPaleta.tintaApagada, fontSize = 17.sp)
		Spacer(modifier = Modifier.height(22.dp))
		Text(
			text = "Tus películas y series siguen disponibles en Inicio.",
			color = LiveTvPaleta.tintaSutil,
			fontSize = 15.sp,
		)
	}
}

/** Segundo y medio: da tiempo a marcar tres cifras sin hacer esperar. */
private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L

/**
 * Cada cuánto avanza el reloj de la sección.
 *
 * Medio minuto: lo que se mueve con él son los minutos que quedan y unas
 * líneas de progreso de 168 dp de ancho. Un reloj por segundo recompondría la
 * lista entera 60 veces por minuto para no cambiar un píxel.
 */
private const val GUIDE_CLOCK_REFRESH_MS = 30_000L

/**
 * La altura reservada para la línea de programación.
 *
 * Fija a propósito: con un canal sin guía el hueco se queda vacío, porque si
 * se encogiera las tarjetas darían un salto cada vez que el foco pasa por un
 * canal que el site no cubre.
 */
private val ALTO_PROGRAMACION = 52.dp

/** Acepta tanto la fila superior como el teclado numérico del mando. */
/**
 * Qué dan en el canal que tiene el foco.
 *
 * **Una vez y arriba de la guía, no en cada tarjeta.** En la tarjeta no cabe:
 * 168 dp cortan cualquier título real a media palabra, y lo que se gana
 * poniéndolo 120 veces se pierde en ruido. Aquí cabe entero, con la hora y los
 * minutos que quedan, y cambia al moverse el foco — que es como se recorre un
 * lineup buscando qué ver. Lo que sí va en la tarjeta es la línea de progreso,
 * que no es texto y se lee a tres metros.
 *
 * **El hueco se reserva aunque este canal no tenga programación.** Un site
 * sirve guía de unos canales y no de otros, así que si la altura dependiera de
 * eso las tarjetas subirían y bajarían al recorrer la lista. Vacío no dice
 * «sin programación»: el sitio está reservado para no mover nada, y rellenarlo
 * con una disculpa llamaría la atención sobre lo que falta.
 *
 * Sin color: el acento del ISP ya viste los títulos de grupo justo debajo, y
 * repetirlo aquí dejaría dos cosas distintas pintadas igual.
 */
@Composable
private fun QueDanAhora(franja: FranjaEpg?, ahoraMs: Long) {
	Column(modifier = Modifier.height(ALTO_PROGRAMACION)) {
		val ahora = franja?.ahora ?: return@Column

		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text = ahora.titulo,
				color = Color.White,
				fontWeight = FontWeight.SemiBold,
				fontSize = 20.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f, fill = false),
			)
			Spacer(modifier = Modifier.width(16.dp))
			Text(
				text = "${hora(ahora.inicioMs)} – ${hora(ahora.finMs)}",
				color = LiveTvPaleta.tintaApagada,
				fontSize = 16.sp,
			)
			Spacer(modifier = Modifier.width(12.dp))
			Text(
				text = "quedan ${ahora.restanteMin(ahoraMs)} min",
				color = LiveTvPaleta.tintaSutil,
				fontSize = 16.sp,
			)
		}

		franja.luego?.let { siguiente ->
			Spacer(modifier = Modifier.height(6.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(text = "Luego", color = LiveTvPaleta.tintaSutil, fontSize = 16.sp)
				Spacer(modifier = Modifier.width(12.dp))
				Text(
					text = siguiente.titulo,
					color = LiveTvPaleta.tintaApagada,
					fontSize = 16.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

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
