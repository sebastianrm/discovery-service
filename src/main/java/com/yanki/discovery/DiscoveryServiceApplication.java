package com.yanki.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * DiscoveryServiceApplication es el punto de entrada del microservicio de discovery-service,
 * el cual implementa un servidor Eureka utilizando Spring Boot y Spring Cloud Netflix Eureka Server.
 *
 * <p>
 * Parte de la plataforma bancaria Yanki. Este servicio sigue el patrón database-per-service:
 * no utiliza base de datos propia, sólo expone capacidades de registro y descubrimiento de servicios,
 * monitoreo de instancias y dashboard.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {

    /**
     * Inicia la aplicación Spring Boot que levanta el servidor Eureka.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        log.info("Iniciando Discovery Service (Eureka Server)...");
        long startTime = System.currentTimeMillis();

        try {
            SpringApplication app = new SpringApplication(DiscoveryServiceApplication.class);
            app.addListeners(event -> {
                if (event instanceof org.springframework.boot.context.event.ApplicationReadyEvent) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("Discovery Service iniciado exitosamente en {} ms", elapsed);
                    log.info("Eureka Server disponible en http://localhost:8761");
                    log.info("Dashboard Eureka: http://localhost:8761/");
                    log.info("API Registry: http://localhost:8761/eureka/apps");
                    log.info("Health Check: http://localhost:8761/actuator/health");
                    log.info("Swagger UI: http://localhost:8761/swagger-ui.html");
                }
            });
            app.run(args);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Error fatal al iniciar Discovery Service despues de {} ms: {}",
                    elapsed, e.getMessage(), e);
            throw e;
        }
    }
}
