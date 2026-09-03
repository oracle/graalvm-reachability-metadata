/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdBufferDecompressingStream;
import com.github.luben.zstd.ZstdBufferDecompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import com.github.luben.zstd.ZstdDirectBufferCompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdDirectBufferDecompressingStream;
import com.github.luben.zstd.ZstdDirectBufferDecompressingStreamNoFinalizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ZstdBufferStreamApiCoverageTest {
    private static final byte[] INPUT = ("direct buffer streams encode and decode application records with "
            + "repeated fields so the stream APIs do meaningful work. ").repeat(10)
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] DICT = ("direct buffer stream records and repeated fields. ").repeat(12)
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void directCompressionStreamsFlushCloseAndHonorDictionaries() throws Exception {
        assertThat(ZstdDirectBufferCompressingStream.recommendedOutputBufferSize()).isPositive();
        assertThat(ZstdDirectBufferCompressingStreamNoFinalizer.recommendedOutputBufferSize()).isPositive();

        ByteBuffer output = ByteBuffer.allocateDirect(ZstdDirectBufferCompressingStream.recommendedOutputBufferSize());
        try (ZstdDirectBufferCompressingStream stream = new ZstdDirectBufferCompressingStream(output, 3);
                ZstdDictCompress dictionary = new ZstdDictCompress(DICT, 3)) {
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            assertThat(stream.setDict(dictionary)).isSameAs(stream);
            stream.setFinalize(false);
            stream.compress(directBuffer(INPUT));
            stream.flush();
        }
        assertThat(decompressDirect(output, DICT)).isEqualTo(INPUT);

        ByteBuffer secondOutput = ByteBuffer.allocateDirect(
                ZstdDirectBufferCompressingStreamNoFinalizer.recommendedOutputBufferSize());
        try (ZstdDirectBufferCompressingStreamNoFinalizer stream =
                     new ZstdDirectBufferCompressingStreamNoFinalizer(secondOutput, 3)) {
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            stream.compress(directBuffer(INPUT));
            stream.flush();
        }
        assertThat(decompressDirect(secondOutput, DICT)).isEqualTo(INPUT);
    }

    @Test
    void bufferDecompressionStreamsReadUntilTheirInputIsConsumed() throws Exception {
        byte[] compressed = Zstd.compress(INPUT);
        assertThat(ZstdBufferDecompressingStream.recommendedTargetBufferSize()).isPositive();
        try (ZstdBufferDecompressingStream stream = new ZstdBufferDecompressingStream(ByteBuffer.wrap(compressed))) {
            assertThat(stream.hasRemaining()).isTrue();
            assertThat(stream.setLongMax(20)).isSameAs(stream);
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            try (ZstdDictDecompress dictionary = new ZstdDictDecompress(DICT)) {
                assertThat(stream.setDict(dictionary)).isSameAs(stream);
                stream.setFinalize(false);
                ByteBuffer output = ByteBuffer.allocate(INPUT.length);
                readBufferStream(stream, output);
                assertThat(bytes(output.flip())).isEqualTo(INPUT);
                assertThat(stream.hasRemaining()).isFalse();
            }
        }

        assertThat(ZstdBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize()).isPositive();
        try (ZstdBufferDecompressingStreamNoFinalizer stream =
                     new ZstdBufferDecompressingStreamNoFinalizer(ByteBuffer.wrap(compressed))) {
            assertThat(stream.setLongMax(20)).isSameAs(stream);
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            try (ZstdDictDecompress dictionary = new ZstdDictDecompress(DICT)) {
                assertThat(stream.setDict(dictionary)).isSameAs(stream);
                ByteBuffer output = ByteBuffer.allocate(INPUT.length);
                readBufferStream(stream, output);
                assertThat(bytes(output.flip())).isEqualTo(INPUT);
                assertThat(stream.hasRemaining()).isFalse();
            }
        }
    }

    @Test
    void directDecompressionStreamsUseDirectBuffersAndDictionaries() throws Exception {
        byte[] compressed = Zstd.compress(INPUT);
        assertThat(ZstdDirectBufferDecompressingStream.recommendedTargetBufferSize()).isPositive();
        try (ZstdDirectBufferDecompressingStream stream = new ZstdDirectBufferDecompressingStream(
                directBuffer(compressed))) {
            assertThat(stream.hasRemaining()).isTrue();
            assertThat(stream.setLongMax(20)).isSameAs(stream);
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            try (ZstdDictDecompress dictionary = new ZstdDictDecompress(DICT)) {
                assertThat(stream.setDict(dictionary)).isSameAs(stream);
            }
            stream.setFinalize(false);
            ByteBuffer output = ByteBuffer.allocateDirect(INPUT.length);
            readDirectStream(stream, output);
            assertThat(bytes(output.flip())).isEqualTo(INPUT);
            assertThat(stream.hasRemaining()).isFalse();
        }

        assertThat(ZstdDirectBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize()).isPositive();
        try (ZstdDirectBufferDecompressingStreamNoFinalizer stream =
                     new ZstdDirectBufferDecompressingStreamNoFinalizer(directBuffer(compressed))) {
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            ByteBuffer output = ByteBuffer.allocateDirect(INPUT.length);
            readDirectStream(stream, output);
            assertThat(bytes(output.flip())).isEqualTo(INPUT);
            assertThat(stream.hasRemaining()).isFalse();
        }
    }

    private static void readBufferStream(ZstdBufferDecompressingStream stream, ByteBuffer output) throws IOException {
        while (stream.hasRemaining()) {
            assertThat(stream.read(output)).isGreaterThanOrEqualTo(0);
        }
    }

    private static void readBufferStream(ZstdBufferDecompressingStreamNoFinalizer stream, ByteBuffer output)
            throws IOException {
        while (stream.hasRemaining()) {
            assertThat(stream.read(output)).isGreaterThanOrEqualTo(0);
        }
    }

    private static void readDirectStream(ZstdDirectBufferDecompressingStream stream, ByteBuffer output)
            throws IOException {
        while (stream.hasRemaining()) {
            assertThat(stream.read(output)).isGreaterThanOrEqualTo(0);
        }
    }

    private static void readDirectStream(ZstdDirectBufferDecompressingStreamNoFinalizer stream, ByteBuffer output)
            throws IOException {
        while (stream.hasRemaining()) {
            assertThat(stream.read(output)).isGreaterThanOrEqualTo(0);
        }
    }

    private static ByteBuffer directBuffer(byte[] bytes) {
        return ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
    }

    private static byte[] decompressDirect(ByteBuffer output, byte[] dictionary) {
        output.flip();
        byte[] compressed = bytes(output);
        try (ZstdDictDecompress decompressor = new ZstdDictDecompress(dictionary)) {
            return Zstd.decompress(compressed, decompressor, INPUT.length);
        }
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }
}
