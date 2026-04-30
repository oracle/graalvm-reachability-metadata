/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_asynchttpclient.async_http_client_netty_utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.asynchttpclient.netty.util.ByteBufUtils;
import org.asynchttpclient.netty.util.Utf8ByteBufCharsetDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Async_http_client_netty_utilsTest {
    @Test
    void convertsReadableBytesWithoutChangingReaderIndex() {
        byte[] payload = new byte[] {10, 20, 30, 40, 50};
        ByteBuf buffer = Unpooled.wrappedBuffer(payload).readerIndex(1);

        byte[] bytes = ByteBufUtils.byteBuf2Bytes(buffer);

        assertThat(bytes).containsExactly((byte) 20, (byte) 30, (byte) 40, (byte) 50);
        assertThat(buffer.readerIndex()).isEqualTo(1);
        assertThat(buffer.readableBytes()).isEqualTo(4);
    }

    @Test
    void returnsBackingArrayWhenTheWholeHeapBufferIsReadable() {
        byte[] payload = new byte[] {1, 2, 3, 4};
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);

        byte[] bytes = ByteBufUtils.byteBuf2Bytes(buffer);

        assertThat(bytes).isSameAs(payload);
        assertThat(buffer.readerIndex()).isZero();
    }

    @Test
    void copiesReadableBytesFromDirectBufferWithoutChangingReaderIndex() {
        ByteBuf direct = ByteBufAllocator.DEFAULT.directBuffer();
        direct.writeBytes(new byte[] {5, 10, 15, 20, 25});
        direct.readerIndex(2);

        try {
            byte[] bytes = ByteBufUtils.byteBuf2Bytes(direct);

            assertThat(bytes).containsExactly((byte) 15, (byte) 20, (byte) 25);
            assertThat(direct.readerIndex()).isEqualTo(2);
            assertThat(direct.readableBytes()).isEqualTo(3);
        } finally {
            direct.release();
        }
    }

    @Test
    void decodesSingleUtf8ByteBufToStringAndChars() {
        String text = "ASCII, accents \u00E9\u00E0, CJK \u6771\u4EAC, emoji \uD83D\uDE80";
        ByteBuf buffer = utf8Buffer(text);

        assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, buffer)).isEqualTo(text);
        assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.UTF_8, buffer)).containsExactly(text.toCharArray());
        assertThat(buffer.readerIndex()).isZero();
    }

    @Test
    void decodesAsciiUsingUtf8DecoderPath() {
        String text = "GET /resource?q=async-http-client HTTP/1.1";
        ByteBuf first = asciiBuffer("GET /resource");
        ByteBuf second = asciiBuffer("?q=async-http-client HTTP/1.1");

        assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.US_ASCII, first, second)).isEqualTo(text);
        assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.US_ASCII, first, second))
                .containsExactly(text.toCharArray());
        assertThat(first.readerIndex()).isZero();
        assertThat(second.readerIndex()).isZero();
    }

    @Test
    void decodesNonUtf8CharsetFromSingleAndMultipleBuffers() {
        Charset charset = StandardCharsets.UTF_16BE;
        String text = "Netty buffers \u2713 and \u03A9";
        byte[] encoded = text.getBytes(charset);
        ByteBuf single = Unpooled.wrappedBuffer(encoded);
        ByteBuf first = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, 0, 12));
        ByteBuf second = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, 12, encoded.length));

        assertThat(ByteBufUtils.byteBuf2String(charset, single)).isEqualTo(text);
        assertThat(ByteBufUtils.byteBuf2Chars(charset, single)).containsExactly(text.toCharArray());
        assertThat(ByteBufUtils.byteBuf2String(charset, first, second)).isEqualTo(text);
        assertThat(ByteBufUtils.byteBuf2Chars(charset, first, second)).containsExactly(text.toCharArray());
    }

    @Test
    void decodesIso88591Characters() {
        Charset charset = StandardCharsets.ISO_8859_1;
        String text = "caf\u00E9 d\u00E9j\u00E0 vu \u00A3";
        ByteBuf buffer = Unpooled.wrappedBuffer(text.getBytes(charset));

        assertThat(ByteBufUtils.byteBuf2String(charset, buffer)).isEqualTo(text);
        assertThat(ByteBufUtils.byteBuf2Chars(charset, buffer)).containsExactly(text.toCharArray());
    }

    @Test
    void decodesUtf8CharactersSplitAcrossByteBufBoundaries() {
        String text = "prefix \u20AC middle \uD83D\uDE00 suffix";
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        int euroLeadByte = indexOf(encoded, (byte) 0xE2);
        int emojiLeadByte = indexOf(encoded, (byte) 0xF0);
        ByteBuf part1 = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, 0, euroLeadByte + 1));
        ByteBuf part2 = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, euroLeadByte + 1, emojiLeadByte + 2));
        ByteBuf part3 = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, emojiLeadByte + 2, encoded.length));

        assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8(part1, part2, part3)).isEqualTo(text);
        assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8Chars(part1, part2, part3)).containsExactly(text.toCharArray());
        assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, part1, part2, part3)).isEqualTo(text);
        assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.UTF_8, part1, part2, part3))
                .containsExactly(text.toCharArray());
    }

    @Test
    void decodesCompositeHeapBufferWithSeveralNioBuffers() {
        String text = "alpha \u20AC beta \uD83D\uDE00 gamma";
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        int split = indexOf(encoded, (byte) 0xE2) + 1;
        ByteBuf first = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, 0, split));
        ByteBuf second = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, split, encoded.length));
        ByteBuf composite = Unpooled.wrappedBuffer(first, second);

        try {
            assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8(composite)).isEqualTo(text);
            assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8Chars(composite)).containsExactly(text.toCharArray());
            assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, composite)).isEqualTo(text);
            assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.UTF_8, composite))
                    .containsExactly(text.toCharArray());
        } finally {
            composite.release();
        }
    }

    @Test
    void decodesDirectByteBufs() {
        String text = "direct buffer: \u03A9 and \uD83D\uDE80";
        ByteBuf direct = ByteBufAllocator.DEFAULT.directBuffer();
        direct.writeBytes(text.getBytes(StandardCharsets.UTF_8));

        try {
            assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8(direct)).isEqualTo(text);
            assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8Chars(direct)).containsExactly(text.toCharArray());
            assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, direct)).isEqualTo(text);
            assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.UTF_8, direct)).containsExactly(text.toCharArray());
            assertThat(direct.readerIndex()).isZero();
        } finally {
            direct.release();
        }
    }

    @Test
    void decodesUtf8AcrossMixedHeapAndDirectBuffers() {
        String text = "mixed heap and direct \uD83D\uDE00 buffers";
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        int emojiLeadByte = indexOf(encoded, (byte) 0xF0);
        ByteBuf heap = Unpooled.wrappedBuffer(Arrays.copyOfRange(encoded, 0, emojiLeadByte + 2));
        ByteBuf direct = ByteBufAllocator.DEFAULT.directBuffer();
        direct.writeBytes(Arrays.copyOfRange(encoded, emojiLeadByte + 2, encoded.length));

        try {
            assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8(heap, direct)).isEqualTo(text);
            assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8Chars(heap, direct)).containsExactly(text.toCharArray());
            assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, heap, direct)).isEqualTo(text);
            assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.UTF_8, heap, direct))
                    .containsExactly(text.toCharArray());
            assertThat(heap.readerIndex()).isZero();
            assertThat(direct.readerIndex()).isZero();
        } finally {
            direct.release();
        }
    }

    @Test
    void replacesMalformedUtf8Input() {
        ByteBuf invalid = Unpooled.wrappedBuffer(new byte[] {(byte) 0xE2, 0x28, (byte) 0xA1});

        assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8(invalid)).isEqualTo("\uFFFD(\uFFFD");
        assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8Chars(invalid)).containsExactly('\uFFFD', '(', '\uFFFD');
        assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, invalid)).isEqualTo("\uFFFD(\uFFFD");
    }

    @Test
    void reusableDecoderCanBeResetBetweenIndependentDecodes() {
        Utf8ByteBufCharsetDecoder decoder = new Utf8ByteBufCharsetDecoder();
        ByteBuf first = utf8Buffer("first \u20AC");
        ByteBuf second = utf8Buffer("second \uD83D\uDE00");

        assertThat(decoder.decode(first)).isEqualTo("first \u20AC");
        decoder.reset();
        assertThat(decoder.decode(second)).isEqualTo("second \uD83D\uDE00");
        decoder.reset();
        assertThat(decoder.decodeChars(second)).containsExactly("second \uD83D\uDE00".toCharArray());
    }

    @Test
    void decodesEmptyBuffers() {
        ByteBuf empty = Unpooled.EMPTY_BUFFER;

        assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8(empty)).isEmpty();
        assertThat(Utf8ByteBufCharsetDecoder.decodeUtf8Chars(empty)).isEmpty();
        assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, empty)).isEmpty();
        assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.UTF_8, empty)).isEmpty();
        assertThat(ByteBufUtils.byteBuf2String(StandardCharsets.ISO_8859_1, empty)).isEmpty();
        assertThat(ByteBufUtils.byteBuf2Chars(StandardCharsets.ISO_8859_1, empty)).isEmpty();
    }

    private static ByteBuf utf8Buffer(String text) {
        return Unpooled.wrappedBuffer(text.getBytes(StandardCharsets.UTF_8));
    }

    private static ByteBuf asciiBuffer(String text) {
        return Unpooled.wrappedBuffer(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static int indexOf(byte[] haystack, byte needle) {
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i] == needle) {
                return i;
            }
        }
        throw new IllegalArgumentException("Byte not found: " + needle);
    }
}
