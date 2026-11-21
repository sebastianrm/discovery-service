package com.yanki.discovery.interfaces.rest.controller;

import com.yanki.discovery.domain.port.in.GetApplicationInstancesUseCase;
import com.yanki.discovery.domain.port.in.GetRegistryUseCase;
import com.yanki.discovery.interfaces.rest.dto.ApiError;
import com.yanki.discovery.interfaces.rest.dto.EurekaInstanceResponse;
import com.yanki.discovery.interfaces.rest.dto.EurekaRegistryResponse;
import com.yanki.discovery.interfaces.rest.mapper.EurekaRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * Controlador REST para consultar el registry de Eureka.
 * <p>
 * Expone endpoints para descubrir servicios e instancias registradas.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/eureka")
@RequiredArgsConstructor
@Tag(name = "Eureka Registry", description = "Consulta de servicios e instancias registradas en Eureka")
@SecurityRequirement(name = "basicAuth")
public class EurekaRegistryController {

    private final GetRegistryUseCase getRegistryUseCase;
    private final GetApplicationInstancesUseCase getInstancesUseCase;
    private final EurekaRestMapper mapper;

    /**
     * Obtiene el registry completo con todas las aplicaciones e instancias.
     *
     * @return registry completo
     */
    @GetMapping(value = "/apps", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene el registry completo de Eureka",
            description = """
                    Retorna todas las aplicaciones registradas con sus instancias y metadata completa.
                    
                    **Uso típico:** Monitoreo, debugging, descubrimiento manual de servicios.
                    
                    **Respuesta incluye:**
                    - Lista de aplicaciones con contador de instancias
                    - Por instancia: Instance ID, Host, Port, Status, Metadata, Lease timestamps
                    - Métricas globales: total, UP, DOWN, self-preservation mode
                    """,
            operationId = "getRegistry",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Registry obtenido exitosamente",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EurekaRegistryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Basic Auth requeridas",
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
    public Mono<EurekaRegistryResponse> getRegistry() {
        log.info("Solicitud GET /eureka/apps - Obteniendo registry completo de Eureka");
        long startTime = System.currentTimeMillis();

        try {
            return RxJava3Adapter.singleToMono(getRegistryUseCase.execute())
                    .map(mapper::toRegistryResponse)
                    .doOnSuccess(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        int totalApps = response.getApplications() != null ? response.getApplications().size() : 0;
                        log.info("Registry obtenido exitosamente: {} aplicaciones, {} instancias totales en {} ms",
                                totalApps, response.getTotalInstances(), elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error obteniendo registry completo despues de {} ms: {}", elapsed, error.getMessage(), error);
                    });
        } catch (Exception e) {
            log.error("Excepcion inesperada al obtener registry: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Obtiene todas las instancias de una aplicación específica.
     *
     * @param appName nombre de la aplicación (ej. ACCOUNTS-SERVICE)
     * @return lista de instancias
     */
    @GetMapping(value = "/apps/{appName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene instancias de una aplicación",
            description = """
                    Retorna todas las instancias (UP y DOWN) de la aplicación especificada.
                    
                    **Parámetros:**
                    - `appName`: Nombre de la aplicación en MAYÚSCULAS (ej. `ACCOUNTS-SERVICE`)
                    
                    **Respuesta:** Array de instancias con metadata completa.
                    """,
            operationId = "getApplicationInstances",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Instancias obtenidas exitosamente",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "array", implementation = EurekaInstanceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Basic Auth requeridas",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Aplicación no encontrada en el registry",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<Collection<EurekaInstanceResponse>> getInstances(
            @Parameter(
                    name = "appName",
                    description = "Nombre de la aplicación en MAYÚSCULAS",
                    example = "ACCOUNTS-SERVICE",
                    required = true,
                    schema = @Schema(type = "string", pattern = "^[A-Z0-9-]+$")
            )
            @PathVariable String appName) {
        log.info("Solicitud GET /eureka/apps/{} - Obteniendo todas las instancias", appName);
        long startTime = System.currentTimeMillis();

        try {
            Mono<List<EurekaInstanceResponse>> resultMono = RxJava3Adapter.singleToMono(getInstancesUseCase.execute(appName))
                    .map(mapper::toInstanceResponseList)
                    .doOnSuccess(instances -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("Instancias obtenidas para {}: {} instancias en {} ms", appName, instances.size(), elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error obteniendo instancias de {} despues de {} ms: {}", appName, elapsed, error.getMessage(), error);
                    });
            return (Mono<Collection<EurekaInstanceResponse>>) (Mono<?>) resultMono;
        } catch (Exception e) {
            log.error("Excepcion inesperada al obtener instancias de {}: {}", appName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Obtiene solo las instancias UP (healthy) de una aplicación.
     *
     * @param appName nombre de la aplicación
     * @return lista de instancias healthy
     */
    @GetMapping(value = "/apps/{appName}/healthy", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene instancias healthy de una aplicación",
            description = """
                    Retorna solo instancias con status UP, útil para load balancing.
                    
                    **Diferencia con `/apps/{appName}`:** Filtra solo instancias `status=UP`.
                    Ideal para API Gateway y clientes que necesitan endpoints saludables.
                    """,
            operationId = "getHealthyInstances",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Instancias healthy obtenidas",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "array", implementation = EurekaInstanceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Basic Auth requeridas",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public Mono<Collection<EurekaInstanceResponse>> getHealthyInstances(
            @Parameter(
                    name = "appName",
                    description = "Nombre de la aplicación en MAYÚSCULAS",
                    example = "ACCOUNTS-SERVICE",
                    required = true
            )
            @PathVariable String appName) {
        log.info("Solicitud GET /eureka/apps/{}/healthy - Obteniendo instancias healthy", appName);
        long startTime = System.currentTimeMillis();

        try {
            Mono<List<EurekaInstanceResponse>> resultMono = RxJava3Adapter.singleToMono(getInstancesUseCase.executeHealthyOnly(appName))
                    .map(mapper::toInstanceResponseList)
                    .doOnSuccess(instances -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("Instancias healthy obtenidas para {}: {} instancias UP en {} ms", appName, instances.size(), elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error obteniendo instancias healthy de {} despues de {} ms: {}", appName, elapsed, error.getMessage(), error);
                    });
            return (Mono<Collection<EurekaInstanceResponse>>) (Mono<?>) resultMono;
        } catch (Exception e) {
            log.error("Excepcion inesperada al obtener instancias healthy de {}: {}", appName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Obtiene una instancia específica por su ID.
     *
     * @param appName     nombre de la aplicación
     * @param instanceId  identificador único de la instancia
     * @return instancia si existe
     */
    @GetMapping(value = "/apps/{appName}/{instanceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene una instancia por ID",
            description = """
                    Retorna el detalle de una instancia específica.
                    
                    **Parámetros:**
                    - `appName`: Nombre de la aplicación (ej. `ACCOUNTS-SERVICE`)
                    - `instanceId`: ID completo formato `service-name:ip:port` (ej. `accounts-service:172.20.0.5:8083`)
                    """,
            operationId = "getInstanceById",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Instancia encontrada",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EurekaInstanceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado - Credenciales Basic Auth requeridas",
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
            )
    })
    public Mono<EurekaInstanceResponse> getInstance(
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
        log.info("Solicitud GET /eureka/apps/{}/{} - Buscando instancia por ID", appName, instanceId);
        long startTime = System.currentTimeMillis();

        try {
            Mono<EurekaInstanceResponse> resultMono = RxJava3Adapter.maybeToMono(getInstancesUseCase.findByInstanceId(instanceId))
                    .map(mapper::toInstanceResponse)
                    .doOnSuccess(response -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("Instancia encontrada: {} ({}) en {} ms - status: {}",
                                instanceId, response.getHostName(), elapsed, response.getStatus());
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("Error buscando instancia {} despues de {} ms: {}", instanceId, elapsed, error.getMessage(), error);
                    });

            return resultMono.switchIfEmpty(
                    Mono.<EurekaInstanceResponse>empty().doFirst(() -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.warn("Instancia no encontrada: {} en {} ms", instanceId, elapsed);
                    }));
        } catch (Exception e) {
            log.error("Excepcion inesperada al buscar instancia {}: {}", instanceId, e.getMessage(), e);
            throw e;
        }
    }
}