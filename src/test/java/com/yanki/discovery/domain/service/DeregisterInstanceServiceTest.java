package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.event.InstanceDeregisteredEvent;
import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.out.EventPublisherPort;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import com.yanki.discovery.domain.port.out.PeerSyncPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link DeregisterInstanceService} - CP-166, CP-168.
 * <p>
 * Valida: Desregistro graceful, publicación evento, replicación a peers.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class DeregisterInstanceServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    @Mock
    private EventPublisherPort eventPublisherPort;

    @Mock
    private PeerSyncPort peerSyncPort;

    private DeregisterInstanceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DeregisterInstanceService(registryPort, eventPublisherPort, peerSyncPort);
    }

    @Test
    @DisplayName("CP-166/CP-168: Debe desregistrar instancia, publicar evento y replicar a peers")
    void debeDesregistrarInstanciaPublicarEventoYReplicar() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        EurekaInstanceInfo instanceInfo = buildInstanceInfo(instanceId);

        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(instanceInfo));
        when(registryPort.deregisterInstance(instanceId)).thenReturn(Completable.complete());
        when(eventPublisherPort.publishInstanceDeregistered(ArgumentMatchers.any(InstanceDeregisteredEvent.class)))
                .thenReturn(Completable.complete());
        when(peerSyncPort.getPeerUrls()).thenReturn(Single.just(List.of("http://peer1:8761/eureka/", "http://peer2:8761/eureka/")));
        when(peerSyncPort.replicateDeregistrationToPeers(anyList(), anyString())).thenReturn(Completable.complete());

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .verifyComplete();

        // Verificar interacciones
        verify(registryPort).findByInstanceId(instanceId);
        verify(registryPort).deregisterInstance(instanceId);
        verify(eventPublisherPort).publishInstanceDeregistered(ArgumentMatchers.any(InstanceDeregisteredEvent.class));
        verify(peerSyncPort).getPeerUrls();
        verify(peerSyncPort).replicateDeregistrationToPeers(
                List.of("http://peer1:8761/eureka/", "http://peer2:8761/eureka/"),
                instanceId
        );
    }

    @Test
    @DisplayName("CP-168: Debe completar idempotentemente si instancia no existe")
    void debeCompletarIdempotentementeSiInstanciaNoExiste() {
        // Given
        String instanceId = "non-existent:127.0.0.1:8080";
        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.empty());

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-166: Debe completar aunque fallen evento y replicación (resiliente)")
    void debeCompletarAunqueFallenEventoYReplicacion() {
        // Given
        String instanceId = "test-service:localhost:8080";
        EurekaInstanceInfo instanceInfo = buildInstanceInfo(instanceId);

        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(instanceInfo));
        when(registryPort.deregisterInstance(instanceId)).thenReturn(Completable.complete());
        when(eventPublisherPort.publishInstanceDeregistered(ArgumentMatchers.any()))
                .thenReturn(Completable.error(new RuntimeException("Kafka down")));
        when(peerSyncPort.getPeerUrls()).thenReturn(Single.just(List.of("http://peer:8761/eureka/")));
        when(peerSyncPort.replicateDeregistrationToPeers(anyList(), anyString()))
                .thenReturn(Completable.error(new RuntimeException("Peer unreachable")));

        // When & Then - No debe fallar, debe completar
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .verifyComplete();

        verify(eventPublisherPort).publishInstanceDeregistered(ArgumentMatchers.any());
        verify(peerSyncPort).replicateDeregistrationToPeers(anyList(), anyString());
    }

    @Test
    @DisplayName("CP-166: Debe publicar evento con motivo SHUTDOWN")
    void debePublicarEventoConMotivoShutdown() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        EurekaInstanceInfo instanceInfo = buildInstanceInfo(instanceId);

        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(instanceInfo));
        when(registryPort.deregisterInstance(instanceId)).thenReturn(Completable.complete());
        when(eventPublisherPort.publishInstanceDeregistered(ArgumentMatchers.any(InstanceDeregisteredEvent.class)))
                .thenReturn(Completable.complete());
        when(peerSyncPort.getPeerUrls()).thenReturn(Single.just(Collections.emptyList()));

        ArgumentCaptor<InstanceDeregisteredEvent> eventCaptor = ArgumentCaptor.forClass(InstanceDeregisteredEvent.class);

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .verifyComplete();

        verify(eventPublisherPort).publishInstanceDeregistered(eventCaptor.capture());
        InstanceDeregisteredEvent event = eventCaptor.getValue();
        assert event.getReason().equals("SHUTDOWN");
        assert event.getInstanceId().equals(instanceId);
        assert event.getAppName().equals("ACCOUNTS-SERVICE");
    }

    private EurekaInstanceInfo buildInstanceInfo(String instanceId) {
        return EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName(instanceId.split(":")[0].toUpperCase())
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of("version", "1.0.0", "zone", "primary"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();
    }
}