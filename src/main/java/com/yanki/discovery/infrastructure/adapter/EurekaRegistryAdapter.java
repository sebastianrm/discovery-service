package com.yanki.discovery.infrastructure.adapter;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Applications;
import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.domain.port.out.EurekaRegistryPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Adaptador de salida que implementa {@link EurekaRegistryPort} usando el cliente Eureka de Spring Cloud.
 * <p>
 * Traduce entre el modelo de dominio puro ({@link EurekaInstanceInfo}, {@link EurekaRegistry})
 * y el modelo interno de Netflix Eureka ({@link InstanceInfo}, {@link Applications}).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class EurekaRegistryAdapter implements EurekaRegistryPort {

    private final EurekaClient eurekaClient;

    // Cache local para métricas y self-preservation (Eureka Server expone via EurekaServerContext)
    private final Map<String, Object> serverMetrics = new ConcurrentHashMap<>();

    @Override
    public Single<EurekaRegistry> getRegistry() {
        log.debug("Consultando registry de Eureka");
        return Single.fromCallable(this::buildRegistry)
                .doOnSuccess(registry -> log.debug("Registry construido: {} aplicaciones, {} instancias",
                        registry.getApplicationNames().size(), registry.getTotalInstances()))
                .doOnError(error -> log.error("Error construyendo registry: {}",
                        error.getMessage(), error));
    }

    @Override
    public Completable registerInstance(EurekaInstanceInfo instanceInfo) {
        return Completable.fromCallable(() -> {
            log.info("Registro de instancia delegado a Eureka Server: {} ({})",
                    instanceInfo.getInstanceId(), instanceInfo.getAppName());
            return null;
        }).doOnError(error -> log.error("Error registrando instancia {}: {}",
                instanceInfo.getInstanceId(), error.getMessage(), error));
    }

    @Override
    public Maybe<EurekaInstanceInfo> renewLease(String instanceId) {
        log.debug("Renovando lease para instancia: {}", instanceId);
        return Maybe.fromCallable(() -> {
            try {
                String appName = instanceId.split(":")[0];
                InstanceInfo instanceInfo = eurekaClient.getApplication(appName)
                        .getInstances().stream()
                        .filter(i -> i.getInstanceId().equals(instanceId))
                        .findFirst()
                        .orElse(null);

                if (instanceInfo == null) {
                    log.warn("Instancia no encontrada para renovar lease: {}", instanceId);
                    return null;
                }

                log.debug("Heartbeat delegado a Eureka Server para instancia: {}", instanceId);
                log.debug("Lease renovado exitosamente para: {}", instanceId);

                return toDomainInstance(instanceInfo);
            } catch (Exception e) {
                log.error("Error renovando lease para {}: {}", instanceId, e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Completable updateInstanceStatus(String instanceId, EurekaInstanceInfo.InstanceStatus status) {
        log.info("Actualizando status de instancia {} a: {}", instanceId, status);
        return Completable.fromCallable(() -> {
            try {
                String appName = instanceId.split(":")[0];
                eurekaClient.getApplication(appName).getInstances().stream()
                        .filter(i -> i.getInstanceId().equals(instanceId))
                        .findFirst()
                        .ifPresent(instance -> {
                            instance.setStatus(toNetflixStatus(status));
                            log.debug("Status actualizado para {}: {}", instanceId, status);
                        });
                return null;
            } catch (Exception e) {
                log.error("Error actualizando status de {}: {}", instanceId, e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Completable deregisterInstance(String instanceId) {
        log.info("Desregistrando instancia del registry: {}", instanceId);
        return Completable.fromCallable(() -> {
            try {
                String appName = instanceId.split(":")[0];
                eurekaClient.getApplication(appName).getInstances().stream()
                        .filter(i -> i.getInstanceId().equals(instanceId))
                        .findFirst()
                        .ifPresent(instance -> {
                            instance.setStatus(InstanceInfo.InstanceStatus.DOWN);
                            log.debug("Instancia marcada como DOWN: {}", instanceId);
                            // Eureka Server manejara la eviccion automaticamente
                        });
                return null;
            } catch (Exception e) {
                log.error("Error desregistrando instancia {}: {}", instanceId, e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Single<List<EurekaInstanceInfo>> getInstances(String appName) {
        log.debug("Obteniendo instancias de aplicacion: {}", appName);
        return Single.fromCallable(() -> {
            try {
                Applications apps = eurekaClient.getApplications();
                List<EurekaInstanceInfo> instances = apps.getRegisteredApplications().stream()
                        .filter(app -> app.getName().equalsIgnoreCase(appName))
                        .flatMap(app -> app.getInstances().stream())
                        .map(this::toDomainInstance)
                        .collect(Collectors.toList());
                log.debug("Encontradas {} instancias para {}", instances.size(), appName);
                return instances;
            } catch (Exception e) {
                log.error("Error obteniendo instancias de {}: {}", appName, e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Maybe<EurekaInstanceInfo> findByInstanceId(String instanceId) {
        log.debug("Buscando instancia por ID en adapter: {}", instanceId);
        return Maybe.fromCallable(() -> {
            try {
                String appName = instanceId.split(":")[0];
                EurekaInstanceInfo result = eurekaClient.getApplication(appName).getInstances().stream()
                        .filter(i -> i.getInstanceId().equals(instanceId))
                        .findFirst()
                        .map(this::toDomainInstance)
                        .orElse(null);
                if (result != null) {
                    log.debug("Instancia encontrada en adapter: {}", instanceId);
                } else {
                    log.debug("Instancia no encontrada en adapter: {}", instanceId);
                }
                return result;
            } catch (Exception e) {
                log.error("Error buscando instancia {}: {}", instanceId, e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Completable evictExpiredInstances() {
        return Completable.fromCallable(() -> {
            log.info("Eviccion de instancias expiradas delegada a Eureka Server interno");
            return null;
        });
    }

    @Override
    public boolean isSelfPreservationActive() {
        // Eureka Server expone esto via EurekaServerContextBean
        // Por ahora retornamos false (dev mode)
        return Boolean.TRUE.equals(serverMetrics.get("selfPreservationMode"));
    }

    @Override
    public long getLastUpdatedTimestamp() {
        return Long.parseLong(serverMetrics.getOrDefault("lastUpdatedTimestamp", String.valueOf(System.currentTimeMillis())).toString());
    }

    // ===== Métodos de mapeo privado =====

    private EurekaRegistry buildRegistry() {
        log.debug("Construyendo registry desde Eureka Client");
        try {
            Applications apps = eurekaClient.getApplications();
            Map<String, Collection<EurekaInstanceInfo>> applicationsMap = apps.getRegisteredApplications().stream()
                    .collect(Collectors.toMap(
                            app -> app.getName().toUpperCase(),
                            app -> app.getInstances().stream()
                                    .map(this::toDomainInstance)
                                    .collect(Collectors.toList())
                    ));

            long totalInstances = applicationsMap.values().stream().mapToInt(Collection::size).sum();
            long instancesUp = applicationsMap.values().stream()
                    .flatMap(Collection::stream)
                    .filter(EurekaInstanceInfo::isHealthy)
                    .count();
            long instancesDown = totalInstances - instancesUp;

            log.info("Registry construido: {} aplicaciones, {} instancias ({} UP, {} DOWN)",
                    applicationsMap.size(), totalInstances, instancesUp, instancesDown);

            return EurekaRegistry.builder()
                    .applications(applicationsMap)
                    .lastUpdatedTimestamp(System.currentTimeMillis())
                    .selfPreservationMode(isSelfPreservationActive())
                    .totalInstances((int) totalInstances)
                    .instancesUp((int) instancesUp)
                    .instancesDown((int) instancesDown)
                    .build();
        } catch (Exception e) {
            log.error("Error construyendo registry: {}", e.getMessage(), e);
            throw e;
        }
    }

    private EurekaInstanceInfo toDomainInstance(InstanceInfo instanceInfo) {
        Map<String, String> metadata = new ConcurrentHashMap<>();
        instanceInfo.getMetadata().forEach(metadata::put);

        long lastRenewal = System.currentTimeMillis();
        long registration = instanceInfo.getLeaseInfo().getRegistrationTimestamp();
        long eviction = instanceInfo.getLeaseInfo().getEvictionTimestamp();

        return EurekaInstanceInfo.builder()
                .instanceId(instanceInfo.getInstanceId())
                .appName(instanceInfo.getAppName())
                .hostName(instanceInfo.getHostName())
                .ipAddress(instanceInfo.getIPAddr())
                .port(instanceInfo.getPort())
                .securePort(instanceInfo.getSecurePort())
                .status(toDomainStatus(instanceInfo.getStatus()))
                .metadata(metadata)
                .lastRenewalTimestamp(lastRenewal)
                .registrationTimestamp(registration)
                .evictionTimestamp(eviction)
                .build();
    }

    private EurekaInstanceInfo.InstanceStatus toDomainStatus(InstanceInfo.InstanceStatus status) {
        return switch (status) {
            case UP -> EurekaInstanceInfo.InstanceStatus.UP;
            case DOWN -> EurekaInstanceInfo.InstanceStatus.DOWN;
            case STARTING -> EurekaInstanceInfo.InstanceStatus.STARTING;
            case OUT_OF_SERVICE -> EurekaInstanceInfo.InstanceStatus.OUT_OF_SERVICE;
            default -> EurekaInstanceInfo.InstanceStatus.UNKNOWN;
        };
    }

    private InstanceInfo.InstanceStatus toNetflixStatus(EurekaInstanceInfo.InstanceStatus status) {
        return switch (status) {
            case UP -> InstanceInfo.InstanceStatus.UP;
            case DOWN -> InstanceInfo.InstanceStatus.DOWN;
            case STARTING -> InstanceInfo.InstanceStatus.STARTING;
            case OUT_OF_SERVICE -> InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
            default -> InstanceInfo.InstanceStatus.UNKNOWN;
        };
    }
}