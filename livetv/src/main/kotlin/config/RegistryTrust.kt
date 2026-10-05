package org.jellyfin.livetv.config

import android.content.Context
import org.jellyfin.livetv.R
import org.jellyfin.livetv.diagnostics.LiveLog
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * La confianza con la que se habla **con el registro, y solo con él**.
 *
 * ## El defecto que esto arregla
 *
 * `minSdk` es 21 y el certificado del registro lo emite Let's Encrypt. **Android
 * no trae ninguna raíz de ISRG hasta la 7.1.1**: comprobado el 2026-10-05 en un
 * emulador de API 22, cuyo almacén tiene 162 certificados y **ninguno** de ISRG
 * —sí tiene DST Root CA X3, que es la vieja que Let's Encrypt dejó de usar.
 *
 * Sin esto, una caja con Android 5.0 a 7.1.0 **nunca consigue autorización y
 * nunca entra a TV en vivo**, mientras las demás del mismo ISP sí. Y no da error
 * visible: el registro no puede ni verla, porque no llega a conectar. Es el
 * reporte más caro de diagnosticar que hay — «a este cliente no le funciona».
 *
 * ## Por qué en código y no en `network_security_config.xml`
 *
 * Android ignora ese archivo por debajo de API 24, que es justo donde está el
 * problema.
 *
 * ## Por qué «del sistema **o** las incluidas», y no solo las incluidas
 *
 * Si el registro cambia algún día de autoridad de certificados, las cajas
 * modernas siguen entrando sin publicar una versión. Lo que se añade es una
 * raíz legítima más, no una excepción.
 *
 * ## Lo que esto NO es
 *
 * **Nunca un `TrustManager` que acepte todo.** Eso abriría la puerta a que
 * cualquiera en la red del ISP se haga pasar por el registro y cambie el
 * `channelsUrl` de un site, que es la peor brecha posible de los tres
 * proyectos. Aquí la verificación sigue siendo completa por los dos caminos: si
 * ninguno valida la cadena, se rechaza.
 *
 * Y se aplica **solo a la conexión con el registro**, nunca a la confianza
 * global de la app: el VOD y los canales van por HTTP en la LAN del ISP y no
 * tienen nada que ver con esto.
 */
object RegistryTrust {

	/**
	 * Las raíces que viajan dentro del APK.
	 *
	 * X1 es la que salva a las cajas viejas hoy, porque la cadena que sirve el
	 * registro acaba en ella: `YR2 → ISRG Root YR → ISRG Root X1`. Root YR y YE
	 * son la jerarquía nueva de Let's Encrypt, y están aquí **para el día que
	 * deje de servirse esa firma cruzada** — ese día X1 ya no basta, y hasta
	 * cajas modernas que aún no traigan Root YR fallarían.
	 *
	 * X2 se incluye por simetría con YE: ninguna de las dos está en la cadena de
	 * hoy, y las dos lo estarían si el registro pasara a un certificado de curva
	 * elíptica.
	 */
	private val RAICES = listOf(
		R.raw.isrg_root_x1,
		R.raw.isrg_root_x2,
		R.raw.isrg_root_yr,
		R.raw.isrg_root_ye,
	)

	/**
	 * Las dos piezas juntas, porque hay clientes que piden las dos.
	 *
	 * `HttpURLConnection` se conforma con la fábrica de sockets; OkHttp exige
	 * además el `X509TrustManager`, porque sin él no puede construir su cadena
	 * de certificados y se niega a adivinarlo.
	 */
	class Confianza(val socketFactory: SSLSocketFactory, val trustManager: X509TrustManager)

	@Volatile
	private var cache: Confianza? = null

	/**
	 * La confianza ampliada, construida una vez.
	 *
	 * `null` significa «usa la del sistema». En una caja moderna eso funciona
	 * igual; en una vieja es el fallo que [RegistryTrust] viene a evitar, y por
	 * eso se registra en vez de callarse.
	 */
	fun confianza(context: Context): Confianza? = cache ?: runCatching {
		val tm = trustManager(RAICES.map { context.resources.openRawResource(it) })
		val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
		Confianza(ctx.socketFactory, tm).also { cache = it }
	}.getOrElse {
		LiveLog.error("registro.confianzaNoConstruida", it)
		null
	}

	/** La fábrica de sockets sola, para quien no necesita más. */
	fun socketFactory(context: Context): SSLSocketFactory? = confianza(context)?.socketFactory

	/**
	 * Construye el `TrustManager` compuesto a partir de las raíces dadas.
	 *
	 * Recibe flujos y no un `Context` **para poder probarse sin Android**: la
	 * parte que puede equivocarse en silencio es ésta, y es la que no debería
	 * depender de tener un emulador delante.
	 */
	fun trustManager(raices: List<InputStream>): X509TrustManager {
		val incluidas = deLasRaices(raices)
		val sistema = delSistema()
		return Compuesto(sistema, incluidas)
	}

	private fun deLasRaices(raices: List<InputStream>): X509TrustManager {
		val fabrica = java.security.cert.CertificateFactory.getInstance("X.509")
		val almacen = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }

		raices.forEachIndexed { i, flujo ->
			flujo.use {
				val cert = fabrica.generateCertificate(it) as X509Certificate
				almacen.setCertificateEntry("raiz$i", cert)
			}
		}

		return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
			.apply { init(almacen) }
			.trustManagers
			.filterIsInstance<X509TrustManager>()
			.first()
	}

	private fun delSistema(): X509TrustManager =
		TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
			.apply { init(null as KeyStore?) }
			.trustManagers
			.filterIsInstance<X509TrustManager>()
			.first()

	/**
	 * Válido si **cualquiera de los dos** valida la cadena, y rechazado si
	 * ninguno.
	 *
	 * El orden importa poco para el resultado y mucho para el diagnóstico: se
	 * prueba primero el del sistema, que es el camino normal, y solo se recurre
	 * a las incluidas cuando aquél no reconoce la raíz.
	 */
	private class Compuesto(
		private val sistema: X509TrustManager,
		private val incluidas: X509TrustManager,
	) : X509TrustManager {

		override fun checkServerTrusted(cadena: Array<out X509Certificate>?, tipo: String?) {
			try {
				sistema.checkServerTrusted(cadena, tipo)
			} catch (delSistema: CertificateException) {
				try {
					incluidas.checkServerTrusted(cadena, tipo)
				} catch (deLasIncluidas: CertificateException) {
					// Se lanza el del sistema, que es el que describe el caso
					// normal, con el otro encadenado para no perder ninguno.
					delSistema.addSuppressed(deLasIncluidas)
					throw delSistema
				}
			}
		}

		override fun checkClientTrusted(cadena: Array<out X509Certificate>?, tipo: String?) =
			sistema.checkClientTrusted(cadena, tipo)

		override fun getAcceptedIssuers(): Array<X509Certificate> =
			sistema.acceptedIssuers + incluidas.acceptedIssuers
	}
}
