package com.yanki.discovery.domain.port.in;

import com.yanki.discovery.domain.model.EurekaRegistry;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de entrada para obtener el health check del Eureka Server.
 * <p>
 * Caso de uso: Endpoint /actuator/health para monitoreo y Docker healthcheck.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface GetEurekaHealthUseCase {

    /**
     * Obtiene el estado de salud del Eureka Server.
     *
     * @return Single con el registry actual (incluye métricas de salud)
     */
    Single<EurekaRegistry> execute();
}