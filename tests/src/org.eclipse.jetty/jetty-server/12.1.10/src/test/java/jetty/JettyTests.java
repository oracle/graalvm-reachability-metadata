/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;


import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.URLResourceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyTests {

    private static int port;

    private static final boolean DEBUG = false;

    @BeforeAll
    static void beforeAll() throws IOException {
        port = findAvailablePort();
        System.out.println("Using port " + port + " for Jetty");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", DEBUG ? "debug" : "warn");
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(null);
            return socket.getLocalPort();
        }
    }

    @Test
    void typeUtilWorks() {
        assertThat(TypeUtil.fromName("java.lang.String")).isEqualTo(String.class);
    }

    @Test
    void http() throws Exception {
        Server server = new Server(port);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.setStatus(200);
                response.getHeaders().add("Content-Type", "text/plain");
                response.write(true, ByteBuffer.wrap("Hello world".getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    void servletEe10() throws Exception {
        Server server = new Server(port);
        org.eclipse.jetty.ee10.servlet.ServletContextHandler handler = new org.eclipse.jetty.ee10.servlet.ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(HelloWorldServlet.class, "/*");
        server.setHandler(handler);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    void servletEe11() throws Exception {
        Server server = new Server(port);
        org.eclipse.jetty.ee11.servlet.ServletContextHandler handler = new org.eclipse.jetty.ee11.servlet.ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(HelloWorldServlet.class, "/*");
        server.setHandler(handler);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    void webappEe10(@TempDir Path tempDir) throws Exception {
        // This is needed to work in a native image, otherwise it fails with:
        // java.lang.IllegalArgumentException: not an allowed scheme: resource:/org/eclipse/jetty/ee10/webapp/webdefault-ee10.xml
        // See https://github.com/eclipse/jetty.project/issues/9116
        ResourceFactory.registerResourceFactory("resource", new URLResourceFactory());

        Server server = new Server(port);
        org.eclipse.jetty.ee10.webapp.WebAppContext context = new org.eclipse.jetty.ee10.webapp.WebAppContext();
        // EnvConfiguration and PlusConfiguration uses JNDI, which is not what we want to include
        context.setBaseResourceAsPath(tempDir);
        context.setContextPath("/");
        context.getServletContext().addServlet("HelloWorld", HelloWorldServlet.class).addMapping("/*");
        server.setHandler(context);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    void webappEe11(@TempDir Path tempDir) throws Exception {
        // This is needed to work in a native image, otherwise it fails with:
        // java.lang.IllegalArgumentException: not an allowed scheme: resource:/org/eclipse/jetty/ee10/webapp/webdefault-ee10.xml
        // See https://github.com/eclipse/jetty.project/issues/9116
        ResourceFactory.registerResourceFactory("resource", new URLResourceFactory());

        Server server = new Server(port);
        org.eclipse.jetty.ee11.webapp.WebAppContext context = new org.eclipse.jetty.ee11.webapp.WebAppContext();
        // EnvConfiguration and PlusConfiguration uses JNDI, which is not what we want to include
        context.setBaseResourceAsPath(tempDir);
        context.setContextPath("/");
        context.getServletContext().addServlet("HelloWorld", HelloWorldServlet.class).addMapping("/*");
        server.setHandler(context);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    void forwardHeaders() throws Exception {
        Server server = new Server(port);
        for (Connector connector : server.getConnectors()) {
            for (ConnectionFactory connectionFactory : connector.getConnectionFactories()) {
                if (connectionFactory instanceof HttpConfiguration.ConnectionFactory) {
                    ((HttpConfiguration.ConnectionFactory) connectionFactory).getHttpConfiguration()
                            .addCustomizer(new ForwardedRequestCustomizer());
                }
            }
        }
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.setStatus(200);
                response.getHeaders().add("Content-Type", "text/plain");
                String content = "I am " + Request.getServerName(request) + ":" + Request.getServerPort(request);
                response.write(true, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest("X-Forwarded-Host", "some-host", "X-Forwarded-Port", "12345");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("I am some-host:12345");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(String... headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", port)))
                .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
