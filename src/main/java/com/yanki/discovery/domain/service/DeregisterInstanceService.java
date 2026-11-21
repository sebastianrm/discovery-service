package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.event.InstanceDeregisteredEvent;
import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.DeregisterInstanceUseCase;
import com.yanki.discovery.domain.port.out.EventPublisherPort;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import com.yanki.discovery.domain.port.out.PeerSyncPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del caso de uso para desregistrar una instancia de Eureka.
 * <p>
 * Orquesta:
 * <ol>
 *   <li>Obtiene la instancia antes de eliminar (para evento)</li>
 *   <li>Elimina del registry via {@link EurekaRegistryPort}</li>
 *   <li>Publica evento de dominio via {@link EventPublisherPort}</li>
 *   <li>Replica desregistro a peers via {@link PeerSyncPort}</li>
 * </ol>
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeregisterInstanceService implements DeregisterInstanceUseCase {

    private final EurekaRegistryPort registryPort;
    private final EventPublisherPort eventPublisherPort;
    private final PeerSyncPort peerSyncPort;

    @Override
    public Completable execute(String instanceId) {
        log.info("Desregistrando instancia: {}", instanceId);

        try {
            // Obtener instancia antes de eliminar para el evento
            return registryPort.findByInstanceId(instanceId)
                    .flatMapCompletable(instance -> {
                        // instance es EurekaInstanceInfo directamente (Maybe emite el valor o empty)
                        if (instance == null) {
                            log.warn("Intento de desregistrar instancia inexistente: {}", instanceId);
                            return Completable.complete(); // Idempotente: no fallar si ya no existe
                        }

                        String appName = instance.getAppName();
                        log.debug("Instancia encontrada para desregistro: {} (app: {})", instanceId, appName);

                        return registryPort.deregisterInstance(instanceId)
                                .doOnComplete(() -> log.info("Instancia eliminada del registry: {}",
                                        instanceId))
                                .doOnError(error -> log.error("Error al eliminar instancia {} del registry: {}",
                                        instanceId, error.getMessage(), error))
                                .andThen(publishAndReplicateDeregistration(instance, appName));
                    })
                    .onErrorResumeNext(error -> {
                        log.warn("Instancia no encontrada para desregistro (idempotente): {}", instanceId);
                        return Completable.complete();
                    })
                    .doOnError(error -> log.error("Error desregistrando instancia {}: {}",
                            instanceId, error.getMessage(), error));
        } catch (Exception e) {
            log.error("Excepcion inesperada al desregistrar instancia {}: {}",
                    instanceId, e.getMessage(), e);
            throw e;
        }
    }

    private Completable publishAndReplicateDeregistration(EurekaInstanceInfo instanceInfo, String appName) {
        // Publicar evento de dominio
        InstanceDeregisteredEvent event = InstanceDeregisteredEvent.from(instanceInfo, "SHUTDOWN");
        Completable publishEvent = eventPublisherPort.publishInstanceDeregistered(event)
                .doOnComplete(() -> log.info("Evento InstanceDeregisteredEvent publicado para: {}",
                        instanceInfo.getInstanceId()))
                .onErrorResumeNext(error -> {
                    log.error("Error publicando evento de desregistro para {}: {}",
                            instanceInfo.getInstanceId(), error.getMessage(), error);
                    return Completable.complete();
                });

        // Replicar a peers
        Completable replicateToPeers = peerSyncPort.getPeerUrls()
                .flatMapCompletable(peerUrls -> {
                    if (peerUrls.isEmpty()) {
                        log.debug("Sin peers configurados, omitiendo replicacion de desregistro");
                        return Completable.complete();
                    }
                    log.info("Replicando desregistro a {} peers para instancia: {}", peerUrls.size(), instanceInfo.getInstanceId());
                    return peerSyncPort.replicateDeregistrationToPeers(peerUrls, instanceInfo.getInstanceId())
                            .doOnComplete(() -> log.info("Replicacion desregistro a peers completada para: {}",
                                    instanceInfo.getInstanceId()))
                            .onErrorResumeNext(error -> {
                                log.warn("Error replicando desregistro a peers para {}: {}",
                                        instanceInfo.getInstanceId(), error.getMessage(), error);
                                return Completable.complete();
                            });
                });

        return publishEvent.andThen(replicateToPeers);
    }
}