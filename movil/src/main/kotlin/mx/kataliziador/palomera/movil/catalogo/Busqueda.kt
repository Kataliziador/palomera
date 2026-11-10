package mx.kataliziador.palomera.movil.catalogo

/**
 * Lo mínimo que hay que teclear para que se consulte al servidor del ISP.
 *
 * **Dos, y es una decisión sobre su CPU.** La búsqueda consulta mientras el
 * abonado teclea, así que cada tecla sería una petición; y con una sola letra
 * Jellyfin devuelve medio catálogo —16 233 elementos en el site de pruebas—,
 * que ni sirve a quien busca ni le sienta bien a un servidor que atiende a
 * todas las casas del ISP a la vez.
 *
 * Los canales no pasan por aquí: ya están en memoria y se filtran desde la
 * primera letra, sin pedirle nada a nadie.
 */
const val MINIMO_PARA_BUSCAR = 2

/** Si esta consulta merece una petición. Los espacios no cuentan como letras. */
fun sePreguntaAlServidor(consulta: String): Boolean =
	consulta.trim().length >= MINIMO_PARA_BUSCAR
