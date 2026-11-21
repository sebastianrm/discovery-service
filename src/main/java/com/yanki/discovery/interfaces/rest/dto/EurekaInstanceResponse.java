package com.yanki.discovery.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * DTO de respuesta para información de instancia Eureka.
 * <p>
 * Expone solo los campos necesarios para la API REST, ocultando detalles internos.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Schema(description = "Información de una instancia registrada en Eureka")
public class EurekaInstanceResponse {

    @Schema(description = "Identificador único de la instancia", example = "accounts-service:172.20.0.5:8083")
    private final String instanceId;

    @Schema(description = "Nombre de la aplicación", example = "ACCOUNTS-SERVICE")
    private final String appName;

    @Schema(description = "Hostname del host", example = "172.20.0.5")
    private final String hostName;

    @Schema(description = "Dirección IP", example = "172.20.0.5")
    private final String ipAddress;

    @Schema(description = "Puerto HTTP", example = "8083")
    private final int port;

    @Schema(description = "Puerto HTTPS", example = "8443")
    private final int securePort;

    @Schema(description = "Estado de la instancia", example = "UP", allowableValues = {"UP", "DOWN", "STARTING", "OUT_OF_SERVICE", "UNKNOWN"})
    private final String status;

    @Schema(description = "Metadata rica de la instancia (version, zone, api.version, canary, weight, ...)")
    private final Map<String, String> metadata;

    @Schema(description = "Timestamp del último heartbeat (epoch millis)", example = "1731590400000")
    private final long lastRenewalTimestamp;

    @Schema(description = "Timestamp de registro inicial (epoch millis)", example = "1731590355000")
    private final long registrationTimestamp;

    @Schema(description = "Timestamp de expiración del lease (epoch millis)", example = "1731590430000")
    private final long evictionTimestamp;

    @Schema(description = "Indica si la instancia es canary deployment", example = "false")
    private final boolean canary;

    @Schema(description = "Zona de disponibilidad", example = "primary")
    private final String zone;

    @Schema(description = "Versión de API expuesta", example = "v1")
    private final String apiVersion;

    @Schema(description = "Peso para weighted load balancing", example = "100")
    private final int weight;

    @Schema(description = "Versión del servicio", example = "1.0.0")
    private final String version;
}