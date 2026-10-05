package org.jellyfin.livetv.epg

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * El XMLTV, probado sin emulador.
 *
 * SAX es de la plataforma **y de la JVM**, así que lo que puede salir mal aquí
 * —fechas, correspondencia de canales, ventana, topes— se prueba de verdad y no
 * mirando una barra en una tele. Lo que sí necesita caja es que la barra pinte
 * lo que esto devuelve, y eso es otra cosa.
 */
class XmltvParserTest : StringSpec({

	// 2026-10-05 18:00:00 UTC
	val ahora = 1791223200000L
	val hora = 3_600_000L

	fun xml(programas: String) = """
		<?xml version="1.0" encoding="utf-8"?>
		<tv generator-info-name="servidor-canales">
		 <channel id="uno"><display-name>Uno</display-name></channel>
		 $programas
		</tv>
	""".trimIndent().byteInputStream()

	fun programa(canal: String, inicio: String, fin: String, titulo: String, extra: String = "") =
		"""<programme start="$inicio" stop="$fin" channel="$canal">
		   <title lang="es">$titulo</title>$extra</programme>"""

	"lo que dan ahora y lo que viene después" {
		val guia = XmltvParser.parse(
			xml(
				programa("uno", "20261005173000 +0000", "20261005183000 +0000", "Noticiero") +
					programa("uno", "20261005183000 +0000", "20261005193000 +0000", "Cine")
			),
			setOf("uno"),
			ahora,
		)
		val franja = guia.franja("uno", ahora)!!
		franja.ahora!!.titulo shouldBe "Noticiero"
		franja.luego!!.titulo shouldBe "Cine"
	}

	"la descripción no se cuela en el título" {
		// El fallo que esto fija: `characters()` llega para **todo** el texto
		// del documento, así que sin distinguir dónde estamos, el `<desc>`
		// acaba pegado al título y la barra enseña un párrafo.
		val guia = XmltvParser.parse(
			xml(
				programa(
					"uno", "20261005173000 +0000", "20261005183000 +0000", "Noticiero",
					extra = "<desc lang=\"es\">Un resumen de la jornada con invitados.</desc>",
				)
			),
			setOf("uno"),
			ahora,
		)
		guia.franja("uno", ahora)!!.ahora!!.titulo shouldBe "Noticiero"
	}

	"un canal que no está en el config.json se descarta" {
		// Un site puede servir la guía de un lineup mayor que el que publica.
		val guia = XmltvParser.parse(
			xml(programa("otro", "20261005173000 +0000", "20261005183000 +0000", "Lo que sea")),
			setOf("uno"),
			ahora,
		)
		guia.canales shouldBe 0
		guia.franja("otro", ahora) shouldBe null
	}

	"un programa largo que ya empezó sigue siendo el de ahora" {
		// Es la razón de ser del margen hacia atrás: un partido de tres horas
		// que empezó hace dos es justo lo que la barra tiene que enseñar, y sin
		// margen se habría descartado por empezar antes de la ventana.
		val guia = XmltvParser.parse(
			xml(programa("uno", "20261005160000 +0000", "20261005190000 +0000", "El partido")),
			setOf("uno"),
			ahora,
		)
		guia.franja("uno", ahora)!!.ahora!!.titulo shouldBe "El partido"
	}

	"lo de mañana queda fuera de la ventana" {
		val guia = XmltvParser.parse(
			xml(programa("uno", "20261006180000 +0000", "20261006190000 +0000", "Mañana")),
			setOf("uno"),
			ahora,
		)
		guia.programas shouldBe 0
	}

	"sin «stop» se descarta: media información miente en la barra de progreso" {
		val guia = XmltvParser.parse(
			xml("""<programme start="20261005173000 +0000" channel="uno"><title>Sin fin</title></programme>"""),
			setOf("uno"),
			ahora,
		)
		guia.programas shouldBe 0
	}

	"sin título se descarta" {
		val guia = XmltvParser.parse(
			xml("""<programme start="20261005173000 +0000" stop="20261005183000 +0000" channel="uno"/>"""),
			setOf("uno"),
			ahora,
		)
		guia.programas shouldBe 0
	}

	"una entrada mal formada no tira las demás" {
		val guia = XmltvParser.parse(
			xml(
				"""<programme start="no-es-una-fecha" stop="20261005183000 +0000" channel="uno"><title>Mala</title></programme>""" +
					programa("uno", "20261005173000 +0000", "20261005183000 +0000", "Buena")
			),
			setOf("uno"),
			ahora,
		)
		guia.programas shouldBe 1
		guia.franja("uno", ahora)!!.ahora!!.titulo shouldBe "Buena"
	}

	"el desfase horario se aplica" {
		// Las mismas 13:30 de Ciudad de México son las 18:30 UTC. Si el desfase
		// se ignorara, el programa saldría cinco horas corrido y la barra
		// enseñaría lo de la tarde a media mañana.
		val guia = XmltvParser.parse(
			xml("""<programme start="20261005123000 -0500" stop="20261005133000 -0500" channel="uno"><title>Mediodía</title></programme>"""),
			setOf("uno"),
			ahora,
		)
		val p = guia.franja("uno", ahora)!!.ahora!!
		p.titulo shouldBe "Mediodía"
		// 12:30 -0500 == 17:30 UTC, media hora antes del «ahora» de la prueba.
		(ahora - p.inicioMs) shouldBe hora / 2
	}

	"una fecha sin desfase se toma como UTC" {
		val guia = XmltvParser.parse(
			xml("""<programme start="20261005173000" stop="20261005183000" channel="uno"><title>Sin huso</title></programme>"""),
			setOf("uno"),
			ahora,
		)
		guia.franja("uno", ahora)!!.ahora!!.titulo shouldBe "Sin huso"
	}

	"pasado el tope por canal se deja de añadir" {
		// Un XMLTV con un programa por minuto no puede llenar la memoria de la
		// caja. Se fabrican 200 de cinco minutos: caben 64.
		val programas = (0 until 200).joinToString("") { i ->
			val inicio = ahora + i * 5 * 60_000L
			programa("uno", marca(inicio), marca(inicio + 5 * 60_000L), "P$i")
		}
		val guia = XmltvParser.parse(xml(programas), setOf("uno"), ahora)
		guia.programas shouldBe 64
	}

	"se ordena por hora aunque el documento no lo esté" {
		// XMLTV no promete orden, y `Guia.franja()` recorre la lista dando por
		// hecho que lo hay.
		val guia = XmltvParser.parse(
			xml(
				programa("uno", "20261005183000 +0000", "20261005193000 +0000", "Segundo") +
					programa("uno", "20261005173000 +0000", "20261005183000 +0000", "Primero")
			),
			setOf("uno"),
			ahora,
		)
		val franja = guia.franja("uno", ahora)!!
		franja.ahora!!.titulo shouldBe "Primero"
		franja.luego!!.titulo shouldBe "Segundo"
	}

	"un hueco en la parrilla deja «ahora» vacío y «luego» lleno" {
		val guia = XmltvParser.parse(
			xml(programa("uno", "20261005190000 +0000", "20261005200000 +0000", "Dentro de una hora")),
			setOf("uno"),
			ahora,
		)
		val franja = guia.franja("uno", ahora)!!
		franja.ahora shouldBe null
		franja.luego!!.titulo shouldBe "Dentro de una hora"
	}

	"una entidad externa no se resuelve" {
		// Es la protección que importa y la única que funciona igual en la JVM
		// y en Android: el SAX de Android no conoce las banderas de Xerces
		// —medido el 05-oct en la caja—, pero sí pregunta al resolvedor antes
		// de resolver nada, y ahí la respuesta es siempre un documento vacío.
		//
		// Se apunta a un fichero que existe en las dos: si se resolviera, el
		// título traería su contenido o la lectura fallaría. Lo que tiene que
		// pasar es que no traiga nada.
		val conEntidad = (
			"""<?xml version="1.0"?>""" +
				"""<!DOCTYPE tv [<!ENTITY fuga SYSTEM "file:///etc/hostname">]>""" +
				"""<tv><channel id="uno"/>""" +
				"""<programme start="20261005173000 +0000" stop="20261005183000 +0000" channel="uno">""" +
				"""<title>Limpio&fuga;</title></programme></tv>"""
			).byteInputStream()

		// En la JVM el `DOCTYPE` se rechaza de entrada; en Android se analiza y
		// la entidad se queda vacía. Las dos salidas son correctas; lo que no
		// puede pasar es que el fichero acabe en el título.
		val resultado = runCatching { XmltvParser.parse(conEntidad, setOf("uno"), ahora) }
		val titulo = resultado.getOrNull()?.franja("uno", ahora)?.ahora?.titulo
		(titulo == null || titulo == "Limpio") shouldBe true
	}
})

/** `YYYYMMDDHHMMSS +0000` a partir de un instante, para fabricar pruebas. */
private fun marca(ms: Long): String {
	val c = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
	c.timeInMillis = ms
	return "%04d%02d%02d%02d%02d%02d +0000".format(
		c.get(java.util.Calendar.YEAR),
		c.get(java.util.Calendar.MONTH) + 1,
		c.get(java.util.Calendar.DAY_OF_MONTH),
		c.get(java.util.Calendar.HOUR_OF_DAY),
		c.get(java.util.Calendar.MINUTE),
		c.get(java.util.Calendar.SECOND),
	)
}
