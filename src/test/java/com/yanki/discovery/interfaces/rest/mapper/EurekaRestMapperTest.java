package com.yanki.discovery.interfaces.rest.mapper;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import com.yanki.discovery.interfaces.rest.dto.EurekaInstanceResponse;
import com.yanki.discovery.interfaces.rest.dto.EurekaRegistryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitario para {@link EurekaRestMapper} - CP-166, CP-170, CP-174.
 * <p>
 * Valida: Conversión de modelos de dominio a DTOs REST (instancia, lista, registry).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
class EurekaRestMapperTest {

    private EurekaRestMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EurekaRestMapper();
    }

    @Test
    @DisplayName("CP-166: toInstanceResponse debe convertir instancia de dominio a DTO")
    void toInstanceResponseDebeConvertirInstanciaDeDominioADTO() {
        // Given
        EurekaInstanceInfo domain = buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP);

        // When
        EurekaInstanceResponse response = mapper.toInstanceResponse(domain);

        // Then
        assertNotNull(response);
        assertEquals("accounts-service:172.20.0.5:8083", response.getInstanceId());
        assertEquals("ACCOUNTS-SERVICE", response.getAppName());
        assertEquals("172.20.0.5", response.getHostName());
        assertEquals("172.20.0.5", response.getIpAddress());
        assertEquals(8083, response.getPort());
        assertEquals(443, response.getSecurePort());
        assertEquals("UP", response.getStatus());
        assertEquals("1.0.0", response.getVersion());
        assertEquals("primary", response.getZone());
        assertEquals("v1", response.getApiVersion());
        assertFalse(response.isCanary());
        assertEquals(100, response.getWeight());
        assertNotNull(response.getMetadata());
        assertEquals("1.0.0", response.getMetadata().get("version"));
    }

    @Test
    @DisplayName("CP-166: toInstanceResponse debe retornar null para input null")
    void toInstanceResponseDebeRetornarNullParaInputNull() {
        // When
        EurekaInstanceResponse response = mapper.toInstanceResponse(null);

        // Then
        assertNull(response);
    }

    @Test
    @DisplayName("CP-174: toInstanceResponse debe incluir metadata completa")
    void toInstanceResponseDebeIncluirMetadataCompleta() {
        // Given
        Map<String, String> fullMetadata = Map.of(
                "version", "1.0.0",
                "zone", "primary",
                "api.version", "v1",
                "canary", "false",
                "weight", "100",
                "deployment.environment", "docker",
                "management.port", "8083"
        );

        EurekaInstanceInfo domain = EurekaInstanceInfo.builder()
                .instanceId("accounts-service:172.20.0.5:8083")
                .appName("ACCOUNTS-SERVICE")
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(8443)
                .status(EurekaInstanceInfo.InstanceStatus.UP)
                .metadata(fullMetadata)
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis())
                .evictionTimestamp(0)
                .build();

        // When
        EurekaInstanceResponse response = mapper.toInstanceResponse(domain);

        // Then
        assertNotNull(response);
        assertEquals("1.0.0", response.getVersion());
        assertEquals("primary", response.getZone());
        assertEquals("v1", response.getApiVersion());
        assertFalse(response.isCanary());
        assertEquals(100, response.getWeight());
        assertEquals("docker", response.getMetadata().get("deployment.environment"));
        assertEquals("8083", response.getMetadata().get("management.port"));
    }

    @Test
    @DisplayName("CP-170: toInstanceResponseList debe convertir lista de instancias")
    void toInstanceResponseListDebeConvertirListaDeInstancias() {
        // Given
        List<EurekaInstanceInfo> domains = List.of(
                buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP),
                buildInstanceInfo("accounts-service:172.20.0.6:8083", EurekaInstanceInfo.InstanceStatus.DOWN)
        );

        // When
        List<EurekaInstanceResponse> responses = mapper.toInstanceResponseList(domains);

        // Then
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("UP", responses.get(0).getStatus());
        assertEquals("DOWN", responses.get(1).getStatus());
    }

    @Test
    @DisplayName("CP-170: toInstanceResponseList debe retornar lista vacía para input null")
    void toInstanceResponseListDebeRetornarListaVaciaParaInputNull() {
        // When
        List<EurekaInstanceResponse> responses = mapper.toInstanceResponseList(null);

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    @DisplayName("CP-170: toInstanceResponseList debe manejar lista vacía")
    void toInstanceResponseListDebeManejarListaVacia() {
        // When
        List<EurekaInstanceResponse> responses = mapper.toInstanceResponseList(Collections.emptyList());

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    @DisplayName("CP-166: toRegistryResponse debe convertir registry completo")
    void toRegistryResponseDebeConvertirRegistryCompleto() {
        // Given
        EurekaRegistry domain = buildRegistry();

        // When
        EurekaRegistryResponse response = mapper.toRegistryResponse(domain);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getTotalInstances());
        assertEquals(1, response.getInstancesUp());
        assertEquals(1, response.getInstancesDown());
        assertFalse(response.isSelfPreservationMode());
        assertNotNull(response.getApplications());
        assertEquals(2, response.getApplications().size());
        assertTrue(response.getLastUpdatedTimestamp() > 0);
    }

    @Test
    @DisplayName("CP-166: toRegistryResponse debe retornar registry vacío para input null")
    void toRegistryResponseDebeRetornarRegistryVacioParaInputNull() {
        // When
        EurekaRegistryResponse response = mapper.toRegistryResponse(null);

        // Then
        assertNotNull(response);
        assertTrue(response.getApplications().isEmpty());
        assertEquals(0, response.getTotalInstances());
        assertEquals(0, response.getInstancesUp());
        assertEquals(0, response.getInstancesDown());
        assertFalse(response.isSelfPreservationMode());
    }

    @Test
    @DisplayName("CP-166: toRegistryResponse debe incluir aplicaciones con instancias")
    void toRegistryResponseDebeIncluirAplicacionesConInstancias() {
        // Given
        EurekaRegistry domain = buildRegistry();

        // When
        EurekaRegistryResponse response = mapper.toRegistryResponse(domain);

        // Then
        List<EurekaRegistryResponse.ApplicationResponse> apps = response.getApplications();
        assertEquals(2, apps.size());

        EurekaRegistryResponse.ApplicationResponse accountsApp = apps.stream()
                .filter(a -> "ACCOUNTS-SERVICE".equals(a.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, accountsApp.getInstances().size());
        assertEquals("UP", accountsApp.getInstances().get(0).getStatus());

        EurekaRegistryResponse.ApplicationResponse cardsApp = apps.stream()
                .filter(a -> "CARDS-SERVICE".equals(a.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, cardsApp.getInstances().size());
        assertEquals("DOWN", cardsApp.getInstances().get(0).getStatus());
    }

    // ===== Helpers =====

    private EurekaInstanceInfo buildInstanceInfo(String instanceId, EurekaInstanceInfo.InstanceStatus status) {
        return EurekaInstanceInfo.builder()
                .instanceId(instanceId)
                .appName(instanceId.split(":")[0].toUpperCase())
                .hostName("172.20.0.5")
                .ipAddress("172.20.0.5")
                .port(8083)
                .securePort(443)
                .status(status)
                .metadata(Map.of("version", "1.0.0", "zone", "primary", "api.version", "v1", "canary", "false", "weight", "100"))
                .lastRenewalTimestamp(System.currentTimeMillis())
                .registrationTimestamp(System.currentTimeMillis() - 60000)
                .evictionTimestamp(0)
                .build();
    }

    private EurekaRegistry buildRegistry() {
        EurekaInstanceInfo instanceUp = buildInstanceInfo("accounts-service:172.20.0.5:8083", EurekaInstanceInfo.InstanceStatus.UP);
        EurekaInstanceInfo instanceDown = buildInstanceInfo("cards-service:172.20.0.6:8085", EurekaInstanceInfo.InstanceStatus.DOWN);

        return EurekaRegistry.builder()
                .applications(Map.of(
                        "ACCOUNTS-SERVICE", List.of(instanceUp),
                        "CARDS-SERVICE", List.of(instanceDown)
                ))
                .lastUpdatedTimestamp(System.currentTimeMillis())
                .selfPreservationMode(false)
                .totalInstances(2)
                .instancesUp(1)
                .instancesDown(1)
                .build();
    }
}
