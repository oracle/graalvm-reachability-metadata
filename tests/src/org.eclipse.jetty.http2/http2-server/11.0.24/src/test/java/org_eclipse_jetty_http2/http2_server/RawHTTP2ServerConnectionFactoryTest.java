/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_http2.http2_server;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.GoAwayFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.PingFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.http2.server.RawHTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class RawHTTP2ServerConnectionFactoryTest {
    @Test
    void exchangesSettingsPingHeadersDataAndTrailers() throws Exception {
        CompletableFuture<MetaData.Request> receivedRequest = new CompletableFuture<>();
        CompletableFuture<GoAwayFrame> serverClosed = new CompletableFuture<>();
        CompletableFuture<MetaData.Response> responseHeaders = new CompletableFuture<>();
        CompletableFuture<String> responseBody = new CompletableFuture<>();
        CompletableFuture<HttpFields> responseTrailers = new CompletableFuture<>();
        ServerSessionListener listener = new ServerSessionListener.Adapter() {
            @Override
            public Map<Integer, Integer> onPreface(Session session) {
                return Map.of(SettingsFrame.MAX_CONCURRENT_STREAMS, 7);
            }

            @Override
            public Stream.Listener onNewStream(Stream stream, HeadersFrame frame) {
                receivedRequest.complete((MetaData.Request) frame.getMetaData());
                HttpFields fields = HttpFields.build().add("X-Frame-API", "raw");
                MetaData.Response headers = new MetaData.Response(HttpVersion.HTTP_2, 200, fields);
                stream.headers(new HeadersFrame(stream.getId(), headers, null, false), Callback.from(() -> {
                    ByteBuffer body = StandardCharsets.UTF_8.encode("response from raw frames");
                    stream.data(new DataFrame(stream.getId(), body, false), Callback.from(() -> {
                        MetaData trailers = new MetaData(HttpVersion.HTTP_2, HttpFields.build().add("X-Complete", "yes"));
                        stream.headers(new HeadersFrame(stream.getId(), trailers, null, true), Callback.NOOP);
                    }, responseBody::completeExceptionally));
                }, responseBody::completeExceptionally));
                return null;
            }

            @Override
            public void onClose(Session session, GoAwayFrame frame) {
                serverClosed.complete(frame);
            }
        };

        RawHTTP2ServerConnectionFactory factory = new RawHTTP2ServerConnectionFactory(listener);
        factory.setMaxConcurrentStreams(11);
        factory.setInitialStreamRecvWindow(32768);
        factory.setMaxDecoderTableCapacity(2048);
        factory.setConnectProtocolEnabled(false);
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, factory);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(30000);
        server.addConnector(connector);
        HTTP2Client client = new HTTP2Client();
        client.setConnectTimeout(30000);
        client.setIdleTimeout(30000);
        try {
            server.start();
            client.start();
            CompletableFuture<Map<Integer, Integer>> settings = new CompletableFuture<>();
            CompletableFuture<Long> pong = new CompletableFuture<>();
            Session session = client.connect(new InetSocketAddress("127.0.0.1", connector.getLocalPort()), new Session.Listener.Adapter() {
                @Override
                public void onSettings(Session current, SettingsFrame frame) {
                    if (!frame.isReply()) {
                        settings.complete(frame.getSettings());
                    }
                }

                @Override
                public void onPing(Session current, PingFrame frame) {
                    if (frame.isReply()) {
                        pong.complete(frame.getPayloadAsLong());
                    }
                }
            }).get(30, TimeUnit.SECONDS);

            assertThat(settings.get(30, TimeUnit.SECONDS))
                    .containsEntry(SettingsFrame.MAX_CONCURRENT_STREAMS, 7)
                    .containsEntry(SettingsFrame.INITIAL_WINDOW_SIZE, 32768)
                    .containsEntry(SettingsFrame.HEADER_TABLE_SIZE, 2048)
                    .containsEntry(SettingsFrame.ENABLE_CONNECT_PROTOCOL, 0);
            session.ping(new PingFrame(123456789L, false), Callback.NOOP);
            assertThat(pong.get(30, TimeUnit.SECONDS)).isEqualTo(123456789L);

            HttpURI uri = HttpURI.from("http://127.0.0.1:" + connector.getLocalPort() + "/frames?mode=raw");
            MetaData.Request request = new MetaData.Request("GET", uri, HttpVersion.HTTP_2, HttpFields.build().add("X-Request", "frames"));
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            session.newStream(new HeadersFrame(request, null, true), new Stream.Listener.Adapter() {
                @Override
                public void onHeaders(Stream stream, HeadersFrame frame) {
                    if (frame.getMetaData().isResponse()) {
                        responseHeaders.complete((MetaData.Response) frame.getMetaData());
                    } else if (frame.isEndStream()) {
                        responseTrailers.complete(frame.getMetaData().getFields());
                        responseBody.complete(content.toString(StandardCharsets.UTF_8));
                    }
                }

                @Override
                public void onData(Stream stream, DataFrame frame, Callback callback) {
                    ByteBuffer data = frame.getData();
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    content.writeBytes(bytes);
                    callback.succeeded();
                }

                @Override
                public void onFailure(Stream stream, int error, String reason, Throwable failure, Callback callback) {
                    responseBody.completeExceptionally(failure);
                    callback.succeeded();
                }
            }).get(30, TimeUnit.SECONDS);

            MetaData.Request observed = receivedRequest.get(30, TimeUnit.SECONDS);
            assertThat(observed.getMethod()).isEqualTo("GET");
            assertThat(observed.getURI().getPathQuery()).isEqualTo("/frames?mode=raw");
            assertThat(observed.getFields().get("X-Request")).isEqualTo("frames");
            MetaData.Response response = responseHeaders.get(30, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getFields().get("X-Frame-API")).isEqualTo("raw");
            assertThat(responseBody.get(30, TimeUnit.SECONDS)).isEqualTo("response from raw frames");
            assertThat(responseTrailers.get(30, TimeUnit.SECONDS).get("X-Complete")).isEqualTo("yes");

            assertThat(session.close(ErrorCode.NO_ERROR.code, "finished", Callback.NOOP)).isTrue();
            assertThat(serverClosed.get(30, TimeUnit.SECONDS).getError()).isEqualTo(ErrorCode.NO_ERROR.code);
        } finally {
            try {
                client.stop();
            } finally {
                server.stop();
            }
        }
    }
}
