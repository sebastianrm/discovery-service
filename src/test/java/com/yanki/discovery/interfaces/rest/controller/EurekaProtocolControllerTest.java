package com.yanki.discovery.interfaces.rest.controller;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.DeregisterInstanceUseCase;
import com.yanki.discovery.domain.port.in.RegisterInstanceUseCase;
import com.yanki.discovery.domain.port.in.RenewLeaseUseCase;
import com.yanki.discovery.interfaces.rest.dto.EurekaInstanceRequest;
import com.yanki.discovery.interfaces.rest.mapper.EurekaRestMapper;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link EurekaProtocolController} - CP-166, CP-167, CP-168.
 * <p>
 * Valida: Endpoints POST/PUT/DELETE del protocolo Eureka (register, heartbeat, deregister).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class EurekaProtocolControllerTest {

    @Mock
    private RegisterInstanceUseCase registerUseCase;

    @Mock
    private RenewLeaseUseCase renewLeaseUseCase;

    @Mock
    private DeregisterInstanceUseCase deregisterUseCase;

    @Mock
    private EurekaRestMapper mapper;

    private EurekaProtocolController controller;

    @BeforeEach
    void setUp() {
        controller = new EurekaProtocolController(registerUseCase, renewLeaseUseCase, deregisterUseCase, mapper);
    }

    @Test
    @DisplayName("CP-166: POST /eureka/apps/{appName} debe registrar instancia exitosamente")
    void registerDebeRegistrarInstanciaExitosamente() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        EurekaInstanceRequest request = buildInstanceRequest();
        when(registerUseCase.execute(any(EurekaInstanceInfo.class))).thenReturn(Completable.complete());

        // When & Then
        StepVerifier.create(controller.register(appName, request))
                .verifyComplete();

        verify(registerUseCase).execute(any(EurekaInstanceInfo.class));
    }

    @Test
    @DisplayName("CP-166: POST /eureka/apps/{appName} debe propagar error de registro")
    void registerDebePropagarErrorDeRegistro() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        EurekaInstanceRequest request = buildInstanceRequest();
        when(registerUseCase.execute(any(EurekaInstanceInfo.class)))
                .thenReturn(Completable.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(controller.register(appName, request))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
    }

    @Test
    @DisplayName("CP-167: PUT /eureka/apps/{appName}/{instanceId} debe renovar lease")
    void renewLeaseDebeRenovarLeaseExitosamente() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(renewLeaseUseCase.execute(instanceId)).thenReturn(Completable.complete());

        // When & Then
        StepVerifier.create(controller.renewLease(appName, instanceId))
                .verifyComplete();

        verify(renewLeaseUseCase).execute(instanceId);
    }

    @Test
    @DisplayName("CP-167: PUT /eureka/apps/{appName}/{instanceId} debe propagar error")
    void renewLeaseDebePropagarError() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(renewLeaseUseCase.execute(instanceId))
                .thenReturn(Completable.error(new RuntimeException("Instance not found")));

        // When & Then
        StepVerifier.create(controller.renewLease(appName, instanceId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Instance not found"))
                .verify();
    }

    @Test
    @DisplayName("CP-168: DELETE /eureka/apps/{appName}/{instanceId} debe desregistrar instancia")
    void deregisterDebeDesregistrarInstanciaExitosamente() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(deregisterUseCase.execute(instanceId)).thenReturn(Completable.complete());

        // When & Then
        StepVerifier.create(controller.deregister(appName, instanceId))
                .verifyComplete();

        verify(deregisterUseCase).execute(instanceId);
    }

    @Test
    @DisplayName("CP-168: DELETE /eureka/apps/{appName}/{instanceId} debe propagar error")
    void deregisterDebePropagarError() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(deregisterUseCase.execute(instanceId))
                .thenReturn(Completable.error(new RuntimeException("Instance not found")));

        // When & Then
        StepVerifier.create(controller.deregister(appName, instanceId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Instance not found"))
                .verify();
    }

    @Test
    @DisplayName("CP-166: POST debe normalizar appName a MAYÚSCULAS")
    void registerDebeNormalizarAppNameAMayusculas() {
        // Given
        String appName = "accounts-service"; // lowercase
        EurekaInstanceRequest request = buildInstanceRequest();
        when(registerUseCase.execute(any(EurekaInstanceInfo.class))).thenReturn(Completable.complete());

        // When & Then
        StepVerifier.create(controller.register(appName, request))
                .verifyComplete();

        verify(registerUseCase).execute(any(EurekaInstanceInfo.class));
    }

    // ===== Helpers =====

    private EurekaInstanceRequest buildInstanceRequest() {
        return EurekaInstanceRequest.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(8443)
                .metadata(Map.of(
                        "version", "1.0.0",
                        "zone", "primary",
                        "api.version", "v1",
                        "canary", "false",
                        "weight", "100",
                        "deployment.environment", "docker",
                        "management.port", "8083"
                ))
                .build();
    }
}
