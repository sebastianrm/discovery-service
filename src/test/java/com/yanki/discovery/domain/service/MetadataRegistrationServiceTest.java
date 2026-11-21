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
 * Test unitario para validar metadata de registro - CP-174.
 * <p>
 * Valida: 7 campos metadata obligatorios (version, zone, api.version, canary, weight,
 * deployment.environment, management.port) registrados y expuestos correctamente.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class MetadataRegistrationServiceTest {

    @Mock
    private EurekaRegistryPort registryPort;

    private GetApplicationInstancesService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetApplicationInstancesService(registryPort);
    }

    @Test
    @DisplayName("CP-174: Debe registrar y exponer los 7 campos metadata obligatorios")
    void debeRegistrarYExponerSieteCamposMetadataObligatorios() {
        // Given
        String instanceId = "accounts-service:172.20.0.5:8083";
        Map<String, String> fullMetadata = Map.of(
                "version", "1.0.0",
                "zone", "primary",
                "api.version", "v1",
                "canary", "false",
                "weight", "100",
                "deployment.environment", "docker",
                "management.port", "8083"
        );

        EurekaInstanceInfo expectedInstance = EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(8443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(fullMetadata)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        when(registryPort.getInstances("ACCOUNTS-SERVICE")).thenReturn(Single.just(List.of(expectedInstance)));
        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(expectedInstance));

        // When & Then: Verificar todos los campos via execute()
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute("ACCOUNTS-SERVICE")))
                .expectNextMatches(instances -> {
                    EurekaInstanceInfo i = instances.get(0);
                    return i.getVersion().equals("1.0.0")
                            && i.getZone().equals("primary")
                            && i.getApiVersion().equals("v1")
                            && !i.isCanary()
                            && i.getWeight() == 100
                            && i.getMetadata().get("deployment.environment").equals("docker")
                            && i.getMetadata().get("management.port").equals("8083");
                })
                .verifyComplete();

        // When & Then: Verificar via findByInstanceId
        StepVerifier.create(RxJava3Adapter.maybeToMono(service.findByInstanceId(instanceId)))
                .expectNextMatches(foundInstance -> foundInstance != null
                        && foundInstance.getVersion().equals("1.0.0")
                        && foundInstance.getZone().equals("primary")
                        && foundInstance.getApiVersion().equals("v1")
                        && !foundInstance.isCanary()
                        && foundInstance.getWeight() == 100)
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: Debe manejar canary deployment con metadata canary=true, weight=10")
    void debeManejarCanaryDeploymentConMetadataCanaryTrueWeight10() {
        // Given
        String instanceId = "accounts-service-canary:172.20.0.6:8083";
        Map<String, String> canaryMetadata = Map.of(
                "version", "1.1.0",
                "zone", "primary",
                "api.version", "v1",
                "canary", "true",
                "weight", "10",
                "deployment.environment", "docker",
                "management.port", "8083"
        );

        EurekaInstanceInfo canaryInstance = EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.6")
                .ipAddress("172.20.0.6")
                .port(8083)
                .securePort(8443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(canaryMetadata)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        when(registryPort.getInstances("ACCOUNTS-SERVICE")).thenReturn(Single.just(List.of(canaryInstance)));
        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(canaryInstance));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute("ACCOUNTS-SERVICE")))
                .expectNextMatches(instances -> {
                    EurekaInstanceInfo i = instances.get(0);
                    return i.getVersion().equals("1.1.0")
                            && i.isCanary()
                            && i.getWeight() == 10
                            && i.getApiVersion().equals("v1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: Debe retornar valores por defecto cuando metadata incompleta")
    void debeRetornarValoresPorDefectoCuandoMetadataIncompleta() {
        // Given: Solo metadata mínima
        String instanceId = "minimal-service:172.20.0.7:8080";
        Map<String, String> minimalMetadata = Map.of("version", "2.0.0");

        EurekaInstanceInfo minimalInstance = EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName("MINIMAL-SERVICE")
                .hostName("172.20.0.7")
                .ipAddress("172.20.0.7")
                .port(8080)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(minimalMetadata)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        when(registryPort.getInstances("MINIMAL-SERVICE")).thenReturn(Single.just(List.of(minimalInstance)));
        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(minimalInstance));

        // When & Then: Debe usar defaults para campos faltantes
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute("MINIMAL-SERVICE")))
                .expectNextMatches(instances -> {
                    EurekaInstanceInfo i = instances.get(0);
                    return i.getVersion().equals("2.0.0")
                            && i.getZone().equals("unknown")
                            && i.getApiVersion().equals("v1")
                            && !i.isCanary()
                            && i.getWeight() == 100;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: Debe soportar zone affinity con metadata zone")
    void debeSoportarZoneAffinityConMetadataZone() {
        // Given: Instancias en diferentes zonas
        Map<String, String> zone1Metadata = Map.of("version", "1.0.0", "zone", "zone1");
        Map<String, String> zone2Metadata = Map.of("version", "1.0.0", "zone", "zone2");

        EurekaInstanceInfo instanceZone1 = EurekaInstanceInfo.builder()
                .instanceId("svc:172.20.0.5:8080")
                .appName("ZONE-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8080)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(zone1Metadata)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        EurekaInstanceInfo instanceZone2 = instanceZone1.toBuilder()
                .instanceId("svc:172.20.0.6:8080")
                .hostName("172.20.0.6")
                .ipAddress("172.20.0.6")
                .metadata(zone2Metadata)
                .build();

        when(registryPort.getInstances("ZONE-SERVICE")).thenReturn(Single.just(List.of(instanceZone1, instanceZone2)));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute("ZONE-SERVICE")))
                .expectNextMatches(instances -> instances.size() == 2
                        && instances.stream().anyMatch(item -> item.getZone().equals("zone1"))
                        && instances.stream().anyMatch(item -> item.getZone().equals("zone2")))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: Debe soportar weighted routing con metadata weight")
    void debeSoportarWeightedRoutingConMetadataWeight() {
        // Given: Instancias con diferentes pesos
        Map<String, String> weight100 = Map.of("version", "1.0.0", "weight", "100");
        Map<String, String> weight10 = Map.of("version", "1.1.0", "weight", "10");

        EurekaInstanceInfo instance100 = EurekaInstanceInfo.builder()
                .instanceId("svc:172.20.0.5:8080")
                .appName("WEIGHTED-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8080)
                .securePort(443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(weight100)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        EurekaInstanceInfo instance10 = instance100.toBuilder()
                .instanceId("svc:172.20.0.6:8080")
                .hostName("172.20.0.6")
                .ipAddress("172.20.0.6")
                .metadata(weight10)
                .build();

        when(registryPort.getInstances("WEIGHTED-SERVICE")).thenReturn(Single.just(List.of(instance100, instance10)));

        // When & Then
        StepVerifier.create(RxJava3Adapter.singleToMono(service.execute("WEIGHTED-SERVICE")))
                .expectNextMatches(instances -> instances.size() == 2
                        && instances.stream().anyMatch(item -> item.getWeight() == 100)
                        && instances.stream().anyMatch(item -> item.getWeight() == 10))
                .verifyComplete();
    }

    @Test
    @DisplayName("CP-174: findByInstanceId debe retornar metadata completa")
    void findByInstanceIdDebeRetornarMetadataCompleta() {
        // Given
        String instanceId = "full-metadata-svc:172.20.0.8:8080";
        Map<String, String> fullMetadata = Map.of(
                "version", "3.0.0",
                "zone", "zone3",
                "api.version", "v2",
                "canary", "false",
                "weight", "50",
                "deployment.environment", "k8s",
                "management.port", "9090"
        );

        EurekaInstanceInfo expectedInstance = EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName("FULL-METADATA-SVC")
                .hostName("172.20.0.8")
                .ipAddress("172.20.0.8")
                .port(8080)
                .securePort(8443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(fullMetadata)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        when(registryPort.findByInstanceId(instanceId)).thenReturn(Maybe.just(expectedInstance));

        // When & Then
        StepVerifier.create(RxJava3Adapter.maybeToMono(service.findByInstanceId(instanceId)))
                .expectNextMatches(foundInstance -> foundInstance != null
                        && foundInstance.getVersion().equals("3.0.0")
                        && foundInstance.getZone().equals("zone3")
                        && foundInstance.getApiVersion().equals("v2")
                        && !foundInstance.isCanary()
                        && foundInstance.getWeight() == 50
                        && foundInstance.getMetadata().get("deployment.environment").equals("k8s")
                        && foundInstance.getMetadata().get("management.port").equals("9090"))
                .verifyComplete();
    }
}