import java.util.Properties

plugins {
	id("com.android.application")
	kotlin("android")
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.aboutlibraries)
}

// La identidad publicada de la app, y **lo único de todo el proyecto que no se
// puede cambiar nunca**: Play fija el nombre de paquete en el primer envío y no
// admite cambiarlo después.
//
// Heredábamos `org.jellyfin.androidtv`, que es el paquete del upstream y ya está
// publicado por el proyecto Jellyfin. Con él, el envío se rechaza —los nombres de
// paquete son únicos en toda la tienda— y además la app choca con la Jellyfin de
// verdad si alguien tiene las dos en la misma caja.
val appId = "mx.kataliziador.palomera"

// **`local.properties` no lo lee Gradle, y los dos sitios que mandaban usarlo
// pedían un archivo sin efecto**: `PLAY.md` §1 y el mensaje de error del final
// de este archivo. `findProperty` ve `gradle.properties`, los `-P` y las
// `ORG_GRADLE_PROJECT_*`; `local.properties` es un archivo del plugin de
// Android, pensado para `sdk.dir`, y nadie lo expone como propiedad del
// proyecto. Medido el 01-nov: con las cuatro propiedades de firma ahí,
// `assembleRelease` se detenía diciendo que faltaban las cuatro — y el propio
// mensaje te devolvía a `local.properties`. Una instrucción circular, y justo
// en el paso que no se puede deshacer.
//
// Se lee aquí y no en `buildSrc/Properties.kt`, que es donde vive el ayudante,
// para no abrir territorio nuevo (§4.7): este archivo ya está en la lista y
// `buildSrc/` no. El precio es que `jellyfin.version` sigue sin leerse de ahí,
// y no hace falta: `PLAY.md` §8 la pasa por el entorno.
//
// El orden importa: una `-P` o una variable de entorno **mandan sobre el
// archivo**, para poder firmar distinto en una compilación suelta sin editarlo.
//
// Y **una propiedad vacía cuenta como ausente**, que no es un detalle: el
// archivo se deja preparado con las contraseñas en blanco para que sólo haya
// que escribirlas, y `""` no es `null`. Sin esto, la configuración de firma se
// crearía con una contraseña vacía, el guardián del final no vería nada raro y
// el fallo saldría más tarde y sin nombrar la causa — que es exactamente el
// modo de fallar que ese guardián existe para evitar.
val propiedadesLocales = Properties().apply {
	rootProject.file("local.properties")
		.takeIf { it.isFile }
		?.inputStream()
		?.use { load(it) }
}

fun propiedadDeFirma(nombre: String): String? =
	(getProperty(nombre) ?: propiedadesLocales.getProperty(nombre))
		?.takeIf { it.isNotBlank() }

android {
	// El `namespace` **no** cambia: es el paquete Kotlin de ~2.000 archivos y
	// sólo existe en tiempo de compilación. Renombrarlo reescribiría el árbol
	// entero y haría ingobernable el rebase contra upstream (§4), sin que nadie
	// llegue a verlo nunca. Lo que se publica es `applicationId`, y ese sí es
	// nuestro.
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version
		applicationId = appId
		versionName = project.getVersionName()
		versionCode = getVersionCode(versionName!!)

		// El nombre con el que la app se identifica ante el Jellyfin del ISP:
		// es lo que sale en su lista de sesiones y de dispositivos, y lo que el
		// panel de abonados enseña al operador al confirmar un alta. Venía
		// diciendo «Jellyfin Android TV», que es §8 sin cumplir. Va aquí y no en
		// `strings.xml` porque es identidad, como el `applicationId`: no se
		// traduce y no cambia con la marca del ISP.
		buildConfigField("String", "CLIENT_NAME", "\"Palomera\"")

		// La oferta de código que exige la GPL-2.0 (`PLAY.md` §5). Va aquí por
		// lo mismo que el nombre: la necesitan **tres** sitios —el aviso de
		// licencias de la pantalla de Información y la cabecera de los dos
		// informes que se suben al servidor del ISP— y escribirla a mano en
		// los tres es cómo acabó el nombre apuntando al upstream durante un
		// mes. Si el repositorio se mueve, se cambia una línea.
		buildConfigField("String", "CLIENT_REPOSITORY", "\"https://github.com/Kataliziador/palomera\"")

		// LiveTV — nada de esto se codifica en el módulo (CLAUDE.md regla 12).
		// En B5 cada uno pasa a ser un valor por product flavor.
		buildConfigField("boolean", "VOD_ENABLED", "true")
		buildConfigField("String", "LIVETV_CONFIG_URL", "\"\"")

		// La dirección del registro de sites va compilada: la app tiene que
		// saber a quién preguntar antes de preguntar nada, así que no puede
		// venir de él (`INTEGRACION_REGISTRO.md` §7).
		//
		// **Dominio y no IP** —mudar el servidor tiene que ser un registro DNS,
		// no una versión de la app—, sin puerto, y el código compone
		// `/v1/resolve`. Cambiarla después del primer despliegue exige una
		// versión nueva para todos los ISPs a la vez.
		buildConfigField("String", "REGISTRY_URL", "\"https://authjelly.satmx.net\"")

		// Canales de prueba de M0. Andamiaje: desaparece en B1, cuando el
		// ConfigRepository consuma el config.json del servidor.
		// Formato: id|nombre|url|videoCodec|audioCodec, separados por ';'
		//
		// `index.m3u8` y no `mono.m3u8` (CLAUDE.md §6, revisado el 02-oct):
		// el media playlist multiplexa los audios en un solo TS y Media3 solo
		// expone uno. El master los declara como renditions y los expone todos.
		val livetvBase = "http://192.168.11.250:8181"
		val livetvBootstrapChannels = listOf(
			"estrellashd|Estrellas HD|$livetvBase/test-sat77w-estrellashd-mp4/index.m3u8|h264|aac",
			"beisbol|Béisbol|$livetvBase/beisbol/index.m3u8|h264|aac",
			"discoveryhd|Discovery HD|$livetvBase/test-sat70w-discoveryhd-mp4/index.m3u8|h264|aac",
			"hbohd|HBO HD|$livetvBase/test-sat70w-hbohd-mp4/index.m3u8|h264|aac",
			"estrellassd|Estrellas SD|$livetvBase/test-sat77w-estrellassd-mp2v/index.m3u8|mpeg2video|aac",
			"mileniosd|Milenio SD|$livetvBase/mileniosd/index.m3u8|h264|aac",
		).joinToString(";")
		buildConfigField("String", "LIVETV_BOOTSTRAP_CHANNELS", "\"$livetvBootstrapChannels\"")
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	signingConfigs {
		val keystoreFile = propiedadDeFirma("keystore.file")
		val keystorePassword = propiedadDeFirma("keystore.password")
		val signingKeyAlias = propiedadDeFirma("signing.key.alias")
		val signingKeyPassword = propiedadDeFirma("signing.key.password")

		if (keystoreFile != null && keystorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
			create("release") {
				storeFile = file(keystoreFile)
				storePassword = keystorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}
	}

	dependenciesInfo {
		includeInBundle = false
		includeInApk = false
	}

	buildTypes {
		release {
			isMinifyEnabled = false

			// Set package names used in various XML files
			// Derivados de `appId`, no del `namespace`: las `authorities` de los
			// proveedores se declaran en el manifest como `${applicationId}.…`,
			// y si estas cadenas apuntaran a otro sitio las sugerencias de
			// búsqueda consultarían un proveedor que no existe.
			resValue("string", "app_id", appId)
			resValue("string", "app_search_suggest_authority", "${appId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${appId}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_release")

			buildConfigField("boolean", "DEVELOPMENT", "false")

			// **Sin lista de arranque en producción.** Los seis canales del
			// `defaultConfig` apuntan al Flussonic de desarrollo: en la caja de
			// un abonado serían seis canales fantasma, con nombres de otro
			// headend y URLs muertas, justo cuando su site no responde. Es
			// peor que no enseñar nada, porque parece que la app funciona.
			buildConfigField("String", "LIVETV_BOOTSTRAP_CHANNELS", "\"\"")

			signingConfig = signingConfigs.findByName("release")
		}

		debug {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"

			// Set package names used in various XML files
			resValue("string", "app_id", appId + applicationIdSuffix)
			resValue("string", "app_search_suggest_authority", "${appId + applicationIdSuffix}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${appId + applicationIdSuffix}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		sarifReport = true
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

// El artefacto se llama como la app que se publica, no como el proyecto del
// que salió el fork. Es el archivo que se sube a la Play Console y el que
// aparece en su historial de versiones: dejarlo como «jellyfin-androidtv»
// mientras la ficha dice Palomera invita a subir el equivocado.
base.archivesName.set("palomera-v${project.getVersionName()}")

// Las dos maneras silenciosas de construir un bundle que no se puede publicar:
// sin versión y sin firma. Las dos producen un archivo de 100 MB con buena cara.

// **Un AAB sin versión se construye igual, y Play sí lo acepta.** Ese es el
// problema: `getVersionName()` cae a `0.0.0-dev.1` cuando nadie pasa
// `jellyfin.version` ni `JELLYFIN_VERSION`, y de ahí sale `versionCode 1`. El
// envío no falla —falla el siguiente—, porque el `versionCode` solo puede
// subir: publicado el 1, los 98 números por debajo de 100 quedan quemados y
// con ellos la convención `MAMIPAPR` de `VersionUtils.kt`, que es de donde
// sale el número de toda versión futura. Es irreversible por el mismo motivo
// que el nombre de paquete, y no se avisa en ningún sitio.
//
// `versionCode < 100` es exactamente «major, minor y patch son cero», o sea
// la familia del marcador de desarrollo; es el mismo criterio con el que el
// tipo `debug` decide `DEVELOPMENT`.
if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } &&
	android.defaultConfig.versionCode!! < 100
) {
	error(
		"""
		La compilación de release no tiene versión: salió `${android.defaultConfig.versionName}`,
		que es el marcador de desarrollo, con `versionCode ${android.defaultConfig.versionCode}`.
		Play aceptaría ese envío y después no se puede bajar el número.

		Se fija con la propiedad o con la variable de entorno, no en el código:

		    ./gradlew -Pjellyfin.version=0.1.0-rc.1 :app:bundleRelease
		    JELLYFIN_VERSION=0.1.0-rc.1 ./gradlew :app:bundleRelease

		Ver PLAY.md §2, que lleva la tabla de números y el candidato recomendado.
		""".trimIndent(),
	)
}

// **Un AAB sin firmar se construye igual y Play lo rechaza al subirlo.**
// `signingConfigs` solo crea la configuración `release` si están las cuatro
// propiedades (arriba), y `findByName` devuelve null sin decir nada cuando
// falta alguna. El resultado es un bundle de 100 MB que parece bueno y muere
// en la consola media hora después, con un mensaje que no nombra la causa.
if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } &&
	android.buildTypes.getByName("release").signingConfig == null
) {
	error(
		"""
		La compilación de release no tiene clave de firma, así que el AAB saldría sin firmar
		y Play lo rechazaría. Hacen falta las cuatro propiedades, con estos nombres exactos:

		    keystore.file          (o la variable KEYSTORE_FILE)
		    keystore.password      (KEYSTORE_PASSWORD)
		    signing.key.alias      (SIGNING_KEY_ALIAS)
		    signing.key.password   (SIGNING_KEY_PASSWORD)

		Van en `local.properties`, que no se versiona, o en el entorno. Ver PLAY.md §1.
		""".trimIndent(),
	)
}

tasks.register("versionTxt") {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

dependencies {
	// Jellyfin
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.accompanist.permissions)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.datasource.okhttp)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	// media3-ffmpeg-decoder (GPL-3.0) retirado a propósito: ver LIVETV_LOG.md,
	// pendiente 19. Solo aportaba decoders de audio que ninguna biblioteca usa.

	// LiveTV — el motor de canales en vivo
	implementation(projects.livetv)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.bundles.coil)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
