package com.yanki.discovery.domain.port.out;

import com.yanki.discovery.domain.model.EurekaRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de salida para sincronización del registry entre peers Eureka (HA Cluster).
 * <p>
 * Define el contrato para replicar el registry entre nodos del cluster Eureka
 * en configuración de alta disponibilidad (3+ nodos peer-to-peer).
 * </p>
 *
 * @author Sebastian Retamal
 * @email semorale@emeal.nttdata.com
 * @since 1.0.0
 */
public interface PeerSyncPort {

    /**
     * Obtiene la lista de URLs de peers configurados.
     *
     * @return Single con lista de URLs de peers (ej. http://eureka-2:8761/eureka/)
     */
    Single<java.util.List<String>> getPeerUrls();

    /**
     * Sincroniza el registry local con un peer remoto.
     *
     * @param peerUrl URL del peer Eureka
     * @return Completable que completa cuando la sincronización termina
     */
    Completable syncWithPeer(String peerUrl);

    /**
     * Replica una instancia registrada localmente a todos los peers.
     *
     * @param peerUrls   lista de URLs de peers
     * @param instanceId ID de la instancia a replicar
     * @return Completable que completa cuando la replicación termina en todos los peers
     */
    Completable replicateToPeers(java.util.List<String> peerUrls, String instanceId);

    /**
     * Replica un desregistro a todos los peers.
     *
     * @param peerUrls   lista de URLs de peers
     * @param instanceId ID de la instancia a desregistrar en peers
     * @return Completable que completa cuando la replicación termina
     */
    Completable replicateDeregistrationToPeers(java.util.List<String> peerUrls, String instanceId);

    /**
     * Obtiene el registry completo de un peer remoto (para bootstrap o recovery).
     *
     * @param peerUrl URL del peer
     * @return Single con el registry del peer
     */
    Single<EurekaRegistry> fetchRegistryFromPeer(String peerUrl);

    /**
     * Verifica la conectividad con un peer.
     *
     * @param peerUrl URL del peer
     * @return Single con true si el peer está accesible
     */
    Single<Boolean> checkPeerHealth(String peerUrl);
}