package com.yanki.discovery.domain.port.out;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import com.yanki.discovery.domain.model.EurekaRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

/**
 * Puerto de salida para operaciones de persistencia del registry de Eureka.
 * <p>
 * Define el contrato para almacenar, consultar y gestionar el estado del registry.
 * La implementación concreta puede ser en memoria (standalone), replicada (HA cluster),
 * o persistida en base de datos externa.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface EurekaRegistryPort {

    /**
     * Obtiene el estado completo del registry.
     *
     * @return Single con el EurekaRegistry actual
     */
    Single<EurekaRegistry> getRegistry();

    /**
     * Registra una nueva instancia en el registry.
     *
     * @param instanceInfo información de la instancia
     * @return Completable que completa cuando el registro es exitoso
     */
    Completable registerInstance(EurekaInstanceInfo instanceInfo);

    /**
     * Renueva el lease de una instancia (heartbeat).
     *
     * @param instanceId identificador de la instancia
     * @return Maybe con la instancia actualizada si existe
     */
    Maybe<EurekaInstanceInfo> renewLease(String instanceId);

    /**
     * Actualiza el estado de una instancia (UP/DOWN/OUT_OF_SERVICE).
     *
     * @param instanceId identificador de la instancia
     * @param status     nuevo estado
     * @return Completable que completa cuando la actualización es exitosa
     */
    Completable updateInstanceStatus(String instanceId, EurekaInstanceInfo.InstanceStatus status);

    /**
     * Desregistra una instancia del registry.
     *
     * @param instanceId identificador de la instancia
     * @return Completable que completa cuando el desregistro es exitoso
     */
    Completable deregisterInstance(String instanceId);

    /**
     * Obtiene todas las instancias de una aplicación.
     *
     * @param appName nombre de la aplicación
     * @return Single con lista de instancias (vacía si no existe)
     */
    Single<List<EurekaInstanceInfo>> getInstances(String appName);

    /**
     * Busca una instancia por su ID único.
     *
     * @param instanceId identificador único
     * @return Maybe con la instancia si existe
     */
    Maybe<EurekaInstanceInfo> findByInstanceId(String instanceId);

    /**
     * Ejecuta la evicción de instancias con lease expirado.
     * <p>
     * Invocado periódicamente por el scheduler de Eureka Server.
     * </p>
     *
     * @return Completable que completa cuando la evicción termina
     */
    Completable evictExpiredInstances();

    /**
     * Verifica si el modo self-preservation está activo.
     *
     * @return true si self-preservation está ON
     */
    boolean isSelfPreservationActive();

    /**
     * Obtiene el timestamp de la última actualización del registry.
     *
     * @return timestamp en epoch millis
     */
    long getLastUpdatedTimestamp();
}