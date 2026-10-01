package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.livetv.source.ChannelProfile

/**
 * La barra de canal: qué se está viendo, y en qué estado.
 *
 * Aparece al sintonizar y al zapear, y se esconde sola. **Si algo va mal se
 * queda fija**: cuando el canal no está en vivo, esconder la única pista de lo
 * que ocurre sería dejar al suscriptor mirando una pantalla muda.
 *
 * El número va en monoespaciada y grande porque es lo que la gente busca de un
 * vistazo al zapear, y lo que teclea para volver.
 */
@Composable
fun LiveTvChannelBar(
	channel: ChannelProfile,
	state: String,
	detail: String?,
	resolution: String?,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			// Degradado y no un bloque opaco: la barra se apoya sobre la
			// imagen sin recortarla con un borde duro.
			.background(
				Brush.verticalGradient(
					listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.88f))
				)
			)
			.padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 28.dp)
	) {
		Row(verticalAlignment = Alignment.Bottom) {
			channel.number?.let { numero ->
				Text(
					text = numero.toString(),
					color = Color.White,
					fontFamily = FontFamily.Monospace,
					fontWeight = FontWeight.Bold,
					fontSize = 40.sp,
				)
				Spacer(modifier = Modifier.width(20.dp))
			}

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = channel.name,
					color = Color.White,
					fontWeight = FontWeight.Bold,
					fontSize = 28.sp,
				)
				Spacer(modifier = Modifier.height(2.dp))
				Text(
					text = buildString {
						append(state)
						resolution?.let { append("  ·  ").append(it) }
						detail?.let { append("  ·  ").append(it) }
					},
					color = Color(0xFFBDBDBD),
					fontSize = 14.sp,
				)
			}

			channel.group?.let { grupo ->
				Text(
					text = grupo,
					color = Color(0xFF9E9E9E),
					fontSize = 14.sp,
					modifier = Modifier
						.clip(RoundedCornerShape(4.dp))
						.background(Color.White.copy(alpha = 0.10f))
						.padding(horizontal = 10.dp, vertical = 4.dp),
				)
			}
		}

		Spacer(modifier = Modifier.height(14.dp))

		// Las pistas de navegación. En televisión nadie lee un manual: si una
		// tecla hace algo, hay que decirlo en pantalla la primera vez.
		Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
			Pista("▲ ▼", "cambiar de canal")
			Pista("0-9", "ir al número")
			Pista("OK", "ver esta barra")
		}
	}
}

@Composable
private fun Pista(tecla: String, que: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = tecla,
			color = Color.White,
			fontFamily = FontFamily.Monospace,
			fontSize = 12.sp,
			modifier = Modifier
				.clip(RoundedCornerShape(3.dp))
				.background(Color.White.copy(alpha = 0.14f))
				.padding(horizontal = 7.dp, vertical = 3.dp),
		)
		Spacer(modifier = Modifier.width(7.dp))
		Text(text = que, color = Color(0xFF8A8A8A), fontSize = 12.sp)
	}
}
