/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5_h2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2PseudoRequestHeaders;
import org.apache.hc.core5.http2.H2PseudoResponseHeaders;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.http2.hpack.HPackDecoder;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.http2.hpack.HeaderListConstraintException;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;
import org.apache.hc.core5.http2.impl.H2Processors;
import org.apache.hc.core5.http2.nio.command.PingCommand;
import org.apache.hc.core5.http2.nio.support.BasicPingHandler;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Httpcore5_h2Test {
    @Test
    void configSettingsAndStreamIdGeneratorsExposeHttp2DefaultsAndValidation() {
        H2Config config = H2Config.custom()
                .setHeaderTableSize(128)
                .setPushEnabled(false)
                .setMaxConcurrentStreams(7)
                .setInitialWindowSize(1024)
                .setMaxFrameSize(32768)
                .setMaxHeaderListSize(8192)
                .setCompressionEnabled(false)
                .build();

        assertThat(config.getHeaderTableSize()).isEqualTo(128);
        assertThat(config.isPushEnabled()).isFalse();
        assertThat(config.getMaxConcurrentStreams()).isEqualTo(7);
        assertThat(config.getInitialWindowSize()).isEqualTo(1024);
        assertThat(config.getMaxFrameSize()).isEqualTo(32768);
        assertThat(config.getMaxHeaderListSize()).isEqualTo(8192);
        assertThat(config.isCompressionEnabled()).isFalse();
        assertThat(config.toString()).contains("headerTableSize=128", "compressionEnabled=false");

        H2Config copied = H2Config.copy(config).setPushEnabled(true).build();
        assertThat(copied.getHeaderTableSize()).isEqualTo(config.getHeaderTableSize());
        assertThat(copied.isPushEnabled()).isTrue();
        assertThat(H2Config.INIT.getMaxConcurrentStreams()).isEqualTo(Integer.MAX_VALUE);
        assertThat(H2Config.DEFAULT.isCompressionEnabled()).isTrue();

        H2Setting setting = new H2Setting(H2Param.MAX_FRAME_SIZE, config.getMaxFrameSize());
        assertThat(setting.getCode()).isEqualTo(H2Param.MAX_FRAME_SIZE.getCode());
        assertThat(setting.getValue()).isEqualTo(32768);
        assertThat(setting.toString()).contains("MAX_FRAME_SIZE", "32768");
        assertThat(H2Param.valueOf(setting.getCode())).isEqualTo(H2Param.MAX_FRAME_SIZE);
        assertThat(H2Param.toString(setting.getCode())).isEqualTo("MAX_FRAME_SIZE");

        assertThat(StreamIdGenerator.ODD.generate(0)).isEqualTo(1);
        assertThat(StreamIdGenerator.ODD.generate(1)).isEqualTo(3);
        assertThat(StreamIdGenerator.ODD.isSameSide(5)).isTrue();
        assertThat(StreamIdGenerator.EVEN.generate(0)).isEqualTo(2);
        assertThat(StreamIdGenerator.EVEN.generate(2)).isEqualTo(4);
        assertThat(StreamIdGenerator.EVEN.isSameSide(6)).isTrue();
    }

    @Test
    void frameFactoryCreatesSettingsPingResetGoAwayAndWindowUpdateFrames() {
        FrameFactory frameFactory = DefaultFrameFactory.INSTANCE;

        RawFrame settings = frameFactory.createSettings(
                new H2Setting(H2Param.HEADER_TABLE_SIZE, 4096),
                new H2Setting(H2Param.INITIAL_WINDOW_SIZE, 65535));
        assertThat(settings.isType(FrameType.SETTINGS)).isTrue();
        assertThat(settings.getStreamId()).isZero();
        assertThat(settings.getLength()).isEqualTo(12);
        ByteBuffer settingsPayload = settings.getPayload();
        assertThat(settingsPayload.getShort()).isEqualTo((short) H2Param.HEADER_TABLE_SIZE.getCode());
        assertThat(settingsPayload.getInt()).isEqualTo(4096);
        assertThat(settingsPayload.getShort()).isEqualTo((short) H2Param.INITIAL_WINDOW_SIZE.getCode());
        assertThat(settingsPayload.getInt()).isEqualTo(65535);

        RawFrame settingsAck = frameFactory.createSettingsAck();
        assertThat(settingsAck.isType(FrameType.SETTINGS)).isTrue();
        assertThat(settingsAck.isFlagSet(FrameFlag.ACK)).isTrue();
        assertThat(settingsAck.getPayload()).isNull();

        ByteBuffer opaque = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        RawFrame ping = frameFactory.createPing(opaque);
        assertThat(ping.isType(FrameType.PING)).isTrue();
        assertThat(ping.isFlagSet(FrameFlag.ACK)).isFalse();
        assertThat(ping.getPayload().remaining()).isEqualTo(8);
        RawFrame pingAck = frameFactory.createPingAck(ByteBuffer.wrap(new byte[] {7, 6, 5, 4, 3, 2, 1, 0}));
        assertThat(pingAck.isFlagSet(FrameFlag.ACK)).isTrue();

        RawFrame reset = frameFactory.createResetStream(3, H2Error.CANCEL);
        assertThat(reset.isType(FrameType.RST_STREAM)).isTrue();
        assertThat(reset.getStreamId()).isEqualTo(3);
        assertThat(reset.getPayload().getInt()).isEqualTo(H2Error.CANCEL.getCode());
        assertThat(H2Error.getByCode(H2Error.CANCEL.getCode())).isEqualTo(H2Error.CANCEL);

        RawFrame goAway = frameFactory.createGoAway(5, H2Error.PROTOCOL_ERROR, "debug");
        ByteBuffer goAwayPayload = goAway.getPayload();
        assertThat(goAway.isType(FrameType.GOAWAY)).isTrue();
        assertThat(goAwayPayload.getInt()).isEqualTo(5);
        assertThat(goAwayPayload.getInt()).isEqualTo(H2Error.PROTOCOL_ERROR.getCode());
        byte[] debugData = new byte[goAwayPayload.remaining()];
        goAwayPayload.get(debugData);
        assertThat(new String(debugData, StandardCharsets.US_ASCII)).isEqualTo("debug");

        RawFrame windowUpdate = frameFactory.createWindowUpdate(0, 1024);
        assertThat(windowUpdate.isType(FrameType.WINDOW_UPDATE)).isTrue();
        assertThat(windowUpdate.getPayload().getInt()).isEqualTo(1024);
    }

    @Test
    void dataHeaderContinuationPushAndPaddedFramesExposeFlagsAndPayloadViews() {
        FrameFactory frameFactory = DefaultFrameFactory.INSTANCE;
        ByteBuffer dataPayload = ByteBuffer.wrap("hello".getBytes(StandardCharsets.US_ASCII));

        RawFrame data = frameFactory.createData(1, dataPayload, true);
        assertThat(data.isType(FrameType.DATA)).isTrue();
        assertThat(data.isFlagSet(FrameFlag.END_STREAM)).isTrue();
        assertThat(data.getLength()).isEqualTo(5);
        ByteBuffer firstView = data.getPayload();
        firstView.get();
        assertThat(data.getPayload().position()).isZero();

        RawFrame headers = frameFactory.createHeaders(1, ByteBuffer.wrap(new byte[] {1, 2}), true, true);
        assertThat(headers.isType(FrameType.HEADERS)).isTrue();
        assertThat(headers.isFlagSet(FrameFlag.END_HEADERS)).isTrue();
        assertThat(headers.isFlagSet(FrameFlag.END_STREAM)).isTrue();

        RawFrame continuation = frameFactory.createContinuation(1, ByteBuffer.wrap(new byte[] {3}), true);
        assertThat(continuation.isType(FrameType.CONTINUATION)).isTrue();
        assertThat(continuation.isFlagSet(FrameFlag.END_HEADERS)).isTrue();

        RawFrame pushPromise = frameFactory.createPushPromise(1, ByteBuffer.wrap(new byte[] {0, 0, 0, 2}), false);
        assertThat(pushPromise.isType(FrameType.PUSH_PROMISE)).isTrue();
        assertThat(pushPromise.isFlagSet(FrameFlag.END_HEADERS)).isFalse();

        RawFrame padded = new RawFrame(
                FrameType.DATA.getValue(),
                FrameFlag.of(FrameFlag.PADDED),
                3,
                ByteBuffer.wrap(new byte[] {2, 'a', 'b', 0, 0}));
        ByteBuffer payloadContent = padded.getPayloadContent();
        assertThat(padded.isPadded()).isTrue();
        assertThat(payloadContent.remaining()).isEqualTo(2);
        assertThat((char) payloadContent.get()).isEqualTo('a');
        assertThat((char) payloadContent.get()).isEqualTo('b');

        RawFrame invalidPadding = new RawFrame(
                FrameType.DATA.getValue(),
                FrameFlag.of(FrameFlag.PADDED),
                3,
                ByteBuffer.wrap(new byte[] {5, 'x'}));
        assertThat(invalidPadding.getPayloadContent()).isNull();
    }

    @Test
    void hpackEncoderAndDecoderRoundTripPseudoRegularAndSensitiveHeaders() throws Exception {
        List<Header> headers = Arrays.asList(
                new BasicHeader(H2PseudoRequestHeaders.METHOD, "GET"),
                new BasicHeader(H2PseudoRequestHeaders.SCHEME, "https"),
                new BasicHeader(H2PseudoRequestHeaders.AUTHORITY, "example.test"),
                new BasicHeader(H2PseudoRequestHeaders.PATH, "/resource"),
                new BasicHeader("accept", "text/plain"),
                new BasicHeader("x-unicode", "h\u00e4llo"),
                new BasicHeader("authorization", "secret", true));
        HPackEncoder encoder = new HPackEncoder(512, StandardCharsets.UTF_8);
        HPackDecoder decoder = new HPackDecoder(512, StandardCharsets.UTF_8);
        encoder.setMaxTableSize(512);
        decoder.setMaxTableSize(512);

        ByteArrayBuffer encoded = new ByteArrayBuffer(128);
        encoder.encodeHeaders(encoded, headers, true);
        List<Header> decoded = decoder.decodeHeaders(ByteBuffer.wrap(encoded.array(), 0, encoded.length()));

        assertThat(decoded).hasSize(headers.size());
        assertThat(decoded).extracting(Header::getName).containsExactly(
                H2PseudoRequestHeaders.METHOD,
                H2PseudoRequestHeaders.SCHEME,
                H2PseudoRequestHeaders.AUTHORITY,
                H2PseudoRequestHeaders.PATH,
                "accept",
                "x-unicode",
                "authorization");
        assertThat(decoded).extracting(Header::getValue).containsExactly(
                "GET",
                "https",
                "example.test",
                "/resource",
                "text/plain",
                "h\u00e4llo",
                "secret");
        assertThat(decoded.get(6).isSensitive()).isTrue();

        HPackDecoder constrainedDecoder = new HPackDecoder(512, StandardCharsets.UTF_8);
        constrainedDecoder.setMaxListSize(1);
        assertThatThrownBy(() -> constrainedDecoder.decodeHeaders(
                ByteBuffer.wrap(encoded.array(), 0, encoded.length())))
                .isInstanceOf(HeaderListConstraintException.class)
                .hasMessageContaining("Maximum header list size exceeded");
    }

    @Test
    void basicPingHandlerReportsSuccessfulFailedAndCancelledPings() throws Exception {
        List<Boolean> results = new ArrayList<>();
        BasicPingHandler successfulHandler = new BasicPingHandler(results::add);
        ByteBuffer expectedPayload = successfulHandler.getData();

        assertThat(expectedPayload.remaining()).isEqualTo(8);
        successfulHandler.consumeResponse(expectedPayload);
        assertThat(results).containsExactly(Boolean.TRUE);
        assertThat(successfulHandler.getData().position()).isZero();

        BasicPingHandler failedHandler = new BasicPingHandler(results::add);
        failedHandler.consumeResponse(ByteBuffer.wrap(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}));
        failedHandler.failed(new RuntimeException("ping failed"));
        assertThat(results).containsExactly(Boolean.TRUE, Boolean.FALSE, Boolean.FALSE);

        BasicPingHandler cancelledHandler = new BasicPingHandler(results::add);
        PingCommand pingCommand = new PingCommand(cancelledHandler);
        assertThat(pingCommand.getHandler()).isSameAs(cancelledHandler);
        assertThat(pingCommand.cancel()).isTrue();
        assertThat(results).containsExactly(Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
    }

    @Test
    void requestConverterRoundTripsRegularAndConnectRequestsAndRejectsInvalidHeaders() throws Exception {
        DefaultH2RequestConverter converter = DefaultH2RequestConverter.INSTANCE;
        BasicHttpRequest request = new BasicHttpRequest(Method.GET, "/api?q=1");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.test", 8443));
        request.addHeader("Accept", "application/json");
        request.addHeader("TE", "trailers");

        List<Header> h2Headers = converter.convert(request);
        assertThat(h2Headers).extracting(Header::getName).containsExactly(
                H2PseudoRequestHeaders.METHOD,
                H2PseudoRequestHeaders.SCHEME,
                H2PseudoRequestHeaders.AUTHORITY,
                H2PseudoRequestHeaders.PATH,
                "accept",
                "te");
        assertThat(h2Headers).extracting(Header::getValue).containsExactly(
                "GET",
                "https",
                "example.test:8443",
                "/api?q=1",
                "application/json",
                "trailers");

        HttpRequest converted = converter.convert(h2Headers);
        assertThat(converted.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(converted.getMethod()).isEqualTo("GET");
        assertThat(converted.getScheme()).isEqualTo("https");
        assertThat(converted.getPath()).isEqualTo("/api?q=1");
        assertThat(converted.getAuthority().getHostName()).isEqualTo("example.test");
        assertThat(converted.getAuthority().getPort()).isEqualTo(8443);
        assertThat(converted.getFirstHeader("accept").getValue()).isEqualTo("application/json");

        BasicHttpRequest connect = new BasicHttpRequest(Method.CONNECT, (String) null);
        connect.setAuthority(new URIAuthority("proxy.example", 443));
        List<Header> connectHeaders = converter.convert(connect);
        assertThat(connectHeaders).extracting(Header::getName).containsExactly(
                H2PseudoRequestHeaders.METHOD,
                H2PseudoRequestHeaders.AUTHORITY);
        assertThat(converter.convert(connectHeaders).getAuthority().toString()).isEqualTo("proxy.example:443");

        assertThatThrownBy(() -> converter.convert(Arrays.<Header>asList(
                new BasicHeader(H2PseudoRequestHeaders.METHOD, "GET"),
                new BasicHeader(H2PseudoRequestHeaders.SCHEME, "https"),
                new BasicHeader(H2PseudoRequestHeaders.PATH, "/"),
                new BasicHeader("Connection", "close"))))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("contains uppercase characters");
        BasicHttpRequest invalidPseudoHeader = new BasicHttpRequest(Method.GET, "/");
        invalidPseudoHeader.setScheme("https");
        invalidPseudoHeader.addHeader(":path", "/other");
        assertThatThrownBy(() -> converter.convert(invalidPseudoHeader))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("Header name ':path' is invalid");
    }

    @Test
    void h2ProcessorsAddEntityMetadataAndProtocolHeadersWithoutHttp1ConnectionHeaders() throws Exception {
        HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_2);

        StringEntity requestEntity = new StringEntity("", ContentType.APPLICATION_JSON, "br", false);
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/submit");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.test"));

        HttpProcessor clientProcessor = H2Processors.client("test-agent");
        clientProcessor.process(request, requestEntity, context);

        assertThat(request.getFirstHeader("content-type").getValue())
                .isEqualTo(ContentType.APPLICATION_JSON.toString());
        assertThat(request.getFirstHeader("content-encoding").getValue()).isEqualTo("br");
        assertThat(request.getFirstHeader("user-agent").getValue()).isEqualTo("test-agent");
        assertThat(request.containsHeader("host")).isFalse();
        assertThat(request.containsHeader("connection")).isFalse();
        assertThat(request.containsHeader("content-length")).isFalse();
        assertThat(request.containsHeader("transfer-encoding")).isFalse();
        assertThat(request.containsHeader("expect")).isFalse();

        StringEntity responseEntity = new StringEntity("", ContentType.TEXT_PLAIN, "gzip", false);
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        HttpProcessor serverProcessor = H2Processors.server("test-server");
        serverProcessor.process(response, responseEntity, context);

        assertThat(response.getFirstHeader("content-type").getValue())
                .isEqualTo(ContentType.TEXT_PLAIN.toString());
        assertThat(response.getFirstHeader("content-encoding").getValue()).isEqualTo("gzip");
        assertThat(response.getFirstHeader("server").getValue()).isEqualTo("test-server");
        assertThat(response.containsHeader("date")).isTrue();
        assertThat(response.containsHeader("connection")).isFalse();
        assertThat(response.containsHeader("content-length")).isFalse();
        assertThat(response.containsHeader("transfer-encoding")).isFalse();
    }

    @Test
    void responseConverterRoundTripsSuccessfulResponsesAndRejectsInvalidResponses() throws Exception {
        DefaultH2ResponseConverter converter = DefaultH2ResponseConverter.INSTANCE;
        BasicHttpResponse response = new BasicHttpResponse(204);
        response.addHeader("Content-Type", "text/plain");
        response.addHeader("X-Trace", "abc123");

        List<Header> h2Headers = converter.convert(response);
        assertThat(h2Headers).extracting(Header::getName).containsExactly(
                H2PseudoResponseHeaders.STATUS,
                "content-type",
                "x-trace");
        assertThat(h2Headers).extracting(Header::getValue).containsExactly("204", "text/plain", "abc123");

        HttpResponse converted = converter.convert(h2Headers);
        assertThat(converted.getVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(converted.getCode()).isEqualTo(204);
        assertThat(converted.getFirstHeader("content-type").getValue()).isEqualTo("text/plain");

        assertThatThrownBy(() -> converter.convert(new BasicHttpResponse(99)))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("invalid");
        assertThatThrownBy(() -> converter.convert(Arrays.<Header>asList(
                new BasicHeader("content-type", "text/plain"),
                new BasicHeader(H2PseudoResponseHeaders.STATUS, "200"))))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("pseudo-headers must precede message headers");
        assertThatThrownBy(() -> converter.convert(Arrays.<Header>asList(
                new BasicHeader(H2PseudoResponseHeaders.STATUS, "ok"))))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("Invalid response status");
    }
}
