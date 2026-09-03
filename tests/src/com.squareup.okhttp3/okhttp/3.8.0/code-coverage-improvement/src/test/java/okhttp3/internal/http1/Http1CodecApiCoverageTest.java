/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.http1;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;
import org.junit.jupiter.api.Test;

public class Http1CodecApiCoverageTest {
    @Test
    void writesRequestsAndBodiesUsingHttp1Framing() throws Exception {
        Buffer output = new Buffer();
        Http1Codec codec = new Http1Codec(new OkHttpClient(), null, new Buffer(), output);
        Request request = new Request.Builder().url("http://example.com/upload").build();
        codec.writeRequest(okhttp3.Headers.of("X-Test", "yes"), "POST /upload HTTP/1.1");
        codec.flushRequest();
        assertThat(output.readUtf8()).contains("POST /upload HTTP/1.1", "X-Test: yes");

        codec.finishRequest();
        assertThat(codec.isClosed()).isFalse();
    }

    @Test
    void readsHttp1HeadersAndResponseBodies() throws Exception {
        Http1Codec headersCodec = new Http1Codec(new OkHttpClient(), null,
                new Buffer().writeUtf8("A: b\r\nC: d\r\n\r\n"), new Buffer());
        assertThat(headersCodec.readHeaders().get("A")).isEqualTo("b");

    }

    @Test
    void streamsChunkedAndUnknownLengthBodies() throws Exception {
        Buffer chunkedInput = new Buffer().writeUtf8(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                        + "4\r\ntest\r\n0\r\n\r\n");
        Buffer chunkedOutput = new Buffer();
        Http1Codec chunkedCodec = new Http1Codec(new OkHttpClient(), null, chunkedInput,
                chunkedOutput);
        Request request = new Request.Builder().url("http://example.com/upload").build();
        chunkedCodec.writeRequest(okhttp3.Headers.of("Transfer-Encoding", "chunked"),
                "POST /upload HTTP/1.1");
        Sink requestSink = chunkedCodec.newChunkedSink();
        Buffer requestData = new Buffer().writeUtf8("payload");
        requestSink.write(requestData, requestData.size());
        requestSink.close();
        assertThat(chunkedOutput.readUtf8()).contains("7\r\npayload\r\n0\r\n\r\n");

        Response response = chunkedCodec.readResponseHeaders(false).request(request).build();
        Source responseSource = chunkedCodec.newChunkedSource(request.url());
        Buffer responseData = new Buffer();
        assertThat(responseSource.read(responseData, 4L)).isEqualTo(4L);
        assertThat(responseData.readUtf8()).isEqualTo("test");
        assertThat(responseSource.read(responseData, 4L)).isEqualTo(-1L);
        responseSource.close();
        assertThat(response.code()).isEqualTo(200);

        Buffer unknownInput = new Buffer().writeUtf8("HTTP/1.1 200 OK\r\n\r\nunknown");
        okhttp3.ConnectionPool unknownPool = new okhttp3.ConnectionPool();
        okhttp3.internal.connection.StreamAllocation unknownAllocation =
                new okhttp3.internal.connection.StreamAllocation(unknownPool,
                        new okhttp3.Address("example.com", 80, okhttp3.Dns.SYSTEM,
                                javax.net.SocketFactory.getDefault(), null, null,
                                okhttp3.CertificatePinner.DEFAULT, okhttp3.Authenticator.NONE, null,
                                java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1),
                                java.util.Collections.singletonList(okhttp3.ConnectionSpec.CLEARTEXT),
                                java.net.ProxySelector.getDefault()), "unknown");
        okhttp3.internal.http1.Http1Codec unknownCodec = new Http1Codec(new OkHttpClient(),
                unknownAllocation, unknownInput, new Buffer());
        java.lang.reflect.Field codecField =
                okhttp3.internal.connection.StreamAllocation.class.getDeclaredField("codec");
        codecField.setAccessible(true);
        codecField.set(unknownAllocation, unknownCodec);
        okhttp3.Route unknownRoute = new okhttp3.Route(
                unknownAllocation.address, java.net.Proxy.NO_PROXY,
                new java.net.InetSocketAddress("localhost", 80));
        okhttp3.internal.connection.RealConnection unknownConnection =
                okhttp3.internal.connection.RealConnection.testConnection(unknownPool,
                        unknownRoute, new java.net.Socket(), 0L);
        synchronized (unknownPool) {
            unknownAllocation.acquire(unknownConnection);
        }
        unknownCodec.writeRequest(okhttp3.Headers.of(), "GET / HTTP/1.1");
        Sink emptyRequest = unknownCodec.newFixedLengthSink(0L);
        emptyRequest.close();
        unknownCodec.readResponseHeaders(false);
        Source unknownSource = unknownCodec.newUnknownLengthSource();
        Buffer unknownData = new Buffer();
        assertThat(unknownSource.read(unknownData, 7L)).isEqualTo(7L);
        assertThat(unknownData.readUtf8()).isEqualTo("unknown");
        assertThat(unknownSource.read(unknownData, 1L)).isEqualTo(-1L);
        unknownSource.close();
    }

    @Test
    void cancellationDelegatesToTheCurrentAllocation() {
        okhttp3.internal.connection.StreamAllocation allocation =
                new okhttp3.internal.connection.StreamAllocation(new okhttp3.ConnectionPool(),
                        new okhttp3.Address("example.com", 80, okhttp3.Dns.SYSTEM,
                                javax.net.SocketFactory.getDefault(), null, null,
                                okhttp3.CertificatePinner.DEFAULT, okhttp3.Authenticator.NONE, null,
                                java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1),
                                java.util.Collections.singletonList(okhttp3.ConnectionSpec.CLEARTEXT),
                                java.net.ProxySelector.getDefault()), "cancel");
        Http1Codec codec = new Http1Codec(new OkHttpClient(), allocation, new Buffer(), new Buffer());
        codec.cancel();
        assertThat(allocation.connection()).isNull();
    }

    @Test
    void clientRoundTripExercisesResponseParsingAndStreaming() throws Exception {
        java.net.ServerSocket server = new java.net.ServerSocket(0);
        Thread responder = new Thread(() -> {
            try (java.net.Socket socket = server.accept()) {
                okio.BufferedSource source = Okio.buffer(Okio.source(socket));
                source.readUtf8Line();
                String line;
                while ((line = source.readUtf8Line()) != null && !line.isEmpty()) {
                    assertThat(line).isNotEmpty();
                }
                okio.BufferedSink sink = Okio.buffer(Okio.sink(socket));
                sink.writeUtf8("HTTP/1.1 200 OK\r\nContent-Length: 7\r\n\r\nnetwork");
                sink.flush();
            } catch (Exception ignored) {
            }
        });
        responder.start();
        try {
            okhttp3.Response response = new OkHttpClient().newCall(new Request.Builder()
                    .url("http://localhost:" + server.getLocalPort() + "/")
                    .post(okhttp3.RequestBody.create(null, "request"))
                    .build()).execute();
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("network");
            response.close();
        } finally {
            server.close();
            responder.join(1000L);
        }
    }
}
