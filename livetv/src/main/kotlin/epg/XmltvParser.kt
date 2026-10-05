package org.jellyfin.livetv.epg

import org.jellyfin.livetv.LiveTuning
import org.jellyfin.livetv.diagnostics.LiveLog
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/**
 * Convierte un XMLTV en una [Guia].
 *
 * ## Por qué SAX y no cargar el documento
 *
 * La guía de un lineup real pesa megas. Leerla entera a un `String` para luego
 * construir un árbol en memoria son dos copias del documento en una caja de 2
 * GB que además está decodificando vídeo. SAX recorre el flujo y solo retiene
 * lo que se decide guardar, que es una fracción minúscula.
 *
 * Y es de la plataforma: está en Android y en la JVM, así que esto **se prueba
 * sin emulador** y sin añadir una dependencia (regla 11).
 *
 * ## Qué se guarda y qué se tira
 *
 * Tres filtros, y los tres existen para que un XMLTV ajeno no pueda decidir
 * cuánta memoria usa la caja:
 *
 * 1. **Solo los canales que esta caja tiene.** Un site puede servir la guía de
 *    un lineup mayor que el que publica; lo que no está en `channels` no se
 *    va a enseñar nunca.
 * 2. **Solo una ventana alrededor de ahora** — unas horas hacia delante y un
 *    margen hacia atrás, porque un programa largo que ya empezó es justo el
 *    que la barra tiene que enseñar.
 * 3. **Topes por canal y de canales.** Un XMLTV con un programa por minuto no
 *    puede llenar la memoria.
 *
 * ## Qué se ignora en silencio, y qué no
 *
 * Un `<programme>` sin `channel`, sin `start`, sin título o con fechas que no
 * se entienden **se descarta solo**: perder la guía entera por una entrada mal
 * formada sería desproporcionado, igual que con los canales (`CLAUDE.md` §6).
 * Pero el total de descartados se registra, porque «la barra no enseña nada» y
 * «el generador emite fechas en otro formato» se parecen demasiado en pantalla.
 */
object XmltvParser {

	class GuiaInvalida(message: String) : Exception(message)

	/**
	 * @param canalesConocidos los `id` del `config.json`. Lo que no esté aquí
	 *   se descarta: es la correspondencia que fija `CONFIG_SCHEMA.md` §5.
	 */
	fun parse(
		entrada: InputStream,
		canalesConocidos: Set<String>,
		ahoraMs: Long,
	): Guia {
		val manejador = Manejador(canalesConocidos, ahoraMs)

		runCatching {
			val analizador = SAXParserFactory.newInstance()
				.apply { isNamespaceAware = false }
				.newSAXParser()
			blindar(analizador)
			analizador.parse(entrada, manejador)
		}.onFailure {
			throw GuiaInvalida("no se pudo leer el XMLTV: ${it.message}")
		}

		val guia = manejador.guia()
		LiveLog.info(
			"epg.interpretada",
			"canales" to guia.canales,
			"programas" to guia.programas,
			"descartados" to manejador.descartados,
			"fueraDeVentana" to manejador.fueraDeVentana,
			"canalesDesconocidos" to manejador.canalesDesconocidos.size,
		)
		if (manejador.canalesDesconocidos.isNotEmpty()) {
			// No es un error —un site puede servir la guía de un lineup mayor—
			// pero si son *todos*, lo que pasa es que los `id` no casan, y eso
			// sí es un fallo del generador que hay que poder ver.
			LiveLog.warn(
				"epg.canalesSinCorrespondencia",
				"cuantos" to manejador.canalesDesconocidos.size,
				"ejemplo" to manejador.canalesDesconocidos.first(),
			)
		}
		return guia
	}

	/**
	 * Impide que el XMLTV se convierta en una petición de red o en una lectura
	 * de fichero desde el analizador.
	 *
	 * **La protección que de verdad cuenta es la última**, y es la única que
	 * funciona igual en las dos plataformas. Las dos banderas de entidades
	 * externas y la de prohibir el `DOCTYPE` son de Xerces: la JVM las acepta y
	 * **el SAX de Android no** —medido el 05-oct en la caja, donde
	 * `disallow-doctype-decl` lanza y se llevaba por delante la guía entera—.
	 * Por eso cada una va en su propio intento y ninguna puede tumbar al resto.
	 *
	 * El resolvedor de entidades no es opcional ni depende del analizador: sea
	 * cual sea, si decide resolver una entidad nos lo pregunta, y aquí la
	 * respuesta es siempre un documento vacío. Eso cierra el agujero aunque las
	 * tres banderas fallen.
	 */
	private fun blindar(analizador: javax.xml.parsers.SAXParser) {
		for (bandera in BANDERAS) {
			runCatching { analizador.xmlReader.setFeature(bandera, false) }
		}
		runCatching {
			analizador.xmlReader.setFeature(
				"http://apache.org/xml/features/disallow-doctype-decl", true,
			)
		}
		analizador.xmlReader.setEntityResolver { _, _ ->
			org.xml.sax.InputSource(java.io.StringReader(""))
		}
	}

	private val BANDERAS = listOf(
		"http://xml.org/sax/features/external-general-entities",
		"http://xml.org/sax/features/external-parameter-entities",
	)

	private class Manejador(
		private val conocidos: Set<String>,
		private val ahoraMs: Long,
	) : DefaultHandler() {

		private val desde = ahoraMs - LiveTuning.EPG_MARGEN_PASADO_HORAS * 3_600_000L
		private val hasta = ahoraMs + LiveTuning.EPG_WINDOW_HOURS * 3_600_000L

		private val porCanal = LinkedHashMap<String, MutableList<Programa>>()

		var descartados = 0; private set
		var fueraDeVentana = 0; private set
		val canalesDesconocidos = LinkedHashSet<String>()

		/** El `<programme>` que se está leyendo, si interesa. */
		private var canal: String? = null
		private var inicioMs = 0L
		private var finMs = 0L
		private var titulo: StringBuilder? = null
		private var yaTengoTitulo = false

		/**
		 * Si el texto que llega ahora es del título.
		 *
		 * Hace falta un indicador aparte: `characters()` se llama para **todo**
		 * el texto del documento, así que sin esto el contenido de `<desc>` y
		 * los saltos de línea entre etiquetas se pegarían al título. Un
		 * `StringBuilder` no nulo no significa «estamos dentro», significa
		 * «hay un título empezado».
		 */
		private var dentroDelTitulo = false

		override fun startElement(uri: String?, local: String?, nombre: String, atributos: Attributes) {
			when (nombre) {
				"programme" -> empezarPrograma(atributos)
				// Solo el primer `<title>`: XMLTV admite uno por idioma y la
				// barra enseña uno. Quedarse con el primero es lo que hace todo
				// el mundo, y elegir por idioma exigiría saber cuál quiere el
				// abonado, que es una preferencia que aquí no existe.
				"title" -> if (canal != null && !yaTengoTitulo) {
					titulo = StringBuilder()
					dentroDelTitulo = true
				}
			}
		}

		override fun characters(buffer: CharArray, inicio: Int, largo: Int) {
			// SAX puede trocear el texto de un mismo elemento en varias
			// llamadas, así que se acumula en vez de asignarse.
			if (dentroDelTitulo) titulo?.append(buffer, inicio, largo)
		}

		override fun endElement(uri: String?, local: String?, nombre: String) {
			when (nombre) {
				// El `StringBuilder` se deja de alimentar pero no se tira: su
				// contenido es lo que se usa al cerrar el `<programme>`.
				"title" -> if (dentroDelTitulo) {
					dentroDelTitulo = false
					yaTengoTitulo = true
				}
				"programme" -> cerrarPrograma()
			}
		}

		private fun empezarPrograma(atributos: Attributes) {
			limpiar()
			val id = atributos.getValue("channel") ?: return
			if (id !in conocidos) {
				if (canalesDesconocidos.size < 50) canalesDesconocidos += id
				return
			}
			val inicio = FechaXmltv.aMillis(atributos.getValue("start")) ?: return
			// `stop` es opcional en XMLTV. Sin él no se sabe cuándo acaba, y un
			// programa sin fin haría que la barra de progreso mintiera para
			// siempre. Se descarta: media información aquí es peor que ninguna.
			val fin = FechaXmltv.aMillis(atributos.getValue("stop")) ?: return
			if (fin <= inicio) return

			if (fin <= desde || inicio >= hasta) {
				fueraDeVentana++
				return
			}

			canal = id
			inicioMs = inicio
			finMs = fin
		}

		private fun cerrarPrograma() {
			val id = canal
			val texto = titulo?.toString()?.trim()
			limpiar()
			if (id == null) return
			if (texto.isNullOrEmpty()) {
				descartados++
				return
			}

			val lista = porCanal[id]
				?: if (porCanal.size >= LiveTuning.EPG_MAX_CANALES) {
					descartados++
					return
				} else {
					mutableListOf<Programa>().also { porCanal[id] = it }
				}

			if (lista.size >= LiveTuning.EPG_MAX_PROGRAMAS_POR_CANAL) {
				descartados++
				return
			}
			lista += Programa(texto, inicioMs, finMs)
		}

		private fun limpiar() {
			canal = null
			titulo = null
			yaTengoTitulo = false
			dentroDelTitulo = false
		}

		fun guia(): Guia = Guia(
			// Se ordena aquí y no al consultarla: un XMLTV no promete orden, y
			// `Guia.franja()` recorre la lista dando por hecho que lo hay.
			porCanal.mapValues { (_, lista) -> lista.sortedBy { it.inicioMs } },
			interpretadaMs = ahoraMs,
		)
	}
}

/**
 * La fecha de XMLTV: `YYYYMMDDHHMMSS` seguida, opcionalmente, de ` ±HHMM`.
 *
 * Se interpreta a mano y no con `SimpleDateFormat` por dos razones. La primera
 * es que el formato es de ancho fijo y trivial, mientras que `SimpleDateFormat`
 * no es seguro entre hilos y obliga a una instancia por llamada. La segunda, y
 * la que decide: **una guía de 120 canales trae decenas de miles de fechas**, y
 * dos `parse()` por programa en una caja barata se notan.
 */
internal object FechaXmltv {

	fun aMillis(texto: String?): Long? {
		val t = texto?.trim() ?: return null
		if (t.length < 14) return null

		val anio = numero(t, 0, 4) ?: return null
		val mes = numero(t, 4, 6) ?: return null
		val dia = numero(t, 6, 8) ?: return null
		val hora = numero(t, 8, 10) ?: return null
		val minuto = numero(t, 10, 12) ?: return null
		val segundo = numero(t, 12, 14) ?: return null

		val calendario = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
			isLenient = false
			clear()
			set(anio, mes - 1, dia, hora, minuto, segundo)
		}
		val utc = runCatching { calendario.timeInMillis }.getOrNull() ?: return null

		return utc - desfaseMs(t)
	}

	/**
	 * El desfase declarado, en milisegundos, o cero.
	 *
	 * **Cero significa UTC, y es una suposición.** La especificación de XMLTV
	 * dice que sin desfase la fecha está en la hora local de quien la generó, y
	 * eso no se puede saber desde aquí: la caja puede estar en otro huso que el
	 * site. Nuestro generador emite siempre `+0000` (`INTEGRACION_CANALES.md`),
	 * así que el caso no debería darse; si se diera, la guía saldría corrida
	 * unas horas, que es visible y mejor que descartarla entera.
	 */
	private fun desfaseMs(t: String): Long {
		val signo = t.indexOfFirst { it == '+' || it == '-' }
		if (signo < 14 || t.length < signo + 5) return 0
		val horas = numero(t, signo + 1, signo + 3) ?: return 0
		val minutos = numero(t, signo + 3, signo + 5) ?: return 0
		val magnitud = (horas * 60L + minutos) * 60_000L
		return if (t[signo] == '-') -magnitud else magnitud
	}

	private fun numero(t: String, desde: Int, hasta: Int): Int? {
		var valor = 0
		for (i in desde until hasta) {
			val c = t[i]
			if (c !in '0'..'9') return null
			valor = valor * 10 + (c - '0')
		}
		return valor
	}
}
