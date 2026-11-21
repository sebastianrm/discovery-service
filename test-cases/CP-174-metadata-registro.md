# CP-174 — Metadata registro: version, zone, api.version, canary, weighted routing

> **CU:** CU-18 | **SEC:** SEC049_eureka_metadata_registro.md | **Servicio:** discovery-service (Eureka Server) + **Todos los microservicios cliente**

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-174 |
| **Tipo** | Happy Path |
| **Prioridad** | Media |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Unit |

---

## Descripción

Validar que los microservicios cliente Eureka registran **metadata rica** en su InstanceInfo (`eureka.instance.metadata-map`) y que el Eureka Server la almacena, expone vía `/eureka/apps/{serviceId}` y el Dashboard la muestra, permitiendo **routing avanzado**, **canary deployments**, **zone affinity** y **observabilidad**.

---

## Precondiciones

1. **Eureka Server** UP
2. **Microservicio cliente** con metadata-map configurado:
   ```yaml
   eureka:
     instance:
       metadata-map:
         version: ${project.version:1.0.0}
         zone: primary
         management.port: ${management.server.port:${server.port}}
         api.version: v1
         deployment.environment: ${SPRING_PROFILES_ACTIVE:docker}
         canary: "false"
         weight: "100"
   ```
3. Cliente registrado y UP (ver CP-166)

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Verificar payload registro POST `/eureka/apps/{SERVICE_ID}` | Metadata-map incluido en InstanceInfo |
| 2 | Consultar registry: `GET /eureka/apps/{SERVICE_ID}/{INSTANCE_ID}` | Metadata completa en respuesta JSON/XML |
| 3 | Verificar Dashboard Eureka: columna "Metadata" muestra pares clave-valor | version=1.0.0, zone=primary, api.version=v1, canary=false, weight=100, deployment.environment=docker, management.port=8083 |
| 4 | Verificar Actuator Info endpoint del cliente: `GET /actuator/info` | Metadata consistente con registry |
| 5 | Simular canary deployment: desplegar v1.1.0 con `canary: "true", weight: "10"` | Nueva instancia con metadata canary=true, weight=10 |
| 6 | Verificar Gateway/Consumidor puede filtrar por metadata | Routing puede usar zone, version, canary para decisiones |

---

## Metadata Obligatoria (Estándar Yanki)

| Clave | Descripción | Ejemplo | Uso |
|-------|-------------|---------|-----|
| `version` | Versión semántica del servicio | `1.0.0`, `1.1.0-SNAPSHOT` | Observabilidad, debugging |
| `zone` | Zona de disponibilidad | `primary`, `zone1`, `zone2` | **Zone affinity** (routing prefer same zone) |
| `api.version` | Versión de API expuesta | `v1`, `v2` | Versionado API, compatibilidad |
| `deployment.environment` | Entorno de despliegue | `docker`, `k8s`, `local` | Observabilidad |
| `canary` | Instancia canary | `"true"`, `"false"` | **Canary deployments** (routing 10% tráfico) |
| `weight` | Peso para weighted routing | `"100"`, `"10"` | **Weighted load balancing** |
| `management.port` | Puerto Actuator | `8083`, `9090` | Health checks directos, scraping Prometheus |

---

## Validaciones Técnicas

### Registry Response (JSON)
```json
{
  "instance": {
    "instanceId": "accounts-service:172.20.0.5:8083",
    "hostName": "172.20.0.5",
    "app": "ACCOUNTS-SERVICE",
    "ipAddr": "172.20.0.5",
    "status": "UP",
    "port": {"$": 8083, "@enabled": "true"},
    "securePort": {"$": 8443, "@enabled": "false"},
    "metadata": {
      "version": "1.0.0",
      "zone": "primary",
      "management.port": "8083",
      "api.version": "v1",
      "deployment.environment": "docker",
      "canary": "false",
      "weight": "100"
    },
    "leaseInfo": {...}
  }
}
```

### Dashboard - Columna Metadata
```
Instance ID                          | Status | Metadata
accounts-service:172.20.0.5:8083     | UP     | version=1.0.0, zone=primary, api.version=v1, canary=false, weight=100, deployment.environment=docker, management.port=8083
```

---

## Casos de Uso de Metadata

### 1. Zone Affinity (Preferir misma zona)
```yaml
# Gateway / Client Ribbon/LoadBalancer
ribbon:
  eureka:
    zoneAffinity: true
```
→ Requests van a instancias en misma `zone` que el cliente.

### 2. Canary Deployment (10% tráfico a v1.1.0)
```yaml
# Instancia canary
metadata-map:
  version: "1.1.0"
  canary: "true"
  weight: "10"

# Instancia estable
metadata-map:
  version: "1.0.0"
  canary: "false"
  weight: "90"
```
→ Load balancer usa `weight` para distribuir 10% a canary.

### 3. API Versioning
```yaml
# Cliente v1
metadata-map:
  api.version: "v1"

# Cliente v2 (breaking changes)
metadata-map:
  api.version: "v2"
```
→ Gateway/router puede dirigir por version.

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void metadataRegistroCompletaYExpuestaEnRegistry() {
    // Given: Cliente con metadata-map completo registrado
    // When: GET /eureka/apps/ACCOUNTS-SERVICE/{instanceId}
    // Then: 
    // - metadata.version = "1.0.0"
    // - metadata.zone = "primary"
    // - metadata.api.version = "v1"
    // - metadata.canary = "false"
    // - metadata.weight = "100"
    // - metadata.deployment.environment = "docker"
    // - metadata.management.port = "8083"
    
    // When: Desplegar canary con metadata canary=true, weight=10
    // Then: Registry muestra ambas instancias con metadata diferenciada
}
```

---

## Criterios de Aceptación

- [ ] Metadata-map registrado en InstanceInfo al hacer POST `/eureka/apps/{SERVICE_ID}`
- [ ] Metadata expuesta en GET `/eureka/apps/{SERVICE_ID}/{INSTANCE_ID}`
- [ ] Dashboard muestra metadata en columna dedicada
- [ ] 7 campos obligatorios presentes: version, zone, api.version, deployment.environment, canary, weight, management.port
- [ ] Canary deployment: metadata `canary=true`, `weight=10` registrada correctamente
- [ ] Zone affinity: metadata `zone` usada por load balancer
- [ ] API versioning: metadata `api.version` expuesta
- [ ] Actuator `/actuator/info` del cliente consistente con registry

---

## Configuración Cliente Completa (Referencia)

```yaml
eureka:
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    metadata-map:
      version: ${project.version:1.0.0}
      zone: ${DEPLOYMENT_ZONE:primary}
      management.port: ${management.server.port:${server.port}}
      api.version: v1
      deployment.environment: ${SPRING_PROFILES_ACTIVE:docker}
      canary: "${CANARY_DEPLOYMENT:false}"
      weight: "${INSTANCE_WEIGHT:100}"
```

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) |
| **Diagrama Secuencia** | [SEC049_eureka_metadata_registro.md](../DIAGRAMAS_SECUENCIAS/SEC049_eureka_metadata_registro.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |
| **README Servicio** | [README.md](../README.md#32-metadata-de-instancia) |
| **Configuración Cliente** | [CU-18 §6.2](../CASOS_USO/CU-18-Gestion-Discovery-Service.md#62-configuración-bootstrapyml--config-server) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |