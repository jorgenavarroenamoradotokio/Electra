import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
}

val appVersionCode = providers.gradleProperty("electra.versionCode").get().toInt()
val appVersionName = providers.gradleProperty("electra.versionName").get()
// =====================================================================
// Entornos — ÚNICO sitio que hay que tocar para cambiar URLs, nombre
// de la app o logging. Se deriva de aquí.
// =====================================================================
enum class LogLevel(val priority: String) {
    VERBOSE("android.util.Log.VERBOSE"),
    DEBUG("android.util.Log.DEBUG"),
    INFO("android.util.Log.INFO"),
    WARN("android.util.Log.WARN"),
    ERROR("android.util.Log.ERROR"),
}

data class Logging(
    val toLogcat: Boolean,
    val toFile: Boolean,
    val minLevel: LogLevel,
)

data class Environment(
    /** Nombre del buildType y sufijo del applicationId (salvo en release). */
    val name: String,
    /** true → hereda de "release": sin debug, minificado, firmado con el keystore real. */
    val isRelease: Boolean,
    val appName: String,
    val baseUrl: String,
    val logging: Logging,
) {
    val environmentId: String get() = name.uppercase()

    /** El tráfico en claro solo se permite si el propio entorno usa http://. */
    val allowsCleartext: Boolean get() = baseUrl.startsWith("http://")
}

val environments = listOf(
    Environment(
        name = "dev",
        isRelease = false,
        appName = "Electra DEV",
        baseUrl = "http://10.80.70.132:8201/",
        logging = Logging(toLogcat = true, toFile = false, minLevel = LogLevel.VERBOSE),
    ),
    Environment(
        name = "pre",
        isRelease = false,
        appName = "Electra PRE",
        baseUrl = "https://pre.api.example.com/",
        // Pon toLogcat = true si quieres ver también Logcat al probar
        logging = Logging(toLogcat = false, toFile = true, minLevel = LogLevel.DEBUG),
    ),
    Environment(
        name = "pro",
        isRelease = true,
        appName = "Electra",
        baseUrl = "https://api.example.com/",
        // v/d además se eliminan con R8 (ver proguard-rules.pro)
        logging = Logging(toLogcat = false, toFile = true, minLevel = LogLevel.INFO),
    ),
)

// =====================================================================
// Firma — credenciales fuera del repo (keystore.properties en .gitignore)
// =====================================================================
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.also { props ->
        val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        val missing = required.filter { props.getProperty(it).isNullOrBlank() }
        require(missing.isEmpty()) { "keystore.properties incompleto, faltan: $missing" }
    }

if (keystoreProperties == null) {
    logger.warn("⚠ keystore.properties no encontrado: los builds release (pro) saldrán SIN firmar.")
}

// =====================================================================
// Android
// =====================================================================
android {
    namespace = "com.redur.electra"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.redur.electra"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("long", "INACTIVITY_TIMEOUT_MS", "15L * 60L * 1000L")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }

        environments.forEach { env ->
            val parent = if (env.isRelease) "release" else "debug"

            create(env.name) {
                initWith(getByName(parent))
                matchingFallbacks += parent

                if (!env.isRelease) {
                    applicationIdSuffix = ".${env.name}"
                    versionNameSuffix = "-${env.name}"
                }

                resValue("string", "app_name", env.appName)
                manifestPlaceholders["usesCleartextTraffic"] = env.allowsCleartext.toString()

                buildConfigField("String", "ENVIRONMENT", "\"${env.environmentId}\"")
                buildConfigField("String", "BASE_URL", "\"${env.baseUrl}\"")
                buildConfigField("boolean", "LOG_TO_LOGCAT", env.logging.toLogcat.toString())
                buildConfigField("boolean", "LOG_TO_FILE", env.logging.toFile.toString())
                buildConfigField("int", "LOG_MIN_PRIORITY", env.logging.minLevel.priority)
            }
        }
    }

    // Los tests se ejecutan contra dev (por defecto usarían "debug", que se desactiva)
    testBuildType = "dev"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Solo se generan variantes de los entornos declarados (debug/release quedan como plantillas)
androidComponents {
    val enabledBuildTypes = environments.map { it.name }.toSet()
    beforeVariants { variant ->
        variant.enable = variant.buildType in enabledBuildTypes
    }
}

dependencies {
    // Android
    implementation(libs.appcompat)
    implementation(libs.material)

    // Inyección de dependencias
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)

    // Logging
    implementation(libs.timber)

    // Red
    implementation(libs.retrofit2)
    implementation(libs.retrofit2.conver)
    implementation(libs.okhhtp3)
    implementation(libs.okhttp3.logging)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}