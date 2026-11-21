package com.yanki.discovery.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración global de OpenAPI 3 / Swagger para discovery-service.
 * <p>
 * Define información de la API, servidores, esquemas de seguridad
 * y componentes reutilizables.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8761}")
    private String serverPort;

    @Value("${spring.application.name:discovery-service}")
    private String applicationName;

    @Bean
    public OpenAPI discoveryServiceOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor de desarrollo local"),
                        new Server()
                                .url("https://discovery.yanki.internal")
                                .description("Producción (HTTPS via Gateway)")
                ))
                .components(new Components()
                        .addSecuritySchemes("basicAuth", basicAuthScheme())
                        .addSecuritySchemes("eurekaAuth", eurekaAuthScheme())
                )
                .security(List.of(
                        new SecurityRequirement().addList("basicAuth"),
                        new SecurityRequirement().addList("eurekaAuth")
                ))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentación completa de la plataforma Yanki")
                        .url("https://github.com/yanki/discovery-service/wiki"));
    }

    private Info apiInfo() {
        return new Info()
                .title("Yanki Discovery Service - Eureka Server API")
                .version("1.0.0")
                .description("""
                        API REST del servidor Eureka para Service Discovery de la plataforma bancaria Yanki.
                        
                        ## Funcionalidades principales
                        * **Registry**: Consulta de servicios e instancias registradas
                        * **Protocol**: Endpoints internos para clientes Eureka (registro, heartbeat, desregistro)
                        * **Health**: Health checks compatibles con Spring Boot Actuator
                        * **Metadata**: Soporte para metadata rica (version, zone, api.version, canary, weighted routing)
                        * **HA Cluster**: Configuración de alta disponibilidad 3 nodos peer-to-peer
                        * **Self-Preservation**: Modo protección contra evicción masiva en producción
                        
                        ## Autenticación
                        * **Dashboard Eureka** (`/`): Basic Auth obligatorio en producción (`EUREKA_USER`/`EUREKA_PASSWORD`)
                        * **Registry API** (`/eureka/apps/**`): Acceso libre para clientes Eureka registrados
                        * **Actuator** (`/actuator/**`): Acceso libre para monitoreo
                        
                        ## Convenciones
                        * Nombres de aplicación en MAYÚSCULAS (ej. `ACCOUNTS-SERVICE`)
                        * Instance ID formato: `service-name:ip:port`
                        * Metadata estándar: `version`, `zone`, `api.version`, `canary`, `weight`, `deployment.environment`
                        """)
                .contact(new Contact()
                        .name("Platform Team - Service Discovery")
                        .email("platform-discovery@yanki.internal")
                        .url("https://github.com/yanki/discovery-service"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://yanki.internal/license"));
    }

    private SecurityScheme basicAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")
                .description("Autenticación Basic Auth para Dashboard Eureka y endpoints protegidos");
    }

    private SecurityScheme eurekaAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Credenciales Eureka para clientes (opcional en dev, obligatorio en prod)");
    }
}