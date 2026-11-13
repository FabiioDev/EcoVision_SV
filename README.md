# EcoVisionSV

EcoVisionSV es una aplicación para Android desarrollada en Java que clasifica residuos a partir de fotografías. La imagen se captura con la cámara del dispositivo (o se elige de la galería), se envía al backend de EcoVision en Google Cloud Run (que la reenvía al modelo de detección de objetos) y el resultado se muestra con las detecciones marcadas, un consejo de reciclaje por categoría y un historial local de todas las detecciones.

## ✨ Funcionalidades

- **Cuenta obligatoria (Firebase Authentication):** inicio de sesión con correo y contraseña o con Google (Credential Manager), registro, recuperación de contraseña, cierre de sesión y borrado de la cuenta desde la foto de perfil de la barra superior.
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
- **Estado del servicio:** indicador en tiempo real (conectado / conectando / sin conexión) que consulta `/api/v1/health` del backend periódicamente; "conectado" solo cuando el modelo está listo (si está arrancando en frío, "conectando"). Los botones de escaneo se deshabilitan mientras tanto.

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
                                          3. POST /api/v1/predict al backend (Retrofit + token)
                                          4. Filtra detecciones solapadas (NMS de la config)
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
│   │   ├── java/com/irkana/ecovisionsv/
│   │   │   ├── api/         # EcoVisionApi (/api/v1/health, /api/v1/predict) y BackendApi (config).
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
│   └── build.gradle.kts     # Configuración del módulo app (incluye BuildConfig.BACKEND_BASE_URL).
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
| Historial | Base de datos Room `ecovision.db` | Tabla `detecciones`; guarda el JSON completo de `/api/v1/predict` para reconstruir el detalle sin volver a llamar a la API. |

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

La app habla con una sola URL, la del backend (detección, estado y config remota). El backend reenvía las imágenes al servicio de inferencia, que la app no llama directamente. La URL se lee de `local.properties` (raíz del proyecto) y se expone como `BuildConfig.BACKEND_BASE_URL`. Añade la propiedad **sin comillas** y con `https://`:

```properties
BACKEND_BASE_URL=https://BACKEND-HASH-uc.a.run.app/
```

Si no está en `local.properties`, se usa la variable de entorno `BACKEND_BASE_URL` del sistema al compilar (útil en CI). La URL nunca va en el código. Sin URL, la app compila (Gradle muestra un aviso), el indicador queda en "sin conexión" y la config es la última guardada o, si no hay ninguna, la de por defecto. `API_BASE_URL` ya no se usa.

### Firebase Authentication

`app/google-services.json` (en el repo) identifica el proyecto de Firebase; no contiene secretos. En la consola de Firebase tienen que estar activados los proveedores **Correo electrónico/contraseña** y **Google**, y registrada la huella SHA-1 de la clave con la que se firma la app (debug y release); sin ella, "Continuar con Google" falla. La huella de debug se obtiene con:

```
./gradlew signingReport
```

Si cambias el paquete (`applicationId`) o el proyecto de Firebase, vuelve a descargar `google-services.json`.

### APK de release (firmado)

La clave de firma (`.jks`) vive **fuera del repo** y sus datos solo en `local.properties` (o en variables de entorno con el mismo nombre):

```properties
RELEASE_STORE_FILE=C:/ruta/fuera/del/repo/ecovisionsv-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=ecovisionsv
RELEASE_KEY_PASSWORD=...
```

```
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

Si falta alguna propiedad, Gradle avisa y el APK sale sin firmar. La huella SHA-1 de la clave de release tiene que estar registrada en Firebase (si no, "Continuar con Google" falla en el APK). Cada versión publicada sube `versionCode` en `app/build.gradle.kts`. Si se pierde la clave, no se pueden publicar actualizaciones que se instalen encima. El APK se publica en GitHub Releases y se descarga desde la página `/descargar` del backend.

### Endpoints utilizados

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/api/v1/health` | Estado para el indicador de conexión (`inferencia`: `lista`, `despertando`, `no_disponible`, `sin_configurar`); despierta el modelo. Sin token. |
| `POST` | `/api/v1/predict?umbral=` | Multipart con la imagen JPEG 640×640 en el campo `file` y `Authorization: Bearer <idToken>`; devuelve las detecciones (`categoria`, `confianza`, `bbox_norm`, `bbox_px`) y la latencia. La app espera `request_timeout_s` (config); el backend responde antes. |
| `GET` | `/api/v1/config` (backend) | Config remota con `If-None-Match`/`ETag` (304 si no cambió); timeout de 3 s. Lleva `Authorization: Bearer <idToken>` de Firebase. |
| `GET` | `/api/v1/config/public` (backend) | Sin token, antes del login: aviso de mantenimiento y registro abierto o cerrado. |

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
- `com.squareup.retrofit2:retrofit:2.11.0` - Cliente HTTP tipado para el backend en Cloud Run.
- `com.squareup.retrofit2:converter-gson:2.11.0` - Conversión automática de las respuestas JSON.
- `com.squareup.okhttp3:logging-interceptor:4.12.0` - Logging de peticiones para depuración.
- `com.google.code.gson:gson:2.11.0` - Mapeo de JSON a POJOs y serialización del resultado guardado en el historial.

### Autenticación
- `com.google.firebase:firebase-bom` + `firebase-auth` - Cuentas de usuario (correo/contraseña y Google).
- `androidx.credentials:credentials`, `credentials-play-services-auth` y `com.google.android.libraries.identity.googleid:googleid` - Selector de cuentas de Google (Credential Manager).

### Base de Datos Local
- `androidx.room:room-runtime` y `room-compiler` (`2.6.1`) - Persistencia del historial de detecciones sobre SQLite.

### Testing
- `junit:junit:4.13.2` - Pruebas unitarias locales.
- `com.squareup.okhttp3:mockwebserver:4.12.0` - Servidor HTTP local para probar el interceptor del token.
- `androidx.test.ext:junit:1.1.5` - Extensiones de JUnit para Android.
- `androidx.test.espresso:espresso-core:3.5.1` - Pruebas de interfaz de usuario.

## 🛠️ Solución de problemas

- **La app se cierra al abrir el historial o al guardar una detección después de cambiar la base de datos:** revisa la sección *Cambios de esquema en Room*. Durante el desarrollo, si se reinstala la app con `android:allowBackup="true"`, Android puede restaurar una base de datos antigua aunque se hayan borrado los datos.
- **"Continuar con Google" no hace nada o da error:** falta la huella SHA-1 en la consola de Firebase o el proveedor Google no está activado; después de añadirla, vuelve a descargar `google-services.json`.
- **No se conecta a la API:** verifica que `BACKEND_BASE_URL` en `local.properties` tenga `https://` y no lleve comillas; después sincroniza Gradle y recompila. Si el indicador se queda en "Conectando…" un rato, el modelo está arrancando en frío (puede tardar cerca de un minuto).
