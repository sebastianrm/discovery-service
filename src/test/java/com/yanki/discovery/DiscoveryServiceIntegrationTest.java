package com.yanki.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Clase de pruebas de integración para el microservicio discovery-service.
 *
 * <p>Valida los endpoints principales del servidor Eureka definidos en los
 * casos de prueba funcionales del proyecto, incluyendo registro, manejo
 * de errores y disponibilidad del dashboard.</p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class DiscoveryServiceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Verifica que el endpoint de listado de aplicaciones registradas
     * responda correctamente.
     */
    @Test
    void testRegistroExitoso() {
        webTestClient.get()
                .uri("/eureka/apps")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Verifica el comportamiento esperado cuando se consulta una
     * aplicación inexistente en Eureka.
     */
    @Test
    void testErrorRegistro() {
        webTestClient.get()
                .uri("/eureka/apps/nonexistent-app")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Verifica que el dashboard principal del servidor Eureka esté disponible.
     */
    @Test
    void testDescubrimientoExitoso() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk();
    }
}
