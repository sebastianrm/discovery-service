package com.yanki.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Clase de pruebas unitarias para DiscoveryServiceApplication
 * Verifica que el contexto de Spring Boot arranque correctamente sin fallos.
 *
 * <p>Parte de la plataforma Yanki, testing básico de carga de contexto.</p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
public class DiscoveryServiceApplicationTests {

    /**
     * Prueba que verifica la carga del contexto de la aplicación Spring Boot.
     */
    @Test
    void contextLoads() {
        // Test que valida la carga del contexto Spring Boot
    }
}
