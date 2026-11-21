package com.yanki.discovery.interfaces.rest.controller;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.in.GetEurekaHealthUseCase;
import com.yanki.discovery.interfaces.rest.dto.EurekaHealthResponse;
import com.yanki.discovery.interfaces.rest.mapper.EurekaRestMapper;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link EurekaHealthController} - CP-171.
 * <p>
 * Valida: Endpoints de health check y info del Eureka Server.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class EurekaHealthControllerTest {

    @Mock
    private GetEurekaHealthUseCase healthUseCase;

    @Mock
    private EurekaRestMapper mapper;

    private EurekaHealthController controller;

    @BeforeEach
    void setUp() {
        controller = new EurekaHealthController(healthUseCase, mapper);
    }

    @Test
    @DisplayName("CP-171: GET /actuator/health debe retornar UP cuando hay instancias UP")
    void healthDebeRetornarUPCuandoHayInstanciasUP() {
        // Given
        EurekaRegistry registry = buildRegistryWithUpInstances();
        when(healthUseCase.execute()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(controller.health())
                .expectNextMatches(response -> "UP".equals(response.getStatus())
                        && response.getComponents().containsKey("eureka")
                        && response.getComponents().containsKey("diskSpace")
                        && response.getTimestamp() > 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: GET /actuator/health debe retornar DOWN cuando todas las instancias están DOWN")
    void healthDebeRetornarDOWNCuandoTodasInstanciasDOWN() {
        // Given
        EurekaRegistry registry = buildRegistryWithDownInstances();
        when(healthUseCase.execute()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(controller.health())
                .expectNextMatches(response -> "DOWN".equals(response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: GET /actuator/health debe retornar OUT_OF_SERVICE cuando registry está vacío")
    void healthDebeRetornarOUT_OF_SERVICECuandoRegistryVacio() {
        // Given
        EurekaRegistry emptyRegistry = EurekaRegistry.empty();
        when(healthUseCase.execute()).thenReturn(Single.just(emptyRegistry));

        // When & Then
        StepVerifier.create(controller.health())
                .expectNextMatches(response -> "OUT_OF_SERVICE".equals(response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: GET /actuator/health debe incluir métricas de self-preservation")
    void healthDebeIncluirMetricasSelfPreservation() {
        // Given
        EurekaRegistry registry = buildRegistryWithSelfPreservation();
        when(healthUseCase.execute()).thenReturn(Single.just(registry));

        // When & Then
        StepVerifier.create(controller.health())
                .expectNextMatches(response -> {
                    EurekaHealthResponse.ComponentHealth eurekaComponent = response.getComponents().get("eureka");
                    return eurekaComponent != null
                            && eurekaComponent.getDetails().containsKey("selfPreservationMode")
                            && Boolean.TRUE.equals(eurekaComponent.getDetails().get("selfPreservationMode"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-171: GET /actuator/health debe propagar error del use case")
    void healthDebePropagarErrorDelUseCase() {
        // Given
        when(healthUseCase.execute()).thenReturn(Single.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(controller.health())
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
    }

    @Test
    @DisplayName("CP-171: GET /actuator/info debe retornar información del servicio")
    void infoDebeRetornarInformacionDelServicio() {
        // When & Then
        StepVerifier.create(controller.info())
                .expectNextMatches(info -> "discovery-service".equals(info.get("service"))
                        && "1.0.0".equals(info.get("version"))
                        && Integer.valueOf(8761).equals(info.get("port")))
                .verifyComplete();
    }

    // ===== Helpers =====

    private EurekaRegistry buildRegistryWithUpInstances() {
        EurekaInstanceInfo instanceUp = EurekaInstanceInfo.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of("version", "1.0.0"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();

        return EurekaRegistry.builder()
                .applications(Map.of("ACCOUNTS-SERVICE", List.of(instanceUp)))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(1)
                .instancesUp(1)
                .instancesDown(0)
                .build();
    }

    private EurekaRegistry buildRegistryWithDownInstances() {
        EurekaInstanceInfo instanceDown = EurekaInstanceInfo.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.DOWN)
                .metadata(Map.of("version", "1.0.0"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();

        return EurekaRegistry.builder()
                .applications(Map.of("ACCOUNTS-SERVICE", List.of(instanceDown)))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(1)
                .instancesUp(0)
                .instancesDown(1)
                .build();
    }

    private EurekaRegistry buildRegistryWithSelfPreservation() {
        EurekaInstanceInfo instanceUp = EurekaInstanceInfo.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of("version", "1.0.0"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();

        return EurekaRegistry.builder()
                .applications(Map.of("ACCOUNTS-SERVICE", List.of(instanceUp)))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(true)
                .totalInstances(1)
                .instancesUp(1)
                .instancesDown(0)
                .build();
    }
}
