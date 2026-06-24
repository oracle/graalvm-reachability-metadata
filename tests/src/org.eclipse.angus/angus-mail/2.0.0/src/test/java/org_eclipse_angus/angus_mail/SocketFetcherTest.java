/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.angus.mail.util.SocketFetcher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Test;

public class SocketFetcherTest {
    private static final int TIMEOUT_MILLIS = 5_000;
    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();
    private static final String LOCALHOST_CERTIFICATE = """
        -----BEGIN CERTIFICATE-----
        MIIC7TCCAdWgAwIBAgIUJ4hzJj01gI8FoidCDBGP9r0KFC0wDQYJKoZIhvcNAQEL
        BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUyMDE2MTMzN1oXDTM2MDUx
        NzE2MTMzN1owFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
        AAOCAQ8AMIIBCgKCAQEAwQJQDoLJb+X5ZCglW5mGLEYkTx4BvNPI/idzmyEmqI4Y
        Zzcspkg5SgCPoycEBHLjexG/Qq6UzTuit0kLc5lwkZbPm9P4IchvQbY4GWTa9YhQ
        XXvZ4jm9YgjyD2BzG/eEYa+kKXhYhHdeypwThtmI1rVfIovuLJrS/xC3gP7FKQbB
        lnSMhhAhmz40htgaj50ZwHJEQO6OoDpA+OoJC7OxldPIGYMykpl9aNSmEtdgfHdH
        UDIPVqxD9/jldd1KyXVLrr7FMNgtw7V6wcCTwgP4VT+cxw+iqd4QXPuEa5aks3i5
        P+jXfiNWwyoHmVLjFGdJR/JBXz5P4crk0mbVNAVKtQIDAQABozcwNTAUBgNVHREE
        DTALgglsb2NhbGhvc3QwHQYDVR0OBBYEFEd1kou2IM62pN3p8Crxv8wTI7PhMA0G
        CSqGSIb3DQEBCwUAA4IBAQA8Pr/YOEUVVvESc2xFyDS4pszghcU2pNBezPUDTbze
        8imv2UJWHkD6UO/U2XWNsIr55CVFv+ollmjkp62XFI3JMoS9rwrpAe5UdRCDiiA0
        l/A1MagQgRXpMIlahyoBwz3eoDyeWFhRl95VZpiG6zj6Ylu6bEJCKWdK3/DsXPTF
        1t37phHFSyNgi8H3kVIG/gLLIIG37bzIXmnqy8fL0lrCpZ0tSKpNVO1y1hzlnHHF
        sP8bQgCZmLxcwqCh6q/89+1HIUYXSdkTZzB6D1r/qjYXBNUzhElB1YTynuOIsClv
        3yGIy0A6tLQEnxidpwTn6EL6j1q8n0ygQC6gEpXThTl1
        -----END CERTIFICATE-----
        """;
    private static final String LOCALHOST_PRIVATE_KEY = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDBAlAOgslv5flk
        KCVbmYYsRiRPHgG808j+J3ObISaojhhnNyymSDlKAI+jJwQEcuN7Eb9CrpTNO6K3
        SQtzmXCRls+b0/ghyG9BtjgZZNr1iFBde9niOb1iCPIPYHMb94Rhr6QpeFiEd17K
        nBOG2YjWtV8ii+4smtL/ELeA/sUpBsGWdIyGECGbPjSG2BqPnRnAckRA7o6gOkD4
        6gkLs7GV08gZgzKSmX1o1KYS12B8d0dQMg9WrEP3+OV13UrJdUuuvsUw2C3DtXrB
        wJPCA/hVP5zHD6Kp3hBc+4RrlqSzeLk/6Nd+I1bDKgeZUuMUZ0lH8kFfPk/hyuTS
        ZtU0BUq1AgMBAAECggEACgl01dMDJtnyq+dy4o5Q3f0LdtTPOigDoq81BxXEf6Kc
        uMNeWPWrtazioKqkU2yREVg/1g0jnlTyANjgKv5eo6eQJiDyB/m3IBeZ17zjrmiM
        u1Z34xiQar3J2WGxOBID/5t6weWo/sHWu1pooTbZ85vCIIGz3Kce1AD6dfoUZoNv
        oyeammEif7Op4USD8UqcsW/nhlaiTZmQufJh/Xm5VoJ7iPqvGIWiG0pIkqXI0+pq
        o5QLf0H74mcikNMWnY3Kg+MIeZmgqbgdjMVk97sE4jXF5dO8eHoTc4faR9vX8L9U
        xYsz6NWWlTeneqcI8Z9qd+/SgBvRXr3yn7C0wzTKuQKBgQDy6HH+Zd8W0XuetlMt
        k2qilErKuvYD6QTyZ1Ngzz3UlOXY9aPoENZAI30/4So/d+vDNISiQHtuG7DApus3
        mqjqFnl0WEps60OZMaaWLx9tY0ATkXTo+Ph3KDlHwNFBrQG6IWSHXDqGTVeffOYv
        ZVeDq6pvX/7vDvVs4RutDrKkWQKBgQDLaWFM4gWY0Z9cxpSfIUABmbckbJnniN2V
        w8ntooHu8E4I646C2/8sAAMCy9yULHG8G2z/EdxLcY2/N7hfNs4g9QDm9edXEbyx
        2rIIDjiOokBT2d87wARB24R2XW4mIYrYKy6XRs9jPPKpCbN1sgbVI5+HVmBf0uQw
        UNc+nKb9vQKBgQCoTSWufib0SHC+ptU63skjnFu125RZYTpsOesrSGwuvnwCty+5
        uvC3v98oQ3piP1S7C8haIxNiVw1AbmbLP/2JM8kzM3ldviQ4II6rwDqmL/5VkJLQ
        WLDO3q/RZ2eVeamYrUpZ/y0NoMj1WDSk4jKgqHCOepTITjT+G3pxjZ15AQKBgEH2
        4aPyJEiDqj+G8omMWdprA/Ze9aYdP2ajAKf8rFBVQ6km4qdTOrQFKPTOMbEnnJaY
        +kbZfuxEXehl5HeUKVKMwYcktaoJyXyP5G4yVmsC+QN4Qyl4QqkszA8qi174P7OM
        hWZvgy+2gycIS1derVKPY9uaylQo6vE0NilK2eitAoGBAL9ggxBm95MZjKk43oso
        4Vzw3awebMevkXMYtizl+NhzbAyPIR9SuaXmi74wSd4gXB2uSYDqVm3A30aovRHr
        aCrDybcM0S9hbheV4SPUU9PVs4B6EjKGrtBZwlZGhFeD9NmOfLIc9763EFofniB1
        e1k5F+Sazh9tk5b33fQY7mAs
        -----END PRIVATE KEY-----
        """;

    @Test
    public void socketFactoryClassLoadsThroughContextClassLoader() throws Exception {
        RecordingSocketFactory.reset();

        connectWithConfiguredSocketFactory(SocketFetcherTest.class.getClassLoader());

        assertThat(RecordingSocketFactory.defaultCalls).hasValue(1);
        assertThat(RecordingSocketFactory.unconnectedSocketCalls).hasValue(1);
    }

    @Test
    public void socketFactoryClassFallsBackToSocketFetcherClassLoader() throws Exception {
        RecordingSocketFactory.reset();

        connectWithConfiguredSocketFactory(new RejectingFactoryClassLoader());

        assertThat(RecordingSocketFactory.defaultCalls).hasValue(1);
        assertThat(RecordingSocketFactory.unconnectedSocketCalls).hasValue(1);
    }

    @Test
    public void sslServerIdentityCheckUsesJdkHostnameChecker() throws Exception {
        SSLContext serverContext = newServerContext();
        InetAddress loopback = InetAddress.getByName("localhost");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, loopback)) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            Future<Void> accepted = executor.submit(() -> acceptSslConnection(serverSocket));

            Properties properties = timeoutProperties();
            properties.setProperty("mail.test.ssl.trust", "*");
            properties.setProperty("mail.test.ssl.checkserveridentity", "true");

            try (Socket socket = SocketFetcher.getSocket("localhost", serverSocket.getLocalPort(),
                    properties, "mail.test", true)) {
                assertThat(socket).isInstanceOf(SSLSocket.class);
            }

            accepted.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void connectWithConfiguredSocketFactory(ClassLoader contextClassLoader) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            Future<Void> accepted = executor.submit(() -> acceptPlainConnection(serverSocket));

            Properties properties = timeoutProperties();
            properties.setProperty("mail.test.socketFactory.class", RecordingSocketFactory.class.getName());

            withContextClassLoader(contextClassLoader, () -> {
                try (Socket socket = SocketFetcher.getSocket(serverSocket.getInetAddress().getHostAddress(),
                        serverSocket.getLocalPort(), properties, "mail.test")) {
                    socket.getOutputStream().write(1);
                }
                return null;
            });

            accepted.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Void acceptPlainConnection(ServerSocket serverSocket) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            assertThat(socket.getInputStream().read()).isEqualTo(1);
        }
        return null;
    }

    private static Void acceptSslConnection(SSLServerSocket serverSocket) throws IOException {
        try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.startHandshake();
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        }
        return null;
    }

    private static Properties timeoutProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.test.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.test.timeout", Integer.toString(TIMEOUT_MILLIS));
        return properties;
    }

    private static SSLContext newServerContext() throws Exception {
        Certificate certificate = certificateFromPem(LOCALHOST_CERTIFICATE);
        PrivateKey privateKey = privateKeyFromPem(LOCALHOST_PRIVATE_KEY);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("localhost", privateKey, KEYSTORE_PASSWORD, new Certificate[] {certificate});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    private static Certificate certificateFromPem(String pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(new ByteArrayInputStream(derBytes(pem)));
    }

    private static PrivateKey privateKeyFromPem(String pem) throws Exception {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes(pem));
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static byte[] derBytes(String pem) {
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static <T> T withContextClassLoader(ClassLoader loader, ThrowingSupplier<T> supplier) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static final class RecordingSocketFactory extends SocketFactory {
        private static final AtomicInteger defaultCalls = new AtomicInteger();
        private static final AtomicInteger unconnectedSocketCalls = new AtomicInteger();

        public static SocketFactory getDefault() {
            defaultCalls.incrementAndGet();
            return new RecordingSocketFactory();
        }

        @Override
        public Socket createSocket() {
            unconnectedSocketCalls.incrementAndGet();
            return new Socket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return new Socket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return new Socket(address, port, localAddress, localPort);
        }

        private static void reset() {
            defaultCalls.set(0);
            unconnectedSocketCalls.set(0);
        }
    }

    private static final class RejectingFactoryClassLoader extends ClassLoader {
        private RejectingFactoryClassLoader() {
            super(SocketFetcherTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (RecordingSocketFactory.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
