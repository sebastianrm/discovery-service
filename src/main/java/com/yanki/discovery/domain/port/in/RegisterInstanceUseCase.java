package com.yanki.discovery.domain.port.in;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import io.reactivex.rxjava3.core.Completable;

/**
 * Puerto de entrada para registrar una nueva instancia en Eureka.
 * <p>
 * Caso de uso: Registro inicial de un microservicio al arrancar (bootstrap).
 * Invocado automáticamente por el cliente Eureka via POST /eureka/apps/{appName}.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface RegisterInstanceUseCase {

    /**
     * Registra una nueva instancia en el registry.
     *
     * @param instanceInfo información completa de la instancia a registrar
     * @return Completable que completa cuando el registro es exitoso
     */
    Completable execute(EurekaInstanceInfo instanceInfo);
}