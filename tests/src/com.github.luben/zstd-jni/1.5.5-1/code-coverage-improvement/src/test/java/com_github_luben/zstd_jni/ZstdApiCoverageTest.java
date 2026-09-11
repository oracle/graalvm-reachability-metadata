/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdException;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.util.ZstdVersion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZstdApiCoverageTest {
    private static final byte[] INPUT = ("zstd-jni exposes a useful compression API; "
            + "the same payload is repeated so dictionary and streaming paths have work to do. ").repeat(8)
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] DICT = ("zstd dictionary words: compression API coverage and realistic repeated records. ")
            .repeat(8).getBytes(StandardCharsets.UTF_8);

    @Test
    void arrayApisSupportOffsetsDictionariesAndSizeQueries() {
        new Zstd();
        byte[] compressed = Zstd.compress(INPUT);
        byte[] destination = new byte[(int) Zstd.compressBound(INPUT.length) + 32];
        long compressedLength = Zstd.compress(destination, INPUT, 3);
        assertRoundTrip(Arrays.copyOf(destination, (int) compressedLength), INPUT);
        compressedLength = Zstd.compress(destination, INPUT, 3, true);
        assertRoundTrip(Arrays.copyOf(destination, (int) compressedLength), INPUT);

        byte[] offsetDestination = new byte[destination.length + 7];
        long offsetLength = Zstd.compressByteArray(offsetDestination, 3, offsetDestination.length - 3,
                INPUT, 2, INPUT.length - 2, 4);
        assertThat(offsetLength).isPositive();
        byte[] restored = new byte[INPUT.length - 2];
        long restoredLength = Zstd.decompressByteArray(restored, 0, restored.length,
                offsetDestination, 3, (int) offsetLength);
        assertThat(restoredLength).isEqualTo(restored.length);
        assertThat(restored).isEqualTo(Arrays.copyOfRange(INPUT, 2, INPUT.length));

        offsetLength = Zstd.compressByteArray(offsetDestination, 3, offsetDestination.length - 3,
                INPUT, 2, INPUT.length - 2, 4, true);
        assertThat(offsetLength).isPositive();
        restoredLength = Zstd.decompressByteArray(restored, 0, restored.length,
                offsetDestination, 3, (int) offsetLength);
        assertThat(restoredLength).isEqualTo(restored.length);

        byte[] dictDestination = new byte[destination.length];
        long dictLength = Zstd.compress(dictDestination, INPUT, DICT, 3);
        assertThat(decompressInto(dictDestination, (int) dictLength, DICT)).isEqualTo(INPUT);
        dictLength = Zstd.compressUsingDict(dictDestination, INPUT, DICT, 3);
        assertThat(decompressInto(dictDestination, (int) dictLength, DICT)).isEqualTo(INPUT);

        dictLength = Zstd.compressUsingDict(dictDestination, 2, INPUT, 1, DICT, 3);
        assertThat(decompressUsingDict(dictDestination, 2, (int) dictLength, INPUT.length - 1, DICT))
                .isEqualTo(Arrays.copyOfRange(INPUT, 1, INPUT.length));
        dictLength = Zstd.compressUsingDict(dictDestination, 2, INPUT, 1, INPUT.length - 1, DICT, 3);
        assertThat(decompressUsingDict(dictDestination, 2, (int) dictLength, INPUT.length - 1, DICT))
                .isEqualTo(Arrays.copyOfRange(INPUT, 1, INPUT.length));

        assertThat(Zstd.decompressedSize(compressed)).isEqualTo(INPUT.length);
        assertThat(Zstd.decompressedSize(compressed, 0)).isEqualTo(INPUT.length);
        assertThat(Zstd.decompressedSize(compressed, 0, compressed.length)).isEqualTo(INPUT.length);
        assertThat(Zstd.decompressedSize(compressed, 0, compressed.length, false)).isEqualTo(INPUT.length);
        assertThat(Zstd.decompressedSize(directBuffer(compressed))).isEqualTo(INPUT.length);
        assertThat(Zstd.isError(Zstd.getErrorCode(Zstd.errNoError()))).isFalse();
    }

    @Test
    void convenienceAndByteBufferApisRoundTripData() {
        byte[] compressed = Zstd.compress(INPUT, 5);
        assertThat(Zstd.decompress(compressed, INPUT.length)).isEqualTo(INPUT);
        assertThat(Zstd.decompress(new byte[INPUT.length], compressed)).isEqualTo(INPUT.length);
        byte[] dictionaryCompressed = Zstd.compressUsingDict(INPUT, DICT, 3);
        assertThat(Zstd.decompress(dictionaryCompressed, DICT, INPUT.length)).isEqualTo(INPUT);
        assertThat(Zstd.decompress(new byte[INPUT.length], dictionaryCompressed, DICT)).isEqualTo(INPUT.length);
        assertThat(Zstd.decompressUsingDict(new byte[INPUT.length], dictionaryCompressed, DICT))
                .isEqualTo(INPUT.length);

        ByteBuffer source = ByteBuffer.allocateDirect(INPUT.length);
        source.put(INPUT).flip();
        ByteBuffer encoded = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length) + 8);
        int length = Zstd.compress(encoded, source);
        assertThat(length).isPositive();
        ByteBuffer output = ByteBuffer.allocateDirect(INPUT.length);
        assertThat(Zstd.decompress(output, encoded.flip())).isEqualTo(INPUT.length);
        output.flip();
        assertThat(bytes(output)).isEqualTo(INPUT);

        ByteBuffer heapSource = directBuffer(INPUT);
        ByteBuffer heapDestination = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length));
        assertThat(Zstd.compress(heapDestination, heapSource, 3)).isPositive();
        assertThat(Zstd.compress(ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length)),
                directBuffer(INPUT), 3, true)).isPositive();
        assertThat(Zstd.compress(ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length)),
                directBuffer(INPUT), DICT, 3)).isPositive();
        assertThat(Zstd.compress(directBuffer(INPUT), DICT, 3)).isNotNull();
        ByteBuffer compressedBuffer = Zstd.compress(directBuffer(INPUT), 3);
        assertThat(bytes(Zstd.decompress(compressedBuffer, INPUT.length))).isEqualTo(INPUT);
        ByteBuffer outputWithDict = ByteBuffer.allocateDirect(INPUT.length);
        ByteBuffer dictInput = directBuffer(dictionaryCompressed);
        assertThat(Zstd.decompress(outputWithDict, dictInput, DICT)).isEqualTo(INPUT.length);
        assertThat(bytes(outputWithDict.flip())).isEqualTo(INPUT);

        byte[] dictDestination = new byte[(int) Zstd.compressBound(INPUT.length) + 8];
        ZstdDictCompress compressDict = new ZstdDictCompress(DICT, 3);
        ZstdDictDecompress decompressDict = new ZstdDictDecompress(DICT);
        try {
            byte[] convenienceDictionaryResult = Zstd.compress(INPUT, compressDict);
            assertThat(Zstd.decompress(convenienceDictionaryResult, decompressDict, INPUT.length))
                    .isEqualTo(INPUT);
            assertThat(Zstd.compress(dictDestination, INPUT, compressDict)).isPositive();
            int objectLength = (int) Zstd.compress(dictDestination, INPUT, compressDict);
            assertThat(decompressInto(dictDestination, objectLength, DICT)).isEqualTo(INPUT);
            ByteBuffer dictEncoded = Zstd.compress(directBuffer(INPUT), compressDict);
            assertThat(bytes(Zstd.decompress(dictEncoded, decompressDict, INPUT.length))).isEqualTo(INPUT);
            assertThat(Zstd.decompress(Zstd.compressUsingDict(INPUT, DICT, 3), decompressDict, INPUT.length))
                    .isEqualTo(INPUT);
            ByteBuffer arrayDictionaryResult = Zstd.decompress(
                    directBuffer(Zstd.compressUsingDict(INPUT, DICT, 3)), DICT, INPUT.length);
            assertThat(bytes(arrayDictionaryResult)).isEqualTo(INPUT);

            long fastLength = Zstd.compressFastDict(dictDestination, 2, INPUT, 1, compressDict);
            assertThat(fastLength).isPositive();
            long fullFastLength = Zstd.compressFastDict(dictDestination, 2, INPUT, 1, INPUT.length - 1,
                    compressDict);
            assertThat(fullFastLength).isPositive();
            byte[] fastRestored = new byte[INPUT.length - 1];
            assertThat(Zstd.decompressFastDict(fastRestored, 0, dictDestination, 2, (int) fullFastLength,
                    decompressDict)).isEqualTo(fastRestored.length);
            assertThat(fastRestored).isEqualTo(Arrays.copyOfRange(INPUT, 1, INPUT.length));
                ByteBuffer dictOutput = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length));
            assertThat(Zstd.compress(dictOutput, directBuffer(INPUT), compressDict)).isPositive();
            dictOutput.flip();
            ByteBuffer restored = ByteBuffer.allocateDirect(INPUT.length);
            assertThat(Zstd.decompress(restored, dictOutput, decompressDict)).isEqualTo(INPUT.length);
            assertThat(bytes(restored.flip())).isEqualTo(INPUT);

            ByteBuffer directEncoded = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length) + 8);
            long directLength = Zstd.compressDirectByteBufferUsingDict(directEncoded, 2,
                    directEncoded.capacity() - 2, directBuffer(INPUT), 0, INPUT.length, DICT, 3);
            assertThat(directLength).isPositive();
            ByteBuffer directRestored = ByteBuffer.allocateDirect(INPUT.length);
            assertThat(Zstd.decompressDirectByteBufferUsingDict(directRestored, 0, directRestored.capacity(),
                    directEncoded, 2, (int) directLength, DICT)).isEqualTo(INPUT.length);
            directRestored.position(INPUT.length);
            assertThat(bytes(directRestored.flip())).isEqualTo(INPUT);

            directLength = Zstd.compressDirectByteBufferFastDict(directEncoded, 2,
                    directEncoded.capacity() - 2, directBuffer(INPUT), 0, INPUT.length, compressDict);
            assertThat(directLength).isPositive();
            directRestored.clear();
            assertThat(Zstd.decompressDirectByteBufferFastDict(directRestored, 0, directRestored.capacity(),
                    directEncoded, 2, (int) directLength, decompressDict)).isEqualTo(INPUT.length);
            directRestored.position(INPUT.length);
            assertThat(bytes(directRestored.flip())).isEqualTo(INPUT);
        } finally {
            compressDict.close();
            decompressDict.close();
        }
    }

    @Test
    void directAndUnsafeApisRoundTripData() throws Exception {
        ByteBuffer source = ByteBuffer.allocateDirect(INPUT.length + 3);
        source.position(1).put(INPUT);
        ByteBuffer compressed = ByteBuffer.allocateDirect((int) Zstd.compressBound(INPUT.length) + 8);
        long length = Zstd.compressDirectByteBuffer(compressed, 2, compressed.capacity() - 2, source, 1,
                INPUT.length, 4);
        assertThat(length).isPositive();
        ByteBuffer checksumDestination = ByteBuffer.allocateDirect(compressed.capacity());
        assertThat(Zstd.compressDirectByteBuffer(checksumDestination, 2, checksumDestination.capacity() - 2,
                source, 1, INPUT.length, 4, true)).isPositive();
        ByteBuffer restored = ByteBuffer.allocateDirect(INPUT.length);
        assertThat(Zstd.decompressDirectByteBuffer(restored, 0, restored.capacity(), compressed, 2, (int) length))
                .isEqualTo(INPUT.length);
        restored.position(INPUT.length);
        assertThat(bytes(restored.flip())).isEqualTo(INPUT);
        assertThat(Zstd.decompressedDirectByteBufferSize(compressed, 2, (int) length)).isEqualTo(INPUT.length);

        sun.misc.Unsafe unsafe = unsafe();
        long sourceAddress = unsafe.allocateMemory(INPUT.length);
        long destinationAddress = unsafe.allocateMemory(Zstd.compressBound(INPUT.length));
        long restoredAddress = unsafe.allocateMemory(INPUT.length);
        try {
            long base = unsafe.arrayBaseOffset(byte[].class);
            unsafe.copyMemory(INPUT, base, null, sourceAddress, INPUT.length);
            long unsafeLength = Zstd.compressUnsafe(destinationAddress, Zstd.compressBound(INPUT.length),
                    sourceAddress, INPUT.length, 3);
            assertThat(unsafeLength).isPositive();
            long restoredLength = Zstd.decompressUnsafe(restoredAddress, INPUT.length, destinationAddress,
                    unsafeLength);
            assertThat(restoredLength).isEqualTo(INPUT.length);
            byte[] result = new byte[INPUT.length];
            unsafe.copyMemory(null, restoredAddress, result, base, INPUT.length);
            assertThat(result).isEqualTo(INPUT);
        } finally {
            unsafe.freeMemory(sourceAddress);
            unsafe.freeMemory(destinationAddress);
            unsafe.freeMemory(restoredAddress);
        }
    }

    @Test
    void trainingAndPublicSupportTypesHaveUsefulResults() {
        byte[][] samples = new byte[16][];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = ("record-" + i + " contains a repeated dictionary phrase for training. ")
                    .repeat(4).getBytes(StandardCharsets.UTF_8);
        }
        byte[] dictionary = new byte[1024];
        long trained = Zstd.trainFromBuffer(samples, dictionary);
        assertThat(trained).isPositive();

        ZstdDictTrainer trainer = new ZstdDictTrainer(4096, dictionary.length);
        for (byte[] sample : samples) {
            assertThat(trainer.addSample(sample)).isTrue();
        }
        assertThat(trainer.trainSamples()).isNotEmpty();
        assertThat(trainer.trainSamples(true)).isNotEmpty();
        assertThat(trainer.trainSamplesDirect()).isNotNull();
        assertThat(trainer.trainSamplesDirect(true)).isNotNull();

        ByteBuffer sampleBuffer = ByteBuffer.allocateDirect(4096);
        int[] sizes = new int[samples.length];
        for (int i = 0; i < samples.length; i++) {
            sampleBuffer.put(samples[i]);
            sizes[i] = samples[i].length;
        }
        sampleBuffer.flip();
        ByteBuffer trainedBuffer = ByteBuffer.allocateDirect(1024);
        assertThat(Zstd.trainFromBufferDirect(sampleBuffer, sizes, trainedBuffer)).isPositive();

        ZstdVersion version = new ZstdVersion();
        assertThat(ZstdVersion.VERSION).isNotBlank();
        assertThat(version).isNotNull();
        assertThat(Native.values()).isEmpty();
        assertThatThrownBy(() -> Native.valueOf("NONE")).isInstanceOf(IllegalArgumentException.class);
        Native.assumeLoaded();
        assertThat(Native.isLoaded()).isTrue();

        assertThat(com.github.luben.zstd.EndDirective.values()).containsExactly(
                com.github.luben.zstd.EndDirective.CONTINUE, com.github.luben.zstd.EndDirective.FLUSH,
                com.github.luben.zstd.EndDirective.END);
        assertThat(com.github.luben.zstd.EndDirective.valueOf("END"))
                .isEqualTo(com.github.luben.zstd.EndDirective.END);
        long genericError = Zstd.errGeneric();
        assertThat(new ZstdException(genericError).getErrorCode()).isEqualTo(Zstd.getErrorCode(genericError));
        assertThat(new ZstdException(8, "bad frame").getErrorCode()).isEqualTo(8);
        assertThat(new ZstdIOException(genericError).getErrorCode()).isEqualTo(Zstd.getErrorCode(genericError));
        assertThat(new ZstdIOException(10, "bad stream").getErrorCode()).isEqualTo(10);
    }

    private static void assertRoundTrip(byte[] compressed, byte[] expected) {
        assertThat(Zstd.decompress(compressed, expected.length)).isEqualTo(expected);
    }

    private static byte[] decompressInto(byte[] compressed, int length, byte[] dictionary) {
        byte[] output = new byte[INPUT.length];
        byte[] frame = Arrays.copyOf(compressed, length);
        assertThat(Zstd.decompress(output, frame, dictionary)).isEqualTo(INPUT.length);
        return output;
    }

    private static byte[] decompressUsingDict(byte[] compressed, int offset, int length, int outputLength,
            byte[] dictionary) {
        byte[] output = new byte[outputLength];
        assertThat(Zstd.decompressUsingDict(output, 0, compressed, offset, length, dictionary))
                .isEqualTo(outputLength);
        return output;
    }

    private static ByteBuffer directBuffer(byte[] bytes) {
        return ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
