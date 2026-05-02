/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import bsh.util.Httpd;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpdConnectionTest {
    @Test
    void servesBundledRemoteConsoleResource() throws Exception {
        int port = findAvailablePort();
        Httpd httpd = new Httpd(port);
        httpd.setDaemon(true);
        httpd.start();

        String response = requestRootResource(port);

        assertThat(response)
                .contains("HTTP/1.0 200 Document follows")
                .contains("Content-Type: text/html")
                .contains("BeanShell Remote Session");
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return serverSocket.getLocalPort();
        }
    }

    private static String requestRootResource(int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000);
            socket.setSoTimeout(2_000);

            try (OutputStream output = socket.getOutputStream()) {
                output.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes(ISO_8859_1));
                output.flush();
                socket.shutdownOutput();

                ByteArrayOutputStream response = new ByteArrayOutputStream();
                socket.getInputStream().transferTo(response);
                return response.toString(ISO_8859_1);
            }
        }
    }
}
