package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.in.GetRegistryUseCase;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del caso de uso para obtener el registry completo de Eureka.
 * <p>
 * Orquesta el puerto de salida {@link EurekaRegistryPort} para obtener
 * el estado actual del registry sin lógica de negocio adicional.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetRegistryService implements GetRegistryUseCase {

    private final EurekaRegistryPort registryPort;

    @Override
    public Single<EurekaRegistry> execute() {
        log.debug("Obteniendo registry completo de Eureka");

        try {
            return registryPort.getRegistry()
                    .doOnSuccess(registry -> {
                        log.info("Registry obtenido: {} aplicaciones, {} instancias totales ({} UP, {} DOWN), self-preservation: {}",
                                registry.getApplicationNames().size(),
                                registry.getTotalInstances(),
                                registry.getInstancesUp(),
                                registry.getInstancesDown(),
                                registry.isSelfPreservationMode());
                    })
                    .doOnError(error -> log.error("Error obteniendo registry: {}",
                            error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al obtener registry: {}",
                    e.getMessage(), e);
            throw e;
        }
    }
}