package mx.kataliziador.palomera.movil

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mx.kataliziador.palomera.movil.catalogo.Reproduccion
import mx.kataliziador.palomera.movil.catalogo.leerReproduccion
import mx.kataliziador.palomera.movil.catalogo.perfilDeDispositivo
import org.jellyfin.livetv.capabilities.DecoderInfo
import org.jellyfin.livetv.capabilities.DeviceCapabilities

/**
 * Cómo se reproduce un título: directo, o transcodificando.
 *
 * ## Por qué esto importa más que casi nada
 *
 * Es el riesgo R2 de `MOVIL_PLAN.md`, y **falla en los dos sentidos**. Si el
 * perfil promete de más, el abonado ve una pantalla negra; si se queda corto,
 * el servidor del ISP transcodifica **para todas las casas a la vez** y nadie
 * se entera hasta que su CPU llega al techo.
 *
 * ## Lo medido el 09-nov contra el site de los probadores
 *
 * Con «Top Gun: Maverick» —un `mkv` con subtítulos `.srt` al lado— y el mismo
 * perfil salvo una línea:
 *
 * | perfil | resultado |
 * |---|---|
 * | sin declarar subtítulos externos | `SupportsDirectPlay: false` → transcodifica |
 * | declarando `subrip`/`srt` como `External` | `SupportsDirectPlay: true` |
 *
 * **Una línea que falta en el perfil pone a transcodificar al servidor del ISP
 * cada título que tenga un `.srt` al lado.** Este catálogo los tiene, así que
 * la última prueba de este archivo está para que nadie la quite sin enterarse.
 */
class ReproduccionTest : FunSpec({

	val base = "http://site:8096"
	val token = "abc123"
	val id = "0ee08fcc"

	test("si el servidor dice que si, se reproduce directo") {
		val json = """
			{"MediaSources":[{"Id":"src1","Container":"mkv",
			  "SupportsDirectPlay":true,"SupportsDirectStream":true,"SupportsTranscoding":true}]}
		""".trimIndent()

		val r = leerReproduccion(json, base, token, id) as Reproduccion.Directo

		r.url shouldContain "/Videos/$id/stream"
		r.url shouldContain "static=true"
		r.url shouldContain "mediaSourceId=src1"
		r.url shouldContain "api_key=$token"
	}

	test("si no, se usa la url de transcodificacion y se le pega la llave") {
		// Medido: `TranscodingUrl` llega relativa y **sin** `api_key`. Sin
		// pegársela, el reproductor recibe un 401 y el abonado una pantalla
		// negra sin explicación.
		val json = """
			{"MediaSources":[{"Id":"src1","Container":"mkv",
			  "SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,
			  "TranscodingUrl":"/videos/abc/master.m3u8?&DeviceId=x&MediaSourceId=src1",
			  "TranscodeReasons":"ContainerNotSupported"}]}
		""".trimIndent()

		val r = leerReproduccion(json, base, token, id) as Reproduccion.Transcodificado

		r.url shouldBe "$base/videos/abc/master.m3u8?&DeviceId=x&MediaSourceId=src1&api_key=$token"
		r.motivo shouldBe "ContainerNotSupported"
	}

	test("sin directo y sin transcodificacion no hay nada que hacer") {
		val json = """
			{"MediaSources":[{"Id":"s","SupportsDirectPlay":false,
			  "SupportsDirectStream":false,"SupportsTranscoding":false}]}
		""".trimIndent()

		leerReproduccion(json, base, token, id) shouldBe Reproduccion.NoSePuede
	}

	test("una respuesta sin fuentes, o rota, no deja al reproductor a medias") {
		leerReproduccion("""{"MediaSources":[]}""", base, token, id) shouldBe Reproduccion.NoSePuede
		leerReproduccion("no es json", base, token, id) shouldBe Reproduccion.NoSePuede
	}

	test("el perfil declara lo que el aparato tiene de verdad") {
		val caps = DeviceCapabilities.paraPruebas(
			decoders = listOf(
				DecoderInfo("c2.mtk.avc.decoder", "video/avc", hardware = true, maxInstances = 1),
				DecoderInfo("c2.mtk.hevc.decoder", "video/hevc", hardware = true, maxInstances = 1),
				DecoderInfo("c2.android.aac.decoder", "audio/mp4a-latm", hardware = false, maxInstances = 1),
			),
		)

		val perfil = perfilDeDispositivo(caps)

		perfil shouldContain "h264"
		perfil shouldContain "hevc"
		perfil shouldContain "aac"
		// No tiene MPEG-2 —medido el 08-nov en el Xiaomi— y el perfil no
		// puede prometerlo: prometer de más es la pantalla negra.
		(perfil.contains("mpeg2")) shouldBe false
	}

	test("el perfil SIEMPRE declara los subtitulos externos") {
		// **No se quita.** Sin esta línea el servidor del ISP transcodifica
		// cada título con un `.srt` al lado, y eso no se nota hasta que su CPU
		// llega al techo con veinte casas viendo a la vez. Medido el 09-nov.
		val perfil = perfilDeDispositivo(DeviceCapabilities.paraPruebas(decoders = emptyList()))

		perfil shouldContain "\"Method\":\"External\""
		perfil shouldContain "subrip"
		perfil shouldContain "srt"
	}
})
