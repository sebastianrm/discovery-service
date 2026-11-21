package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario para {@link RenewLeaseService} - CP-167.
 * <p>
 * Valida: Heartbeat cada 10s → lease renovado, lastRenewal actualizado.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class RenewLeaseServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    private RenewLeaseService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RenewLeaseService(registryPort);
    }

    @Test
    @DisplayName("CP-167: Debe renovar lease y completar exitosamente")
    void debeRenovarLeaseCompletarExitosamente() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(registryPort.renewLease(instanceId)).thenReturn(Maybe.just(buildInstanceInfo(instanceId)));

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .verifyComplete();

        verify(registryPort).renewLease(instanceId);
    }

    @Test
    @DisplayName("CP-167: Debe renovar lease y retornar instancia actualizada")
    void debeRenovarLeaseYRetornarInstanciaActualizada() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        EurekaInstanceInfo updatedInstance = buildInstanceInfo(instanceId);
        when(registryPort.renewLease(instanceId)).thenReturn(Maybe.just(updatedInstance));

        // When & Then
        StepVerifier.create(RxJava3Adapter.maybeToMono(service.executeAndGet(instanceId)))
                .expectNextMatches(instance -> instance.getInstanceId().equals(instanceId)
                        && instance.getStatus() == EurekaInstanceInfo.InstanceStatus.UP)
                .verifyComplete();

        verify(registryPort).renewLease(instanceId);
    }

    @Test
    @DisplayName("CP-167: Debe completar aunque instancia no exista (idempotente)")
    void debeCompletarAunqueInstanciaNoExista() {
        // Given
        String instanceId = "non-existent:127.0.0.1:8080";
        when(registryPort.renewLease(instanceId)).thenReturn(Maybe.empty());

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .verifyComplete();

        verify(registryPort).renewLease(instanceId);
    }

    @Test
    @DisplayName("CP-167: Debe propagar error cuando el puerto falla")
    void debePropagarErrorCuandoPuertoFalla() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(registryPort.renewLease(instanceId)).thenReturn(Maybe.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(RxJava3Adapter.completableToMono(service.execute(instanceId)))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
    }

    @Test
    @DisplayName("CP-167: executeAndGet debe propagar error cuando el puerto falla")
    void executeAndGetDebePropagarErrorCuandoPuertoFalla() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        when(registryPort.renewLease(instanceId)).thenReturn(Maybe.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(RxJava3Adapter.maybeToMono(service.executeAndGet(instanceId)))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Registry unavailable"))
                .verify();
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