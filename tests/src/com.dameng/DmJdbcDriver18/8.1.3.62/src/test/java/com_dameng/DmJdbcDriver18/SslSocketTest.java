/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.a.c;
import dm.jdbc.a.f;
import dm.jdbc.driver.DmdbConnection;
import dm.jdbc.internal.conf.DmProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslSocketTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(90)
    void upgradesSocketUsingConfiguredClientKeystore() throws Exception {
        Path clientStore = directory.resolve("client.jks");
        try (InputStream fixture = SslSocketTest.class.getResourceAsStream("/dameng-tls.jks")) {
            assertNotNull(fixture);
            Files.copy(fixture, clientStore);
        }
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(clientStore)) {
            keyStore.load(input, "changeit".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance("SunX509");
        keys.init(keyStore, "changeit".toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance("SunX509");
        trust.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);

        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(
                0, 1, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(20000);
            server.setEnabledProtocols(new String[]{"TLSv1.2"});
            Future<Integer> received = executor.submit(() -> {
                try (SSLSocket peer = (SSLSocket) server.accept()) {
                    peer.setSoTimeout(20000);
                    int request = peer.getInputStream().read();
                    peer.getOutputStream().write(84);
                    peer.getOutputStream().flush();
                    return request;
                }
            });
            DmProperties properties = new DmProperties();
            properties.setProperty("host", server.getInetAddress().getHostAddress());
            properties.setProperty("port", Integer.toString(server.getLocalPort()));
            properties.setProperty("user", "TESTUSER");
            properties.setProperty("sslFilesPath", clientStore.toString());
            properties.setProperty("sslKeystorePass", "changeit");
            properties.setProperty("sslProtocol", "TLSv1.2");
            properties.setProperty("connectTimeout", "20000");
            properties.setProperty("socketTimeout", "20000");
            try (DmdbConnection configuration = new DmdbConnection(properties);
                 c transport = new c()) {
                transport.b(configuration);
                try (f secure = new f(transport, false)) {
                    secure.d(configuration);
                    SSLSocket encrypted = (SSLSocket) secure.g();
                    assertEquals("TLSv1.2", encrypted.getSession().getProtocol());
                    assertTrue(encrypted.getSession().getPeerPrincipal().getName().contains("CN=TESTUSER"));
                    encrypted.getOutputStream().write(42);
                    encrypted.getOutputStream().flush();
                    assertEquals(84, encrypted.getInputStream().read());
                }
                assertEquals(42, received.get(20, TimeUnit.SECONDS).intValue());
            }
        }
    }
}
