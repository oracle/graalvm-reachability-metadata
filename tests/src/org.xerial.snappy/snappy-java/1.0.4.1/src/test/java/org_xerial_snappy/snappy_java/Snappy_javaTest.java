/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyCodec;
import org.xerial.snappy.SnappyError;
import org.xerial.snappy.SnappyErrorCode;
import org.xerial.snappy.SnappyException;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

public class Snappy_javaTest {
    private static final byte[] SAMPLE_BYTES = createSampleBytes();

    @Test
    void compressesAndUncompressesByteArraysWithOffsets() throws Exception {
        byte[] compressed = Snappy.compress(SAMPLE_BYTES);

        assertThat(Snappy.isValidCompressedBuffer(compressed)).isTrue();
        assertThat(Snappy.uncompressedLength(compressed)).isEqualTo(SAMPLE_BYTES.length);
        assertThat(Snappy.uncompress(compressed)).isEqualTo(SAMPLE_BYTES);
        assertThat(compressed.length).isLessThanOrEqualTo(Snappy.maxCompressedLength(SAMPLE_BYTES.length));

        byte[] paddedCompressed = new byte[compressed.length + 7];
        int compressedLength = Snappy.compress(SAMPLE_BYTES, 11, 90, paddedCompressed, 5);
        assertThat(Snappy.isValidCompressedBuffer(paddedCompressed, 5, compressedLength)).isTrue();
        assertThat(Snappy.uncompressedLength(paddedCompressed, 5, compressedLength)).isEqualTo(90);

        byte[] destination = new byte[128];
        Arrays.fill(destination, (byte) -1);
        int uncompressedLength = Snappy.uncompress(paddedCompressed, 5, compressedLength, destination, 17);
        assertThat(uncompressedLength).isEqualTo(90);
        assertThat(Arrays.copyOfRange(destination, 17, 17 + uncompressedLength))
                .isEqualTo(Arrays.copyOfRange(SAMPLE_BYTES, 11, 101));
        assertThat(destination[16]).isEqualTo((byte) -1);
        assertThat(destination[17 + uncompressedLength]).isEqualTo((byte) -1);
    }

    @Test
    void rejectsInvalidCompressedBuffers() throws Exception {
        byte[] invalidCompressedBuffer = "not-snappy-data".getBytes(StandardCharsets.US_ASCII);

        assertThat(Snappy.isValidCompressedBuffer(invalidCompressedBuffer)).isFalse();
        assertThat(Snappy.isValidCompressedBuffer(invalidCompressedBuffer, 2, 7)).isFalse();
    }

    @Test
    void compressesAndUncompressesStringsWithCharsets() throws Exception {
        String text = "Snappy handles Unicode text: "
                + "Καλημέρα κόσμε, こんにちは世界, Привет мир";
        byte[] utf8Compressed = Snappy.compress(text, StandardCharsets.UTF_8);

        assertThat(Snappy.uncompressString(utf8Compressed, StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(Snappy.uncompressString(utf8Compressed, 0, utf8Compressed.length, StandardCharsets.UTF_8))
                .isEqualTo(text);

        String latin1Text = "caf\u00e9, jalape\u00f1o, pi\u00f1ata";
        byte[] latin1Compressed = Snappy.compress(latin1Text, StandardCharsets.ISO_8859_1.name());
        assertThat(Snappy.uncompressString(latin1Compressed, StandardCharsets.ISO_8859_1.name())).isEqualTo(latin1Text);
    }

    @Test
    void compressesAndUncompressesPrimitiveArrays() throws Exception {
        int[] ints = {Integer.MIN_VALUE, -10, 0, 1, 42, Integer.MAX_VALUE};
        long[] longs = {Long.MIN_VALUE, -1L, 0L, 1234567890123456789L, Long.MAX_VALUE};
        short[] shorts = {Short.MIN_VALUE, -7, 0, 99, Short.MAX_VALUE};
        char[] chars = {'S', 'n', 'a', 'p', 'p', 'y', '\u2603'};
        float[] floats = {-1.5F, 0.0F, 3.25F, Float.MAX_VALUE, Float.MIN_NORMAL};
        double[] doubles = {-Math.PI, 0.0D, Math.E, Double.MAX_VALUE, Double.MIN_NORMAL};

        assertThat(Snappy.uncompressIntArray(Snappy.compress(ints))).containsExactly(ints);
        assertThat(Snappy.uncompressLongArray(Snappy.compress(longs))).containsExactly(longs);
        assertThat(Snappy.uncompressShortArray(Snappy.compress(shorts))).containsExactly(shorts);
        assertThat(Snappy.uncompressCharArray(Snappy.compress(chars))).containsExactly(chars);
        assertThat(Snappy.uncompressFloatArray(Snappy.compress(floats))).containsExactly(floats);
        assertThat(Snappy.uncompressDoubleArray(Snappy.compress(doubles))).containsExactly(doubles);
    }

    @Test
    void compressesAndUncompressesDirectByteBuffers() throws Exception {
        ByteBuffer source = ByteBuffer.allocateDirect(SAMPLE_BYTES.length + 20);
        source.position(9);
        source.put(SAMPLE_BYTES);
        source.flip();
        source.position(9);

        ByteBuffer compressed = ByteBuffer.allocateDirect(Snappy.maxCompressedLength(SAMPLE_BYTES.length) + 11);
        compressed.position(4);
        int compressedLength = Snappy.compress(source, compressed);

        assertThat(compressed.limit()).isEqualTo(4 + compressedLength);

        ByteBuffer compressedSlice = compressed.duplicate();
        compressedSlice.position(4);
        compressedSlice.limit(4 + compressedLength);
        assertThat(Snappy.isValidCompressedBuffer(compressedSlice)).isTrue();
        assertThat(Snappy.uncompressedLength(compressedSlice)).isEqualTo(SAMPLE_BYTES.length);

        ByteBuffer destination = ByteBuffer.allocateDirect(SAMPLE_BYTES.length + 13);
        destination.position(6);
        int uncompressedLength = Snappy.uncompress(compressedSlice, destination);
        assertThat(uncompressedLength).isEqualTo(SAMPLE_BYTES.length);
        assertThat(destination.limit()).isEqualTo(6 + SAMPLE_BYTES.length);

        byte[] restored = new byte[SAMPLE_BYTES.length];
        ByteBuffer restoredView = destination.duplicate();
        restoredView.position(6);
        restoredView.get(restored);
        assertThat(restored).isEqualTo(SAMPLE_BYTES);
    }

    @Test
    void requiresDirectByteBuffersForByteBufferCompression() {
        ByteBuffer heapInput = ByteBuffer.wrap(SAMPLE_BYTES);
        ByteBuffer directOutput = ByteBuffer.allocateDirect(Snappy.maxCompressedLength(SAMPLE_BYTES.length));

        assertThatThrownBy(() -> Snappy.compress(heapInput, directOutput))
                .isInstanceOf(SnappyError.class)
                .hasMessageContaining(SnappyErrorCode.NOT_A_DIRECT_BUFFER.name());
    }

    @Test
    void streamsRoundTripBytesAcrossMultipleChunks() throws Exception {
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (SnappyOutputStream output = new SnappyOutputStream(compressedBytes, 17)) {
            output.write(SAMPLE_BYTES, 0, 23);
            output.write(SAMPLE_BYTES, 23, SAMPLE_BYTES.length - 23);
            output.write('!');
        }

        byte[] restored = new byte[SAMPLE_BYTES.length + 1];
        try (SnappyInputStream input = new SnappyInputStream(new ByteArrayInputStream(compressedBytes.toByteArray()))) {
            int totalRead = 0;
            while (totalRead < restored.length) {
                int read = input.read(restored, totalRead, restored.length - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
            assertThat(totalRead).isEqualTo(restored.length);
            assertThat(input.read()).isEqualTo(-1);
        }

        assertThat(Arrays.copyOf(restored, SAMPLE_BYTES.length)).isEqualTo(SAMPLE_BYTES);
        assertThat(restored[SAMPLE_BYTES.length]).isEqualTo((byte) '!');
    }

    @Test
    void streamsRoundTripPrimitiveArrays() throws Exception {
        int[] values = {100, 200, -300, 400, Integer.MAX_VALUE};
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (SnappyOutputStream output = new SnappyOutputStream(compressedBytes, 12)) {
            output.write(values, 0, 2);
            output.write(values, 2, values.length - 2);
        }

        int[] restored = new int[values.length];
        try (SnappyInputStream input = new SnappyInputStream(new ByteArrayInputStream(compressedBytes.toByteArray()))) {
            assertThat(input.read(restored)).isEqualTo(values.length * Integer.BYTES);
            assertThat(input.read(restored)).isEqualTo(-1);
        }

        assertThat(restored).containsExactly(values);
    }

    @Test
    void codecHeaderRoundTripsAndExposesCurrentVersion() throws Exception {
        SnappyCodec codec = SnappyCodec.currentHeader();
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();

        codec.writeHeader(headerBytes);
        SnappyCodec decoded = SnappyCodec.readHeader(new ByteArrayInputStream(headerBytes.toByteArray()));

        assertThat(headerBytes.toByteArray()).hasSize(SnappyCodec.headerSize());
        assertThat(decoded.magic).isEqualTo(SnappyCodec.MAGIC_HEADER);
        assertThat(decoded.isValidMagicHeader()).isTrue();
        assertThat(decoded.version).isEqualTo(SnappyCodec.DEFAULT_VERSION);
        assertThat(decoded.compatibleVersion).isEqualTo(SnappyCodec.MINIMUM_COMPATIBLE_VERSION);
        assertThat(decoded.toString()).contains("version:", "compatible version:");
    }

    @Test
    void exposesNativeLibraryAndErrorInformation() throws Exception {
        assertThat(Snappy.getNativeLibraryVersion()).isNotBlank();
        assertThat(SnappyErrorCode.getErrorCode(SnappyErrorCode.FAILED_TO_UNCOMPRESS.id))
                .isEqualTo(SnappyErrorCode.FAILED_TO_UNCOMPRESS);
        assertThat(SnappyErrorCode.getErrorMessage(SnappyErrorCode.FAILED_TO_UNCOMPRESS.id))
                .contains(SnappyErrorCode.FAILED_TO_UNCOMPRESS.name());

        SnappyException exception = new SnappyException(SnappyErrorCode.PARSING_ERROR, "bad header");
        assertThat(exception.getErrorCode()).isEqualTo(SnappyErrorCode.PARSING_ERROR);
        assertThat(exception.getMessage()).contains(SnappyErrorCode.PARSING_ERROR.name(), "bad header");
    }

    @Test
    void copiesArraysThroughSnappyArrayCopy() throws Exception {
        byte[] source = {10, 20, 30, 40, 50};
        byte[] destination = {-1, -1, -1, -1, -1, -1};

        Snappy.arrayCopy(source, 1, 3, destination, 2);

        assertThat(destination).containsExactly((byte) -1, (byte) -1, (byte) 20, (byte) 30, (byte) 40, (byte) -1);
    }

    private static byte[] createSampleBytes() {
        byte[] bytes = new byte[512];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 31) % 127);
        }
        return bytes;
    }
}
