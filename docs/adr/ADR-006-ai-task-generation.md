# ADR-006: Generación de Tareas con IA — Diferido hasta completar sdd-explore

## Estado
PROPOSED

## Fecha
2026-06-04

## Fecha de revisión
A definir una vez que la fase sdd-explore finalice y retorne una recomendación.

## Contexto

La Fase 5 del proyecto incluyó un Eje D experimental: *Generación automática de tareas mediante IA*. La idea consiste en permitir que un modelo de lenguaje (LLM) sugiera o genere tareas dentro de un proyecto a partir de una descripción de alto nivel provista por el usuario.

Al momento de planificar la Fase 5, el Eje D fue catalogado como experimental porque:

1. **Alcance indefinido**: no existe una especificación concreta de qué datos se enviarían al LLM, qué formato tendría la respuesta, ni cómo se integraría con el modelo de dominio existente de `Task`.
2. **Costos de API impredecibles**: el volumen de llamadas a la API de un proveedor LLM (OpenAI, Anthropic, etc.) depende de patrones de uso todavía desconocidos. Sin un modelo de pricing validado, incluir esta funcionalidad en producción representa un riesgo financiero no cuantificado.
3. **Límites de tasa (rate limits)**: los proveedores LLM imponen rate limits que pueden afectar la disponibilidad del sistema. La estrategia de manejo de errores, reintentos y degradación graceful requiere diseño explícito.
4. **Complejidad de integración**: la integración requiere decisiones sobre cliente HTTP, manejo de contexto de autenticación, estrategia de streaming vs. respuesta completa, y almacenamiento de resultados intermedios.
5. **Privacidad y seguridad**: los datos de tareas enviados a un proveedor externo pueden contener información sensible del negocio. Se necesita una política de datos clara antes de proceder.

Dado que ninguno de estos puntos fue explorado durante la Fase 5, comprometerse a un diseño e implementación sin pasar por un `sdd-explore` significaría incurrir en deuda técnica desde el primer commit.

## Alternativas consideradas

| Alternativa | Pros | Contras |
|-------------|------|---------|
| **Diferir hasta sdd-explore** (decisión adoptada) | Sin deuda de diseño. Sin riesgo de costo/rate-limit en producción. Permite explorar proveedores, modelos y patrones de integración antes de comprometerse. | La funcionalidad no estará disponible en Fase 5. |
| Implementar con proveedor X (OpenAI/Anthropic) en Fase 5 | Funcionalidad disponible más rápido. | Alcance no definido. Costos no modelados. Sin evaluación de seguridad/privacidad. Alta probabilidad de reescritura posterior. |
| Implementar como mock/stub (sin LLM real) | Permite prototipar la UI y el flujo. | Crea falsa expectativa. Mock sin exploración previa puede divergir del diseño final. |

## Decisión

**La funcionalidad de generación de tareas con IA queda DIFERIDA.** No se implementará ningún código de producción para el Eje D hasta que:

1. Se ejecute una sesión completa de `sdd-explore` para el Eje D.
2. La exploración retorne una recomendación aprobada que cubra:
   - Proveedor LLM seleccionado con modelo de costos validado.
   - Estrategia de manejo de rate limits y fallback.
   - Política de privacidad y sanitización de datos de tareas.
   - Diseño del contrato de integración (puerto en la arquitectura hexagonal).
3. El resultado de la exploración sea revisado y aprobado por el equipo antes de iniciar `sdd-design`.

## Condición de desbloqueo (gate)

```
sdd-explore(eje-d-ai-task-generation) → recomendación aprobada → sdd-design → sdd-tasks → sdd-apply
```

Hasta que este gate se complete, **no se abrirán PRs de implementación** para el Eje D.

## Consecuencias

**Positivas:**
- Fase 5 se entrega sin deuda de diseño en el Eje D.
- El equipo puede dedicar el tiempo de exploración sin presión de entrega.
- Las decisiones de arquitectura (qué puerto exponer, cómo modelar la respuesta del LLM en el dominio) se tomarán con información suficiente.

**Negativas:**
- La funcionalidad de IA no estará disponible hasta una fase posterior.
- Riesgo de que la exploración identifique bloqueos (costo, privacidad) que posterguen la implementación indefinidamente.

**Mitigaciones:**
- Fijar una fecha límite para la sesión de `sdd-explore`: no más de 2 sprints después de completar la Fase 5.
- Si la exploración identifica bloqueos estructurales, documentarlos en un ADR de cierre y descartar el Eje D de forma explícita (en lugar de dejarlo en limbo).

## Referencias

- Tasks Phase 5 — Engram observation id: 655 (Eje D sección)
- Apply progress Phase 5 — Engram observation id: 656
- ADR-002 Arquitectura Hexagonal (`docs/adr/ADR-002-arquitectura-hexagonal.md`) — el puerto de IA debe seguir el patrón de puertos definido aquí
