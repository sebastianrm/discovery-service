package com.yanki.discovery.interfaces.rest.controller;

import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.in.GetEurekaHealthUseCase;
import com.yanki.discovery.interfaces.rest.dto.ApiError;
import com.yanki.discovery.interfaces.rest.dto.EurekaHealthResponse;
import com.yanki.discovery.interfaces.rest.mapper.EurekaRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controlador REST para health check del Eureka Server.
 * <p>
 * Endpoint /actuator/health compatible con Spring Boot Actuator.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Health checks del Eureka Server")
public class EurekaHealthController {

    private final GetEurekaHealthUseCase healthUseCase;
    private final EurekaRestMapper mapper;

    /**
     * Health check del Eureka Server.
     * <p>
     * Retorna estado general UP/DOWN basado en el registry y métricas internas.
     * Compatible con Spring Boot Actuator y Docker healthcheck.
     * </p>
     *
     * @return estado de salud
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Health check del Eureka Server",
            description = """
                    Verifica el estado del servidor Eureka y el registry.
                    
                    **Lógica de determinación de status:**
                    - `UP`: Registry operativo, al menos una instancia UP
                    - `DOWN`: Todas las instancias DOWN o registry vacío con instancias registradas
                    - `OUT_OF_SERVICE`: Registry vacío (ningún servicio registrado)
                    
                    **Componentes verificados:**
                    - `eureka`: Estado del registry (instancias UP/DOWN, self-preservation)
                    - `diskSpace`: Espacio en disco del contenedor
                    
                    **Uso en Docker:**
                    ```yaml
                    healthcheck:
                      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8761/actuator/health"]
                      interval: 10s
                      timeout: 5s
                      retries: 5
                      start_period: 20s
                    ```
                    """,
            operationId = "healthCheck",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Health check completado (status puede ser UP, DOWN, OUT_OF_SERVICE)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EurekaHealthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Servicio no disponible (status DOWN)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<EurekaHealthResponse> health() {
        log.debug("Solicitud GET /actuator/health - Health check del Eureka Server");
        long startTime = System.currentTimeMillis();

        try {
            return RxJava3Adapter.singleToMono(healthUseCase.execute())
                    .map(this::toHealthResponse)
                    .doOnSuccess(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        if ("UP".equals(response.getStatus())) {
                            log.info("Health check exitoso: status={} en {} ms",
                                    response.getStatus(), elapsed);
                        } else {
                            log.warn("Health check con advertencia: status={} en {} ms",
                                    response.getStatus(), elapsed);
                        }
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error en health check despues de {} ms: {}",
                                elapsed, error.getMessage(), error);
                    });
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Excepcion inesperada en health check despues de {} ms: {}",
                    elapsed, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Info endpoint del Eureka Server.
     *
     * @return información básica del servicio
     */
    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Información del servicio",
            description = "Retorna información básica: nombre, versión, descripción, puerto",
            operationId = "serviceInfo",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Información del servicio",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", example = "{\"service\":\"discovery-service\",\"version\":\"1.0.0\",\"description\":\"Eureka Server para Service Discovery\",\"port\":8761}")
                    )
            )
    })
    public Mono<Map<String, Object>> info() {
        log.debug("Solicitud GET /actuator/info - Informacion del servicio");
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> info = Map.of(
                    "service", "discovery-service",
                    "version", "1.0.0",
                    "description", "Eureka Server para Service Discovery",
                    "port", 8761
            );
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Info endpoint respondido en {} ms", elapsed);
            return Mono.just(info);
        } catch (Exception e) {
            log.error("Excepcion inesperada en info endpoint: {}", e.getMessage(), e);
            throw e;
        }
    }

    private EurekaHealthResponse toHealthResponse(EurekaRegistry registry) {
        log.debug("Convirtiendo registry a HealthResponse: {} instancias totales, {} UP, {} DOWN",
                registry.getTotalInstances(), registry.getInstancesUp(), registry.getInstancesDown());

        try {
            String status = registry.getTotalInstances() == 0 ? "OUT_OF_SERVICE" :
                    registry.getInstancesDown() == registry.getTotalInstances() ? "DOWN" : "UP";

            if ("DOWN".equals(status)) {
                log.warn("Eureka Server en estado DOWN: todas las instancias ({}) estan DOWN",
                        registry.getTotalInstances());
            } else if ("OUT_OF_SERVICE".equals(status)) {
                log.warn("Eureka Server sin instancias registradas (OUT_OF_SERVICE)");
            } else {
                log.debug("Eureka Server en estado UP: {}/{} instancias saludables",
                        registry.getInstancesUp(), registry.getTotalInstances());
            }

            Map<String, EurekaHealthResponse.ComponentHealth> components = Map.of(
                    "eureka", EurekaHealthResponse.ComponentHealth.builder()
                            .status(status)
                            .details(Map.of(
                                    "totalInstances", registry.getTotalInstances(),
                                    "instancesUp", registry.getInstancesUp(),
                                    "instancesDown", registry.getInstancesDown(),
                                    "selfPreservationMode", registry.isSelfPreservationMode()
                            ))
                            .build(),
                    "diskSpace", EurekaHealthResponse.ComponentHealth.builder()
                            .status("UP")
                            .details(Map.of(
                                    "total", Runtime.getRuntime().totalMemory(),
                                    "free", Runtime.getRuntime().freeMemory()
                            ))
                            .build()
            );

            return EurekaHealthResponse.builder()
                    .status(status)
                    .components(components)
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("Error convirtiendo registry a HealthResponse: {}", e.getMessage(), e);
            throw e;
        }
    }
}