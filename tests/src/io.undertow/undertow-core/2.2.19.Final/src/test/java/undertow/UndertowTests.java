/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowMessages;
import io.undertow.client.UndertowClientMessages;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.Headers;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.jsr.JsrWebSocketMessages;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.xnio.IoFuture;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class UndertowTests {

    private static final int PORT = 8080;

    @Test
    void core() throws Exception {
        // Start server
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(exchange -> {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send("Hello World");
                }).build();
        server.start();
        try {
            // Make request
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", PORT)))
                    .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello World");
        } finally {
            // Cleanup
            server.stop();
        }
    }

    @Test
    void loggersWork() {
        UndertowServletLogger.ROOT_LOGGER.log(Logger.Level.INFO, "test");
        UndertowServletLogger.REQUEST_LOGGER.log(Logger.Level.INFO, "test");
    }

    @Test
    void messagesWork() {
        assertThat(UndertowMessages.MESSAGES.expectedContinuationFrame()).isNotNull();
        assertThat(UndertowClientMessages.MESSAGES.connectionClosed()).isNotNull();
        assertThat(UndertowServletMessages.MESSAGES.asyncAlreadyStarted().getMessage()).isNotNull();
        assertThat(WebSocketMessages.MESSAGES.channelClosed().getMessage()).isNotNull();
        assertThat(JsrWebSocketMessages.MESSAGES.clientNotSupported().getMessage()).isNotNull();
    }

    @Test
    void servlet() throws Exception {
        // Start server
        DeploymentInfo servletBuilder = Servlets.deployment()
                .setClassLoader(MessageServlet.class.getClassLoader())
                .setContextPath("/myapp")
                .setDeploymentName("test")
                .addServlets(
                        Servlets.servlet("MessageServlet", MessageServlet.class, new ImmediateInstanceFactory<>(new MessageServlet())).addMapping("/hello")
                );
        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path = Handlers.path(Handlers.redirect("/myapp"))
                .addPrefixPath("/myapp", manager.start());
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(path)
                .build();
        server.start();
        try {
            // Make request
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/myapp/hello", PORT)))
                    .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            // Cleanup
            server.stop();
        }
    }

    @Test
    void websocket() throws Exception {
        // Start server
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(Handlers.websocket((exchange, channel) -> {
                    System.out.printf("onConnect: %s%n", channel);
                    channel.getReceiveSetter().set(new AbstractReceiveListener() {
                        @Override
                        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                            String data = message.getData();
                            System.out.printf("Server: onFullTextMessage: %s %s%n", channel, data);
                            WebSockets.sendText(data, channel, null);
                        }
                    });
                    channel.resumeReceives();
                })).build();
        server.start();
        try {
            String received = sendWebSocketRequest(server.getWorker(), "/", "Hello websocket");
            assertThat(received).isEqualTo("Hello websocket");
        } finally {
            // Cleanup
            server.stop();
        }
    }

    @Test
    void websocketJsr() throws Exception {
        // Start server
        PathHandler path = Handlers.path();
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(path)
                .build();
        ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(WebSocketHandler.class.getClassLoader())
                .setContextPath("/")
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(new DefaultByteBufferPool(true, 100))
                                .addEndpoint(WebSocketHandler.class)
                )
                .setDeploymentName("websocket-jsr");
        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath("/", manager.start());
        server.start();
        try {
            String received = sendWebSocketRequest(server.getWorker(), "/websocket", "Hello websocket");
            assertThat(received).isEqualTo("Hello websocket");
        } finally {
            // Cleanup
            server.stop();
        }
    }

    private String sendWebSocketRequest(XnioWorker worker, String path, String text) throws IOException, InterruptedException {
        try (ByteBufferPool pool = new DefaultByteBufferPool(false, 8192)) {
            IoFuture<WebSocketChannel> future = WebSocketClient.connectionBuilder(worker, pool, URI.create(String.format("ws://localhost:%d%s", PORT, path))).connect();
            IoFuture.Status status = future.await(1, TimeUnit.SECONDS);
            if (status != IoFuture.Status.DONE) {
                throw new IOException("Failed to establish websocket client connection");
            }
            try (WebSocketChannel channel = future.get()) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                AtomicReference<String> received = new AtomicReference<>();
                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                        String data = message.getData();
                        System.out.printf("Client: onFullTextMessage: %s %s%n", channel, data);
                        received.set(data);
                        countDownLatch.countDown();
                    }
                });
                channel.resumeReceives();
                WebSockets.sendText(text, channel, null);
                if (!countDownLatch.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Failed to receive echo websocket message");
                }
                return received.get();
            }
        }
    }
}
