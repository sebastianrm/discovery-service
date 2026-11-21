package com.yanki.discovery.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * DTO de respuesta para health check de Eureka Server.
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Schema(description = "Health check del Eureka Server")
public class EurekaHealthResponse {

    @Schema(description = "Estado general", example = "UP", allowableValues = {"UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"})
    private final String status;

    @Schema(description = "Detalles de componentes")
    private final Map<String, ComponentHealth> components;

    @Schema(description = "Timestamp del check (epoch millis)", example = "1731590400000")
    private final long timestamp;

    @Getter
    @Builder(toBuilder = true)
    @ToString
    @Schema(description = "Salud de un componente individual")
    public static class ComponentHealth {

        @Schema(description = "Estado del componente", example = "UP")
        private final String status;

        @Schema(description = "Detalles adicionales")
        private final Map<String, Object> details;
    }
}