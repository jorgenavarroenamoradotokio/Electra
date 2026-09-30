# Electra

Aplicación Android de Redur para operarios de plaza. Se conecta al backend **ElectraWS** para autenticar al usuario, gestionar la plaza (centro de trabajo) en la que trabaja y enviar el registro de actividad del terminal.

> Estado: **Fase 1 completada** (login, pantalla principal, perfil, cambio de plaza, envío de log).
> Análisis funcional y técnico detallado: [docs/analisis-fase-01.md](docs/analisis-fase-01.md).

---

## Funcionalidades

- **Inicio de sesión** con usuario y contraseña, con validación de formulario y mensajes de error según el código que devuelve el backend.
- **Pantalla principal** con toolbar (acceso al perfil y a ajustes). "Atrás" pide confirmar el cierre de sesión.
- **Cierre de sesión** mediante una hoja inferior de confirmación.
- **Perfil de usuario:** nombre, usuario, plaza actual y versión de la app.
- **Cambio de plaza:** plazas guardadas en el terminal (SQLite) y sincronizadas con el backend. La opción solo aparece para los usuarios con permiso.
- **Envío del log de actividad** al servidor, con progreso. Si falla dos veces seguidas, se ofrece enviarlo por correo.

## Stack

| Área | Tecnología |
|------|------------|
| Lenguaje | Java 17 (sin Kotlin) |
| Android | `minSdk 29` · `targetSdk 36` · AndroidX · Material 3 |
| UI | XML Views + ViewBinding |
| Arquitectura | MVVM + Repository |
| DI | Hilt |
| Red | Retrofit 3 + Gson · OkHttp 4 |
| Persistencia | SQLite (`SQLiteOpenHelper`) |
| Logging | Timber (Logcat + fichero con rotación) |
| Tests | JUnit 4 · `arch-core-testing` · fakes propios |

## Requisitos

- Android Studio (versión compatible con AGP 8.13).
- **JDK 21** para el daemon de Gradle (`gradle/gradle-daemon-jvm.properties`). El código compila con Java 17.
- Android SDK 36.

## Entornos

Los entornos se definen en un único sitio: la lista `environments` de [app/build.gradle.kts](app/build.gradle.kts). Cada uno genera su `buildType`.

| Variante | applicationId | Nombre | Logs | Minify |
|----------|---------------|--------|------|--------|
| `dev` | `com.redur.electra.dev` | Electra DEV | Logcat + fichero (VERBOSE) | No |
| `pre` | `com.redur.electra.pre` | Electra PRE | Fichero (DEBUG) | No |
| `pro` | `com.redur.electra` | Electra | Fichero (INFO) | Sí (R8) |

Para cambiar la URL del backend, el nombre de la app o el nivel de log de un entorno, edita su entrada en `environments`. Las URLs de PRE y PRO todavía son valores de ejemplo.

La versión se define en `gradle.properties` (`electra.versionCode`, `electra.versionName`).

### Firma de release

La variante `pro` se firma con los datos de un fichero `keystore.properties` en la raíz del proyecto. Este fichero está en `.gitignore` y **nunca se versiona**:

```properties
storeFile=ruta/al/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Sin este fichero, la build `pro` se genera sin firmar y Gradle muestra un aviso.

## Compilar y ejecutar

Instalar la variante de desarrollo:

```bash
./gradlew installDev
```

Generar el APK de producción:

```bash
./gradlew assemblePro
```

Ejecutar los tests unitarios (se lanzan contra `dev`):

```bash
./gradlew testDevUnitTest
```

## Estructura del proyecto

```text
app/src/main/java/com/redur/electra/
├── core/        Infraestructura común: async, concurrencia, DI, errores, logging, estado de UI
├── data/
│   ├── local/       SQLite (helper + DAO)
│   ├── model/       Modelos de dominio (User, MenuItem, Place)
│   ├── remote/      API Retrofit, DTOs, mappers, subida de ficheros
│   ├── repository/  Repositorios (login, plazas, logs)
│   └── session/     Sesión del usuario en memoria
└── ui/          Activities, BottomSheets y ViewModels por feature
                 (login, logout, place, profile, main)
```

Flujo: `UI → ViewModel → Repository → API / DAO / Session`.

### Convenciones clave

- Las operaciones asíncronas devuelven un `Cancellable` y notifican el resultado con `ResultCallback<T>`, siempre en el hilo principal. Los ViewModels las cancelan en `onCleared()`.
- Los errores se modelan con `AppError` (`Network` | `Api`) y se traducen a texto con `ErrorUiMapper` → `UiText`, que la UI resuelve con su `Context`.
- El estado de las operaciones de UI se expone con `UiState` (`Idle` | `Loading` | `Success` | `Error`).
- Los DTOs no llegan a la UI: se convierten en modelos con mappers inyectables.
- Los textos visibles para el usuario están en recursos (`values/` en inglés, `values-es/` en español).

Las reglas completas de desarrollo están en [CLAUDE.md](CLAUDE.md).

## Logs

- En los entornos con log a fichero, se escriben en `Android/data/<applicationId>/files/logs/electra_yyyy-MM-dd[_n].log`.
- Rotación diaria y a partir de 5 MB. Se conservan 7 días, con un máximo de 50 MB en total.
- Para extraerlos:

```bash
adb pull /sdcard/Android/data/com.redur.electra.dev/files/logs
```

- Las contraseñas y los cuerpos de los endpoints sensibles nunca se registran.

## Backend

| Operación | Endpoint |
|-----------|----------|
| Login | `POST ElectraWS/mobile/login/` |
| Listado de plazas | `POST ElectraWS/mobile/user/place/list/` |
| Cambio de plaza | `POST ElectraWS/mobile/user/place/update/` |
| Subida de log | `POST ElectraWS/mobile/file/upload/log/` (multipart) |

Todas las respuestas usan el formato `{ status, errorText, errorList[], data }`.

## Flujo de ramas

- `main`: versión estable.
- `develop`: integración.
- `feature/NN-nombre`: cada funcionalidad, que se integra en `develop` con un merge.
- Los mensajes de commit usan prefijos convencionales: `feat:`, `fix:`, `style:`…
