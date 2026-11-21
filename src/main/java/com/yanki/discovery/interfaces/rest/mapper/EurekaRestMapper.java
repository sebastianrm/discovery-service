package com.yanki.discovery.interfaces.rest.mapper;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.interfaces.rest.dto.EurekaInstanceResponse;
import com.yanki.discovery.interfaces.rest.dto.EurekaRegistryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper para convertir modelos de dominio a DTOs de respuesta REST.
 * <p>
 * Traductores estáticos sin lógica de negocio, solo transformación de datos.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Slf4j
@Component
public class EurekaRestMapper {

    /**
     * Convierte EurekaInstanceInfo (dominio) a EurekaInstanceResponse (REST DTO).
     *
     * @param domain modelo de dominio
     * @return DTO de respuesta
     */
    public EurekaInstanceResponse toInstanceResponse(EurekaInstanceInfo domain) {
        if (domain == null) {
            log.warn("Intento de mapear EurekaInstanceInfo nulo a EurekaInstanceResponse");
            return null;
        }
        try {
            EurekaInstanceResponse response = EurekaInstanceResponse.builder()
                    .instanceId(domain.getInstanceId())
                    .appName(domain.getAppName())
                    .hostName(domain.getHostName())
                    .ipAddress(domain.getIpAddress())
                    .port(domain.getPort())
                    .securePort(domain.getSecurePort())
                    .status(domain.getStatus().name())
                    .metadata(domain.getMetadata())
                    .lastRenewalTimestamp(domain.getLastRenewalTimestamp())
                    .registrationTimestamp(domain.getRegistrationTimestamp())
                    .evictionTimestamp(domain.getEvictionTimestamp())
                    .canary(domain.isCanary())
                    .zone(domain.getZone())
                    .apiVersion(domain.getApiVersion())
                    .weight(domain.getWeight())
                    .version(domain.getVersion())
                    .build();
            log.debug("Mapeo de instancia: {} -> {} (status: {})",
                    domain.getInstanceId(), response.getInstanceId(), response.getStatus());
            return response;
        } catch (Exception e) {
            log.error("Error mapeando instancia {}: {}",
                    domain.getInstanceId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Convierte una lista de instancias de dominio a DTOs.
     *
     * @param domains lista de modelos de dominio
     * @return lista de DTOs
     */
    public List<EurekaInstanceResponse> toInstanceResponseList(Iterable<EurekaInstanceInfo> domains) {
        if (domains == null) {
            log.warn("Intento de mapear lista nula de EurekaInstanceInfo");
            return List.of();
        }
        try {
            List<EurekaInstanceResponse> result = java.util.stream.StreamSupport.stream(domains.spliterator(), false)
                    .map(this::toInstanceResponse)
                    .collect(Collectors.toList());
            log.debug("Mapeo de lista: {} instancias convertidas", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error mapeando lista de instancias: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Convierte EurekaRegistry (dominio) a EurekaRegistryResponse (REST DTO).
     *
     * @param domain modelo de dominio
     * @return DTO de respuesta
     */
    public EurekaRegistryResponse toRegistryResponse(EurekaRegistry domain) {
        if (domain == null) {
            log.warn("Intento de mapear EurekaRegistry nulo a EurekaRegistryResponse");
            return EurekaRegistryResponse.builder()
                    .applications(List.of())
                    .lastUpdatedTimestamp(System.currentTimeMillis())
                    .selfPreservationMode(false)
                    .totalInstances(0)
                    .instancesUp(0)
                    .instancesDown(0)
                    .build();
        }

        try {
            List<EurekaRegistryResponse.ApplicationResponse> apps = domain.getApplicationNames().stream()
                    .map(appName -> {
                        List<EurekaInstanceResponse> instances = toInstanceResponseList(domain.getInstances(appName));
                        return EurekaRegistryResponse.ApplicationResponse.builder()
                                .name(appName)
                                .instances(instances)
                                .build();
                    })
                    .collect(Collectors.toList());

            EurekaRegistryResponse response = EurekaRegistryResponse.builder()
                    .applications(apps)
                    .lastUpdatedTimestamp(domain.getLastUpdatedTimestamp())
                    .selfPreservationMode(domain.isSelfPreservationMode())
                    .totalInstances(domain.getTotalInstances())
                    .instancesUp(domain.getInstancesUp())
                    .instancesDown(domain.getInstancesDown())
                    .build();

            log.debug("Mapeo de registry: {} aplicaciones, {} instancias totales",
                    apps.size(), domain.getTotalInstances());
            return response;
        } catch (Exception e) {
            log.error("Error mapeando registry: {}", e.getMessage(), e);
            throw e;
        }
    }
}