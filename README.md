# EcoVisionSV

EcoVisionSV es una aplicación para Android desarrollada en Java. El proyecto está enfocado en utilizar la cámara del dispositivo para el escaneo y, potencialmente, la clasificación mediante aprendizaje automático (Machine Learning), como sugiere su estructura de archivos.

## 📂 Estructura de Carpetas

La arquitectura del proyecto sigue las convenciones de Android Studio, y el código fuente está organizado de la siguiente manera:

```text
ecovisionsv/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/ecovisionsv/
│   │       │   ├── camera/      # Destinado a la lógica de configuración y manejo de CameraX.
│   │       │   ├── ml/          # Destinado a los modelos de Machine Learning (ej. TensorFlow Lite).
│   │       │   ├── ui/          # Componentes de la Interfaz de Usuario.
│   │       │   │   ├── Clasificador.java     # Lógica o interfaz de clasificación.
│   │       │   │   ├── HomeFragment.java     # Fragmento principal de inicio.
│   │       │   │   ├── MainActivity.java     # Actividad principal que aloja la app.
│   │       │   │   └── ScannerActivity.java  # Actividad encargada del escaneo con la cámara.
│   │       │   └── viewmodel/   # Destinado a los ViewModels para la gestión de estados de la UI.
│   │       ├── res/             # Recursos de la app (layouts, drawables, values).
│   │       └── AndroidManifest.xml # Archivo de manifiesto de la aplicación Android.
│   └── build.gradle.kts         # Configuración de compilación a nivel de módulo (app).
├── gradle/
│   └── libs.versions.toml       # Catálogo de versiones centralizado de dependencias.
├── build.gradle.kts             # Configuración de compilación a nivel de proyecto raíz.
└── settings.gradle.kts          # Definición del proyecto y repositorios de plugins.
```

## ⚙️ Versiones del Proyecto

La aplicación está configurada con las siguientes características de entorno y SDK:

- **Android Gradle Plugin (AGP):** `9.3.2`
- **Compile SDK:** `37`
- **Target SDK:** `37`
- **Min SDK:** `24` (Soporta Android 7.0 Nougat y superiores)
- **Compatibilidad de Java:** `Java 11`

## 📦 Dependencias Utilizadas

Las dependencias del proyecto están gestionadas mediante un catálogo de versiones (`libs.versions.toml`). Se incluyen bibliotecas base, componentes de interfaz de usuario y la suite de **CameraX** para la manipulación de la cámara.

### Core & UI
- `androidx.appcompat:appcompat:1.6.1` - Funcionalidades compatibles con versiones anteriores de Android.
- `com.google.android.material:material:1.10.0` - Componentes de Material Design.
- `androidx.constraintlayout:constraintlayout:2.1.4` - Para la creación de layouts complejos y responsivos.
- `androidx.activity:activity-ktx:1.8.0` - Extensiones para actividades de Android.

### CameraX (Cámara y Escaneo)
- `androidx.camera:camera-core:1.6.1` - Casos de uso centrales de CameraX.
- `androidx.camera:camera-camera2:1.6.1` - Implementación de la API de Camera2 para CameraX.
- `androidx.camera:camera-lifecycle:1.6.2` - Control del ciclo de vida de la cámara vinculado a la aplicación.
- `androidx.camera:camera-view:1.6.1` - Vista de UI (`PreviewView`) para mostrar la cámara fácilmente.

### Testing (Pruebas)
- `junit:junit:4.13.2` - Pruebas unitarias locales.
- `androidx.test.ext:junit:1.1.5` - Extensiones de JUnit para Android.
- `androidx.test.espresso:espresso-core:3.5.1` - Pruebas de interfaz de usuario.
