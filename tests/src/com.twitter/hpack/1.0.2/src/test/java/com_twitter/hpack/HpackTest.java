/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.hpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import com.twitter.hpack.HeaderListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HpackTest {
    @Test
    void decodesBasicHpackRepresentationsFromRfcExamples() throws IOException {
        Decoder decoder = new Decoder(8192, 4096);

        assertThat(decode(decoder, hex("400a637573746f6d2d6b65790d637573746f6d2d686561646572")))
                .containsExactly(header("custom-key", "custom-header", false));
        assertThat(decode(decoder, hex("82"))).containsExactly(header(":method", "GET", false));
        assertThat(decode(decoder, hex("040c2f73616d706c652f70617468")))
                .containsExactly(header(":path", "/sample/path", false));
        assertThat(decode(decoder, hex("100870617373776f726406736563726574")))
                .containsExactly(header("password", "secret", true));
    }

    @Test
    void decodesSequentialRequestHeaderBlocksUsingDynamicTable() throws IOException {
        Decoder decoder = new Decoder(8192, 4096);

        assertThat(decode(decoder, hex("828684410f7777772e6578616d706c652e636f6d")))
                .containsExactly(
                        header(":method", "GET", false),
                        header(":scheme", "http", false),
                        header(":path", "/", false),
                        header(":authority", "www.example.com", false));

        assertThat(decode(decoder, hex("828684be58086e6f2d6361636865")))
                .containsExactly(
                        header(":method", "GET", false),
                        header(":scheme", "http", false),
                        header(":path", "/", false),
                        header(":authority", "www.example.com", false),
                        header("cache-control", "no-cache", false));

        assertThat(decode(decoder, hex("828785bf400a637573746f6d2d6b65790c637573746f6d2d76616c7565")))
                .containsExactly(
                        header(":method", "GET", false),
                        header(":scheme", "https", false),
                        header(":path", "/index.html", false),
                        header(":authority", "www.example.com", false),
                        header("custom-key", "custom-value", false));
    }

    @Test
    void decodesHuffmanEncodedStringLiteralsFromRfcExamples() throws IOException {
        Decoder decoder = new Decoder(8192, 4096);

        assertThat(decode(decoder, hex("828684418cf1e3c2e5f23a6ba0ab90f4ff")))
                .containsExactly(
                        header(":method", "GET", false),
                        header(":scheme", "http", false),
                        header(":path", "/", false),
                        header(":authority", "www.example.com", false));
    }

    @Test
    void encodesStaticAndDynamicTableReferencesForRoundTripDecoding() throws IOException {
        Encoder encoder = new Encoder(256);
        Decoder decoder = new Decoder(8192, 256);

        ByteArrayOutputStream staticHeaderBlock = new ByteArrayOutputStream();
        encoder.encodeHeader(staticHeaderBlock, ascii(":method"), ascii("GET"), false);
        assertThat(staticHeaderBlock.toByteArray()).containsExactly((byte) 0x82);
        assertThat(decode(decoder, staticHeaderBlock.toByteArray()))
                .containsExactly(header(":method", "GET", false));

        ByteArrayOutputStream firstCustomBlock = new ByteArrayOutputStream();
        encoder.encodeHeader(firstCustomBlock, ascii("x-debug"), ascii("alpha"), false);
        assertThat(decode(decoder, firstCustomBlock.toByteArray()))
                .containsExactly(header("x-debug", "alpha", false));

        ByteArrayOutputStream indexedCustomBlock = new ByteArrayOutputStream();
        encoder.encodeHeader(indexedCustomBlock, ascii("x-debug"), ascii("alpha"), false);
        byte[] indexedBytes = indexedCustomBlock.toByteArray();

        assertThat(indexedBytes).hasSize(1);
        assertThat(indexedBytes[0] & 0x80).isEqualTo(0x80);
        assertThat(decode(decoder, indexedBytes)).containsExactly(header("x-debug", "alpha", false));
    }

    @Test
    void roundTripsSensitiveHeadersAsNeverIndexedHeaders() throws IOException {
        Encoder encoder = new Encoder(128);
        Decoder decoder = new Decoder(8192, 128);
        ByteArrayOutputStream headerBlock = new ByteArrayOutputStream();

        encoder.encodeHeader(headerBlock, ascii("authorization"), ascii("secret-token"), true);

        assertThat(decode(decoder, headerBlock.toByteArray()))
                .containsExactly(header("authorization", "secret-token", true));
    }

    @Test
    void appliesDynamicTableSizeUpdatesBeforeDecodingShrunkTables() throws IOException {
        Encoder originalSizeEncoder = new Encoder(128);
        Decoder requiredUpdateDecoder = new Decoder(8192, 128);
        requiredUpdateDecoder.setMaxHeaderTableSize(64);

        ByteArrayOutputStream missingUpdate = new ByteArrayOutputStream();
        originalSizeEncoder.encodeHeader(missingUpdate, ascii("x-small"), ascii("v"), false);
        assertThatThrownBy(() -> requiredUpdateDecoder.decode(
                        new ByteArrayInputStream(missingUpdate.toByteArray()),
                        new CapturingHeaderListener()))
                .isInstanceOf(IOException.class)
                .hasMessage("max dynamic table size change required");

        Encoder resizedEncoder = new Encoder(128);
        Decoder resizedDecoder = new Decoder(8192, 128);
        resizedDecoder.setMaxHeaderTableSize(64);
        ByteArrayOutputStream resizedHeaderBlock = new ByteArrayOutputStream();
        resizedEncoder.setMaxHeaderTableSize(resizedHeaderBlock, 64);
        resizedEncoder.encodeHeader(resizedHeaderBlock, ascii("x-small"), ascii("v"), false);

        assertThat(decode(resizedDecoder, resizedHeaderBlock.toByteArray()))
                .containsExactly(header("x-small", "v", false));
        assertThat(resizedDecoder.getMaxHeaderTableSize()).isEqualTo(64);
        assertThat(resizedEncoder.getMaxHeaderTableSize()).isEqualTo(64);
    }

    @Test
    void reportsTruncatedHeaderBlocksWhenDecodedHeadersExceedMaximumHeaderSize() throws IOException {
        Decoder decoder = new Decoder(4, 4096);
        CapturingHeaderListener listener = new CapturingHeaderListener();

        decoder.decode(new ByteArrayInputStream(hex("82")), listener);

        assertThat(listener.headers()).isEmpty();
        assertThat(decoder.endHeaderBlock()).isTrue();
    }

    @Test
    void rejectsIllegalIndexedHeaderZero() {
        Decoder decoder = new Decoder(8192, 4096);

        assertThatThrownBy(() -> decoder.decode(
                        new ByteArrayInputStream(new byte[] {(byte) 0x80}),
                        new CapturingHeaderListener()))
                .isInstanceOf(IOException.class)
                .hasMessage("illegal index value");
    }

    private static List<Header> decode(Decoder decoder, byte[] headerBlock) throws IOException {
        CapturingHeaderListener listener = new CapturingHeaderListener();
        decoder.decode(new ByteArrayInputStream(headerBlock), listener);
        assertThat(decoder.endHeaderBlock()).isFalse();
        return listener.headers();
    }

    private static Header header(String name, String value, boolean sensitive) {
        return new Header(name, value, sensitive);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static String ascii(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static byte[] hex(String hex) {
        String normalized = hex.replace(" ", "");
        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private record Header(String name, String value, boolean sensitive) {
    }

    private static final class CapturingHeaderListener implements HeaderListener {
        private final List<Header> headers = new ArrayList<>();

        @Override
        public void addHeader(byte[] name, byte[] value, boolean sensitive) {
            headers.add(new Header(ascii(name), ascii(value), sensitive));
        }

        private List<Header> headers() {
            return headers;
        }
    }
}
