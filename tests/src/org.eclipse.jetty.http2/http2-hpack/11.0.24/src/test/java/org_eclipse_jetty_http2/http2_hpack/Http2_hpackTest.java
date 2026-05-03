/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_http2.http2_hpack;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.hpack.AuthorityHttpField;
import org.eclipse.jetty.http2.hpack.HpackContext;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import org.eclipse.jetty.http2.hpack.HpackException;
import org.eclipse.jetty.http2.hpack.HpackFieldPreEncoder;
import org.eclipse.jetty.http2.hpack.StaticTableHttpField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Http2_hpackTest {
    @Test
    void encodesAndDecodesRequestMetadataWithDynamicTableReuse() throws Exception {
        HpackEncoder encoder = new HpackEncoder();
        HpackDecoder decoder = new HpackDecoder(4096, () -> 12345L);
        MetaData.Request request = newRequest("same-dynamic-value");

        ByteBuffer firstBlock = encode(encoder, request);
        int firstSize = firstBlock.remaining();
        MetaData firstDecoded = decoder.decode(firstBlock);

        assertThat(firstDecoded).isInstanceOf(MetaData.Request.class);
        MetaData.Request decodedRequest = (MetaData.Request) firstDecoded;
        assertThat(decodedRequest.getBeginNanoTime()).isEqualTo(12345L);
        assertThat(decodedRequest.getMethod()).isEqualTo("GET");
        assertThat(decodedRequest.getHttpVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(decodedRequest.getURI().getScheme()).isEqualTo("https");
        assertThat(decodedRequest.getURI().getHost()).isEqualTo("example.com");
        assertThat(decodedRequest.getURI().getPort()).isEqualTo(8443);
        assertThat(decodedRequest.getURI().getPathQuery()).isEqualTo("/items/1?expand=true");
        assertThat(decodedRequest.getFields().get(HttpHeader.ACCEPT)).isEqualTo("application/json");
        assertThat(decodedRequest.getFields().get(HttpHeader.USER_AGENT)).isEqualTo("reachability-hpack-test");
        assertThat(decodedRequest.getFields().get("x-repeatable")).isEqualTo("same-dynamic-value");
        assertThat(decodedRequest.getContentLength()).isEqualTo(7L);

        HttpField dynamicField = new HttpField("x-repeatable", "same-dynamic-value");
        HpackContext.Entry encoderEntry = encoder.getHpackContext().get(dynamicField);
        HpackContext.Entry decoderEntry = decoder.getHpackContext().get(dynamicField);
        assertThat(encoderEntry).isNotNull();
        assertThat(encoderEntry.isStatic()).isFalse();
        assertThat(decoderEntry).isNotNull();
        assertThat(decoderEntry.isStatic()).isFalse();

        ByteBuffer secondBlock = encode(encoder, request);
        assertThat(secondBlock.remaining()).isLessThan(firstSize);
        MetaData secondDecoded = decoder.decode(secondBlock);
        assertThat(secondDecoded.getFields().get("x-repeatable")).isEqualTo("same-dynamic-value");
    }

    @Test
    void encodesAndDecodesExtendedConnectRequestWithProtocolPseudoHeader() throws Exception {
        HpackEncoder encoder = new HpackEncoder();
        HpackDecoder decoder = new HpackDecoder(4096, () -> 67890L);
        HttpFields.Mutable fields = HttpFields.build();
        fields.add("sec-websocket-protocol", "chat");
        fields.add("x-connect-test", "extended-connect");
        MetaData.ConnectRequest request = new MetaData.ConnectRequest(
                HttpScheme.HTTPS,
                new HostPortHttpField("example.com", 8443),
                "/socket?room=blue",
                fields,
                "websocket");

        MetaData decoded = decoder.decode(encode(encoder, request));

        assertThat(decoded).isInstanceOf(MetaData.ConnectRequest.class);
        MetaData.ConnectRequest decodedRequest = (MetaData.ConnectRequest) decoded;
        assertThat(decodedRequest.getBeginNanoTime()).isEqualTo(67890L);
        assertThat(decodedRequest.getMethod()).isEqualTo("CONNECT");
        assertThat(decodedRequest.getProtocol()).isEqualTo("websocket");
        assertThat(decodedRequest.getHttpVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(decodedRequest.getURI().getScheme()).isEqualTo("https");
        assertThat(decodedRequest.getURI().getHost()).isEqualTo("example.com");
        assertThat(decodedRequest.getURI().getPort()).isEqualTo(8443);
        assertThat(decodedRequest.getURI().getPathQuery()).isEqualTo("/socket?room=blue");
        assertThat(decodedRequest.getFields().get("sec-websocket-protocol")).isEqualTo("chat");
        assertThat(decodedRequest.getFields().get("x-connect-test")).isEqualTo("extended-connect");
    }

    @Test
    void encodesAndDecodesResponseMetadataAndPreEncodedFields() throws Exception {
        HpackEncoder encoder = new HpackEncoder();
        HpackFieldPreEncoder preEncoder = new HpackFieldPreEncoder();
        ByteBuffer block = ByteBuffer.allocate(512);

        encoder.encode(block, new MetaData.Response(HttpVersion.HTTP_2, 204, HttpFields.EMPTY));
        byte[] preEncodedContentType = preEncoder.getEncodedField(
                HttpHeader.CONTENT_TYPE,
                HttpHeader.CONTENT_TYPE.asString(),
                "text/plain;charset=utf-8");
        byte[] preEncodedServer = preEncoder.getEncodedField(
                HttpHeader.SERVER,
                HttpHeader.SERVER.asString(),
                "jetty-test");
        block.put(preEncodedContentType);
        block.put(preEncodedServer);
        block.flip();

        HpackDecoder decoder = new HpackDecoder(4096, System::nanoTime);
        MetaData decoded = decoder.decode(block);

        assertThat(preEncoder.getHttpVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(preEncodedContentType).isNotEmpty();
        assertThat(preEncodedServer).isNotEmpty();
        assertThat(decoded).isInstanceOf(MetaData.Response.class);
        MetaData.Response response = (MetaData.Response) decoded;
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHttpVersion()).isEqualTo(HttpVersion.HTTP_2);
        assertThat(response.getFields().get(HttpHeader.CONTENT_TYPE)).isEqualTo("text/plain;charset=utf-8");
        assertThat(response.getFields().get(HttpHeader.SERVER)).isEqualTo("jetty-test");
    }

    @Test
    void encodesIndividualFieldsAndKeepsNeverIndexedHeadersOutOfDynamicTable() throws Exception {
        HpackEncoder encoder = new HpackEncoder();
        HpackDecoder decoder = new HpackDecoder(4096, () -> 24680L);
        HttpField contentType = new HttpField(HttpHeader.CONTENT_TYPE, "text/plain");
        HttpField setCookie = new HttpField(HttpHeader.SET_COOKIE, "session=secret");
        ByteBuffer block = ByteBuffer.allocate(512);

        encoder.encode(block, new HttpField(HttpHeader.C_STATUS, "200"));
        encoder.encode(block, contentType);
        encoder.encode(block, setCookie);
        block.flip();
        MetaData decoded = decoder.decode(block);

        assertThat(decoded).isInstanceOf(MetaData.Response.class);
        MetaData.Response response = (MetaData.Response) decoded;
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getFields().get(HttpHeader.CONTENT_TYPE)).isEqualTo("text/plain");
        assertThat(response.getFields().get(HttpHeader.SET_COOKIE)).isEqualTo("session=secret");
        assertThat(encoder.getHpackContext().get(contentType)).isNotNull();
        assertThat(decoder.getHpackContext().get(contentType)).isNotNull();
        assertThat(encoder.getHpackContext().get(setCookie)).isNull();
        assertThat(decoder.getHpackContext().get(setCookie)).isNull();
    }

    @Test
    void exposesStaticAndDynamicHpackContextEntries() {
        HpackEncoder encoder = new HpackEncoder();
        HpackContext context = encoder.getHpackContext();

        HpackContext.Entry methodEntry = context.get(HttpHeader.C_METHOD);
        HpackContext.Entry pathEntry = HpackContext.getStatic(HttpHeader.C_PATH);
        assertThat(methodEntry).isNotNull();
        assertThat(methodEntry.isStatic()).isTrue();
        assertThat(methodEntry.getHttpField().getName()).isEqualTo(":method");
        assertThat(pathEntry).isNotNull();
        assertThat(HpackContext.staticIndex(HttpHeader.C_PATH)).isGreaterThan(0);
        assertThat(context.index(pathEntry)).isEqualTo(HpackContext.staticIndex(HttpHeader.C_PATH));

        HttpField customField = new HttpField("x-context", "context-value");
        HpackContext.Entry dynamicEntry = context.add(customField);
        assertThat(dynamicEntry).isNotNull();
        assertThat(dynamicEntry.isStatic()).isFalse();
        assertThat(dynamicEntry.getHttpField()).isEqualTo(customField);
        assertThat(dynamicEntry.getSize()).isEqualTo(32 + "x-context".length() + "context-value".length());
        assertThat(context.get(customField)).isSameAs(dynamicEntry);
        assertThat(context.get("x-context")).isSameAs(dynamicEntry);
        assertThat(context.get(context.index(dynamicEntry))).isSameAs(dynamicEntry);
        assertThat(context.size()).isEqualTo(1);
        assertThat(context.getDynamicTableSize()).isEqualTo(dynamicEntry.getSize());

        context.resize(0);
        assertThat(context.getMaxDynamicTableSize()).isZero();
        assertThat(context.size()).isZero();
        assertThat(context.getDynamicTableSize()).isZero();
        assertThat(context.get(customField)).isNull();
    }

    @Test
    void validatesTableCapacityConfigurationAndDynamicTableSizeUpdates() throws Exception {
        HpackEncoder encoder = new HpackEncoder();
        assertThat(encoder.isValidateEncoding()).isTrue();

        encoder.setValidateEncoding(false);
        encoder.setLocalMaxDynamicTableSize(128);
        encoder.setRemoteMaxDynamicTableSize(64);
        assertThat(encoder.isValidateEncoding()).isFalse();
        assertThat(encoder.getMaxTableCapacity()).isEqualTo(128);
        assertThat(encoder.getTableCapacity()).isEqualTo(64);
        assertThatThrownBy(() -> encoder.setTableCapacity(129))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max table capacity");

        ByteBuffer dynamicTableSizeUpdate = ByteBuffer.allocate(16);
        encoder.encodeMaxDynamicTableSize(dynamicTableSizeUpdate, 32);
        dynamicTableSizeUpdate.flip();
        assertThat(dynamicTableSizeUpdate.remaining()).isGreaterThan(0);

        HpackDecoder decoder = new HpackDecoder(4096, System::nanoTime);
        assertThat(decoder.getMaxTableCapacity()).isEqualTo(HpackContext.DEFAULT_MAX_TABLE_CAPACITY);
        decoder.setLocalMaxDynamicTableSize(128);
        assertThat(decoder.getMaxTableCapacity()).isEqualTo(128);
        decoder.getHpackContext().resize(32);
        assertThat(decoder.getHpackContext().getMaxDynamicTableSize()).isEqualTo(32);
    }

    @Test
    void representsAuthorityAndStaticTableFields() {
        AuthorityHttpField authority = new AuthorityHttpField("example.org:9443");
        assertThat(AuthorityHttpField.AUTHORITY).isEqualTo(":authority");
        assertThat(authority.getHeader()).isEqualTo(HttpHeader.C_AUTHORITY);
        assertThat(authority.getName()).isEqualTo(":authority");
        assertThat(authority.getValue()).isEqualTo("example.org:9443");
        assertThat(authority.getHost()).isEqualTo("example.org");
        assertThat(authority.getPort()).isEqualTo(9443);
        assertThat(authority.toString()).contains(":authority", "example.org:9443");

        StaticTableHttpField status = new StaticTableHttpField(HttpHeader.C_STATUS, ":status", "204", 204);
        assertThat(status.getHeader()).isEqualTo(HttpHeader.C_STATUS);
        assertThat(status.getName()).isEqualTo(":status");
        assertThat(status.getValue()).isEqualTo("204");
        assertThat(status.getStaticValue()).isEqualTo(204);
        assertThat(status.getIntValue()).isEqualTo(204);
        assertThat(status.toString()).contains(":status", "204");
    }

    @Test
    void rejectsInvalidOrOversizedHeaderBlocks() throws Exception {
        HpackDecoder invalidIndexDecoder = new HpackDecoder(4096, System::nanoTime);
        assertThatThrownBy(() -> invalidIndexDecoder.decode(ByteBuffer.wrap(new byte[] {(byte) 0x80})))
                .isInstanceOf(HpackException.class);

        HpackEncoder encoder = new HpackEncoder();
        ByteBuffer encoded = encode(encoder, newRequest("value-that-will-exceed-a-small-limit"));
        HpackDecoder sizeLimitedDecoder = new HpackDecoder(8, System::nanoTime);
        assertThat(sizeLimitedDecoder.getMaxHeaderListSize()).isEqualTo(8);
        assertThatThrownBy(() -> sizeLimitedDecoder.decode(encoded))
                .isInstanceOf(HpackException.class)
                .hasMessageContaining("Header");
    }

    @Test
    void decodesIso88591StringFromByteBuffer() {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[] {'J', 'e', 't', 't', 'y', '-', (byte) 0xE9});

        assertThat(HpackDecoder.toISO88591String(bytes, bytes.remaining())).isEqualTo("Jetty-?");
        assertThat(bytes.position()).isEqualTo(bytes.limit());
    }

    private static MetaData.Request newRequest(String repeatableValue) {
        HttpFields.Mutable fields = HttpFields.build();
        fields.add(HttpHeader.ACCEPT, "application/json");
        fields.add(HttpHeader.USER_AGENT, "reachability-hpack-test");
        fields.add("x-repeatable", repeatableValue);
        fields.putLongField(HttpHeader.CONTENT_LENGTH, 7L);

        return new MetaData.Request(
                "GET",
                HttpURI.from("https://example.com:8443/items/1?expand=true"),
                HttpVersion.HTTP_2,
                fields);
    }

    private static ByteBuffer encode(HpackEncoder encoder, MetaData metadata) throws HpackException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        encoder.encode(buffer, metadata);
        buffer.flip();
        return buffer;
    }
}
