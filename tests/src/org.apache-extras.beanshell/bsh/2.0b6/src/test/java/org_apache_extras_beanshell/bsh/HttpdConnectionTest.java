/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.util.Httpd;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpdConnectionTest {

    @Test
    public void rootRequestServesClasspathResource() throws Exception {
        int port = findAvailablePort();
        Httpd httpd = new Httpd(port);
        httpd.setDaemon(true);
        httpd.start();

        String response = request(port, "/");

        assertThat(response).contains("HTTP/1.0 200 Document follows");
        assertThat(response).contains("Content-Type: text/html");
        assertThat(response).contains("<title>BeanShell Remote Session</title>");
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static String request(int port, String path) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(5000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("GET " + path + " HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                response.write(buffer, 0, read);
            }
            return response.toString(StandardCharsets.UTF_8);
        }
    }
}
