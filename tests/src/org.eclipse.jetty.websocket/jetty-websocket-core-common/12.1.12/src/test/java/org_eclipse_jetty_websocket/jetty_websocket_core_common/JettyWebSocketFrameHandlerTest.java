/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_core_common;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.JettyWebSocketFrameHandler;
import org.eclipse.jetty.websocket.common.JettyWebSocketFrameHandlerMetadata;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.core.WebSocketComponents;
import org.eclipse.jetty.websocket.core.messages.ByteArrayMessageSink;
import org.eclipse.jetty.websocket.core.messages.ByteBufferMessageSink;
import org.eclipse.jetty.websocket.core.messages.InputStreamMessageSink;
import org.eclipse.jetty.websocket.core.messages.MessageSink;
import org.eclipse.jetty.websocket.core.messages.PartialByteArrayMessageSink;
import org.eclipse.jetty.websocket.core.messages.PartialByteBufferMessageSink;
import org.eclipse.jetty.websocket.core.messages.PartialStringMessageSink;
import org.eclipse.jetty.websocket.core.messages.ReaderMessageSink;
import org.eclipse.jetty.websocket.core.messages.StringMessageSink;
import org.eclipse.jetty.websocket.core.util.MethodHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class JettyWebSocketFrameHandlerTest {
    @Test
    void deliversWholeTextMessage() throws Exception {
        assertDelivery(StringMessageSink.class, OpCode.TEXT, "text",
                List.of("hello world"), String.class);
    }

    @Test
    void deliversPartialTextMessages() throws Exception {
        assertDelivery(PartialStringMessageSink.class, OpCode.TEXT, "partialText",
                List.of("hello :false", "world:true"), String.class, boolean.class);
    }

    @Test
    void deliversWholeByteBufferMessage() throws Exception {
        assertDelivery(ByteBufferMessageSink.class, OpCode.BINARY, "buffer",
                List.of("hello world"), ByteBuffer.class);
    }

    @Test
    void deliversPartialByteBufferMessages() throws Exception {
        assertDelivery(PartialByteBufferMessageSink.class, OpCode.BINARY, "partialBuffer",
                List.of("hello :false", "world:true"), ByteBuffer.class, boolean.class);
    }

    @Test
    void deliversWholeByteArrayMessage() throws Exception {
        assertDelivery(ByteArrayMessageSink.class, OpCode.BINARY, "bytes",
                List.of("hello world"), byte[].class, int.class, int.class);
    }

    @Test
    void deliversPartialByteArrayMessages() throws Exception {
        assertDelivery(PartialByteArrayMessageSink.class, OpCode.BINARY, "partialBytes",
                List.of("hello :false", "world:true"), byte[].class, boolean.class);
    }

    @Test
    void deliversTextThroughReader() throws Exception {
        assertDelivery(ReaderMessageSink.class, OpCode.TEXT, "reader",
                List.of("hello world"), Reader.class);
    }

    @Test
    void deliversBinaryThroughInputStream() throws Exception {
        assertDelivery(InputStreamMessageSink.class, OpCode.BINARY, "stream",
                List.of("hello world"), InputStream.class);
    }

    private static void assertDelivery(Class<? extends MessageSink> sinkType, byte opcode, String method,
            List<String> expected, Class<?>... parameterTypes) throws Exception {
        WebSocketComponents components = new WebSocketComponents();
        WebSocketClient container = new WebSocketClient();
        try {
            components.start();
            Endpoint endpoint = new Endpoint();
            MethodHolder callback = MethodHolder.from(MethodHandles.lookup().findVirtual(
                    Endpoint.class, method, MethodType.methodType(void.class, parameterTypes)));
            JettyWebSocketFrameHandlerMetadata handlers = new JettyWebSocketFrameHandlerMetadata();
            handlers.setAutoDemand(true);
            handlers.setIdleTimeout(Duration.ofSeconds(30));
            if (opcode == OpCode.TEXT) {
                handlers.setTextHandle(sinkType, callback, method);
            } else {
                handlers.setBinaryHandle(sinkType, callback, method);
            }
            JettyWebSocketFrameHandler handler = new JettyWebSocketFrameHandler(container, endpoint, handlers);
            CoreSession session = new CoreSession.Empty() {
                @Override
                public WebSocketComponents getWebSocketComponents() {
                    return components;
                }
            };
            Callback.Completable opened = new Callback.Completable();
            handler.onOpen(session, opened);
            opened.get(30, TimeUnit.SECONDS);
            assertTrue(handler.getSession().isOpen());

            Callback.Completable first = new Callback.Completable();
            Callback.Completable last = new Callback.Completable();
            handler.onFrame(new Frame(opcode).setPayload("hello ").setFin(false), first);
            handler.onFrame(new Frame(OpCode.CONTINUATION).setPayload("world").setFin(true), last);
            first.get(30, TimeUnit.SECONDS);
            last.get(30, TimeUnit.SECONDS);
            for (String message : expected) {
                assertEquals(message, endpoint.messages.poll(30, TimeUnit.SECONDS));
            }
            assertTrue(endpoint.messages.isEmpty());

            Callback.Completable closed = new Callback.Completable();
            handler.onClosed(new CloseStatus(CloseStatus.NORMAL), closed);
            closed.get(30, TimeUnit.SECONDS);
        } finally {
            try {
                components.stop();
            } finally {
                container.stop();
            }
        }
    }

    public static class Endpoint {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        public void text(String text) {
            messages.add(text);
        }

        public void partialText(String text, boolean last) {
            messages.add(text + ":" + last);
        }

        public void buffer(ByteBuffer data) {
            messages.add(StandardCharsets.UTF_8.decode(data).toString());
        }

        public void partialBuffer(ByteBuffer data, boolean last) {
            messages.add(StandardCharsets.UTF_8.decode(data) + ":" + last);
        }

        public void bytes(byte[] data, int offset, int length) {
            messages.add(new String(data, offset, length, StandardCharsets.UTF_8));
        }

        public void partialBytes(byte[] data, boolean last) {
            messages.add(new String(data, StandardCharsets.UTF_8) + ":" + last);
        }

        public void reader(Reader reader) throws IOException {
            StringWriter output = new StringWriter();
            reader.transferTo(output);
            messages.add(output.toString());
        }

        public void stream(InputStream stream) throws IOException {
            messages.add(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
