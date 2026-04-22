/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class CloseableHttpResponseProxyTest {

    @Test
    void executesRequestsWithCloseableProxyResponses() throws Exception {
        try (SingleRequestServer server = new SingleRequestServer("proxy-ok")) {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            try {
                HttpGet request = new HttpGet(server.getUri());
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("proxy-ok");
                }
            } finally {
                httpClient.getConnectionManager().shutdown();
            }
        }
    }

    private static final class SingleRequestServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final Future<?> serverTask;
        private final byte[] body;

        private SingleRequestServer(String body) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.executorService = Executors.newSingleThreadExecutor();
            this.body = body.getBytes(StandardCharsets.UTF_8);
            this.serverTask = executorService.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    readRequestHeaders(socket);
                    writeResponse(socket);
                } catch (IOException exception) {
                    if (!serverSocket.isClosed()) {
                        throw new IllegalStateException(exception);
                    }
                }
            });
        }

        private String getUri() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/";
        }

        private void readRequestHeaders(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Read until the empty line that terminates headers.
            }
        }

        private void writeResponse(Socket socket) throws IOException {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write((
                    "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/plain; charset=UTF-8\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n"
                            + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            outputStream.write(body);
            outputStream.flush();
        }

        @Override
        public void close() throws Exception {
            try {
                serverSocket.close();
                serverTask.get();
            } finally {
                executorService.shutdownNow();
            }
        }
    }
}
