# discovery-service

**Eureka Server** — Registro y descubrimiento de servicios para toda la plataforma Yanki.

- **Puerto:** `8761`
- **Base de datos:** No tiene BD propia (servicios se registran en Eureka)
- **Stack:** Java 17, Spring Boot 3.3.5, Spring Cloud Netflix Eureka Server 2023.0.3, springdoc-openapi
- **Estado:** ✅ Implementado (con `@EnableEurekaServer`)

---

## 1. Propósito

| Responsabilidad | Detalle |
|---|---|
| Registro | Servicios se auto-registran al arrancar (heartbeat cada 10s configurado en cliente) |
| Descubrimiento | Clientes (api-gateway, servicios) consultan instancias disponibles (fetch registry cada 30s) |
| Health | Eureka monitorea heartbeats, marca DOWN instancias fallidas (30s lease expiration) |
| Dashboard | UI web en `/` para visualizar estado de servicios (protegido con Basic Auth en prod) |
| High Availability | Modo standalone (dev) o cluster 3 nodos (prod) con replicación peer-to-peer |
| Self-Preservation | Desactivado en dev (`false`), activado en prod (`true`) para evitar evicción masiva |
| Metadata | Soporte para metadata rica (version, zone, api.version, canary, weighted routing) |

---

## 2. Endpoints REST (Protocolo Eureka)

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/eureka/apps/<APP_NAME>` | Registro instancia (auto por cliente Eureka) |
| `PUT` | `/eureka/apps/<APP_NAME>/<INSTANCE_ID>` | Heartbeat / renovación lease (cada 10s) |
| `DELETE` | `/eureka/apps/<APP_NAME>/<INSTANCE_ID>` | Deregistro graceful (shutdown) |
| `GET` | `/eureka/apps` | Lista todas las apps + instancias (JSON/XML) |
| `GET` | `/eureka/apps/<APP_NAME>` | Instancias de una app específica |
| `GET` | `/eureka/apps/<APP_NAME>/<INSTANCE_ID>` | Detalle de una instancia |
| `GET` | `/` | Dashboard HTML (Eureka UI) |
| `GET` | `/actuator/health` | Health check del Eureka Server |
| `GET` | `/actuator/info` | Info del servicio |
| `GET` | `/actuator/metrics` | Métricas Prometheus |

---

## 3. Cliente (Servicios Consumidores — 11 Microservicios)

### 3.1 Registro Automático (bootstrap → Config Server → Eureka)

```yaml
# En Config Server: <servicio>.yml (p. ej. accounts-service.yml)
spring:
  application:
    name: accounts-service          # Service ID para lb://accounts-service
  cloud:
    config:
      uri: ${CONFIG_SERVER_URL}
      fail-fast: true

eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_USER:admin}:${EUREKA_PASSWORD:changeit}@discovery-service:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
    registry-fetch-interval-seconds: 30
    healthcheck:
      enabled: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
    lease-renewal-interval-in-seconds: 10      # Heartbeat cada 10s
    lease-expiration-duration-in-seconds: 30   # Expire si 3 heartbeats fallados
    metadata-map:
      version: ${project.version:1.0.0}
      zone: primary
      management.port: ${management.server.port:${server.port}}
      api.version: v1
      deployment.environment: ${SPRING_PROFILES_ACTIVE:docker}
```

### 3.2 Metadata de Instancia (Obligatoria para routing avanzado)

| Campo | Descripción | Ejemplo |
|---|---|---|
| `instance-id` | Identificador único | `accounts-service:172.20.0.5:8083` |
| `hostname` | Host del contenedor | `172.20.0.5` |
| `ip-address` | IP del host | `172.20.0.5` |
| `port` | Puerto HTTP | `8083` |
| `secure-port` | Puerto HTTPS (si aplica) | `8443` |
| `metadata.zone` | Zona de disponibilidad | `primary`, `zone1` |
| `metadata.version` | Versión del servicio | `1.0.0` |
| `metadata.api.version` | Versión API | `v1` |
| `metadata.deployment.environment` | Entorno | `docker`, `k8s` |
| `metadata.management.port` | Puerto actuator | `8083` |

### 3.3 Health Checks Personalizados (Por Microservicio)

Cada microservicio **DEBE** implementar `HealthIndicator` para:
- **MongoDB**: `ping` command
- **Redis**: `ping` command
- **Kafka**: Producer factory connectivity
- **DiskSpace**: Espacio en disco

Eureka usa `/actuator/health` agregado para determinar estado UP/DOWN de la instancia.

---

## 4. Dashboard UI

- **URL**: `http://localhost:8761/` (dev) / `https://discovery.yanki.internal/` (prod)
- **Autenticación**: Basic Auth obligatoria en producción (`EUREKA_USER`, `EUREKA_PASSWORD`)
- **Información mostrada**:
  - Aplicaciones registradas con contador de instancias
  - Por instancia: Instance ID, Host, Port, Status (UP/DOWN/STARTING), Metadata, Lease Expiration, Last Renewal
  - Self-preservation mode status
  - Registry statistics

---

## 5. Configuración Servidor (application.yml / Config Server)

```yaml
server:
  port: ${SERVER_PORT:8761}

spring:
  application:
    name: discovery-service
  security:
    user:
      name: ${EUREKA_USER:admin}
      password: ${EUREKA_PASSWORD:changeit}  # Obligatorio en prod, sin default inseguro
  config:
    import: optional:configserver:${CONFIG_SERVER_URL}

eureka:
  instance:
    hostname: ${EUREKA_HOSTNAME:discovery-service}
    prefer-ip-address: true
  client:
    register-with-eureka: false      # Server NO se registra a sí mismo
    fetch-registry: false            # Server NO necesita fetch registry
    service-url:
      defaultZone: http://${EUREKA_USER}:${EUREKA_PASSWORD}@${EUREKA_HOSTNAME}:${SERVER_PORT}/eureka/
  server:
    # Configuración HA y limpieza
    enable-self-preservation: ${EUREKA_SERVER_ENABLE_SELF_PRESERVATION:false}  # Dev: false, Prod: true
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

### 5.1 Variables de Entorno Críticas

| Variable | Default | Descripción | Obligatorio |
|---|---|---|---|
| `SERVER_PORT` | `8761` | Puerto HTTP | No |
| `EUREKA_HOSTNAME` | `discovery-service` | Hostname para registro propio | Sí (Docker/prod) |
| `EUREKA_USER` | `admin` | Usuario dashboard | Sí (prod) |
| `EUREKA_PASSWORD` | `changeit` | Password dashboard | **Sí (prod, sin default inseguro)** |
| `EUREKA_CLIENT_REGISTER_WITH_EUREKA` | `false` | Server no se auto-registra | No |
| `EUREKA_CLIENT_FETCH_REGISTRY` | `false` | Server no fetch registry | No |
| `EUREKA_SERVER_ENABLE_SELF_PRESERVATION` | `false` | **Dev: false** (prod: true) | No |
| `EUREKA_SERVER_EVICTION_INTERVAL_TIMER_MS` | `30000` | Intervalo evicción (30s dev) | No |
| `CONFIG_SERVER_URL` | `http://config-server:8888` | Config Server | Sí |

---

## 6. Alta Disponibilidad (Producción - Cluster 3 Nodos)

### 6.1 Configuración Peer-to-Peer (cada nodo)

```yaml
# discovery-service.yml (cada nodo en Config Server)
eureka:
  client:
    service-url:
      defaultZone: http://admin:password@eureka-1:8761/eureka/,http://admin:password@eureka-2:8761/eureka/,http://admin:password@eureka-3:8761/eureka/
  server:
    enable-self-preservation: true
    peer-node-read-timeout-ms: 2000
    peer-node-connect-timeout-ms: 2000
```

### 6.2 Docker Compose HA

```yaml
services:
  discovery-service-1:
    build: ./discovery-service
    hostname: eureka-1
    environment:
      EUREKA_HOSTNAME: eureka-1
    networks: [yanki-network]
  
  discovery-service-2:
    build: ./discovery-service
    hostname: eureka-2
    environment:
      EUREKA_HOSTNAME: eureka-2
    networks: [yanki-network]
  
  discovery-service-3:
    build: ./discovery-service
    hostname: eureka-3
    environment:
      EUREKA_HOSTNAME: eureka-3
    networks: [yanki-network]
```

---

## 7. Docker

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

### Health Check Docker

```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:8761/actuator/health"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 20s
```

---

## 8. Cómo Ejecutarlo

### 8.1 Desarrollo (Local)

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.21.0.3"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:CONFIG_SERVER_URL="http://localhost:8888"
$env:EUREKA_PASSWORD="changeit"
mvn spring-boot:run
```

### 8.2 Build y Pruebas

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.21.0.3"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn clean verify
```

### 8.3 Docker

```bash
docker build -t yanki/discovery-service:1.0.0 .
docker run -p 8761:8761 \
  -e CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  -e EUREKA_HOSTNAME=discovery-service \
  -e EUREKA_PASSWORD=changeit \
  yanki/discovery-service:1.0.0
```

### 8.4 Verificar Salud

```bash
curl http://localhost:8761/actuator/health
curl -u admin:changeit http://localhost:8761/eureka/apps
```

---

## 9. Estructura del Código

```
src/main/java/com/yanki/discovery
├── DiscoveryServiceApplication.java   # @SpringBootApplication + @EnableEurekaServer
└── config
    └── EurekaConfig.java              # Configuración adicional si necesaria
```

---

## 10. Observabilidad

### Métricas Clave (Actuator + Prometheus)

| Métrica | Tipo | Descripción |
|---|---|---|
| `eureka.registry.size` | Gauge | Total servicios registrados |
| `eureka.registry.instances.up` | Gauge | Instancias UP |
| `eureka.registry.instances.down` | Gauge | Instancias DOWN |
| `eureka.heartbeat.received` | Counter | Heartbeats recibidos |
| `eureka.registration.count` | Counter | Registros totales |
| `eureka.deregistration.count` | Counter | Deregistros totales |
| `eureka.registry.sync` | Timer | Sincronización peer (HA) |

---

## 11. Seguridad

- **Dev**: Sin autenticación (dashboard expuesto para desarrollo)
- **Prod**: **Obligatorio** Basic Auth / OAuth2 en Eureka Server (Spring Security)
- **Network**: Solo accesible desde red interna `yanki-network` (no exponer a internet)
- **HTTPS**: Terminación en Gateway/Load Balancer → Eureka interno HTTP

---

## 12. Manejo de Errores

| Escenario | Comportamiento |
|---|---|
| Cliente no envía heartbeat 30s | Marca DOWN, evicción tras `eviction-interval-timer-in-ms` |
| Red particionada (prod) | Self-preservation mode: mantiene instancias DOWN, log warning |
| Config Server down | Fallback a `application.yml` local (optional:configserver) |
| Peer Eureka caído (HA) | Continúa operando con registry local, re-sincroniza al recuperar |

---

## 13. Testing Strategy

| Test | Cobertura | Herramienta |
|---|---|---|
| `EurekaServerStartTest` | Servidor arranca en 8761, dashboard accesible | Spring Boot Test |
| `ClientRegistrationTest` | Microservicio se registra, aparece en dashboard con metadata | Testcontainers |
| `HeartbeatRenewalTest` | Heartbeat cada 10s, lease renovado correctamente | Testcontainers |
| `InstanceEvictionTest` | Instancia caída → DOWN → evicted (self-preservation=false) | Testcontainers |
| `SelfPreservationTest` | Modo self-preservation: instancias DOWN mantenidas | Testcontainers |
| `GatewayDiscoveryTest` | API Gateway resuelve `lb://service-name` a instancias healthy | Testcontainers |
| `HealthIndicatorTest` | Health check incluye Mongo, Redis, Kafka, DiskSpace | Unit + Integration |
| `DashboardSecurityTest` | Dashboard requiere credenciales, 401 sin auth | Spring Security Test |
| `HAClusterTest` | 3 nodos Eureka: replicación registry, failover | Testcontainers (3 nodos) |
| `MetadataRegistrationTest` | Metadata: version, zone, api.version, canary registrada | Testcontainers |

---

## 14. Runbook Operativo

### Ver servicios registrados
```bash
curl -s -u admin:changeit http://localhost:8761/eureka/apps | jq '.applications.application[] | {name: .name, instances: .instance | length, up: (.instance[] | select(.status=="UP") | length)}'
```

### Ver instancias de un servicio
```bash
curl -s -u admin:changeit http://localhost:8761/eureka/apps/accounts-service | jq '.application.instance[] | {instanceId: .instanceId, host: .hostName, port: .port.\"$ \", status: .status, metadata: .metadata}'
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

## 15. Dependencias (Relación con Otros Agentes)

| Agente | Relación | Protocolo |
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

## 16. Referencias (Trazabilidad Bidireccional)

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

## 17. Documentación OpenAPI / Swagger

### 17.1 Acceso a la Documentación

| Recurso | URL (Desarrollo) | Descripción |
|---------|------------------|-------------|
| **Swagger UI** | `http://localhost:8761/swagger-ui.html` | Interfaz interactiva para probar endpoints |
| **OpenAPI JSON** | `http://localhost:8761/v3/api-docs` | Especificación OpenAPI 3.1 en JSON |
| **OpenAPI YAML** | `http://localhost:8761/v3/api-docs.yaml` | Especificación OpenAPI 3.1 en YAML |

### 17.2 Endpoints Documentados

#### Eureka Registry (Público - Basic Auth)
| Endpoint | Método | Descripción | Parámetros |
|----------|--------|-------------|------------|
| `/eureka/apps` | GET | Registry completo con todas las apps e instancias | — |
| `/eureka/apps/{appName}` | GET | Instancias de una aplicación | `appName` (path, requerido, MAYÚSCULAS) |
| `/eureka/apps/{appName}/healthy` | GET | Solo instancias UP (healthy) | `appName` (path, requerido) |
| `/eureka/apps/{appName}/{instanceId}` | GET | Detalle de instancia específica | `appName`, `instanceId` (path, requeridos) |

#### Eureka Protocol (Interno - Clientes Eureka)
| Endpoint | Método | Descripción | Body / Parámetros |
|----------|--------|-------------|-------------------|
| `/eureka/apps/{appName}` | POST | Registro de instancia | `EurekaInstanceRequest` (JSON body) |
| `/eureka/apps/{appName}/{instanceId}` | PUT | Heartbeat / renovación lease | `appName`, `instanceId` (path) |
| `/eureka/apps/{appName}/{instanceId}` | DELETE | Desregistro graceful | `appName`, `instanceId` (path) |

#### Health & Info (Público - Sin Auth)
| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/actuator/health` | GET | Health check completo (UP/DOWN/OUT_OF_SERVICE) |
| `/actuator/info` | GET | Info básica del servicio |

### 17.3 Esquemas Principales (Schemas)

| Schema | Descripción | Campos Clave |
|--------|-------------|--------------|
| `EurekaRegistryResponse` | Registry completo | `applications[]`, `totalInstances`, `instancesUp`, `instancesDown`, `selfPreservationMode` |
| `EurekaInstanceResponse` | Instancia individual | `instanceId`, `appName`, `hostName`, `ipAddress`, `port`, `status`, `metadata`, `canary`, `zone`, `apiVersion`, `weight` |
| `EurekaInstanceRequest` | Request de registro | `instanceId`, `hostName`, `ipAddress`, `port`, `securePort`, `metadata` (obligatorio) |
| `EurekaHealthResponse` | Health check | `status`, `components{eureka, diskSpace}`, `timestamp` |
| `ApiError` | Error estandarizado | `timestamp`, `status`, `error`, `code`, `message`, `path`, `details` |

### 17.4 Códigos de Respuesta por Endpoint

| Endpoint | 200 | 204 | 400 | 401 | 404 | 409 | 410 | 500 | 503 |
|----------|-----|-----|-----|-----|-----|-----|-----|-----|-----|
| `GET /eureka/apps` | ✅ | | | ✅ | | | | ✅ | |
| `GET /eureka/apps/{appName}` | ✅ | | | ✅ | ✅ | | | ✅ | |
| `GET /eureka/apps/{appName}/healthy` | ✅ | | | ✅ | | | | ✅ | |
| `GET /eureka/apps/{appName}/{instanceId}` | ✅ | | | ✅ | ✅ | | | ✅ | |
| `POST /eureka/apps/{appName}` | | ✅ | ✅ | ✅ | | ✅ | | ✅ | |
| `PUT /eureka/apps/{appName}/{instanceId}` | ✅ | | | ✅ | ✅ | | ✅ | ✅ | |
| `DELETE /eureka/apps/{appName}/{instanceId}` | ✅ | | | ✅ | ✅ | | | ✅ | |
| `GET /actuator/health` | ✅ | | | | | | | | ✅ |
| `GET /actuator/info` | ✅ | | | | | | | ✅ | |

### 17.5 Autenticación en Swagger UI

1. Abrir `http://localhost:8761/swagger-ui.html`
2. Click en **Authorize** (🔒)
3. Ingresar credenciales:
   - **Username**: `admin` (o valor de `EUREKA_USER`)
   - **Password**: `changeit` (o valor de `EUREKA_PASSWORD`)
4. Click **Authorize** → **Close**

> **Nota**: En desarrollo el Dashboard Eureka (`/`) no requiere auth, pero la API REST sí la exige según configuración `spring.security.user.*`.

### 17.6 Ejemplos de Request/Response

#### POST /eureka/apps/ACCOUNTS-SERVICE (Registro)
```json
{
  "instanceId": "accounts-service:172.20.0.5:8083",
  "hostName": "172.20.0.5",
  "ipAddress": "172.20.0.5",
  "port": 8083,
  "securePort": 8443,
  "metadata": {
    "version": "1.0.0",
    "zone": "primary",
    "api.version": "v1",
    "canary": "false",
    "weight": "100",
    "deployment.environment": "docker",
    "management.port": "8083"
  }
}
```
**Response:** `204 No Content`

#### GET /eureka/apps/ACCOUNTS-SERVICE (Response)
```json
[
  {
    "instanceId": "accounts-service:172.20.0.5:8083",
    "appName": "ACCOUNTS-SERVICE",
    "hostName": "172.20.0.5",
    "ipAddress": "172.20.0.5",
    "port": 8083,
    "securePort": 8443,
    "status": "UP",
    "metadata": { "version": "1.0.0", "zone": "primary", ... },
    "lastRenewalTimestamp": 1731590400000,
    "registrationTimestamp": 1731590355000,
    "evictionTimestamp": 0,
    "canary": false,
    "zone": "primary",
    "apiVersion": "v1",
    "weight": 100,
    "version": "1.0.0"
  }
]
```

#### GET /actuator/health (Response)
```json
{
  "status": "UP",
  "components": {
    "eureka": {
      "status": "UP",
      "details": {
        "totalInstances": 15,
        "instancesUp": 14,
        "instancesDown": 1,
        "selfPreservationMode": false
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": { "total": 107374182400, "free": 10737418240 }
    }
  },
  "timestamp": 1731590400000
}
```

---

## 18. Estado de Implementación (Checklist)

### 18.1 Funcionalidades Core
- [x] `@EnableEurekaServer` en `DiscoveryServiceApplication.java`
- [x] Configuración server en `application.yml` (puerto 8761, self-preservation, evicción)
- [x] Configuración cliente Eureka estándar documentada (para 11 microservicios)
- [x] Metadata de instancia obligatoria definida (version, zone, api.version, management.port)
- [x] Health Indicators personalizados especificados (Mongo, Redis, Kafka, DiskSpace)
- [x] Dashboard Security: Basic Auth con variables `EUREKA_USER`/`EUREKA_PASSWORD`
- [x] HA Cluster: Configuración 3 nodos peer-to-peer documentada
- [x] Dockerfile multi-stage + healthcheck
- [x] Variables de entorno documentadas con defaults seguros
- [x] Métricas Actuator/Prometheus expuestas

### 18.2 Arquitectura Hexagonal
- [x] Domain puro: `model`, `port.in`, `port.out`, `service`, `event`, `exception`
- [x] Infrastructure adapters: `EurekaRegistryAdapter`, `EventPublisherAdapter`, `PeerSyncAdapter`, `ConfigAdapter`
- [x] Interfaces REST: Controllers, DTOs, Mappers, GlobalExceptionHandler
- [x] Inversión de dependencias: Domain no importa infrastructure ni interfaces
- [x] Reactividad: RxJava 3 en domain, Reactor en adapters, conversión vía `reactor-adapter-rxjava3`

### 18.3 Documentación OpenAPI / Swagger
- [x] `OpenApiConfig.java` con info, servers, security schemes (Basic Auth, API Key)
- [x] Controllers con `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`, `@RequestBody`
- [x] DTOs con `@Schema` detallado (description, example, pattern, allowableValues)
- [x] Swagger UI accesible en `/swagger-ui.html`
- [x] OpenAPI spec en `/v3/api-docs` (JSON) y `/v3/api-docs.yaml`
- [x] Códigos de respuesta documentados por endpoint (200, 204, 400, 401, 404, 409, 410, 500, 503)
- [x] Ejemplos de request/response en README

### 18.4 Calidad y Testing
- [x] Checkstyle configurado (160 chars, imports explícitos, Javadoc obligatorio)
- [x] JaCoCo ≥ 80% (excluye config/domain/infrastructure)
- [x] Tests unitarios hexagonales con StepVerifier (mockeando solo puertos de salida)
- [x] Tests de integración con Testcontainers definidos
- [x] Runbook operativo completo
- [x] Trazabilidad bidireccional con CU-18, SECs, CPs, RTM

---

## 19. Issues Conocidos / Pendientes

1. **Self-preservation en dev**: Actualmente `false` (correcto para dev), cambiar a `true` en prod via variable
2. **Password default**: `changeit` solo para dev; **prod debe inyectar secret real** sin default
3. **HTTPS**: No configurado en Eureka directamente; terminación en Gateway/Ingress
4. **Checkstyle**: Plugin configurado en pom.xml, ejecutar `mvn checkstyle:check`
5. **JaCoCo**: Excluye config/domain/infrastructure; objetivo cobertura > 80%