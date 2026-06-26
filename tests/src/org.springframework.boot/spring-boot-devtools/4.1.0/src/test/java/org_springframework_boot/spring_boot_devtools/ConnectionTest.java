/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.livereload.LiveReloadServer;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ConnectionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void liveReloadServerServesJavaScriptResource() throws Exception {
        LiveReloadServer server = new LiveReloadServer(0);
        int port = server.start();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), (int) TIMEOUT.toMillis());
            socket.setSoTimeout((int) TIMEOUT.toMillis());

            writeRequest(socket.getOutputStream());
            String response = readResponse(socket.getInputStream());

            assertThat(response).startsWith("HTTP/1.1 200 OK\r\n");
            assertThat(response).contains("Content-Type: text/javascript");
            assertThat(response).contains("LiveReload");
        }
        finally {
            server.stop();
        }
    }

    private static void writeRequest(OutputStream outputStream) throws IOException {
        String request = "GET /livereload.js HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1));
        outputStream.flush();
    }

    private static String readResponse(InputStream inputStream) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            response.write(buffer, 0, read);
        }
        return response.toString(StandardCharsets.UTF_8);
    }

}
