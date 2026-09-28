# CLAUDE.md — Electra Android

## 1. Propósito

Actúa como un equipo senior responsable de evolucionar esta aplicación Android de producción.

Prioridades, en este orden:

1. Requisito explícito del usuario.
2. Arquitectura y convenciones ya existentes en el repositorio.
3. Corrección y seguridad.
4. Compatibilidad con Android y ciclo de vida.
5. Mantenibilidad y testabilidad.
6. UX/UI y accesibilidad.
7. Rendimiento.
8. Elegancia del código.

La regla fundamental es:

> **La estructura actual del proyecto es la fuente de verdad.**

No reorganices el proyecto porque otra arquitectura te parezca más limpia.

---

## 2. Stack obligatorio

El proyecto utiliza:

- Java 17.
- Android.
- AndroidX.
- MVVM.
- Hilt.
- ViewBinding.
- XML Views.
- Retrofit para HTTP cuando corresponda.
- Gson cuando corresponda.
- MapStruct cuando ya exista en la zona del proyecto.
- JUnit.
- Mockito.
- Robolectric cuando sea apropiado.

### Prohibido salvo petición explícita

- Kotlin.
- Jetpack Compose.
- Migraciones masivas de arquitectura.
- Nuevos frameworks para resolver problemas triviales.
- Cambios masivos de versiones.
- Refactorizaciones no relacionadas con la tarea.

No crees archivos `.kt`.

---

## 3. Arquitectura existente

Antes de implementar cualquier cosa:

1. Examina el árbol del proyecto.
2. Localiza el paquete/feature relacionado.
3. Estudia al menos 2 implementaciones similares si existen.
4. Identifica cómo se organizan:
   - UI.
   - ViewModel.
   - Repository.
   - API.
   - DTO/modelos.
   - mappers.
   - Hilt.
   - navegación.
   - tests.
5. Sigue la convención encontrada.

Si existe una estructura como:

```text
core/
data/
domain/
ui/
```

no la sustituyas por:

```text
presentation/
application/
infrastructure/
```

solo porque sea una arquitectura que prefieras.

---

## 4. Regla de inspección antes de modificar

Nunca edites directamente una clase que no hayas inspeccionado.

Antes de crear una clase nueva, busca si ya existe algo equivalente.

Antes de crear un componente UI, busca componentes reutilizables.

Antes de añadir una dependencia, comprueba si el proyecto ya resuelve el problema.

Antes de crear un nuevo patrón, comprueba cómo se resuelve en features vecinas.

---

## 5. MVVM

Flujo preferente:

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
Data Source / API / DB
```

La UI debe encargarse principalmente de:

- renderizar estado;
- recoger interacción;
- observar estado;
- navegación;
- responsabilidades específicas del framework Android.

Evita poner en Activity/Fragment:

- llamadas HTTP;
- acceso a base de datos;
- reglas de negocio;
- lógica compleja;
- sincronizaciones;
- autenticación.

El ViewModel no debe guardar referencias a:

- Activity;
- Fragment;
- View;
- RecyclerView;
- TextView;
- Context, salvo necesidad Android justificada.

---

## 6. Hilt

Prioriza constructor injection.

Ejemplo:

```java
@Inject
public BultoRepository(
        BultoApi api,
        BultoMapper mapper
) {
    this.api = api;
    this.mapper = mapper;
}
```

No hagas manualmente:

```java
new BultoRepository(...);
```

si Hilt ya gestiona esa dependencia.

Usa el scope mínimo necesario. No conviertas todo en Singleton por comodidad.

---

## 7. Java 17

Escribe Java moderno, pero no uses características solo para demostrar que existen.

Prioriza:

- clases pequeñas;
- métodos cohesivos;
- `final` cuando mejore claridad;
- APIs inmutables cuando sea razonable;
- nombres explícitos;
- manejo correcto de null;
- excepciones significativas;
- composición sobre herencias innecesarias.

Evita:

- `Utils` genéricos;
- `Helper` genéricos;
- `Manager` genéricos;
- clases gigantes;
- métodos gigantes;
- booleanos cuyo significado no sea evidente;
- strings mágicos;
- números mágicos;
- duplicación;
- abstracciones especulativas.

---

## 8. Recursos Android

Usa recursos para valores reutilizables:

- `strings.xml`;
- `colors.xml`;
- `dimens.xml`;
- themes/styles;
- drawables.

No hardcodees textos de usuario en Java.

No dupliques colores, dimensiones o estilos que ya existan.

---

## 9. UI y UX

Para tareas de UI, el diseño debe pasar por esta secuencia:

```text
Contexto
  ↓
Análisis UX
  ↓
Dirección visual
  ↓
Diseño
  ↓
Implementación XML
  ↓
Revisión visual
  ↓
Accesibilidad
  ↓
Pulido
```

Cuando estén disponibles, usa las skills de Impeccable y Emil Kowalski.

### Impeccable

Úsalo para:

- crítica de interfaz;
- jerarquía;
- composición;
- claridad;
- tipografía;
- color;
- espaciado;
- consistencia;
- estados;
- accesibilidad;
- pulido;
- evitar diseños genéricos.

### Emil Kowalski

Úsalo especialmente para:

- interacción;
- feedback;
- transiciones;
- motion;
- microinteracciones;
- percepción de velocidad;
- estados de componentes.

No copies literalmente patrones web en Android. Adapta los principios al sistema Android.

### Android

La implementación debe respetar:

- XML;
- ViewBinding;
- componentes existentes;
- theme existente;
- navegación existente;
- arquitectura MVVM.

---

## 10. Design system

Antes de inventar colores, tamaños o componentes:

1. inspecciona el theme;
2. inspecciona `colors.xml`;
3. inspecciona `dimens.xml`;
4. inspecciona styles;
5. busca componentes similares;
6. identifica el lenguaje visual existente.

No diseñes cada pantalla como si fuera una aplicación diferente.

---

## 11. Estados de UI

Toda operación asíncrona debe considerar, cuando corresponda:

- inicial;
- loading;
- success;
- empty;
- error;
- retry;
- datos parciales.

Un botón que dispara una operación no debería permitir acciones duplicadas sin una razón.

Los errores deben explicar al usuario qué puede hacer a continuación.

---

## 12. Accesibilidad

Revisa:

- tamaño táctil;
- contraste;
- escalado de texto;
- content descriptions;
- orden de foco;
- lectura por TalkBack;
- comunicación independiente del color;
- labels de formularios.

No uses únicamente color para comunicar estados.

---

## 13. Seguridad

Nunca introduzcas:

- contraseñas hardcodeadas;
- tokens en logs;
- API keys en código;
- secretos en recursos públicos;
- datos sensibles en logs.

No muestres excepciones internas al usuario.

---

## 14. Concurrencia y ciclo de vida

Nunca bloquees el hilo principal con:

- HTTP;
- base de datos;
- operaciones de disco;
- procesamiento pesado.

Respeta el ciclo de vida del Fragment y especialmente el lifecycle de su View.

Evita fugas por callbacks, listeners, adapters o referencias a Views.

---

## 15. Red y datos

Retrofit debe permanecer en la capa correspondiente.

Los DTOs no deben convertirse automáticamente en modelos de UI si el proyecto ya separa ambas representaciones.

Si existe MapStruct, utiliza la convención existente.

No ocultes reglas de negocio dentro de mappers.

No ignores excepciones silenciosamente.

---

## 16. Testing

Cuando modifiques lógica significativa, considera:

- unit tests;
- ViewModel tests;
- repository tests;
- mapper tests;
- Robolectric cuando se necesite Android framework.

Los tests deben comprobar comportamiento, no implementación accidental.

No añadas tests triviales solo para aumentar cobertura.

---

## 17. Rendimiento

El rendimiento debe medirse o razonarse sobre un problema concreto.

Revisa:

- trabajo en main thread;
- operaciones repetidas;
- listas;
- RecyclerView;
- imágenes;
- asignaciones innecesarias;
- consultas repetidas;
- llamadas HTTP duplicadas;
- ciclos de vida;
- fugas;
- memoria.

No hagas micro-optimizaciones sin evidencia.

---

## 18. Cambios mínimos

Haz el cambio mínimo coherente.

No aproveches una tarea para:

- renombrar todo;
- mover paquetes;
- actualizar todas las dependencias;
- reformatear todo el proyecto;
- migrar arquitectura;
- eliminar código no relacionado.

Si detectas una mejora independiente, indícala como nota, pero no la mezcles con la implementación salvo que sea necesaria.

---

## 19. Workflow obligatorio

### Fase A — Explorar

Inspecciona código, estructura, recursos y tests.

### Fase B — Diseñar

Define la solución técnica y, si es UI, la solución UX/UI.

### Fase C — Implementar

Haz cambios pequeños y coherentes.

### Fase D — Validar

Comprueba compilación, tests, imports, lifecycle, arquitectura, UX y accesibilidad.

### Fase E — Revisar

Usa los agentes especializados cuando aporten valor.

---

## 20. Agentes

Dispones de:

- `android-architect`: arquitectura Android, Java 17, MVVM y Hilt.
- `ux-ui-designer`: diseño UX/UI e integración de Impeccable + Emil.
- `code-reviewer`: revisión de calidad, seguridad y mantenibilidad.
- `testing-performance`: testing, regresiones, rendimiento y análisis técnico.

No delegues tareas triviales.

Usa varios agentes cuando existan líneas de trabajo independientes.

---

## 21. Definición de terminado

Una tarea está terminada cuando:

- cumple el requisito;
- respeta el árbol existente;
- usa Java 17;
- no introduce Kotlin;
- respeta MVVM;
- usa Hilt correctamente;
- respeta lifecycle;
- contempla errores;
- contempla estados UI cuando proceda;
- respeta el design system;
- considera accesibilidad;
- tiene tests adecuados;
- no introduce regresiones evidentes;
- no modifica código no relacionado;
- ha pasado revisión de calidad.

---

## 22. Respuesta final

Al terminar una tarea informa brevemente de:

### Cambios
Qué se ha implementado.

### Archivos
Qué se ha creado/modificado.

### Arquitectura
Cómo encaja en el proyecto.

### UX/UI
Solo si aplica.

### Tests
Qué se ha ejecutado o añadido.

### Rendimiento
Solo si aplica.

### Riesgos
Solo los que realmente existan.

No afirmes que has ejecutado un comando que realmente no hayas ejecutado.
