/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdFrameProgression;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ZstdContextApiCoverageTest {
    private static final byte[] INPUT = ("context compression keeps configuration and frame progression useful; "
            + "the payload contains repeated fields for a realistic frame. ").repeat(10)
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] DICT = ("context dictionary repeated fields and frame configuration. ").repeat(12)
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void compressionContextConfiguresFramesAndSupportsAllBufferForms() {
        ZstdCompressCtx context = new ZstdCompressCtx();
        try {
            assertThat(context.setLevel(3)).isSameAs(context);
            assertThat(context.setMagicless(false)).isSameAs(context);
            assertThat(context.setChecksum(true)).isSameAs(context);
            assertThat(context.setWorkers(0)).isSameAs(context);
            assertThat(context.setContentSize(true)).isSameAs(context);
            assertThat(context.setDictID(true)).isSameAs(context);
            assertThat(context.setLong(20)).isSameAs(context);
            context.setPledgedSrcSize(INPUT.length);
            byte[] compressed = context.compress(INPUT);
            assertThat(Zstd.decompress(compressed, INPUT.length)).isEqualTo(INPUT);
            ZstdFrameProgression progression = context.getFrameProgression();
            assertThat(progression.getIngested()).isPositive();
            assertThat(progression.getConsumed()).isPositive();
            assertThat(progression.getProduced()).isPositive();
            assertThat(progression.getFlushed()).isPositive();
            assertThat(progression.getCurrentJobID()).isGreaterThanOrEqualTo(0);
            assertThat(progression.getNbActiveWorkers()).isGreaterThanOrEqualTo(0);

            context.reset();
            byte[] destination = new byte[(int) Zstd.compressBound(INPUT.length)];
            int compressedLength = context.compress(destination, INPUT);
            assertThat(compressedLength).isPositive();
            assertThat(Zstd.decompress(Arrays.copyOf(destination, compressedLength), INPUT.length))
                    .isEqualTo(INPUT);

            ByteBuffer source = ByteBuffer.allocateDirect(INPUT.length + 2);
            source.position(1).put(INPUT).flip();
            source.position(1);
            ByteBuffer output = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length) + 8);
            int length = context.compress(output, source);
            assertThat(length).isPositive();
            assertThat(Zstd.decompress(output.flip(), INPUT.length)).isNotNull();

            ByteBuffer convenience = context.compress(directBuffer(INPUT));
            byte[] convenienceCompressed = bytes(convenience.duplicate());
            assertThat(Zstd.decompress(convenienceCompressed, INPUT.length)).isEqualTo(INPUT);

            ByteBuffer directOutput = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length) + 8);
            ByteBuffer directSource = directBuffer(INPUT);
            assertThat(context.compressDirectByteBuffer(directOutput, 0, directOutput.capacity(), directSource, 0,
                    INPUT.length)).isPositive();
        } finally {
            context.close();
        }
    }

    @Test
    void compressionContextLoadsBothDictionaryFormsAndStreamsAnEndDirective() {
        try (ZstdDictCompress dictionary = new ZstdDictCompress(DICT, 0, DICT.length, 3)) {
            ZstdCompressCtx context = new ZstdCompressCtx();
            try {
                assertThat(context.loadDict(DICT)).isSameAs(context);
                assertThat(context.loadDict(dictionary)).isSameAs(context);
                context.reset();
                context.setPledgedSrcSize(INPUT.length);
                ByteBuffer source = directBuffer(INPUT);
                ByteBuffer output = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length) + 32);
                assertThat(context.compressDirectByteBufferStream(source, output, EndDirective.END)).isTrue();
                byte[] frame = bytes(output.flip());
                assertThat(frame).isNotEmpty();
            } finally {
                context.close();
            }
        }
    }

    @Test
    void decompressionContextSupportsArraysBuffersStreamsAndReset() {
        byte[] compressed = Zstd.compress(INPUT);
        ZstdDecompressCtx context = new ZstdDecompressCtx();
        try {
            assertThat(context.setMagicless(false)).isSameAs(context);
            byte[] restored = context.decompress(compressed, INPUT.length);
            assertThat(restored).isEqualTo(INPUT);
            byte[] destination = new byte[INPUT.length];
            assertThat(context.decompress(destination, compressed)).isEqualTo(INPUT.length);

            ByteBuffer source = directBuffer(compressed);
            ByteBuffer output = ByteBuffer.allocateDirect(INPUT.length);
            assertThat(context.decompress(output, source)).isEqualTo(INPUT.length);
            assertThat(bytes(output.flip())).isEqualTo(INPUT);

            ByteBuffer returned = context.decompress(directBuffer(compressed), INPUT.length);
            assertThat(bytes(returned)).isEqualTo(INPUT);

            ByteBuffer directSource = directBuffer(compressed);
            ByteBuffer directOutput = ByteBuffer.allocateDirect(INPUT.length + 3);
            assertThat(context.decompressDirectByteBuffer(directOutput, 1, INPUT.length, directSource, 0,
                    compressed.length)).isEqualTo(INPUT.length);
            directOutput.position(1);
            byte[] offsetResult = new byte[INPUT.length];
            directOutput.get(offsetResult);
            assertThat(offsetResult).isEqualTo(INPUT);

            context.reset();
            ByteBuffer streamSource = directBuffer(compressed);
            ByteBuffer streamOutput = ByteBuffer.allocateDirect(131072);
            assertThat(context.decompressDirectByteBufferStream(streamOutput, streamSource)).isTrue();
            assertThat(bytes(streamOutput.flip())).isEqualTo(INPUT);
            context.reset();
            assertThat(context.setMagicless(true)).isSameAs(context);
            assertThat(context.setMagicless(false)).isSameAs(context);
        } finally {
            context.close();
        }
    }

    @Test
    void decompressionContextLoadsDictionaryObjectsAndBytes() {
        byte[] compressed = Zstd.compressUsingDict(INPUT, DICT, 3);
        try (ZstdDictDecompress dictionary = new ZstdDictDecompress(DICT, 0, DICT.length)) {
            ZstdDecompressCtx context = new ZstdDecompressCtx();
            try {
                assertThat(context.loadDict(DICT)).isSameAs(context);
                assertThat(context.loadDict(dictionary)).isSameAs(context);
                assertThat(context.decompress(compressed, INPUT.length)).isEqualTo(INPUT);
                context.reset();
                assertThat(context.loadDict(dictionary).decompress(compressed, INPUT.length)).isEqualTo(INPUT);
            } finally {
                context.close();
            }
        }
    }

    @Test
    void closeIsIdempotentForContextsAndDictionaries() {
        ZstdCompressCtx compressContext = new ZstdCompressCtx();
        assertThatCode(() -> {
            compressContext.close();
            compressContext.close();
        }).doesNotThrowAnyException();

        ZstdDecompressCtx decompressContext = new ZstdDecompressCtx();
        assertThatCode(() -> {
            decompressContext.close();
            decompressContext.close();
        }).doesNotThrowAnyException();

        ZstdDictCompress compressDictionary = new ZstdDictCompress(DICT, 3);
        assertThatCode(() -> {
            compressDictionary.close();
            compressDictionary.close();
        }).doesNotThrowAnyException();

        ZstdDictDecompress decompressDictionary = new ZstdDictDecompress(DICT);
        assertThatCode(() -> {
            decompressDictionary.close();
            decompressDictionary.close();
        }).doesNotThrowAnyException();
    }

    @Test
    void frameProgressionExposesItsConstructorValues() {
        ZstdFrameProgression progression = new ZstdFrameProgression(11, 12, 13, 14, 15, 16);
        assertThat(progression.getIngested()).isEqualTo(11);
        assertThat(progression.getConsumed()).isEqualTo(12);
        assertThat(progression.getProduced()).isEqualTo(13);
        assertThat(progression.getFlushed()).isEqualTo(14);
        assertThat(progression.getCurrentJobID()).isEqualTo(15);
        assertThat(progression.getNbActiveWorkers()).isEqualTo(16);
    }

    private static ByteBuffer directBuffer(byte[] bytes) {
        return ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }
}
