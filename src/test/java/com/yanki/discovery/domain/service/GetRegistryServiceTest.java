package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link GetRegistryService}.
 * <p>
 * Demuestra testing hexagonal: prueba el caso de uso mockeando solo el puerto de salida,
 * sin Spring context, sin base de datos, sin Kafka.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class GetRegistryServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    private GetRegistryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetRegistryService(registryPort);
    }

    @Test
    @DisplayName("Debe obtener registry vacío cuando no hay instancias registradas")
    void debeObtenerRegistryVacio() {
        // Given
        EurekaRegistry emptyRegistry = EurekaRegistry.empty();
        when(registryPort.getRegistry()).thenReturn(Single.just(emptyRegistry));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectNextMatches(registry -> registry.isEmpty()
                        && registry.getTotalInstances() == 0
                        && registry.getInstancesUp() == 0
                        && registry.getInstancesDown() == 0
                        && !registry.isSelfPreservationMode())
                .verifyComplete();
    }

    @Test
    @DisplayName("Debe obtener registry con instancias UP y DOWN")
    void debeObtenerRegistryConInstancias() {
        // Given
        EurekaInstanceInfo instanceUp = EurekaInstanceInfo.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .appName("ACCOUNTS-SERVICE")
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

        EurekaInstanceInfo instanceDown = instanceUp.toBuilder()
                .instanceId("cards-service:172.20.0.6:8085")
                .appName("CARDS-SERVICE")
                .status(EurekaInstanceInfo.InstanceStatus.DOWN)
                .build();

        EurekaRegistry registry = EurekaRegistry.builder()
                .applications(Map.of(
                        "ACCOUNTS-SERVICE", List.of(instanceUp),
                        "CARDS-SERVICE", List.of(instanceDown)
                ))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(2)
                .instancesUp(1)
                .instancesDown(1)
                .build();

        when(registryPort.getRegistry()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectNextMatches(r -> r.getTotalInstances() == 2
                        && r.getInstancesUp() == 1
                        && r.getInstancesDown() == 1
                        && r.getApplicationNames().size() == 2
                        && r.getInstances("ACCOUNTS-SERVICE").size() == 1
                        && r.getInstances("CARDS-SERVICE").size() == 1
                        && r.getHealthyInstances("ACCOUNTS-SERVICE").size() == 1
                        && r.getHealthyInstances("CARDS-SERVICE").size() == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("Debe propagar error cuando el puerto falla")
    void debePropagarErrorCuandoPuertoFalla() {
        // Given
        when(registryPort.getRegistry()).thenReturn(Single.error(new RuntimeException("Connection timeout")));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Connection timeout"))
                .verify();
    }
}