package com.yanki.discovery.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;

/**
 * Respuesta de error estandarizada para toda la API.
 * <p>
 * Formato unificado: timestamp, status, error, code, message, path, details.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Schema(description = "Respuesta de error estandarizada")
public class ApiError {

    @Schema(description = "Timestamp del error (ISO-8601)", example = "2026-11-14T10:30:45.123Z")
    private final String timestamp;

    @Schema(description = "Código de estado HTTP", example = "404")
    private final int status;

    @Schema(description = "Nombre del error HTTP", example = "Not Found")
    private final String error;

    @Schema(description = "Código de error de negocio único", example = "INSTANCE_NOT_FOUND")
    private final String code;

    @Schema(description = "Mensaje descriptivo para el cliente", example = "Instancia no encontrada: accounts-service:172.20.0.5:8083")
    private final String message;

    @Schema(description = "Path de la request que generó el error", example = "/eureka/apps/ACCOUNTS-SERVICE/instance-id")
    private final String path;

    @Schema(description = "Detalles técnicos adicionales")
    private final Map<String, Object> details;

    /**
     * Crea un ApiError con timestamp actual.
     */
    public static ApiError of(int status, String error, String code, String message, String path) {
        return ApiError.builder()
                .timestamp(Instant.now().toString())
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .path(path)
                .build();
    }

    /**
     * Crea un ApiError con detalles adicionales.
     */
    public static ApiError of(int status, String error, String code, String message, String path, Map<String, Object> details) {
        return ApiError.builder()
                .timestamp(Instant.now().toString())
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .path(path)
                .details(details)
                .build();
    }
}