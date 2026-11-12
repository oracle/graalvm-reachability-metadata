/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Encoder;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class WsServerContainerTest {
    @TempDir
    Path directory;
    private static final AtomicInteger ENCODERS = new AtomicInteger();
    private static final AtomicInteger CONFIGURATORS = new AtomicInteger();

    @Test
    void deploysAnnotatedEndpointWithEncoderAndCustomConfigurator() throws Exception {
        ENCODERS.set(0);
        CONFIGURATORS.set(0);
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory, Set.of(ConfiguredEcho.class))) {
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .preferredSubprotocols(List.of("chat")).build();
            Session session = server.track(server.client.connectToServer(endpoint, config, server.uri("/configured")));
            assertThat(session.getNegotiatedSubprotocol()).isEqualTo("chat");
            session.getBasicRemote().sendText("hello");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("server:hello");
            assertThat(ENCODERS.get()).isGreaterThanOrEqualTo(1);
            assertThat(CONFIGURATORS.get()).isEqualTo(1);
        }
    }

    @Test
    void validatesEncodersWithoutContainerInstanceManager() throws Exception {
        ENCODERS.set(0);
        ServletContainerInitializer initializer = (classes, context) -> {
            String attribute = InstanceManager.class.getName();
            Object instanceManager = context.getAttribute(attribute);
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            context.removeAttribute(attribute);
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            try {
                new WsSci().onStartup(classes, context);
            } finally {
                thread.setContextClassLoader(previous);
                if (instanceManager != null) {
                    context.setAttribute(attribute, instanceManager);
                }
            }
        };
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory,
                Set.of(ConfiguredEcho.class), initializer)) {
            assertThat(ENCODERS.get()).isEqualTo(1);
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            Session session = server.track(server.client.connectToServer(endpoint,
                    ClientEndpointConfig.Builder.create().build(), server.uri("/configured")));
            session.getBasicRemote().sendText("standalone encoder");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("server:standalone encoder");
            assertThat(ENCODERS.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void rejectsUnannotatedEndpointWithLocalizedDiagnostic() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            DeploymentException exception = assertThrows(DeploymentException.class,
                    () -> server.container().addEndpoint(UnannotatedEndpoint.class));
            assertThat(exception).hasMessageContaining(UnannotatedEndpoint.class.getName())
                    .hasMessageContaining("@ServerEndpoint");
        }
    }

    public record Reply(String text) {
    }

    public static class ReplyEncoder implements Encoder.Text<Reply> {
        public ReplyEncoder() {
            ENCODERS.incrementAndGet();
        }

        @Override
        public String encode(Reply reply) {
            return "server:" + reply.text();
        }
    }

    public static class ChatConfigurator extends ServerEndpointConfig.Configurator {
        public ChatConfigurator() {
            CONFIGURATORS.incrementAndGet();
        }
    }

    @ServerEndpoint(value = "/configured", subprotocols = "chat", encoders = ReplyEncoder.class,
            configurator = ChatConfigurator.class)
    public static class ConfiguredEcho {
        @OnMessage
        public Reply message(String text) {
            return new Reply(text);
        }
    }

    public static class UnannotatedEndpoint {
    }
}
