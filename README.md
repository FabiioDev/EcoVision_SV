# EcoVisionSV

EcoVisionSV es una aplicación para Android desarrollada en Java. El proyecto está enfocado en utilizar la cámara del dispositivo para la captura y clasificación de imágenes mediante una API externa (como en servicios Cloud Run), manteniendo un historial de inferencias de manera local y monitoreando el estado del servicio en tiempo real.

## 📂 Estructura de Carpetas

La arquitectura del proyecto sigue las convenciones de Android Studio, y el código fuente está organizado de la siguiente manera:

```text
ecovisionsv/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/ecovisionsv/
│   │       │   ├── api/         # Definición de los endpoints de la API (EcoVisionApi).
│   │       │   ├── database/    # Configuración de Room para el historial local (DAO, Entidades).
│   │       │   ├── model/       # POJOs para respuestas de la API, incluyendo lógica de filtrado NMS.
│   │       │   ├── network/     # Cliente Retrofit, InferenciaRepository (orquestador) y ApiStatusChecker.
│   │       │   ├── ui/          # Componentes de la Interfaz de Usuario (Actividades y Fragmentos).
│   │       │   │   ├── HomeFragment.java     # Fragmento principal de inicio.
│   │       │   │   ├── MainActivity.java     # Actividad principal que aloja la app.
│   │       │   │   └── ScannerActivity.java  # Actividad encargada del escaneo con la cámara.
│   │       │   └── utils/       # Utilidades como ImageResizer (640x640) y verificador de red.
│   │       ├── res/             # Recursos de la app (layouts, drawables, values).
│   │       └── AndroidManifest.xml # Archivo de manifiesto, incluye permisos (INTERNET, ACCESS_NETWORK_STATE).
│   └── build.gradle.kts         # Configuración de compilación a nivel de módulo (app).
├── gradle/
│   └── libs.versions.toml       # Catálogo de versiones centralizado de dependencias.
├── build.gradle.kts             # Configuración de compilación a nivel de proyecto raíz.
└── settings.gradle.kts          # Definición del proyecto y repositorios de plugins.
```

## ⚙️ Versiones del Proyecto

La aplicación está configurada con las siguientes características de entorno y SDK:

- **Android Gradle Plugin (AGP):** `9.3.3`
- **Compile SDK:** `37`
- **Target SDK:** `37`
- **Min SDK:** `24` (Soporta Android 7.0 Nougat y superiores)
- **Compatibilidad de Java:** `Java 11`

## 🔐 Configuración del Proyecto (Variables de Entorno)

Para compilar correctamente el proyecto y comunicarse con el backend, es necesario configurar la URL base de la API. Crea o modifica el archivo `local.properties` en la raíz del proyecto y añade la siguiente propiedad:

```properties
API_BASE_URL="SERVICIO-HASH-uc.a.run.app"
```

## 📦 Dependencias Utilizadas

Las dependencias del proyecto están gestionadas mediante un catálogo de versiones (`libs.versions.toml`). se utilizan librerías de red y bases de datos locales:

### Core & UI
- `androidx.appcompat:appcompat:1.6.1` - Funcionalidades compatibles con versiones anteriores de Android.
- `com.google.android.material:material:1.10.0` - Componentes de Material Design.
- `androidx.constraintlayout:constraintlayout:2.1.4` - Para la creación de layouts complejos y responsivos.
- `androidx.activity:activity-ktx:1.8.0` - Extensiones para actividades de Android.

### CameraX & Procesamiento de Imágenes
- `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` (`1.6.1`/`1.6.2`) - API robusta y ciclo de vida controlado para el uso de la cámara.
- `androidx.exifinterface:exifinterface:1.4.2` - Para procesar metadatos y correcta orientación de las imágenes capturadas.

### Red y Consumo de API (Integración Retrofit)
- `com.squareup.retrofit2:retrofit:2.11.0` - Cliente HTTP seguro y tipado para realizar llamadas a la API (Cloud Run).
- `com.squareup.retrofit2:converter-gson:2.11.0` - Serialización y deserialización automática de las respuestas JSON.
- `com.squareup.okhttp3:logging-interceptor:4.12.0` - Registro (Logging) de red para depuración de peticiones y respuestas.
- `com.google.code.gson:gson:2.11.0` - Librería para el mapeo de los datos en objetos Java (POJOs).

### Base de Datos Local
- `androidx.room:room-runtime` y `room-compiler` (`2.6.1`) - Abstracción sobre SQLite que permite guardar el historial de inferencias de manera persistente en el dispositivo.

### Testing (Pruebas)
- `junit:junit:4.13.2` - Pruebas unitarias locales.
- `androidx.test.ext:junit:1.1.5` - Extensiones de JUnit para Android.
- `androidx.test.espresso:espresso-core:3.5.1` - Pruebas de interfaz de usuario.
