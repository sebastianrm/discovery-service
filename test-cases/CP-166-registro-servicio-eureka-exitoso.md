# CP-166 — Registro de servicio en Eureka exitoso (Startup)

> **CU:** CU-18 | **SEC:** SEC021_registro_servicio.md | **Servicio:** discovery-service (Eureka Server)

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-166 |
| **Tipo** | Happy Path |
| **Prioridad** | Alta |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Unit + Integration |

---

## Descripción

Validar que un microservicio cliente Eureka se registre correctamente en el Discovery Service al arrancar, obteniendo configuración del Config Server, enviando InstanceInfo completa con metadata, y apareciendo UP en el Dashboard de Eureka.

---

## Precondiciones

1. **Eureka Server (discovery-service)** UP en puerto 8761
2. **Config Server** accesible en puerto 8888
3. Credenciales Eureka configuradas: `EUREKA_USER`, `EUREKA_PASSWORD`
4. Microservicio cliente (ej. accounts-service) con dependencias:
   - `spring-cloud-starter-netflix-eureka-client`
   - `spring-boot-starter-actuator`
   - `spring-cloud-starter-config`

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Iniciar microservicio (ej. accounts-service) | Arranque sin errores |
| 2 | Bootstrap: fetch config desde Config Server (`GET /accounts-service/default`) | Configuración resuelta correctamente (eureka.url, credentials) |
| 3 | Registro: Cliente envía `POST /eureka/apps/ACCOUNTS-SERVICE` con InstanceInfo + metadata | HTTP `204 No Content` |
| 4 | Verificar payload del registro contiene: `instance-id`, `hostName`, `ipAddr`, `port`, `securePort`, `metadata` (version, zone, api.version, canary, deployment.environment, management.port) | Metadata completa presente |
| 5 | Verificar en Dashboard Eureka (`GET /` o `/eureka/apps/ACCOUNTS-SERVICE`): instancia aparece **UP** con metadata completa | Instancia visible, status UP, metadata expuesta |
| 6 | Verificar Heartbeat: `PUT /eureka/apps/ACCOUNTS-SERVICE/{instanceId}` cada 10s | Lease renovado, `lastRenewal` actualizado |

---

## Postcondiciones

- Servicio registrado en Eureka Registry
- Instancia visible en Dashboard con status UP
- Metadata completa expuesta (version, zone, api.version, canary, deployment.environment, management.port)
- Heartbeat renovando lease cada 10s

---

## Datos de Prueba

```yaml
# Configuración cliente esperada (accounts-service.yml en Config Server)
spring:
  application:
    name: accounts-service
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true

eureka:
  client:
    service-url:
      defaultZone: http://admin:${EUREKA_PASSWORD}@discovery-service:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
    registry-fetch-interval-seconds: 30
    healthcheck:
      enabled: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    metadata-map:
      version: 1.0.0
      zone: primary
      management.port: 8083
      api.version: v1
      deployment.environment: docker
      canary: "false"
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class ClientRegistrationTest {

    @Container
    static GenericContainer<?> eureka = new GenericContainer<>("yanki/discovery-service:1.0.0")
        .withExposedPorts(8761)
        .withEnv("EUREKA_PASSWORD", "changeit");

    @Container
    static GenericContainer<?> configServer = new GenericContainer<>("yanki/config-server:1.0.0")
        .withExposedPorts(8888);

    @Test
    void servicioSeRegistraEnEurekaConMetadataCompleta() {
        // Given: Eureka + Config Server UP
        // When: Iniciar cliente Eureka (accounts-service)
        // Then: Verificar registro en /eureka/apps/ACCOUNTS-SERVICE
        // - Status 204 en POST register
        // - Instancia UP en dashboard
        // - Metadata: version, zone, api.version, canary, management.port presentes
    }
}
```

---

## Criterios de Aceptación

- [ ] Registro POST retorna `204 No Content`
- [ ] Instancia aparece UP en Dashboard Eureka
- [ ] Metadata completa presente: `version`, `zone`, `api.version`, `canary`, `deployment.environment`, `management.port`
- [ ] Heartbeat cada 10s renueva lease correctamente
- [ ] Instancia accesible via `GET /eureka/apps/ACCOUNTS-SERVICE`

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) |
| **Diagrama Secuencia** | [SEC021_registro_servicio.md](../DIAGRAMAS_SECUENCIAS/SEC021_registro_servicio.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |
| **README Servicio** | [README.md](../README.md#3-cliente-servicios-consumidores--11-microservicios) |
| **AGENTS Servicio** | [AGENTS.md](../AGENTS.md##testing-strategy) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |