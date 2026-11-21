# CP-172 — Eureka Dashboard Security: Basic Auth obligatorio

> **CU:** CU-18 | **SEC:** SEC047_eureka_dashboard_security.md | **Servicio:** discovery-service (Eureka Server)

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-172 |
| **Tipo** | Happy Path / Seguridad |
| **Prioridad** | Crítica (Producción) |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Integration |

---

## Descripción

Validar que el Dashboard de Eureka (`/`) está protegido con **Spring Security Basic Auth** obligatoria en producción, requiriendo credenciales `EUREKA_USER` / `EUREKA_PASSWORD` (sin default inseguro en prod). En desarrollo puede estar desprotegido.

---

## Precondiciones

1. **Eureka Server** UP con Spring Security configurado:
   ```yaml
   spring:
     security:
       user:
         name: ${EUREKA_USER:admin}
         password: ${EUREKA_PASSWORD:}  # Sin default en prod
   ```
2. Variables de entorno inyectadas (Docker secrets, K8s secrets, Vault):
   - `EUREKA_USER=admin`
   - `EUREKA_PASSWORD=<secret-seguro>`

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Acceder a Dashboard sin autenticación: `GET http://discovery-service:8761/` | HTTP `401 Unauthorized` + header `WWW-Authenticate: Basic realm="Eureka Dashboard"` |
| 2 | Acceder con credenciales inválidas: `GET -u admin:wrong http://discovery-service:8761/` | HTTP `401 Unauthorized` |
| 3 | Acceder con credenciales válidas: `GET -u admin:password http://discovery-service:8761/` | HTTP `200 OK` + HTML Dashboard Eureka |
| 4 | Verificar Dashboard solo lectura: no hay botones de acción (deregister, etc.) | UI de solo lectura para operadores |
| 5 | Acceder a API Registry SIN auth (clientes Eureka): `GET http://discovery-service:8761/eureka/apps` | HTTP `200 OK` (registry accesible para clientes) |
| 6 | Verificar HTTPS en producción (terminación en Gateway/Ingress) | Dashboard solo accesible via HTTPS |

---

## Validaciones Técnicas

### Sin autenticación → 401
```bash
curl -i http://discovery-service:8761/
```
```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic realm="Eureka Dashboard"
Content-Type: text/html;charset=utf-8
```

### Con credenciales válidas → 200
```bash
curl -u admin:password http://discovery-service:8761/
```
```
HTTP/1.1 200 OK
Content-Type: text/html;charset=utf-8
<!DOCTYPE html><html>...Eureka Dashboard...
```

### Registry API accesible para clientes (sin auth en dev, con auth opcional en prod)
```bash
curl -i http://discovery-service:8761/eureka/apps
```
```
HTTP/1.1 200 OK
Content-Type: application/json
{"applications": {"application": [...]}}
```

---

## Configuración Spring Security (Referencia)

```java
@Configuration
@EnableWebSecurity
public class EurekaSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/eureka/**").permitAll()  // Registry API para clientes
                .requestMatchers("/actuator/**").permitAll() // Health checks
                .requestMatchers("/").hasRole("OPERATOR")   // Dashboard protegido
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());  // Eureka protocol no usa CSRF
        return http.build();
    }
}
```

### application.yml (Producción)
```yaml
spring:
  security:
    user:
      name: ${EUREKA_USER}           # Obligatorio, sin default
      password: ${EUREKA_PASSWORD}   # Obligatorio, sin default
      roles: OPERATOR
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void dashboardRequiereBasicAuthEnProduccion() {
    // Given: Eureka con spring.security.user.name/password configurados
    // When: GET / sin auth
    // Then: 401 + WWW-Authenticate header
    
    // When: GET / con credenciales inválidas
    // Then: 401
    
    // When: GET / con credenciales válidas
    // Then: 200 + HTML Dashboard
    
    // When: GET /eureka/apps sin auth (clientes Eureka)
    // Then: 200 (registry accesible)
}
```

---

## Criterios de Aceptación

- [ ] Dashboard `/` retorna `401` sin credenciales
- [ ] Header `WWW-Authenticate: Basic realm="Eureka Dashboard"` presente
- [ ] Credenciales válidas → `200` + HTML Dashboard
- [ ] Credenciales inválidas → `401`
- [ ] Registry API `/eureka/apps` accesible para clientes Eureka (sin auth o auth separada)
- [ ] Actuator endpoints (`/actuator/health`, `/actuator/info`) accesibles sin auth
- [ ] En producción: HTTPS obligatorio (terminación en Gateway/Ingress)
- [ ] Password inyectado como secret (no en código, no default inseguro)

---

## Diferencia Dev vs Prod

| Aspecto | Desarrollo | Producción |
|---------|------------|------------|
| `spring.security.user.password` | `changeit` (default) | **Secret inyectado, sin default** |
| Dashboard auth | Opcional | **Obligatorio** |
| Registry API auth | Sin auth | Opcional (mTLS recomendado) |
| HTTPS | No requerido | **Obligatorio** (Gateway termination) |

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) |
| **Diagrama Secuencia** | [SEC047_eureka_dashboard_security.md](../DIAGRAMAS_SECUENCIAS/SEC047_eureka_dashboard_security.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |
| **README Servicio** | [README.md](../README.md#11-seguridad) |
| **AGENTS Servicio** | [AGENTS.md](../AGENTS.md##security) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |