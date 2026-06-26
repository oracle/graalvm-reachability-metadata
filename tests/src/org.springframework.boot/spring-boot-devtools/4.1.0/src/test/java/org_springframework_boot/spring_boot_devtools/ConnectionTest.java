/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.livereload.LiveReloadServer;

public class ConnectionTest {

    @Test
    void livereloadJavascriptRequestServesPackagedResource() throws IOException {
        final LiveReloadServer server = new LiveReloadServer(0);
        final int port = server.start();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
            socket.setSoTimeout(5_000);

            final String request = "GET /livereload.js HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();

            final String response = readResponse(socket);

            assertTrue(response.startsWith("HTTP/1.1 200 OK"));
            assertTrue(response.contains("Content-Type: text/javascript"));
            assertTrue(response.contains("LiveReload"));
        } finally {
            server.stop();
        }
    }

    private static String readResponse(Socket socket) throws IOException {
        final ByteArrayOutputStream response = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = socket.getInputStream().read(buffer)) != -1) {
            response.write(buffer, 0, read);
        }
        return response.toString(StandardCharsets.UTF_8);
    }

}
