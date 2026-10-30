/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

import net.sf.ehcache.Cache;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.RMICachePeer;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

final class PeerFixture implements AutoCloseable {
    final Cache cache;
    final RMICachePeer peer;
    final CachePeer client;
    final Registry registry;
    final int port;
    final String url;

    PeerFixture() throws Exception {
        port = freePort();
        cache = TestSupport.cache("replicated", 20, MemoryStoreEvictionPolicy.LRU);
        registry = LocateRegistry.createRegistry(port);
        peer = new RMICachePeer(cache, "127.0.0.1", port, 10000);
        client = (CachePeer) RemoteObject.toStub(peer);
        registry.rebind(cache.getName(), peer);
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
            registry.unbind(cache.getName());
            UnicastRemoteObject.unexportObject(peer, true);
        } finally {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } finally {
                cache.dispose();
            }
        }
    }
}
