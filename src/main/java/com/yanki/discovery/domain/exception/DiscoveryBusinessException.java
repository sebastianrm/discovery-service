package com.yanki.discovery.domain.exception;

/**
 * Excepción base para errores de negocio del Discovery Service.
 * <p>
 * Encapsula códigos de error estandarizados y mensajes descriptivos
 * para ser mapeados a respuestas HTTP apropiadas por el GlobalExceptionHandler.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public class DiscoveryBusinessException extends RuntimeException {

    private final String code;
    private final String detail;

    /**
     * Constructor con código y mensaje.
     *
     * @param code    código de error único (ej. INSTANCE_NOT_FOUND)
     * @param message mensaje descriptivo del error
     */
    public DiscoveryBusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.detail = message;
    }

    /**
     * Constructor con código, mensaje y detalle adicional.
     *
     * @param code    código de error único
     * @param message mensaje principal
     * @param detail  detalle técnico adicional
     */
    public DiscoveryBusinessException(String code, String message, String detail) {
        super(message);
        this.code = code;
        this.detail = detail;
    }

    /**
     * Constructor con causa.
     *
     * @param code    código de error único
     * @param message mensaje descriptivo
     * @param cause   excepción causa
     */
    public DiscoveryBusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.detail = cause != null ? cause.getMessage() : message;
    }

    /**
     * Obtiene el código de error.
     *
     * @return código de error
     */
    public String getCode() {
        return code;
    }

    /**
     * Obtiene el detalle técnico.
     *
     * @return detalle del error
     */
    public String getDetail() {
        return detail;
    }

    // ===== Códigos de Error Estándar =====

    /**
     * Instancia no encontrada en el registry.
     */
    public static final String INSTANCE_NOT_FOUND = "INSTANCE_NOT_FOUND";

    /**
     * Aplicación no registrada en Eureka.
     */
    public static final String APPLICATION_NOT_FOUND = "APPLICATION_NOT_FOUND";

    /**
     * Error en la configuración de Eureka Server.
     */
    public static final String EUREKA_CONFIG_ERROR = "EUREKA_CONFIG_ERROR";

    /**
     * Error de comunicación con peer Eureka (HA cluster).
     */
    public static final String PEER_COMMUNICATION_ERROR = "PEER_COMMUNICATION_ERROR";

    /**
     * Error en la replicación del registry entre peers.
     */
    public static final String REGISTRY_REPLICATION_ERROR = "REGISTRY_REPLICATION_ERROR";

    /**
     * Configuración de seguridad inválida (credenciales faltantes en prod).
     */
    public static final String SECURITY_CONFIG_ERROR = "SECURITY_CONFIG_ERROR";

    /**
     * Metadata de instancia inválida o incompleta.
     */
    public static final String INVALID_INSTANCE_METADATA = "INVALID_INSTANCE_METADATA";

    /**
     * Self-preservation mode activado inesperadamente.
     */
    public static final String SELF_PRESERVATION_UNEXPECTED = "SELF_PRESERVATION_UNEXPECTED";
}