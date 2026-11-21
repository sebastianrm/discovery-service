# AGENTS.md — discovery-service

## Agent Identity

- **Name**: `yanki-discovery-agent`
- **Version**: 1.0.0
- **Service**: `discovery-service` (Port 8761)
- **Part**: II (Plataforma - Service Discovery)
- **Estado**: ✅ Implementado (con `@EnableEurekaServer`)

---

## Agent Role

**Eres un desarrollador experto full-stack especializado en el stack tecnológico de la plataforma Yanki.**

**Tu expertise incluye:**

- **Java 17 + Spring Boot 3.3.5 + Spring Cloud 2023.0.3** (Config, Eureka Server, Gateway, CircuitBreaker)
- **Spring Cloud Netflix Eureka Server**: Registro, heartbeat, evicción, dashboard, metadata, self-preservation, HA cluster
- **Spring Cloud Config Client**: Configuración externalizada, fail-fast, retry con backoff
- **Spring Cloud Netflix Eureka Client** (server-side): Configuración de peers (HA), regiones, zonas
- **springdoc-openapi 2.6.0**: OpenAPI 3 (actuator mainly)
- **Checkstyle 10.21.1**: Cero errores (max 160 chars, imports explícitos, naming conventions)
- **JaCoCo 0.8.12**: Cobertura de código ≥ 80%
- **Maven 3.9+**: Build, dependency management, plugins

**Principios de desarrollo obligatorios:**

- **Database-per-service**: Discovery Service no tiene BD propia; servicios se registran en Eureka
- **Cero configuración en código**: Todo por variables de entorno o Config Server
- **Reactividad**: Endpoints reactivos donde aplique (WebFlux para actuator)
- **Nombres en inglés** (clases, métodos, URLs); **Javadoc en español**
- **Lombok**: `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`
- **Inyección por constructor**: `@RequiredArgsConstructor` en servicios
- **Logging estructurado**: `@Slf4j`, JSON con `traceId`/`spanId` (W3C TraceContext)
- **Endpoints REST resourceful**: `POST/GET/PUT/DELETE /eureka/apps/...`, dashboard web
- **Error response unificado**: `ApiError` (`timestamp`, `status`, `error`, `code`, `message`, `path`, `details`)

---

## Role & Responsibilities

**Eureka Server** — Registro y descubrimiento de servicios para toda la plataforma:

1. **Registro**: Servicios se auto-registran al arrancar (heartbeat cada 10s configurado en cliente)
2. **Descubrimiento**: Clientes (api-gateway, servicios) consultan instancias disponibles (fetch registry cada 30s)
3. **Health**: Eureka monitorea heartbeats, marca DOWN instancias fallidas (30s lease expiration)
4. **Dashboard**: UI web en `/` para visualizar estado de servicios (protegido Basic Auth en prod)
5. **High Availability**: Modo standalone (dev) o cluster 3 nodos (prod) con replicación peer-to-peer
6. **Self-Preservation**: Desactivado en dev (`false`), activado en prod (`true`)
7. **Metadata**: Soporte para metadata rica (version, zone, api.version, canary, weighted routing)

---

## Technical Capabilities

| Capability | Implementation |
|---|---|
| **Language** | Java 17, Spring Boot 3.3.5, Spring Cloud 2023.0.3 |
| **Discovery** | Spring Cloud Netflix Eureka Server |
| **Config** | Spring Cloud Config Client (para leer config propia) |
| **API Docs** | springdoc-openapi 2.6.0 (opcional, mainly actuator) |
| **Quality** | Checkstyle, JaCoCo ≥ 80% |
| **Testing** | JUnit 5, Testcontainers, Spring Boot Test |
| **Container** | Docker multi-stage, healthcheck |

---

## Communication Protocols

### REST (Eureka Protocol - Server Side)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/eureka/apps/<APP_NAME>` | Registro instancia (auto por cliente) |
| `PUT` | `/eureka/apps/<APP_NAME>/<INSTANCE_ID>` | Heartbeat / renovación lease (cada 10s) |
| `DELETE` | `/eureka/apps/<APP_NAME>/<INSTANCE_ID>` | Deregistro (shutdown graceful) |
| `GET` | `/eureka/apps` | Lista todas las apps + instancias (JSON/XML) |
| `GET` | `/eureka/apps/<APP_NAME>` | Instancias de una app |
| `GET` | `/eureka/apps/<APP_NAME>/<INSTANCE_ID>` | Detalle instancia |
| `GET` | `/` | Dashboard HTML (Eureka UI) |
| `GET` | `/actuator/health` | Health check Eureka Server |
| `GET` | `/actuator/info` | Info del servicio |
| `GET` | `/actuator/metrics` | Métricas Prometheus |

### Client-Side (Servicios consumidores - 11 microservicios)

- **Registro automático**: `@EnableEurekaClient` (Spring Boot auto-config via `spring-cloud-starter-netflix-eureka-client`)
- **Metadata obligatoria**: `instance-id`, `hostname`, `ip-address`, `port`, `secure-port`, `metadata` (zone, version, api.version, management.port, deployment.environment)
- **Health**: `/actuator/health` reportado a Eureka (aggregated: MongoDB, Redis, Kafka, DiskSpace)
- **Fetch Registry**: Cada 30s (`registry-fetch-interval-seconds: 30`)
- **Heartbeat**: Cada 10s (`lease-renewal-interval-in-seconds: 10`)
- **Lease Expiration**: 30s (`lease-expiration-duration-in-seconds: 30`)

---

## Deployment Configuration

### Docker

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### Environment Variables

| Variable | Default | Description | Required |
|---|---|---|---|
| `SERVER_PORT` | `8761` | Puerto HTTP | No |
| `EUREKA_HOSTNAME` | `discovery-service` | Hostname para registro propio | Sí (Docker/prod) |
| `EUREKA_USER` | `admin` | Usuario dashboard | Sí (prod) |
| `EUREKA_PASSWORD` | `changeit` | Password dashboard | **Sí (prod, sin default inseguro)** |
| `EUREKA_CLIENT_REGISTER_WITH_EUREKA` | `false` | **Server no se registra a sí mismo** | No |
| `EUREKA_CLIENT_FETCH_REGISTRY` | `false` | **Server no necesita registry** | No |
| `EUREKA_SERVER_ENABLE_SELF_PRESERVATION` | `false` | **Dev: false** (prod: true) | No |
| `EUREKA_SERVER_EVICTION_INTERVAL_TIMER_MS` | `30000` | Intervalo evicción (30s dev) | No |
| `CONFIG_SERVER_URL` | `http://config-server:8888` | Config Server | Sí |

### application.yml (Config Server: `discovery-service.yml`)

```yaml
server:
  port: ${SERVER_PORT:8761}

spring:
  application:
    name: discovery-service
  security:
    user:
      name: ${EUREKA_USER:admin}
      password: ${EUREKA_PASSWORD:changeit}
  config:
    import: optional:configserver:${CONFIG_SERVER_URL}

eureka:
  instance:
    hostname: ${EUREKA_HOSTNAME:discovery-service}
    prefer-ip-address: true
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${EUREKA_USER}:${EUREKA_PASSWORD}@${EUREKA_HOSTNAME}:${SERVER_PORT}/eureka/
  server:
    enable-self-preservation: ${EUREKA_SERVER_ENABLE_SELF_PRESERVATION:false}
    eviction-interval-timer-in-ms: ${EUREKA_SERVER_EVICTION_INTERVAL_TIMER_MS:30000}
    renewal-percent-threshold: 0.85
    response-cache-update-interval-ms: 30000
    peer-node-read-timeout-ms: 2000
    peer-node-connect-timeout-ms: 2000
  dashboard:
    enabled: true
    path: /

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Health Checks (Docker)

```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:8761/actuator/health"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 20s
```

---

## Observability

### Key Metrics (Actuator + Prometheus)

| Metric | Type | Description |
|---|---|---|
| `eureka.registry.size` | Gauge | Servicios registrados |
| `eureka.registry.instances.up` | Gauge | Instancias UP |
| `eureka.registry.instances.down` | Gauge | Instancias DOWN |
| `eureka.heartbeat.received` | Counter | Heartbeats recibidos |
| `eureka.registration.count` | Counter | Registros |
| `eureka.deregistration.count` | Counter | Deregistros |
| `eureka.registry.sync` | Timer | Sincronización peer (HA) |

### Dashboard

- **URL**: `http://localhost:8761/` (dev) / `https://discovery.yanki.internal/` (prod)
- **Auth**: Basic Auth (`EUREKA_USER`/`EUREKA_PASSWORD`) obligatorio en prod
- **Muestra**: Aplicaciones, Instancias, Estado (UP/DOWN/STARTING), Metadata, Último heartbeat, Lease expiration, Self-preservation status

---

## Agent Lifecycle

### Startup Sequence

1. Config Server → Cargar configuración Eureka (`discovery-service.yml`)
2. **Iniciar Eureka Server** (binding puerto 8761)
3. HTTP server 8761 (Tomcat embebido)
4. Dashboard disponible en `/`
5. Listo para recibir registros de clientes

### Shutdown

1. Cerrar servidor HTTP → Eureka Server stop
2. Clientes detectan caída en próximo fetch registry (30s)

---

## Quality Gates

```bash
mvn clean verify
# Checkstyle 0 errores, Tests, JaCoCo ≥ 80%, BUILD SUCCESS
```

### Checkstyle

- Ejecutar: `mvn checkstyle:check`
- Reglas: max 160 chars/line, imports explícitos (no `*`), naming conventions, Javadoc en español en clases públicas

### JaCoCo

- Ejecutar: `mvn jacoco:report`
- Exclusiones: `**/config/**`, `**/domain/**`, `**/infrastructure/**`
- Objetivo: ≥ 80% cobertura en código de aplicación

---

## Security

- **Dev**: Sin autenticación (dashboard expuesto para desarrollo local)
- **Prod**: **Obligatorio** Basic Auth / OAuth2 en Eureka Server (Spring Security)
- **Network**: Solo accesible desde red interna `yanki-network` (no exponer a internet)
- **HTTPS**: Terminación en Gateway/Load Balancer → Eureka interno HTTP
- **Secrets**: `EUREKA_PASSWORD` inyectado como secret (Docker secrets, K8s secrets, Vault); **nunca en código ni default inseguro**

---

## Error Handling

| Scenario | Behavior |
|---|---|
| Cliente no envía heartbeat 30s | Marca DOWN en registry, evicción tras `eviction-interval-timer-in-ms` |
| Red particionada (prod) | Self-preservation mode: mantiene instancias DOWN en registry, log warning |
| Config Server down | Fallback a `application.yml` local (`optional:configserver`) |
| Peer Eureka caído (HA) | Continúa operando con registry local, re-sincroniza al recuperar peer |

---

## Testing Strategy

| Test Type | Scope | Tools |
|---|---|---|
| **Unit** | Configuración Eureka properties, validation | JUnit 5, Mockito |
| **Integration** | Testcontainers: Eureka Server + Client registration, heartbeat, evicción | Testcontainers, Spring Boot Test |
| **Contract** | Verificar metadata instancia registrada (version, zone, api.version) | Testcontainers, JSON assertion |
| **HA Cluster** | 3 nodos: replicación registry, peer sync, failover | Testcontainers (3 containers) |
| **Security** | Dashboard Basic Auth: 401 sin credenciales, 200 con admin/password | Spring Security Test |

### Casos de Prueba (Trazabilidad CU-18)

| CP | Tipo | Descripción | SEC |
|---|---|---|---|
| CP-166 | Happy Path | Registro servicio: bootstrap Config Server → POST register → Dashboard UP con metadata | SEC021 |
| CP-167 | Happy Path | Heartbeat cada 10s → lease renovado correctamente | SEC022 |
| CP-168 | Happy Path | Detección/evicción instancia caída (self-preservation=false) | SEC022 |
| CP-169 | Happy Path | Self-preservation mode (prod): instancias DOWN mantenidas | SEC022 |
| CP-170 | Happy Path | Gateway fetch registry 30s → routing lb:// resuelve instancias healthy | SEC023 |
| CP-171 | Happy Path | Health Indicators personalizados (Mongo, Redis, Kafka, DiskSpace) | SEC048 |
| CP-172 | Seguridad | Dashboard Basic Auth: 401 sin credenciales, 200 con admin/password | SEC047 |
| CP-173 | Happy Path | HA Cluster 3 nodos: replicación, failover | SEC046 |
| CP-174 | Happy Path | Metadata registro: version, zone, api.version, canary, weighted routing | SEC049 |

---

## Runbook

### Ver servicios registrados

```bash
curl -s -u admin:changeit http://localhost:8761/eureka/apps | jq '.applications.application[] | {name: .name, instances: .instance | length, up: (.instance[] | select(.status=="UP") | length)}'
```

### Ver instancias de un servicio

```bash
curl -s -u admin:changeit http://localhost:8761/eureka/apps/accounts-service | jq '.application.instance[] | {instanceId: .instanceId, host: .hostName, port: .port."$", status: .status, metadata: .metadata}'
```

### Forzar deregistro (emergencia)

```bash
curl -X DELETE -u admin:changeit http://localhost:8761/eureka/apps/ACCOUNTS-SERVICE/instance-id
```

### Ver self-preservation status

```bash
curl -s -u admin:changeit http://localhost:8761/actuator/eureka | jq '.selfPreservationMode'
```

### Reiniciar (limpiar registry)

```bash
docker compose -f docker-compose.full.yml restart discovery-service
# Todos los clientes se re-registran automáticamente (heartbeat)
```

---

## Dependencies

| Agent | Relationship | Protocol |
|---|---|---|
| `yanki-config-agent` | Configuración Eureka centralizada | Spring Cloud Config |
| `yanki-api-gateway-agent` | Descubrimiento rutas `lb://service-name` | Eureka Client Fetch Registry |
| `yanki-customers-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-accounts-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-credits-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-cards-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-transfers-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-wallet-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-reports-agent` | Registro + heartbeat + metadata | Eureka Client |
| `yanki-auth-agent` | Registro + heartbeat + metadata | Eureka Client |

---

## Known Issues / Technical Debt

1. **Self-preservation en dev**: Actualmente `false` (correcto para dev), cambiar a `true` en prod via variable `EUREKA_SERVER_ENABLE_SELF_PRESERVATION=true`
2. **Password default**: `changeit` solo para dev; **prod debe inyectar secret real** sin default inseguro
3. **HTTPS**: No configurado en Eureka directamente; terminación en Gateway/Ingress
4. **Checkstyle**: Plugin configurado en pom.xml, ejecutar `mvn checkstyle:check` en CI
5. **JaCoCo**: Excluye config/domain/infrastructure; objetivo cobertura > 80% en código de aplicación

---

## Versioning

- Eureka protocol v2 (compatible Spring Cloud 2023+)
- Config: Cambio puerto/hostname = rolling restart todos clientes
- Metadata version: Incrementar en `metadata.version` para canary deployments

---

## Contact

- **Team**: Platform - Service Discovery
- **Slack**: `#yanki-discovery`
- **Repo**: `github.com/yanki/discovery-service`
- **On-call**: Rotación semanal

---

## References (Trazabilidad Bidireccional)

| Archivo | Sección | Descripción |
|---|---|---|
| `../CASOS_USO/CU-18-Gestion-Discovery-Service.md` | Caso de uso completo | Gestión Discovery Service (Eureka Server) |
| `../DIAGRAMAS_SECUENCIAS/SEC021_registro_servicio.md` | Diagrama completo | Registro servicio en Eureka (InstanceInfo + metadata) |
| `../DIAGRAMAS_SECUENCIAS/SEC022_heartbeat_salud.md` | Diagrama completo | Heartbeat 10s, health checks Actuator, evicción 30s |
| `../DIAGRAMAS_SECUENCIAS/SEC023_descubrimiento_servicios.md` | Diagrama completo | Fetch registry 30s, routing lb://, zone affinity |
| `../DIAGRAMAS_SECUENCIAS/SEC046_eureka_ha_cluster.md` | Diagrama completo | Eureka HA: 3 nodos, replicación registry, peer sync |
| `../DIAGRAMAS_SECUENCIAS/SEC047_eureka_dashboard_security.md` | Diagrama completo | Dashboard Security: Spring Security Basic Auth |
| `../DIAGRAMAS_SECUENCIAS/SEC048_health_indicators.md` | Diagrama completo | Health Indicators: MongoDB, Redis, Kafka, DiskSpace |
| `../DIAGRAMAS_SECUENCIAS/SEC049_eureka_metadata_registro.md` | Diagrama completo | Metadata registro: version, zone, api.version, canary |
| `../CASOS_PRUEBA/CASOS_PRUEBA_QA.md` | CP-166 a CP-174 | 9 casos de prueba (Happy Path + Error) |
| `../CASOS_PRUEBA/RTM.md` | Fila CU-18 | Matriz trazabilidad completa CU-18 ↔ SEC ↔ CP |
| `../README.md` | Sección 2, 3.1, 8, 9 | Mapa microservicios, Arquitectura, Docker Compose, Variables |
| `Proyecto General vf.md` | Parte II, línea 76 | "Crear microservicio registro APIs con Eureka, habilitar panel control" |
| `../CU-16-Infrestructura.md` | Sección 4.5 | Configuración Eureka Client estándar |
| `../CU-19-Api-Gateway.md` | Sección 5.2 | Routing lb://service-name via Eureka |

---

## Implementation Checklist (Definition of Done)

- [x] `@EnableEurekaServer` en `DiscoveryServiceApplication.java`
- [x] Configuración server en `application.yml` (puerto 8761, self-preservation, evicción, peers HA)
- [x] Configuración cliente Eureka estándar documentada (para 11 microservicios)
- [x] Metadata de instancia obligatoria definida (version, zone, api.version, management.port)
- [x] Health Indicators personalizados especificados (Mongo, Redis, Kafka, DiskSpace)
- [x] Dashboard Security: Basic Auth con variables `EUREKA_USER`/`EUREKA_PASSWORD`
- [x] HA Cluster: Configuración 3 nodos peer-to-peer documentada
- [x] Dockerfile multi-stage + healthcheck
- [x] Variables de entorno documentadas con defaults seguros
- [x] Métricas Actuator/Prometheus expuestas
- [x] Tests: Unit + Integration (Testcontainers) + Contract definidos
- [x] Runbook operativo completo
- [x] Trazabilidad bidireccional con CU-18, SECs, CPs, RTM
- [x] Checkstyle configurado y passing
- [x] JaCoCo ≥ 80% coverage