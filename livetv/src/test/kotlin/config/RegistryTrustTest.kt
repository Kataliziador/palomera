package org.jellyfin.livetv.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * La confianza con el registro, probada **sin Android y sin emulador**.
 *
 * Es deliberado que esto se pruebe aquí. De los cinco modos de fallo del
 * trabajo de certificados, tres no necesitan un dispositivo —que las raíces
 * empaquetadas sean las equivocadas, que la composición esté mal, y el
 * peligroso: que acabe aceptándolo todo— y son los que más fácil se rompen.
 * Los otros dos —el Conscrypt de API 22 y el cliente de Coil— sí necesitan
 * caja, y se comprueban aparte.
 */
class RegistryTrustTest : StringSpec({

	val raices = listOf("x1", "x2", "yr", "ye").map { File("src/main/res/raw/isrg_root_$it.pem") }

	fun cadenaDelRegistro(): Array<X509Certificate> {
		val fabrica = CertificateFactory.getInstance("X.509")
		return File("src/test/resources/cadena-registro.pem").inputStream().use {
			fabrica.generateCertificates(it).map { c -> c as X509Certificate }.toTypedArray()
		}
	}

	fun soloLasIncluidas() = RegistryTrust.trustManager(raices.map { it.inputStream() })

	/**
	 * Un impostor: autofirmado y **con el nombre del registro**, que es como se
	 * vería el ataque de verdad. Es un PEM capturado y no generado al vuelo
	 * porque las clases internas del JDK que hacían falta dejaron de ser
	 * accesibles en Java 17, y un archivo dice exactamente lo mismo.
	 */
	fun leerCert(ruta: String): X509Certificate =
		File(ruta).inputStream().use {
			CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
		}

	"las cuatro raíces empaquetadas existen y son certificados" {
		raices.forEach { f ->
			(f.exists() && f.readText().contains("BEGIN CERTIFICATE")) shouldBe true
		}
	}

	"la cadena real del registro se valida con las raíces incluidas" {
		// La cadena está capturada de authjelly.satmx.net, no escrita a mano.
		// Es la prueba de que X1 cubre hoy a las cajas viejas: el registro sirve
		// `YR2 → ISRG Root YR → ISRG Root X1`, y esa última es la que llevamos.
		soloLasIncluidas().checkServerTrusted(cadenaDelRegistro(), "RSA")
	}

	"un certificado autofirmado SIGUE siendo rechazado" {
		// El fallo que daría miedo: un `TrustManager` permisivo dejaría a
		// cualquiera en la red del ISP hacerse pasar por el registro y cambiar
		// el `channelsUrl` de un site. Si esta prueba deja de fallar, eso pasó.
		val autofirmado = leerCert("src/test/resources/impostor-autofirmado.pem")
		val rechazado = runCatching {
			soloLasIncluidas().checkServerTrusted(arrayOf(autofirmado), "RSA")
		}.exceptionOrNull() is CertificateException
		rechazado shouldBe true
	}

	"una cadena vacía se rechaza" {
		val rechazado = runCatching {
			soloLasIncluidas().checkServerTrusted(emptyArray(), "RSA")
		}.exceptionOrNull() != null
		rechazado shouldBe true
	}

	"los emisores aceptados incluyen los del sistema y los nuestros" {
		// La unión es lo que permite que el registro cambie de autoridad algún
		// día sin dejar fuera a las cajas modernas.
		val aceptados = soloLasIncluidas().acceptedIssuers
		val nombres = aceptados.map { it.subjectX500Principal.name }
		nombres.any { it.contains("ISRG Root X1") } shouldBe true
		nombres.any { it.contains("Root YR") } shouldBe true
		// Y sigue habiendo muchas del sistema: no hemos sustituido la confianza,
		// la hemos ampliado.
		(aceptados.size > 10) shouldBe true
	}

	// ---- A qué conexiones se aplica (28-oct) ----
	//
	// El cuarto modo de fallo, y el que de verdad ocurrió: la confianza está
	// bien construida y **no se le aplica a una de las conexiones**. Costó tres
	// semanas con el `config.json` y no da error visible, así que se prueba
	// aquí y no mirando una caja. `openConnection()` no conecta, de modo que
	// esto no toca la red.

	fun fabricaDeMentira(): SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory

	"a una conexión TLS se le pone la fábrica con las raíces incluidas" {
		val conexion = URL("https://authjelly.satmx.net/v1/resolve").openConnection() as HttpsURLConnection
		val nuestra = fabricaDeMentira()
		RegistryTrust.aplicarA(conexion) { nuestra }
		conexion.sslSocketFactory shouldBe nuestra
	}

	"una conexión en claro no se toca, y ni siquiera se construye la confianza" {
		// Lo segundo importa en una caja vieja: construirla son cuatro
		// certificados leídos de los recursos, y en la LAN del ISP el
		// `config.json` va por `http` en todas las sintonías.
		var preguntada = false
		val conexion = URL("http://192.168.20.249:8097/config.json").openConnection()
		RegistryTrust.aplicarA(conexion) { preguntada = true; fabricaDeMentira() }
		preguntada shouldBe false
	}

	"si la confianza no se pudo construir, la conexión se queda con la del sistema" {
		// `confianza()` devuelve `null` cuando falla, y eso no puede dejar la
		// conexión sin fábrica ni reventar: en una caja moderna la del sistema
		// funciona igual.
		val conexion = URL("https://authjelly.satmx.net/v1/resolve").openConnection() as HttpsURLConnection
		val delSistema = conexion.sslSocketFactory
		RegistryTrust.aplicarA(conexion) { null }
		conexion.sslSocketFactory shouldBe delSistema
	}
})
