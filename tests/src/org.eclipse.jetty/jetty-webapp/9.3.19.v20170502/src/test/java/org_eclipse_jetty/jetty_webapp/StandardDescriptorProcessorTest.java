/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardDescriptorProcessorTest {
    private static final Charset HTTP_CHARSET = StandardCharsets.ISO_8859_1;

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    void startsWebAppFromDescriptorAndServesStaticContent() throws Exception {
        Path webApp = createWebApp();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);

        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setDefaultsDescriptor(null);
        context.setWar(webApp.toString());
        server.setHandler(context);

        try {
            server.start();

            assertThat(context.isAvailable()).isTrue();
            assertThat(context.getDisplayName()).isEqualTo("Descriptor WebApp");
            assertThat(context.getInitParameter("descriptor.processed")).isEqualTo("true");
            assertThat(context.getMimeTypes().getMimeByExtension("example.txt")).isEqualTo("text/example");

            HttpResponse response = request(connector.getLocalPort(), "/index.txt");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("served by WebAppContext");
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private Path createWebApp() throws Exception {
        Path webInf = temporaryDirectory.resolve("WEB-INF");
        Files.createDirectories(webInf);
        Files.writeString(temporaryDirectory.resolve("index.txt"), "served by WebAppContext", StandardCharsets.UTF_8);
        Files.writeString(webInf.resolve("web.xml"), """
                <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="3.1">
                  <display-name>Descriptor WebApp</display-name>
                  <context-param>
                    <param-name>descriptor.processed</param-name>
                    <param-value>true</param-value>
                  </context-param>
                  <servlet>
                    <servlet-name>default</servlet-name>
                    <servlet-class>org.eclipse.jetty.servlet.DefaultServlet</servlet-class>
                    <load-on-startup>0</load-on-startup>
                  </servlet>
                  <servlet-mapping>
                    <servlet-name>default</servlet-name>
                    <url-pattern>/</url-pattern>
                  </servlet-mapping>
                  <mime-mapping>
                    <extension>txt</extension>
                    <mime-type>text/example</mime-type>
                  </mime-mapping>
                  <welcome-file-list>
                    <welcome-file>index.txt</welcome-file>
                  </welcome-file-list>
                </web-app>
                """, StandardCharsets.UTF_8);
        return temporaryDirectory;
    }

    private static HttpResponse request(int port, String path) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 10_000);
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("""
                    GET %s HTTP/1.1\r
                    Host: 127.0.0.1\r
                    Connection: close\r
                    \r
                    """.formatted(path)).getBytes(HTTP_CHARSET));
            socket.getOutputStream().flush();

            String response = new String(socket.getInputStream().readAllBytes(), HTTP_CHARSET);
            int headerEnd = response.indexOf("\r\n\r\n");
            assertThat(headerEnd).isGreaterThanOrEqualTo(0);

            String statusLine = response.substring(0, response.indexOf("\r\n"));
            int statusCode = Integer.parseInt(statusLine.split(" ")[1]);
            String body = response.substring(headerEnd + 4);
            return new HttpResponse(statusCode, body);
        }
    }

    private record HttpResponse(int statusCode, String body) {
    }
}
