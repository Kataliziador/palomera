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

	// Logging
	implementation(libs.timber)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
