---
name: electra-performance
description: Analiza rendimiento, memoria, concurrencia y estrategia de testing de una aplicación Android Java 17.
---

# Electra Performance & Testing

## Objetivo

Encontrar regresiones reales y riesgos de rendimiento sin hacer micro-optimizaciones especulativas.

## Testing

Para cada cambio relevante pregunta:

- ¿Qué comportamiento puede romperse?
- ¿Qué caso feliz existe?
- ¿Qué error puede producirse?
- ¿Qué pasa con datos vacíos?
- ¿Qué ocurre durante lifecycle changes?
- ¿Qué pasa si el usuario repite la acción?

## Rendimiento

Revisa:

### Main thread

No debe contener:

- HTTP;
- DB;
- I/O;
- parsing pesado;
- procesamiento intensivo.

### Red

Busca:

- llamadas duplicadas;
- llamadas innecesarias;
- respuestas excesivamente grandes;
- reintentos incorrectos.

### UI

Busca:

- RecyclerView ineficiente;
- layouts innecesariamente profundos;
- trabajo pesado durante binding;
- cargas repetidas.

### Memoria

Busca:

- referencias largas a Activity/Fragment/View;
- listeners no liberados;
- callbacks;
- observers;
- caches sin límite;
- objetos grandes retenidos.

## Evidencia

Clasifica cada hallazgo como:

- Confirmado.
- Riesgo.
- Hipótesis.

No afirmes que existe un problema de rendimiento si no existe evidencia suficiente.

## Tests

Prioriza tests de comportamiento sobre cobertura artificial.

Cuando sea apropiado:

- JUnit;
- Mockito;
- Robolectric.

## Resultado

Devuelve:

1. riesgos críticos;
2. tests necesarios;
3. problemas de rendimiento;
4. mediciones existentes;
5. recomendaciones.
