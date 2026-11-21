# CP-173 — Eureka HA Cluster: 3 nodos, replicación registry, failover

> **CU:** CU-18 | **SEC:** SEC046_eureka_ha_cluster.md | **Servicio:** discovery-service (Eureka Server)

---

## Información General

| Campo | Valor |
|-------|-------|
| **ID** | CP-173 |
| **Tipo** | Happy Path (Producción) |
| **Prioridad** | Alta |
| **Estado** | ✅ Definido |
| **Automatizado** | ✅ Integration |

---

## Descripción

Validar la configuración de **Alta Disponibilidad** del Discovery Service con **3 nodos Eureka** en cluster peer-to-peer, replicando el registry entre sí, sincronizando peers, y manteniendo continuidad de servicio ante fallo de uno o dos nodos.

---

## Precondiciones

1. **3 nodos Eureka** desplegados con hostnames únicos:
   - `eureka-1` (puerto 8761)
   - `eureka-2` (puerto 8761)
   - `eureka-3` (puerto 8761)
2. Configuración peer-to-peer mutua en cada nodo:
   ```yaml
   eureka:
     client:
       service-url:
         defaultZone: http://admin:password@eureka-1:8761/eureka/,http://admin:password@eureka-2:8761/eureka/,http://admin:password@eureka-3:8761/eureka/
     server:
       enable-self-preservation: true
       peer-node-read-timeout-ms: 2000
       peer-node-connect-timeout-ms: 2000
   ```
3. Red Docker `yanki-network` para comunicación interna
4. Múltiples microservicios cliente registrados

---

## Pasos de Prueba

| Paso | Acción | Resultado Esperado |
|------|--------|-------------------|
| 1 | Levantar 3 nodos Eureka (`docker-compose up -d discovery-service-1 discovery-service-2 discovery-service-3`) | 3 nodos UP, logs muestran peer sync |
| 2 | Registrar microservicio cliente (ej. accounts-service) | Cliente se registra en uno de los nodos (balanceado) |
| 3 | Verificar réplica: `GET /eureka/apps/ACCOUNTS-SERVICE` en **los 3 nodos** | Instancia visible en **todos los 3 nodos** con mismo InstanceInfo |
| 4 | Verificar peer sync en logs | Logs: "Replicating instance info to peer..." |
| 5 | Caer nodo 1 (`docker stop discovery-service-1`) | Nodos 2 y 3 siguen operativos |
| 6 | Verificar registry en nodos 2 y 3: instancias siguen visibles | Registry intacto en nodos supervivientes |
| 7 | Gateway fetch registry desde nodo disponible → continuidad | Gateway resuelve `lb://accounts-service` sin interrupción |
| 8 | Levantar nodo 1 nuevamente | Nodo 1 se sincroniza con peers (fetch registry) |
| 9 | Verificar consistencia: 3 nodos con registry idéntico | Todos los nodos muestran mismas instancias UP |

---

## Arquitectura HA (3 Nodos Peer-to-Peer)

```
                    ┌─────────────────┐
                    │   eureka-1      │
                    │   :8761         │
                    └────────┬────────┘
                             │ peer sync
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌───────────┐  ┌───────────┐  ┌───────────┐
        │ eureka-2  │  │ eureka-3  │  │  Clientes │
        │ :8761     │  │ :8761     │  │  Eureka   │
        └───────────┘  └───────────┘  └───────────┘
              ▲              ▲
              └──────────────┘
                   peer sync
```

---

## Validaciones Técnicas

### Configuración Peer (cada nodo)
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://admin:password@eureka-1:8761/eureka/,http://admin:password@eureka-2:8761/eureka/,http://admin:password@eureka-3:8761/eureka/
  server:
    enable-self-preservation: true
    peer-node-read-timeout-ms: 2000
    peer-node-connect-timeout-ms: 2000
```

### Logs Esperados - Peer Sync
```
PeerEurekaNodes - Adding peer node eureka-2:8761
PeerEurekaNodes - Adding peer node eureka-3:8761
ReplicationTaskProcessor - Replicating instance info to peer eureka-2
ReplicationTaskProcessor - Replicating instance info to peer eureka-3
```

### Actuator Eureka Endpoint (cada nodo)
```bash
curl -u admin:password http://eureka-1:8761/actuator/eureka
```
```json
{
  "peers": ["http://eureka-2:8761/eureka/", "http://eureka-3:8761/eureka/"],
  "selfPreservationMode": true,
  "registrySize": 10
}
```

---

## Validaciones Automatizadas (Testcontainers)

```java
@Test
void haCluster3NodosReplicaRegistryYFailover() {
    // Given: 3 contenedores Eureka en red compartida con config peer-to-peer
    // When: Registrar servicio cliente
    // Then: Instancia visible en /eureka/apps en LOS 3 NODOS
    
    // When: Detener nodo 1
    // Then: Nodos 2 y 3 siguen sirviendo registry completo
    // And: Gateway fetch desde nodo 2/3 → resuelve correctamente
    
    // When: Levantar nodo 1
    // Then: Nodo 1 sincroniza registry con peers
    // And: 3 nodos con registry idéntico
}
```

---

## Criterios de Aceptación

- [ ] 3 nodos Eureka inician y descubren peers mutuamente
- [ ] Registro en un nodo → replicado a los otros 2 en < 5s
- [ ] Peer sync logs visibles: "Replicating instance info to peer..."
- [ ] Caída de 1 nodo → 2 nodos restantes operativos sin pérdida de registry
- [ ] Caída de 2 nodos → 1 nodo restante operativo (quorum)
- [ ] Gateway fetch registry desde nodo disponible → continuidad servicio
- [ ] Recuperación nodo caído → auto-sync con peers
- [ ] Actuator `/actuator/eureka` muestra peers y registrySize consistente
- [ ] Self-preservation `true` en producción

---

## Docker Compose HA (Referencia)

```yaml
services:
  discovery-service-1:
    build: ./discovery-service
    hostname: eureka-1
    environment:
      EUREKA_HOSTNAME: eureka-1
      EUREKA_PASSWORD: ${EUREKA_PASSWORD}
    networks: [yanki-network]
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8761/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  discovery-service-2:
    build: ./discovery-service
    hostname: eureka-2
    environment:
      EUREKA_HOSTNAME: eureka-2
      EUREKA_PASSWORD: ${EUREKA_PASSWORD}
    networks: [yanki-network]

  discovery-service-3:
    build: ./discovery-service
    hostname: eureka-3
    environment:
      EUREKA_HOSTNAME: eureka-3
      EUREKA_PASSWORD: ${EUREKA_PASSWORD}
    networks: [yanki-network]

networks:
  yanki-network:
    driver: bridge
```

---

## Trazabilidad

| Artefacto | Referencia |
|-----------|------------|
| **Caso de Uso** | [CU-18](../CASOS_USO/CU-18-Gestion-Discovery-Service.md) |
| **Diagrama Secuencia** | [SEC046_eureka_ha_cluster.md](../DIAGRAMAS_SECUENCIAS/SEC046_eureka_ha_cluster.md) |
| **RTM** | [RTM.md](../CASOS_PRUEBA/RTM.md#cu-18) |
| **Matriz Por Servicio** | [TRACEABILIDAD_POR_SERVICIO.md](../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md#10-discovery-service--eureka-server-puerto-8761) |
| **README Servicio** | [README.md](../README.md#6-alta-disponibilidad-producción---cluster-3-nodos) |

---

## Historial

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0 | Sistema | Creación inicial desde CASOS_PRUEBA_QA.md |