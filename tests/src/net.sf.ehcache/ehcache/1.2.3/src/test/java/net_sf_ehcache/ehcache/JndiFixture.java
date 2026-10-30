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

import javax.naming.Context;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.JNDIRMICacheManagerPeerListener;
import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;

final class JndiFixture implements AutoCloseable {
    final CacheManager manager;
    final Cache cache;
    final JNDIRMICacheManagerPeerListener listener;
    final String url;
    private final Registry registry;
    private final String originalFactory;
    private final String originalUrl;

    JndiFixture(Path directory) throws Exception {
        int port = PeerFixture.freePort();
        manager = TestSupport.replicatingManager(directory, "");
        cache = manager.getCache("replicated");
        cache.getCacheEventNotificationService().registerListener(new RMISynchronousCacheReplicator(true, true, true, true));
        originalFactory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
        originalUrl = System.getProperty(Context.PROVIDER_URL);
        String providerUrl = "rmi://127.0.0.1:" + port;
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.rmi.registry.RegistryContextFactory");
        System.setProperty(Context.PROVIDER_URL, providerUrl);
        url = providerUrl + "/replicated";
        listener = new JNDIRMICacheManagerPeerListener("127.0.0.1", port, manager, 10000);
        registry = LocateRegistry.createRegistry(port);
        listener.init();
    }

    @Override
    public void close() throws Exception {
        try {
            listener.dispose();
        } finally {
            restore(Context.INITIAL_CONTEXT_FACTORY, originalFactory);
            restore(Context.PROVIDER_URL, originalUrl);
            try {
                manager.shutdown();
            } finally {
                UnicastRemoteObject.unexportObject(registry, true);
            }
        }
    }

    private static void restore(String name, String original) {
        if (original == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, original);
        }
    }
}
