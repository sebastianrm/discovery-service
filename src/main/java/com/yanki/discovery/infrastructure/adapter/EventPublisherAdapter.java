package com.yanki.discovery.infrastructure.adapter;

import com.yanki.discovery.domain.event.InstanceDeregisteredEvent;
import com.yanki.discovery.domain.event.InstanceRegisteredEvent;
import com.yanki.discovery.domain.event.InstanceStatusChangedEvent;
import com.yanki.discovery.domain.port.out.EventPublisherPort;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

/**
 * Adaptador de salida que implementa {@link EventPublisherPort} publicando eventos hacia Apache Kafka.
 * <p>
 * Convierte eventos de dominio (RxJava 3 {@link Completable}) a {@link Mono} de Reactor
 * para usar {@link KafkaTemplate} y serializa payloads como JSON.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisherAdapter implements EventPublisherPort {

    private static final String TOPIC_INSTANCE_REGISTERED = "yanki.discovery.instance.registered.v1";
    private static final String TOPIC_INSTANCE_DEREGISTERED = "yanki.discovery.instance.deregistered.v1";
    private static final String TOPIC_INSTANCE_STATUS_CHANGED = "yanki.discovery.instance.status.changed.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Completable publishInstanceRegistered(InstanceRegisteredEvent event) {
        log.info("Publicando InstanceRegisteredEvent: {} para instancia {}",
                event.getEventId(), event.getInstanceId());

        try {
            Mono<Void> sendMono = Mono.fromFuture(() -> kafkaTemplate.send(
                            TOPIC_INSTANCE_REGISTERED,
                            event.getInstanceId(), // key = instanceId para particionamiento
                            event
                    ))
                    .doOnNext(result -> log.info("Evento {} publicado en particion {} offset {}",
                            TOPIC_INSTANCE_REGISTERED,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()))
                    .doOnError(error -> log.error("Error enviando evento {} a Kafka: {}",
                            TOPIC_INSTANCE_REGISTERED, error.getMessage(), error))
                    .then();

            return RxJava3Adapter.monoToCompletable(sendMono);
        } catch (Exception e) {
            log.error("Excepcion publicando InstanceRegisteredEvent para {}: {}",
                    event.getInstanceId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Completable publishInstanceDeregistered(InstanceDeregisteredEvent event) {
        log.info("Publicando InstanceDeregisteredEvent: {} para instancia {} (razon: {})",
                event.getEventId(), event.getInstanceId(), event.getReason());

        try {
            Mono<Void> sendMono = Mono.fromFuture(() -> kafkaTemplate.send(
                            TOPIC_INSTANCE_DEREGISTERED,
                            event.getInstanceId(),
                            event
                    ))
                    .doOnNext(result -> log.info("Evento {} publicado en particion {} offset {}",
                            TOPIC_INSTANCE_DEREGISTERED,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()))
                    .doOnError(error -> log.error("Error enviando evento {} a Kafka: {}",
                            TOPIC_INSTANCE_DEREGISTERED, error.getMessage(), error))
                    .then();

            return RxJava3Adapter.monoToCompletable(sendMono);
        } catch (Exception e) {
            log.error("Excepcion publicando InstanceDeregisteredEvent para {}: {}",
                    event.getInstanceId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Completable publishInstanceStatusChanged(InstanceStatusChangedEvent event) {
        log.info("Publicando InstanceStatusChangedEvent: {} para instancia {} ({}: {} -> {})",
                event.getEventId(), event.getInstanceId(),
                event.getAppName(), event.getPreviousStatus(), event.getNewStatus());

        try {
            Mono<Void> sendMono = Mono.fromFuture(() -> kafkaTemplate.send(
                            TOPIC_INSTANCE_STATUS_CHANGED,
                            event.getInstanceId(),
                            event
                    ))
                    .doOnNext(result -> log.info("Evento {} publicado en particion {} offset {}",
                            TOPIC_INSTANCE_STATUS_CHANGED,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()))
                    .doOnError(error -> log.error("Error enviando evento {} a Kafka: {}",
                            TOPIC_INSTANCE_STATUS_CHANGED, error.getMessage(), error))
                    .then();

            return RxJava3Adapter.monoToCompletable(sendMono);
        } catch (Exception e) {
            log.error("Excepcion publicando InstanceStatusChangedEvent para {}: {}",
                    event.getInstanceId(), e.getMessage(), e);
            throw e;
        }
    }
}