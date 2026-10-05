package org.jellyfin.androidtv.ui.livetv

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import okhttp3.OkHttpClient
import org.jellyfin.livetv.config.RegistryTrust
import org.jellyfin.livetv.diagnostics.LiveLog

/**
 * El cargador de imágenes de **los logos de marca**, y de nada más.
 *
 * ## Por qué existe uno aparte
 *
 * El logo de marca lo puede servir el registro, que está en internet y detrás
 * de un certificado de Let's Encrypt. Una caja anterior a Android 7.1.1 no
 * trae la raíz ISRG en su almacén, así que la descarga falla: medido el 05-oct
 * en API 22, la autorización entraba —`RegistryRepository` ya usa la confianza
 * ampliada— y el logo no, porque lo pide Coil con la confianza del sistema.
 *
 * **La tentación era ampliar el `ImageLoader` global**, y es justo lo que no se
 * hace. Ese cargador es el del VOD: ampliar la confianza de todas las imágenes
 * de la app para arreglar un logo es mal negocio, y además metería mano en el
 * camino que `CLAUDE.md` §4 declara invariante.
 *
 * Así que este cargador es pequeño a propósito: una conexión, la misma
 * confianza que ya usa el registro, y el decodificador SVG porque un logo de
 * marca es tan probable en vector como en mapa de bits.
 *
 * ## Qué pasa si no funciona
 *
 * Nada que se vea como un error. Si la confianza no se puede construir se usa
 * la del sistema, que en una caja moderna es la que hacía falta de todas
 * formas; y si aun así el logo no carga, `LogoDeMarca` cae al nombre del
 * proveedor, que es el comportamiento diseñado para eso (§8).
 */
object LogoLoader {

	@Volatile
	private var cache: ImageLoader? = null

	fun of(context: Context): ImageLoader = cache ?: synchronized(this) {
		cache ?: construir(context.applicationContext).also { cache = it }
	}

	private fun construir(contexto: Context): ImageLoader {
		val cliente = OkHttpClient.Builder().apply {
			val confianza = RegistryTrust.confianza(contexto)
			if (confianza != null) {
				sslSocketFactory(confianza.socketFactory, confianza.trustManager)
			} else {
				// No es fatal, pero es exactamente el caso en el que el logo no
				// va a cargar en una caja vieja. Decirlo aquí ahorra buscarlo
				// en la pantalla.
				LiveLog.warn(
					"marca.logoSinConfianzaAmpliada",
					"detalle" to "se usa la confianza del sistema",
				)
			}
		}.build()

		return ImageLoader.Builder(contexto)
			.components {
				// `serviceLoaderEnabled(false)` lo apaga el cargador global por
				// arranque; aquí se deja el descubrimiento apagado por la misma
				// razón y se declara lo que hace falta, que son dos cosas.
				add(OkHttpNetworkFetcherFactory(callFactory = { cliente }))
				add(SvgDecoder.Factory())
			}
			.serviceLoaderEnabled(false)
			.build()
	}
}
