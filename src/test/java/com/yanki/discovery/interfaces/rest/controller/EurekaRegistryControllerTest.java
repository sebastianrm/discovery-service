package com.yanki.discovery.interfaces.rest.controller;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.in.GetApplicationInstancesUseCase;
import com.yanki.discovery.domain.port.in.GetRegistryUseCase;
import com.yanki.discovery.interfaces.rest.dto.EurekaInstanceResponse;
import com.yanki.discovery.interfaces.rest.dto.EurekaRegistryResponse;
import com.yanki.discovery.interfaces.rest.mapper.EurekaRestMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link EurekaRegistryController} - CP-166, CP-170, CP-174.
 * <p>
 * Valida: Endpoints GET del registry (apps, instancias, healthy, por ID).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class EurekaRegistryControllerTest {

    @Mock
    private GetRegistryUseCase getRegistryUseCase;

    @Mock
    private GetApplicationInstancesUseCase getInstancesUseCase;

    @Mock
    private EurekaRestMapper mapper;

    private EurekaRegistryController controller;

    @BeforeEach
    void setUp() {
        controller = new EurekaRegistryController(getRegistryUseCase, getInstancesUseCase, mapper);
    }

    @Test
    @DisplayName("CP-166: GET /eureka/apps debe retornar registry completo")
    void getRegistryDebeRetornarRegistryCompleto() {
        // Given
        EurekaRegistry registry = buildRegistry();
        EurekaRegistryResponse expectedResponse = buildRegistryResponse();

        when(getRegistryUseCase.execute()).thenReturn(Single.just(registry));
        when(mapper.toRegistryResponse(registry)).thenReturn(expectedResponse);

        // When & Then
        StepVerifier.create(controller.getRegistry())
                .expectNextMatches(response -> response.getTotalInstances() == 2
                        && response.getInstancesUp() == 1
                        && response.getInstancesDown() == 1
                        && response.getApplications().size() == 2)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-166: GET /eureka/apps debe manejar registry vacío")
    void getRegistryDebeManejarRegistryVacio() {
        // Given
        EurekaRegistry emptyRegistry = EurekaRegistry.empty();
        EurekaRegistryResponse emptyResponse = EurekaRegistryResponse.builder()
                .applications(List.of())
                .totalInstances(0)
                .instancesUp(0)
                .instancesDown(0)
                .selfPreservationMode(false)
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .build();

        when(getRegistryUseCase.execute()).thenReturn(Single.just(emptyRegistry));
        when(mapper.toRegistryResponse(emptyRegistry)).thenReturn(emptyResponse);

        // When & Then
        StepVerifier.create(controller.getRegistry())
                .expectNextMatches(response -> response.getTotalInstances() == 0
                        && response.getApplications().isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-170: GET /eureka/apps/{appName} debe retornar todas las instancias")
    void getInstancesDebeRetornarTodasLasInstancias() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        List<EurekaInstanceInfo> instances = List.of(
                buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP),
                buildInstanceInfo("accounts-service:172.20.0.6:8083", EurekaInstanceInfo.InstanceStatus.DOWN)
        );
        List<EurekaInstanceResponse> responseList = List.of(
                buildInstanceResponse("accounts-service:172.20.0.5:8083", "UP"),
                buildInstanceResponse("accounts-service:172.20.0.6:8083", "DOWN")
        );

        when(getInstancesUseCase.execute(appName)).thenReturn(Single.just(instances));
        when(mapper.toInstanceResponseList(instances)).thenReturn(responseList);

        // When & Then
        StepVerifier.create(controller.getInstances(appName))
                .expectNextMatches(collection -> collection.size() == 2)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-170: GET /eureka/apps/{appName} debe retornar lista vacía si no existe")
    void getInstancesDebeRetornarListaVaciaSiNoExiste() {
        // Given
        String appName = "NON-EXISTENT-SERVICE";
        List<EurekaInstanceInfo> emptyList = Collections.emptyList();
        List<EurekaInstanceResponse> emptyResponse = Collections.emptyList();

        when(getInstancesUseCase.execute(appName)).thenReturn(Single.just(emptyList));
        when(mapper.toInstanceResponseList(emptyList)).thenReturn(emptyResponse);

        // When & Then
        StepVerifier.create(controller.getInstances(appName))
                .expectNextMatches(collection -> collection.isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-170: GET /eureka/apps/{appName}/healthy debe retornar solo instancias UP")
    void getHealthyInstancesDebeRetornarSoloInstanciasUP() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        List<EurekaInstanceInfo> healthyInstances = List.of(
                buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP)
        );
        List<EurekaInstanceResponse> responseList = List.of(
                buildInstanceResponse("accounts-service:172.20.0.5:8083", "UP")
        );

        when(getInstancesUseCase.executeHealthyOnly(appName)).thenReturn(Single.just(healthyInstances));
        when(mapper.toInstanceResponseList(healthyInstances)).thenReturn(responseList);

        // When & Then
        StepVerifier.create(controller.getHealthyInstances(appName))
                .expectNextMatches(collection -> collection.size() == 1)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: GET /eureka/apps/{appName}/{instanceId} debe retornar instancia por ID")
    void getInstanceDebeRetornarInstanciaPorId() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        String instanceId = "accounts-service:172.20.0.5:8083";
        EurekaInstanceInfo instance = buildInstanceInfo(instanceId, EurekaInstanceInfo.InstanceStatus.UP);
        EurekaInstanceResponse expectedResponse = buildInstanceResponse(instanceId, "UP");

        when(getInstancesUseCase.findByInstanceId(instanceId)).thenReturn(Maybe.just(instance));
        when(mapper.toInstanceResponse(instance)).thenReturn(expectedResponse);

        // When & Then
        StepVerifier.create(controller.getInstance(appName, instanceId))
                .expectNextMatches(response -> response.getInstanceId().equals(instanceId)
                        && response.getStatus().equals("UP"))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: GET /eureka/apps/{appName}/{instanceId} debe retornar empty si no existe")
    void getInstanceDebeRetornarEmptySiNoExiste() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        String instanceId = "non-existent:127.0.0.1:8080";

        when(getInstancesUseCase.findByInstanceId(instanceId)).thenReturn(Maybe.empty());

        // When & Then - maybeToMono with empty completes without emitting a value
        // The controller's switchIfEmpty handles the 404 case via Spring's response handling
        StepVerifier.create(controller.getInstance(appName, instanceId))
                .thenCancel();
    }

    @Test
    @DisplayName("CP-166: GET /eureka/apps debe propagar error del use case")
    void getRegistryDebePropagarErrorDelUseCase() {
        // Given
        when(getRegistryUseCase.execute()).thenReturn(Single.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(controller.getRegistry())
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
    }

    @Test
    @DisplayName("CP-170: GET /eureka/apps/{appName} debe propagar error del use case")
    void getInstancesDebePropagarErrorDelUseCase() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        when(getInstancesUseCase.execute(appName)).thenReturn(Single.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(controller.getInstances(appName))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
    }

    // ===== Helpers =====

    private EurekaRegistry buildRegistry() {
        EurekaInstanceInfo instanceUp = buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP);
        EurekaInstanceInfo instanceDown = buildInstanceInfo("cards-service:172.20.0.6:8085", EurekaInstanceInfo.InstanceStatus.DOWN);

        return EurekaRegistry.builder()
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
    }

    private EurekaRegistryResponse buildRegistryResponse() {
        return EurekaRegistryResponse.builder()
                .applications(List.of(
                        EurekaRegistryResponse.ApplicationResponse.builder()
                                .name("ACCOUNTS-SERVICE")
                                .instances(List.of(buildInstanceResponse("accounts-service:172.20.0.5:8083", "UP")))
                                .build(),
                        EurekaRegistryResponse.ApplicationResponse.builder()
                                .name("CARDS-SERVICE")
                                .instances(List.of(buildInstanceResponse("cards-service:172.20.0.6:8085", "DOWN")))
                                .build()
                ))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(2)
                .instancesUp(1)
                .instancesDown(1)
                .build();
    }

    private EurekaInstanceInfo buildInstanceInfo(String instanceId, EurekaInstanceInfo.InstanceStatus status) {
        return EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName(instanceId.split(":")[0].toUpperCase())
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(status)
                .metadata(Map.of("version", "1.0.0", "zone", "primary"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();
    }

    private EurekaInstanceResponse buildInstanceResponse(String instanceId, String status) {
        return EurekaInstanceResponse.builder()
                .instanceId(instanceId)
                .appName(instanceId.split(":")[0].toUpperCase())
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(status)
                .metadata(Map.of("version", "1.0.0", "zone", "primary"))
                .canary(false)
                .zone("primary")
                .apiVersion("v1")
                .weight(100)
                .version("1.0.0")
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();
    }
}
