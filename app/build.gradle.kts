import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.ecovisionsv"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.ecovisionsv"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL de la API: local.properties o, si no está, la variable de entorno API_BASE_URL.
        // Nunca se escribe en el código (el repo es público). Sin URL, la app compila y
        // muestra "sin conexión".
        val apiUrl = localProps.getProperty("API_BASE_URL")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: System.getenv("API_BASE_URL")?.trim().orEmpty()
        if (apiUrl.isEmpty()) {
            logger.warn("API_BASE_URL no está definida (local.properties o variable de entorno): la app no podrá conectarse.")
        }
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$apiUrl\""
        )

        // URL del backend (config remota GET /api/v1/config), con las mismas reglas que
        // API_BASE_URL. Sin ella la app no consulta la config y usa la última guardada o
        // los valores por defecto del código.
        val backendUrl = localProps.getProperty("BACKEND_BASE_URL")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: System.getenv("BACKEND_BASE_URL")?.trim().orEmpty()
        if (backendUrl.isEmpty()) {
            logger.warn("BACKEND_BASE_URL no está definida (local.properties o variable de entorno): la app usará la config por defecto.")
        }
        buildConfigField(
            "String",
            "BACKEND_BASE_URL",
            "\"$backendUrl\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.constraintlayout)
    implementation(libs.exifinterface)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    // red y api
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    // bd local
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
}
