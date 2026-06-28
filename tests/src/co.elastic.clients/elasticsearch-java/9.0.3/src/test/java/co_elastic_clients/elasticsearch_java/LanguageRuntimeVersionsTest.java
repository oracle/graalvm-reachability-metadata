/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LanguageRuntimeVersionsTest {
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final String META_HEADER_NAME = "x-elastic-client-meta";

    @Test
    void rest5ClientMetaHeaderIncludesDetectedJvmLanguageRuntimes() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(loopback, 0));
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);

            FutureTask<String> headerTask = new FutureTask<>(readMetaHeader(serverSocket));
            Thread serverThread = new Thread(headerTask, "elastic-rest5-test-server");
            serverThread.start();

            URI uri = URI.create("http://" + loopback.getHostAddress() + ":" + serverSocket.getLocalPort());
            try (Rest5Client client = Rest5Client.builder(uri).build()) {
                Response response = client.performRequest(new Request("GET", "/"));
                assertThat(response.getStatusCode()).isEqualTo(200);
            } finally {
                serverThread.join(TIMEOUT_MILLIS);
            }

            String metaHeader = headerTask.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertThat(metaHeader).isNotNull().contains(",kt=").contains(",sc=");
            assertThat(metaHeader).containsPattern("(^|,)kt=\\d+\\.\\d+(,|$)");
            assertThat(metaHeader).containsPattern("(^|,)sc=\\d+\\.\\d+(,|$)");
        }
    }

    private static Callable<String> readMetaHeader(ServerSocket serverSocket) {
        return () -> {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(TIMEOUT_MILLIS);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
                );

                String metaHeader = null;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    int separator = line.indexOf(':');
                    if (separator > 0) {
                        String name = line.substring(0, separator).toLowerCase(Locale.ROOT);
                        if (META_HEADER_NAME.equals(name)) {
                            metaHeader = line.substring(separator + 1).trim();
                        }
                    }
                }

                writeOkResponse(socket.getOutputStream());
                return metaHeader;
            }
        };
    }

    private static void writeOkResponse(OutputStream outputStream) throws IOException {
        String response = "HTTP/1.1 200 OK\r\n"
            + "Content-Length: 0\r\n"
            + "Connection: close\r\n"
            + "\r\n";
        outputStream.write(response.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
    }
}
