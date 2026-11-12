/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.server.WsSci;

final class EmbeddedWebSocketServer implements AutoCloseable {
    private final Tomcat tomcat = new Tomcat();
    private final Context context;
    private final List<Session> sessions = new ArrayList<>();
    final WsWebSocketContainer client = new WsWebSocketContainer();

    EmbeddedWebSocketServer(Path directory) throws Exception {
        this(directory, Set.of(Echo.class));
    }

    EmbeddedWebSocketServer(Path directory, Set<Class<?>> discovered) throws Exception {
        this(directory, discovered, new WsSci());
    }

    EmbeddedWebSocketServer(Path directory, Set<Class<?>> discovered, ServletContainerInitializer initializer)
            throws Exception {
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getConnector().setProperty("connectionTimeout", "20000");
        Path docBase = Files.createDirectories(directory.resolve("webapp"));
        context = tomcat.addContext("", docBase.toString());
        context.setParentClassLoader(EmbeddedWebSocketServer.class.getClassLoader());
        Tomcat.addServlet(context, "default", new DefaultServlet());
        context.addServletMappingDecoded("/", "default");
        context.addServletContainerInitializer(initializer, discovered);
        client.setDefaultMaxSessionIdleTimeout(30000);
        client.setAsyncSendTimeout(20000);
        try {
            tomcat.start();
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    URI uri(String path) {
        return URI.create("ws://127.0.0.1:" + tomcat.getConnector().getLocalPort() + path);
    }

    ServerContainer container() {
        return (ServerContainer) context.getServletContext().getAttribute(ServerContainer.class.getName());
    }

    Session track(Session session) {
        sessions.add(session);
        return session;
    }

    Session connect(Endpoint endpoint) throws Exception {
        return track(client.connectToServer(endpoint, ClientEndpointConfig.Builder.create().build(), uri("/echo")));
    }

    static <T> T receive(BlockingQueue<T> messages) throws InterruptedException {
        T value = messages.poll(20, TimeUnit.SECONDS);
        if (value == null) {
            throw new AssertionError("Timed out waiting for a WebSocket callback");
        }
        return value;
    }

    @Override
    public void close() throws Exception {
        try {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    session.close();
                }
            }
        } finally {
            client.destroy();
            try {
                tomcat.stop();
            } finally {
                tomcat.destroy();
            }
        }
    }

    @ServerEndpoint("/echo")
    public static class Echo {
        @OnMessage
        public String text(String message) {
            return message;
        }

        @OnMessage
        public void binary(byte[] fragment, boolean last, Session session) throws IOException {
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(fragment), last);
        }
    }

    public static class TextReceiver extends Endpoint {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(String.class, messages::add);
        }
    }
}
