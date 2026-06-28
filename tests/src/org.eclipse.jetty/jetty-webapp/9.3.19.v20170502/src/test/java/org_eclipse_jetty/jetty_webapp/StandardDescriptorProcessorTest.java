/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import java.net.HttpURLConnection;
import java.net.URL;
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

            HttpURLConnection connection = openConnection(connector.getLocalPort(), "/index.txt");
            try {
                assertThat(connection.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
                assertThat(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo("served by WebAppContext");
            } finally {
                connection.disconnect();
            }
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

    private static HttpURLConnection openConnection(int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        return connection;
    }
}
