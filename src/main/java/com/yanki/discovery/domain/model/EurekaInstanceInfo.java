package com.yanki.discovery.domain.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Modelo de dominio puro que representa la información de una instancia registrada en Eureka.
 * <p>
 * Contiene los datos esenciales para el registro, descubrimiento y monitoreo de servicios
 * sin dependencias de frameworks ni anotaciones de infraestructura.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class EurekaInstanceInfo {

    /**
     * Identificador único de la instancia (ej. accounts-service:172.20.0.5:8083).
     */
    private final String instanceId;

    /**
     * Nombre de la aplicación/servicio (ej. ACCOUNTS-SERVICE).
     */
    private final String appName;

    /**
     * Hostname del host donde corre la instancia.
     */
    private final String hostName;

    /**
     * Dirección IP del host.
     */
    private final String ipAddress;

    /**
     * Puerto HTTP de la instancia.
     */
    private final int port;

    /**
     * Puerto HTTPS de la instancia (si aplica).
     */
    private final int securePort;

    /**
     * Estado actual de la instancia en Eureka (UP, DOWN, STARTING, OUT_OF_SERVICE).
     */
    private final InstanceStatus status;

    /**
     * Metadata rica de la instancia (version, zone, api.version, canary, weight, etc.).
     */
    private final Map<String, String> metadata;

    /**
     * Timestamp del último heartbeat recibido (epoch millis).
     */
    private final long lastRenewalTimestamp;

    /**
     * Timestamp del registro inicial (epoch millis).
     */
    private final long registrationTimestamp;

    /**
     * Timestamp calculado de expiración del lease (epoch millis).
     */
    private final long evictionTimestamp;

    /**
     * Indica si la instancia está en modo canary deployment.
     *
     * @return true si metadata.canary == "true"
     */
    public boolean isCanary() {
        return "true".equalsIgnoreCase(metadata.getOrDefault("canary", "false"));
    }

    /**
     * Obtiene la zona de disponibilidad de la instancia.
     *
     * @return zona (ej. primary, zone1) o "unknown" si no está definida
     */
    public String getZone() {
        return metadata.getOrDefault("zone", "unknown");
    }

    /**
     * Obtiene la versión de la API expuesta por la instancia.
     *
     * @return versión API (ej. v1, v2) o "v1" por defecto
     */
    public String getApiVersion() {
        return metadata.getOrDefault("api.version", "v1");
    }

    /**
     * Obtiene el peso para weighted load balancing.
     *
     * @return peso (ej. 100, 10) o 100 por defecto
     */
    public int getWeight() {
        try {
            return Integer.parseInt(metadata.getOrDefault("weight", "100"));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    /**
     * Obtiene la versión del servicio.
     *
     * @return versión semántica (ej. 1.0.0) o "unknown"
     */
    public String getVersion() {
        return metadata.getOrDefault("version", "unknown");
    }

    /**
     * Verifica si la instancia está saludable (status UP).
     *
     * @return true si status es UP
     */
    public boolean isHealthy() {
        return status == InstanceStatus.UP;
    }

    /**
     * Verifica si el lease ha expirado basado en el timestamp actual.
     *
     * @param now timestamp actual en epoch millis
     * @return true si evictionTimestamp > 0 y now >= evictionTimestamp
     */
    public boolean isLeaseExpired(long now) {
        return evictionTimestamp > 0 && now >= evictionTimestamp;
    }

    /**
     * Enum que representa los estados posibles de una instancia en Eureka.
     */
    public enum InstanceStatus {
        /** La instancia está operativa y recibiendo tráfico. */
        UP,
        /** La instancia no está operativa (falló health check o lease expiró). */
        DOWN,
        /** La instancia se está iniciando, aún no lista para tráfico. */
        STARTING,
        /** La instancia está fuera de servicio (mantenimiento, drenando conexiones). */
        OUT_OF_SERVICE,
        /** Estado desconocido. */
        UNKNOWN
    }
}