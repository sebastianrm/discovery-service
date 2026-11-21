package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.in.GetEurekaHealthUseCase;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del caso de uso para health check del Eureka Server.
 * <p>
 * Retorna el registry actual que incluye métricas de salud (instancias UP/DOWN,
 * self-preservation mode, timestamp última actualización).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetEurekaHealthService implements GetEurekaHealthUseCase {

    private final EurekaRegistryPort registryPort;

    @Override
    public Single<EurekaRegistry> execute() {
        log.debug("Health check de Eureka Server solicitado");

        try {
            return registryPort.getRegistry()
                    .doOnSuccess(registry -> {
                        log.info("Health check: {} instancias UP, {} DOWN, self-preservation: {}",
                                registry.getInstancesUp(), registry.getInstancesDown(),
                                registry.isSelfPreservationMode());
                        if (registry.isSelfPreservationMode()) {
                            log.warn("Eureka Server en modo self-preservation activo");
                        }
                    })
                    .doOnError(error -> log.error("Error en health check: {}",
                            error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada en health check: {}",
                    e.getMessage(), e);
            throw e;
        }
    }
}