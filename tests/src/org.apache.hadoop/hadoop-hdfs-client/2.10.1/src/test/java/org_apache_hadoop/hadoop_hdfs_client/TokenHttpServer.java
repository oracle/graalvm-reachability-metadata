/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/// A loopback HTTP peer supporting the legacy token protocol's custom reason phrase.
final class TokenHttpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<?> serving;
    private final String status;
    private final String body;
    final AtomicReference<String> request = new AtomicReference<>();

    TokenHttpServer(String responseStatus, String responseBody) throws IOException {
        status = responseStatus;
        body = responseBody;
        listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        listener.setSoTimeout(120000);
        serving = executor.submit(this::serve);
    }

    URI uri(String scheme) {
        return URI.create(scheme + "://localhost:" + listener.getLocalPort());
    }

    private void serve() {
        try {
            while (!listener.isClosed()) {
                try (Socket client = listener.accept()) {
                    client.setSoTimeout(10000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            client.getInputStream(), StandardCharsets.US_ASCII));
                    String firstLine = reader.readLine();
                    if (firstLine == null) {
                        throw new IOException("HTTP peer closed before sending a request");
                    }
                    String header;
                    do {
                        header = reader.readLine();
                    } while (header != null && !header.isEmpty());
                    boolean authenticate = firstLine.startsWith("OPTIONS ");
                    if (!authenticate) {
                        request.set(firstLine);
                    }
                    byte[] payload = (authenticate ? "" : body).getBytes(StandardCharsets.UTF_8);
                    String cookie = authenticate
                            ? "Set-Cookie: hadoop.auth=\"u=token-user&p=token-user&t=simple&e="
                                    + (System.currentTimeMillis() + 600000) + "\"; Path=/\r\n"
                            : "";
                    String response = "HTTP/1.1 " + (authenticate ? "200 OK" : status) + "\r\n"
                            + cookie + "Content-Type: text/plain\r\nContent-Length: " + payload.length
                            + "\r\nConnection: close\r\n\r\n";
                    OutputStream output = client.getOutputStream();
                    output.write(response.getBytes(StandardCharsets.US_ASCII));
                    output.write(payload);
                    output.flush();
                }
            }
        } catch (SocketException e) {
            if (!listener.isClosed()) {
                throw new UncheckedIOException(e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws Exception {
        listener.close();
        try {
            serving.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IOException("HTTP server did not terminate");
            }
        }
    }
}
