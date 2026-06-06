/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_buffers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.buffers.Ascii;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.CompositeBufferData;
import io.helidon.common.buffers.DataListener;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataReader.IncorrectNewLineException;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.buffers.LazyString;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Helidon_common_buffersTest {
    @Test
    void asciiConversionHandlesOnlyAsciiLetters() {
        assertEquals("header-name-123_\u00c4", Ascii.toLowerCase("Header-Name-123_\u00c4"));
        assertEquals("HEADER-NAME-123_\u00c4", Ascii.toUpperCase((CharSequence) "Header-Name-123_\u00c4"));
        assertEquals('q', Ascii.toLowerCase('Q'));
        assertEquals('Q', Ascii.toUpperCase('q'));
        assertEquals('!', Ascii.toLowerCase('!'));
        assertEquals('!', Ascii.toUpperCase('!'));
        assertTrue(Ascii.isLowerCase('z'));
        assertTrue(Ascii.isUpperCase('Z'));
        assertFalse(Ascii.isLowerCase('Z'));
        assertFalse(Ascii.isUpperCase('z'));
    }

    @Test
    void bytesFindsDelimitersAndBuildsNativeWords() {
        byte[] bytes = "GET /path?a=b&c=d HTTP/1.1".getBytes(StandardCharsets.US_ASCII);

        assertEquals((byte) ':', Bytes.COLON_BYTE);
        assertEquals((byte) ' ', Bytes.SPACE_BYTE);
        assertEquals((byte) '/', Bytes.SLASH_BYTE);
        assertEquals((byte) '?', Bytes.QUESTION_MARK_BYTE);
        assertEquals((byte) '&', Bytes.AMPERSAND_BYTE);
        assertEquals(3, Bytes.firstIndexOf(bytes, 0, bytes.length, Bytes.SPACE_BYTE));
        assertEquals(4, Bytes.firstIndexOf(bytes, 0, bytes.length, Bytes.SLASH_BYTE));
        assertEquals(9, Bytes.firstIndexOf(bytes, 5, bytes.length, Bytes.QUESTION_MARK_BYTE));
        assertEquals(-1, Bytes.firstIndexOf(bytes, 0, 3, Bytes.SLASH_BYTE));

        ByteBuffer littleEndian = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        littleEndian.put(bytes, 4, Long.BYTES).flip();
        assertEquals(littleEndian.getLong(), Bytes.toWord(bytes, 4));
    }

    @Test
    void fixedBufferWritesPrimitivesAndReadsThemInOrder() throws Exception {
        BufferData data = BufferData.create(64);
        data.writeInt8(0x7f)
                .writeInt16(0x1234)
                .writeInt24(0xabcdef)
                .writeInt32(0x01020304)
                .writeUnsignedInt32(0xfedcba98L);
        data.writeAscii("END");

        assertEquals(0x7f, data.read());
        assertEquals(0x1234, data.readInt16());
        assertEquals(0xabcdef, data.readInt24());
        assertEquals(0x01020304, data.readInt32());
        assertEquals(0xfedcba98L, data.readUnsignedInt32());
        assertEquals("END", data.readString(3, StandardCharsets.US_ASCII));
        assertTrue(data.consumed());

        data.rewind();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        data.writeTo(output);
        assertEquals(17, output.toByteArray().length);
        assertTrue(data.consumed());
    }

    @Test
    void bufferCanReadFromStreamsAndExposeInputStreamView() throws Exception {
        BufferData data = BufferData.create(16);
        int read = data.readFrom(new ByteArrayInputStream("stream-data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("stream-data".length(), read);
        assertEquals("stream", data.readString(6));
        assertEquals('d', data.get(1));

        data.skip(1);
        byte[] target = new byte[4];
        assertEquals(4, data.asInputStream().read(target));
        assertArrayEquals("data".getBytes(StandardCharsets.UTF_8), target);
        assertTrue(data.consumed());
    }

    @Test
    void bufferSupportsCopyTrimSearchAndByteBufferWrites() {
        BufferData data = BufferData.create("alpha beta beta!");
        assertEquals(5, data.indexOf((byte) ' '));
        assertEquals(10, data.lastIndexOf((byte) ' '));
        assertEquals(12, data.lastIndexOf((byte) 'e', 14));

        data.skip(6);
        BufferData copy = data.copy();
        assertEquals("beta beta!", copy.readString(copy.available(), StandardCharsets.UTF_8));
        assertTrue(data.consumed(), "copy reads the remaining bytes from the source buffer");

        BufferData trimmed = BufferData.create("trail!!");
        trimmed.trim(2);
        assertEquals("trail", trimmed.readString(trimmed.available()));
        assertThrows(IllegalArgumentException.class, () -> BufferData.create("small").trim(6));

        BufferData byteBufferSource = BufferData.create("abcdef");
        ByteBuffer destination = ByteBuffer.allocate(4);
        assertEquals(4, byteBufferSource.writeTo(destination, 10));
        assertEquals("abcd", new String(destination.array(), StandardCharsets.UTF_8));
        assertEquals("ef", byteBufferSource.readString(byteBufferSource.available()));
    }

    @Test
    void growingBufferExpandsAndCanBeReused() {
        BufferData data = BufferData.growing(2);
        data.write("abcdef".getBytes(StandardCharsets.UTF_8));

        assertTrue(data.capacity() >= 0);
        assertEquals(6, data.available());
        assertEquals("abc", data.readString(3));
        assertEquals('d', data.read());
        assertEquals("ef", data.readString(2));
        assertTrue(data.consumed());

        data.clear();
        data.writeAscii("xy");
        assertEquals("xy", data.readString(2));
    }

    @Test
    void compositeBuffersReadAcrossSegments() {
        BufferData first = BufferData.create("hello ");
        BufferData second = BufferData.create("world");
        BufferData third = BufferData.create("!");
        BufferData composite = BufferData.create(first, second, third);

        assertEquals(12, composite.available());
        assertEquals("hello", composite.readString(5));
        assertEquals(0, composite.indexOf((byte) ' '));
        composite.skip(1);
        assertEquals('w', composite.get(0));
        assertEquals("world!", composite.readString(composite.available()));
        assertTrue(composite.consumed());

        CompositeBufferData growableComposite = BufferData.createComposite(BufferData.create("one"));
        assertSame(growableComposite, growableComposite.add(BufferData.create("-two")));
        assertEquals("one-two", growableComposite.readString(growableComposite.available()));

        BufferData single = BufferData.create("single");
        assertSame(single, BufferData.create(List.of(single)));
        assertSame(single, BufferData.create(single));
        assertTrue(BufferData.create(List.of()).consumed());
        assertTrue(BufferData.create(new BufferData[0]).consumed());
    }

    @Test
    void readOnlySlicesCanBeReadAndRewoundButNotMutated() {
        byte[] bytes = "0123456789".getBytes(StandardCharsets.US_ASCII);
        BufferData readOnly = BufferData.createReadOnly(bytes, 2, 5);

        assertEquals(5, readOnly.available());
        assertEquals("234", readOnly.readString(3, StandardCharsets.US_ASCII));
        assertEquals("23", readOnly.rewind().readString(2, StandardCharsets.US_ASCII));
        assertThrows(UnsupportedOperationException.class, () -> readOnly.write(1));
        assertThrows(UnsupportedOperationException.class, readOnly::clear);
    }

    @Test
    void hpackIntegerEncodingRoundTripsShortAndLongValues() {
        BufferData shortValue = BufferData.create(8).writeHpackInt(10, 0b1010_0000, 5);
        int firstShortByte = shortValue.read();
        assertEquals(0b1010_1010, firstShortByte);
        assertEquals(10, shortValue.readHpackInt(firstShortByte, 5));

        BufferData longValue = BufferData.create(8).writeHpackInt(1337, 0b0100_0000, 5);
        int firstLongByte = longValue.read();
        assertEquals(0b0101_1111, firstLongByte);
        assertEquals(1337, longValue.readHpackInt(firstLongByte, 5));
    }

    @Test
    void dataReaderReadsAcrossSuppliedChunks() {
        DataReader reader = reader("Hel", "idon", "\r", "\nNext");

        assertEquals(0, reader.available());
        assertTrue(reader.startsWith("Helidon".getBytes(StandardCharsets.US_ASCII)));
        assertEquals('H', reader.lookup());
        assertEquals("Hel", reader.readAsciiString(3));
        assertEquals("idon", reader.readLazyString(StandardCharsets.US_ASCII, 4).toString());
        assertTrue(reader.startsWithNewLine());
        assertEquals("", reader.readLine());
        assertEquals("Next", reader.readBuffer(4).readString(4, StandardCharsets.US_ASCII));
    }

    @Test
    void dataReaderPeeksBuffersWithoutConsumingAndExtractsCurrentChunks() {
        DataReader reader = reader("ab", "cd", "ef");

        BufferData preview = reader.getBuffer(4);
        assertEquals("abcd", preview.readString(preview.available(), StandardCharsets.US_ASCII));
        assertEquals("ab", reader.readAsciiString(2));

        BufferData currentChunk = reader.readBuffer();
        assertEquals("cd", currentChunk.readString(currentChunk.available(), StandardCharsets.US_ASCII));
        assertArrayEquals("ef".getBytes(StandardCharsets.US_ASCII), reader.readBytes(2));
    }

    @Test
    void dataReaderFindsDelimitersAndReportsInvalidNewLines() {
        DataReader reader = reader("abc:def", "\r", "\nrest");
        assertEquals(3, reader.findOrNewLine((byte) ':', 64));
        assertEquals("abc", reader.readAsciiString(3));
        reader.skip(1);
        assertEquals(-4, reader.findOrNewLine((byte) ';', 64));
        assertEquals("def", reader.readLine());
        assertEquals("rest", reader.readAsciiString(4));

        assertThrows(IncorrectNewLineException.class, () -> reader("bad\nline").findNewLine(32));
        assertEquals(3, new DataReader(chunks("ok\nline"), true).findNewLine(3));
    }

    @Test
    void lazyStringStripsOptionalWhitespaceAndCachesStringValues() {
        LazyString lazyString = new LazyString("  value\t".getBytes(StandardCharsets.US_ASCII),
                StandardCharsets.US_ASCII);

        String stringValue = lazyString.toString();
        assertSame(stringValue, lazyString.toString());
        assertEquals("  value\t", stringValue);
        assertEquals("value", lazyString.stripOws());
        assertSame(lazyString.stripOws(), lazyString.stripOws());

        LazyString slice = new LazyString("xxpayloadyy".getBytes(StandardCharsets.US_ASCII), 2, 7,
                StandardCharsets.US_ASCII);
        assertEquals("payload", slice.toString());
    }

    @Test
    void dataListenerAndDataWriterDefaultMethodsAreUsable() {
        List<String> events = new ArrayList<>();
        DataListener<String> listener = new DataListener<>() {
            @Override
            public void data(String context, BufferData buffer) {
                events.add(context + ":" + buffer.readString(buffer.available(), StandardCharsets.UTF_8));
            }

            @Override
            public void data(String context, byte[] bytes, int offset, int length) {
                events.add(context + ":" + new String(bytes, offset, length, StandardCharsets.UTF_8));
            }
        };
        listener.data("ctx", BufferData.create("buffer"));
        listener.data("ctx", "array".getBytes(StandardCharsets.UTF_8), 0, "array".length());
        assertEquals(List.of("ctx:buffer", "ctx:array"), events);

        RecordingWriter writer = new RecordingWriter();
        writer.write(BufferData.create("a"), BufferData.create("b"));
        writer.writeNow(BufferData.create("c"));
        writer.flush();
        writer.close();
        assertEquals("abc", writer.payload());
    }

    private static DataReader reader(String... chunks) {
        return new DataReader(chunks(chunks));
    }

    private static Supplier<byte[]> chunks(String... chunks) {
        Queue<byte[]> queue = new ArrayDeque<>();
        Arrays.stream(chunks)
                .map(chunk -> chunk.getBytes(StandardCharsets.US_ASCII))
                .forEach(queue::add);
        return queue::poll;
    }

    private static final class RecordingWriter implements DataWriter {
        private final StringBuilder payload = new StringBuilder();
        private final AtomicInteger immediateWrites = new AtomicInteger();

        @Override
        public void write(BufferData... buffers) {
            for (BufferData buffer : buffers) {
                write(buffer);
            }
        }

        @Override
        public void write(BufferData buffer) {
            payload.append(buffer.readString(buffer.available(), StandardCharsets.UTF_8));
        }

        @Override
        public void writeNow(BufferData... buffers) {
            immediateWrites.addAndGet(buffers.length);
            write(buffers);
        }

        @Override
        public void writeNow(BufferData buffer) {
            immediateWrites.incrementAndGet();
            write(buffer);
        }

        private String payload() {
            assertEquals(1, immediateWrites.get());
            return payload.toString();
        }
    }
}
