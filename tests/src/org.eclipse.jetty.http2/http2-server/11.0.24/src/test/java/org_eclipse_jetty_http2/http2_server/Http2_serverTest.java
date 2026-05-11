/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_http2.http2_server;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.http2.server.RawHTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Http2_serverTest {
    private static final byte[] CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final int FRAME_TYPE_DATA = 0;
    private static final int FRAME_TYPE_HEADERS = 1;
    private static final int FRAME_TYPE_RST_STREAM = 3;
    private static final int FRAME_TYPE_SETTINGS = 4;
    private static final int FRAME_TYPE_GO_AWAY = 7;
    private static final int FRAME_TYPE_WINDOW_UPDATE = 8;
    private static final int FLAG_ACK = 0x1;
    private static final int FLAG_END_STREAM = 0x1;
    private static final int FLAG_END_HEADERS = 0x4;

    @Test
    void validatesProtocolsCipherRulesAndFactoryConfiguration() {
        assertThat(HTTP2ServerConnection.isSupportedProtocol("h2")).isTrue();
        assertThat(HTTP2ServerConnection.isSupportedProtocol("h2c-17")).isTrue();
        assertThat(HTTP2ServerConnection.isSupportedProtocol("http/1.1")).isFalse();

        HTTP2ServerConnectionFactory tlsFactory = new HTTP2ServerConnectionFactory(new HttpConfiguration());
        assertThat(tlsFactory.isAcceptable("h2", "TLSv1.3", "TLS_AES_128_GCM_SHA256")).isTrue();
        assertThat(tlsFactory.isAcceptable("h2", "TLSv1.2", "TLS_RSA_WITH_AES_128_CBC_SHA")).isFalse();
        assertThat(tlsFactory.isAcceptable("h2-14", "TLSv1.2", "TLS_RSA_WITH_AES_128_CBC_SHA")).isTrue();

        HttpConfiguration configuration = new HttpConfiguration();
        configuration.setUseInputDirectByteBuffers(true);
        configuration.setUseOutputDirectByteBuffers(false);
        HTTP2CServerConnectionFactory clearTextFactory = new HTTP2CServerConnectionFactory(
                configuration,
                "h2c",
                "h2c-16");
        clearTextFactory.setMaxDecoderTableCapacity(2048);
        clearTextFactory.setMaxEncoderTableCapacity(1024);
        clearTextFactory.setInitialSessionRecvWindow(131_072);
        clearTextFactory.setInitialStreamRecvWindow(98_304);
        clearTextFactory.setMaxConcurrentStreams(17);
        clearTextFactory.setMaxHeaderBlockFragment(4096);
        clearTextFactory.setMaxFrameSize(16_384);
        clearTextFactory.setMaxSettingsKeys(12);
        clearTextFactory.setConnectProtocolEnabled(false);
        clearTextFactory.setStreamIdleTimeout(2500);
        clearTextFactory.setUseInputDirectByteBuffers(false);
        clearTextFactory.setUseOutputDirectByteBuffers(true);

        assertThat(clearTextFactory.getProtocols()).containsExactly("h2c", "h2c-16");
        assertThat(clearTextFactory.isAcceptable("h2c", "TLSv1.3", "TLS_AES_128_GCM_SHA256")).isFalse();
        assertThat(clearTextFactory.getHttpConfiguration()).isSameAs(configuration);
        assertThat(clearTextFactory.getMaxDecoderTableCapacity()).isEqualTo(2048);
        assertThat(clearTextFactory.getMaxEncoderTableCapacity()).isEqualTo(1024);
        assertThat(clearTextFactory.getInitialSessionRecvWindow()).isEqualTo(131_072);
        assertThat(clearTextFactory.getInitialStreamRecvWindow()).isEqualTo(98_304);
        assertThat(clearTextFactory.getMaxConcurrentStreams()).isEqualTo(17);
        assertThat(clearTextFactory.getMaxHeaderBlockFragment()).isEqualTo(4096);
        assertThat(clearTextFactory.getMaxFrameSize()).isEqualTo(16_384);
        assertThat(clearTextFactory.getMaxSettingsKeys()).isEqualTo(12);
        assertThat(clearTextFactory.isConnectProtocolEnabled()).isFalse();
        assertThat(clearTextFactory.getStreamIdleTimeout()).isEqualTo(2500);
        assertThat(clearTextFactory.isUseInputDirectByteBuffers()).isFalse();
        assertThat(clearTextFactory.isUseOutputDirectByteBuffers()).isTrue();
        assertThat(clearTextFactory.getFlowControlStrategyFactory().newFlowControlStrategy()).isNotNull();
        assertThat(clearTextFactory.getRateControlFactory().newRateControl(null)).isNotNull();

        assertThatThrownBy(() -> new HTTP2CServerConnectionFactory(configuration, "h2c", "spdy/3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HTTP2 Protocol variant");
    }

    @Test
    void clearTextHttp2ServerHandlesGetAndPostRequestsOverOneConnection() throws Exception {
        HttpConfiguration configuration = new HttpConfiguration();
        configuration.setRequestHeaderSize(8192);
        HTTP2CServerConnectionFactory connectionFactory = new HTTP2CServerConnectionFactory(configuration);
        connectionFactory.setInitialStreamRecvWindow(70_000);
        connectionFactory.setMaxConcurrentStreams(11);
        connectionFactory.setConnectProtocolEnabled(false);

        Server server = newServer(connectionFactory);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(
                    String target,
                    Request baseRequest,
                    HttpServletRequest request,
                    HttpServletResponse response) throws IOException, ServletException {
                baseRequest.setHandled(true);
                if ("/hello".equals(target)) {
                    response.setStatus(HttpStatus.OK_200);
                    response.setContentType("text/plain;charset=utf-8");
                    response.setHeader("x-test-path", target);
                    response.getOutputStream().write("hello over h2c".getBytes(StandardCharsets.UTF_8));
                } else if ("/echo".equals(target)) {
                    String body = request.getReader().lines().collect(Collectors.joining("\n"));
                    response.setStatus(HttpStatus.CREATED_201);
                    response.setHeader("x-request-method", request.getMethod());
                    response.getOutputStream().write(("echo:" + body).getBytes(StandardCharsets.UTF_8));
                } else {
                    response.sendError(HttpStatus.NOT_FOUND_404);
                }
            }
        });

        try (StartedServer startedServer = start(server);
             Http2SocketClient client = Http2SocketClient.connect(startedServer.port())) {
            Map<Integer, Integer> serverSettings = client.getServerSettings();
            assertThat(serverSettings).containsEntry(SettingsFrame.INITIAL_WINDOW_SIZE, 70_000);
            assertThat(serverSettings).containsEntry(SettingsFrame.MAX_CONCURRENT_STREAMS, 11);
            assertThat(serverSettings).containsEntry(SettingsFrame.MAX_HEADER_LIST_SIZE, 8192);
            assertThat(serverSettings).containsEntry(SettingsFrame.ENABLE_CONNECT_PROTOCOL, 0);

            HttpFields.Mutable getFields = HttpFields.build();
            getFields.add("x-client", "raw-h2c");
            client.sendHeaders(1, "GET", startedServer.port(), "/hello", getFields, 0, true);
            Http2Response getResponse = client.readResponse(1);
            assertThat(getResponse.status()).isEqualTo(HttpStatus.OK_200);
            assertThat(getResponse.headers().get("x-test-path")).isEqualTo("/hello");
            assertThat(getResponse.trailers().size()).isZero();
            assertThat(getResponse.bodyAsString()).isEqualTo("hello over h2c");

            byte[] requestBody = "payload from client".getBytes(StandardCharsets.UTF_8);
            HttpFields.Mutable postFields = HttpFields.build();
            postFields.add("content-type", "text/plain;charset=utf-8");
            client.sendHeaders(3, "POST", startedServer.port(), "/echo", postFields, requestBody.length, false);
            client.sendData(3, requestBody, true);
            Http2Response postResponse = client.readResponse(3);
            assertThat(postResponse.status()).isEqualTo(HttpStatus.CREATED_201);
            assertThat(postResponse.headers().get("x-request-method")).isEqualTo("POST");
            assertThat(postResponse.trailers().size()).isZero();
            assertThat(postResponse.bodyAsString()).isEqualTo("echo:payload from client");
        }
    }

    @Test
    void clearTextHttp2ServerSendsResponseTrailers() throws Exception {
        Server server = newServer(new HTTP2CServerConnectionFactory(new HttpConfiguration()));
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(
                    String target,
                    Request baseRequest,
                    HttpServletRequest request,
                    HttpServletResponse response) throws IOException, ServletException {
                baseRequest.setHandled(true);
                if (!"/trailers".equals(target)) {
                    response.sendError(HttpStatus.NOT_FOUND_404);
                    return;
                }

                byte[] body = "body with trailers".getBytes(StandardCharsets.UTF_8);
                response.setStatus(HttpStatus.OK_200);
                response.setContentType("text/plain;charset=utf-8");
                response.setTrailerFields(() -> Map.of(
                        "x-response-trailer", "trailers-supported",
                        "x-body-length", String.valueOf(body.length)));
                response.getOutputStream().write(body);
            }
        });

        try (StartedServer startedServer = start(server);
             Http2SocketClient client = Http2SocketClient.connect(startedServer.port())) {
            client.sendHeaders(1, "GET", startedServer.port(), "/trailers", HttpFields.EMPTY, 0, true);
            Http2Response response = client.readResponse(1);

            assertThat(response.status()).isEqualTo(HttpStatus.OK_200);
            assertThat(response.bodyAsString()).isEqualTo("body with trailers");
            assertThat(response.headers().get("x-response-trailer")).isNull();
            assertThat(response.trailers().get("x-response-trailer")).isEqualTo("trailers-supported");
            assertThat(response.trailers().get("x-body-length")).isEqualTo(String.valueOf(response.body().length));
        }
    }

    @Test
    void rawHttp2ServerConnectionFactoryDispatchesRequestsToSessionListener() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch prefaced = new CountDownLatch(1);
        CountDownLatch streamed = new CountDownLatch(1);
        AtomicReference<MetaData.Request> rawRequest = new AtomicReference<>();
        AtomicBoolean endStream = new AtomicBoolean();

        ServerSessionListener listener = new ServerSessionListener.Adapter() {
            @Override
            public void onAccept(Session session) {
                accepted.countDown();
            }

            @Override
            public Map<Integer, Integer> onPreface(Session session) {
                prefaced.countDown();
                return Map.of(SettingsFrame.MAX_CONCURRENT_STREAMS, 5);
            }

            @Override
            public Stream.Listener onNewStream(Stream stream, HeadersFrame frame) {
                rawRequest.set((MetaData.Request) frame.getMetaData());
                endStream.set(frame.isEndStream());
                streamed.countDown();

                HttpFields.Mutable fields = HttpFields.build();
                fields.add("x-raw-listener", "invoked");
                MetaData.Response response = new MetaData.Response(
                        HttpVersion.HTTP_2,
                        HttpStatus.NO_CONTENT_204,
                        fields);
                stream.headers(new HeadersFrame(stream.getId(), response, null, true), Callback.NOOP);
                return null;
            }
        };

        Server server = newServer(new RawHTTP2ServerConnectionFactory(listener));
        try (StartedServer startedServer = start(server);
             Http2SocketClient client = Http2SocketClient.connect(startedServer.port())) {
            assertThat(client.getServerSettings()).containsEntry(SettingsFrame.MAX_CONCURRENT_STREAMS, 5);
            client.sendHeaders(1, "GET", startedServer.port(), "/raw", HttpFields.EMPTY, 0, true);
            Http2Response response = client.readResponse(1);

            assertThat(response.status()).isEqualTo(HttpStatus.NO_CONTENT_204);
            assertThat(response.headers().get("x-raw-listener")).isEqualTo("invoked");
            assertThat(response.trailers().size()).isZero();
            assertThat(response.body()).isEmpty();
            assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(prefaced.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(streamed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(rawRequest.get().getMethod()).isEqualTo("GET");
            assertThat(rawRequest.get().getURI().getPath()).isEqualTo("/raw");
            assertThat(endStream.get()).isTrue();
        }
    }

    private static Server newServer(ConnectionFactory connectionFactory) {
        Server server = new Server();
        server.setStopTimeout(5000);
        ServerConnector connector = new ServerConnector(server, connectionFactory);
        connector.setHost("127.0.0.1");
        connector.setIdleTimeout(5000);
        server.addConnector(connector);
        return server;
    }

    private static StartedServer start(Server server) throws Exception {
        server.start();
        ServerConnector connector = (ServerConnector) server.getConnectors()[0];
        return new StartedServer(server, connector.getLocalPort());
    }

    private static final class StartedServer implements Closeable {
        private final Server server;
        private final int port;

        private StartedServer(Server server, int port) {
            this.server = server;
            this.port = port;
        }

        private int port() {
            return port;
        }

        @Override
        public void close() throws IOException {
            try {
                server.stop();
            } catch (Exception e) {
                throw new IOException(e);
            } finally {
                server.destroy();
            }
        }
    }

    private static final class Http2SocketClient implements Closeable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final HpackEncoder encoder = new HpackEncoder();
        private final HpackDecoder decoder = new HpackDecoder(8192, System::nanoTime);
        private final Map<Integer, Integer> serverSettings;

        private Http2SocketClient(Socket socket) throws Exception {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
            this.serverSettings = sendPrefaceAndReadSettings();
        }

        private static Http2SocketClient connect(int port) throws Exception {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(5000);
            return new Http2SocketClient(socket);
        }

        private Map<Integer, Integer> getServerSettings() {
            return serverSettings;
        }

        private Map<Integer, Integer> sendPrefaceAndReadSettings() throws Exception {
            output.write(CLIENT_PREFACE);
            writeFrame(FRAME_TYPE_SETTINGS, 0, 0, new byte[0]);
            output.flush();

            while (true) {
                Http2Frame frame = readFrame();
                if (frame.type() == FRAME_TYPE_SETTINGS && (frame.flags() & FLAG_ACK) == 0) {
                    writeFrame(FRAME_TYPE_SETTINGS, FLAG_ACK, 0, new byte[0]);
                    output.flush();
                    return parseSettings(frame.payload());
                }
                if (frame.type() == FRAME_TYPE_GO_AWAY) {
                    throw new IOException("Server closed the HTTP/2 session before sending SETTINGS");
                }
            }
        }

        private void sendHeaders(int streamId, String method, int port, String path, HttpFields fields,
                long contentLength, boolean endStream) throws Exception {
            MetaData.Request request = new MetaData.Request(
                    method,
                    HttpURI.build().scheme("http").host("localhost").port(port).pathQuery(path),
                    HttpVersion.HTTP_2,
                    fields,
                    contentLength);
            ByteBuffer headerBlock = ByteBuffer.allocate(1024);
            encoder.encode(headerBlock, request);
            headerBlock.flip();
            byte[] payload = new byte[headerBlock.remaining()];
            headerBlock.get(payload);
            int flags = FLAG_END_HEADERS | (endStream ? FLAG_END_STREAM : 0);
            writeFrame(FRAME_TYPE_HEADERS, flags, streamId, payload);
            output.flush();
        }

        private void sendData(int streamId, byte[] data, boolean endStream) throws IOException {
            writeFrame(FRAME_TYPE_DATA, endStream ? FLAG_END_STREAM : 0, streamId, data);
            output.flush();
        }

        private Http2Response readResponse(int streamId) throws Exception {
            Integer status = null;
            HttpFields headers = HttpFields.EMPTY;
            HttpFields trailers = HttpFields.EMPTY;
            ByteArrayOutputStream body = new ByteArrayOutputStream();

            while (true) {
                Http2Frame frame = readFrame();
                if (frame.type() == FRAME_TYPE_SETTINGS && (frame.flags() & FLAG_ACK) == 0) {
                    writeFrame(FRAME_TYPE_SETTINGS, FLAG_ACK, 0, new byte[0]);
                    output.flush();
                    continue;
                }
                if (frame.type() == FRAME_TYPE_GO_AWAY) {
                    throw new IOException("HTTP/2 session closed while waiting for stream " + streamId);
                }
                if (frame.type() == FRAME_TYPE_WINDOW_UPDATE || frame.streamId() != streamId) {
                    continue;
                }
                if (frame.type() == FRAME_TYPE_HEADERS) {
                    MetaData metaData = decoder.decode(ByteBuffer.wrap(frame.payload()));
                    if (metaData instanceof MetaData.Response) {
                        MetaData.Response response = (MetaData.Response) metaData;
                        status = response.getStatus();
                        headers = response.getFields();
                    } else {
                        trailers = metaData.getFields();
                    }
                    if ((frame.flags() & FLAG_END_STREAM) != 0) {
                        return new Http2Response(status, headers, trailers, body.toByteArray());
                    }
                } else if (frame.type() == FRAME_TYPE_DATA) {
                    body.write(frame.payload());
                    if ((frame.flags() & FLAG_END_STREAM) != 0) {
                        return new Http2Response(status, headers, trailers, body.toByteArray());
                    }
                } else if (frame.type() == FRAME_TYPE_RST_STREAM) {
                    throw new IOException("Stream " + streamId + " was reset");
                }
            }
        }

        private Http2Frame readFrame() throws IOException {
            byte[] header = readFully(9);
            int length = ((header[0] & 0xFF) << 16) | ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
            int type = header[3] & 0xFF;
            int flags = header[4] & 0xFF;
            int streamId = ((header[5] & 0x7F) << 24) | ((header[6] & 0xFF) << 16)
                    | ((header[7] & 0xFF) << 8) | (header[8] & 0xFF);
            byte[] payload = readFully(length);
            return new Http2Frame(length, type, flags, streamId, payload);
        }

        private byte[] readFully(int length) throws IOException {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = input.read(bytes, offset, length - offset);
                if (read < 0) {
                    throw new EOFException("Unexpected end of HTTP/2 stream");
                }
                offset += read;
            }
            return bytes;
        }

        private void writeFrame(int type, int flags, int streamId, byte[] payload) throws IOException {
            int length = payload.length;
            byte[] header = new byte[9];
            header[0] = (byte) ((length >>> 16) & 0xFF);
            header[1] = (byte) ((length >>> 8) & 0xFF);
            header[2] = (byte) (length & 0xFF);
            header[3] = (byte) type;
            header[4] = (byte) flags;
            header[5] = (byte) ((streamId >>> 24) & 0x7F);
            header[6] = (byte) ((streamId >>> 16) & 0xFF);
            header[7] = (byte) ((streamId >>> 8) & 0xFF);
            header[8] = (byte) (streamId & 0xFF);
            output.write(header);
            output.write(payload);
        }

        private Map<Integer, Integer> parseSettings(byte[] payload) {
            assertThat(payload.length % 6).isZero();
            Map<Integer, Integer> settings = new LinkedHashMap<>();
            for (int i = 0; i < payload.length; i += 6) {
                int key = ((payload[i] & 0xFF) << 8) | (payload[i + 1] & 0xFF);
                int value = ((payload[i + 2] & 0xFF) << 24) | ((payload[i + 3] & 0xFF) << 16)
                        | ((payload[i + 4] & 0xFF) << 8) | (payload[i + 5] & 0xFF);
                settings.put(key, value);
            }
            return settings;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class Http2Frame {
        private final int length;
        private final int type;
        private final int flags;
        private final int streamId;
        private final byte[] payload;

        private Http2Frame(int length, int type, int flags, int streamId, byte[] payload) {
            this.length = length;
            this.type = type;
            this.flags = flags;
            this.streamId = streamId;
            this.payload = Arrays.copyOf(payload, payload.length);
        }

        private int type() {
            return type;
        }

        private int flags() {
            return flags;
        }

        private int streamId() {
            return streamId;
        }

        private byte[] payload() {
            return Arrays.copyOf(payload, length);
        }
    }

    private static final class Http2Response {
        private final Integer status;
        private final HttpFields headers;
        private final HttpFields trailers;
        private final byte[] body;

        private Http2Response(Integer status, HttpFields headers, HttpFields trailers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.trailers = trailers;
            this.body = Arrays.copyOf(body, body.length);
        }

        private Integer status() {
            return status;
        }

        private HttpFields headers() {
            return headers;
        }

        private HttpFields trailers() {
            return trailers;
        }

        private byte[] body() {
            return Arrays.copyOf(body, body.length);
        }

        private String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
