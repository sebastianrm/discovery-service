package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.event.InstanceRegisteredEvent;
import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.out.EventPublisherPort;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import com.yanki.discovery.domain.port.out.PeerSyncPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Test unitario para {@link RegisterInstanceService} - CP-166.
 * <p>
 * Valida: Registro de instancia, publicación evento, replicación peers.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class RegisterInstanceServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    @Mock
    private EventPublisherPort eventPublisherPort;

    @Mock
    private PeerSyncPort peerSyncPort;

    private RegisterInstanceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RegisterInstanceService(registryPort, eventPublisherPort, peerSyncPort);
    }

    @Test
    @DisplayName("CP-166: Debe registrar instancia, publicar evento y replicar a peers")
    void debeRegistrarInstanciaPublicarEventoYReplicar() {
        // Given
        EurekaInstanceInfo instanceInfo = EurekaInstanceInfo.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of("version", "1.0.0", "zone", "primary", "canary", "false"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        when(registryPort.registerInstance(instanceInfo)).thenReturn(Completable.complete());
        when(eventPublisherPort.publishInstanceRegistered(ArgumentMatchers.any(InstanceRegisteredEvent.class)))
                .thenReturn(Completable.complete());
        when(peerSyncPort.getPeerUrls()).thenReturn(Single.just(List.of("http://peer1:8761/eureka/", "http://peer2:8761/eureka/")));
        when(peerSyncPort.replicateToPeers(anyList(), anyString())).thenReturn(Completable.complete());

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceInfo)))
                .verifyComplete();

        // Verificar interacciones
        verify(registryPort).registerInstance(instanceInfo);
        verify(eventPublisherPort).publishInstanceRegistered(ArgumentMatchers.any(InstanceRegisteredEvent.class));
        verify(peerSyncPort).getPeerUrls();
        verify(peerSyncPort).replicateToPeers(
                List.of("http://peer1:8761/eureka/", "http://peer2:8761/eureka/"),
                instanceInfo.getInstanceId()
        );
    }

    @Test
    @DisplayName("CP-166: Debe completar registro aunque fallen evento y replicación (resiliente)")
    void debeCompletarRegistroAunqueFallenEventoYReplicacion() {
        // Given
        EurekaInstanceInfo instanceInfo = EurekaInstanceInfo.builder()
                .instanceId("test-service:localhost:8080")
                .appName("TEST-SERVICE")
                .hostName("localhost")
                .ipAddress("127.0.0.1")
                .port(8080)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of())
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        when(registryPort.registerInstance(instanceInfo)).thenReturn(Completable.complete());
        when(eventPublisherPort.publishInstanceRegistered(ArgumentMatchers.any()))
                .thenReturn(Completable.error(new RuntimeException("Kafka down")));
        when(peerSyncPort.getPeerUrls()).thenReturn(Single.just(List.of("http://peer:8761/eureka/")));
        when(peerSyncPort.replicateToPeers(anyList(), anyString()))
                .thenReturn(Completable.error(new RuntimeException("Peer unreachable")));

        // When & Then - No debe fallar, debe completar
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceInfo)))
                .verifyComplete();

        // Verificar que se intentó publicar y replicar
        verify(eventPublisherPort).publishInstanceRegistered(ArgumentMatchers.any());
        verify(peerSyncPort).replicateToPeers(anyList(), anyString());
    }
}