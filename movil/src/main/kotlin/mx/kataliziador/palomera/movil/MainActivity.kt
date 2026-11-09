package mx.kataliziador.palomera.movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import mx.kataliziador.palomera.movil.catalogo.EnProgreso
import mx.kataliziador.palomera.movil.ui.Carril
import mx.kataliziador.palomera.movil.ui.PantallaCanal
import mx.kataliziador.palomera.movil.ui.PantallaCapacidades
import mx.kataliziador.palomera.movil.ui.PantallaEnVivo
import mx.kataliziador.palomera.movil.ui.InicioConDatos
import mx.kataliziador.palomera.movil.ui.PantallaInicio
import mx.kataliziador.palomera.movil.ui.Portada

/**
 * La única actividad de la app, como manda el diseño de una sola actividad con
 * Compose. Hoy enseña la pantalla de capacidades de la fase 0.
 */
class MainActivity : ComponentActivity() {
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
			when {
				!canal.isNullOrBlank() -> PantallaCanal(canal)
				pantalla == "capacidades" -> PantallaCapacidades()
				pantalla == "envivo" -> PantallaEnVivo(
					intent?.getStringExtra("config") ?: CONFIG_DE_MUESTRA,
				)
				// Con credenciales llega el catálogo de verdad; sin ellas, la
				// muestra. Se pasan por `adb` y **nunca viajan en el
				// repositorio** (`CLAUDE.md` §9), y es un token, no una
				// contraseña: la app no sabe autenticar.
				!intent?.getStringExtra("token").isNullOrBlank() -> InicioConDatos(
					proveedor = intent?.getStringExtra("proveedor") ?: PROVEEDOR_DE_MUESTRA,
					acento = ACENTO_DE_MUESTRA,
					baseUrl = intent?.getStringExtra("jf").orEmpty(),
					token = intent?.getStringExtra("token").orEmpty(),
					usuarioId = intent?.getStringExtra("uid").orEmpty(),
				)
				else -> PantallaInicio(
					proveedor = PROVEEDOR_DE_MUESTRA,
					acento = ACENTO_DE_MUESTRA,
					enProgreso = EN_PROGRESO_DE_MUESTRA,
					carriles = CARRILES_DE_MUESTRA,
				)
			}
		}
	}
}


// **Contenido de muestra, y es temporal.** Son los títulos reales del servidor
// del 09-nov, puestos a mano para poder juzgar la pantalla en un teléfono antes
// de que exista el cliente de Jellyfin. Se borra cuando la fase 1 traiga los
// datos de verdad; hasta entonces, mentir con lorem ipsum habría escondido
// justo lo que hay que mirar: dónde se corta un título largo en español.
// El `config.json` del site de pruebas. Va aquí y **no en un archivo
// versionado**: `CLAUDE.md` §9 no permite direcciones en el repositorio, y
// `comprobar.py h)` lo rechazaría. Se pasa por `adb` con `--es config <url>`.
private const val CONFIG_DE_MUESTRA = ""

private const val PROVEEDOR_DE_MUESTRA = "TotalPlay"
private val ACENTO_DE_MUESTRA = Color(0xFFC8E04A)

private val EN_PROGRESO_DE_MUESTRA = listOf(
	EnProgreso("1", "F1 la película", 31, 98),
	EnProgreso("2", "¡Canta! 2", 19, 91),
	EnProgreso("3", "¡Canta! 2", 19, 91),
	EnProgreso("4", "Las guerreras k-pop", 8, 95),
	EnProgreso("5", "Madagascar", 26, 66),
	EnProgreso("6", "La purga infinita", 29, 74),
	EnProgreso("7", "Mayday", 13, 87),
	EnProgreso("8", "Mayday", 13, 87),
)

private val CARRILES_DE_MUESTRA = listOf(
	Carril(
		"Películas",
		listOf(
			Portada("Transformers: El despertar de las bestias", Color(0xFF2E2A33)),
			Portada("Top Gun: Maverick", Color(0xFF2A2D34)),
			Portada("Tomb Raider: Las aventuras de Lara Croft", Color(0xFF332B2B)),
			Portada("Scream 7", Color(0xFF302A2A)),
			Portada("RoboCop", Color(0xFF26302B)),
		),
	),
	Carril(
		"Series",
		listOf(
			Portada("Outlander: Sangre de mi sangre", Color(0xFF26302B)),
			Portada("Los Simpson", Color(0xFF33302A)),
			Portada("American Horror Story", Color(0xFF2E2A33)),
			Portada("Chucky", Color(0xFF302A2A)),
			Portada("Peppa Pig", Color(0xFF2A2D34)),
		),
	),
)
