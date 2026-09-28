---
name: electra-ui
description: Diseña y pule interfaces Android de Electra usando los principios de Impeccable y Emil Kowalski, adaptados a XML/ViewBinding y al design system existente.
---

# Electra UI Design Skill

## Propósito

Esta skill conecta las prácticas de diseño de Impeccable y Emil Kowalski con una aplicación Android real.

No pretende reemplazar esas skills externas.

Si están instaladas, utilízalas como referencia principal para la parte de diseño.

## Orden de trabajo

```text
1. Entender el producto
2. Inspeccionar UI existente
3. Definir objetivo del usuario
4. Crear dirección visual
5. Criticar el diseño
6. Implementar
7. Revisar implementación
8. Pulir
```

## Impeccable

Cuando sea apropiado, usa sus conceptos y comandos para:

- auditar;
- criticar;
- pulir;
- mejorar jerarquía;
- revisar color;
- revisar tipografía;
- mejorar composición;
- reducir ruido visual;
- corregir accesibilidad;
- mejorar estados;
- evitar patrones genéricos.

La skill de Impeccable está orientada principalmente a interfaces frontend/web. Por tanto, traduce sus principios a Android en vez de copiar literalmente HTML/CSS.

## Emil Kowalski

Cuando exista interacción o motion, aplica:

- respuesta inmediata;
- animaciones con propósito;
- ease-out para entradas/feedback;
- duración corta;
- evitar animación de acciones frecuentes;
- evitar `transition-all` equivalente;
- atención a los estados de componentes;
- percepción de rendimiento.

En Android esto debe traducirse a APIs de animación apropiadas para el proyecto.

## Design system

No inventes una paleta nueva si existe una.

No inventes componentes si existe uno equivalente.

Primero busca:

- themes;
- styles;
- colors;
- dimens;
- drawables;
- layouts;
- componentes;
- pantallas vecinas.

## Entregable de diseño

Antes de implementar una pantalla importante, genera internamente:

- objetivo;
- usuario;
- jerarquía;
- acción primaria;
- estados;
- interacción;
- accesibilidad;
- dirección visual;
- posibles riesgos.

Después implementa.

## Regla

> Diseño creativo, arquitectura conservadora.

Puedes proponer una interfaz significativamente mejor.

No puedes cambiar MVVM, Java 17, Hilt o la estructura del proyecto para conseguirla.
