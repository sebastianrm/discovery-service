package com.yanki.discovery.domain.port.in;

import com.yanki.discovery.domain.model.EurekaRegistry;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de entrada para obtener el estado completo del registro de Eureka.
 * <p>
 * Caso de uso: Consulta de registry para monitoreo, debugging o descubrimiento manual.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface GetRegistryUseCase {

    /**
     * Obtiene el registro completo de Eureka con todas las aplicaciones e instancias.
     *
     * @return Single con el EurekaRegistry actual
     */
    Single<EurekaRegistry> execute();
}