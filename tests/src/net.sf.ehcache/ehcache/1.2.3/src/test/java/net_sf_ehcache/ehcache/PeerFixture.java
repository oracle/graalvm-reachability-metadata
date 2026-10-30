/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.RMICachePeer;
import net.sf.ehcache.distribution.RMICacheManagerPeerListener;
import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;

final class PeerFixture implements AutoCloseable {
    final Cache cache;
    final RMICachePeer peer;
    final CachePeer client;
    final Registry registry;
    final int port;
    final String url;
    private final CacheManager manager;
    private final RMICacheManagerPeerListener listener;

    PeerFixture() throws Exception {
        port = freePort();
        manager = TestSupport.replicatingManager(Path.of(System.getProperty("java.io.tmpdir")), "");
        cache = manager.getCache("replicated");
        cache.getCacheEventNotificationService().registerListener(new RMISynchronousCacheReplicator(true, true, true, true));
        listener = new RMICacheManagerPeerListener("127.0.0.1", port, manager, 10000);
        registry = LocateRegistry.createRegistry(port);
        listener.init();
        peer = (RMICachePeer) listener.getBoundCachePeers().get(0);
        client = (CachePeer) RemoteObject.toStub(peer);
        url = peer.getUrl();
    }

    static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close() throws Exception {
        try {
            listener.dispose();
        } finally {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } finally {
                manager.shutdown();
            }
        }
    }
}
