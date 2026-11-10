package mx.kataliziador.palomera.movil

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.kataliziador.palomera.movil.acceso.Puerta
import mx.kataliziador.palomera.movil.acceso.Sesion
import mx.kataliziador.palomera.movil.acceso.guardarSesion
import mx.kataliziador.palomera.movil.acceso.liberarLaTele
import mx.kataliziador.palomera.movil.acceso.olvidarSesion
import mx.kataliziador.palomera.movil.acceso.sesionGuardada
import mx.kataliziador.palomera.movil.ui.acceso.PantallaPuerta
import org.jellyfin.livetv.config.Brand
import org.jellyfin.livetv.config.RegistryRepository
import androidx.compose.ui.graphics.Color
import mx.kataliziador.palomera.movil.catalogo.EnProgreso
import mx.kataliziador.palomera.movil.ui.Carril
import mx.kataliziador.palomera.movil.ui.PantallaCanal
import mx.kataliziador.palomera.movil.ui.PantallaCapacidades
import mx.kataliziador.palomera.movil.ui.PantallaEnVivo
import mx.kataliziador.palomera.movil.catalogo.cabeceraDeAutorizacion
import mx.kataliziador.palomera.movil.catalogo.idDeInstalacion
import mx.kataliziador.palomera.movil.catalogo.nombreDeAparato
import mx.kataliziador.palomera.movil.catalogo.Jellyfin
import mx.kataliziador.palomera.movil.catalogo.destinoDeCanales
import mx.kataliziador.palomera.movil.ui.PantallaPrincipal
import mx.kataliziador.palomera.movil.ui.Seccion
import mx.kataliziador.palomera.movil.ui.PantallaInicio
import mx.kataliziador.palomera.movil.ui.Portada

/**
 * La única actividad de la app.
 *
 * **Decide una cosa y sólo una: si hay sesión o no.** Con sesión guardada se
 * entra directo; sin ella se abre la puerta (`ui/acceso/`). Todo lo demás son
 * extras de `adb` que existen porque en el teléfono de pruebas HyperOS deniega
 * `INJECT_EVENTS` y no hay forma de tocar la pantalla desde aquí.
 */
class MainActivity : ComponentActivity() {

	/**
	 * Cómo se presenta la app, con token o sin él.
	 *
	 * Sin token también hace falta: `AuthenticateByName` es lo que **crea** la
	 * sesión, y el servidor guarda ahí mismo el cliente y el aparato que el
	 * operador verá luego en su panel.
	 */
	private fun cabeceraPara(token: String) = cabeceraDeAutorizacion(
		cliente = BuildConfig.CLIENT_NAME,
		aparato = nombreDeAparato(Build.MANUFACTURER, Build.MODEL),
		idDeInstalacion = idDeInstalacion(),
		version = BuildConfig.VERSION_NAME,
		token = token,
	)

	override fun onCreate(savedInstanceState: Bundle?) {
		// **Obligatorio, no decorativo.** Con `targetSdk 36` Android dibuja de
		// borde a borde y **deja de reservar sitio** para la barra de estado y
		// la de navegación: declararlo aquí es lo que hace que el sistema nos
		// dé los insets en vez de recortarnos. Medido el 08-nov en el Xiaomi de
		// pruebas: sin esto, el título de la pantalla se pintaba encima del
		// reloj. Quien escriba las pantallas de la fase 1 tiene que contar con
		// ello en todas.
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		// `--es canal <url>` abre un canal a pantalla completa. Es el
		// instrumento de la fase 0.2 y sólo se alcanza por `adb`; sin el extra,
		// la app abre lo de siempre.
		val canal = intent?.getStringExtra("canal")
		val pantalla = intent?.getStringExtra("pantalla")
		setContent {
			// La sesión es estado, no un dato de arranque: al entrar por la
			// puerta cambia y la app tiene que pasar sola al catálogo.
			var sesion by remember { mutableStateOf(sesionGuardada()) }
			val deAdb = intent?.getStringExtra("token")

			// Un token por `adb` manda sobre la sesión guardada: es como se
			// prueba un site sin tener que salir de la cuenta a mano.
			val efectiva = if (!deAdb.isNullOrBlank()) {
				Sesion(
					servidor = intent?.getStringExtra("jf").orEmpty(),
					token = deAdb,
					usuarioId = intent?.getStringExtra("uid").orEmpty(),
					proveedor = intent?.getStringExtra("proveedor"),
					servidorId = intent?.getStringExtra("sid"),
				)
			} else {
				sesion
			}

			val destino = destinoDeCanales(
				servidor = efectiva?.servidor,
				idServidor = efectiva?.servidorId,
				// TODO del registro: hoy el móvil no pregunta por `channelsUrl`.
				delRegistro = null,
				manual = intent?.getStringExtra("config"),
				deFabrica = "",
			)

			when {
				!canal.isNullOrBlank() -> PantallaCanal(canal)
				pantalla == "capacidades" -> PantallaCapacidades()
				pantalla == "envivo" -> PantallaEnVivo(
					intent?.getStringExtra("config") ?: CONFIG_DE_MUESTRA,
				)

				// **Sin sesión, la puerta.** Es la primera pantalla de toda
				// instalación y no puede decir Jellyfin ni parecer un error
				// (`CLAUDE.md` §8).
				efectiva == null -> {
					val registro = remember { RegistryRepository(applicationContext) }
					PantallaPuerta(
						acento = ACENTO_NEUTRO,
						preguntarAlRegistro = { codigo ->
							registro.proveedor(BuildConfig.REGISTRY_URL, codigo)
						},
						construirPuerta = { servidor ->
							Puerta(servidor, cabeceraPara(""))
						},
						alEntrar = { nueva ->
							guardarSesion(nueva)
							sesion = nueva
						},
					)
				}

				else -> PantallaPrincipal(
					proveedor = efectiva.proveedor ?: PROVEEDOR_NEUTRO,
					acento = ACENTO_NEUTRO,
					jf = Jellyfin(
						baseUrl = efectiva.servidor,
						token = efectiva.token,
						usuarioId = efectiva.usuarioId,
						// Sin esta cabecera el servidor atiende las peticiones y
						// **no crea sesión**, así que nada de lo que se reproduzca
						// queda guardado. Ver `Identidad.kt`.
						cabecera = cabeceraPara(efectiva.token),
					),
					// **Derivado, no configurado** (`CLAUDE.md` §6): el servicio
					// de canales vive en la misma máquina que el Jellyfin, en
					// su puerto. Pedirle al abonado una segunda dirección sería
					// pedirle algo que no sabe.
					configUrl = destino.first,
					claveDeCache = destino.second,
					sesion = efectiva,
					alCerrarSesion = {
						// **Se avisa a Jellyfin antes de olvidar el token**,
						// que es el arreglo que la caja necesitó el 09-nov: sin
						// eso la sesión queda viva ocupando una de las teles
						// que permite `MaxActiveSessions`, y al reactivar este
						// mismo aparato el servidor contesta 403.
						//
						// Va por un alcance propio y no por el de la pantalla:
						// ésta desaparece en cuanto la sesión se olvida.
						val quien = cabeceraPara(efectiva.token)
						val donde = efectiva.servidor
						CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
							liberarLaTele(donde, quien)
						}
						olvidarSesion()
						sesion = null
					},
					seccionInicial = when (intent?.getStringExtra("seccion")) {
						"catalogo" -> Seccion.CATALOGO
						"envivo" -> Seccion.EN_VIVO
						"buscar" -> Seccion.BUSCAR
						else -> Seccion.INICIO
					},
					fichaInicial = intent?.getStringExtra("ficha"),
					reproducirAlArrancar = intent?.getStringExtra("reproducir"),
					cerrarVideoEnS = intent?.getStringExtra("cerrarEn")?.toIntOrNull(),
				)
			}
		}
	}
}


// **Los datos de muestra se fueron el 10-nov**, que era su condición de
// salida: existían para juzgar las pantallas antes de que hubiera cliente de
// Jellyfin, y ahora la app arranca en la puerta y trae lo del site de verdad.
// El `config.json` del site de pruebas. Va aquí y **no en un archivo
// versionado**: `CLAUDE.md` §9 no permite direcciones en el repositorio, y
// `comprobar.py h)` lo rechazaría. Se pasa por `adb` con `--es config <url>`.
private const val CONFIG_DE_MUESTRA = ""

/**
 * La marca que se ve **antes de que llegue la del ISP**.
 *
 * Sale de `Brand.NEUTRA`, que es donde `CLAUDE.md` §8 la pone, y **no de una
 * cadena escrita aquí**. Hasta el 10-nov eran «TotalPlay» y un verde lima
 * inventados, y se veían de verdad: con el token caducado el `config.json` no
 * carga y la cabecera enseñaba la marca de **otra empresa** al abonado de un
 * ISP cualquiera. Medido ese día en el emulador.
 *
 * Es además §5.12 al pie de la letra: ni marca, ni color, ni nombre escritos
 * en el módulo.
 */
private val PROVEEDOR_NEUTRO = Brand.NEUTRA.displayName
private val ACENTO_NEUTRO = Color(Brand.NEUTRA.accent)
