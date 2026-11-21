package com.yanki.discovery.domain.event;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Evento de dominio emitido cuando cambia el estado de salud de una instancia (UP/DOWN/OUT_OF_SERVICE).
 * <p>
 * Payload inmutable para publicación en Kafka (topic: yanki.discovery.instance.status.changed.v1).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class InstanceStatusChangedEvent {

    /**
     * Identificador único del evento (UUID).
     */
    private final String eventId;

    /**
     * ID de la instancia.
     */
    private final String instanceId;

    /**
     * Nombre de la aplicación.
     */
    private final String appName;

    /**
     * Estado anterior.
     */
    private final String previousStatus;

    /**
     * Estado nuevo.
     */
    private final String newStatus;

    /**
     * Timestamp del evento (epoch millis).
     */
    private final long occurredAt;

    /**
     * Crea el evento con transición de estado.
     *
     * @param instanceInfo   información de la instancia
     * @param previousStatus estado anterior
     * @param newStatus      estado nuevo
     * @return evento de cambio de estado
     */
    public static InstanceStatusChangedEvent from(EurekaInstanceInfo instanceInfo,
                                                  String previousStatus,
                                                  String newStatus) {
        return InstanceStatusChangedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .instanceId(instanceInfo.getInstanceId())
                .appName(instanceInfo.getAppName())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .occurredAt(Instant.now().toEpochMilli())
                .build();
    }
}