# Postman Collection — Discovery Service (Eureka Server)

Colección Postman completa para probar y validar todos los endpoints del **Discovery Service (Eureka Server)** de la plataforma Yanki.

## 📋 Archivos

| Archivo | Descripción |
|---------|-------------|
| `Yanki-Discovery-Service.postman_collection.json` | Colección principal con 18 requests en 4 folders |
| `Yanki-Discovery-Service.postman_environment.json` | Entorno con variables preconfiguradas |

## 🚀 Importación

1. Abre **Postman**
2. **Import** → Selecciona `Yanki-Discovery-Service.postman_collection.json`
3. **Import** → Selecciona `Yanki-Discovery-Service.postman_environment.json`
4. Activa el entorno importado desde el selector de entornos (arriba a la derecha)

## 🔧 Variables de Entorno

| Variable | Valor por Defecto | Descripción |
|----------|-------------------|-------------|
| `baseUrl` | `http://localhost:8761` | URL base del Discovery Service |
| `eurekaUser` | `admin` | Usuario para Basic Auth |
| `eurekaPassword` | `changeit` | Contraseña para Basic Auth (en producción: secret inyectado) |
| `appName` | `ACCOUNTS-SERVICE` | Nombre de aplicación en MAYÚSCULAS |
| `instanceId` | `accounts-service:172.20.0.5:8083` | ID de instancia (formato: service:ip:port) |

## 📂 Estructura de la Colección

### 1. Eureka Registry (4 requests)

Endpoints para consultar el registry de Eureka. **Basic Auth requerida.**

| Request | Método | Path | CP | Descripción |
|---------|--------|------|----|-------------|
| GET Registry Completo | `GET` | `/eureka/apps` | CP-170, CP-173 | Todas las aplicaciones e instancias |
| GET Instancias por Aplicación | `GET` | `/eureka/apps/{appName}` | CP-166, CP-170, CP-174 | Instancias de una aplicación |
| GET Instancias Healthy | `GET` | `/eureka/apps/{appName}/healthy` | CP-170 | Solo instancias UP |
| GET Instancia por ID | `GET` | `/eureka/apps/{appName}/{instanceId}` | CP-166, CP-167, CP-174 | Detalle de una instancia |

### 2. Eureka Protocol (3 requests)

Endpoints internos del protocolo Eureka. **Eureka Auth requerida.**

| Request | Método | Path | CP | Descripción |
|---------|--------|------|----|-------------|
| POST Registrar Instancia | `POST` | `/eureka/apps/{appName}` | CP-166, CP-174 | Registrar nueva instancia (204) |
| PUT Renovar Lease (Heartbeat) | `PUT` | `/eureka/apps/{appName}/{instanceId}` | CP-167, CP-168 | Heartbeat cada 10s (200) |
| DELETE Desregistrar Instancia | `DELETE` | `/eureka/apps/{appName}/{instanceId}` | CP-166, CP-168 | Desregistro graceful (200) |

### 3. Health & Info (2 requests)

Endpoints de health check. **Sin autenticación.**

| Request | Método | Path | CP | Descripción |
|---------|--------|------|----|-------------|
| GET Health Check | `GET` | `/actuator/health` | CP-171, CP-172 | Status UP/DOWN/OUT_OF_SERVICE |
| GET Service Info | `GET` | `/actuator/info` | CP-171 | Info del servicio |

### 4. Error Responses Reference (9 requests)

Ejemplos de respuestas de error con los códigos de negocio exactos de `GlobalExceptionHandler.java`.

| Request | HTTP Status | Código de Negocio | Descripción |
|---------|-------------|-------------------|-------------|
| Ejemplo 400 | `400` | `INVALID_INSTANCE_METADATA` | Metadata inválida |
| Ejemplo 403 | `403` | `SECURITY_CONFIG_ERROR` | Seguridad inválida |
| Ejemplo 404 (Instance) | `404` | `INSTANCE_NOT_FOUND` | Instancia no encontrada |
| Ejemplo 404 (Application) | `404` | `APPLICATION_NOT_FOUND` | Aplicación no encontrada |
| Ejemplo 409 | `409` | `SELF_PRESERVATION_UNEXPECTED` | Self-preservation activado |
| Ejemplo 500 (Config) | `500` | `EUREKA_CONFIG_ERROR` | Error de configuración |
| Ejemplo 500 (Generic) | `500` | `INTERNAL_SERVER_ERROR` | Error genérico |
| Ejemplo 503 (Peer) | `503` | `PEER_COMMUNICATION_ERROR` | Error de comunicación peer |
| Ejemplo 503 (Replication) | `503` | `REGISTRY_REPLICATION_ERROR` | Error de replicación |

## ✅ Casos de Prueba Cubiertos

| CP | Nombre | Requests Relacionados |
|----|--------|----------------------|
| **CP-166** | Registro de servicio en Eureka exitoso | POST Registrar, GET Instancia por ID, DELETE Desregistrar |
| **CP-167** | Heartbeat y renovación de lease | PUT Renovar Lease, GET Instancia por ID |
| **CP-168** | Detección y evicción de instancia caída | DELETE Desregistrar, GET Instancias por Aplicación |
| **CP-169** | Self-Preservation mode (producción) | GET Registry Completo (selfPreservationMode) |
| **CP-170** | API Gateway descubrimiento servicios | GET Registry Completo, GET Instancias por Aplicación, GET Instancias Healthy |
| **CP-171** | Health Indicators personalizados | GET Health Check, GET Service Info |
| **CP-172** | Dashboard Security (Basic Auth) | GET Health Check (sin auth), GET Registry Completo (con auth) |
| **CP-173** | Eureka HA Cluster | GET Registry Completo (consistencia entre nodos) |
| **CP-174** | Metadata registro | GET Instancia por ID, GET Instancias por Aplicación |

## 🧪 Tests Automatizados

Cada request incluye tests automatizados (`pm.test`) que validan:

- **Código de estado HTTP** esperado
- **Content-Type** `application/json`
- **Schema de respuesta** (campos obligatorios presentes)
- **Consistencia de datos** (métricas, timestamps, metadata)
- **Cobertura de CP** específica (ej. `[CP-166]`, `[CP-174]`)

Los tests están etiquetados con el CP que validan para trazabilidad directa.

## 📝 Flujo de Prueba Completo

Para probar el ciclo de vida completo de una instancia:

1. **POST** `/eureka/apps/ACCOUNTS-SERVICE` → Registrar instancia (204)
2. **GET** `/eureka/apps/ACCOUNTS-SERVICE/accounts-service:172.20.0.5:8083` → Verificar registro (200)
3. **PUT** `/eureka/apps/ACCOUNTS-SERVICE/accounts-service:172.20.0.5:8083` → Heartbeat (200)
4. **GET** `/eureka/apps` → Ver en registry completo (200)
5. **GET** `/actuator/health` → Verificar health UP (200)
6. **DELETE** `/eureka/apps/ACCOUNTS-SERVICE/accounts-service:172.20.0.5:8083` → Desregistrar (200)
7. **GET** `/eureka/apps/ACCOUNTS-SERVICE/accounts-service:172.20.0.5:8083` → Verificar eliminación (404)

## 🔐 Autenticación

| Endpoint | Autenticación | Detalle |
|----------|---------------|---------|
| `/eureka/**` | Basic Auth | `admin:{{eurekaPassword}}` |
| `/actuator/**` | Sin autenticación | Accesible para health checks |

> **Nota:** En producción, `eurekaPassword` debe ser un secret inyectado (no el default `changeit`).

## 📊 Schema de Respuestas

### EurekaRegistryResponse
```json
{
  "applications": [...],
  "lastUpdatedTimestamp": 1731590400000,
  "selfPreservationMode": false,
  "totalInstances": 1,
  "instancesUp": 1,
  "instancesDown": 0
}
```

### EurekaInstanceResponse
```json
{
  "instanceId": "accounts-service:172.20.0.5:8083",
  "appName": "ACCOUNTS-SERVICE",
  "hostName": "172.20.0.5",
  "ipAddress": "172.20.0.5",
  "port": 8083,
  "securePort": 8443,
  "status": "UP",
  "metadata": {
    "version": "1.0.0",
    "zone": "primary",
    "api.version": "v1",
    "canary": "false",
    "weight": "100",
    "deployment.environment": "docker",
    "management.port": "8083"
  },
  "lastRenewalTimestamp": 1731590400000,
  "registrationTimestamp": 1731590355000,
  "evictionTimestamp": 0,
  "canary": false,
  "zone": "primary",
  "apiVersion": "v1",
  "weight": 100,
  "version": "1.0.0"
}
```

### EurekaHealthResponse
```json
{
  "status": "UP",
  "components": {
    "eureka": {
      "status": "UP",
      "details": {
        "totalInstances": 1,
        "instancesUp": 1,
        "instancesDown": 0,
        "selfPreservationMode": false
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 2147483648,
        "free": 1073741824
      }
    }
  },
  "timestamp": 1731590400000
}
```

### ApiError (Error Estandarizado)
```json
{
  "timestamp": "2026-11-14T10:30:45.123Z",
  "status": 404,
  "error": "Not Found",
  "code": "INSTANCE_NOT_FOUND",
  "message": "Instancia no encontrada: accounts-service:172.20.0.5:8083",
  "path": "/eureka/apps/ACCOUNTS-SERVICE/accounts-service:172.20.0.5:8083"
}
```

## 🔗 Referencias

| Artefacto | Ubicación |
|-----------|-----------|
| Test Cases | `../test-cases/CP-166.md` a `CP-174.md` |
| Controllers | `src/main/java/com/yanki/discovery/interfaces/rest/controller/` |
| DTOs | `src/main/java/com/yanki/discovery/interfaces/rest/dto/` |
| GlobalExceptionHandler | `src/main/java/com/yanki/discovery/interfaces/rest/exception/GlobalExceptionHandler.java` |
| DiscoveryBusinessException | `src/main/java/com/yanki/discovery/domain/exception/DiscoveryBusinessException.java` |
| OpenAPI Spec | `../API/openapi.yml` |
| Tests Unitarios | `src/test/java/com/yanki/discovery/` |

## 📝 Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-15 | 1.0 | Sistema | Creación inicial: colección completa con 18 requests, environment, y documentación |
