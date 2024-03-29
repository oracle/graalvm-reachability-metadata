/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyTests {

    private static final int PORT = 8080;

    private static boolean DEBUG = false;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", DEBUG ? "debug" : "warn");
    }

    @Test
    void typeUtilWorks() {
        assertThat(TypeUtil.fromName("java.lang.String")).isEqualTo(String.class);
    }

    @Test
    void http() throws Exception {
        Server server = new Server(PORT);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                response.setStatus(200);
                response.setHeader("Content-Type", "text/plain");
                response.getWriter().print("Hello world");
                response.getWriter().flush();
                baseRequest.setHandled(true);
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
    void forwardHeaders() throws Exception {
        Server server = new Server(PORT);
        for (Connector connector : server.getConnectors()) {
            for (ConnectionFactory connectionFactory : connector.getConnectionFactories()) {
                if (connectionFactory instanceof HttpConfiguration.ConnectionFactory) {
                    ((HttpConfiguration.ConnectionFactory) connectionFactory).getHttpConfiguration()
                            .addCustomizer(new ForwardedRequestCustomizer());
                }
            }
        }
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                response.setStatus(200);
                response.setHeader("Content-Type", "text/plain");
                response.getWriter().print("I am " + request.getServerName() + ":" + request.getServerPort());
                response.getWriter().flush();
                baseRequest.setHandled(true);
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

    @Test
    void servlet() throws Exception {
        Server server = new Server(PORT);
        ServletContextHandler handler = new ServletContextHandler();
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
    @SuppressWarnings("unchecked")
    void webapp(@TempDir File tempDir) throws Exception {
        Server server = new Server(PORT);
        WebAppContext context = new WebAppContext();
        // EnvConfiguration and PlusConfiguration uses JNDI, which is not what we want to include
        context.getConfigurations().remove(EnvConfiguration.class, PlusConfiguration.class);
        context.setContextPath("/");
        context.setResourceBase(tempDir.getAbsolutePath());
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
    void websocket() throws Exception {
        Server server = new Server(PORT);
        ServletContextHandler handler = new ServletContextHandler(server, "/");
        server.setHandler(handler);
        JettyWebSocketServletContainerInitializer.configure(handler, (servletContext, container) ->
                container.addMapping("/websocket", WebSocketServerEndpoint.class));
        server.start();
        try {
            doWebsocketRequest();
            // doBinaryWebsocketRequest();
        } finally {
            server.stop();
        }
    }

    @Test
    void jakartaWebsocket() throws Exception {
        Server server = new Server(PORT);
        ServletContextHandler handler = new ServletContextHandler(server, "/");
        server.setHandler(handler);
        JakartaWebSocketServletContainerInitializer.configure(handler, (servletContext, container) ->
                container.addEndpoint(JakartaWebSocketServerEndpoint.class));
        server.start();
        try {
            doWebsocketRequest();
            // doBinaryWebsocketRequest();
        } finally {
            server.stop();
        }
    }

    private static void doWebsocketRequest() throws Exception {
        WebSocketClient client = new WebSocketClient();
        client.start();
        try {
            WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint();
            Session session = client.connect(endpoint, URI.create(String.format("ws://localhost:%d/websocket", PORT))).get(1, TimeUnit.SECONDS);
            session.getRemote().sendString("Hello world");
            String message = endpoint.awaitStringMessage();
            assertThat(message).isEqualTo("Hello world");
            session.close();
        } finally {
            client.stop();
        }
    }

    private static void doBinaryWebsocketRequest() throws Exception {
        WebSocketClient client = new WebSocketClient();
        client.start();
        try {
            WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint();
            Session session = client.connect(endpoint, URI.create(String.format("ws://localhost:%d/websocket", PORT))).get(1, TimeUnit.SECONDS);
            session.getRemote().sendBytes(ByteBuffer.wrap("Hello world".getBytes(StandardCharsets.UTF_8)));
            byte[] message = endpoint.awaitBinaryMessage();
            assertThat(message).isEqualTo("Hello world".getBytes(StandardCharsets.UTF_8));
            session.close();
        } finally {
            client.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(String... headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", PORT)))
                .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
