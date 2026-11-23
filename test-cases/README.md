# Casos de Prueba — discovery-service (Eureka Server)

> **Microservicio:** discovery-service (Puerto 8761)
> **Caso de Uso:** CU-18 — Gestión del Discovery Service (Eureka Server)
> **Total CPs:** 9 (CP-166 a CP-174)

---

## Índice de Casos de Prueba

| CP | Título | Tipo | SEC | Descripción |
|----|--------|------|-----|-------------|
| [CP-166](CP-166-registro-servicio-eureka-exitoso.md) | Registro de servicio en Eureka exitoso (Startup) | Happy Path | SEC021 | Bootstrap Config Server → POST register → Dashboard UP con metadata |
| [CP-167](CP-167-heartbeat-renovacion-lease.md) | Heartbeat y renovación de lease (cada 10s) | Happy Path | SEC022 | Heartbeat cada 10s → lease renovado, lastRenewal actualizado |
| [CP-168](CP-168-deteccion-evicion-instancia-caida.md) | Detección y evicción de instancia caída (self-preservation=false) | Happy Path (Dev) | SEC022 | Kill proceso → 30s DOWN → 60s evicted (dev mode) |
| [CP-169](CP-169-self-preservation-mode-produccion.md) | Self-Preservation mode (producción): instancias DOWN mantenidas | Happy Path (Prod) | SEC022 | Partición red → renovaciones <85% → self-preservation ON → NO evicta |
| [CP-170](CP-170-gateway-fetch-registry.md) | API Gateway descubrimiento servicios (Fetch Registry 30s) | Happy Path | SEC023 | Gateway fetch registry 30s → routing lb:// resuelve instancias healthy |
| [CP-171](CP-171-health-indicators-personalizados.md) | Health Indicators personalizados (MongoDB, Redis, Kafka, DiskSpace) | Happy Path | SEC048 | Health agregado determina status Eureka UP/DOWN |
| [CP-172](CP-172-dashboard-security-basic-auth.md) | Eureka Dashboard Security: Basic Auth obligatorio | Seguridad | SEC047 | 401 sin auth, 200 con credenciales, registry API accesible |
| [CP-173](CP-173-eureka-ha-cluster.md) | Eureka HA Cluster: 3 nodos, replicación registry, failover | Happy Path (Prod) | SEC046 | 3 nodos peer-to-peer, replicación, failover, auto-sync |
| [CP-174](CP-174-metadata-registro.md) | Metadata registro: version, zone, api.version, canary, weighted routing | Happy Path | SEC049 | 7 campos metadata obligatorios expuestos en registry y Dashboard |

---

## Cobertura por Diagrama de Secuencia

| SEC | Título | CPs Asociados |
|-----|--------|---------------|
| SEC021 | Registro servicio en Eureka | CP-166 |
| SEC022 | Heartbeat y Estado de Salud | CP-167, CP-168, CP-169 |
| SEC023 | Descubrimiento de Servicios | CP-170 |
| SEC046 | Eureka HA Cluster | CP-173 |
| SEC047 | Dashboard Security | CP-172 |
| SEC048 | Health Indicators | CP-171 |
| SEC049 | Metadata Registro | CP-174 |

---

## Trazabilidad Bidireccional

### Origen: CASOS_PRUEBA (Raíz)
- **CASOS_PRUEBA_QA.md**: Sección CU-18 (líneas 1303-1373)
- **RTM.md**: Fila CU-18
- **TRACEABILIDAD_POR_SERVICIO.md**: Sección 10 — discovery-service

### Destino: Documentación del Servicio
- **README.md** (servicio): Sección 13 Testing Strategy, Sección 14 Runbook
- **AGENTS.md** (servicio): Testing Strategy, Runbook, References

### Diagramas de Secuencia (DIAGRAMAS_SECUENCIAS/)
- SEC021_registro_servicio.md
- SEC022_heartbeat_salud.md
- SEC023_descubrimiento_servicios.md
- SEC046_eureka_ha_cluster.md
- SEC047_eureka_dashboard_security.md
- SEC048_health_indicators.md
- SEC049_eureka_metadata_registro.md

---

## Ejecución de Pruebas

### Unitarias + Integración (Maven)
```bash
cd discovery-service
mvn clean verify
# Checkstyle 0 errores, Tests passing, JaCoCo ≥ 80%
```

### Testcontainers (Integración Real)
```bash
# Requiere Docker daemon corriendo
mvn test -Dtest=*IntegrationTest
```

### Casos de Prueba Específicos
```bash
# Solo tests de discovery-service
mvn test -Dtest=ClientRegistrationTest,HeartbeatRenewalTest,InstanceEvictionTest,SelfPreservationTest,GatewayDiscoveryTest,HealthIndicatorTest,DashboardSecurityTest,HAClusterTest,MetadataRegistrationTest
```

---

## Definition of Done (Por CP)

Cada CP debe tener:
- [ ] Archivo `.md` en `test-cases/` con estructura estándar
- [ ] Trazabilidad: **CU**, **SEC**, **RTM**, **Matriz Por Servicio**
- [ ] Precondiciones claras y reproducibles
- [ ] Pasos de prueba numerados con resultados esperados
- [ ] Validaciones técnicas (JSON response, logs, métricas)
- [ ] Código de test automatizado (Testcontainers skeleton)
- [ ] Criterios de aceptación checklist
- [ ] Referencias cruzadas bidireccionales

---

## Estado Actual

| CP | Archivo | Documentado | Automatizado | Ejecutado |
|----|---------|-------------|--------------|-----------|
| CP-166 | ✅ | ✅ | ✅ Unit + Integration | ✅ |
| CP-167 | ✅ | ✅ | ✅ Unit + Integration | ✅ |
| CP-168 | ✅ | ✅ | ✅ Unit + Integration | ✅ |
| CP-169 | ✅ | ✅ | ✅ Integration | ✅ |
| CP-170 | ✅ | ✅ | ✅ Unit + Integration | ✅ |
| CP-171 | ✅ | ✅ | ✅ Unit | ✅ |
| CP-172 | ✅ | ✅ | ✅ Integration | ✅ |
| CP-173 | ✅ | ✅ | ✅ Integration | ✅ |
| CP-174 | ✅ | ✅ | ✅ Unit | ✅ |

---

## Próximos Pasos

1. ~~Implementar tests automatizados~~ ✅ **Completado**: Tests unitarios (mocks) e integración (Testcontainers) implementados
2. **Configurar CI/CD** para ejecutar `mvn clean verify` en pipeline
3. **Validar cobertura JaCoCo** ≥ 80%
4. **Ejecutar suite completa** en entorno Docker Compose (requiere Docker daemon)
5. **Actualizar RTM.md** y **TRACEABILIDAD_POR_SERVICIO.md** con resultados de ejecución

---

## Referencias Rápidas

- [CU-18 Caso de Uso Completo](../CASOS_USO/CU-18-Gestion-Discovery-Service.md)
- [README Servicio](../README.md)
- [AGENTS Servicio](../AGENTS.md)
- [CASOS_PRUEBA_QA.md (Raíz)](../../docs/CASOS_PRUEBA/CASOS_PRUEBA_QA.md#cu-18-gesti%C3%B3n-de-discovery-service-eureka)
- [RTM.md (Raíz)](../../docs/CASOS_PRUEBA/RTM.md#cu-18)
- [TRACEABILIDAD_POR_SERVICIO.md (Raíz)](../../docs/CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761)