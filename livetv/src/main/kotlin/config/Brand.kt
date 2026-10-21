package org.jellyfin.livetv.config

import org.jellyfin.livetv.diagnostics.LiveLog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * La marca del proveedor, en ranuras fijas.
 *
 * **Ranuras, no tema libre** (`CLAUDE.md` §8). No hay forma de enviar una hoja
 * de estilos ni una disposición: el servicio **elige entre** lo que el APK ya
 * sabe hacer. Con colores libres, tarde o temprano un ISP elige un acento que
 * sobre el fondo de la sección no se lee, y la llamada de soporte es nuestra.
 *
 * Por eso cada ranura tiene un respaldo que **siempre funciona**, y el color se
 * valida antes de usarse.
 */
data class Brand(
	val displayName: String,
	/** `0xAARRGGBB`. Ya validado: si el del servidor no se leía, este es el neutro. */
	val accent: Int,
	val logoUrl: String? = null,
	/** De dónde salió, para el diagnóstico: `servidor`, `neutra`, `parcial`. */
	val origen: String = "neutra",
) {
	companion object {
		/**
		 * La marca que se ve **antes de entrar, sin red y sin configurar**.
		 *
		 * No es un caso raro: es la primera pantalla de toda instalación. No
		 * puede decir Jellyfin ni parecer un error, así que se diseña, no se
		 * improvisa.
		 */
		val NEUTRA = Brand(displayName = "TV en vivo", accent = ACENTO_NEUTRO, origen = "neutra")
	}
}

/** Gris claro: legible sobre el negro de la sección y sobre el claro de la app. */
const val ACENTO_NEUTRO = 0xFFBDBDBD.toInt()

/**
 * El fondo de la sección de canales, contra el que se mide el contraste.
 *
 * **No es negro puro, y medirlo contra negro puro era medir contra una
 * pantalla que no existe.** El fondo del tema de la app es `#101010`
 * (`ui/base/colorScheme.kt`), y sobre él todo contrasta un 10 % menos: un
 * acento que pasaba justo con 3,00 vale 2,72 donde se va a ver de verdad.
 *
 * Si algún día el tema cambia de fondo, este valor cambia con él. No hay forma
 * de leerlo desde aquí: el módulo `livetv` no depende de `app` (§4.1).
 */
private const val FONDO_SECCION = 0xFF101010.toInt()

/**
 * Contraste mínimo exigido al acento.
 *
 * 3:1 es el umbral de WCAG para texto grande y elementos de interfaz, que es
 * justo lo que el acento va a ser: títulos y marcas de selección, nunca párrafos.
 * Pedir 4.5 descartaría colores de marca perfectamente legibles a tres metros.
 */
private const val CONTRASTE_MINIMO = 3.0

object BrandParser {

	/**
	 * Interpreta la marca del `config.json`. Nunca falla: cae a la neutra.
	 *
	 * Que devuelva algo siempre es deliberado. La marca es lo primero que se ve,
	 * y un `config.json` con una errata en el color no puede dejar la sección
	 * sin identidad.
	 */
	fun parse(displayName: String?, accent: String?, logoUrl: String?): Brand {
		val nombre = displayName?.trim()?.takeIf { it.isNotEmpty() }
		val logo = logoUrl?.trim()?.takeIf { it.isNotEmpty() }
		val color = colorValidado(accent)

		if (nombre == null && color == null && logo == null) return Brand.NEUTRA

		val completa = nombre != null && color != null
		return Brand(
			displayName = nombre ?: Brand.NEUTRA.displayName,
			accent = color ?: ACENTO_NEUTRO,
			logoUrl = logo,
			origen = if (completa) "servidor" else "parcial",
		)
	}

	/**
	 * `#RRGGBB` legible sobre el fondo de la sección, o `null`.
	 *
	 * Se rechaza por dos motivos distintos y los dos se registran por separado:
	 * mal escrito es un error del operador que puede corregir; ilegible es una
	 * elección suya que no podemos respetar.
	 */
	fun colorValidado(crudo: String?): Int? {
		val texto = crudo?.trim() ?: return null
		if (texto.isEmpty()) return null

		val hex = texto.removePrefix("#")
		if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) {
			LiveLog.warn("marca.colorMalFormado", "accent" to texto)
			return null
		}

		val color = 0xFF000000.toInt() or hex.toInt(16)
		val contraste = contraste(color, FONDO_SECCION)
		if (contraste < CONTRASTE_MINIMO) {
			LiveLog.warn(
				"marca.colorIlegible",
				"accent" to texto,
				"contraste" to "%.2f".format(contraste),
				"minimo" to CONTRASTE_MINIMO,
			)
			return null
		}
		return color
	}

	/** Razón de contraste de WCAG, de 1 a 21. */
	fun contraste(a: Int, b: Int): Double {
		val la = luminancia(a)
		val lb = luminancia(b)
		return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
	}

	private fun luminancia(color: Int): Double {
		fun canal(v: Int): Double {
			val s = v / 255.0
			return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
		}
		val r = canal((color shr 16) and 0xFF)
		val g = canal((color shr 8) and 0xFF)
		val bl = canal(color and 0xFF)
		return 0.2126 * r + 0.7152 * g + 0.0722 * bl
	}
}
