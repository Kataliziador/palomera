package org.jellyfin.androidtv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

/**
 * Lo que ve un abonado suspendido. Es el **pendiente 84**, y la pantalla la
 * pidió Edgar así: *«una pantalla de color sólido que diga cuenta suspendida,
 * comuníquese con su proveedor para reactivar»*.
 *
 * ## El texto no es nuevo, y eso importa
 *
 * Son las cadenas `livetv_acceso_suspendido_*`, escritas y medidas el 28-oct
 * para el mismo caso al arrancar la app. Reusarlas no es ahorro: es que **el
 * abonado lea lo mismo** se entere al encender la caja o a media tarde. Dos
 * redacciones del mismo problema se leen como dos problemas.
 *
 * La frase que más vale es la segunda —«no hace falta activar el aparato otra
 * vez»—, porque sin ella el abonado reactivado llama pidiendo un código nuevo,
 * y con uno nuevo entra con otra sesión y el panel del operador ve un
 * dispositivo de más.
 *
 * ## Por qué no dice «error» ni lleva rojo
 *
 * Mismo criterio que [SeccionCerrada] y que la ficha de un canal caído: el
 * aparato no está roto y el abonado no ha hecho nada mal. Lo único útil que
 * esta pantalla hace es decirle **qué pasa y a quién llamar**, y un error rojo
 * manda a revisar la antena.
 *
 * ## Qué es «color sólido» aquí
 *
 * El fondo de la sección, a pantalla completa y sin nada más: ni cabecera, ni
 * canales detrás, ni barra. No es un color nuevo —uno distinto se leería como
 * otra aplicación— sino el mismo negro con la pantalla entera para él, que es
 * lo que la hace inequívoca frente al aviso discreto de [AvisoSuspendido].
 */
@Composable
fun PantallaSuspendido(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.fillMaxSize()
			.background(LiveTvPaleta.fondo),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier.fillMaxWidth(0.62f),
			horizontalAlignment = Alignment.Start,
		) {
			Text(
				text = stringResource(R.string.livetv_acceso_suspendido_titulo),
				color = LiveTvPaleta.tinta,
				fontWeight = FontWeight.Bold,
				fontSize = 34.sp,
			)
			Spacer(modifier = Modifier.height(18.dp))
			Text(
				text = stringResource(R.string.livetv_acceso_suspendido_mensaje),
				color = LiveTvPaleta.tintaApagada,
				fontSize = 18.sp,
				lineHeight = 26.sp,
			)
			Spacer(modifier = Modifier.height(26.dp))
			// Igual que en las demás pantallas de sección cerrada: el VOD no
			// depende de esto y decirlo es la diferencia entre «se rompió la
			// aplicación» y «esta parte no está disponible».
			Text(
				text = "Tus películas y series siguen disponibles en Inicio.",
				color = LiveTvPaleta.tintaSutil,
				fontSize = 15.sp,
			)
		}
	}
}

/**
 * El mismo aviso, pequeño, para cuando hay vídeo en marcha.
 *
 * **Lo que se decidió sobre la imagen**, que es la parte Modo A del pendiente
 * 84: con un 403 a media película **no se corta nada**. Cortar en seco es
 * apagarle la televisión a alguien que a lo mejor pagó hace diez minutos y el
 * panel todavía no se ha enterado; y esperar al siguiente encendido es lo que
 * ya pasaba y es justo lo que se viene a arreglar.
 *
 * El punto medio: se avisa **ya**, encima del vídeo y sin tapar nada, y la
 * pantalla entera aparece en cuanto el abonado toque algo —cambiar de canal o
 * volver a la guía—. Se entera en segundos, no se le corta lo que está
 * viendo, y no hay forma de seguir zapeando sin verla.
 *
 * Va en ámbar y no en rojo por lo mismo que la pantalla: es un estado del
 * servicio, no una avería.
 */
@Composable
fun AvisoSuspendido(modifier: Modifier = Modifier) {
	Row(
		modifier = modifier
			.clip(RoundedCornerShape(8.dp))
			.background(Color.Black.copy(alpha = 0.82f))
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(9.dp)
				.clip(CircleShape)
				.background(LiveTvPaleta.aviso),
		)
		Spacer(modifier = Modifier.width(12.dp))
		Text(
			text = stringResource(R.string.livetv_acceso_suspendido_breve),
			color = LiveTvPaleta.tinta,
			fontSize = 15.sp,
		)
	}
}
