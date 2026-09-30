# Electra Android — Análisis funcional y técnico · Fase 1

> **Alcance:** desde `init commit` hasta `8b04dac Merge branch 'feature/07-envio-log' into develop` (rama `develop`).
> **Fecha del análisis:** 2026-09-29 · **Versión de la app:** `1.0.1` (versionCode `1`).

---

## 1. Resumen ejecutivo

La primera fase deja construida la **base técnica de la aplicación** y las primeras funcionalidades de usuario:

- Infraestructura: entornos (DEV/PRE/PRO), logging a fichero, red, inyección de dependencias, manejo de errores y un patrón asíncrono común.
- Funcionalidad: **inicio de sesión**, **pantalla principal con toolbar**, **cierre de sesión**, **perfil de usuario**, **cambio de plaza** con caché local y **envío del log de actividad** al servidor (o por correo si falla).

La arquitectura MVVM + Repository es coherente en toda la fase, con separación clara entre DTO y modelo, errores tipados y cancelación de operaciones ligada al ciclo de vida del ViewModel. Hay 147 tests unitarios en verde (según la última ejecución registrada).

Los riesgos principales están en la **build de producción (R8 + Gson)**, en la **sesión solo en memoria** y en **un volcado de datos de usuario en el log** (ver §6).

---

## 2. Alcance de la fase

| # | Rama | Entrega |
|---|------|---------|
| 01 | `feature/01-setup` | Proyecto base, Gradle Kotlin DSL, version catalog. |
| 02 | `feature/02-style` | Theme Material 3 DayNight, colores corporativos, strings (EN/ES), iconos. |
| 03 | `feature/03-log` | Sistema de logging con Timber: Logcat + fichero con rotación y purga. |
| 04 | `feature/04-login` | Retrofit/OkHttp, MVVM, sistema de errores, login, logout, trazas HTTP sin datos sensibles. |
| 05 | `feature/05-toolbar` | Toolbar personalizada de la pantalla principal. |
| 06 | `feature/06-user-profile` | Pantalla de perfil, permisos de menú y globales, cambio de plaza con caché SQLite. |
| 07 | `feature/07-envio-log` | Envío del log al backend (multipart con progreso), con alternativa por correo. |

**Cambios sin commitear en `develop`:** el cualificador `IoExecutor` se ha movido de `core/di` a `core/concurrency`, y se han actualizado sus imports en `ConcurrencyModule`, `ChangePlaceRepository`, `LogUploadRepository` y `ProfileViewModel`. Es coherente, porque agrupa `IoExecutor` con `MainExecutor`.

---

## 3. Análisis funcional

### 3.1 Actores y contexto

- **Operario/usuario de plaza:** se autentica con usuario y contraseña del backend `ElectraWS` y trabaja asignado a una **plaza** (centro de trabajo).
- **Backend ElectraWS:** autentica cada operación con usuario y contraseña (no hay token de sesión), devuelve menú y permisos y recibe los logs.

### 3.2 Mapa de navegación

```text
LoginActivity (launcher)
   │  login OK  (limpia la pila)
   ▼
MainActivity ──(toolbar: Perfil)──► ProfileActivity
   │  atrás                              │  ├─ Cambiar plaza  → PlaceBottomSheet
   ▼                                     │  ├─ Enviar log     → subida / correo
LogoutBottomSheet ◄──────(Cerrar sesión)─┘  └─ atrás/flecha   → MainActivity
   │  confirmar  (limpia la pila)
   ▼
LoginActivity
```

### 3.3 Casos de uso

#### CU-01 · Iniciar sesión
- **Entrada:** usuario y contraseña. El usuario se envía sin espacios alrededor (`trim`); la contraseña, tal cual.
- **Validación local:** los dos campos son obligatorios. El error se muestra en cada campo, el foco va al primer campo inválido y el error se limpia al editar ese campo.
- **Proceso:** `POST ElectraWS/mobile/login/` con `userName`, `password` y `language` (idioma del dispositivo).
- **Resultado OK:** se abre la sesión en memoria (usuario, menú, permisos, plaza y credenciales) y se navega a la pantalla principal vaciando la pila.
- **Errores:** tarjeta de error bajo el formulario, con texto según el código (ver §3.4). Mientras la petición está en curso, los campos y el botón están bloqueados y se ignora cualquier pulsación duplicada.
- **"¿Olvidaste tu contraseña?":** todavía no está disponible; muestra un aviso.

#### CU-02 · Pantalla principal
- Toolbar con el nombre de la app y dos acciones: **Ajustes** (aún no implementada, muestra un aviso) y **Perfil**.
- **Atrás** no cierra la app: abre la confirmación de cierre de sesión.
- El contenido funcional de la pantalla principal (el menú del usuario) todavía no se pinta.

#### CU-03 · Cerrar sesión
- Hoja inferior de confirmación, a la que se llega con "atrás" desde la pantalla principal o con "Cerrar sesión" en el perfil.
- Al confirmar se borra la sesión local y se vuelve al login vaciando la pila. No se llama al backend.
- Si ya hay una hoja visible no se abre otra, y una pulsación repetida no dispara la navegación dos veces.

#### CU-04 · Perfil de usuario
- Muestra el nombre (el completo o, si falta, el usuario), el usuario, la plaza actual (o "Sin asignar") y la versión de la app.
- Si la sesión se ha perdido (el sistema mató el proceso), redirige al login.
- **Visibilidad de "Cambiar plaza"** (`ProfileViewModel.canChangePlaza`). Se muestra si se cumple **cualquiera** de estas condiciones:
  1. el usuario tiene activo el menú `4` (cambio de plaza);
  2. tiene el permiso global `1` (ejecutar);
  3. su plaza actual es una de las plazas fijas (`CTR`).

#### CU-05 · Cambiar plaza
- **Carga de plazas en dos pasos:**
  1. muestra al instante las plazas guardadas en el terminal (SQLite);
  2. en paralelo sincroniza con `POST ElectraWS/mobile/user/place/list/`, guarda **solo las plazas nuevas** (nunca modifica ni borra las existentes) y refresca la lista.
- Si el terminal no tenía plazas, la selección queda bloqueada hasta que termina la sincronización. Si esta falla, el botón principal pasa a **Reintentar**.
- Si falla la sincronización pero hay plazas en caché, se sigue trabajando con la caché sin molestar al usuario.
- Si llegan plazas nuevas con el desplegable abierto, no se aplican hasta que el usuario lo cierra, para que las opciones no se muevan mientras elige.
- **Confirmar** solo está habilitado si la plaza elegida es distinta de la actual y no hay otro cambio en curso.
- **Proceso:** `POST ElectraWS/mobile/user/place/update/`. Si el backend devuelve `true`, la sesión se actualiza, la hoja se cierra y el perfil se repinta con el mensaje "Plaza cambiada a X".
- Mientras el cambio está en curso, la hoja no se puede cerrar.

#### CU-06 · Enviar log de actividad
- Busca el fichero de log más reciente que no esté vacío. Si no hay ninguno, informa y no ofrece reintento.
- Lo sube como multipart a `POST ElectraWS/mobile/file/upload/log/`, junto con las credenciales, la plaza y el idioma.
- El progreso se muestra en porcentaje. Al llegar al 100 % pasa a "Confirmando entrega…" (barra indeterminada) mientras responde el servidor.
- **Política de fallos:**
  - 1.er fallo → Snackbar con **Reintentar**;
  - 2.º fallo consecutivo → Snackbar con **Enviar por correo** (visible 8 s);
  - por correo: `ACTION_SEND` restringido a apps de correo (selector `mailto:`), con el log adjunto vía `FileProvider`. Si no hay app de correo, se abre el selector general.

### 3.4 Catálogo de errores visibles

| Origen | Código | Mensaje (resumen) |
|--------|--------|-------------------|
| Red | `NO_CONNECTION` | Sin conexión, revisa la red. |
| Red | `TIMEOUT` | El servidor tarda demasiado. |
| API | `ERROR_A01` | Usuario o contraseña incorrectos. |
| API | `ERROR_A02` | Usuario no activo. |
| API | `ERROR_M01` | Usuario sin menú configurado. |
| API | `ERROR_T01` | No se pudo asignar la plaza. |
| API | `ERROR_C01` / `ERROR_C06` / `ERROR_CCB_04_BARCODE_INVALID` | Petición o código de barras no válidos (preparados para próximas fases). |
| API | código no mapeado | Se muestra el `errorDescription` del servidor o, si no viene, un mensaje genérico. |
| HTTP | `HTTP_xxx` | Mensaje genérico. |

### 3.5 Reglas de negocio implementadas

- La contraseña **nunca** se guarda en disco ni llega a la UI: solo vive en memoria dentro de `UserSession`.
- Las plazas locales son **de solo alta**: la sincronización añade, pero no actualiza ni elimina.
- Las plazas sin código se descartan; las que no tienen descripción se muestran solo con el código.
- El log se envía como **instantánea**: se copia en memoria antes de enviarlo porque el fichero sigue creciendo.

---

## 4. Análisis técnico

### 4.1 Stack

| Área | Tecnología |
|------|------------|
| Lenguaje | Java 17 (records, sealed interfaces, pattern matching en `instanceof`/`switch`) |
| SDK | `minSdk 29`, `targetSdk 36`, `compileSdk 36` |
| Build | AGP 8.13.2, Gradle Kotlin DSL, version catalog; el daemon de Gradle requiere JDK 21 |
| UI | XML Views, ViewBinding, Material 3 (DayNight), edge-to-edge |
| DI | Hilt 2.58 |
| Red | Retrofit 3.0.0 + Gson, OkHttp 4.12.0 |
| Persistencia | `SQLiteOpenHelper` nativo (sin Room) |
| Logging | Timber 4.7.1 |
| Tests | JUnit 4, `arch-core-testing` y fakes propios (sin Mockito ni Robolectric) |

### 4.2 Entornos (`app/build.gradle.kts`)

Los entornos se declaran en **un único sitio** y de ahí se generan los `buildType`. Las variantes `debug` y `release` quedan solo como plantillas deshabilitadas.

| Entorno | applicationId | Base URL | Logcat | Fichero | Nivel mínimo | Minify |
|---------|---------------|----------|--------|---------|--------------|--------|
| `dev` | `com.redur.electra.dev` | `http://192.168.1.17:8085/` | ✔ | ✔ | VERBOSE | ✘ |
| `pre` | `com.redur.electra.pre` | `https://pre.api.example.com/` *(placeholder)* | ✘ | ✔ | DEBUG | ✘ |
| `pro` | `com.redur.electra` | `https://api.example.com/` *(placeholder)* | ✘ | ✔ | INFO | ✔ R8 + shrink |

- El tráfico en claro (`usesCleartextTraffic`) solo se permite si la URL del entorno usa `http://`.
- La firma se lee de `keystore.properties`, que está fuera del repo. Si falta, la build `pro` sale sin firmar y Gradle muestra un aviso.
- `BuildConfig`: `ENVIRONMENT`, `BASE_URL`, `LOG_TO_LOGCAT`, `LOG_TO_FILE`, `LOG_MIN_PRIORITY` e `INACTIVITY_TIMEOUT_MS` (15 min; **todavía no se usa**).

### 4.3 Arquitectura y paquetes

```text
com.redur.electra
├── ElectraApp                 @HiltAndroidApp · inicializa el logging
├── core/
│   ├── async/                 Cancellable, ResultCallback<T>
│   ├── concurrency/           @IoExecutor, @MainExecutor, ConcurrencyModule
│   ├── di/                    NetworkModule, DatabaseModule
│   ├── error/                 AppError (sealed: Network | Api), NetworkType
│   ├── log/                   FileLoggingTree, LoggingInitializer, SensitiveAwareHttpLoggingInterceptor
│   ├── ui/                    UiState (sealed), UiText (sealed), ErrorUiMapper
│   └── util/                  Validations
├── data/
│   ├── local/                 ElectraDatabaseHelper, dao/plaza (PlaceDao, SqlitePlaceDao)
│   ├── model/                 User, MenuItem, Place            ← modelos de dominio (records)
│   ├── remote/
│   │   ├── api/               LoginApiService, PlaceApiService, FileApiService
│   │   ├── dto/               request/… response/…             ← DTOs Gson (records)
│   │   ├── mapper/            UserMapper, PlaceMapper          ← mapeo manual DTO → modelo
│   │   └── upload/            ProgressRequestBody
│   ├── repository/            BaseRepository, LoginRepository, ChangePlaceRepository,
│   │                          LogRepository, LogUploadRepository
│   └── session/               UserSession (@Singleton), Credentials
└── ui/
    ├── login/                 LoginActivity, LoginViewModel, LoginFormState
    ├── logout/                LogoutBottomSheet, LogoutViewModel
    ├── place/                 PlaceBottomSheet, PlaceViewModel, PlaceAdapter
    ├── profile/               ProfileActivity, ProfileViewModel, LogSendState
    └── MainActivity, MainViewModel
```

Flujo de dependencias: `Activity/BottomSheet → ViewModel (@HiltViewModel) → Repository → ApiService / DAO / UserSession`.

### 4.4 Patrones transversales

**Operaciones asíncronas.** Todos los repositorios exponen:

```java
Cancellable operacion(..., ResultCallback<T> callback)
```

- El callback se invoca **una sola vez**, **en el hilo principal**, y **nunca después de `cancel()`**.
- Cada ViewModel guarda la operación pendiente y la cancela en `onCleared()`.
- Para red se usa `Call.enqueue` de Retrofit, cuyos callbacks llegan al hilo principal en Android. Para disco se usa `@IoExecutor`, y el resultado vuelve al hilo principal con `@MainExecutor`, comprobando antes un `AtomicBoolean canceled`.

**Errores.**
1. `BaseRepository.extractData()` interpreta el sobre `ApiResponseDTO{status, errorText, errorList, data}`: HTTP no exitoso → cuerpo nulo → primer error de `errorList` → `data` nulo.
2. `BaseRepository.toAppError()` traduce las excepciones: `InterruptedIOException` → `TIMEOUT`, cualquier otra `IOException` → `NO_CONNECTION`, y el resto → `Api(null, null)`.
3. `ErrorUiMapper` convierte `AppError` en `UiText` a partir del mapa de códigos.
4. La Activity resuelve el `UiText` con su `Context`, así que el ViewModel no necesita `Context` y se respeta el idioma.

**Estado de UI.** `UiState` (`Idle | Loading | Success | Error(UiText)`) se usa en las operaciones simples. Las features con más matices tienen su propio estado:
- `LoginFormState`: errores por campo.
- `LogSendState`: `Idle | Sending(%) | Sent | Failed(msg, Recovery) | ReadyToEmail(file)`, con eventos de un solo uso que se consumen con `onLogSendHandled()`.

**Mapeo DTO → modelo.** Se hace con mappers manuales inyectables. No se usa MapStruct. Toleran los nulos que deja Gson: las colecciones ausentes se tratan como vacías y se descartan las entradas sin id.

### 4.5 Red

| Servicio | Endpoint | Petición | Respuesta |
|----------|----------|----------|-----------|
| `LoginApiService.login` | `POST ElectraWS/mobile/login/` | JSON `LoginRequestDTO` | `ApiResponseDTO<UserDTO>` |
| `PlaceApiService.getPlaces` | `POST ElectraWS/mobile/user/place/list/` | JSON `PlaceListRequestDTO` | `ApiResponseDTO<List<PlaceDTO>>` |
| `PlaceApiService.changePlace` | `POST ElectraWS/mobile/user/place/update/` | JSON `ChangePlaceRequestDTO` | `ApiResponseDTO<Boolean>` |
| `FileApiService.uploadLog` | `POST ElectraWS/mobile/file/upload/log/` | multipart (`file`, `plzs_id`, `userName`, `password`, `language`) | `ApiResponseDTO<Boolean>` |

- OkHttp: connect 15 s, read 20 s, write 20 s.
- `SensitiveAwareHttpLoggingInterceptor` solo se instala si `BuildConfig.DEBUG`:
  - registra el cuerpo completo salvo en los 4 endpoints anteriores, de los que solo registra cabeceras;
  - redacta siempre `Authorization`, `Cookie` y `Set-Cookie`;
  - evita además que el volcado del cuerpo lea el log entero y falsee el progreso de subida.
- `ProgressRequestBody` escribe en bloques de 8 KB e informa del porcentaje solo cuando cambia.

### 4.6 Persistencia local

- Base de datos `electra.db`, versión 1, con una tabla `place(plzs_id TEXT PK, description TEXT)`.
- `SqlitePlaceDao.insertMissing` usa una transacción con `CONFLICT_IGNORE`; `getAll` devuelve las plazas ordenadas por código.
- La base de datos se abre de forma perezosa y siempre desde `@IoExecutor`.
- `onUpgrade` está vacío: cada cambio de esquema futuro tendrá que añadir su migración.

### 4.7 Sesión

- `UserSession` es `@Singleton` y **solo vive en memoria**. Guarda un record interno `Active(user, credentials)` publicado de forma atómica (`volatile`), de modo que nunca se lee el usuario sin sus credenciales ni al revés.
- `Credentials.toString()` y `UploadFileDTO.toString()` enmascaran la contraseña.
- El backend no usa tokens: la contraseña viaja en **cada** petición autenticada.

### 4.8 Logging

- `LoggingInitializer` planta los árboles de Timber según `BuildConfig` e instala un handler de crash que vacía la cola del log a fichero (hasta 2 s) antes de delegar en el handler anterior.
- `FileLoggingTree`:
  - escribe desde un único hilo daemon (`file-logger`) y hace `flush` en cada línea;
  - rota los ficheros por día y al llegar a 5 MB;
  - purga los ficheros de más de 7 días o cuando el total supera 50 MB, tanto al arrancar como en cada rotación;
  - los ficheros están en `getExternalFilesDir("logs")`, o en `filesDir/logs` si no hay almacenamiento externo, y el `FileProvider` los expone para compartirlos por correo;
  - formato de línea: `yyyy-MM-dd HH:mm:ss.SSS P/Tag [hilo]: mensaje`.

### 4.9 Concurrencia

- `@IoExecutor`: `Executors.newCachedThreadPool()`, que crea hilos bajo demanda.
- `@MainExecutor`: `Handler(Looper.getMainLooper())::post`.
- No hay I/O en el hilo principal: los accesos a disco llevan `@WorkerThread` y se ejecutan en el executor de I/O.

### 4.10 UI, design system y accesibilidad

- `Theme.Electra` hereda de `Theme.Material3.DayNight.NoActionBar` y tiene variante `values-night`.
- Colores corporativos: `green_redur #0F4945`, `green_redur_light`, `aegean`, y paletas semánticas de error, aviso, información y éxito.
- Tokens de movimiento (`motion.xml`): 100, 150, 200 y 300 ms; escala al pulsar `0.97`; desplazamiento de entrada `24dp`.
- Estilos reutilizables: `Widget.Electra.Toolbar`, `Widget.Electra.Button.ListAction` y su variante `.Destructive`.
- `dimens.xml` con variante `values-w600dp` para tablets.
- Detalles cuidados en esta fase:
  - edge-to-edge con gestión de insets y del teclado; en el login, el campo con foco se desplaza completo, incluida su línea de error;
  - el hueco de los errores de formulario queda reservado para que el contenido no salte;
  - transiciones cortas con ease-out;
  - el texto de los avisos se fija antes de hacerlos visibles, para que TalkBack lea el mensaje correcto;
  - `contentDescription` en los indicadores de progreso y altura mínima táctil en los botones.
- Localización: inglés por defecto y español en `values-es`, separados en `strings`, `strings_error`, `strings_messages` y `strings_validation`.

### 4.11 Testing

| Área | Clases de test | Tests |
|------|----------------|------:|
| core/log | `FileLoggingTreeTest`, `SensitiveAwareHttpLoggingInterceptorTest` | 19 |
| core/ui | `ErrorUiMapperTest`, `UiTextTest` | 14 |
| DTO / mapper / upload | `UploadFileDTOTest`, `PlaceRequestDTOTest`, `PlaceMapperTest`, `UserMapperTest`, `ProgressRequestBodyTest` | 18 |
| Repositorios | `LoginRepositoryTest`, `ChangePlaceRepositoryTest`, `LogRepositoryTest`, `LogUploadRepositoryTest` | 42 |
| Sesión | `UserSessionTest` | 6 |
| ViewModels | `Login`, `Logout`, `Main`, `Place`, `Profile` | 47 |
| Plantilla | `ExampleUnitTest` | 1 |
| **Total** | | **147** |

- Los dobles de prueba están escritos a mano en `fake/` (`FakeCall`, `Fake*ApiService`, `FakePlaceDao`, respuestas de ejemplo). También hay `TimberTestRule` y `MutableClock`, que controla el reloj en los tests de rotación y purga.
- Última ejecución registrada (`app/build/test-results/testDevUnitTest`, 2026-09-29 16:25): **147 tests, 0 fallos, 0 errores**.
- No hay tests instrumentados (solo la plantilla `ExampleInstrumentedTest`) ni tests del `SqlitePlaceDao` real.

---

## 5. Puntos fuertes

1. **Arquitectura consistente**: todas las features siguen el mismo patrón `Cancellable` + `ResultCallback` + `UiState`.
2. **Errores tipados** con `AppError` sealed, y **textos independientes del `Context`** con `UiText`.
3. **Preocupación real por la seguridad de los logs**: `toString` enmascarado, interceptor que conoce los endpoints sensibles, y solo se registra el código de error de la API, no el mensaje.
4. **Logging a fichero robusto**: escritura ordenada, rotación, purga y vaciado del log ante un crash.
5. **Buena UX en los casos límite**: caché de plazas que no bloquea, desplegable que no se mueve mientras el usuario elige, reintento en el mismo sitio y alternativa por correo.
6. **Entornos centralizados** y generados desde una única declaración.
7. **Buena cobertura unitaria** de la lógica de ViewModels y repositorios.

---

## 6. Hallazgos y riesgos

Ordenados por prioridad.

### Altos

| # | Hallazgo | Dónde | Impacto |
|---|----------|-------|---------|
| H1 | **`proguard-rules.pro` está vacío**, pero `pro` usa R8. Los DTO son records serializados por reflexión con Gson y algunos campos no tienen `@SerializedName` (p. ej. `LoginRequestDTO`, `password` y `language` de `PlaceListRequestDTO`). Además, los comentarios del código dan por hechas reglas que no existen: que R8 elimina los logs v/d y que se conservan los nombres de clase para los tags de Timber. | `app/proguard-rules.pro`, `data/remote/dto/**` | R8 puede renombrar los campos y enviar JSON con claves incorrectas: **el login en PRO podría fallar**. Los tags de log quedarían ofuscados. Hay que validarlo con una build `pro` real. |
| H2 | **Se vuelca `UserDTO` completo al log** (nombre, plaza, menú y permisos) a nivel DEBUG. | [LoginRepository.java:88](app/src/main/java/com/redur/electra/data/repository/LoginRepository.java:88) | En DEV y **PRE** (`minLevel DEBUG` y escritura a fichero) quedan datos personales en el fichero, que además se envía al servidor o por correo. |
| H3 | **Sesión solo en memoria.** `ProfileActivity` comprueba si se ha perdido, pero `MainActivity` no lo hace. | [MainActivity.java](app/src/main/java/com/redur/electra/ui/MainActivity.java) | Si el sistema mata el proceso, la app vuelve a la pantalla principal "vacía" y cualquier operación posterior falla con un error genérico. |

### Medios

| # | Hallazgo | Dónde |
|---|----------|-------|
| M1 | `insertMissing` captura `Exception` y la registra, pero **devuelve un recuento de filas que después se deshacen**, y se "traga" la `SQLException` que el repositorio sí espera tratar. | [SqlitePlaceDao.java:75](app/src/main/java/com/redur/electra/data/local/dao/plaza/SqlitePlaceDao.java:75) |
| M2 | Las reglas de permisos usan **números y códigos mágicos** en el ViewModel (`MENU_CAMBIO_PLAZA = 4`, `PERMISO_EJECUTAR = 1`, `PLAZAS_FIJAS = ["CTR"]`), y la regla combinada con OR es poco evidente. | [ProfileViewModel.java:36](app/src/main/java/com/redur/electra/ui/profile/ProfileViewModel.java:36) |
| M3 | `User.hasMenuActive` exige `count() == 1`: si el menú llega duplicado, devuelve `false`. | [User.java:38](app/src/main/java/com/redur/electra/data/model/user/User.java:38) |
| M4 | La contraseña viaja en **cada** petición, y en DEV por `http://` (en claro). Depende del diseño del backend; conviene plantear un token de sesión. | `ChangePlaceRepository`, `LogUploadRepository` |
| M5 | Copias de seguridad: `allowBackup="true"` con las reglas de ejemplo sin configurar, por lo que `electra.db` y los logs pueden acabar en la copia en la nube. | `AndroidManifest.xml`, `res/xml/backup_rules.xml` |
| M6 | Para códigos no mapeados se muestra el **mensaje del servidor tal cual** (`UiText.Raw`). Si el backend devuelve texto técnico, llega al usuario. | [ErrorUiMapper.java:61](app/src/main/java/com/redur/electra/core/ui/ErrorUiMapper.java:61) |

### Bajos / limpieza

| # | Hallazgo | Dónde |
|---|----------|-------|
| B1 | `setupToolbar(String subtitle)` **no usa** el parámetro: el nombre del usuario no se muestra en la toolbar. | [MainActivity.java:58](app/src/main/java/com/redur/electra/ui/MainActivity.java:58) |
| B2 | El método `showCanEditPlaza()` está vacío. | `ProfileActivity.java` |
| B3 | `INACTIVITY_TIMEOUT_MS` está definido pero no se usa. | `app/build.gradle.kts` |
| B4 | Las URLs de PRE y PRO son placeholders (`example.com`). | `app/build.gradle.kts` |
| B5 | `newCachedThreadPool()` no tiene límite de hilos. Con la carga actual es aceptable, pero conviene vigilarlo si crecen las tareas de I/O. | [ConcurrencyModule.java:25](app/src/main/java/com/redur/electra/core/concurrency/ConcurrencyModule.java:25) |
| B6 | `PlaceBottomSheet` consulta cada 300 ms si el desplegable se ha cerrado (polling), aunque de forma acotada y limpiada en `onDestroyView`. | [PlaceBottomSheet.java:35](app/src/main/java/com/redur/electra/ui/place/PlaceBottomSheet.java:35) |
| B7 | `CLAUDE.md` menciona Mockito y Robolectric, pero no están en las dependencias. Los tests usan fakes, que es válido, pero conviene alinear la documentación. | `app/build.gradle.kts` |
| B8 | `constraintlayout` se usa en los layouts sin declararse como dependencia: llega de forma transitiva a través de Material. | `activity_main.xml` |
| B9 | En este equipo la build falla desde la consola porque el daemon de Gradle exige un **JDK 21** que no está instalado (sí funciona desde Android Studio). | `gradle/gradle-daemon-jvm.properties` |

---

## 7. Recomendaciones para la siguiente fase

1. **Antes de publicar en PRO:** añadir las reglas de R8 (DTOs y Gson, conservación de nombres para los tags de Timber, eliminación de `Log.v/d`) y validar el login y el cambio de plaza con una build `pro`. *(H1)*
2. Cambiar el log de login para registrar solo el `username`. *(H2)*
3. Centralizar la comprobación de sesión (p. ej. en una `BaseActivity` autenticada o en el `onCreate` de `MainActivity`) y volver al login si se ha perdido. *(H3)*
4. Mover los identificadores de menú y permisos a constantes de dominio (p. ej. en `User`/`MenuItem` o en una clase de permisos) con nombres expresivos. *(M2)*
5. Configurar `backup_rules` y `data_extraction_rules` para excluir la base de datos y los logs. *(M5)*
6. Pintar el menú del usuario en la pantalla principal, que es el contenido pendiente del CU-02.
7. Implementar el cierre de sesión por inactividad usando `INACTIVITY_TIMEOUT_MS`.
8. Añadir tests instrumentados o Robolectric para `SqlitePlaceDao` y un smoke test de navegación.
