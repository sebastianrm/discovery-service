package com.yanki.discovery.infrastructure.adapter;

import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.out.PeerSyncPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adaptador de salida que implementa {@link PeerSyncPort} para sincronización
 * de registry entre nodos Eureka en cluster HA (peer-to-peer).
 * <p>
 * Usa {@link WebClient} reactivo para comunicación HTTP entre peers Eureka.
 * Timeouts configurados para fail-fast en particiones de red.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeerSyncAdapter implements PeerSyncPort {

    @Value("${eureka.client.service-url.defaultZone:}")
    private String defaultZone;

    private final WebClient.Builder webClientBuilder;

    @Override
    public Single<List<String>> getPeerUrls() {
        return Single.fromCallable(() -> parsePeerUrls(defaultZone))
                .doOnSuccess(urls -> {
                    if (urls.isEmpty()) {
                        log.info("No hay peers configurados (modo standalone)");
                    } else {
                        log.info("Peers configurados: {}", urls);
                    }
                })
                .doOnError(error -> log.error("Error obteniendo URLs de peers: {}",
                        error.getMessage(), error));
    }

    @Override
    public Completable syncWithPeer(String peerUrl) {
        log.info("Sincronizando registry con peer: {}", peerUrl);

        try {
            WebClient webClient = buildWebClient(peerUrl);
            Mono<Void> syncMono = webClient.get()
                    .uri("/eureka/apps")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(response -> log.info("Registry sincronizado desde peer: {}", peerUrl))
                    .doOnError(error -> log.error("Error obteniendo registry de peer {}: {}",
                            peerUrl, error.getMessage(), error))
                    .then();

            return RxJava3Adapter.monoToCompletable(syncMono)
                    .onErrorResumeNext(error -> {
                        log.warn("Error sincronizando con peer {}: {}", peerUrl, error.getMessage(), error);
                        return Completable.complete(); // No propagar error, log only
                    });
        } catch (Exception e) {
            log.error("Excepcion sincronizando con peer {}: {}", peerUrl, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Completable replicateToPeers(List<String> peerUrls, String instanceId) {
        log.info("Replicando instancia {} a {} peers", instanceId, peerUrls.size());

        if (peerUrls.isEmpty()) {
            log.debug("No hay peers para replicar, omitiendo");
            return Completable.complete();
        }

        try {
            return Completable.merge(
                    peerUrls.stream()
                            .map(peerUrl -> replicateToSinglePeer(peerUrl, instanceId))
                            .collect(Collectors.toList())
            );
        } catch (Exception e) {
            log.error("Excepcion replicando instancia {} a peers: {}",
                    instanceId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Completable replicateDeregistrationToPeers(List<String> peerUrls, String instanceId) {
        log.info("Replicando desregistro de instancia {} a {} peers", instanceId, peerUrls.size());

        if (peerUrls.isEmpty()) {
            log.debug("No hay peers para replicar desregistro, omitiendo");
            return Completable.complete();
        }

        try {
            return Completable.merge(
                    peerUrls.stream()
                            .map(peerUrl -> replicateDeregistrationToPeer(peerUrl, instanceId))
                            .collect(Collectors.toList())
            );
        } catch (Exception e) {
            log.error("Excepcion replicando desregistro de {} a peers: {}",
                    instanceId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Single<EurekaRegistry> fetchRegistryFromPeer(String peerUrl) {
        log.info("Obteniendo registry completo desde peer: {}", peerUrl);

        try {
            WebClient webClient = buildWebClient(peerUrl);
            Mono<EurekaRegistry> fetchMono = webClient.get()
                    .uri("/eureka/apps")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseRegistryJson)
                    .doOnSuccess(registry -> log.info("Registry obtenido de peer {}: {} apps",
                            peerUrl, registry.getApplicationNames().size()))
                    .doOnError(error -> log.error("Error obteniendo registry de peer {}: {}",
                            peerUrl, error.getMessage(), error));

            return RxJava3Adapter.monoToSingle(fetchMono);
        } catch (Exception e) {
            log.error("Excepcion obteniendo registry de peer {}: {}",
                    peerUrl, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Single<Boolean> checkPeerHealth(String peerUrl) {
        log.debug("Verificando salud de peer: {}", peerUrl);

        try {
            WebClient webClient = buildWebClient(peerUrl);
            Mono<Boolean> healthMono = webClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> response.contains("\"status\":\"UP\""))
                    .doOnSuccess(isHealthy -> {
                        if (Boolean.TRUE.equals(isHealthy)) {
                            log.debug("Peer {} esta saludable", peerUrl);
                        } else {
                            log.warn("Peer {} no esta saludable", peerUrl);
                        }
                    })
                    .onErrorReturn(false)
                    .timeout(Duration.ofSeconds(2));

            return RxJava3Adapter.monoToSingle(healthMono);
        } catch (Exception e) {
            log.error("Excepcion verificando salud de peer {}: {}",
                    peerUrl, e.getMessage(), e);
            throw e;
        }
    }

    // ===== Métodos privados =====

    private List<String> parsePeerUrls(String defaultZone) {
        if (defaultZone == null || defaultZone.isBlank()) {
            return List.of();
        }
        return Arrays.stream(defaultZone.split(","))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .collect(Collectors.toList());
    }

    private WebClient buildWebClient(String baseUrl) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    private Completable replicateToSinglePeer(String peerUrl, String instanceId) {
        log.debug("Replicando a peer individual: {}", peerUrl);
        WebClient webClient = buildWebClient(peerUrl);
        Mono<Void> replicateMono = webClient.post()
                .uri("/eureka/apps/{appName}/{instanceId}/replicate", instanceId.split(":")[0], instanceId)
                .retrieve()
                .toBodilessEntity()
                .doOnNext(response -> log.debug("Replicacion a peer {} completada: {}",
                        peerUrl, response.getStatusCode()))
                .then();

        return RxJava3Adapter.monoToCompletable(replicateMono)
                .onErrorResumeNext(error -> {
                    log.warn("Error replicando a peer {}: {}", peerUrl, error.getMessage(), error);
                    return Completable.complete();
                });
    }

    private Completable replicateDeregistrationToPeer(String peerUrl, String instanceId) {
        log.debug("Replicando desregistro a peer individual: {}", peerUrl);
        WebClient webClient = buildWebClient(peerUrl);
        Mono<Void> replicateMono = webClient.delete()
                .uri("/eureka/apps/{appName}/{instanceId}", instanceId.split(":")[0], instanceId)
                .retrieve()
                .toBodilessEntity()
                .doOnNext(response -> log.debug("Replicacion desregistro a peer {} completada: {}",
                        peerUrl, response.getStatusCode()))
                .then();

        return RxJava3Adapter.monoToCompletable(replicateMono)
                .onErrorResumeNext(error -> {
                    log.warn("Error replicando desregistro a peer {}: {}", peerUrl, error.getMessage(), error);
                    return Completable.complete();
                });
    }

    private EurekaRegistry parseRegistryJson(String json) {
        log.debug("Parseando JSON de registry desde peer");
        // Simplificado: en implementacion real usar ObjectMapper para deserializar
        // Eureka devuelve JSON con estructura: {"applications": {"application": [...]}}
        return EurekaRegistry.empty(); // Placeholder
    }
}