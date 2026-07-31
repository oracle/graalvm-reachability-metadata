/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.livereload.LiveReloadServer;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionTest {

    @Test
    void servesLiveReloadJavaScript() throws Exception {
        LiveReloadServer server = new LiveReloadServer(0);
        int port = server.start();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 10_000);
            socket.setSoTimeout(10_000);
            try (OutputStream outputStream = socket.getOutputStream()) {
                outputStream.write("GET /livereload.js HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
                outputStream.flush();

                try (InputStream inputStream = socket.getInputStream()) {
                    String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                    assertThat(response).startsWith("HTTP/1.1 200 OK\r\n");
                    assertThat(response).contains("Content-Type: text/javascript\r\n");
                }
            }
        } finally {
            server.stop();
        }
    }
}
