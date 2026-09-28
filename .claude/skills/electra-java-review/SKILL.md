---
name: electra-java-review
description: Revisa código Java 17 Android con especial atención a claridad, arquitectura, nullabilidad, excepciones y mantenibilidad.
---

# Electra Java Review

## Objetivo

Producir Java de producción claro y mantenible.

## Revisión

Comprueba:

- responsabilidad única razonable;
- métodos cohesivos;
- nombres;
- nullabilidad;
- excepciones;
- duplicación;
- complejidad;
- mutabilidad;
- acoplamiento;
- dependencias;
- logging.

## Java 17

Utiliza características modernas solo cuando mejoren claridad.

No conviertas el código en una demostración de Java 17.

## Android

Revisa:

- lifecycle;
- Context;
- Views;
- threading;
- callbacks;
- observers;
- memoria.

## Hilt

Comprueba:

- constructor injection;
- scopes;
- modules;
- dependencias manuales innecesarias.

## Regla

El código más corto no es necesariamente el mejor.

Prioriza código que otro desarrollador pueda entender y modificar con seguridad.
