/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.servlet.ServletHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletContextImplTest {
    private static final String HOST = "127.0.0.1";
    private static final int TIMEOUT_MILLIS = 3_000;
    private static final String RESOURCE_PATH = "/servlet-context-impl-resource.txt";
    private static final String RESOURCE_CONTENT = "resource loaded through ServletContextImpl";

    @Test
    void servletContextLoadsClasspathResourcesByUrlAndStream() throws Exception {
        ServletHandler handler = new ServletHandler(new ResourceLoadingServlet());
        handler.setContextPath("/resources");
        handler.setServletPath("/load");

        ServerHandle server = startServer(handler, "/resources/load");
        try {
            HttpResponse response = get(server.port(), "/resources/load");

            assertThat(response.status).isEqualTo(HttpURLConnection.HTTP_OK);
            Map<String, String> lines = parseBody(response.body);
            assertThat(lines).containsEntry("resourceUrlPresent", "true");
            assertThat(lines).containsEntry("resourceUrlContent", RESOURCE_CONTENT);
            assertThat(lines).containsEntry("resourceStreamContent", RESOURCE_CONTENT);
        } finally {
            server.close();
        }
    }

    private static ServerHandle startServer(ServletHandler handler, String mapping) throws IOException {
        int port = availablePort();
        HttpServer server = new HttpServer();
        server.addListener(new NetworkListener("servlet-context-impl-test-listener", HOST, port));
        server.getServerConfiguration().addHttpHandler(handler, mapping);
        server.start();
        return new ServerHandle(server, port);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpResponse get(int port, String path) throws IOException {
        URL url = new URL("http", HOST, port, path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Connection", "close");
        try {
            int status = connection.getResponseCode();
            InputStream input = status >= HttpURLConnection.HTTP_BAD_REQUEST
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String body = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new HttpResponse(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static Map<String, String> parseBody(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : body.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return values;
    }

    private record ServerHandle(HttpServer server, int port) implements AutoCloseable {
        @Override
        public void close() {
            server.stop();
        }
    }

    private record HttpResponse(int status, String body) {
    }

    private static final class ResourceLoadingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            URL resourceUrl = getServletContext().getResource(RESOURCE_PATH);
            String resourceUrlContent = resourceUrl == null
                    ? ""
                    : new String(resourceUrl.openStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String resourceStreamContent;
            try (InputStream resourceStream = getServletContext().getResourceAsStream(RESOURCE_PATH)) {
                resourceStreamContent = resourceStream == null
                        ? ""
                        : new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain");
            response.getWriter().println("resourceUrlPresent=" + (resourceUrl != null));
            response.getWriter().println("resourceUrlContent=" + resourceUrlContent);
            response.getWriter().println("resourceStreamContent=" + resourceStreamContent);
        }
    }
}
