package com.yanki.discovery.interfaces.rest.controller;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.port.in.DeregisterInstanceUseCase;
import com.yanki.discovery.domain.port.in.RegisterInstanceUseCase;
import com.yanki.discovery.domain.port.in.RenewLeaseUseCase;
import com.yanki.discovery.interfaces.rest.dto.ApiError;
import com.yanki.discovery.interfaces.rest.dto.EurekaInstanceRequest;
import com.yanki.discovery.interfaces.rest.mapper.EurekaRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para el protocolo de Eureka (registro, heartbeat, desregistro).
 * <p>
 * Endpoints usados internamente por clientes Eureka (@EnableEurekaClient).
 * No destinados a uso directo por aplicaciones externas.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/eureka/apps")
@RequiredArgsConstructor
@Tag(name = "Eureka Protocol", description = "Endpoints internos del protocolo Eureka (clientes Eureka)")
@SecurityRequirement(name = "eurekaAuth")
public class EurekaProtocolController {

    private final RegisterInstanceUseCase registerUseCase;
    private final RenewLeaseUseCase renewLeaseUseCase;
    private final DeregisterInstanceUseCase deregisterUseCase;
    private final EurekaRestMapper mapper;

    /**
     * Registro de instancia (POST /eureka/apps/{appName}).
     * <p>
     * Invocado por clientes Eureka al arrancar para registrarse.
     * </p>
     *
     * @param appName        nombre de la aplicación
     * @param instanceRequest datos de la instancia (JSON body)
     * @return 204 No Content
     */
    @PostMapping(value = "/{appName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Registrar instancia en Eureka",
            description = """
                    Endpoint interno para registro automático de clientes Eureka al arrancar.
                    
                    **Flujo:**
                    1. Cliente Eureka inicia y obtiene config de Config Server
                    2. Cliente envía POST con InstanceInfo completo + metadata
                    3. Eureka Server valida, persiste en registry, retorna 204
                    4. Cliente inicia heartbeats cada 10s (PUT)
                    
                    **Metadata obligatoria en request:**
                    - `instanceId`: Formato `service:ip:port`
                    - `hostName`, `ipAddr`, `port`, `securePort`
                    - `metadata`: version, zone, api.version, canary, weight, deployment.environment, management.port
                    """,
            operationId = "registerInstance",
            security = @SecurityRequirement(name = "eurekaAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Instancia registrada exitosamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request inválido - JSON malformado o campos obligatorios faltantes",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Eureka requeridas en producción",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflicto - Instancia ya registrada con mismo ID",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<Void> register(
            @Parameter(
                    name = "appName",
                    description = "Nombre de la aplicación en MAYÚSCULAS",
                    example = "ACCOUNTS-SERVICE",
                    required = true,
                    schema = @Schema(type = "string", pattern = "^[A-Z0-9-]+$")
            )
            @PathVariable String appName,
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Información completa de la instancia a registrar",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EurekaInstanceRequest.class)
                    )
            )
            EurekaInstanceRequest instanceRequest) {
        log.info("Solicitud POST /eureka/apps/{} - Registrando instancia {} ({}:{})",
                appName, instanceRequest.getInstanceId(), instanceRequest.getHostName(), instanceRequest.getPort());
        long startTime = System.currentTimeMillis();

        try {
            EurekaInstanceInfo instanceInfo = parseInstanceInfo(appName, instanceRequest);
            log.debug("InstanceInfo parseado: {} con metadata: {}",
                    instanceInfo.getInstanceId(), instanceInfo.getMetadata());

            return RxJava3Adapter.completableToMono(registerUseCase.execute(instanceInfo))
                    .doOnSuccess(v -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("Instancia registrada exitosamente: {} ({}) en {} ms",
                                instanceInfo.getInstanceId(), appName, elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error registrando instancia {} despues de {} ms: {}",
                                instanceInfo.getInstanceId(), elapsed, error.getMessage(), error);
                    });
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Excepcion inesperada al registrar instancia despues de {} ms: {}",
                    elapsed, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Heartbeat / renovación de lease (PUT /eureka/apps/{appName}/{instanceId}).
     * <p>
     * Invocado por clientes Eureka cada 10s para mantener el lease activo.
     * </p>
     *
     * @param appName     nombre de la aplicación
     * @param instanceId  ID de la instancia
     * @return 200 OK
     */
    @PutMapping("/{appName}/{instanceId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Renovar lease (heartbeat)",
            description = """
                    Endpoint interno para heartbeat periódico de clientes Eureka.
                    
                    **Frecuencia:** Cada 10 segundos (configurable en cliente: `lease-renewal-interval-in-seconds`)
                    
                    **Comportamiento:**
                    - Actualiza `lastRenewalTimestamp` en registry
                    - Mantiene status UP si health check OK
                    - Si 3 heartbeats fallidos (30s) → marca DOWN → evicción tras intervalo
                    """,
            operationId = "renewLease",
            security = @SecurityRequirement(name = "eurekaAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Lease renovado exitosamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Eureka requeridas",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Instancia no encontrada en el registry",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "Gone - Instancia expirada y evictada del registry",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<Void> renewLease(
            @Parameter(
                    name = "appName",
                    description = "Nombre de la aplicación en MAYÚSCULAS",
                    example = "ACCOUNTS-SERVICE",
                    required = true
            )
            @PathVariable String appName,
            @Parameter(
                    name = "instanceId",
                    description = "Identificador único de la instancia (formato: service:ip:port)",
                    example = "accounts-service:172.20.0.5:8083",
                    required = true
            )
            @PathVariable String instanceId) {
        log.debug("Solicitud PUT /eureka/apps/{}/{} - Renovando lease (heartbeat)", appName, instanceId);
        long startTime = System.currentTimeMillis();

        try {
            return RxJava3Adapter.completableToMono(renewLeaseUseCase.execute(instanceId))
                    .doOnSuccess(v -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.debug("Lease renovado para {} en {} ms", instanceId, elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error renovando lease de {} despues de {} ms: {}",
                                instanceId, elapsed, error.getMessage(), error);
                    });
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Excepcion inesperada al renovar lease de {} despues de {} ms: {}",
                    instanceId, elapsed, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Desregistro de instancia (DELETE /eureka/apps/{appName}/{instanceId}).
     * <p>
     * Invocado por clientes Eureka en shutdown graceful.
     * </p>
     *
     * @param appName     nombre de la aplicación
     * @param instanceId  ID de la instancia
     * @return 200 OK
     */
    @DeleteMapping("/{appName}/{instanceId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Desregistrar instancia de Eureka",
            description = """
                    Endpoint interno para desregistro graceful de clientes Eureka.
                    
                    **Flujo:**
                    1. Cliente recibe señal de terminación (SIGTERM)
                    2. Cliente envía DELETE a su propia instancia
                    3. Eureka elimina del registry inmediatamente
                    4. Publica evento `yanki.discovery.instance.deregistered.v1`
                    5. Réplica desregistro a peers (HA cluster)
                    """,
            operationId = "deregisterInstance",
            security = @SecurityRequirement(name = "eurekaAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Instancia desregistrada exitosamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Eureka requeridas",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Instancia no encontrada (idempotente: no falla si ya no existe)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<Void> deregister(
            @Parameter(
                    name = "appName",
                    description = "Nombre de la aplicación en MAYÚSCULAS",
                    example = "ACCOUNTS-SERVICE",
                    required = true
            )
            @PathVariable String appName,
            @Parameter(
                    name = "instanceId",
                    description = "Identificador único de la instancia (formato: service:ip:port)",
                    example = "accounts-service:172.20.0.5:8083",
                    required = true
            )
            @PathVariable String instanceId) {
        log.info("Solicitud DELETE /eureka/apps/{}/{} - Desregistrando instancia", appName, instanceId);
        long startTime = System.currentTimeMillis();

        try {
            return RxJava3Adapter.completableToMono(deregisterUseCase.execute(instanceId))
                    .doOnSuccess(v -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("Instancia desregistrada exitosamente: {} ({}) en {} ms",
                                instanceId, appName, elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error desregistrando instancia {} despues de {} ms: {}",
                                instanceId, elapsed, error.getMessage(), error);
                    });
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Excepcion inesperada al desregistrar instancia {} despues de {} ms: {}",
                    instanceId, elapsed, e.getMessage(), e);
            throw e;
        }
    }

    // ===== Helper para parsing =====

    private EurekaInstanceInfo parseInstanceInfo(String appName, EurekaInstanceRequest request) {
        log.debug("Parseando InstanceInfo para {}: host={}, ip={}, port={}, securePort={}",
                request.getInstanceId(), request.getHostName(), request.getIpAddress(),
                request.getPort(), request.getSecurePort());

        try {
            EurekaInstanceInfo instanceInfo = EurekaInstanceInfo.builder()
                    .instanceId(request.getInstanceId())
                    .appName(appName.toUpperCase())
                    .hostName(request.getHostName())
                    .ipAddress(request.getIpAddress())
                    .port(request.getPort())
                    .securePort(request.getSecurePort())
                    .status(EurekaInstanceInfo.InstanceStatus.UP)
                    .metadata(request.getMetadata())
                    .lastRenewalTimestamp(System.currentTimeMillis())
                    .registrationTimestamp(System.currentTimeMillis())
                    .evictionTimestamp(0)
                    .build();

            log.debug("InstanceInfo creado: {} -> {} (status: UP, metadata keys: {})",
                    request.getInstanceId(), instanceInfo.getAppName(),
                    instanceInfo.getMetadata() != null ? instanceInfo.getMetadata().keySet().size() : 0);

            return instanceInfo;
        } catch (Exception e) {
            log.error("Error parseando InstanceInfo para {}: {}", request.getInstanceId(), e.getMessage(), e);
            throw e;
        }
    }
}