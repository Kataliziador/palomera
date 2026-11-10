package mx.kataliziador.palomera.movil.catalogo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lo poco que la app le pide al Jellyfin del ISP para pintar Inicio.
 *
 * **No usa el SDK**, y es deliberado: sus modelos generados obligan a que todo
 * encaje, y lo que hace falta aquí es lo contrario —descartar lo que venga mal
 * y seguir (ver [leerTitulos])—. Tres peticiones y un parser que perdona.
 *
 * El token se le pasa ya hecho: **esta clase no sabe autenticar**, y así no hay
 * ninguna ruta por la que una contraseña llegue hasta aquí.
 */
class Jellyfin(
	private val baseUrl: String,
	private val token: String,
	private val usuarioId: String,
	/**
	 * Cómo se presenta la app (ver [cabeceraDeAutorizacion]).
	 *
	 * Se le pasa ya hecha porque lleva dentro el modelo del teléfono y el
	 * identificador de instalación, que son datos de Android, y esta clase no
	 * toca Android. Sin ella la app sigue leyendo el catálogo, pero **el
	 * servidor no la ve como una sesión** y nada de lo que reproduzca queda
	 * guardado.
	 */
	private val cabecera: String = "MediaBrowser Token=\"$token\"",
) {
	/** Lo dejado a medias. Vacío en una cuenta nueva, que es el primer día de un probador. */
	suspend fun enProgreso(limite: Int = 12): List<EnProgreso> =
		leerEnProgreso(pedir("/Users/$usuarioId/Items/Resume?Limit=$limite&MediaTypes=Video&Fields=RunTimeTicks"))

	/**
	 * Cómo ver un título: directo si se puede, convertido si no.
	 *
	 * Se le manda al servidor el perfil del aparato —lo que de verdad sabe
	 * decodificar— y él contesta. **No se adivina desde aquí**: adivinar de más
	 * es una pantalla negra y adivinar de menos pone a transcodificar al
	 * servidor del ISP (ver `perfilDeDispositivo`).
	 */
	suspend fun comoReproducir(id: String, perfil: String): Reproduccion = leerReproduccion(
		cuerpo = enviar("/Items/$id/PlaybackInfo?userId=$usuarioId", """{"DeviceProfile":$perfil}"""),
		baseUrl = baseUrl,
		token = token,
		itemId = id,
	)

	/**
	 * Avisar al servidor de lo que se está viendo.
	 *
	 * **Esto faltaba entero hasta el 10-nov**, y lo que se rompía no era la
	 * sesión invisible: era que **«Seguir viendo» no avanzaba nunca** desde el
	 * teléfono. Ese carril es lo primero del inicio y se alimenta de
	 * `PlaybackPositionTicks`, que sólo escribe quien reproduce. Medido: una
	 * película cinco minutos en marcha y `GET /Sessions` sin rastro de ella.
	 *
	 * Nunca lanza y nunca retiene al reproductor: si el site no contesta, la
	 * película sigue. Un servidor caído no puede parar lo que ya se está viendo.
	 */
	suspend fun avisar(aviso: Aviso, itemId: String, posicionMs: Long, enPausa: Boolean) {
		val ruta = when (aviso) {
			Aviso.EMPEZO -> "/Sessions/Playing"
			Aviso.AVANZA -> "/Sessions/Playing/Progress"
			Aviso.PARO -> "/Sessions/Playing/Stopped"
		}
		val cuerpo = """{"ItemId":"$itemId","PositionTicks":${aTicks(posicionMs)},""" +
			""""IsPaused":$enPausa,"CanSeek":true,"PlayMethod":"DirectPlay"}"""

		enviar(ruta, cuerpo)
	}

	/** La ficha de un título, o `null` si el site no la da. */
	suspend fun detalle(id: String): Detalle? = leerDetalle(pedir("/Users/$usuarioId/Items/$id"))

	/** Las bibliotecas del site, que son los carriles de Inicio. */
	suspend fun bibliotecas(): List<BibliotecaDelSite> =
		leerBibliotecas(pedir("/Users/$usuarioId/Views"))

	/**
	 * Lo último de una biblioteca concreta.
	 *
	 * **`IncludeItemTypes` no es opcional aquí.** Con `Recursive=true` a secas,
	 * una biblioteca de series devuelve los **episodios** recién añadidos, y el
	 * carril «Series» salía con «Episodio 8», «Episodio 7», «Episodio 6». Se
	 * vio en el teléfono el 09-nov. Lo que un carril de catálogo enseña es la
	 * obra, no su último trozo.
	 */
	suspend fun ultimosDe(bibliotecaId: String, limite: Int = 12): List<Titulo> = leerTitulos(
		pedir(
			"/Items?userId=$usuarioId&parentId=$bibliotecaId&Recursive=true" +
				"&IncludeItemTypes=Movie,Series" +
				"&Limit=$limite&SortBy=DateCreated&SortOrder=Descending&Fields=ProductionYear",
		),
	)

	/** Una página de una biblioteca, con cuántos hay en total detrás. */
	suspend fun paginaDe(bibliotecaId: String, limite: Int): Pair<List<Titulo>, Int> {
		val cuerpo = pedir(
			"/Items?userId=$usuarioId&parentId=$bibliotecaId&Recursive=true" +
				"&IncludeItemTypes=Movie,Series" +
				"&Limit=$limite&SortBy=SortName&SortOrder=Ascending&Fields=ProductionYear",
		)
		return leerTitulos(cuerpo) to leerTotal(cuerpo)
	}

	/** Lo último que entró en la biblioteca, por tipo. */
	suspend fun ultimos(tipo: String, limite: Int = 12): List<Titulo> = leerTitulos(
		pedir(
			"/Items?userId=$usuarioId&IncludeItemTypes=$tipo&Recursive=true" +
				"&Limit=$limite&SortBy=DateCreated&SortOrder=Descending&Fields=ProductionYear",
		),
	)

	/** Lo mismo que [pedir], pero mandando un cuerpo. */
	private suspend fun enviar(ruta: String, cuerpo: String): String = withContext(Dispatchers.IO) {
		var conexion: HttpURLConnection? = null
		try {
			conexion = (URL(baseUrl.trimEnd('/') + ruta).openConnection() as HttpURLConnection).apply {
				requestMethod = "POST"
				connectTimeout = 8_000
				readTimeout = 15_000
				doOutput = true
				setRequestProperty("Content-Type", "application/json")
				setRequestProperty("Authorization", cabecera)
			}
			conexion.outputStream.use { it.write(cuerpo.toByteArray()) }
			val codigo = conexion.responseCode
			if (codigo !in 200..299) {
				Timber.w("catalogo.noContesta ruta=%s codigo=%d", ruta, codigo)
				return@withContext ""
			}
			conexion.inputStream.bufferedReader().use { it.readText() }
		} catch (err: Exception) {
			Timber.w(err, "catalogo.falló ruta=%s motivo=%s", ruta, err::class.simpleName)
			""
		} finally {
			conexion?.disconnect()
		}
	}

	/**
	 * Una petición, o una cadena vacía.
	 *
	 * **Nunca lanza.** Quien llama pinta su estado vacío, que está diseñado; una
	 * excepción aquí dejaría al abonado ante una pantalla en blanco sin saber
	 * por qué. Lo que sí hace es dejarlo escrito, con el código.
	 */
	private suspend fun pedir(ruta: String): String = withContext(Dispatchers.IO) {
		var conexion: HttpURLConnection? = null
		try {
			conexion = (URL(baseUrl.trimEnd('/') + ruta).openConnection() as HttpURLConnection).apply {
				connectTimeout = 8_000
				readTimeout = 15_000
				setRequestProperty("Authorization", cabecera)
			}
			val codigo = conexion.responseCode
			if (codigo !in 200..299) {
				Timber.w("catalogo.noContesta ruta=%s codigo=%d", ruta, codigo)
				return@withContext ""
			}
			conexion.inputStream.bufferedReader().use { it.readText() }
		} catch (err: Exception) {
			Timber.w(err, "catalogo.falló ruta=%s motivo=%s", ruta, err::class.simpleName)
			""
		} finally {
			conexion?.disconnect()
		}
	}
}
