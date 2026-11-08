package mx.kataliziador.palomera.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.livetv.capabilities.DeviceCapabilities

/**
 * Qué decoders trae este teléfono.
 *
 * **Es el instrumento de la fase 0, no una pantalla de bienvenida.** El riesgo
 * R1 de `MOVIL_PLAN.md` es que un SoC de teléfono no traiga decoder de MPEG-2 y
 * que el H.264 entrelazado —cuatro de los seis canales de referencia, los tres
 * HD incluidos— salga con peine. Lo primero se contesta aquí, en una tabla.
 * Lo segundo **no**: hace falta mirar la pantalla, igual que en la caja.
 *
 * Pinta en pantalla y no en logcat por la misma razón que la sonda de
 * Palomera-TV: el teléfono de un probador no tiene `adb` enchufado.
 */
private val MIMES = listOf(
	"video/avc" to "H.264 — el 98 % del lineup",
	"video/mpeg2" to "MPEG-2 — un canal de 53 lo necesita (§4.2)",
	"video/hevc" to "HEVC — VOD, no canales",
	"audio/mp4a-latm" to "AAC — el audio normalizado (§2)",
	"audio/mpeg" to "MP2/MP3 — no debería hacer falta",
	"audio/ac3" to "AC-3 — no debería hacer falta",
)

@Composable
fun PantallaCapacidades() {
	val contexto = LocalContext.current
	val caps = remember { DeviceCapabilities.of(contexto) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF101014))
			.verticalScroll(rememberScrollState())
			// Antes del padding propio: lo que aparta de la barra de estado y
			// de la de navegación es esto, no los 20 dp (ver MainActivity).
			.windowInsetsPadding(WindowInsets.safeDrawing)
			.padding(20.dp),
	) {
		Texto("Palomera para teléfonos", 22.sp, FontWeight.Bold)
		Texto("Fase 0 · lo que este aparato declara", 14.sp, color = Color(0xFF9A9AA6))
		Spacer(Modifier.height(20.dp))

		Fila("fabricante", caps.manufacturer)
		Fila("modelo", caps.model)
		Fila("API", caps.sdkInt.toString())
		Fila("RAM", "${caps.totalRamMb} MB")
		Fila("ABIs", caps.abis.joinToString(", "))

		Spacer(Modifier.height(20.dp))
		Texto("Decoders", 18.sp, FontWeight.Bold)
		Spacer(Modifier.height(8.dp))

		for ((mime, para) in MIMES) {
			val mejor = caps.decoderFor(mime)
			val veredicto = when {
				mejor == null -> "no hay"
				mejor.hardware -> "hardware · ${mejor.name}"
				else -> "software · ${mejor.name}"
			}
			Fila(mime, veredicto)
			Texto("    $para", 11.sp, color = Color(0xFF6E6E7A))
			Spacer(Modifier.height(6.dp))
		}

		Spacer(Modifier.height(24.dp))
		Texto(
			"Esto demuestra que el módulo :livetv compila y se ejecuta en un " +
				"teléfono. No demuestra que reproduzca: eso es la fase 0.2, y se " +
				"juzga mirando la pantalla.",
			11.sp,
			color = Color(0xFF6E6E7A),
			align = TextAlign.Start,
		)
	}
}

@Composable
private fun Fila(clave: String, valor: String) {
	Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
		Texto(clave, 13.sp, color = Color(0xFF9A9AA6), modifier = Modifier.weight(1f))
		Texto(valor, 13.sp, FontWeight.Medium, modifier = Modifier.weight(1.4f))
	}
}

@Composable
private fun Texto(
	texto: String,
	tamano: androidx.compose.ui.unit.TextUnit,
	peso: FontWeight = FontWeight.Normal,
	color: Color = Color(0xFFECECF1),
	align: TextAlign? = null,
	modifier: Modifier = Modifier,
) = androidx.compose.foundation.text.BasicText(
	text = texto,
	modifier = modifier,
	style = androidx.compose.ui.text.TextStyle(
		color = color,
		fontSize = tamano,
		fontWeight = peso,
		textAlign = align ?: TextAlign.Unspecified,
	),
)
