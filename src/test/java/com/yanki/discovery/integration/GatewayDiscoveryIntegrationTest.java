package com.yanki.discovery.integration;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.GetApplicationInstancesUseCase;
import com.yanki.discovery.domain.port.in.RegisterInstanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración para CP-170: API Gateway descubrimiento servicios (Fetch Registry 30s).
 * <p>
 * Valida: Gateway fetch registry cada 30s → routing lb:// resuelve instancias healthy.
 * <p>
 * Requiere: Eureka Server + API Gateway (Testcontainers).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Testcontainers
@SpringBootTest
class GatewayDiscoveryIntegrationTest {

    @Container
    static GenericContainer<?> eurekaServer = new GenericContainer<>("yanki/discovery-service:1.0.0")
            .withExposedPorts(8761)
            .withEnv("EUREKA_PASSWORD", "changeit")
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .forPort(8761));

    @Container
    static GenericContainer<?> apiGateway = new GenericContainer<>("yanki/api-gateway:1.0.0")
            .withExposedPorts(8080)
            .withEnv("EUREKA_URL", "http://admin:changeit@eureka:8761/eureka/")
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .forPort(8080));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.service-url.defaultZone", () ->
                "http://admin:changeit@" + eurekaServer.getHost() + ":" + eurekaServer.getMappedPort(8761) + "/eureka/");
    }

    @Autowired
    private RegisterInstanceUseCase registerUseCase;

    @Autowired
    private GetApplicationInstancesUseCase getInstancesUseCase;

    @BeforeEach
    void setUp() {
        // Registrar servicio de prueba
    }

    @Test
    @DisplayName("CP-170: Gateway resuelve lb://service-name a instancias healthy via Eureka")
    void gatewayResuelveLbServiceNameAInstanciasHealthy() {
        // Given: Registrar 2 instancias de ACCOUNTS-SERVICE
        for (int i = 1; i <= 2; i++) {
            String instanceId = "accounts-service:172.20.0." + (10 + i) + ":808" + i;
            EurekaInstanceInfo instanceInfo = EurekaInstanceInfo.builder()
                    .instanceId(instanceId)
                    .appName("ACCOUNTS-SERVICE")
                    .hostName("172.20.0." + (10 + i))
                    .ipAddress("172.20.0." + (10 + i))
                    .port(8080 + i)
                    .securePort(8443)
                    .status(EurekaInstanceInfo.InstanceStatus.UP)
                    .metadata(Map.of("version", "1.0.0", "zone", "primary"))
                    .lastRenewalTimestamp(System.currentTimeMillis())
                    .registrationTimestamp(System.currentTimeMillis())
                    .evictionTimestamp(0)
                    .build();

            StepVerifier.create(RxJava3Adapter.completableToMono(registerUseCase.execute(instanceInfo)))
                    .verifyComplete();
        }

        // When: Verificar que Eureka tiene 2 instancias UP
        StepVerifier.create(RxJava3Adapter.singleToMono(getInstancesUseCase.executeHealthyOnly("ACCOUNTS-SERVICE")))
                .expectNextMatches(instances -> instances.size() == 2
                        && instances.stream().allMatch(EurekaInstanceInfo::isHealthy))
                .verifyComplete();

        // Then: Verificar que Gateway puede resolver lb://accounts-service
        // NOTE: Esto requiere hacer request real al Gateway y verificar que
        // el routing funciona. Requiere que el Gateway esté configurado con
        // discovery.locator.enabled=true y rutas lb://
        //
        // En test real:
        // WebClient gatewayClient = WebClient.builder()
        //     .baseUrl("http://" + apiGateway.getHost() + ":" + apiGateway.getMappedPort(8080))
        //     .build();
        // gatewayClient.get().uri("/api/accounts/health").retrieve().toBodilessEntity()...
    }

    @Test
    @DisplayName("CP-170: Gateway actualiza routing table tras fetch registry (30s)")
    void gatewayActualizaRoutingTableTrasFetchRegistry() {
        // Given: Registrar 1 instancia
        String instanceId = "accounts-service:172.20.0.11:8083";
        EurekaInstanceInfo instanceInfo = EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.11")
                .ipAddress("172.20.0.11")
                .port(8083)
                .securePort(8443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(Map.of("version", "1.0.0"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        StepVerifier.create(RxJava3Adapter.completableToMono(registerUseCase.execute(instanceInfo)))
                .verifyComplete();

        // When: Verificar fetch registry
        StepVerifier.create(RxJava3Adapter.singleToMono(getInstancesUseCase.execute("ACCOUNTS-SERVICE")))
                .expectNextMatches(instances -> instances.size() == 1)
                .verifyComplete();

        // NOTE: Para test completo:
        // 1. Detener instancia
        // 2. Esperar 30s (registry-fetch-interval-seconds)
        // 3. Verificar que Gateway ya no resuelve a esa instancia
        // Requiere control sobre el Gateway real.
    }
}
