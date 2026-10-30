/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.FactoryConfiguration;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class RMICacheManagerPeerListenerTest {
    @TempDir
    Path directory;

    @Test
    void bindsAndUnbindsDynamicallyAddedDistributedCache() throws Exception {
        int port = PeerFixture.freePort();
        Registry registry = LocateRegistry.createRegistry(port);
        Configuration configuration = TestSupport.configuration(directory);
        FactoryConfiguration listener = new FactoryConfiguration();
        listener.setClass("net.sf.ehcache.distribution.RMICacheManagerPeerListenerFactory");
        listener.setProperties("hostName=127.0.0.1,port=" + port + ",socketTimeoutMillis=10000");
        configuration.addCacheManagerPeerListenerFactory(listener);
        CacheManager manager = new CacheManager(configuration);
        try {
            Cache cache = new Cache("distributed", 20, false, true, 0, 0);
            cache.getCacheEventNotificationService().registerListener(new RMISynchronousCacheReplicator(true, true, true, true));
            manager.addCache(cache);
            CachePeer remote = (CachePeer) registry.lookup("distributed");
            remote.put(new Element("key", "value"));
            assertEquals("value", cache.get("key").getObjectValue());
            assertEquals(Status.STATUS_ALIVE, manager.getCachePeerListener().getStatus());
            assertEquals(1, manager.getCachePeerListener().getBoundCachePeers().size());
            manager.removeCache("distributed");
            assertEquals(0, registry.list().length);
            assertTrue(manager.getCachePeerListener().getBoundCachePeers().isEmpty());
        } finally {
            try {
                manager.shutdown();
            } finally {
                UnicastRemoteObject.unexportObject(registry, true);
            }
        }
    }
}
