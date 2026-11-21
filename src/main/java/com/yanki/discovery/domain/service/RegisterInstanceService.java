package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.event.InstanceRegisteredEvent;
import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.RegisterInstanceUseCase;
import com.yanki.discovery.domain.port.out.EventPublisherPort;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import com.yanki.discovery.domain.port.out.PeerSyncPort;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del caso de uso para registrar una instancia en Eureka.
 * <p>
 * Orquesta:
 * <ol>
 *   <li>Persistencia en registry via {@link EurekaRegistryPort}</li>
 *   <li>Publicación de evento de dominio via {@link EventPublisherPort}</li>
 *   <li>Replicación a peers en HA cluster via {@link PeerSyncPort}</li>
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
public class RegisterInstanceService implements RegisterInstanceUseCase {

    private final EurekaRegistryPort registryPort;
    private final EventPublisherPort eventPublisherPort;
    private final PeerSyncPort peerSyncPort;

    @Override
    public Completable execute(EurekaInstanceInfo instanceInfo) {
        log.info("Registrando instancia: {} ({}) - host: {}:{} - status: {}",
                instanceInfo.getInstanceId(), instanceInfo.getAppName(),
                instanceInfo.getHostName(), instanceInfo.getPort(), instanceInfo.getStatus());

        try {
            return registryPort.registerInstance(instanceInfo)
                    .doOnComplete(() -> log.debug("Instancia persistida en registry local: {}",
                            instanceInfo.getInstanceId()))
                    .doOnError(error -> log.error("Error al persistir instancia {} en registry: {}",
                            instanceInfo.getInstanceId(), error.getMessage(), error))
                    .andThen(publishAndReplicate(instanceInfo));
        } catch (Exception e) {
            log.error("Excepcion inesperada al registrar instancia {}: {}",
                    instanceInfo.getInstanceId(), e.getMessage(), e);
            throw e;
        }
    }

    private Completable publishAndReplicate(EurekaInstanceInfo instanceInfo) {
        // Publicar evento de dominio
        InstanceRegisteredEvent event = InstanceRegisteredEvent.from(instanceInfo);
        Completable publishEvent = eventPublisherPort.publishInstanceRegistered(event)
                .doOnComplete(() -> log.debug("Evento InstanceRegisteredEvent publicado para: {}",
                        instanceInfo.getInstanceId()))
                .onErrorResumeNext(error -> {
                    log.error("Error publicando evento de registro para {}: {}",
                            instanceInfo.getInstanceId(), error.getMessage(), error);
                    return Completable.complete(); // No fallar el registro por error de evento
                });

        // Replicar a peers (HA cluster)
        Completable replicateToPeers = peerSyncPort.getPeerUrls()
                .flatMapCompletable(peerUrls -> {
                    if (peerUrls.isEmpty()) {
                        log.debug("Sin peers configurados, omitiendo replicacion");
                        return Completable.complete();
                    }
                    log.info("Replicando registro a {} peers para instancia: {}", peerUrls.size(), instanceInfo.getInstanceId());
                    return peerSyncPort.replicateToPeers(peerUrls, instanceInfo.getInstanceId())
                            .doOnComplete(() -> log.info("Replicacion a peers completada para: {}",
                                    instanceInfo.getInstanceId()))
                            .onErrorResumeNext(error -> {
                                log.warn("Error replicando a peers para {}: {}",
                                        instanceInfo.getInstanceId(), error.getMessage(), error);
                                return Completable.complete(); // No fallar por error de replicacion
                            });
                });

        return publishEvent.andThen(replicateToPeers);
    }
}