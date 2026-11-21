# CP-168 — Detección y evicción de instancia caída (self-preservation=false)

> **CU:** CU-18 | **SEC:** SEC022_heartbeat_salud.md | **Servicio:** discovery-service (Eureka Server)

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-168 |
| **Tipo** | Happy Path (Dev/Test) |
| **Prioridad** | Alta |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Unit + Integration |

---

## Descripción

Validar que en entorno de desarrollo/test (con `self-preservation=false`), cuando una instancia deja de enviar heartbeats, Eureka la marca **DOWN** tras la expiración del lease (30s por defecto: 3 heartbeats fallados × 10s) y la **evicta** del registry tras el `eviction-interval-timer-in-ms` (30s en dev).

---

## Precondiciones

1. **Eureka Server** UP con configuración dev:
   ```yaml
   eureka:
     server:
       enable-self-preservation: false
       eviction-interval-timer-in-ms: 30000
   ```
2. **Microservicio cliente** registrado y UP (ver CP-166, CP-167)
3. Configuración cliente: `lease-expiration-duration-in-seconds: 30`

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Verificar instancia UP en Dashboard `/eureka/apps/{SERVICE_ID}` | Status UP, heartbeats cada 10s |
| 2 | Detener microservicio cliente (kill -9 / docker stop) | Proceso terminado, sin shutdown graceful |
| 3 | Esperar 30s (3 intervalos de heartbeat fallados = lease-expiration) | - |
| 4 | Verificar Dashboard: instancia marcada **DOWN** | Status DOWN visible en `/eureka/apps/{SERVICE_ID}` |
| 5 | Esperar 30s adicionales (`eviction-interval-timer-in-ms: 30000`) | - |
| 6 | Verificar evicción: instancia **eliminada** del registry | `GET /eureka/apps/{SERVICE_ID}` → array vacío o sin la instancia |
| 7 | Verificar Gateway: próximo fetch registry (30s) actualiza routing table | Gateway ya no resuelve `lb://{service-name}` a esa instancia |

---

## Flujo de Estados

```
UP (heartbeats OK)
    │
    ▼ (kill proceso, 0 heartbeats)
UP (esperando heartbeats...)
    │
    ▼ (30s = 3 × 10s sin heartbeat → lease expirado)
DOWN (marcado en registry)
    │
    ▼ (30s = eviction-interval-timer-in-ms)
EVICTED (eliminado del registry)
    │
    ▼ (próximo fetch registry 30s)
Gateway actualiza routing table
```

---

## Validaciones Técnicas

### Registry tras expiración lease (DOWN)
```json
{
  "instance": {
    "instanceId": "accounts-service:172.20.0.5:8083",
    "status": "DOWN",
    "leaseInfo": {
      "renewalIntervalInSecs": 10,
      "durationRegisteredInSecs": 120,
      "lastRenewal": 1731590400000,
      "evictionTimestamp": 1731590430000
    }
  }
}
```

### Registry tras evicción (instancia eliminada)
```json
{
  "application": {
    "name": "ACCOUNTS-SERVICE",
    "instance": []  // Array vacío - instancia evicta
  }
}
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void instanciaCaidaEsMarcadaDownYEvictadaEnDev() {
    // Given: Servicio registrado y UP (self-preservation=false)
    // When: Detener cliente (kill)
    // Then: 
    // 1. A los 30-35s: instancia status DOWN en /eureka/apps/ACCOUNTS-SERVICE
    // 2. A los 60-65s: instancia eliminada del registry (array vacío)
    // 3. Verificar log Eureka: "Evicting instance accounts-service:..."
}
```

---

## Criterios de Aceptación

- [ ] Instancia marcada DOWN a los ~30s tras último heartbeat (lease-expiration)
- [ ] Instancia evicta (eliminada) del registry a los ~60s (lease-expiration + eviction-interval)
- [ ] Dashboard muestra transición UP → DOWN → (desaparece)
- [ ] Gateway actualiza routing table en próximo fetch (30s)
- [ ] Logs Eureka muestran evicción: "Evicting instance..."

---

## Diferencia con CP-169 (Self-Preservation)

| Aspecto | CP-168 (Dev: self-preservation=false) | CP-169 (Prod: self-preservation=true) |
|---------|--------------------------------------|--------------------------------------|
| **DOWN** | Sí, a los 30s | Sí, a los 30s |
| **EVICTED** | Sí, a los 60s | **NO** - se mantiene en registry |
| **Log** | "Evicting instance..." | "Self-preservation mode: retaining instance..." |
| **Gateway** | Deja de resolver la instancia | Sigue resolviendo (puede enviar a DOWN) |

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