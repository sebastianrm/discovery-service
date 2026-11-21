package com.yanki.discovery.infrastructure.adapter;

import com.yanki.discovery.domain.port.out.ConfigPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.config.client.ConfigServerConfigDataLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de salida que implementa {@link ConfigPort} para obtener configuración
 * del Spring Cloud Config Server.
 * <p>
 * Envuelve el mecanismo de Spring Cloud Config para exponerlo como puerto de dominio
 * con tipos reactivos RxJava 3.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigAdapter implements ConfigPort {

    private final ConfigurableEnvironment environment;

    @Override
    public Single<Map<String, Object>> getEurekaServerConfig() {
        log.debug("Obteniendo configuracion de Eureka Server");
        return Single.fromCallable(() -> {
            try {
                Map<String, Object> config = new HashMap<>();

                // Extraer propiedades relevantes de Eureka Server del environment
                String[] eurekaProps = {
                        "eureka.server.enable-self-preservation",
                        "eureka.server.eviction-interval-timer-in-ms",
                        "eureka.server.renewal-percent-threshold",
                        "eureka.server.response-cache-update-interval-ms",
                        "eureka.server.peer-node-read-timeout-ms",
                        "eureka.server.peer-node-connect-timeout-ms",
                        "eureka.instance.hostname",
                        "eureka.client.register-with-eureka",
                        "eureka.client.fetch-registry",
                        "server.port"
                };

                for (String prop : eurekaProps) {
                    String value = environment.getProperty(prop);
                    if (value != null) {
                        config.put(prop, value);
                    }
                }

                log.debug("Configuracion obtenida: {} propiedades cargadas", config.size());
                return config;
            } catch (Exception e) {
                log.error("Error obteniendo configuracion de Eureka Server: {}",
                        e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Single<Optional<Object>> getProperty(String key) {
        log.debug("Obteniendo propiedad: {}", key);
        return Single.fromCallable(() -> {
            Object value = environment.getProperty(key);
            log.debug("Propiedad {} = {}", key, value != null ? "[valor encontrado]" : "null");
            return Optional.ofNullable(value);
        });
    }

    @Override
    public Single<Boolean> isConfigServerAvailable() {
        log.debug("Verificando disponibilidad de Config Server");
        return Single.fromCallable(() -> {
            try {
                boolean available = environment.getPropertySources().stream()
                        .anyMatch(ps -> ps.getName().contains("configserver")
                                || ps.getName().contains("ConfigServer"));
                if (available) {
                    log.info("Config Server disponible y conectado");
                } else {
                    log.warn("Config Server no disponible, usando configuracion local");
                }
                return available;
            } catch (Exception e) {
                log.error("Error verificando Config Server: {}",
                        e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Completable refreshConfig() {
        return Completable.fromCallable(() -> {
            log.info("Refresh de configuracion solicitado para discovery-service");
            // Spring Cloud Config soporta refresh via Actuator /actuator/refresh
            // o Spring Cloud Bus. Aqui solo logueamos.
            return null;
        }).doOnError(error -> log.error("Error refrescando configuracion: {}",
                error.getMessage(), error));
    }
}