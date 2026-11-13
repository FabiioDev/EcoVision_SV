import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.irkana.ecovisionsv"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.irkana.ecovisionsv"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL del backend: la ÚNICA de la app (config, detección y estado; el backend
        // reenvía la imagen a la inferencia). local.properties o, si no está, la variable de
        // entorno BACKEND_BASE_URL. Nunca se escribe en el código (el repo es público).
        // Sin URL, la app compila, muestra "sin conexión" y usa la config por defecto.
        val backendUrl = localProps.getProperty("BACKEND_BASE_URL")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: System.getenv("BACKEND_BASE_URL")?.trim().orEmpty()
        if (backendUrl.isEmpty()) {
            logger.warn("BACKEND_BASE_URL no está definida (local.properties o variable de entorno): la app no podrá conectarse.")
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
    testImplementation(libs.okhttp.mockwebserver)
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
    // autenticación (Fase 3). Lee app/google-services.json (proyecto de Firebase).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
}
