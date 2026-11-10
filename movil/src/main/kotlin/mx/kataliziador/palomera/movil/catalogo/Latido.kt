package mx.kataliziador.palomera.movil.catalogo

/**
 * Lo que la app le cuenta a Jellyfin mientras el abonado ve algo.
 *
 * ## Qué pasaba sin esto, y está medido
 *
 * **10-nov**: una película reproduciéndose cinco minutos en el teléfono y
 * `GET /Sessions` del site devolviendo dos sesiones, **ninguna del teléfono**.
 * La app reproducía y el servidor no se enteraba de nada.
 *
 * Lo caro no era la sesión invisible: era que **«Seguir viendo» no avanzaba**.
 * Ese carril es lo primero del inicio y se alimenta de
 * `PlaybackPositionTicks`, que sólo escribe quien reproduce. Media película en
 * el móvil se encontraba sin empezar, en el móvil y en la caja.
 *
 * Aquí vive sólo la **decisión** —cuándo avisar y de qué—, que es la parte que
 * puede equivocarse en silencio. El envío está en [Jellyfin].
 */

/** Qué se le está contando al servidor. */
enum class Aviso {
	/** Empieza una reproducción: es lo que crea la sesión visible en el panel. */
	EMPEZO,

	/** Sigue en marcha. Es el que escribe la posición de «Seguir viendo». */
	AVANZA,

	/**
	 * Se acabó o se salió.
	 *
	 * **No es opcional.** Sin él Jellyfin se queda con la última posición del
	 * latido —hasta diez segundos atrás— y la sesión sigue colgada en el panel
	 * como si el abonado estuviera viendo algo.
	 */
	PARO,
}

/** Los ticks de Jellyfin son unidades de 100 ns, así que un ms son 10 000. */
fun aTicks(ms: Long): Long = ms * 10_000L

/**
 * Si toca mandar un aviso de progreso.
 *
 * Cada [cadaMs] mientras no cambie nada, **y en el acto cuando cambia la
 * pausa**: el estado de pausa es lo que el panel del proveedor enseña al
 * operador mientras habla por teléfono con el abonado, y diez segundos de
 * retraso ahí son diez segundos de información falsa.
 */
fun tocaLatido(
	ultimoMs: Long,
	ahoraMs: Long,
	pausaAnterior: Boolean,
	pausaAhora: Boolean,
	cadaMs: Long = 10_000L,
): Boolean = pausaAnterior != pausaAhora || ahoraMs - ultimoMs >= cadaMs

/**
 * Si esta reproducción es de las que se le cuentan a Jellyfin.
 *
 * **Un canal en vivo no.** No es un título de su biblioteca, no tiene posición
 * que reanudar, y meterlo en el historial llenaría «Seguir viendo» de cosas que
 * nadie puede continuar.
 */
fun seReporta(enDirecto: Boolean, itemId: String?): Boolean =
	!enDirecto && !itemId.isNullOrBlank()
