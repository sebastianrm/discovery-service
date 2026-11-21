package com.yanki.discovery.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * DTO de respuesta para el registry completo de Eureka.
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Schema(description = "Estado completo del registry de Eureka")
public class EurekaRegistryResponse {

    @Schema(description = "Lista de aplicaciones con sus instancias")
    private final List<ApplicationResponse> applications;

    @Schema(description = "Timestamp de última actualización (epoch millis)", example = "1731590400000")
    private final long lastUpdatedTimestamp;

    @Schema(description = "Modo self-preservation activo", example = "false")
    private final boolean selfPreservationMode;

    @Schema(description = "Total de instancias registradas", example = "15")
    private final int totalInstances;

    @Schema(description = "Instancias con status UP", example = "14")
    private final int instancesUp;

    @Schema(description = "Instancias con status DOWN", example = "1")
    private final int instancesDown;

    @Getter
    @Builder(toBuilder = true)
    @ToString
    @Schema(description = "Aplicación registrada con sus instancias")
    public static class ApplicationResponse {

        @Schema(description = "Nombre de la aplicación", example = "ACCOUNTS-SERVICE")
        private final String name;

        @Schema(description = "Instancias de la aplicación")
        private final List<EurekaInstanceResponse> instances;
    }
}