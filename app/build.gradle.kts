import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** Propiedad de local.properties o, si no está, variable de entorno del mismo nombre. */
fun propiedad(nombre: String): String =
    localProps.getProperty(nombre)?.trim().takeUnless { it.isNullOrEmpty() }
        ?: System.getenv(nombre)?.trim().orEmpty()

// Firma de release. La clave (.jks) vive FUERA del repo y sus contraseñas solo en
// local.properties o en variables de entorno: nunca en el código (el repo es público).
// Si falta alguna, el APK de release sale sin firmar (no se puede instalar).
val firmaRelease = mapOf(
    "RELEASE_STORE_FILE" to propiedad("RELEASE_STORE_FILE"),
    "RELEASE_STORE_PASSWORD" to propiedad("RELEASE_STORE_PASSWORD"),
    "RELEASE_KEY_ALIAS" to propiedad("RELEASE_KEY_ALIAS"),
    "RELEASE_KEY_PASSWORD" to propiedad("RELEASE_KEY_PASSWORD"),
)
val firmaCompleta = firmaRelease.values.all { it.isNotEmpty() } &&
    file(firmaRelease.getValue("RELEASE_STORE_FILE")).exists()

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
        // Cada APK publicado sube versionCode (Android solo instala encima uno mayor)
        versionCode = 1
        versionName = "1.0.0"

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

    signingConfigs {
        if (firmaCompleta) {
            create("release") {
                storeFile = file(firmaRelease.getValue("RELEASE_STORE_FILE"))
                storePassword = firmaRelease.getValue("RELEASE_STORE_PASSWORD")
                keyAlias = firmaRelease.getValue("RELEASE_KEY_ALIAS")
                keyPassword = firmaRelease.getValue("RELEASE_KEY_PASSWORD")
            }
        } else {
            logger.warn("Firma de release incompleta (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                "RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD en local.properties): el APK de release saldrá sin firmar.")
        }
    }

    buildTypes {
        release {
            if (firmaCompleta) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 desactivado: Gson y Retrofit usan reflexión y necesitarían reglas propias
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
