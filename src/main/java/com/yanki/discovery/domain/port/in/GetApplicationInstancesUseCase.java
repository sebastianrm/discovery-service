package com.yanki.discovery.domain.port.in;

import com.yanki.discovery.domain.model.EurekaInstanceInfo;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

/**
 * Puerto de entrada para obtener instancias de una aplicación específica.
 * <p>
 * Caso de uso: Descubrimiento de servicios por parte de API Gateway u otros clientes
 * que necesitan resolver instancias healthy de un servicio.
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface GetApplicationInstancesUseCase {

    /**
     * Obtiene todas las instancias (UP y DOWN) de una aplicación.
     *
     * @param appName nombre de la aplicación (ej. ACCOUNTS-SERVICE)
     * @return Single con lista de instancias (vacía si no existe la app)
     */
    Single<List<EurekaInstanceInfo>> execute(String appName);

    /**
     * Obtiene solo las instancias UP (healthy) de una aplicación.
     * <p>
     * Útil para load balancing - solo instancias que pueden recibir tráfico.
     * </p>
     *
     * @param appName nombre de la aplicación
     * @return Single con lista de instancias healthy (vacía si ninguna)
     */
    Single<List<EurekaInstanceInfo>> executeHealthyOnly(String appName);

    /**
     * Obtiene una instancia específica por su instanceId.
     *
     * @param instanceId identificador único de la instancia
     * @return Maybe con la instancia si existe, vacío si no
     */
    Maybe<EurekaInstanceInfo> findByInstanceId(String instanceId);
}