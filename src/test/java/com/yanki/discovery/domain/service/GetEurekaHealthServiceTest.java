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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link GetEurekaHealthService} - CP-171.
 * <p>
 * Valida: Health check Eureka Server con componentes eureka y diskSpace.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class GetEurekaHealthServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    private GetEurekaHealthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetEurekaHealthService(registryPort);
    }

    @Test
    @DisplayName("CP-171: Debe retornar UP cuando hay instancias UP en registry")
    void debeRetornarUPCuandoHayInstanciasUP() {
        // Given
        EurekaInstanceInfo instanceUp = buildInstanceInfo("service1:172.20.0.5:8080", EurekaInstanceInfo.InstanceStatus.UP);
        EurekaRegistry registry = EurekaRegistry.builder()
                .applications(Map.of("SERVICE1", List.of(instanceUp)))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(1)
                .instancesUp(1)
                .instancesDown(0)
                .build();

        when(registryPort.getRegistry()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectNextMatches(r -> "UP".equals(r.getStatus())
                        && r.getInstancesUp() == 1
                        && r.getInstancesDown() == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: Debe retornar DOWN cuando todas las instancias están DOWN")
    void debeRetornarDOWNCuandoTodasInstanciasDOWN() {
        // Given
        EurekaInstanceInfo instanceDown = buildInstanceInfo("service1:172.20.0.5:8080", EurekaInstanceInfo.InstanceStatus.DOWN);
        EurekaRegistry registry = EurekaRegistry.builder()
                .applications(Map.of("SERVICE1", List.of(instanceDown)))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(1)
                .instancesUp(0)
                .instancesDown(1)
                .build();

        when(registryPort.getRegistry()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectNextMatches(r -> "DOWN".equals(r.getStatus())
                        && r.getInstancesUp() == 0
                        && r.getInstancesDown() == 1)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: Debe retornar OUT_OF_SERVICE cuando registry está vacío")
    void debeRetornarOUT_OF_SERVICECuandoRegistryVacio() {
        // Given
        EurekaRegistry registry = EurekaRegistry.empty();

        when(registryPort.getRegistry()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectNextMatches(r -> "OUT_OF_SERVICE".equals(r.getStatus())
                        && r.getTotalInstances() == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: Debe incluir métricas de self-preservation en health")
    void debeIncluirMetricasSelfPreservationEnHealth() {
        // Given
        EurekaInstanceInfo instanceUp = buildInstanceInfo("service1:172.20.0.5:8080", EurekaInstanceInfo.InstanceStatus.UP);
        EurekaRegistry registry = EurekaRegistry.builder()
                .applications(Map.of("SERVICE1", List.of(instanceUp)))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(true)
                .totalInstances(1)
                .instancesUp(1)
                .instancesDown(0)
                .build();

        when(registryPort.getRegistry()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectNextMatches(r -> r.isSelfPreservationMode() == true
                        && "UP".equals(r.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: Debe propagar error cuando el puerto falla")
    void debePropagarErrorCuandoPuertoFalla() {
        // Given
        when(registryPort.getRegistry()).thenReturn(Single.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute()))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
    }

    private EurekaInstanceInfo buildInstanceInfo(String instanceId, EurekaInstanceInfo.InstanceStatus status) {
        return EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName(instanceId.split(":")[0].toUpperCase())
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8080)
                .securePort(443)
                .status(status)
                .metadata(Map.of("version", "1.0.0"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();
    }
}