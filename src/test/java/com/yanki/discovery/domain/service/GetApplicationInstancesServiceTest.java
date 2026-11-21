package com.yanki.discovery.domain.service;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Maybe;
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
 * Test unitario para {@link GetApplicationInstancesService} - CP-170, CP-174.
 * <p>
 * Valida: Descubrimiento instancias, filtro healthy, metadata completa.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class GetApplicationInstancesServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    private GetApplicationInstancesService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetApplicationInstancesService(registryPort);
    }

    @Test
    @DisplayName("CP-170: Debe obtener todas las instancias de una aplicación")
    void debeObtenerTodasLasInstanciasDeUnaAplicacion() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        EurekaInstanceInfo instance1 = buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP);
        EurekaInstanceInfo instance2 = buildInstanceInfo("accounts-service:172.20.0.6:8083", EurekaInstanceInfo.InstanceStatus.DOWN);

        when(registryPort.getInstances(appName)).thenReturn(Single.just(List.of(instance1, instance2)));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute(appName)))
                .expectNextMatches(instances -> instances.size() == 2
                        && instances.stream().anyMatch(i -> i.getStatus() == EurekaInstanceInfo.InstanceStatus.UP)
                        && instances.stream().anyMatch(i -> i.getStatus() == EurekaInstanceInfo.InstanceStatus.DOWN))
                .verifyComplete();

        verify(registryPort).getInstances(appName);
    }

    @Test
    @DisplayName("CP-170: Debe obtener solo instancias UP (healthy) para load balancing")
    void debeObtenerSoloInstanciasUPParaLoadBalancing() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        EurekaInstanceInfo instanceUp = buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP);
        EurekaInstanceInfo instanceDown = buildInstanceInfo("accounts-service:172.20.0.6:8083", EurekaInstanceInfo.InstanceStatus.DOWN);

        when(registryPort.getInstances(appName)).thenReturn(Single.just(List.of(instanceUp, instanceDown)));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.executeHealthyOnly(appName)))
                .expectNextMatches(instances -> instances.size() == 1
                        && instances.stream().allMatch(i -> i.getStatus() == EurekaInstanceInfo.InstanceStatus.UP)
                        && instances.get(0).getInstanceId().equals("accounts-service:172.20.0.5:8083"))
                .verifyComplete();

        verify(registryPort).getInstances(appName);
    }

    @Test
    @DisplayName("CP-170: Debe retornar lista vacía si aplicación no existe")
    void debeRetornarListaVaciaSiAplicacionNoExiste() {
        // Given
        String appName = "NON-EXISTENT-SERVICE";
        when(registryPort.getInstances(appName)).thenReturn(Single.just(Collections.emptyList()));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute(appName)))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: Debe encontrar instancia por ID con metadata completa")
    void debeEncontrarInstanciaPorIdConMetadataCompleta() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        EurekaInstanceInfo instance = EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(8443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of(
                        "version", "1.0.0",
                        "zone", "primary",
                        "api.version", "v1",
                        "canary", "false",
                        "weight", "100",
                        "deployment.environment", "docker",
                        "management.port", "8083"
                ))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();

        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(instance));

        // When & Then
        StepVerifier.create(RxJava3Adapter.maybeToMono(service.findByInstanceId(instanceId)))
                .expectNextMatches(i -> i != null
                        && i.getVersion().equals("1.0.0")
                        && i.getZone().equals("primary")
                        && i.getApiVersion().equals("v1")
                        && !i.isCanary()
                        && i.getWeight() == 100)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: Debe retornar empty si instancia no existe")
    void debeRetornarEmptySiInstanciaNoExiste() {
        // Given
        String instanceId = "non-existent:127.0.0.1:8080";
        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.empty());

        // When & Then
        StepVerifier.create(RxJava3Adapter.maybeToMono(service.findByInstanceId(instanceId)))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-170: Debe normalizar appName a MAYÚSCULAS")
    void debeNormalizarAppNameAMayusculas() {
        // Given
        String appName = "accounts-service"; // lowercase
        when(registryPort.getInstances("ACCOUNTS-SERVICE")).thenReturn(Single.just(Collections.emptyList()));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute(appName)))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();

        verify(registryPort).getInstances("ACCOUNTS-SERVICE");
    }

    @Test
    @DisplayName("CP-170: Debe propagar error cuando el puerto falla")
    void debePropagarErrorCuandoPuertoFalla() {
        // Given
        String appName = "ACCOUNTS-SERVICE";
        when(registryPort.getInstances(appName)).thenReturn(Single.error(new RuntimeException("Registry unavailable")));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute(appName)))
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
                .port(8083)
                .securePort(443)
                .status(status)
                .metadata(Map.of("version", "1.0.0", "zone", "primary"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();
    }
}