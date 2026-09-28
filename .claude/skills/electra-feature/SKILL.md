---
name: electra-feature
description: Implementa features Android siguiendo la estructura existente de Electra y el patrón de sus features vecinas.
---

# Electra Feature Skill

## Objetivo

Crear features nuevas sin introducir una arquitectura paralela.

## Fase 1 — Descubrimiento

Busca primero:

- features similares;
- Fragments;
- ViewModels;
- repositories;
- APIs;
- DTOs;
- mappers;
- módulos Hilt;
- navegación;
- layouts;
- tests.

Estudia al menos dos ejemplos cuando existan.

## Fase 2 — Mapa

Determina:

```text
UI
↓
ViewModel
↓
Repository
↓
Data source
```

y localiza cada pieza dentro del árbol existente.

## Fase 3 — Implementación

Respeta:

- Java 17;
- MVVM;
- Hilt;
- ViewBinding;
- XML;
- convenciones de nombres;
- navegación existente.

## Fase 4 — Estados

Considera:

- loading;
- success;
- empty;
- error;
- retry;
- disabled;
- duplicación de acciones.

## Fase 5 — Validación

Comprueba:

- compilación;
- tests;
- lifecycle;
- null;
- Hilt;
- recursos;
- accesibilidad;
- consistencia visual.

## Prohibido

No introduzcas automáticamente:

- UseCase;
- Interactor;
- Clean Architecture;
- Compose;
- Kotlin;
- nuevas librerías.

Solo si el proyecto ya las utiliza o el usuario las solicita.
