# CP-171 — Health Indicators personalizados (MongoDB, Redis, Kafka, DiskSpace)

> **CU:** CU-18 | **SEC:** SEC048_health_indicators.md | **Servicio:** discovery-service (Eureka Server) + **Todos los microservicios cliente**

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-171 |
| **Tipo** | Happy Path |
| **Prioridad** | Alta |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Unit |

---

## Descripción

Validar que **cada microservicio cliente Eureka** implementa `HealthIndicator` personalizados para **MongoDB, Redis, Kafka y DiskSpace**, y que el health check agregado (`/actuator/health`) determina correctamente el status UP/DOWN que Eureka usa para marcar la instancia en el registry.

> **Nota**: Aunque este CP valida health indicators en los **microservicios cliente**, el **discovery-service (Eureka Server)** es quien **consume y expone** esta información via registry. El Eureka Server también debe tener su propio health check.

---

## Precondiciones

1. **Eureka Server** UP
2. **Microservicio cliente** (ej. accounts-service) con dependencias:
   - `spring-boot-starter-actuator`
   - `spring-cloud-starter-netflix-eureka-client`
   - Drivers MongoDB, Redis, Kafka
3. Health Indicators implementados en el microservicio:
   - `MongoHealthIndicator`
   - `RedisHealthIndicator`
   - `KafkaHealthIndicator`
   - `DiskSpaceHealthIndicator` (incluido por defecto en actuator)

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Verificar endpoint health del microservicio: `GET /actuator/health` | Status `UP` con details: `{mongo:{status:UP}, redis:{status:UP}, kafka:{status:UP}, diskSpace:{status:UP, free:10GB}}` |
| 2 | Verificar que Eureka usa este health para status instancia | Dashboard muestra instancia UP |
| 3 | Simular fallo MongoDB (detener MongoDB) | `GET /actuator/health` → Status `DOWN` con `mongo:{status:DOWN, error:...}` |
| 4 | Verificar Eureka: instancia marcada DOWN tras lease-expiration (30s) | Dashboard muestra DOWN |
| 5 | Restaurar MongoDB | Health → UP, Eureka → UP tras heartbeats |
| 6 | Repetir para Redis, Kafka, DiskSpace (llenar disco) | Mismo comportamiento: DOWN/UP según health agregado |

---

## Estructura Health Response Esperada

```json
{
  "status": "UP",
  "components": {
    "mongo": {
      "status": "UP",
      "details": {
        "database": "MongoDB",
        "status": "UP"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "broker": "Redis",
        "status": "UP"
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "broker": "Kafka",
        "status": "UP"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 107374182400,
        "free": 10737418240,
        "threshold": 10485760,
        "exists": true
      }
    },
    "eureka": {
      "status": "UP"
    }
  }
}
```

---

## Implementación Health Indicators (Referencia)

### MongoHealthIndicator
```java
@Component
public class MongoHealthIndicator implements HealthIndicator {
    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return Health.up()
                .withDetail("database", "MongoDB")
                .withDetail("status", "UP")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "MongoDB")
                .withException(e)
                .build();
        }
    }
}
```

### RedisHealthIndicator
```java
@Component
public class RedisHealthIndicator implements HealthIndicator {
    private final RedisTemplate<?, ?> redisTemplate;

    @Override
    public Health health() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return Health.up()
                .withDetail("broker", "Redis")
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

### KafkaHealthIndicator
```java
@Component
public class KafkaHealthIndicator implements HealthIndicator {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            kafkaTemplate.getProducerFactory().createProducer().close();
            return Health.up()
                .withDetail("broker", "Kafka")
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void healthIndicatorsDeterminanStatusEureka() {
    // Given: Microservicio con HealthIndicators MongoDB, Redis, Kafka, DiskSpace
    // When: GET /actuator/health
    // Then: Status UP con 4 components UP
    
    // When: Detener MongoDB
    // And: Esperar 35s (lease-expiration)
    // Then: Eureka Dashboard muestra instancia DOWN
    
    // When: Restaurar MongoDB
    // And: Esperar 2 heartbeats (20s)
    // Then: Eureka Dashboard muestra instancia UP
}
```

---

## Criterios de Aceptación

- [ ] `/actuator/health` expone 4 components: mongo, redis, kafka, diskSpace
- [ ] Cada component tiene `status: UP/DOWN` y `details` informativos
- [ ] Health agregado determina status UP solo si **TODOS** components UP
- [ ] Eureka usa `/actuator/health` del cliente para marcar UP/DOWN
- [ ] Fallo en cualquier dependency → DOWN → Eureka evicta (dev) o retiene (prod)
- [ ] DiskSpace threshold configurable (default 10MB)

---

## Eureka Server - Su Propio Health Check

El discovery-service también debe exponer su health:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

```bash
curl http://discovery-service:8761/actuator/health
```
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP", "details": {...}},
    "eureka": {"status": "UP"}
  }
}
```

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) |
| **Diagrama Secuencia** | [SEC048_health_indicators.md](../DIAGRAMAS_SECUENCIAS/SEC048_health_indicators.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |
| **Configuración Cliente** | [README.md](../README.md#33-health-check) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |