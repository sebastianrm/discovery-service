package com.yanki.discovery.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Modelo de dominio puro que representa el estado completo del registro de Eureka.
 * <p>
 * Agrega todas las aplicaciones e instancias registradas, permitiendo consultas
 * de descubrimiento y monitoreo del estado global del cluster.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class EurekaRegistry {

    /**
     * Mapa de aplicaciones registradas (clave: appName, valor: lista de instancias).
     */
    private final Map<String, Collection<EurekaInstanceInfo>> applications;

    /**
     * Timestamp de la última actualización del registry (epoch millis).
     */
    private final long lastUpdatedTimestamp;

    /**
     * Indica si el modo self-preservation está activo.
     */
    private final boolean selfPreservationMode;

    /**
     * Número total de instancias registradas en todo el registry.
     */
    private final int totalInstances;

    /**
     * Número de instancias con status UP.
     */
    private final int instancesUp;

    /**
     * Número de instancias con status DOWN.
     */
    private final int instancesDown;

    /**
     * Crea un registry vacío.
     *
     * @return EurekaRegistry vacío
     */
    public static EurekaRegistry empty() {
        return EurekaRegistry.builder()
                .applications(Collections.emptyMap())
                .lastUpdatedTimestamp(Instant.now().toEpochMilli())
                .selfPreservationMode(false)
                .totalInstances(0)
                .instancesUp(0)
                .instancesDown(0)
                .build();
    }

    /**
     * Obtiene las instancias de una aplicación específica.
     *
     * @param appName nombre de la aplicación (ej. ACCOUNTS-SERVICE)
     * @return colección de instancias (vacía si no existe)
     */
    public Collection<EurekaInstanceInfo> getInstances(String appName) {
        return applications.getOrDefault(appName.toUpperCase(), Collections.emptyList());
    }

    /**
     * Obtiene solo las instancias UP de una aplicación.
     *
     * @param appName nombre de la aplicación
     * @return colección filtrada de instancias UP
     */
    public Collection<EurekaInstanceInfo> getHealthyInstances(String appName) {
        return getInstances(appName).stream()
                .filter(EurekaInstanceInfo::isHealthy)
                .toList();
    }

    /**
     * Busca una instancia por su instanceId en todo el registry.
     *
     * @param instanceId identificador único de la instancia
     * @return Optional con la instancia si existe
     */
    public Optional<EurekaInstanceInfo> findInstanceById(String instanceId) {
        return applications.values().stream()
                .flatMap(Collection::stream)
                .filter(inst -> inst.getInstanceId().equals(instanceId))
                .findFirst();
    }

    /**
     * Obtiene todas las aplicaciones registradas.
     *
     * @return colección de nombres de aplicaciones
     */
    public Collection<String> getApplicationNames() {
        return Collections.unmodifiableCollection(applications.keySet());
    }

    /**
     * Obtiene un mapa con el conteo de instancias por aplicación.
     *
     * @return mapa appName -> total instancias
     */
    public Map<String, Integer> getInstanceCountPerApp() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        applications.forEach((app, instances) -> counts.put(app, instances.size()));
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Verifica si el registry está vacío (sin instancias registradas).
     *
     * @return true si totalInstances == 0
     */
    public boolean isEmpty() {
        return totalInstances == 0;
    }

    /**
     * Obtiene el estado general del registry basado en las instancias.
     *
     * @return "UP" si hay instancias UP, "DOWN" si todas están DOWN, "OUT_OF_SERVICE" si está vacío
     */
    public String getStatus() {
        if (totalInstances == 0) {
            return "OUT_OF_SERVICE";
        }
        return instancesDown == totalInstances ? "DOWN" : "UP";
    }
}