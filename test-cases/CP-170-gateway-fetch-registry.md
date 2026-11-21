# CP-170 — API Gateway descubrimiento servicios (Fetch Registry 30s)

> **CU:** CU-18 | **SEC:** SEC023_descubrimiento_servicios.md | **Servicio:** discovery-service (Eureka Server) + api-gateway

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-170 |
| **Tipo** | Happy Path |
| **Prioridad** | Crítica |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Unit + Integration |

---

## Descripción

Validar que el API Gateway (como cliente Eureka) realiza **Fetch Registry** cada 30 segundos (`registry-fetch-interval-seconds: 30`), construye la tabla de routing dinámica `lb://service-name` → [instancias healthy], y resuelve correctamente las peticiones a instancias UP usando load balancing (RoundRobin/ZoneAware).

---

## Precondiciones

1. **Eureka Server** UP en puerto 8761
2. **API Gateway** UP en puerto 8080 con cliente Eureka configurado:
   ```yaml
   eureka:
     client:
       fetch-registry: true
       registry-fetch-interval-seconds: 30
   ```
3. Múltiples instancias de al menos un backend service (ej. accounts-service) registradas y UP
4. Gateway routes configurados con `lb://accounts-service`

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Verificar Gateway UP y fetch registry inicial | Log: "Fetching registry from eureka" |
| 2 | Verificar rutas activas: `GET /actuator/gateway/routes` | 10 rutas incluyendo `lb://accounts-service` |
| 3 | Request a backend via Gateway: `GET http://gateway:8080/api/accounts/123` | Gateway resuelve `lb://accounts-service` → instancia healthy → forward |
| 4 | Verificar headers propagados: `X-User-Id`, `X-User-Roles`, `X-Customer-Id`, `X-Request-Id` | Headers presentes en backend |
| 5 | Detener una instancia de accounts-service (simular caída) | Instancia marcada DOWN en Eureka (ver CP-168) |
| 6 | Esperar 30s (próximo fetch registry) | Gateway actualiza routing table, elimina instancia DOWN |
| 7 | Request nuevamente: `GET /api/accounts/123` | Gateway resuelve solo a instancias UP restantes |
| 8 | Verificar load balancing: múltiples requests → distribución RoundRobin/ZoneAware | Distribución equitativa entre instancias UP |

---

## Validaciones Técnicas

### Gateway - Fetch Registry Logs
```
DiscoveryClient_... - Fetching registry from eureka server
DiscoveryClient_... - Fetching registry completed in 45ms
```

### Gateway - Routing Table (actuator/gateway/routes)
```json
{
  "route_id": "accounts-service",
  "route_predicate": "Paths: [/api/accounts/**], match trailing slash: true",
  "route_filters": "[[StripPrefix 1], [AddRequestHeader X-Request-Id, ...]]",
  "uri": "lb://accounts-service",
  "order": 0
}
```

### Gateway - Resolución lb:// (ReactoreactorLoadBalancer)
```
LoadBalancerClientFactory - Load balancer for accounts-service: reactorLoadBalancer
RoundRobinLoadBalancer - Selected instance: accounts-service:172.20.0.5:8083
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void gatewayFetchRegistryYResuelveLbServiceName() {
    // Given: Eureka + Gateway + 2 instancias accounts-service UP
    // When: Request GET /api/accounts/123 via Gateway
    // Then: 
    // 1. Gateway resuelve lb://accounts-service a instancia healthy
    // 2. Response 200 (o respuesta esperada del backend)
    // 3. Headers X-User-* propagados
    
    // When: Detener 1 instancia accounts-service
    // And: Esperar 35s (fetch registry 30s + buffer)
    // Then:
    // 4. Gateway ya no resuelve a instancia caída
    // 5. Requests van solo a instancia UP restante
}
```

---

## Criterios de Aceptación

- [ ] Gateway fetch registry cada 30s (configurable)
- [ ] Routing table `lb://service-name` construida correctamente
- [ ] Requests resueltos solo a instancias con status UP
- [ ] Load balancing funcional (RoundRobin/ZoneAware)
- [ ] Instancias DOWN eliminadas de routing table en próximo fetch
- [ ] Headers de autenticación propagados correctamente
- [ ] StripPrefix=1 aplicado en paths `/api/**`

---

## Configuración Gateway Requerida

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: accounts-service
          uri: lb://accounts-service
          predicates:
            - Path=/api/accounts/**
          filters:
            - StripPrefix=1
            - AddRequestHeader=X-Request-Id, ${request.headers.X-Request-Id:-}
      discovery:
        locator:
          enabled: true  # Habilita rutas automáticas lb://
          lower-case-service-id: true

eureka:
  client:
    fetch-registry: true
    registry-fetch-interval-seconds: 30
    service-url:
      defaultZone: http://admin:password@discovery-service:8761/eureka/
```

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) / [CU-19](../CASOS_USO/CU-19-Api-Gateway.md) |
| **Diagrama Secuencia** | [SEC023_descubrimiento_servicios.md](../DIAGRAMAS_SECUENCIAS/SEC023_descubrimiento_servicios.md) / [SEC024_api_gateway_diagrama_secuencia.md](../DIAGRAMAS_SECUENCIAS/SEC024_api_gateway_diagrama_secuencia.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |
| **CP Relacionado** | [CP-175](../test-cases/CP-175-routing-exitoso-lb.md) (en api-gateway) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |