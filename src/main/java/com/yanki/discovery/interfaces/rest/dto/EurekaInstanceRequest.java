package com.yanki.discovery.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * DTO de request para registro de instancia en Eureka.
 * <p>
 * Define la estructura esperada en el body del POST /eureka/apps/{appName}.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Schema(description = "Datos de instancia para registro en Eureka")
public class EurekaInstanceRequest {

    @NotBlank(message = "instanceId es obligatorio")
    @Pattern(regexp = "^[a-z0-9-]+:[0-9.]+:[0-9]+$", message = "instanceId debe tener formato service:ip:port")
    @Schema(
            description = "Identificador único de la instancia",
            example = "accounts-service:172.20.0.5:8083",
            requiredMode = Schema.RequiredMode.REQUIRED,
            pattern = "^[a-z0-9-]+:[0-9.]+:[0-9]+$"
    )
    private final String instanceId;

    @NotBlank(message = "hostName es obligatorio")
    @Schema(
            description = "Hostname del host",
            example = "172.20.0.5",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String hostName;

    @NotBlank(message = "ipAddr es obligatorio")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$", message = "ipAddr debe ser una IPv4 válida")
    @Schema(
            description = "Dirección IPv4 del host",
            example = "172.20.0.5",
            requiredMode = Schema.RequiredMode.REQUIRED,
            pattern = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"
    )
    private final String ipAddress;

    @NotNull(message = "port es obligatorio")
    @Positive(message = "port debe ser positivo")
    @Schema(
            description = "Puerto HTTP de la instancia",
            example = "8083",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1",
            maximum = "65535"
    )
    private final Integer port;

    @Schema(
            description = "Puerto HTTPS de la instancia (opcional)",
            example = "8443",
            minimum = "1",
            maximum = "65535"
    )
    private final Integer securePort;

    @Schema(
            description = "Metadata rica de la instancia",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"version\":\"1.0.0\",\"zone\":\"primary\",\"api.version\":\"v1\",\"canary\":\"false\",\"weight\":\"100\",\"deployment.environment\":\"docker\",\"management.port\":\"8083\"}"
    )
    private final Map<String, String> metadata;
}