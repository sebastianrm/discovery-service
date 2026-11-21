package com.yanki.discovery.domain.port.out;

import io.reactivex.rxjava3.core.Single;
import java.util.Map;

/**
 * Puerto de salida para obtener configuración externalizada del Config Server.
 * <p>
 * Define el contrato para leer configuración de Eureka Server desde Spring Cloud Config Server,
 * con soporte para fail-fast, retry y fallback a configuración local.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface ConfigPort {

    /**
     * Obtiene la configuración completa de Eureka Server.
     *
     * @return Single con mapa de propiedades de configuración
     */
    Single<Map<String, Object>> getEurekaServerConfig();

    /**
     * Obtiene una propiedad específica de configuración.
     *
     * @param key clave de la propiedad
     * @return Single con el valor (puede ser vacío si no existe)
     */
    Single<java.util.Optional<Object>> getProperty(String key);

    /**
     * Verifica si el Config Server está accesible.
     *
     * @return Single con true si Config Server responde
     */
    Single<Boolean> isConfigServerAvailable();

    /**
     * Fuerza la recarga de configuración (refresh).
     *
     * @return Completable que completa cuando el refresh termina
     */
    io.reactivex.rxjava3.core.Completable refreshConfig();
}