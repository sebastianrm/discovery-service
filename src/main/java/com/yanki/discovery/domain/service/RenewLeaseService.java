package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.RenewLeaseUseCase;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del caso de uso para renovar el lease de una instancia (heartbeat).
 * <p>
 * Actualiza el timestamp de último heartbeat y mantiene la instancia UP.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewLeaseService implements RenewLeaseUseCase {

    private final EurekaRegistryPort registryPort;

    @Override
    public Completable execute(String instanceId) {
        log.debug("Renovando lease para instancia: {}", instanceId);

        try {
            return registryPort.renewLease(instanceId)
                    .ignoreElement()
                    .doOnComplete(() -> log.debug("Lease renovado exitosamente para: {}", instanceId))
                    .doOnError(error -> log.warn("Error renovando lease para {}: {}",
                            instanceId, error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al renovar lease de {}: {}",
                    instanceId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Maybe<EurekaInstanceInfo> executeAndGet(String instanceId) {
        log.debug("Renovando lease y obteniendo instancia: {}", instanceId);

        try {
            return registryPort.renewLease(instanceId)
                    .doOnSuccess(instance -> {
                        if (instance != null) {
                            log.debug("Lease renovado, instancia: {} (status: {})",
                                    instance.getInstanceId(), instance.getStatus());
                        }
                    })
                    .doOnError(error -> log.warn("Error renovando lease para {}: {}",
                            instanceId, error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al renovar lease de {}: {}",
                    instanceId, e.getMessage(), e);
            throw e;
        }
    }
}