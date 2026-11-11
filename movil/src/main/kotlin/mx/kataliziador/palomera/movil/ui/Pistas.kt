package mx.kataliziador.palomera.movil.ui

import org.jellyfin.livetv.source.LanguageNormalizer

/**
 * Una pista de audio o de subtítulos, como la ve el abonado.
 *
 * [id] es el identificador del grupo dentro del reproductor; lo demás es lo
 * que se enseña.
 */
data class Pista(
	val id: String,
	val idioma: String?,
	val canales: Int,
	/** El rótulo que trae el propio stream, si trae alguno. */
	val etiqueta: String?,
)

/**
 * Cómo se llama una pista en la lista.
 *
 * ## Por qué importa el nombre
 *
 * Porque `CLAUDE.md` §6 dice que **la etiqueta de idioma puede mentir**: en
 * algunos satélites una pista marcada `spa` trae inglés. Cambiar de audio es
 * el único remedio, y para cambiar hay que poder distinguir una fila de otra.
 * «Pista 1 / Pista 2 / Pista 3» no distingue nada.
 *
 * El orden es: el rótulo del stream si lo trae, luego el idioma en español, y
 * si no hay ni eso, el número — que al menos se diferencia del de al lado.
 */
fun nombreDePista(pista: Pista): String {
	val idioma = LanguageNormalizer.normalize(pista.idioma?.takeIf { it.isNotBlank() })

	// **La etiqueta manda sólo si dice algo que el idioma no diga.** Medido el
	// 11-nov en Las Estrellas: Flussonic rotula las pistas «spa a1» y «eng a2»
	// —nombres técnicos de pista, no nombres para una persona—, y dándoles
	// preferencia la lista decía eso en vez de «Español» e «Inglés»: lo mismo,
	// peor dicho. Lo que sí aporta es «Audio descripción» o «Comentarios».
	pista.etiqueta?.takeIf { it.isNotBlank() && !repiteElIdioma(it, idioma) }
		?.let { return it }

	if (idioma == null) return "Pista ${pista.id}"

	val nombre = NOMBRES[idioma] ?: idioma.uppercase()

	// Los canales sólo cuando dicen algo: un estéreo es lo normal y ponerlo en
	// todas las filas es ruido. Lo que hay que distinguir es el 5.1 del
	// estéreo cuando los dos están en español.
	return when (pista.canales) {
		1 -> "$nombre · Mono"
		6 -> "$nombre · 5.1"
		8 -> "$nombre · 7.1"
		else -> nombre
	}
}

private val NOMBRES = mapOf(
	"spa" to "Español",
	"eng" to "Inglés",
	"por" to "Portugués",
	"fra" to "Francés",
	"deu" to "Alemán",
	"ita" to "Italiano",
	"jpn" to "Japonés",
)

/**
 * Si una etiqueta no dice nada que el idioma no diga ya.
 *
 * Mira la primera palabra: «spa a1» empieza por el código del idioma, y
 * «Español» es su nombre. Las dos son redundantes. «Audio descripción» no
 * empieza por ninguno de los dos, así que aporta.
 */
private fun repiteElIdioma(etiqueta: String, idioma: String?): Boolean {
	if (idioma == null) return false
	val primera = etiqueta.trim().substringBefore(' ')
	if (LanguageNormalizer.normalize(primera) == idioma) return true
	return primera.equals(NOMBRES[idioma], ignoreCase = true)
}
