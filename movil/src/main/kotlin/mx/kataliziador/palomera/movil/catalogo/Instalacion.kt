package mx.kataliziador.palomera.movil.catalogo

import android.content.Context
import java.util.UUID

/**
 * El identificador que Jellyfin usa para distinguir este aparato de otro.
 *
 * **Es opaco y nuestro, no el del teléfono.** No se usa `ANDROID_ID` ni nada
 * derivado del hardware: eso es un identificador de dispositivo, el formulario
 * de Seguridad de los datos de Play obliga a declararlo, y aquí no hace falta
 * para nada. Un UUID aleatorio por instalación contesta la única pregunta que
 * el servidor hace —«¿eres el mismo de antes?»— y no dice nada más.
 *
 * Sobrevive a cerrar la app y muere al desinstalarla, que es exactamente lo que
 * se quiere: reinstalar es una tele nueva.
 */
fun Context.idDeInstalacion(): String {
	val prefs = getSharedPreferences("palomera", Context.MODE_PRIVATE)
	prefs.getString(CLAVE, null)?.let { return it }

	val nuevo = UUID.randomUUID().toString()
	prefs.edit().putString(CLAVE, nuevo).apply()
	return nuevo
}

private const val CLAVE = "idDeInstalacion"
