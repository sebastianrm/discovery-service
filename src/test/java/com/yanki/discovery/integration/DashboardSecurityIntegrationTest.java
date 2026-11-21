package com.yanki.discovery.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración para CP-172: Eureka Dashboard Security - Basic Auth obligatorio.
 * <p>
 * Valida: 401 sin credenciales, 200 con credenciales válidas, registry API accesible.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardSecurityIntegrationTest {

    @Container
    static GenericContainer<?> eurekaServer = new GenericContainer<>("yanki/discovery-service:1.0.0")
            .withExposedPorts(8761)
            .withEnv("EUREKA_USER", "admin")
            .withEnv("EUREKA_PASSWORD", "changeit")
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .forPort(8761));

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.user.name", () -> "admin");
        registry.add("spring.security.user.password", () -> "changeit");
    }

    @Test
    @DisplayName("CP-172: Dashboard retorna 401 sin autenticación")
    void dashboardRetorna401SinAutenticacion() {
        // Given: TestRestTemplate sin credenciales
        TestRestTemplate noAuthTemplate = new TestRestTemplate();

        // When: Acceder a Dashboard
        ResponseEntity<String> response = noAuthTemplate.getForEntity(
                "http://localhost:" + eurekaServer.getMappedPort(8761) + "/", String.class);

        // Then: 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getHeaders().containsKey("WWW-Authenticate"));
        assertTrue(response.getHeaders().getFirst("WWW-Authenticate").contains("Basic"));
    }

    @Test
    @DisplayName("CP-172: Dashboard retorna 401 con credenciales inválidas")
    void dashboardRetorna401ConCredencialesInvalidas() {
        // Given: TestRestTemplate con credenciales incorrectas
        TestRestTemplate wrongAuthTemplate = new TestRestTemplate("admin", "wrongpassword");

        // When: Acceder a Dashboard
        ResponseEntity<String> response = wrongAuthTemplate.getForEntity(
                "http://localhost:" + eurekaServer.getMappedPort(8761) + "/", String.class);

        // Then: 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("CP-172: Dashboard retorna 200 con credenciales válidas")
    void dashboardRetorna200ConCredencialesValidas() {
        // When: Acceder a Dashboard con credenciales correctas
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        // Then: 200 OK con HTML Dashboard
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Eureka") || response.getBody().contains("Dashboard"));
    }

    @Test
    @DisplayName("CP-172: Registry API accesible sin autenticación (para clientes Eureka)")
    void registryApiAccesibleSinAutenticacion() {
        // Given: TestRestTemplate sin credenciales
        TestRestTemplate noAuthTemplate = new TestRestTemplate();

        // When: Acceder a Registry API
        ResponseEntity<String> response = noAuthTemplate.getForEntity(
                "http://localhost:" + eurekaServer.getMappedPort(8761) + "/eureka/apps", String.class);

        // Then: 200 OK (registry accesible para clientes Eureka)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("CP-172: Actuator endpoints accesibles sin autenticación")
    void actuatorEndpointsAccesiblesSinAutenticacion() {
        // Given
        TestRestTemplate noAuthTemplate = new TestRestTemplate();

        // When: Health check
        ResponseEntity<String> healthResponse = noAuthTemplate.getForEntity(
                "http://localhost:" + eurekaServer.getMappedPort(8761) + "/actuator/health", String.class);

        // When: Info
        ResponseEntity<String> infoResponse = noAuthTemplate.getForEntity(
                "http://localhost:" + eurekaServer.getMappedPort(8761) + "/actuator/info", String.class);

        // Then
        assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
        assertEquals(HttpStatus.OK, infoResponse.getStatusCode());
    }
}