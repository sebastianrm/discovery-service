package com.yanki.discovery.domain.port.out;

import com.yanki.discovery.domain.event.InstanceDeregisteredEvent;
import com.yanki.discovery.domain.event.InstanceRegisteredEvent;
import com.yanki.discovery.domain.event.InstanceStatusChangedEvent;
import io.reactivex.rxjava3.core.Completable;

/**
 * Puerto de salida para publicación de eventos de dominio hacia Apache Kafka.
 * <p>
 * Define el contrato para emitir eventos de registro, desregistro y cambio de estado
 * de instancias. La implementación usa KafkaTemplate con serialización JSON.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface EventPublisherPort {

    /**
     * Publica evento de instancia registrada.
     * Topic: yanki.discovery.instance.registered.v1
     *
     * @param event payload del evento
     * @return Completable que completa cuando la publicación es exitosa
     */
    Completable publishInstanceRegistered(InstanceRegisteredEvent event);

    /**
     * Publica evento de instancia desregistrada.
     * Topic: yanki.discovery.instance.deregistered.v1
     *
     * @param event payload del evento
     * @return Completable que completa cuando la publicación es exitosa
     */
    Completable publishInstanceDeregistered(InstanceDeregisteredEvent event);

    /**
     * Publica evento de cambio de estado de instancia.
     * Topic: yanki.discovery.instance.status.changed.v1
     *
     * @param event payload del evento
     * @return Completable que completa cuando la publicación es exitosa
     */
    Completable publishInstanceStatusChanged(InstanceStatusChangedEvent event);
}