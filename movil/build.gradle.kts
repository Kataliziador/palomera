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

		// **No es el 21 del proyecto, y es una decisión, no un descuido.** El
		// suelo de 21 existe por las cajas viejas de ISP; un teléfono con
		// Android 5 en 2026 es una fracción desaparecida del parque y arrastra
		// el coste de Compose en hardware de 2014. El 24 (Android 7.0, 2016)
		// cubre prácticamente todo y da las `VideoCapabilities` de MediaCodec
		// que hacen falta para decidir el perfil de dispositivo del VOD.
		//
		// El módulo `:livetv` declara 21, así que subirlo aquí es legal: un
		// consumidor puede pedir más que su biblioteca, nunca menos.
		minSdk = 24
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		versionCode = 1
		versionName = "0.0.1-esqueleto"
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
