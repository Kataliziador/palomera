package org.jellyfin.livetv.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.jellyfin.livetv.LiveTuning
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Logging estructurado del módulo, desde el commit #1 (CLAUDE.md regla 9).
 *
 * Dos destinos a la vez: Timber, para `adb logcat`, y un buffer circular en
 * memoria que se exporta a archivo desde la app. El buffer es lo que importa:
 * cuando un suscriptor reporta una falla, nadie va a tener un `adb` conectado.
 *
 * Cada entrada lleva el contexto que hace falta para diagnosticar sin la caja
 * delante: canal, URL, códec, modelo, decoder activo y estado del buffer
 * (CLAUDE.md regla 8).
 */
object LiveLog {
	private const val TAG = "livetv"

	data class Entry(
		val timestamp: Long,
		val level: Level,
		val event: String,
		val context: Map<String, Any?>,
		val error: Throwable?,
	)

	enum class Level { DEBUG, INFO, WARN, ERROR }

	private val ring = ArrayDeque<Entry>(LiveTuning.LOG_RING_CAPACITY)
	private val lock = Any()

	/**
	 * Contexto para el volcado automático. Sin esto el log solo sale si alguien
	 * pulsa un botón, que es inútil en la caja de un suscriptor a 2000 km —
	 * justo donde hacen falta los datos.
	 */
	@Volatile
	private var appContext: Context? = null

	@Volatile
	private var lastAutoExportMs = 0L

	private const val AUTO_EXPORT_MIN_GAP_MS = 120_000L

	/** Contexto que acompaña a toda entrada mientras no cambie el canal. */
	@Volatile
	private var session: Map<String, Any?> = emptyMap()

	/**
	 * Activa el volcado automático a Descargas en cuanto se registre un error.
	 *
	 * Con antirrebote: una ráfaga de errores durante una tormenta no puede
	 * convertirse en cien archivos.
	 */
	fun enableAutoExport(context: Context) {
		appContext = context.applicationContext
		info("log.autoExport", "minimoEntreVolcados" to "${AUTO_EXPORT_MIN_GAP_MS / 1000}s")
	}

	fun startSession(channelId: String, url: String, videoCodec: String?, audioCodec: String?) {
		session = mapOf(
			"channel" to channelId,
			"url" to url,
			"videoCodec" to videoCodec,
			"audioCodec" to audioCodec,
			"device" to "${Build.MANUFACTURER}/${Build.MODEL}/${Build.DEVICE}",
			"sdk" to Build.VERSION.SDK_INT,
		)
		info("session.start")
	}

	fun debug(event: String, vararg context: Pair<String, Any?>) = log(Level.DEBUG, event, context, null)
	fun info(event: String, vararg context: Pair<String, Any?>) = log(Level.INFO, event, context, null)
	fun warn(event: String, vararg context: Pair<String, Any?>) = log(Level.WARN, event, context, null)

	/**
	 * No existe el error silencioso (CLAUDE.md regla 8). Todo `catch` termina
	 * aquí, con su causa y su contexto.
	 */
	fun error(event: String, error: Throwable?, vararg context: Pair<String, Any?>) =
		log(Level.ERROR, event, context, error)

	private fun log(level: Level, event: String, context: Array<out Pair<String, Any?>>, error: Throwable?) {
		val merged = session + context.toMap()
		val entry = Entry(System.currentTimeMillis(), level, event, merged, error)

		synchronized(lock) {
			if (ring.size >= LiveTuning.LOG_RING_CAPACITY) ring.removeFirst()
			ring.addLast(entry)
		}

		val line = format(entry)
		when (level) {
			Level.DEBUG -> Timber.tag(TAG).d(line)
			Level.INFO -> Timber.tag(TAG).i(line)
			Level.WARN -> Timber.tag(TAG).w(line)
			Level.ERROR -> {
				Timber.tag(TAG).e(error, line)
				maybeAutoExport()
			}
		}
	}

	private fun maybeAutoExport() {
		val ctx = appContext ?: return
		val ahora = System.currentTimeMillis()
		if (ahora - lastAutoExportMs < AUTO_EXPORT_MIN_GAP_MS) return
		lastAutoExportMs = ahora

		// Fuera del hilo que registró el error: escribir en disco no puede
		// bloquear la reproducción.
		Thread {
			runCatching { export(ctx, automatic = true) }
				.onFailure { Timber.tag(TAG).e(it, "no se pudo volcar el log automáticamente") }
		}.start()
	}

	private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
	private val stampCompleto = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

	/**
	 * Cabecera del volcado.
	 *
	 * Sin ella el archivo empieza en seco y no se sabe de qué caja ni de qué
	 * canal es, ni qué tramo de reloj cubre — que es lo primero que hace falta
	 * cuando alguien dice «lo vi a las nueve y cuarto».
	 */
	private fun encabezado(entradas: List<Entry>, automatic: Boolean): String = buildString {
		val ahora = System.currentTimeMillis()
		append("# volcado ").append(if (automatic) "automático" else "manual")
		append(" · ").append(stampCompleto.format(Date(ahora))).append('\n')
		append("# entradas: ").append(entradas.size)
			.append(" de ").append(LiveTuning.LOG_RING_CAPACITY).append('\n')

		val primera = entradas.firstOrNull()?.timestamp
		val ultima = entradas.lastOrNull()?.timestamp
		if (primera != null && ultima != null) {
			append("# tramo: ").append(stampCompleto.format(Date(primera)))
				.append(" → ").append(stampCompleto.format(Date(ultima)))
				.append("  (").append((ultima - primera) / 60_000).append(" min)").append('\n')
			if (entradas.size >= LiveTuning.LOG_RING_CAPACITY) {
				append("# AVISO: el buffer se llenó, falta lo anterior a esa hora\n")
			}
		}

		session.forEach { (k, v) -> append("# ").append(k).append(": ").append(v).append('\n') }
		append("#\n")
		append("# las líneas omiten los datos de sesión de arriba para no repetirlos\n")
		append("#\n")
	}

	/**
	 * Una línea por entrada, **sin** los datos de sesión: ya están en la
	 * cabecera, y repetirlos en cada latido multiplicaría el archivo por cinco
	 * sin añadir nada.
	 */
	private fun format(entry: Entry): String = buildString {
		append(stamp.format(Date(entry.timestamp)))
		append(' ').append(entry.level.name)
		append(' ').append(entry.event)
		entry.context.forEach { (k, v) ->
			if (k !in session) append(' ').append(k).append('=').append(v)
		}
		entry.error?.let { append(" error=").append(it::class.java.simpleName).append(':').append(it.message) }
	}

	fun snapshot(): List<Entry> = synchronized(lock) { ring.toList() }

	/**
	 * Exporta el buffer a **Descargas**, que es el único sitio del que alguien
	 * puede sacarlo después.
	 *
	 * El destino importa más de lo que parece: escribir en
	 * `getExternalFilesDir()` parece funcionar, pero en Android 10+ ese
	 * directorio no es accesible ni por `adb pull` ni por un gestor de
	 * archivos, así que el log quedaba encerrado en la caja y la función no
	 * servía para nada. Se comprobó midiendo, no suponiendo.
	 *
	 * Devuelve una descripción legible de dónde quedó, para enseñarla al operador.
	 */
	fun export(context: Context, automatic: Boolean = false): String {
		// `.txt` y no `.log`: MediaStore obliga a que la extensión concuerde con
		// el tipo MIME y, si no, añade la suya. El nombre que se le enseña al
		// operador tiene que ser el real, o no encontrará el archivo.
		val prefijo = if (automatic) "livetv-auto-" else "livetv-"
		val name = prefijo + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".txt"
		val entradas = snapshot()
		val body = encabezado(entradas, automatic) + entradas.joinToString("\n") { format(it) }

		val destino = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val values = ContentValues().apply {
				put(MediaStore.MediaColumns.DISPLAY_NAME, name)
				put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
				put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
			}
			val uri = context.contentResolver
				.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
				?: error("MediaStore no devolvió URI para $name")
			context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
				?: error("No se pudo abrir $uri para escribir")
			"Descargas/$name"
		} else {
			// API < 29: el directorio público es escribible directamente.
			val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
			val file = File(dir, name)
			file.writeText(body)
			file.absolutePath
		}

		// Con Timber directamente: pasar por `error()` volvería a disparar el
		// volcado automático.
		Timber.tag(TAG).i("log.exported destino=$destino automatico=$automatic entradas=${snapshot().size}")
		return destino
	}

	/** Volcados ya presentes en Descargas, para enseñarlos en el menú. */
	fun exportedFiles(context: Context): List<String> = runCatching {
		context.contentResolver.query(
			MediaStore.Downloads.EXTERNAL_CONTENT_URI,
			arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
			MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
			arrayOf("livetv-%"),
			MediaStore.MediaColumns.DATE_ADDED + " DESC",
		)?.use { c ->
			generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
		}.orEmpty()
	}.getOrElse {
		Timber.tag(TAG).w(it, "no se pudo listar los volcados")
		emptyList()
	}
}
