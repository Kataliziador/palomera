package mx.kataliziador.palomera.movil

/**
 * Quién es esta app, dicho por quien lo sabe.
 *
 * ## Por qué viene de fuera
 *
 * Porque `movil` es una **biblioteca**, no una app: no tiene versión ni
 * nombre de paquete propios, y fingir que los tiene es cómo se acaba con dos
 * valores que no coinciden. Hasta el 10-nov `CLIENT_NAME` y
 * `CLIENT_REPOSITORY` estaban declarados en los dos `build.gradle.kts` a la
 * vez, que es exactamente la forma en que el nombre del cliente de la caja
 * acabó mal durante un mes (`CLAUDE.md` §8).
 *
 * Ahora los declara `app`, que es la única que se publica, y los pasa aquí.
 */
data class Identidad(
	/** §8: «Palomera», no la marca del ISP. Identifica el aparato, no al proveedor. */
	val cliente: String,
	/** La oferta de la GPL-2.0 necesita una dirección (`PLAY.md` §5). */
	val repositorio: String,
	val version: String,
	/** Dónde preguntar «¿quién te da el servicio?» antes de saber ninguna dirección. */
	val registroUrl: String,
)
