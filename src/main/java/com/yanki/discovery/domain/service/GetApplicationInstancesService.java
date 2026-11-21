package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.GetApplicationInstancesUseCase;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del caso de uso para obtener instancias de una aplicación.
 * <p>
 * Orquesta el puerto de salida {@link EurekaRegistryPort} para consultar
 * instancias registradas, con filtro opcional solo healthy (UP).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetApplicationInstancesService implements GetApplicationInstancesUseCase {

    private final EurekaRegistryPort registryPort;

    @Override
    public Single<List<EurekaInstanceInfo>> execute(String appName) {
        log.debug("Obteniendo instancias de aplicacion: {}", appName);

        try {
            return registryPort.getInstances(appName.toUpperCase())
                    .doOnSuccess(instances -> log.info("Encontradas {} instancias para {}",
                            instances.size(), appName))
                    .doOnError(error -> log.error("Error obteniendo instancias de {}: {}",
                            appName, error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al obtener instancias de {}: {}",
                    appName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Single<List<EurekaInstanceInfo>> executeHealthyOnly(String appName) {
        log.debug("Obteniendo instancias UP de aplicacion: {}", appName);

        try {
            return registryPort.getInstances(appName.toUpperCase())
                    .map(instances -> instances.stream()
                            .filter(EurekaInstanceInfo::isHealthy)
                            .toList())
                    .doOnSuccess(instances -> {
                        int total = instances.size();
                        if (total == 0) {
                            log.warn("No hay instancias UP para {}", appName);
                        } else {
                            log.info("Encontradas {} instancias UP para {}", total, appName);
                        }
                    })
                    .doOnError(error -> log.error("Error obteniendo instancias UP de {}: {}",
                            appName, error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al obtener instancias UP de {}: {}",
                    appName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Maybe<EurekaInstanceInfo> findByInstanceId(String instanceId) {
        log.debug("Buscando instancia por ID: {}", instanceId);

        try {
            return registryPort.findByInstanceId(instanceId)
                    .doOnSuccess(instance -> {
                        if (instance != null) {
                            log.debug("Instancia encontrada: {} (status: {})",
                                    instance.getInstanceId(), instance.getStatus());
                        } else {
                            log.warn("Instancia no encontrada: {}", instanceId);
                        }
                    })
                    .doOnError(error -> log.error("Error buscando instancia {}: {}",
                            instanceId, error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al buscar instancia {}: {}",
                    instanceId, e.getMessage(), e);
            throw e;
        }
    }
}