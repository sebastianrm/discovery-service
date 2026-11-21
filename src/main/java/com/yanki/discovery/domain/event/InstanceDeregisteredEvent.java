package com.yanki.discovery.domain.event;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Evento de dominio emitido cuando una instancia se desregistra de Eureka (shutdown graceful).
 * <p>
 * Payload inmutable para publicación en Kafka (topic: yanki.discovery.instance.deregistered.v1).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class InstanceDeregisteredEvent {

    /**
     * Identificador único del evento (UUID).
     */
    private final String eventId;

    /**
     * ID de la instancia desregistrada.
     */
    private final String instanceId;

    /**
     * Nombre de la aplicación.
     */
    private final String appName;

    /**
     * Motivo del desregistro (SHUTDOWN, EXPIRED, MANUAL).
     */
    private final String reason;

    /**
     * Timestamp del evento (epoch millis).
     */
    private final long occurredAt;

    /**
     * Crea el evento a partir de una EurekaInstanceInfo y motivo.
     *
     * @param instanceInfo información de la instancia
     * @param reason       motivo del desregistro
     * @return evento de desregistro
     */
    public static InstanceDeregisteredEvent from(EurekaInstanceInfo instanceInfo, String reason) {
        return InstanceDeregisteredEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .instanceId(instanceInfo.getInstanceId())
                .appName(instanceInfo.getAppName())
                .reason(reason)
                .occurredAt(Instant.now().toEpochMilli())
                .build();
    }
}