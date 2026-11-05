/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/// Terminates TLS locally and forwards the unmodified Oracle Net stream to the real database.
final class TlsTunnel implements AutoCloseable {
    static final String PASSWORD = "test-only";
    private final SSLServerSocket listener;
    private final ExecutorService executor;
    private final Future<String> forwarding;
    private final String service;

    TlsTunnel(Path identity, String databaseUrl) throws Exception {
        URI destination = URI.create(databaseUrl.substring("jdbc:oracle:thin:@".length()));
        service = destination.getPath().substring(1);
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(identity)) {
            store.load(input, PASSWORD.toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(keys.getKeyManagers(), null, null);
        listener = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        listener.setSoTimeout(30000);
        executor = Executors.newFixedThreadPool(2);
        forwarding = executor.submit(() -> forward(destination));
    }

    int port() {
        return listener.getLocalPort();
    }

    String service() {
        return service;
    }

    String cipherSuite() throws Exception {
        return forwarding.get(40, TimeUnit.SECONDS);
    }

    private String forward(URI destination) throws Exception {
        try (SSLSocket client = (SSLSocket) listener.accept(); Socket database = new Socket()) {
            client.setSoTimeout(30000);
            database.connect(new InetSocketAddress(destination.getHost(), destination.getPort()), 10000);
            database.setSoTimeout(30000);
            client.startHandshake();
            String cipher = client.getSession().getCipherSuite();
            Future<Void> upstream = executor.submit(() -> {
                client.getInputStream().transferTo(database.getOutputStream());
                database.shutdownOutput();
                return null;
            });
            database.getInputStream().transferTo(client.getOutputStream());
            upstream.get(40, TimeUnit.SECONDS);
            return cipher;
        }
    }

    @Override
    public void close() throws Exception {
        try {
            listener.close();
            forwarding.get(40, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.close();
        }
    }
}
