plugins {
	id("com.android.library")
	kotlin("android")
}

android {
	namespace = "org.jellyfin.livetv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
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
	// Kotlin
	implementation(libs.kotlinx.coroutines)

	// AndroidX
	implementation(libs.androidx.core)

	// media3 — sin la extensión FFmpeg: el audio llega normalizado a AAC
	// desde Flussonic, así que no hay decoders que añadir (CLAUDE.md §6).
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.datasource.okhttp)

	// libVLC — el respaldo de vídeo (M6). Entra por la misma interfaz
	// `LiveEngine` que Media3 y no se habla con él desde ningún otro sitio.
	//
	// **Es LGPL-2.1**, así que la pantalla de Información tiene que decirlo
	// (`CLAUDE.md` §8). Viene precompilado: no reintroduce el NDK ni la
	// compilación nativa, que se fueron con la extensión FFmpeg y no vuelven.
	implementation(libs.libvlc)

	// JSON del servidor de configuración. Se navega el árbol a mano, sin el
	// plugin de serialización: el parser necesita descartar canal por canal con
	// su motivo, y la generación de código lo pondría difícil.
	implementation(libs.kotlinx.serialization.json)

	// Logging
	implementation(libs.timber)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
