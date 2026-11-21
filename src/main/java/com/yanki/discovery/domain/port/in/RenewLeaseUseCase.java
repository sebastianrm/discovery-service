package com.yanki.discovery.domain.port.in;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Puerto de entrada para renovar el lease de una instancia (heartbeat).
 * <p>
 * Caso de uso: Heartbeat periódico (cada 10s) enviado por clientes Eureka
 * via PUT /eureka/apps/{appName}/{instanceId} para mantener el lease activo.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface RenewLeaseUseCase {

    /**
     * Renueva el lease de una instancia existente.
     *
     * @param instanceId identificador único de la instancia
     * @return Completable que completa cuando la renovación es exitosa
     */
    Completable execute(String instanceId);

    /**
     * Renueva el lease y retorna la instancia actualizada.
     *
     * @param instanceId identificador único de la instancia
     * @return Maybe con la instancia actualizada si existe, vacío si no
     */
    Maybe<EurekaInstanceInfo> executeAndGet(String instanceId);
}