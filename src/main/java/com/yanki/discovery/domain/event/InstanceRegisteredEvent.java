package com.yanki.discovery.domain.event;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Evento de dominio emitido cuando una instancia se registra exitosamente en Eureka.
 * <p>
 * Payload inmutable para publicación en Kafka (topic: yanki.discovery.instance.registered.v1).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class InstanceRegisteredEvent {

    /**
     * Identificador único del evento (UUID).
     */
    private final String eventId;

    /**
     * ID de la instancia registrada.
     */
    private final String instanceId;

    /**
     * Nombre de la aplicación.
     */
    private final String appName;

    /**
     * Hostname de la instancia.
     */
    private final String hostName;

    /**
     * IP de la instancia.
     */
    private final String ipAddress;

    /**
     * Puerto HTTP.
     */
    private final int port;

    /**
     * Metadata completa de la instancia.
     */
    private final java.util.Map<String, String> metadata;

    /**
     * Timestamp del evento (epoch millis).
     */
    private final long occurredAt;

    /**
     * Crea el evento a partir de una EurekaInstanceInfo.
     *
     * @param instanceInfo información de la instancia
     * @return evento de registro
     */
    public static InstanceRegisteredEvent from(EurekaInstanceInfo instanceInfo) {
        return InstanceRegisteredEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .instanceId(instanceInfo.getInstanceId())
                .appName(instanceInfo.getAppName())
                .hostName(instanceInfo.getHostName())
                .ipAddress(instanceInfo.getIpAddress())
                .port(instanceInfo.getPort())
                .metadata(instanceInfo.getMetadata())
                .occurredAt(Instant.now().toEpochMilli())
                .build();
    }
}