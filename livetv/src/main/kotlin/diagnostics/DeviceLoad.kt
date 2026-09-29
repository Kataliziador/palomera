package org.jellyfin.livetv.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Cuánto le está costando a la caja lo que está reproduciendo.
 *
 * Existe porque un microcorte tiene dos familias de causa que se parecen en
 * pantalla y no se parecen en nada por dentro: el stream llega mal, o la caja
 * no da abasto. Sin esta medida no se distinguen, y perseguir la equivocada
 * cuesta días — ya pasó.
 *
 * **Se muestrea fuera del hilo del reproductor.** Leer `/proc` es una lectura
 * de disco virtual: barata, pero no gratis, y el hilo que suelta los frames no
 * puede pagarla.
 */
class DeviceLoad(context: Context) {

	private val app = context.applicationContext
	private val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

	/** Núcleos de la caja. La de pruebas tiene 4 Cortex-A35. */
	private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

	/** Tics de reloj por segundo del kernel. Normalmente 100. */
	private val hz = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }
		.onFailure { LiveLog.error("carga.sysconf", it) }
		.getOrDefault(100L)
		.coerceAtLeast(1L)

	private var lastTicks = -1L
	private var lastSampleAtMs = 0L

	/** Se avisa una sola vez: un `/proc` cerrado no cambia a mitad de sesión. */
	private var avisadoProcStat = false
	private var avisadoLoadAvg = false

	data class Muestra(
		/** CPU de **nuestro proceso** como porcentaje de la caja entera. */
		val cpuPercent: Float,
		/** Carga media del sistema a 1 minuto, o `null` si el kernel no la expone. */
		val loadAvg1: Float?,
		val ramAvailableMb: Int,
		val ramTotalMb: Int,
		val cores: Int,
	)

	fun sample(): Muestra {
		val ahora = System.currentTimeMillis()

		val cpu = leerTicksDeProceso()?.let { ticks ->
			val anteriores = lastTicks
			val desde = lastSampleAtMs
			lastTicks = ticks
			lastSampleAtMs = ahora

			if (anteriores < 0 || ahora <= desde) return@let 0f
			val segundos = (ahora - desde) / 1000.0
			val segundosDeCpu = (ticks - anteriores).toDouble() / hz
			// Repartido entre todos los núcleos: 100 % significa la caja entera
			// ocupada por nosotros, no un núcleo saturado.
			((segundosDeCpu / (segundos * cores)) * 100.0).toFloat().coerceIn(0f, 100f)
		} ?: 0f

		val mem = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

		return Muestra(
			cpuPercent = cpu,
			loadAvg1 = leerLoadAvg(),
			ramAvailableMb = (mem.availMem / (1024 * 1024)).toInt(),
			ramTotalMb = (mem.totalMem / (1024 * 1024)).toInt(),
			cores = cores,
		)
	}

	/** `utime` + `stime` de `/proc/self/stat`, campos 14 y 15 tras el nombre. */
	private fun leerTicksDeProceso(): Long? {
		val texto = runCatching { File("/proc/${Process.myPid()}/stat").readText() }
			.getOrElse {
				if (!avisadoProcStat) {
					avisadoProcStat = true
					LiveLog.error("carga.procStatIlegible", it)
				}
				return null
			}

		// El nombre del proceso va entre paréntesis y puede contener espacios:
		// se corta por el último ')' antes de separar por espacios.
		val cola = texto.substringAfterLast(')').trim().split(' ')
		// Tras el ')' el primer campo es `state`, que es el número 3 del
		// formato: `utime` es el 14, así que cae en el índice 11.
		val utime = cola.getOrNull(11)?.toLongOrNull()
		val stime = cola.getOrNull(12)?.toLongOrNull()
		if (utime == null || stime == null) {
			if (!avisadoProcStat) {
				avisadoProcStat = true
				LiveLog.error("carga.procStatInesperado", null, "campos" to cola.size)
			}
			return null
		}
		return utime + stime
	}

	private fun leerLoadAvg(): Float? =
		runCatching { File("/proc/loadavg").readText().substringBefore(' ').toFloat() }
			.getOrElse {
				if (!avisadoLoadAvg) {
					avisadoLoadAvg = true
					LiveLog.error("carga.loadavgIlegible", it)
				}
				null
			}
}
