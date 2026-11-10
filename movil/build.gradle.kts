plugins {
	id("com.android.application")
	kotlin("android")
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.compose)
}

// **Identidad publicada, y lo único irreversible de este módulo.** Play fija el
// nombre de paquete en el primer envío. Es un segundo paquete y por tanto una
// segunda ficha: el de la TV (`mx.kataliziador.palomera`) sólo puede llevar un
// juego de APKs, y su interfaz es Leanback.
//
// **Provisional hasta que Edgar lo confirme** (ver `MOVIL_PLAN.md` §8). Mientras
// sólo se compile en depuración no compromete nada, porque el sufijo `.debug`
// hace que el id instalado no sea éste.
val appId = "mx.kataliziador.palomera.movil"

android {
	namespace = appId
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		applicationId = appId

		// **El mismo que `app` y `livetv`, y es la condición de la APK única.**
		// Bajado de 24 a 21 el 10-nov: mientras `movil` fuera una app aparte el
		// 24 era defendible, pero en una sola APK que elige interfaz según el
		// aparato no puede haber dos suelos — el instalador ve uno.
		//
		// El 24 estaba por las `VideoCapabilities` de `MediaCodec`, que deciden
		// el perfil de dispositivo del VOD. **Comprobado el 10-nov leyendo
		// `DeviceCapabilities`**: lo único posterior a 21 que usa es
		// `maxSupportedInstances` (API 23), y va dentro de un `runCatching` que
		// cae a un valor por defecto. No hay nada que se rompa.
		//
		// **Lo que no está medido es cómo se ve.** Compila en 21 y el único
		// aparato viejo a mano es un emulador de Android TV API 22, que no sirve
		// para juzgar una interfaz táctil. Un teléfono de gama baja real sigue
		// siendo el pendiente de `MOVIL_PLAN.md` §7.
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		versionCode = 1
		versionName = "0.0.1-esqueleto"

		// **§8: la app se presenta al Jellyfin del ISP como «Palomera».** No es la
		// marca del ISP —esa viste la app por dentro—: es lo que el operador ve en
		// su panel de abonados al confirmar un alta, y ahí necesita saber que lo
		// que entró es nuestra app. No se traduce y no cambia en caliente.
		buildConfigField("String", "CLIENT_NAME", "\"Palomera\"")

		// La dirección del registro va compilada por el mismo motivo que en la
		// caja: la app tiene que poder preguntar «¿quién te da el servicio?»
		// **antes** de saber ninguna dirección. Es un dominio y no una IP, para
		// que mudar el servidor sea un registro DNS y no una versión nueva.
		buildConfigField("String", "REGISTRY_URL", "\"https://authjelly.satmx.net\"")

		// **Compilar sólo para el ABI del aparato que se tiene delante.**
		// Medido el 08-nov: el APK pesa 217 MB y libVLC son 170 de ellos,
		// repartidos en cuatro ABIs (`MOVIL_PLAN.md` §4.3). Un teléfono usa
		// **uno** —el Xiaomi de pruebas declara `arm64-v8a` y nada más—, así
		// que instalar los cuatro es mandar tres cuartas partes de nada por un
		// enlace lento.
		//
		// Sin la propiedad no cambia nada: se compilan los cuatro, que es lo
		// que tiene que llegar a la tienda. Se usa así:
		//
		//     ./gradlew :movil:assembleDebug -Pmovil.abi=arm64-v8a
		//
		// **No es lo mismo que `-Pandroid.injected.build.abi`**, que es para el
		// despliegue desde el IDE: probado el 08-nov y dejó el APK en 207 MB,
		// o sea que no filtró nada.
		(findProperty("movil.abi") as String?)
			?.split(",")
			?.map(String::trim)
			?.filter { it.isNotEmpty() }
			?.takeIf { it.isNotEmpty() }
			?.let { pedidos ->
				ndk {
					abiFilters.clear()
					abiFilters.addAll(pedidos)
				}
			}
	}

	buildFeatures {
		buildConfig = true
		compose = true
	}

	buildTypes {
		debug {
			// Para poder tener instaladas la de release y la de depuración a la
			// vez, igual que hace `:app`.
			applicationIdSuffix = ".debug"
			buildConfigField("boolean", "DEVELOPMENT", "true")
		}

		release {
			isMinifyEnabled = false
			buildConfigField("boolean", "DEVELOPMENT", "false")
			// **Sin configuración de firma todavía.** La clave de la app de TV
			// no se reutiliza: son dos fichas distintas y mezclarlas ataría el
			// destino de una a la otra. Se decide en la fase 5.
		}
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

dependencies {
	// **El motor, compartido por ruta de Gradle y no copiado.** Es el punto de
	// todo este diseño: 9 226 líneas que ya corren, con sus 220 pruebas, y una
	// sola copia que no puede divergir (`MOVIL_PLAN.md` §3).
	implementation(projects.livetv)

	coreLibraryDesugaring(libs.android.desugar)

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// AndroidX
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity.compose)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.androidx.lifecycle.viewmodel)
	implementation(libs.androidx.lifecycle.runtime)

	// Inyección, igual que en `:app`, para que las dos apps se lean igual
	implementation(libs.bundles.koin)

	// Imágenes: carátulas del VOD y logos de marca
	implementation(libs.bundles.coil)

	// El SDK de Jellyfin. **Es lo que hace el VOD abordable**: la capa de API
	// (bibliotecas, elementos, imágenes, progreso de reproducción) viene hecha
	// y tipada. Lo que no viene es la interfaz táctil.
	implementation(libs.jellyfin.sdk)

	// Reproducción del VOD. Los canales los reproduce `:livetv` por su cuenta.
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)

	// Logging
	implementation(libs.timber)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
