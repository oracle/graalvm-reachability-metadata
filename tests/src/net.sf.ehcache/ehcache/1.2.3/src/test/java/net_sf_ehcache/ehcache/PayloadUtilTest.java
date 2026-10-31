/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.FactoryConfiguration;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.MulticastRMICacheManagerPeerProvider;
import net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class PayloadUtilTest {
    @TempDir
    Path directory;

    @Test
    void discoversPeersFromCompressedHeartbeat() throws Exception {
        try (PeerFixture first = new PeerFixture(); PeerFixture second = new PeerFixture()) {
            int registryPort = PeerFixture.freePort();
            Configuration configuration = TestSupport.configuration(directory);
            FactoryConfiguration listener = new FactoryConfiguration();
            listener.setClass("net.sf.ehcache.distribution.RMICacheManagerPeerListenerFactory");
            listener.setProperties("hostName=127.0.0.1,port=" + registryPort + ",socketTimeoutMillis=10000");
            configuration.addCacheManagerPeerListenerFactory(listener);
            Registry registry = LocateRegistry.createRegistry(registryPort);
            try {
                CacheManager manager = new CacheManager(configuration);
                try (DatagramSocket broadcaster = new DatagramSocket()) {
                    InetAddress group = InetAddress.getByName("230.0.0.78");
                    int port;
                    try (MulticastSocket reservation = new MulticastSocket(0)) {
                        port = reservation.getLocalPort();
                    }
                    MulticastRMICacheManagerPeerProvider provider = new MulticastRMICacheManagerPeerProvider(
                            manager, group, port);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (GZIPOutputStream payload = new GZIPOutputStream(bytes)) {
                        payload.write((first.url + "|" + second.url).getBytes(StandardCharsets.US_ASCII));
                    }
                    byte[] heartbeat = bytes.toByteArray();
                    provider.init();
                    try {
                        broadcaster.setBroadcast(true);
                        broadcaster.send(new DatagramPacket(heartbeat, heartbeat.length,
                                InetAddress.getByName("127.255.255.255"), port));
                        TestSupport.await(() -> provider.listRemoteCachePeers(first.cache).size() == 2);
                        Set<String> discovered = new HashSet<>();
                        for (Object peer : provider.listRemoteCachePeers(first.cache)) {
                            discovered.add(((CachePeer) peer).getUrl());
                        }
                        assertEquals(Set.of(first.url, second.url), discovered);
                    } finally {
                        provider.dispose();
                    }
                } finally {
                    manager.shutdown();
                }
            } finally {
                UnicastRemoteObject.unexportObject(registry, true);
            }
        }
    }

    @Test
    void parsesDelimitedPeerUrlsThroughTheProviderFactory() throws Exception {
        try (PeerFixture first = new PeerFixture(); PeerFixture second = new PeerFixture()) {
            CacheManager manager = TestSupport.manager(directory);
            CacheManagerPeerProvider provider = new RMICacheManagerPeerProviderFactory().createCachePeerProvider(manager,
                    PropertyUtil.parseProperties("peerDiscovery=manual,rmiUrls=" + first.url + " | " + second.url));
            try {
                provider.init();
                List peers = provider.listRemoteCachePeers(first.cache);
                Set<String> discovered = new HashSet<>();
                for (Object peer : peers) {
                    discovered.add(((CachePeer) peer).getUrl());
                }
                assertEquals(Set.of(first.url, second.url), discovered);
            } finally {
                provider.dispose();
                manager.shutdown();
            }
        }
    }
}
