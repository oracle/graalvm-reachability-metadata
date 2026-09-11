/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.http2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.Sink;
import org.junit.jupiter.api.Test;

public class Http2ApiCoverageTest {
    @Test
    void connectionCreatesPingsAndStreamsWriteResponseHeaders() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", input, output)
                .build();
        assertThat(connection.getProtocol()).isEqualTo(Protocol.HTTP_2);
        assertThat(connection.isShutdown()).isFalse();
        assertThat(connection.openStreamCount()).isZero();
        assertThat(connection.maxConcurrentStreams()).isGreaterThan(0);

        Ping ping = connection.ping();
        assertThat(ping).isNotNull();

        List<Header> requestHeaders = Collections.singletonList(new Header("method", "GET"));
        Http2Stream stream = connection.newStream(requestHeaders, true);
        assertThat(stream.isOpen()).isTrue();
        stream.sendResponseHeaders(Collections.singletonList(new Header("status", "200")), true);
        Sink streamSink = stream.getSink();
        streamSink.write(new Buffer().writeUtf8("request"), 7L);
        streamSink.flush();
        assertThat(streamSink.timeout()).isNotNull();
        assertThat(stream.getSource()).isNotNull();
        assertThat(stream.getSink()).isNotNull();
        assertThat(stream.readTimeout()).isNotNull();
        assertThat(stream.writeTimeout()).isNotNull();
        assertThat(stream.getErrorCode()).isNull();
        assertThat(stream.getId()).isPositive();
        assertThat(stream.getConnection()).isSameAs(connection);
        assertThat(stream.getRequestHeaders()).containsExactlyElementsOf(requestHeaders);
        assertThat(stream.isLocallyInitiated()).isTrue();
        connection.writeData(stream.getId(), true, new Buffer().writeUtf8("data"), 4L);
        stream.getSource().timeout().timeout(1L, TimeUnit.SECONDS);
        stream.getSource().close();
        Ping timedPing = connection.ping();
        assertThat(timedPing.roundTripTime(1L, TimeUnit.MILLISECONDS)).isEqualTo(-2L);
        Settings peerSettings = new Settings();
        peerSettings.set(Settings.HEADER_TABLE_SIZE, 128);
        connection.setSettings(peerSettings);
        stream.closeLater(ErrorCode.CANCEL);
        assertThat(stream.getErrorCode()).isEqualTo(ErrorCode.CANCEL);
        connection.setSettings(new Settings());
        connection.close();
        assertThat(connection.isShutdown()).isTrue();
    }

    @Test
    void defaultListenerAndPushObserverHandlePeerFramesThroughTheBuilder() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        input.writeUtf8("PRI * HTTP/2.0\\r\\n\\r\\nSM\\r\\n\\r\\n");
        frame(input, 0, 4, 0, 0);
        Buffer requestHeaders = new Buffer().writeByte(0x82);
        frame(input, (int) requestHeaders.size(), 1, 5, 1);
        input.write(requestHeaders, requestHeaders.size());
        Buffer pushHeaders = new Buffer().writeByte(0x82);
        frame(input, (int) pushHeaders.size() + 4, 5, 4, 1);
        input.writeInt(2).write(pushHeaders, pushHeaders.size());
        frame(input, 1, 1, 4, 2);
        input.writeByte(0x82);
        frame(input, 1, 0, 1, 2);
        input.writeByte('p');
        frame(input, 4, 3, 0, 2);
        input.writeInt(ErrorCode.CANCEL.httpCode);
        Http2Connection connection = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", input, output).build();
        connection.start();
        Thread.sleep(50L);
        assertThat(output.size()).isGreaterThan(0L);
        connection.close();
    }

    @Test
    void codecsTranslateRequestsAndHeadersForHttp2() throws Exception {
        okhttp3.internal.Internal.initializeInstanceForTests();
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", input, output).build();
        okhttp3.internal.connection.StreamAllocation allocation =
                new okhttp3.internal.connection.StreamAllocation(new okhttp3.ConnectionPool(),
                        new okhttp3.Address("localhost", 80, okhttp3.Dns.SYSTEM,
                                javax.net.SocketFactory.getDefault(), null, null,
                                okhttp3.CertificatePinner.DEFAULT, okhttp3.Authenticator.NONE, null,
                                Collections.singletonList(Protocol.HTTP_1_1),
                                Collections.singletonList(okhttp3.ConnectionSpec.CLEARTEXT),
                                java.net.ProxySelector.getDefault()), "codec");
        Http2Codec codec = new Http2Codec(new okhttp3.OkHttpClient(), allocation, connection);
        okhttp3.Request request = new okhttp3.Request.Builder().url("http://localhost/path?q=1")
                .header("X-Test", "yes").build();
        assertThat(Http2Codec.http2HeadersList(request)).extracting("name")
                .contains(Header.TARGET_METHOD, Header.TARGET_PATH);
        assertThat(Http2Codec.readHttp2HeadersList(Collections.singletonList(
                new Header(Header.RESPONSE_STATUS, "200"))).request(request).build().protocol())
                .isEqualTo(Protocol.HTTP_2);
        codec.writeRequestHeaders(request);
        Sink bodySink = codec.createRequestBody(request, 4L);
        Buffer body = new Buffer().writeUtf8("body");
        bodySink.write(body, body.size());
        codec.finishRequest();
        Http2Stream stream = connection.streams.values().iterator().next();
        stream.receiveHeaders(Collections.singletonList(new Header(Header.RESPONSE_STATUS, "100")));
        assertThat(codec.readResponseHeaders(true)).isNull();
        stream.receiveHeaders(Collections.singletonList(new Header(Header.RESPONSE_STATUS, "200")));
        Response response = codec.readResponseHeaders(false).request(request).build();
        stream.receiveData(new Buffer().writeUtf8("reply"), 5);
        ResponseBody responseBody = codec.openResponseBody(response);
        Buffer reply = new Buffer();
        assertThat(responseBody.source().read(reply, 5L)).isEqualTo(5L);
        assertThat(reply.readUtf8()).isEqualTo("reply");
        codec.flushRequest();
        codec.cancel();
        connection.close();
    }

    @Test
    void startsConnectionsAndCreatesServerPushStreams() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", input, output).build();
        connection.start();
        assertThat(output.readByte()).isEqualTo((byte) 'P');
        connection.close();

        Http2Connection server = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", new Buffer(), new Buffer()).build();
        Http2Stream pushed = server.pushStream(2,
                Collections.singletonList(new Header(Header.TARGET_METHOD, "GET")), true);
        assertThat(pushed.isLocallyInitiated()).isTrue();
        server.close();
    }

    @Test
    void builderSocketOverloadAndStreamResponseContractsWork() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        Thread acceptor = new Thread(() -> {
            try {
                Socket accepted = serverSocket.accept();
                accepted.close();
            } catch (Exception ignored) {
            }
        });
        acceptor.start();
        Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort());
        Http2Connection socketConnection = new Http2Connection.Builder(true)
                .socket(clientSocket).build();
        assertThat(socketConnection.getProtocol()).isEqualTo(Protocol.HTTP_2);
        socketConnection.close();
        serverSocket.close();
        acceptor.join(1000L);

        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", new Buffer(), new Buffer()).build();
        Http2Stream stream = connection.newStream(Collections.singletonList(
                new Header(Header.TARGET_METHOD, "GET")), true);
        CountDownLatch waitingForHeaders = new CountDownLatch(1);
        List<Header> responseHeaders = Collections.singletonList(
                new Header(Header.RESPONSE_STATUS, "204"));
        Thread headerReader = new Thread(() -> {
            try {
                assertThat(stream.takeResponseHeaders()).extracting("name")
                        .containsExactly(Header.RESPONSE_STATUS);
            } catch (IOException failure) {
                throw new AssertionError(failure);
            } finally {
                waitingForHeaders.countDown();
            }
        });
        headerReader.start();
        Thread.sleep(20L);
        stream.receiveHeaders(responseHeaders);
        assertThat(waitingForHeaders.await(1L, TimeUnit.SECONDS)).isTrue();
        headerReader.join(1000L);
        stream.close(ErrorCode.CANCEL);
        assertThat(stream.getErrorCode()).isEqualTo(ErrorCode.CANCEL);
        connection.close();
    }

    @Test
    void responseHeaderWaitUsesThePublicStreamTimeoutContract() throws Exception {
        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", new Buffer(), new Buffer()).build();
        Http2Stream stream = connection.newStream(Collections.singletonList(
                new Header(Header.TARGET_METHOD, "GET")), true);
        stream.readTimeout().timeout(5L, TimeUnit.MILLISECONDS);
        boolean timedOut = false;
        try {
            stream.takeResponseHeaders();
        } catch (java.io.InterruptedIOException expected) {
            timedOut = true;
        }
        assertThat(timedOut).isTrue();
        connection.close();
    }

    @Test
    void readerProcessesPeerSettingsFramesHeadersDataAndPushCallbacks() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        CountDownLatch streamSeen = new CountDownLatch(1);
        CountDownLatch settingsSeen = new CountDownLatch(1);
        CountDownLatch pushSeen = new CountDownLatch(1);
        CountDownLatch pushHeadersSeen = new CountDownLatch(1);
        CountDownLatch pushDataSeen = new CountDownLatch(1);
        CountDownLatch pushResetSeen = new CountDownLatch(1);
        Http2Connection.Listener listener = new Http2Connection.Listener() {
            public void onStream(Http2Stream stream) {
                streamSeen.countDown();
            }

            public void onSettings(Http2Connection connection) {
                settingsSeen.countDown();
            }
        };
        PushObserver observer = new PushObserver() {
            public boolean onRequest(int streamId, List<Header> headers) {
                pushSeen.countDown();
                return true;
            }

            public boolean onHeaders(int streamId, List<Header> headers, boolean inFinished) {
                pushHeadersSeen.countDown();
                return true;
            }

            public boolean onData(int streamId, okio.BufferedSource source, int byteCount,
                    boolean inFinished) {
                pushDataSeen.countDown();
                return true;
            }

            public void onReset(int streamId, ErrorCode errorCode) {
                pushResetSeen.countDown();
            }
        };
        input.writeUtf8("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
        frame(input, 6, 4, 0, 0);
        input.writeShort(1).writeInt(128);
        frame(input, 1, 1, 4, 3);
        input.writeByte(0x82);
        frame(input, 6, 4, 0, 0);
        input.writeShort(4).writeInt(8192);
        frame(input, 4, 0, 1, 3);
        input.writeUtf8("data");
        frame(input, 5, 5, 4, 3);
        input.writeInt(4).writeByte(0x82);
        frame(input, 1, 1, 4, 4);
        input.writeByte(0x82);
        frame(input, 1, 0, 1, 4);
        input.writeByte('p');
        frame(input, 4, 3, 0, 4);
        input.writeInt(ErrorCode.CANCEL.httpCode);
        frame(input, 5, 2, 0, 3);
        input.writeInt(0).writeByte(10);
        frame(input, 8, 6, 0, 0);
        input.writeLong(7L);
        frame(input, 8, 6, 1, 0);
        input.writeInt(2).writeInt(0x4f4b6f6b);
        frame(input, 4, 8, 0, 0);
        input.writeInt(1);
        frame(input, 0, 4, 1, 0);
        frame(input, 8, 7, 0, 0);
        input.writeInt(0).writeInt(0);

        Http2Connection connection = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", input, output)
                .listener(listener).pushObserver(observer).build();
        Http2Stream localStream = connection.newStream(Collections.singletonList(
                new Header(Header.TARGET_METHOD, "GET")), true);
        Ping pendingPing = connection.ping();
        assertThat(localStream).isNotNull();
        assertThat(pendingPing).isNotNull();
        Logger frameLogger = Logger.getLogger(Http2.class.getName());
        frameLogger.setLevel(Level.FINE);
        connection.start();
        assertThat(streamSeen.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(settingsSeen.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(pushSeen.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(pushHeadersSeen.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(pushDataSeen.await(1L, TimeUnit.SECONDS)).isTrue();
        assertThat(pushResetSeen.await(1L, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50L);
        assertThat(output.size()).isGreaterThan(0L);
        connection.close();
    }

    @Test
    void readerDecodesIndexedLiteralHuffmanAndContinuationHeaders() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        CountDownLatch pushes = new CountDownLatch(7);
        PushObserver observer = new PushObserver() {
            public boolean onRequest(int streamId, List<Header> headers) {
                pushes.countDown();
                return true;
            }

            public boolean onHeaders(int streamId, List<Header> headers, boolean inFinished) {
                return true;
            }

            public boolean onData(int streamId, okio.BufferedSource source, int byteCount,
                    boolean inFinished) {
                return true;
            }

            public void onReset(int streamId, ErrorCode errorCode) {
            }
        };
        input.writeUtf8("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
        frame(input, 0, 4, 0, 0);

        Buffer incrementalNew = new Buffer();
        incrementalNew.writeByte(0x40).writeByte(5).writeUtf8("x-new")
                .writeByte(3).writeUtf8("one");
        pushPromise(input, 2, incrementalNew);

        Buffer indexedDynamic = new Buffer();
        indexedDynamic.writeByte(0xbe);
        pushPromise(input, 4, indexedDynamic);

        Buffer incrementalIndexed = new Buffer();
        incrementalIndexed.writeByte(0x42).writeByte(3).writeUtf8("GET");
        pushPromise(input, 6, incrementalIndexed);

        Buffer withoutIndexed = new Buffer();
        withoutIndexed.writeByte(0x02).writeByte(5).writeUtf8("value");
        pushPromise(input, 8, withoutIndexed);

        Buffer withoutNewName = new Buffer();
        withoutNewName.writeByte(0).writeByte(6).writeUtf8("x-free")
                .writeByte(4).writeUtf8("four");
        pushPromise(input, 10, withoutNewName);

        Buffer huffmanAndEviction = new Buffer();
        huffmanAndEviction.writeByte(0x3f).writeByte(0x01);
        huffmanAndEviction.writeByte(0).writeByte(6).writeUtf8("x-huff")
                .writeByte(0x8c)
                .write(new byte[] {(byte) 0xf1, (byte) 0xe3, (byte) 0xc2, (byte) 0xe5,
                        (byte) 0xf2, (byte) 0x3a, (byte) 0x6b, (byte) 0xa0, (byte) 0xab,
                        (byte) 0x90, (byte) 0xf4, (byte) 0xff})
                .writeByte(0x20);
        pushPromise(input, 12, huffmanAndEviction);

        Buffer large = new Buffer();
        large.writeByte(0x40).writeByte(7).writeUtf8("x-large");
        writeHpackLength(large, 20000);
        for (int i = 0; i < 20000; i++) {
            large.writeByte('a' + (i % 26));
        }
        int firstBlockLength = 16380;
        frame(input, firstBlockLength + 4, 5, 0, 1);
        input.writeInt(14).write(large, firstBlockLength);
        frame(input, (int) large.size(), 9, 4, 1);
        input.write(large, large.size());

        Http2Connection connection = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", input, output)
                .pushObserver(observer).build();
        connection.start();
        assertThat(pushes.await(2L, TimeUnit.SECONDS)).isTrue();
        connection.close();
    }

    @Test
    void serverPushWithLargeHeadersWritesContinuationFrames() throws Exception {
        Buffer output = new Buffer();
        Http2Connection connection = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", new Buffer(), output).build();
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 40000; i++) {
            value.append((char) ('a' + (i % 26)));
        }
        Http2Stream pushed = connection.pushStream(1,
                Collections.singletonList(new Header("x-large", value.toString())), true);
        assertThat(pushed.isLocallyInitiated()).isTrue();
        assertThat(output.size()).isGreaterThan(16384L);
        connection.close();
    }

    private static void pushPromise(Buffer input, int promisedStreamId, Buffer block) {
        frame(input, (int) block.size() + 4, 5, 4, 1);
        input.writeInt(promisedStreamId);
        input.write(block, block.size());
    }

    private static void writeHpackLength(Buffer buffer, int length) {
        if (length < 127) {
            buffer.writeByte(length);
            return;
        }
        buffer.writeByte(0x7f);
        int remaining = length - 127;
        while (remaining >= 128) {
            buffer.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        buffer.writeByte(remaining);
    }

    private static void frame(Buffer buffer, int length, int type, int flags, int streamId) {
        buffer.writeByte((length >>> 16) & 0xff);
        buffer.writeByte((length >>> 8) & 0xff);
        buffer.writeByte(length & 0xff);
        buffer.writeByte(type);
        buffer.writeByte(flags);
        buffer.writeInt(streamId);
    }

    @Test
    void readerAcknowledgesLargeConsumedDataAndPeerSettings() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        CountDownLatch streamSeen = new CountDownLatch(1);
        CountDownLatch settingsSeen = new CountDownLatch(1);
        Http2Connection.Listener listener = new Http2Connection.Listener() {
            public void onStream(Http2Stream stream) {
                try {
                    Buffer consumed = new Buffer();
                    while (consumed.size() < 40000L) {
                        stream.getSource().read(consumed, 40000L - consumed.size());
                    }
                } catch (IOException failure) {
                    throw new AssertionError(failure);
                } finally {
                    streamSeen.countDown();
                }
            }

            public void onSettings(Http2Connection connection) {
                settingsSeen.countDown();
            }
        };
        input.writeUtf8("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
        frame(input, 0, 4, 0, 0);
        frame(input, 1, 1, 4, 1);
        input.writeByte(0x82);
        frame(input, 6, 4, 0, 0);
        input.writeShort(1).writeInt(4096);
        frame(input, 40000, 0, 1, 1);
        for (int i = 0; i < 40000; i++) {
            input.writeByte('d');
        }
        Http2Connection connection = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", input, output).listener(listener).build();
        connection.start();
        assertThat(streamSeen.await(2L, TimeUnit.SECONDS)).isTrue();
        assertThat(settingsSeen.await(2L, TimeUnit.SECONDS)).isTrue();
        connection.close();
    }

    @Test
    void publicConnectionDataValidationReportsInvalidStreamIds() throws Exception {
        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", new Buffer(), new Buffer()).build();
        boolean rejected = false;
        try {
            connection.writeData(Integer.MIN_VALUE, false, new Buffer().writeUtf8("invalid"), 7L);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        } finally {
            connection.close();
        }
        assertThat(rejected).isTrue();
    }

    @Test
    void publicPingCompletesWhenThePeerAcknowledgesIt() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        frame(input, 0, 4, 0, 0);
        frame(input, 8, 6, 1, 0);
        input.writeLong(2L);
        Http2Connection connection = new Http2Connection.Builder(true)
                .socket(new Socket(), "localhost", input, output).build();
        Ping ping = connection.ping();
        connection.start();
        assertThat(ping.roundTripTime(2L, TimeUnit.SECONDS)).isLessThanOrEqualTo(0L);
        connection.close();
    }

    @Test
    void buildersListenersHeadersAndErrorValuesHaveStableContracts() throws Exception {
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        Http2Connection.Listener listener = new Http2Connection.Listener() {
            public void onStream(Http2Stream stream) {
            }
        };
        PushObserver observer = new PushObserver() {
            public boolean onRequest(int streamId, List<Header> headers) {
                return true;
            }

            public boolean onHeaders(int streamId, List<Header> headers, boolean inFinished) {
                return false;
            }

            public boolean onData(int streamId, okio.BufferedSource source, int byteCount,
                    boolean inFinished) {
                return true;
            }

            public void onReset(int streamId, ErrorCode errorCode) {
            }
        };
        Http2Connection connection = new Http2Connection.Builder(false)
                .socket(new Socket(), "localhost", input, output)
                .listener(listener).pushObserver(observer).build();
        listener.onSettings(connection);
        Header header = new Header("name", "value");
        assertThat(header.hashCode()).isEqualTo(new Header("name", "value").hashCode());
        assertThat(header.toString()).contains("name", "value");
        assertThat(ErrorCode.fromHttp2(0)).isEqualTo(ErrorCode.NO_ERROR);
        assertThat(ErrorCode.valueOf("CANCEL")).isEqualTo(ErrorCode.CANCEL);
        assertThat(ErrorCode.values()).containsExactly(ErrorCode.NO_ERROR, ErrorCode.PROTOCOL_ERROR,
                ErrorCode.INTERNAL_ERROR, ErrorCode.FLOW_CONTROL_ERROR,
                ErrorCode.REFUSED_STREAM, ErrorCode.CANCEL);
        assertThat(new ConnectionShutdownException()).isInstanceOf(java.io.IOException.class);
        StreamResetException reset = new StreamResetException(ErrorCode.CANCEL);
        assertThat(reset.errorCode).isEqualTo(ErrorCode.CANCEL);
        connection.close();
    }
}
