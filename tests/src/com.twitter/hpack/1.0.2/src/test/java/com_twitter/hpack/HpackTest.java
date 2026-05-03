/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.hpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
    private static final int MAX_HEADER_SIZE = 4096;
    private static final int DEFAULT_TABLE_SIZE = 4096;

    @Test
    void encoderAndDecoderRoundTripLiteralAndDynamicIndexedHeaders() throws IOException {
        Encoder encoder = new Encoder(DEFAULT_TABLE_SIZE);
        ByteArrayOutputStream encodedHeaders = new ByteArrayOutputStream();

        encoder.encodeHeader(encodedHeaders, ascii("custom-key"), ascii("custom-value"), false);
        int firstHeaderLength = encodedHeaders.size();
        encoder.encodeHeader(encodedHeaders, ascii("custom-key"), ascii("custom-value"), false);
        int repeatedHeaderLength = encodedHeaders.size() - firstHeaderLength;

        CapturingHeaderListener listener = decode(encodedHeaders.toByteArray());

        assertThat(listener.headers())
                .containsExactly(
                        new DecodedHeader("custom-key", "custom-value", false),
                        new DecodedHeader("custom-key", "custom-value", false));
        assertThat(repeatedHeaderLength)
                .as("the second header should be emitted as a compact dynamic-table index")
                .isLessThan(firstHeaderLength);
    }

    @Test
    void dynamicTableEntriesCanBeReusedAcrossHeaderBlocks() throws IOException {
        Encoder encoder = new Encoder(DEFAULT_TABLE_SIZE);
        Decoder decoder = new Decoder(MAX_HEADER_SIZE, DEFAULT_TABLE_SIZE);
        byte[] name = ascii("x-cross-block");
        byte[] value = ascii("persistent");

        ByteArrayOutputStream firstHeaderBlock = new ByteArrayOutputStream();
        encoder.encodeHeader(firstHeaderBlock, name, value, false);
        CapturingHeaderListener firstListener = new CapturingHeaderListener();
        decoder.decode(new ByteArrayInputStream(firstHeaderBlock.toByteArray()), firstListener);
        boolean firstBlockExceededHeaderSize = decoder.endHeaderBlock();

        ByteArrayOutputStream secondHeaderBlock = new ByteArrayOutputStream();
        encoder.encodeHeader(secondHeaderBlock, name, value, false);
        CapturingHeaderListener secondListener = new CapturingHeaderListener();
        decoder.decode(new ByteArrayInputStream(secondHeaderBlock.toByteArray()), secondListener);
        boolean secondBlockExceededHeaderSize = decoder.endHeaderBlock();

        assertThat(firstBlockExceededHeaderSize).isFalse();
        assertThat(secondBlockExceededHeaderSize).isFalse();
        assertThat(secondHeaderBlock.toByteArray())
                .as("the second header block should reference the dynamic table with one indexed field")
                .hasSize(1);
        assertThat(firstListener.headers())
                .containsExactly(new DecodedHeader("x-cross-block", "persistent", false));
        assertThat(secondListener.headers())
                .containsExactly(new DecodedHeader("x-cross-block", "persistent", false));
    }

    @Test
    void decoderReadsStaticTableIndexesAndLiteralValuesWithIndexedNames() throws IOException {
        ByteArrayOutputStream headerBlock = new ByteArrayOutputStream();
        headerBlock.write(0x82); // Indexed Header Field: `:method: GET`.
        headerBlock.write(0x86); // Indexed Header Field: `:scheme: http`.
        headerBlock.write(0x04); // Literal Header Field without Indexing, indexed name `:path`.
        headerBlock.write(0x07);
        headerBlock.write(ascii("/sample"));

        CapturingHeaderListener listener = decode(headerBlock.toByteArray());

        assertThat(listener.headers())
                .containsExactly(
                        new DecodedHeader(":method", "GET", false),
                        new DecodedHeader(":scheme", "http", false),
                        new DecodedHeader(":path", "/sample", false));
    }

    @Test
    void sensitiveHeaderUsesNeverIndexedRepresentation() throws IOException {
        Encoder encoder = new Encoder(DEFAULT_TABLE_SIZE);
        ByteArrayOutputStream encodedHeaders = new ByteArrayOutputStream();

        encoder.encodeHeader(encodedHeaders, ascii("authorization"), ascii("secret-token"), true);
        encoder.encodeHeader(encodedHeaders, ascii("authorization"), ascii("secret-token"), false);

        CapturingHeaderListener listener = decode(encodedHeaders.toByteArray());

        assertThat(listener.headers())
                .containsExactly(
                        new DecodedHeader("authorization", "secret-token", true),
                        new DecodedHeader("authorization", "secret-token", false));
    }

    @Test
    void encoderUsesHuffmanCodingForCompactStringLiterals() throws IOException {
        Encoder encoder = new Encoder(DEFAULT_TABLE_SIZE);
        ByteArrayOutputStream encodedHeaders = new ByteArrayOutputStream();

        encoder.encodeHeader(encodedHeaders, ascii(":authority"), ascii("www.example.com"), false);
        byte[] headerBlock = encodedHeaders.toByteArray();
        CapturingHeaderListener listener = decode(headerBlock);

        assertThat(headerBlock[0] & 0xff)
                .as("literal header should use the static name index for :authority")
                .isEqualTo(0x41);
        assertThat(headerBlock[1] & 0x80)
                .as("the value string literal should be Huffman encoded")
                .isEqualTo(0x80);
        assertThat(headerBlock[1] & 0x7f)
                .as("the encoded value should be shorter than the ASCII representation")
                .isLessThan(ascii("www.example.com").length);
        assertThat(listener.headers())
                .containsExactly(new DecodedHeader(":authority", "www.example.com", false));
    }

    @Test
    void maxHeaderTableSizeUpdateIsEmittedAndAppliedBeforeHeaders() throws IOException {
        Encoder encoder = new Encoder(128);
        Decoder decoder = new Decoder(MAX_HEADER_SIZE, 128);
        ByteArrayOutputStream encodedHeaders = new ByteArrayOutputStream();

        encoder.setMaxHeaderTableSize(encodedHeaders, 64);
        encoder.encodeHeader(encodedHeaders, ascii("cache-control"), ascii("no-cache"), false);
        CapturingHeaderListener listener = new CapturingHeaderListener();
        decoder.decode(new ByteArrayInputStream(encodedHeaders.toByteArray()), listener);
        boolean exceededHeaderSize = decoder.endHeaderBlock();

        assertThat(exceededHeaderSize).isFalse();
        assertThat(encoder.getMaxHeaderTableSize()).isEqualTo(64);
        assertThat(decoder.getMaxHeaderTableSize()).isEqualTo(64);
        assertThat(listener.headers())
                .containsExactly(new DecodedHeader("cache-control", "no-cache", false));
    }

    @Test
    void decoderCanResumeHeaderBlockAcrossFragmentedInput() throws IOException {
        Encoder encoder = new Encoder(DEFAULT_TABLE_SIZE);
        ByteArrayOutputStream encodedHeaders = new ByteArrayOutputStream();
        encoder.encodeHeader(encodedHeaders, ascii("x-fragmented"), ascii("chunked"), false);

        Decoder decoder = new Decoder(MAX_HEADER_SIZE, DEFAULT_TABLE_SIZE);
        CapturingHeaderListener listener = new CapturingHeaderListener();
        IncrementalByteArrayInputStream fragmentedInput =
                new IncrementalByteArrayInputStream(encodedHeaders.toByteArray());
        while (fragmentedInput.revealNextByte()) {
            decoder.decode(fragmentedInput, listener);
        }
        boolean exceededHeaderSize = decoder.endHeaderBlock();

        assertThat(exceededHeaderSize).isFalse();
        assertThat(listener.headers())
                .containsExactly(new DecodedHeader("x-fragmented", "chunked", false));
    }

    @Test
    void decoderReportsOversizedHeadersAtEndOfHeaderBlock() throws IOException {
        Encoder encoder = new Encoder(DEFAULT_TABLE_SIZE);
        ByteArrayOutputStream encodedHeaders = new ByteArrayOutputStream();
        encoder.encodeHeader(encodedHeaders, ascii("x-large-header"), ascii("larger-than-limit"), false);
        Decoder decoder = new Decoder(8, DEFAULT_TABLE_SIZE);
        CapturingHeaderListener listener = new CapturingHeaderListener();

        decoder.decode(new ByteArrayInputStream(encodedHeaders.toByteArray()), listener);
        boolean exceededHeaderSize = decoder.endHeaderBlock();

        assertThat(exceededHeaderSize).isTrue();
        assertThat(listener.headers()).isEmpty();
    }

    @Test
    void invalidInputsFailFastWithClearExceptions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Encoder(-1))
                .withMessageContaining("Illegal Capacity");

        Decoder decoder = new Decoder(MAX_HEADER_SIZE, DEFAULT_TABLE_SIZE);
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> decoder.decode(new ByteArrayInputStream(new byte[] {(byte) 0x80}),
                        new CapturingHeaderListener()))
                .withMessageContaining("illegal index value");

        Decoder strictDecoder = new Decoder(MAX_HEADER_SIZE, 32);
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> strictDecoder.decode(new ByteArrayInputStream(new byte[] {0x3f, 0x02}),
                        new CapturingHeaderListener()))
                .withMessageContaining("invalid max dynamic table size");
    }

    private static CapturingHeaderListener decode(byte[] headerBlock) throws IOException {
        Decoder decoder = new Decoder(MAX_HEADER_SIZE, DEFAULT_TABLE_SIZE);
        CapturingHeaderListener listener = new CapturingHeaderListener();
        decoder.decode(new ByteArrayInputStream(headerBlock), listener);
        boolean exceededHeaderSize = decoder.endHeaderBlock();
        assertThat(exceededHeaderSize).isFalse();
        return listener;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private record DecodedHeader(String name, String value, boolean sensitive) {
    }

    private static final class IncrementalByteArrayInputStream extends ByteArrayInputStream {
        private IncrementalByteArrayInputStream(byte[] bytes) {
            super(bytes);
            count = 0;
        }

        private boolean revealNextByte() {
            if (count == buf.length) {
                return false;
            }
            count++;
            return true;
        }
    }

    private static final class CapturingHeaderListener implements HeaderListener {
        private final List<DecodedHeader> headers = new ArrayList<>();

        @Override
        public void addHeader(byte[] name, byte[] value, boolean sensitive) {
            headers.add(new DecodedHeader(toAsciiString(name), toAsciiString(value), sensitive));
        }

        private List<DecodedHeader> headers() {
            return headers;
        }

        private static String toAsciiString(byte[] value) {
            return new String(value, StandardCharsets.US_ASCII);
        }
    }
}
