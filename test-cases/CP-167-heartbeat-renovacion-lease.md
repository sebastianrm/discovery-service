# CP-167 — Heartbeat y renovación de lease (cada 10s)

> **CU:** CU-18 | **SEC:** SEC022_heartbeat_salud.md | **Servicio:** discovery-service (Eureka Server)

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-167 |
| **Tipo** | Happy Path |
| **Prioridad** | Alta |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Unit + Integration |

---

## Descripción

Validar que el cliente Eureka envía heartbeats cada 10 segundos (configurado en `lease-renewal-interval-in-seconds: 10`) y que el Eureka Server renueva correctamente el lease, actualizando el timestamp `lastRenewal` en el registry y Dashboard.

---

## Precondiciones

1. **Eureka Server** UP en puerto 8761
2. **Microservicio cliente** ya registrado (ver CP-166)
3. Configuración cliente: `lease-renewal-interval-in-seconds: 10`, `lease-expiration-duration-in-seconds: 30`

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Monitorear endpoint `/eureka/apps/{SERVICE_ID}/{INSTANCE_ID}` en Dashboard | Instancia visible con `lastRenewal` inicial |
| 2 | Esperar 10s | Cliente envía `PUT /eureka/apps/{SERVICE_ID}/{INSTANCE_ID}` (heartbeat) |
| 3 | Verificar respuesta: `200 OK` | Lease renovado |
| 4 | Verificar Dashboard: `lastRenewal` actualizado a timestamp actual | Timestamp refrescado |
| 5 | Repetir por 3 ciclos (30s) | 3 heartbeats exitosos, lease activo |
| 6 | Verificar `GET /eureka/apps/{SERVICE_ID}` | Instancia sigue UP, `leaseInfo.durationRegisteredInSecs` incrementando |

---

## Postcondiciones

- Heartbeat cada 10s funcionando correctamente
- Lease renovado en cada ciclo
- Dashboard muestra `lastRenewal` actualizado
- Instancia mantiene status UP

---

## Validaciones Técnicas

### Cliente Eureka (logs esperados)
```
DiscoveryClient_.../... - registration status: 204
DiscoveryClient_.../... - heartbeat status: 200
DiscoveryClient_.../... - heartbeat status: 200
```

### Servidor Eureka (registry)
```json
{
  "instance": {
    "instanceId": "accounts-service:172.20.0.5:8083",
    "status": "UP",
    "leaseInfo": {
      "renewalIntervalInSecs": 10,
      "durationRegisteredInSecs": 45,
      "lastRenewal": 1731590400000,
      "lastRegistration": 1731590355000,
      "evictionTimestamp": 0
    }
  }
}
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void heartbeatCada10sRenuevaLeaseCorrectamente() {
    // Given: Servicio registrado (CP-166)
    // When: Esperar 35 segundos (3+ ciclos de heartbeat)
    // Then: 
    // - Verificar 3+ PUT /eureka/apps/ACCOUNTS-SERVICE/{instanceId} en logs Eureka
    // - Verificar leaseInfo.lastRenewal actualizado cada ~10s
    // - Verificar instancia sigue UP
    // - Verificar leaseInfo.durationRegisteredInSecs > 30
}
```

---

## Criterios de Aceptación

- [ ] Cliente envía PUT heartbeat cada 10s ± 1s
- [ ] Servidor responde `200 OK` a cada heartbeat
- [ ] `lastRenewal` actualizado en registry tras cada heartbeat
- [ ] Instancia mantiene status UP durante toda la prueba
- [ ] `leaseInfo.durationRegisteredInSecs` incrementa correctamente

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