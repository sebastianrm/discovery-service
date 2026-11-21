package com.yanki.discovery.domain.port.in;

import io.reactivex.rxjava3.core.Completable;

/**
 * Puerto de entrada para desregistrar una instancia de Eureka.
 * <p>
 * Caso de uso: Shutdown graceful de un microservicio que envía
 * DELETE /eureka/apps/{appName}/{instanceId} para limpiar su registro.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface DeregisterInstanceUseCase {

    /**
     * Desregistra una instancia del registry.
     *
     * @param instanceId identificador único de la instancia
     * @return Completable que completa cuando el desregistro es exitoso
     */
    Completable execute(String instanceId);
}