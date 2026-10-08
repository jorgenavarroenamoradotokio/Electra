# Electra Android — Análisis funcional

> **Alcance:** todo el proyecto en la rama `develop` hasta `b8a6da5 refactor(log): mejoramos sistema de log`.
> **Fecha del análisis:** 2026-10-08 · **Versión de la app:** `1.0.1` (versionCode `1`).
> **Documento previo:** [analisis-fase-01.md](analisis-fase-01.md) (fase 1: login, perfil, plazas y log). Este documento lo amplía a todas las funcionalidades actuales.

---

## Índice

1. [Introducción](#1-introducción)
2. [Descripción general del sistema](#2-descripción-general-del-sistema)
3. [Mapa funcional y estado de implementación](#3-mapa-funcional-y-estado-de-implementación)
4. [Navegación](#4-navegación)
5. [Seguridad, sesión y permisos](#5-seguridad-sesión-y-permisos)
6. [Casos de uso](#6-casos-de-uso)
7. [Reglas de negocio consolidadas](#7-reglas-de-negocio-consolidadas)
8. [Modelo de datos funcional](#8-modelo-de-datos-funcional)
9. [Integraciones externas](#9-integraciones-externas)
10. [Catálogo de mensajes y errores](#10-catálogo-de-mensajes-y-errores)
11. [Requisitos no funcionales](#11-requisitos-no-funcionales)
12. [Limitaciones, pendientes y riesgos](#12-limitaciones-pendientes-y-riesgos)
13. [Anexos](#13-anexos)

---

## 1. Introducción

### 1.1 Objetivo

Describir **qué hace** la aplicación Electra para Android, quién la usa, qué reglas aplica y en qué estado está cada funcionalidad. El documento sirve como referencia para negocio, QA y desarrollo. Los detalles de implementación solo aparecen cuando explican un comportamiento visible.

### 1.2 Fuentes

El análisis se ha hecho a partir del código fuente (`app/src/main`), los recursos de texto (`values`, `values-es`), el grafo de navegación, el manifest, los tests unitarios y el historial de git. **No se ha contrastado con documentación del backend ElectraWS**: los contratos de los servicios se deducen de los DTOs de la app.

### 1.3 Glosario

| Término | Significado |
|---------|-------------|
| **Plaza** | Centro de trabajo (delegación/almacén) al que está asignado el operario. Código p. ej. `CTR`, `ZAR`. |
| **Bulto** | Paquete físico que se lee, pesa, mide, clasifica o etiqueta. |
| **CB** | Código de barras del bulto. |
| **Lectura** | Registro de un bulto escaneado en una operación (p. ej. recogidas). |
| **Tipo de bulto** | Clasificación del bulto (palé, caja…) con código, abreviatura y descripción. |
| **Cubicar** | Medir alto, ancho y profundo para calcular el volumen. |
| **Expedición completa** | Marca de la lectura (`EXPEDICION_COMPLETA = 'S'`) que obliga a informar el peso. |
| **Muelle / puerta de salida** | Puerta del almacén por la que debe salir el bulto según su destino. |
| **Incidencia** | Anomalía registrada sobre un bulto (código p. ej. `A21`). |
| **ZPL** | Lenguaje de impresión de las impresoras de etiquetas Zebra. |
| **Menú del servidor** | Lista de opciones que el backend devuelve al iniciar sesión según el perfil del usuario. |

---

## 2. Descripción general del sistema

### 2.1 Propósito

Electra es la aplicación de Redur para **operarios de plaza**. Sustituye y amplía los procesos de terminal de almacén: identificación del operario, gestión de su plaza, captura de datos de bultos (tipo, peso, volumen, incidencias, muelle), toma de fotos, generación e impresión de etiquetas y envío de trazas de actividad para soporte.

### 2.2 Actores

| Actor | Descripción |
|-------|-------------|
| **Operario de plaza** | Usuario final. Trabaja con un terminal Android (normalmente con escáner integrado) y una impresora Bluetooth. |
| **Administrador** | No usa la app, pero configura en el backend el menú, los permisos y la plaza de cada usuario. Es a quien remiten muchos mensajes de error. |
| **Backend ElectraWS** | Autentica, devuelve menú/permisos, plazas, tipos de bulto y etiquetas, y recibe ficheros (log e imágenes). |
| **Impresora Bluetooth** | Recibe la etiqueta ZPL por puerto serie (SPP). |
| **Sistema Android** | Concede permisos (cámara, fotos, dispositivos cercanos), proporciona cámara, galería y apps de correo. |

### 2.3 Entornos

| Entorno | Nombre visible | Uso | Backend |
|---------|----------------|-----|---------|
| DEV | Electra DEV | Desarrollo | `http://192.168.1.17:8085/` (red local) |
| PRE | Electra PRE | Preproducción | *URL de ejemplo, pendiente de definir* |
| PRO | Electra | Producción | *URL de ejemplo, pendiente de definir* |

Las tres variantes pueden instalarse a la vez en el mismo terminal (tienen distinto `applicationId`).

### 2.4 Plataforma

- Android 10 (API 29) o superior; objetivo Android 16 (API 36).
- Teléfono y tablet (hay dimensiones específicas a partir de 600 dp).
- Idiomas: **español** e **inglés** (por defecto). El idioma lo marca el dispositivo y se envía al backend en cada petición (`es`, o `uk` para inglés).
- Cámara y Bluetooth son **opcionales**: la app se instala en terminales sin ellos y lo explica al intentar usarlos.

---

## 3. Mapa funcional y estado de implementación

### 3.1 Funcionalidades

| Id | Funcionalidad | Acceso | Estado |
|----|---------------|--------|--------|
| F-01 | Inicio de sesión | Pantalla de arranque | ✅ Completa |
| F-02 | Pantalla principal y menú lateral dinámico | Tras login | ✅ Completa |
| F-03 | Inicio (saludo) | Menú `0` | ✅ Completa |
| F-04 | Cierre de sesión | Menú lateral, "atrás", perfil | ✅ Completa |
| F-05 | Perfil de usuario | Icono de la toolbar | ✅ Completa |
| F-06 | Cambio de plaza | Menú `4` y perfil | ✅ Completa |
| F-07 | Envío del registro de actividad (log) | Perfil | ✅ Completa |
| F-08 | Toma de foto / imagen de galería y subida | Menú `5`; desde incidencias | ✅ Completa |
| F-09 | Gestión de etiquetas ZPL (opciones) | Menú `6` | ✅ Completa |
| F-10 | Vista previa de etiqueta | Desde F-09 | ✅ Completa (etiqueta fija del backend) |
| F-11 | Impresión de etiqueta por Bluetooth | Desde F-09 | ✅ Completa (etiqueta fija del backend) |
| F-12 | Tipo de bulto | Menú `7` | 🟡 Prototipo: carga real, asignación sin grabar |
| F-13 | Peso y medidas | Menú `9` | 🟡 Prototipo: validación completa, sin grabar |
| F-14 | Volumen (sin peso) | Menú `10` | 🟡 Prototipo: validación completa, sin grabar |
| F-15 | Incidencias | Menú `11` | 🟡 Prototipo: catálogo fijo, sin grabar |
| F-16 | Puerta de salida (muelle) | Menú `12` | 🟡 Prototipo: muelle fijo `Z14` |
| F-17 | Ajustes | Icono de la toolbar | ⛔ No implementada (aviso) |
| F-18 | Carga de camión | Menú `1` | ⛔ No implementada (aviso) |
| F-19 | Consulta de albarán | Menú `2` | ⛔ No implementada (aviso) |
| F-20 | Recuperar contraseña | Login | ⛔ No implementada (aviso) |

**Leyenda:** ✅ funcional extremo a extremo · 🟡 interfaz y validaciones terminadas, pero los datos no se graban o vienen de un origen provisional · ⛔ solo existe el acceso, que muestra "aún no disponible".

### 3.2 Catálogo de menús

El backend decide qué menús ve cada usuario. La app solo sabe **cómo pintar y qué hacer** con los identificadores que conoce:

| Id | Literal (ES) | Tipo | Destino |
|----|--------------|------|---------|
| 0 | Inicio | Pantalla | Inicio (fijo, no lo envía el servidor) |
| 1 | Carga de camión | Pendiente | Aviso "no disponible" |
| 2 | Consulta de albarán | Pendiente | Aviso "no disponible" |
| 3 | Administración | Agrupador | Despliega submenús |
| 4 | Cambiar de plaza | Hoja inferior | F-06 |
| 5 | Toma de foto | Hoja inferior | F-08 |
| 6 | Gestión de etiquetas ZPL | Hoja inferior | F-09 |
| 7 | Tipo de bulto | Pantalla | F-12 |
| 8 | Prototipos | Agrupador | Despliega submenús |
| 9 | Peso y medidas | Pantalla | F-13 |
| 10 | Volumen | Pantalla | F-14 |
| 11 | Incidencias | Pantalla | F-15 |
| 12 | Puerta de salida | Pantalla | F-16 |

Un menú que llega del servidor con un id **no registrado** se muestra con el texto del servidor, un icono genérico (carpeta si tiene hijos, documento si no) y al pulsarlo avisa de que no está disponible. Así el backend puede publicar menús nuevos sin romper versiones antiguas de la app.

---

## 4. Navegación

```text
LoginActivity (arranque)
   │ login OK (se vacía la pila)
   ▼
MainActivity ─────────────────────────────────────────────────────────────┐
 │ Toolbar: [☰ menú] título           [Ajustes ⛔] [Perfil]              │
 │                                                    │                   │
 │ Menú lateral (cabecera: avatar, nombre, usuario)   ▼                   │
 │  ├─ 0  Inicio ............................ HomeFragment   ProfileActivity
 │  ├─ 1  Carga de camión ⛔                               ├─ Cambiar plaza → PlaceBottomSheet
 │  ├─ 2  Consulta de albarán ⛔                           ├─ Enviar log → subida / correo
 │  ├─ 3  Administración ▸                                 └─ Cerrar sesión → LogoutBottomSheet
 │  │    ├─ 4  Cambiar de plaza ......... PlaceBottomSheet (hoja)
 │  │    ├─ 5  Toma de foto ............. PhotoSourceBottomSheet (hoja)
 │  │    └─ 6  Etiquetas ZPL ............ LabelOptionsBottomSheet (hoja)
 │  │                                       ├─ Previsualizar → LabelPreviewFragment
 │  │                                       └─ Imprimir ─────→ PrintLabelBottomSheet (hoja)
 │  ├─ 7  Tipo de bulto ................. BultoTypeFragment
 │  ├─ 8  Prototipos ▸
 │  │    ├─ 9  Peso y medidas ........... BultoWeightFragment
 │  │    ├─ 10 Volumen .................. BultoVolumeFragment
 │  │    ├─ 11 Incidencias .............. BultoIncidenceFragment ─→ PhotoSourceBottomSheet
 │  │    └─ 12 Puerta de salida ......... BultoDockFragment
 │  └─ [Cerrar sesión] → LogoutBottomSheet
 └────────────────────────────────────────────────────────────────────────┘
```

> La agrupación bajo "Administración" y "Prototipos" es ilustrativa: la jerarquía real la decide el backend con el `parentId` de cada menú.

### 4.1 Reglas de navegación

- **Pantallas del menú son hermanas.** Abrir una pantalla del menú vuelve siempre a Inicio como base; no se apilan. Pulsar la opción ya abierta no crea una copia.
- **Hojas inferiores** (cambio de plaza, foto, etiquetas, impresión) se abren **sobre** la pantalla actual, sin sustituirla, y no cambian la opción marcada en el menú.
- En las pantallas del menú la toolbar muestra el **botón de menú**; en las secundarias (vista previa de etiqueta) muestra **"atrás"**.
- El menú se cierra **antes** de abrir el destino, para que la animación no compita con la carga de la pantalla. Si el usuario vuelve a abrirlo antes de que termine de cerrarse, la navegación se descarta.
- La opción activa se resalta y sus menús padre se despliegan solos para que siempre quede a la vista.
- **Botón "atrás"** en la pantalla principal, en este orden:
  1. si el menú está abierto, lo cierra;
  2. si hay una pantalla anterior, vuelve a ella;
  3. si se está en Inicio, pide confirmar el **cierre de sesión** (la app nunca se cierra con "atrás").
- Tras una acción en una hoja (plaza cambiada, imagen enviada, etiqueta impresa), la pantalla principal muestra un aviso breve (Snackbar) con el resultado.

---

## 5. Seguridad, sesión y permisos

### 5.1 Sesión

- La sesión (usuario, plaza, menú, permisos y credenciales) **solo vive en memoria**. No se guarda la contraseña en disco ni se muestra en pantalla.
- El backend **no usa token**: usuario y contraseña viajan en cada petición autenticada.
- Si el sistema destruye el proceso (p. ej. por falta de memoria), la sesión se pierde. El perfil lo detecta y vuelve al login; el resto de pantallas no (ver §12, R-03).
- Cerrar sesión borra la sesión local y vuelve al login vaciando la pila. No se notifica al backend.
- Existe un parámetro de **inactividad de 15 minutos** (`INACTIVITY_TIMEOUT_MS`) que todavía **no se aplica**.

### 5.2 Permisos de negocio

El backend devuelve dos tipos de permisos:

| Tipo | Origen | Uso actual |
|------|--------|-----------|
| **Permisos globales** del usuario | `permiso[].permisoId` | Permiso `1` (ejecutar) habilita el cambio de plaza en el perfil. |
| **Permisos por menú** | `menu[].permisosMenu[].permisosId` | Se pasan a la pantalla al abrirla desde el menú (`MenuArgs`). Las pantallas actuales todavía no los consultan. |

**Visibilidad de "Cambiar de plaza" en el perfil** — se muestra si se cumple **cualquiera** de:

1. el usuario tiene el menú `4` en su menú;
2. tiene el permiso global `1`;
3. su plaza actual es una plaza fija (`CTR`).

Los permisos de un menú se leen de la sesión **en el momento del toque**, no al construir el menú.

### 5.3 Permisos del sistema Android

| Permiso | Para qué | Android 10–11 | Android 12 | Android 13 | Android 14+ |
|---------|----------|---------------|------------|------------|-------------|
| **Cámara** | Hacer fotos | `CAMERA` | `CAMERA` | `CAMERA` | `CAMERA` |
| **Fotos** | Elegir imagen de la galería | `READ_EXTERNAL_STORAGE` | `READ_EXTERNAL_STORAGE` | `READ_MEDIA_IMAGES` | `READ_MEDIA_IMAGES` o acceso parcial ("Seleccionar fotos") |
| **Dispositivos cercanos** | Buscar impresoras y conectarse | Ubicación precisa | `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` | ídem | ídem |

Comportamiento común al pedir un permiso:

| Respuesta del usuario | Comportamiento |
|-----------------------|----------------|
| "Mientras se usa la app" / "Solo esta vez" | Se continúa sin más pasos. |
| "No permitir" (aún se puede volver a preguntar) | Se explica por qué hace falta y se deja volver a intentarlo. |
| Denegado para siempre | Hoja que explica cómo activarlo, con **Abrir ajustes** y **Ahora no**. Al volver de ajustes, si se activó, se continúa solo. |

- En galería basta con **uno** de los permisos (el acceso parcial sirve). En Bluetooth hacen falta **todos** (buscar y conectar).
- La búsqueda Bluetooth en Android 12+ se declara **sin uso de ubicación** (`neverForLocation`).

---

## 6. Casos de uso

Formato: **objetivo · acceso · precondiciones · flujo principal · alternativos · reglas · estado**.

---

### CU-01 · Iniciar sesión

- **Objetivo:** identificar al operario y cargar su perfil (plaza, menú, permisos).
- **Acceso:** pantalla de arranque.
- **Flujo principal:**
  1. El usuario introduce usuario y contraseña y pulsa **Iniciar sesión** (o "Hecho" en el teclado).
  2. La app valida que ambos campos estén informados.
  3. Envía `userName` (sin espacios alrededor), `password` (tal cual) e idioma.
  4. El backend devuelve usuario, nombre, plaza, menú y permisos.
  5. Se abre la sesión y se navega a la pantalla principal vaciando la pila.
- **Alternativos:**
  - *Campo vacío:* error en el propio campo ("Introduce tu usuario/contraseña"); el foco va al primer campo con error; el error desaparece al escribir.
  - *Error de backend o red:* tarjeta de error bajo el formulario con el mensaje del §10.
  - *¿Has olvidado tu contraseña?:* aviso de que aún no está disponible y que contacte con su administrador.
- **Reglas:** mientras la petición está en curso, campos y botón se bloquean y se ignoran pulsaciones repetidas. La versión de la app se muestra al pie.
- **Estado:** ✅

---

### CU-02 · Pantalla principal y menú lateral

- **Objetivo:** dar acceso a las tareas del usuario.
- **Precondiciones:** sesión iniciada.
- **Comportamiento:**
  - **Cabecera del menú:** avatar con la inicial del nombre, nombre completo y usuario. Si no hay nombre completo se muestra el usuario como nombre y no se repite debajo.
  - **Lista:** "Inicio" siempre primero; después el menú del servidor **en el orden recibido**, agrupado por `parentId`. Los grupos empiezan **plegados** y se despliegan/pliegan al tocarlos (el chevron gira).
  - **Robustez ante datos del servidor:**
    - un hijo cuyo padre no llega se muestra en el primer nivel para no perder la opción;
    - ids repetidos o padres en bucle no cuelgan la app (cada id se pinta una vez);
    - si el servidor envía también "Inicio" (id `0`) no se duplica.
  - **Pie del menú:** botón **Cerrar sesión**.
  - **Toolbar:** título de la pantalla actual; acciones **Ajustes** (no disponible) y **Perfil**.
- **Estado:** ✅

### CU-03 · Inicio

- Saludo personalizado "Hola, *nombre*" y la indicación "Abre el menú para elegir una tarea".
- **Estado:** ✅

---

### CU-04 · Cerrar sesión

- **Acceso:** botón del menú lateral, "atrás" en Inicio o "Cerrar sesión" en el perfil.
- **Flujo:** hoja de confirmación "¿Cerrar sesión?" → **Cerrar sesión** borra la sesión y vuelve al login; **Cancelar** cierra la hoja.
- **Reglas:** si ya hay una hoja abierta no se abre otra; una pulsación repetida no navega dos veces.
- **Estado:** ✅

---

### CU-05 · Perfil de usuario

- **Acceso:** icono de perfil de la toolbar.
- **Muestra:** nombre, usuario, plaza actual (o "Sin asignar") y versión de la app (`1.0.1 (1)`).
- **Acciones:** Cambiar de plaza (según §5.2), Enviar registro de actividad (CU-07) y Cerrar sesión.
- **Alternativo:** si la sesión se ha perdido, vuelve al login.
- **Estado:** ✅

---

### CU-06 · Cambiar de plaza

- **Acceso:** menú `4` o perfil.
- **Flujo principal:**
  1. Se muestran **al instante** las plazas guardadas en el terminal.
  2. En paralelo se sincronizan con el backend; las plazas **nuevas** se guardan y la lista se refresca.
  3. El usuario elige una plaza en el desplegable y pulsa **Cambiar**.
  4. El backend confirma; la sesión se actualiza, la hoja se cierra y se avisa "Plaza cambiada a X".
- **Alternativos:**
  - *Terminal sin plazas guardadas:* la selección espera a la sincronización; si falla, el botón pasa a **Reintentar**.
  - *Falla la sincronización pero hay caché:* se trabaja con la caché sin avisar.
  - *Llegan plazas nuevas con el desplegable abierto:* se aplican al cerrarlo, para que las opciones no se muevan mientras se elige.
  - *El backend rechaza el cambio:* mensaje `ERROR_T01` u otro del §10.
- **Reglas:** **Cambiar** solo se habilita si la plaza elegida es distinta de la actual y no hay otro cambio en curso; durante el cambio la hoja no se puede cerrar. Las plazas locales solo se **añaden** (nunca se modifican ni borran); las que llegan sin código se descartan y las que no tienen descripción se muestran con el código.
- **Estado:** ✅

---

### CU-07 · Enviar registro de actividad

- **Acceso:** perfil → "Enviar registro de actividad".
- **Flujo principal:**
  1. Se toma el fichero de log más reciente que no esté vacío (una **instantánea**, porque el fichero sigue creciendo).
  2. Se sube al backend con la plaza, el usuario y el idioma, mostrando el porcentaje.
  3. Al 100 % pasa a "Confirmando el envío…" hasta que responde el servidor.
  4. Aviso "Registro de actividad enviado".
- **Alternativos:**
  - *No hay log:* "Todavía no hay registro de actividad…", sin reintento.
  - *1.er fallo:* aviso con **Reintentar**.
  - *2.º fallo seguido:* aviso (8 s) con **Enviar por correo**: abre una app de correo con el log adjunto, asunto "Registro de *usuario* · *plaza* · *fecha*" y un texto explicativo. Si no hay app de correo se abre el selector general.
- **Reglas:** una pulsación repetida durante el envío no lanza otra subida.
- **Estado:** ✅

---

### CU-08 · Hacer o elegir una foto y enviarla

- **Acceso:** menú `5` (Toma de foto) o "Hacer foto" en Incidencias (CU-15).
- **Flujo principal:**
  1. Hoja "Añadir foto" con **Hacer foto**, **Elegir de la galería** y **Cancelar**.
  2. Se pide el permiso del origen elegido (§5.3).
  3. Se abre la cámara (la foto se guarda en la caché de la app) o el selector de fotos.
  4. Con la imagen lista se **sube al backend** mostrando el progreso ("Enviando imagen… 45 %" → "Confirmando el envío…").
  5. Al confirmarse, la hoja se cierra y devuelve la imagen a quien la pidió; desde el menú se avisa "Imagen enviada".
- **Alternativos:**
  - *Permiso denegado:* mensaje explicativo y se puede volver a intentar.
  - *Permiso bloqueado:* hoja de ajustes (§5.3).
  - *No hay app de cámara/galería:* mensaje específico.
  - *No se puede preparar el fichero:* "Comprueba que el dispositivo tiene espacio libre…".
  - *El usuario sale de la cámara o galería sin foto:* la hoja vuelve a su estado inicial sin error; la captura vacía se borra.
  - *Falla la subida:* mensaje de error con **Volver a enviar** (reenvía la misma imagen sin repetirla) o elegir otro origen.
- **Reglas:**
  - Se ignora un doble toque mientras hay una elección en curso o se está enviando.
  - Se comprueba el **fichero** y no solo la respuesta de la cámara: hay apps que dicen "cancelado" aunque guardaron la foto.
  - El estado sobrevive a que el sistema mate el proceso mientras la cámara está abierta. **El envío no**: si se cierra la hoja, se cancela.
  - La imagen se envía con usuario, contraseña, plaza e idioma; si el proveedor no da nombre se genera `IMG_<timestamp>.jpg`.
- **Estado:** ✅

---

### CU-09 · Gestión de etiquetas ZPL

- **Acceso:** menú `6`.
- **Flujo:** hoja "Etiqueta" con **Previsualizar**, **Imprimir** y **Cancelar**. La opción elegida **sustituye** a esta hoja (no se apilan dos) y un doble toque no abre dos destinos.
- **Estado:** ✅

### CU-10 · Vista previa de etiqueta

- **Flujo principal:**
  1. Al abrir la pantalla se pide la etiqueta al backend ("Generando etiqueta…").
  2. Llegan el ZPL y cada página como imagen PNG.
  3. Se muestran las páginas con un fundido breve; cada una con descripción accesible "Etiqueta, página X de N".
- **Alternativos:**
  - *Error de red/backend:* mensaje y **Reintentar**.
  - *Alguna página dañada:* "La etiqueta ha llegado dañada…" con **Reintentar**. Una etiqueta incompleta no se muestra a medias.
- **Reglas:** girar la pantalla no repite la petición; salir la cancela.
- **Limitación:** el backend devuelve **siempre la misma etiqueta** de prueba; la petición solo envía credenciales, idioma y plaza (no hay bulto ni expedición).
- **Estado:** ✅ (contenido provisional)

### CU-11 · Imprimir etiqueta por Bluetooth

- **Flujo principal:**
  1. Se pide el permiso de **dispositivos cercanos**.
  2. Se comprueba que el terminal tiene Bluetooth y que está activado.
  3. Se listan las impresoras **emparejadas** y, a la vez, se **buscan** otras cercanas ("Buscando impresoras cercanas…").
  4. El usuario toca una impresora.
  5. Se genera la etiqueta en el backend ("Generando etiqueta…") y se envía su ZPL ("Enviando a *impresora*…").
  6. Se cierra la hoja y se avisa "Etiqueta enviada a *impresora*".
- **Alternativos:**
  - *Sin Bluetooth:* "Este dispositivo no tiene Bluetooth…".
  - *Bluetooth apagado:* botón **Activar Bluetooth** (diálogo del sistema). Al volver se comprueba el estado real, no la respuesta del diálogo.
  - *Android ≤ 11 con la ubicación apagada:* se muestran las emparejadas con el aviso "Para buscar impresoras nuevas, activa la ubicación".
  - *Ninguna impresora:* "No se ha encontrado ninguna impresora. Comprueba que está encendida, cerca y con el Bluetooth visible", con **Buscar de nuevo**.
  - *Fallo al imprimir:* se vuelve a la lista con el motivo (conexión, envío cortado o Bluetooth no disponible). **Tocar de nuevo la impresora reintenta**; se puede elegir otra.
- **Reglas:**
  - Las impresoras que se anuncian como tales (clase *Imaging*) aparecen **primero**, pero no se descarta el resto porque hay impresoras que no se anuncian.
  - Una impresora encontrada varias veces o ya emparejada solo aparece una vez.
  - **Buscar de nuevo** conserva las ya encontradas.
  - Al elegir una impresora se detiene la búsqueda; mientras se imprime se ignoran otros toques.
  - El ZPL se envía en **UTF-8**: la plantilla debe declarar `^CI28`.
- **Estado:** ✅ (contenido provisional, igual que CU-10)

---

### CU-12 · Tipo de bulto

- **Acceso:** menú `7`.
- **Objetivo:** asignar un tipo al bulto leído y, opcionalmente, mantenerlo para las lecturas siguientes.
- **Flujo principal:**
  1. Se cargan del backend los tipos **activos** con la descripción en el idioma del terminal ("Cargando tipos de bulto…").
  2. Cada fila muestra abreviatura y descripción, y la marca **"exige foto"** cuando corresponde (también en la descripción accesible).
  3. El usuario elige un tipo y, si quiere, marca **"Fijar para las lecturas siguientes"**.
  4. **Asignar** → aviso "Confirmado".
- **Alternativos:**
  - *Error:* "No se han podido cargar los tipos de bulto" + mensaje + **Reintentar**.
  - *Lista vacía:* "No hay tipos de bulto activos. Inténtalo más tarde o contacta con tu administrador" + **Reintentar**.
  - **Cancelar** → aviso "Cancelado".
- **Reglas:** **Asignar** solo se habilita con un tipo elegido de una lista ya cargada. Se descartan los tipos sin código o sin abreviatura. Selección y "fijar" sobreviven al giro.
- **Pendiente:** grabar la asignación, pedir la foto cuando el tipo la exige y aplicar el "fijar" a las lecturas siguientes.
- **Estado:** 🟡

---

### CU-13 · Peso y medidas

- **Acceso:** menú `9`. En el futuro también desde la lectura de un bulto (botón **KG** y checks de peso).
- **Objetivo:** registrar el peso del bulto y, opcionalmente, sus medidas.
- **Campos:** Peso (kg), Alto, Ancho y Profundo (cm), Volumen (m³, **calculado**, no editable).
- **Flujo principal:**
  1. El usuario teclea el peso y, si las tiene, las tres medidas.
  2. El volumen se calcula en vivo cuando las tres medidas están completas: `alto × ancho × profundo / 1.000.000`, redondeado a 2 decimales.
  3. **Aceptar** valida y, si es correcto, avisa "Confirmado".
- **Validaciones:**

| Dato | Regla | Mensaje |
|------|-------|---------|
| Peso | Número > 0 y < 100.000 kg; admite coma o punto; se redondea a **1 decimal** | "El valor del peso debe ser mayor que 0 y menor que 100.000" |
| Medidas | Enteros de 1 a 3 dígitos. **Las tres o ninguna** (se puede grabar solo el peso) | "El valor del volumen no es correcto" |
| Volumen | > 0 y < 10 m³ | "El valor del volumen debe ser mayor a 0 y menor a 10" |

- **Origen de apertura** (decide los datos iniciales y qué pasa al cancelar):

| Origen | Se abre con | Cancelar | Marca expedición completa |
|--------|-------------|----------|---------------------------|
| Botón **KG** (y menú lateral) | Datos actuales del bulto (vacío si no tiene) | La lectura no cambia | No |
| Check **Cubicar peso/volumen** | Vacío | La lectura queda sin peso | No |
| Check **Peso/Volumen anterior** | Datos del bulto anterior (a cero si no hay) | La lectura queda sin peso | No |
| Check **Expedición completa** | Vacío, con aviso de peso obligatorio | **Se anula la lectura** | Sí (`EXPEDICION_COMPLETA = 'S'`) |

  Los cuatro orígenes son **excluyentes** entre sí.
- **Reglas de UX:** los errores desaparecen al corregir el campo; el foco solo se mueve al pulsar Aceptar (al primer campo con error o a la primera medida vacía); los números se muestran con el separador decimal del idioma; las medidas precargadas se muestran con 3 dígitos (`045`).
- **Pendiente:** grabar el resultado y conectarlo con la lectura del bulto.
- **Estado:** 🟡

---

### CU-14 · Volumen (sin peso)

- **Acceso:** menú `10`. Corresponde a la pantalla de lectura de **Recogidas** en modo "Volumen (sin peso)".
- **Objetivo:** cubicar bultos en bucle: leer el CB y sus tres medidas, uno tras otro.
- **Pantalla:**
  - Checks de modo: *Cubicar peso/volumen*, *Peso/Volumen anterior*, *Expedición completa* (deshabilitados por ahora) y **Volumen (sin peso)** (marcado).
  - Campos: CB del bulto, Alto, Ancho, Profundo y Volumen calculado.
  - Acciones rápidas **PS**, **TB**, **KG** e **INC** (aún no disponibles: muestran aviso).
- **Flujo principal:**
  1. Se lee el CB con el escáner o el medidor (o se teclea). Con Intro el foco salta a Alto.
  2. Cada medida llega con sus 3 dígitos; al completarla el foco **avanza solo** a la siguiente.
  3. El volumen se calcula en vivo.
  4. Intro en Profundo o **Aceptar** valida y graba → "Volumen grabado. Lee el siguiente bulto".
  5. Los campos se vacían y el foco vuelve al CB.
- **Alternativos:** **Cancelar** descarta la lectura → "Lectura cancelada. Lee el siguiente bulto".
- **Validaciones:**

| Dato | Regla | Mensaje |
|------|-------|---------|
| CB | Obligatorio | "Lee o teclea el CB del bulto" |
| Medidas | Las tres obligatorias, 1–3 dígitos | "Completa alto, ancho y profundo" |
| Volumen | > 0 y < 10 m³ | "El valor del volumen debe ser mayor a 0 y menor a 10" |

- **Reglas:** con el modo desactivado, Aceptar no graba nada. El Intro de un escáner o teclado físico se procesa una sola vez. El estado del check sobrevive a que el sistema mate el proceso.
- **Pendiente:** grabar la lectura en el backend y habilitar checks y acciones rápidas.
- **Estado:** 🟡

---

### CU-15 · Incidencias

- **Acceso:** menú `11`. En el futuro, también desde la lectura (acción **INC**), con el último bulto leído.
- **Objetivo:** registrar una incidencia sobre el último bulto leído.
- **Pantalla:** operación (p. ej. "Recogidas"), bulto (o "Aún no se ha leído ningún bulto"), lista de incidencias, observaciones y foto.
- **Flujo principal:**
  1. Se cargan las incidencias del **tipo de operación**.
  2. El usuario elige una. La pantalla indica si exige **observaciones** y/o **foto** (con texto e icono, no solo color).
  3. Si exige observaciones, las escribe (máx. 40 caracteres).
  4. Si exige foto, pulsa **Hacer foto** → CU-08. Al volver: "Foto adjuntada" y el botón pasa a **Repetir foto**.
  5. **Aceptar** valida → "Incidencia grabada" y la pantalla queda lista para otra.
- **Alternativos:**
  - *Sin incidencia elegida:* no se puede aceptar ni hacer foto ("Elige una incidencia para hacer la foto").
  - *Cambio de incidencia:* se limpian los errores de la anterior.
  - *Error de carga / lista vacía:* mensaje y **Reintentar**.
  - **Cancelar** → descarta incidencia, observaciones y foto ("Incidencia descartada").
- **Validaciones:**

| Regla | Mensaje |
|-------|---------|
| Observaciones obligatorias si la incidencia lo exige | "Escribe las observaciones que exige esta incidencia" |
| Observaciones ≤ 40 caracteres | "Las observaciones admiten hasta 40 caracteres" |
| Foto obligatoria si la incidencia lo exige | "Esta incidencia exige foto. Pulsa Hacer foto" |

- **Reglas:** las observaciones **solo se graban si la incidencia las exige** (si no, se ignoran). La incidencia elegida y la foto sobreviven a que el sistema mate el proceso mientras la cámara está abierta. Abierta desde el menú, equivale a una recogida sin bulto leído.
- **Catálogo provisional (Recogidas):**

| Código | Exige foto | Exige observaciones |
|--------|:----------:|:-------------------:|
| A16 | — | — |
| A21 | ✔ | — |
| A23 | ✔ | — |
| A40 | — | — |
| A41 | — | — |
| I11 | — | ✔ |

- **Pendiente:** servicio de incidencias del backend, descripciones de cada código y grabación.
- **Estado:** 🟡

---

### CU-16 · Puerta de salida (muelle)

- **Acceso:** menú `12`.
- **Objetivo:** indicar al operario, en grande, el muelle por el que debe salir cada bulto.
- **Flujo principal:**
  1. Estado inicial: "Lee un bulto · Aquí verás el muelle por el que sale". El campo tiene el foco **sin abrir el teclado** (se lee con el escáner).
  2. Se lee el CB → "Buscando el muelle…".
  3. Se muestra el **muelle** en grande (p. ej. `Z14`) y debajo "Plaza ZAR · CP 50237 · España", con una animación breve de llegada.
  4. El muelle queda a la vista hasta la **lectura siguiente**, que lo sustituye.
- **Alternativos:**
  - *Sin muelle asignado:* "Este bulto no tiene muelle asignado · Comprueba la etiqueta y vuelve a leerlo, o avisa a tu responsable".
  - *Error de consulta:* mensaje y **Reintentar** (repite el mismo CB).
  - **Cancelar** → vacía el campo y espera el siguiente ("Lectura cancelada. Lee el siguiente bulto").
- **Reglas:** releer el CB que ya se está consultando no lanza otra consulta; una lectura nueva cancela la anterior para que su respuesta no la sustituya. El CB leído sobrevive a la muerte del proceso.
- **Limitación:** hasta que el backend publique el servicio, **todo bulto devuelve el muelle `Z14` (ZAR, 50237, España)**.
- **Estado:** 🟡

---

## 7. Reglas de negocio consolidadas

| Id | Regla | Ámbito |
|----|-------|--------|
| RN-01 | Usuario y contraseña son obligatorios; el usuario se envía sin espacios alrededor y la contraseña tal cual. | Login |
| RN-02 | La contraseña nunca se guarda en disco, no se muestra y no se escribe en los logs. | Transversal |
| RN-03 | Cada petición autenticada envía usuario, contraseña e idioma (`uk` para inglés). | Transversal |
| RN-04 | "Cambiar de plaza" es visible con el menú `4`, el permiso global `1` o la plaza fija `CTR`. | Perfil |
| RN-05 | Las plazas locales solo se añaden: la sincronización no modifica ni borra. | Plazas |
| RN-06 | Solo se puede cambiar a una plaza distinta de la actual. | Plazas |
| RN-07 | El log se envía como instantánea; tras 2 fallos seguidos se ofrece el correo. | Log |
| RN-08 | Las imágenes se suben al backend **antes** de considerarse adjuntadas. | Fotos |
| RN-09 | La etiqueta se imprime en UTF-8 por Bluetooth SPP; las impresoras que se anuncian como tales van primero. | Impresión |
| RN-10 | Solo se asigna un tipo de bulto activo, elegido de una lista ya cargada. | Tipo de bulto |
| RN-11 | Peso: > 0 y < 100.000 kg, 1 decimal. | Peso |
| RN-12 | Medidas: enteros de 1 a 3 dígitos en cm; las tres o ninguna (peso y medidas). | Peso / Volumen |
| RN-13 | Volumen = alto × ancho × profundo / 1.000.000, redondeo *half-up* a 2 decimales; > 0 y < 10 m³. | Peso / Volumen |
| RN-14 | En modo Volumen (sin peso) CB y las tres medidas son obligatorios. | Volumen |
| RN-15 | Los orígenes de la pantalla de peso son excluyentes; "Expedición completa" obliga al peso y cancelar anula la lectura. | Peso |
| RN-16 | Las incidencias disponibles dependen del tipo de operación. | Incidencias |
| RN-17 | Observaciones: obligatorias si la incidencia lo exige, ≤ 40 caracteres; si no las exige no se graban. | Incidencias |
| RN-18 | La foto es obligatoria si la incidencia lo exige. | Incidencias |
| RN-19 | Una lectura de CB nueva sustituye a la anterior en la puerta de salida. | Muelle |
| RN-20 | Un menú del servidor no registrado en la app se muestra, pero avisa de que no está disponible. | Menú |

---

## 8. Modelo de datos funcional

| Entidad | Atributos | Origen | Persistencia |
|---------|-----------|--------|--------------|
| **Usuario** | usuario, nombre completo, plaza, menú, permisos globales | Login | Memoria (sesión) |
| **Opción de menú** | id, texto, id del padre, permisos | Login | Memoria |
| **Plaza** | código (`plzs_id`), descripción | Backend | SQLite local (`place`), solo altas |
| **Tipo de bulto** | código (`020`), abreviatura (`PL`), descripción traducida, exige foto | Backend | No (se consulta cada vez) |
| **Peso y medidas** | peso kg (1 dec.), alto/ancho/profundo cm, volumen m³ (2 dec.) | Usuario | No (pendiente de grabar) |
| **Lectura de volumen** | CB, medidas, volumen | Usuario / escáner | No (pendiente de grabar) |
| **Incidencia** | código, exige foto, exige observaciones | Provisional en la app | No |
| **Registro de incidencia** | CB (opcional), incidencia, observaciones, foto | Usuario | No (pendiente de grabar) |
| **Muelle** | código, plaza destino, CP, país | Provisional en la app | No |
| **Etiqueta** | ZPL, páginas PNG | Backend | No |
| **Impresora** | nombre, dirección MAC, emparejada, parece impresora | Bluetooth | No |
| **Foto** | fichero JPG en caché o imagen de galería | Cámara / galería | Caché de la app (el sistema puede liberarla) |
| **Log** | ficheros diarios, rotación a 5 MB, 7 días, máx. 50 MB | App | Almacenamiento de la app |

---

## 9. Integraciones externas

### 9.1 Backend ElectraWS

Todas las respuestas usan el sobre `{ status, errorText, errorList[], data }`.

| Operación | Endpoint (`POST`) | Datos enviados | Respuesta | Usado en |
|-----------|-------------------|----------------|-----------|----------|
| Login | `ElectraWS/mobile/login/` | usuario, contraseña, idioma | Usuario con menú y permisos | CU-01 |
| Listado de plazas | `ElectraWS/mobile/user/place/list/` | credenciales, idioma | Lista de plazas | CU-06 |
| Cambio de plaza | `ElectraWS/mobile/user/place/update/` | credenciales, plaza nueva | `true` / error | CU-06 |
| Subida de log | `ElectraWS/mobile/file/upload/log/` | *multipart*: fichero, plaza, credenciales, idioma | `true` / error | CU-07 |
| Subida de imagen | `ElectraWS/mobile/file/upload/img/` | *multipart*: imagen, plaza, credenciales, idioma | `true` / error | CU-08 |
| Etiqueta ZPL | `ElectraWS/mobile/label/zpl/create` | credenciales, idioma, plaza | ZPL + páginas PNG (Base64) | CU-10, CU-11 |
| Tipos de bulto | `ElectraWS/mobile/bulto/internacional/tipo` | credenciales, idioma | Lista de tipos | CU-12 |

**Servicios pendientes del backend:** incidencias por tipo de operación, muelle por CB, grabación de lecturas (tipo, peso/volumen, incidencias), generación de etiqueta por bulto/expedición.

Tiempos máximos: conexión 15 s, lectura 20 s, escritura 20 s.

### 9.2 Impresora Bluetooth

- Perfil SPP (puerto serie), datos ZPL en UTF-8.
- Funciona con impresoras **emparejadas** y con las encontradas en la búsqueda.

### 9.3 Sistema

- **Cámara:** app de cámara del sistema; la foto se escribe en la caché de Electra a través de `FileProvider`.
- **Galería:** selector de fotos del sistema.
- **Correo:** apps que atienden `mailto:` para el envío alternativo del log.
- **Escáner/medidor:** se integra como **teclado** (texto + Intro). No hay SDK de escáner específico.

---

## 10. Catálogo de mensajes y errores

### 10.1 Errores de backend y red

| Código | Mensaje (ES) |
|--------|--------------|
| Sin conexión | Sin conexión. Comprueba tu red e inténtalo de nuevo. |
| Tiempo agotado | El servidor está tardando demasiado en responder. Inténtalo de nuevo. |
| `HTTP_503` | El servicio no está disponible temporalmente. Inténtalo de nuevo en unos minutos. |
| `HTTP_500` y genérico | Algo ha fallado. Inténtalo de nuevo en unos momentos. |
| `ERROR_A01` | Usuario o contraseña incorrectos. Revísalos e inténtalo de nuevo. |
| `ERROR_A06` | Tu usuario no está activo. Contacta con tu administrador. |
| `ERROR_M01` | Tu usuario no tiene menú configurado. Contacta con tu administrador. |
| `ERROR_FILE_01` | El documento no se ha podido subir. |
| `ERROR_T01` | No se ha podido asignar la plaza del camión. Inténtalo de nuevo. |
| `ERROR_C01` | La solicitud del camión no es válida. Revisa los datos e inténtalo de nuevo. |
| `ERROR_C06`, `ERROR_CCB_04_BARCODE_INVALID` | El código de barras no es válido. Vuelve a escanearlo. |
| Código no mapeado | Texto que envía el servidor; si no envía ninguno, el mensaje genérico. |

> El README y el análisis de la fase 1 citan `ERROR_A02` para "usuario no activo"; el código actual usa **`ERROR_A06`**. Conviene confirmar con backend cuál es el correcto.

### 10.2 Errores de impresión

| Causa | Mensaje (ES) |
|-------|--------------|
| No conecta | No se ha podido conectar con la impresora. Comprueba que está encendida, cerca y sin usar por otro dispositivo, y vuelve a tocarla. |
| Conexión cortada | Se ha perdido la conexión con la impresora al enviar la etiqueta. Tócala para intentarlo de nuevo. |
| Bluetooth perdido | El Bluetooth ya no está disponible. Comprueba que está activado y que la app puede acceder a los dispositivos cercanos. |

### 10.3 Principios de redacción

- Todo error dice **qué ha pasado y qué puede hacer** el usuario (reintentar, revisar, contactar).
- No se muestran excepciones internas ni detalles técnicos.
- Los errores de formulario aparecen en el propio campo; su hueco queda reservado para que el formulario no "salte".
- Los textos están en recursos en español e inglés.

---

## 11. Requisitos no funcionales

| Área | Requisito |
|------|-----------|
| **Usabilidad en almacén** | Flujos en bucle (leer → validar → siguiente) con el foco en el siguiente dato esperado; compatibilidad con escáner como teclado; acciones que no se duplican con dobles toques. |
| **Robustez** | El estado de las pantallas con cámara o lectura sobrevive a giros y a que el sistema mate el proceso. Las operaciones en curso se cancelan al salir de la pantalla. |
| **Rendimiento** | Ninguna operación de red, disco o decodificación de imágenes en el hilo principal. Las plazas guardadas se muestran al instante. |
| **Accesibilidad** | Descripciones para lectores de pantalla en indicadores de progreso, páginas de etiqueta, muelle y filas; los estados no se comunican solo con color (texto + icono); tamaños táctiles mínimos; escalado de texto. |
| **Diseño** | Material 3 con tema claro/oscuro y colores corporativos Redur; animaciones cortas (100–300 ms) que acompañan sin bloquear. |
| **Seguridad** | Contraseña solo en memoria; `toString` enmascarado en DTOs con credenciales; trazas HTTP sin cuerpo en los endpoints sensibles y solo en builds de depuración; tráfico en claro solo si la URL del entorno es `http://`. |
| **Trazabilidad** | Log a fichero con rotación diaria y por tamaño, envío al servidor o por correo y vaciado del log ante un cierre inesperado. |
| **Compatibilidad** | Android 10 a 16; permisos adaptados a cada versión; cámara y Bluetooth opcionales. |
| **Internacionalización** | Español e inglés; números con el separador decimal del idioma. |
| **Calidad** | 327 tests unitarios, 0 fallos (última ejecución registrada: 2026-10-07). |

---

## 12. Limitaciones, pendientes y riesgos

### 12.1 Funcionalidad pendiente

| # | Pendiente | Afecta a |
|---|-----------|----------|
| P-01 | Grabar en el backend: tipo de bulto, peso/medidas, lecturas de volumen e incidencias. Hoy solo se confirma con un aviso. | CU-12 a CU-15 |
| P-02 | Servicios reales de incidencias y muelles (catálogo y muelle fijos en la app). | CU-15, CU-16 |
| P-03 | Pantalla de lectura de bultos que integre KG, PS, TB, INC y los checks de peso. | CU-13, CU-14 |
| P-04 | Etiqueta asociada a un bulto o expedición (hoy el backend devuelve una etiqueta fija). | CU-10, CU-11 |
| P-05 | Pedir la foto al asignar un tipo de bulto que la exige y aplicar "fijar para las lecturas siguientes". | CU-12 |
| P-06 | Usar los permisos por menú en las pantallas (llegan, pero ninguna los consulta). | §5.2 |
| P-07 | Carga de camión, consulta de albarán, ajustes y recuperación de contraseña. | F-17 a F-20 |
| P-08 | Cierre de sesión por inactividad (15 min ya definidos). | §5.1 |
| P-09 | URLs reales de PRE y PRO. | §2.3 |
| P-10 | Descripción de cada código de incidencia (hoy solo se muestra el código). | CU-15 |

### 12.2 Riesgos

| # | Riesgo | Impacto | Recomendación |
|---|--------|---------|---------------|
| R-01 | `proguard-rules.pro` está vacío y PRO usa R8. Algunos DTOs dependen del nombre del campo para Gson. | El login y otros servicios podrían fallar **solo en PRO**. | Añadir reglas de R8 y probar una build `pro` real antes de publicar. |
| R-02 | Se registran en el log, a nivel INFO/DEBUG, el usuario completo devuelto por el login y la lista de tipos de bulto. | Datos personales en ficheros que se envían al servidor o por correo. | Registrar solo identificadores. |
| R-03 | La sesión vive en memoria y la pantalla principal no comprueba si se ha perdido. | Tras matar el proceso, el menú aparece vacío y las operaciones fallan con un error genérico. | Comprobar la sesión al crear la pantalla principal y volver al login. |
| R-04 | La contraseña viaja en cada petición (en DEV, por `http://`). | Exposición de credenciales. | Plantear un token de sesión con backend. |
| R-05 | Copias de seguridad activadas con reglas de ejemplo. | La base de datos y los logs pueden acabar en la copia en la nube. | Excluirlos en `backup_rules` y `data_extraction_rules`. |
| R-06 | Para códigos de error no mapeados se muestra el texto del servidor tal cual. | Textos técnicos o en otro idioma ante el usuario. | Mapear los códigos conocidos y acordar textos con backend. |
| R-07 | Reglas de visibilidad con números mágicos (menú `4`, permiso `1`, plaza `CTR`). | Difícil de mantener si cambian en backend. | Centralizar en constantes de dominio documentadas. |
| R-08 | Las pantallas prototipo muestran "Confirmado" / "Volumen grabado" aunque no se graba nada. | Un operario podría creer que el dato quedó registrado. | Mantenerlas bajo "Prototipos" y fuera del menú de producción hasta P-01. |

---

## 13. Anexos

### 13.1 Trazabilidad por rama

| Rama | Funcionalidad |
|------|---------------|
| `feature/01-setup` … `feature/03-log` | Base del proyecto, estilos, logging |
| `feature/04-login` | CU-01, CU-04 |
| `feature/05-toolbar` | Toolbar de la pantalla principal |
| `feature/06-user-profile` | CU-05, CU-06 |
| `feature/07-envio-log` | CU-07 |
| `feature/08-nav-menu` | CU-02, CU-03, catálogo de menús |
| `feature/09-upload-img-camara` | CU-08, permisos del sistema |
| `feature/10-visor-zpl` | CU-09, CU-10, CU-11 |
| `feature/11-layout-tipo-bulto` | CU-12 |
| `feature/12-layout-pesos-medidas` | CU-13 |
| `feature/13-lauyout-volumen` | CU-14 |
| `feature/14-layout-incidencia` | CU-15 |
| `feature/15-layout-puerta-salida` | CU-16 |
| Commits posteriores en `develop` | Mejoras de log, transición menú → pantalla, rediseño del perfil |

### 13.2 Cobertura de tests por funcionalidad

| Funcionalidad | Tests unitarios |
|---------------|-----------------|
| Login / logout / sesión | `LoginRepositoryTest`, `LoginViewModelTest`, `LogoutViewModelTest`, `UserSessionTest`, `UserMapperTest` |
| Menú | `MainViewModelTest` |
| Perfil y log | `ProfileViewModelTest`, `LogRepositoryTest`, `LogUploadRepositoryTest`, `FileLoggingTreeTest` |
| Plazas | `PlaceViewModelTest`, `ChangePlaceRepositoryTest`, `PlaceMapperTest`, `PlaceRequestDTOTest` |
| Fotos y permisos | `PhotoSourceViewModelTest`, `PhotoRepositoryTest`, `ImgUploadRepositoryTest`, `AppPermissionTest`, `PermissionStatusTest`, `UploadFileDTOTest`, `ProgressRequestBodyTest` |
| Etiquetas e impresión | `LabelPreviewViewModelTest`, `LabelRepositoryTest`, `PrintLabelViewModelTest`, `PrinterRepositoryTest` |
| Bultos | `BultoTypeViewModelTest`, `BultoTypeRepositoryTest`, `BultoTypeMapperTest`, `BultoWeightViewModelTest`, `BultoVolumeViewModelTest`, `BultoIncidenceViewModelTest`, `BultoDockViewModelTest` |
| Transversal | `ErrorUiMapperTest`, `UiTextTest`, `SensitiveAwareHttpLoggingInterceptorTest` |

Total: 36 clases, 327 tests. No hay tests instrumentados ni de interfaz.
