# CP-169 — Self-Preservation mode (producción): instancias DOWN mantenidas

> **CU:** CU-18 | **SEC:** SEC022_heartbeat_salud.md | **Servicio:** discovery-service (Eureka Server)

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-169 |
| **Tipo** | Happy Path (Producción) |
| **Prioridad** | Alta |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Integration |

---

## Descripción

Validar que en producción (con `enable-self-preservation=true`), cuando hay una partición de red o fallo masivo que hace que las renovaciones caigan por debajo del 85% (`renewal-percent-threshold: 0.85`), Eureka entra en **modo self-preservation**: **NO evicta** instancias DOWN, mantiene el registry intacto y loggea WARNING, protegiendo contra evicción masiva por fallos de red temporales.

---

## Precondiciones

1. **Eureka Server** UP con configuración producción:
   ```yaml
   eureka:
     server:
       enable-self-preservation: true
       renewal-percent-threshold: 0.85
       eviction-interval-timer-in-ms: 60000
   ```
2. Múltiples instancias registradas (mínimo 3-4 para probar umbral 85%)
3. Heartbeats normales funcionando

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Verificar Dashboard: todas las instancias UP, self-preservation **OFF** | Status UP, dashboard muestra "THE SELF PRESERVATION MODE IS TURNED OFF" |
| 2 | Simular partición de red: bloquear heartbeats de >15% de instancias (ej. 2 de 10 instancias dejan de enviar heartbeats) | - |
| 3 | Esperar 30s (lease-expiration) para que esas instancias marquen DOWN | Instancias afectadas → DOWN |
| 4 | Verificar Dashboard: self-preservation **ON** (WARNING visible) | "THE SELF PRESERVATION MODE IS TURNED ON" + contador instancias DOWN |
| 5 | Verificar registry: instancias DOWN **NO evictadas**, siguen en registry | `GET /eureka/apps/{SERVICE_ID}` muestra instancias con status DOWN |
| 6 | Verificar logs Eureka: WARNING de self-preservation activado | Log: "Self-preservation mode: retaining instance..." |
| 7 | Restaurar conectividad: instancias reanudan heartbeats | Self-preservation se desactiva automáticamente al recuperar >85% renovaciones |

---

## Flujo de Self-Preservation

```
Normal Operation (renovaciones > 85%)
    │
    ▼ (partición red / fallo masivo: renovaciones < 85%)
SELF-PRESERVATION ACTIVADO
    │
    ├── Instancias sin heartbeat → DOWN (NO evictadas)
    ├── Registry mantenido intacto
    ├── Dashboard: WARNING self-preservation ON
    ├── Logs: "Retaining instance..." cada eviction-interval
    │
    ▼ (conectividad restaurada: renovaciones > 85%)
SELF-PRESERVATION DESACTIVADO
    │
    ▼ (instancias DOWN sin heartbeat siguen siendo evictadas normalmente)
Operación normal restaurada
```

---

## Validaciones Técnicas

### Dashboard - Self-Preservation ON
```
THE SELF PRESERVATION MODE IS TURNED ON. THIS MAY NOT PROTECT INSTANCE EXPIRY IN CASE OF NETWORK PARTITION.
```

### Registry - Instancias DOWN retenidas
```json
{
  "application": {
    "name": "ACCOUNTS-SERVICE",
    "instance": [
      {
        "instanceId": "accounts-service-1:...",
        "status": "UP",
        ...
      },
      {
        "instanceId": "accounts-service-2:...",
        "status": "DOWN",  // Mantenida en registry
        "leaseInfo": {
          "evictionTimestamp": 0  // No hay evicción programada
        }
      }
    ]
  }
}
```

### Actuator Eureka Endpoint
```bash
curl -u admin:password http://discovery-service:8761/actuator/eureka
```
```json
{
  "selfPreservationMode": true,
  "renewalPercentThreshold": 0.85,
  "renewalThreshold": 8.5,
  "lastMinRenewals": 7  # < threshold → self-preservation ON
}
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void selfPreservationModeMantieneInstanciasDownEnProduccion() {
    // Given: Eureka con self-preservation=true, 10 instancias UP
    // When: Simular partición (detener 2 instancias = 20% > 15% threshold)
    // Then:
    // 1. A los 30s: 2 instancias DOWN en registry
    // 2. Verificar actuator/eureka: selfPreservationMode = true
    // 3. Verificar NO evicción tras eviction-interval (60s)
    // 4. Logs contienen "Self-preservation mode: retaining instance..."
    // 5. Restaurar instancias → selfPreservationMode = false
}
```

---

## Criterios de Aceptación

- [ ] Self-preservation se activa cuando renovaciones < 85% (`renewal-percent-threshold`)
- [ ] Instancias DOWN **NO son evictadas** mientras self-preservation esté activo
- [ ] Dashboard muestra WARNING "SELF PRESERVATION MODE IS TURNED ON"
- [ ] Logs Eureka muestran "Retaining instance..." cada eviction-interval
- [ ] Actuator endpoint `/actuator/eureka` reporta `selfPreservationMode: true`
- [ ] Al recuperar >85% renovaciones, self-preservation se desactiva automáticamente
- [ ] Instancias DOWN que no recuperan heartbeat son evictadas tras desactivación

---

## Comparación CP-168 vs CP-169

| Aspecto | CP-168 (Dev) | CP-169 (Prod) |
|---------|-------------|---------------|
| `enable-self-preservation` | `false` | `true` |
| Instancias DOWN | Evictadas a los 60s | **Mantenidas** indefinidamente |
| Dashboard | Sin warning | **WARNING visible** |
| Logs | "Evicting instance..." | **"Retaining instance..."** |
| Gateway | Deja de resolver | Sigue resolviendo (riesgo) |

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) |
| **Diagrama Secuencia** | [SEC022_heartbeat_salud.md](../DIAGRAMAS_SECUENCIAS/SEC022_heartbeat_salud.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |