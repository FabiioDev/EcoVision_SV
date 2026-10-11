# EcoVisionSV

EcoVisionSV es una aplicación para Android desarrollada en Java que clasifica residuos a partir de fotografías. La imagen se captura con la cámara del dispositivo (o se elige de la galería), se envía a un modelo de detección de objetos servido en Google Cloud Run y el resultado se muestra con las detecciones marcadas, un consejo de reciclaje por categoría y un historial local de todas las detecciones.

## ✨ Funcionalidades

- **Escanear residuo:** captura con CameraX y guarda la foto en `Pictures/EcoVisionSV`.
- **Subir imagen:** selección desde la galería con el Photo Picker del sistema (sin permisos de almacenamiento).
- **Resultado de la detección:**
  - Skeleton loading mientras se espera la respuesta de la API.
  - Imagen con bounding boxes numeradas por detección.
  - Carrusel con el recorte de cada objeto, su confianza y un consejo de reciclaje.
- **Nueva detección:** desde la pantalla de resultado se vuelve a abrir la cámara y la nueva captura pasa por el mismo flujo de inferencia y guardado.
- **Historial:**
  - Lista de detecciones con miniatura, categorías detectadas (p. ej. `plastico ×2 · metal`), número de objetos y fecha.
  - Al abrir un item se muestra el detalle sin volver a llamar a la API.
  - Cada item se puede eliminar, con una ventana de confirmación.
- **Estado del servicio:** indicador en tiempo real (conectado / conectando / sin conexión) que consulta `/health` periódicamente; los botones de escaneo se deshabilitan cuando la API no está disponible.

## 🔄 Flujo de una detección

```text
HomeFragment ──(cámara)──► ScannerActivity ──URI──┐
     │                                            ▼
     └──(Subir imagen / Photo Picker)──URI──► ResultadoActivity
                                                  │  muestra skeleton
                                                  ▼
                                        InferenciaRepository
                                          1. Copia a la galería las URIs del Photo Picker
                                          2. Redimensiona a 640×640 (ImageResizer)
                                          3. POST /predict (Retrofit)
                                          4. Filtra detecciones solapadas (NMS, IoU 0.45)
                                          5. Guarda en Room + imagen con bboxes
                                                  │
                                                  ▼
                                        ResultadoActivity pinta el resultado
```

## 📂 Estructura de Carpetas

```text
ecovisionsv/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ecovisionsv/
│   │   │   ├── api/         # EcoVisionApi: endpoints /health y /predict.
│   │   │   ├── database/    # Room: EcoVisionDatabase (v2 + migraciones), DeteccionDao, DeteccionEntity.
│   │   │   ├── model/       # POJOs de la API (RespuestaPrediccion, Deteccion, RespuestaHealth) y filtrado NMS.
│   │   │   ├── network/     # RetrofitClient, InferenciaRepository (orquestador) y ApiStatusChecker.
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.java       # Contenedor con navegación inferior e indicador de estado de la API.
│   │   │   │   ├── HomeFragment.java       # Inicio: Escanear residuo / Subir imagen.
│   │   │   │   ├── ScannerActivity.java    # Captura con CameraX.
│   │   │   │   ├── ResultadoActivity.java  # Skeleton, inferencia y detalle del resultado.
│   │   │   │   ├── HistorialFragment.java  # Historial de detecciones (abrir / eliminar).
│   │   │   │   └── AboutFragment.java      # Información del proyecto.
│   │   │   └── utils/
│   │   │       ├── ImageResizer.java       # Redimensiona a 640×640 JPEG antes de enviar.
│   │   │       ├── BitmapSaver.java        # Guarda la imagen con bboxes en almacenamiento privado.
│   │   │       ├── GaleriaHelper.java      # Copia imágenes del Photo Picker a Pictures/EcoVisionSV.
│   │   │       └── NetworkChecker.java     # Verifica conectividad.
│   │   ├── res/             # Layouts, drawables y values.
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts     # Configuración del módulo app (incluye BuildConfig.API_BASE_URL).
├── gradle/
│   └── libs.versions.toml   # Catálogo de versiones de dependencias.
├── build.gradle.kts
└── settings.gradle.kts
```

## 💾 Almacenamiento de datos

| Dato | Ubicación | Notas |
|---|---|---|
| Foto original | `Pictures/EcoVisionSV` (galería) | Fotos de la cámara y copias de las imágenes elegidas con el Photo Picker. No se borra al eliminar una detección del historial. |
| Imagen con bounding boxes | `files/resultados` (privado de la app) | No aparece en la galería. Se elimina junto con su registro del historial o al borrar los datos de la app. |
| Historial | Base de datos Room `ecovision.db` | Tabla `detecciones`; guarda el JSON completo de `/predict` para reconstruir el detalle sin volver a llamar a la API. |

> Las URIs del Photo Picker (`content://media/picker/...`) solo son legibles mientras vive el proceso que las recibió. Por eso `GaleriaHelper` copia la imagen a la galería antes de guardarla en la base de datos.

### Cambios de esquema en Room

Al modificar `DeteccionEntity`:

1. Incrementar `version` en `@Database` de `EcoVisionDatabase`.
2. Crear la `Migration` correspondiente.
3. Registrarla con `.addMigrations(...)` en el builder.

Si falta alguno de los tres pasos, Room lanza `IllegalStateException: Room cannot verify the data integrity` al abrir la base de datos.

## ⚙️ Versiones del Proyecto

- **Android Gradle Plugin (AGP):** `9.3.3`
- **Compile SDK:** `37`
- **Target SDK:** `37`
- **Min SDK:** `24` (Android 7.0 Nougat y superiores)
- **Compatibilidad de Java:** `Java 11`

## 🔐 Configuración del Proyecto

La URL de la API se lee de `local.properties` (raíz del proyecto) y se expone como `BuildConfig.API_BASE_URL`. Añade la propiedad **sin comillas**, con `https://` y con la barra final (Retrofit la exige):

```properties
API_BASE_URL=https://SERVICIO-HASH-uc.a.run.app/
```

### Endpoints utilizados

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/health` | Estado del servicio para el indicador de conexión. |
| `POST` | `/predict?umbral=0.4` | Multipart con la imagen JPEG 640×640 en el campo `file`; devuelve las detecciones (`categoria`, `confianza`, `bbox_norm`, `bbox_px`) y la latencia. |

## 📦 Dependencias Utilizadas

Las dependencias se gestionan mediante el catálogo de versiones (`libs.versions.toml`).

### Core & UI
- `androidx.appcompat:appcompat:1.6.1` - Compatibilidad con versiones anteriores de Android.
- `com.google.android.material:material:1.10.0` - Componentes de Material Design 3 (botones, diálogos, navegación inferior).
- `androidx.constraintlayout:constraintlayout:2.1.4` - Layouts complejos y responsivos.
- `androidx.activity:activity-ktx:1.8.0` - Activity Result API y Photo Picker.

### CameraX & Procesamiento de Imágenes
- `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` (`1.6.1`/`1.6.2`) - Captura de fotos ligada al ciclo de vida.
- `androidx.exifinterface:exifinterface:1.4.2` - Lectura de la orientación EXIF para rotar correctamente las imágenes.

### Red y Consumo de API
- `com.squareup.retrofit2:retrofit:2.11.0` - Cliente HTTP tipado para la API en Cloud Run.
- `com.squareup.retrofit2:converter-gson:2.11.0` - Conversión automática de las respuestas JSON.
- `com.squareup.okhttp3:logging-interceptor:4.12.0` - Logging de peticiones para depuración.
- `com.google.code.gson:gson:2.11.0` - Mapeo de JSON a POJOs y serialización del resultado guardado en el historial.

### Base de Datos Local
- `androidx.room:room-runtime` y `room-compiler` (`2.6.1`) - Persistencia del historial de detecciones sobre SQLite.

### Testing
- `junit:junit:4.13.2` - Pruebas unitarias locales.
- `androidx.test.ext:junit:1.1.5` - Extensiones de JUnit para Android.
- `androidx.test.espresso:espresso-core:3.5.1` - Pruebas de interfaz de usuario.

## 🛠️ Solución de problemas

- **La app se cierra al abrir el historial o al guardar una detección después de cambiar la base de datos:** revisa la sección *Cambios de esquema en Room*. Durante el desarrollo, si se reinstala la app con `android:allowBackup="true"`, Android puede restaurar una base de datos antigua aunque se hayan borrado los datos.
- **No se conecta a la API:** verifica que `API_BASE_URL` en `local.properties` tenga `https://`, la barra final y no lleve comillas; después sincroniza Gradle y recompila.
