package com.yanki.discovery.infrastructure.config;

import com.yanki.discovery.domain.port.out.ConfigPort;
import com.yanki.discovery.domain.port.out.EventPublisherPort;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import com.yanki.discovery.domain.port.out.PeerSyncPort;
import com.yanki.discovery.infrastructure.adapter.ConfigAdapter;
import com.yanki.discovery.infrastructure.adapter.EventPublisherAdapter;
import com.yanki.discovery.infrastructure.adapter.EurekaRegistryAdapter;
import com.yanki.discovery.infrastructure.adapter.PeerSyncAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración de beans de infraestructura (Adaptadores Secundarios).
 * <p>
 * Cableado de implementaciones concretas de los puertos de salida del dominio.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class InfrastructureConfig {

    @Bean
    public EurekaRegistryPort eurekaRegistryPort(com.netflix.discovery.EurekaClient eurekaClient) {
        log.info("Creando bean EurekaRegistryPort (EurekaRegistryAdapter)");
        return new EurekaRegistryAdapter(eurekaClient);
    }

    @Bean
    public EventPublisherPort eventPublisherPort(KafkaTemplate<String, Object> kafkaTemplate) {
        log.info("Creando bean EventPublisherPort (EventPublisherAdapter con Kafka)");
        return new EventPublisherAdapter(kafkaTemplate);
    }

    @Bean
    public PeerSyncPort peerSyncPort(WebClient.Builder webClientBuilder) {
        log.info("Creando bean PeerSyncPort (PeerSyncAdapter con WebClient)");
        return new PeerSyncAdapter(webClientBuilder);
    }

    @Bean
    public ConfigPort configPort(ConfigurableEnvironment environment) {
        log.info("Creando bean ConfigPort (ConfigAdapter)");
        return new ConfigAdapter(environment);
    }
}