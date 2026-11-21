package com.yanki.discovery.interfaces.rest.exception;

import com.yanki.discovery.domain.exception.DiscoveryBusinessException;
import com.yanki.discovery.interfaces.rest.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Manejador global de excepciones para la API REST.
 * <p>
 * Mapea excepciones de dominio ({@link DiscoveryBusinessException}) a respuestas
 * HTTP estandarizadas ({@link ApiError}) con códigos de estado apropiados.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
@Tag(name = "Error Handling", description = "Manejador global de excepciones - respuestas de error estandarizadas")
public class GlobalExceptionHandler {

    /**
     * Maneja excepciones de negocio del dominio.
     *
     * @param ex       excepción de negocio
     * @param exchange intercambio del servidor web
     * @return respuesta de error estandarizada
     */
    @ExceptionHandler(DiscoveryBusinessException.class)
    @Operation(
            summary = "Error de negocio del Discovery Service",
            description = """
                    Maneja excepciones de negocio con códigos de error estandarizados.
                    
                    **Códigos de error posibles:**
                    - `INSTANCE_NOT_FOUND` (404): Instancia no encontrada en el registry
                    - `APPLICATION_NOT_FOUND` (404): Aplicación no registrada en Eureka
                    - `INVALID_INSTANCE_METADATA` (400): Metadata de instancia inválida o incompleta
                    - `EUREKA_CONFIG_ERROR` (500): Error en la configuración de Eureka Server
                    - `PEER_COMMUNICATION_ERROR` (503): Error de comunicación con peer Eureka (HA cluster)
                    - `REGISTRY_REPLICATION_ERROR` (503): Error en la replicación del registry entre peers
                    - `SECURITY_CONFIG_ERROR` (403): Configuración de seguridad inválida
                    - `SELF_PRESERVATION_UNEXPECTED` (409): Self-preservation mode activado inesperadamente
                    """,
            operationId = "handleBusinessException",
            hidden = true
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Metadata de instancia inválida",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "INVALID_INSTANCE_METADATA",
                                    summary = "Metadata inválida",
                                    value = """
                                            {
                                              "timestamp": "2026-11-14T10:30:45.123Z",
                                              "status": 400,
                                              "error": "Bad Request",
                                              "code": "INVALID_INSTANCE_METADATA",
                                              "message": "Metadata incompleta: faltan campos obligatorios",
                                              "path": "/eureka/apps/ACCOUNTS-SERVICE"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Error de configuración de seguridad",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - Instancia o aplicación no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "INSTANCE_NOT_FOUND",
                                    summary = "Instancia no encontrada",
                                    value = """
                                            {
                                              "timestamp": "2026-11-14T10:30:45.123Z",
                                              "status": 404,
                                              "error": "Not Found",
                                              "code": "INSTANCE_NOT_FOUND",
                                              "message": "Instancia no encontrada: accounts-service:172.20.0.5:8083",
                                              "path": "/eureka/apps/ACCOUNTS-SERVICE/accounts-service:172.20.0.5:8083"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Self-preservation mode activado inesperadamente",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error - Error de configuración de Eureka",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Service Unavailable - Error de comunicación con peer o replicación",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<ResponseEntity<ApiError>> handleBusinessException(DiscoveryBusinessException ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id") != null
                ? exchange.getRequest().getHeaders().getFirst("X-Trace-Id") : "N/A";

        try {
            log.warn("Error de negocio [{}] - code={}, message={}, path={}, traceId={}",
                    method, ex.getCode(), ex.getMessage(), path, traceId);

            HttpStatus status = mapBusinessCodeToHttpStatus(ex.getCode());
            ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), ex.getCode(), ex.getMessage(), path);

            log.debug("Respuesta de error generada: status={}, code={}, path={}",
                    status.value(), ex.getCode(), path);

            return Mono.just(ResponseEntity.status(status).body(error));
        } catch (Exception e) {
            log.error("Error inesperado al manejar excepcion de negocio [{}]: {}",
                    ex.getCode(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Maneja excepciones genéricas no controladas.
     *
     * @param ex       excepción
     * @param exchange intercambio del servidor web
     * @return respuesta de error interno
     */
    @ExceptionHandler(Exception.class)
    @Operation(
            summary = "Error interno no controlado",
            description = "Cualquier excepción no manejada retorna 500 Internal Server Error con estructura estandarizada.",
            operationId = "handleGenericException",
            hidden = true
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error - Error no controlado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "INTERNAL_SERVER_ERROR",
                                    summary = "Error interno genérico",
                                    value = """
                                            {
                                              "timestamp": "2026-11-14T10:30:45.123Z",
                                              "status": 500,
                                              "error": "Internal Server Error",
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "Error interno del servidor",
                                              "path": "/eureka/apps",
                                              "details": {
                                                "exception": "NullPointerException"
                                              }
                                            }
                                            """
                            )
                    )
            )
    })
    public Mono<ResponseEntity<ApiError>> handleGenericException(Exception ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id") != null
                ? exchange.getRequest().getHeaders().getFirst("X-Trace-Id") : "N/A";

        try {
            log.error("Error interno no controlado [{}] - path={}, traceId={}, exceptionType={}, message={}",
                    method, path, traceId, ex.getClass().getSimpleName(), ex.getMessage(), ex);

            ApiError error = ApiError.of(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                    "INTERNAL_SERVER_ERROR",
                    "Error interno del servidor",
                    path,
                    Map.of("exception", ex.getClass().getSimpleName())
            );

            log.debug("Respuesta de error generada: status=500, path={}", path);

            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
        } catch (Exception e) {
            log.error("Error critico al manejar excepcion generica en {}: {}",
                    path, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Mapea códigos de error de negocio a códigos HTTP.
     *
     * @param code código de error de negocio
     * @return HttpStatus correspondiente
     */
    private HttpStatus mapBusinessCodeToHttpStatus(String code) {
        return switch (code) {
            case DiscoveryBusinessException.INSTANCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DiscoveryBusinessException.APPLICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DiscoveryBusinessException.INVALID_INSTANCE_METADATA -> HttpStatus.BAD_REQUEST;
            case DiscoveryBusinessException.EUREKA_CONFIG_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case DiscoveryBusinessException.PEER_COMMUNICATION_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case DiscoveryBusinessException.REGISTRY_REPLICATION_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case DiscoveryBusinessException.SECURITY_CONFIG_ERROR -> HttpStatus.FORBIDDEN;
            case DiscoveryBusinessException.SELF_PRESERVATION_UNEXPECTED -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}