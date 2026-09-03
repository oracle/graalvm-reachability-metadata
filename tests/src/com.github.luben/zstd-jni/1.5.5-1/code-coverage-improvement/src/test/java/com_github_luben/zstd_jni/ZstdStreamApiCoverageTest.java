/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.NoPool;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import com.github.luben.zstd.ZstdOutputStream;
import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ZstdStreamApiCoverageTest {
    private static final byte[] INPUT = ("streaming zstd data uses real records and exercises both single-byte "
            + "and bulk IO methods. ").repeat(12).getBytes(StandardCharsets.UTF_8);
    private static final byte[] DICT = ("streaming zstd dictionary records and repeated words. ").repeat(12)
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void outputStreamConstructorsAndConfigurationProduceReadableFrames() throws Exception {
        assertThat(ZstdOutputStream.recommendedCOutSize()).isPositive();
        assertThat(ZstdOutputStreamNoFinalizer.recommendedCOutSize()).isPositive();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZstdDictCompress dictionary = new ZstdDictCompress(DICT, 3);
        try (ZstdOutputStream stream = new ZstdOutputStream(bytes, NoPool.INSTANCE, 3)) {
            assertThat(stream.setChecksum(true)).isSameAs(stream);
            assertThat(stream.setLevel(4)).isSameAs(stream);
            assertThat(stream.setLong(20)).isSameAs(stream);
            assertThat(stream.setWorkers(0)).isSameAs(stream);
            assertThat(stream.setCloseFrameOnFlush(false)).isSameAs(stream);
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            assertThat(stream.setDict(dictionary)).isSameAs(stream);
            stream.setFinalize(false);
            stream.write(INPUT, 0, INPUT.length / 2);
            stream.write(INPUT[INPUT.length / 2]);
            stream.flush();
            stream.write(INPUT, INPUT.length / 2 + 1, INPUT.length - INPUT.length / 2 - 1);
        }
        try (ZstdDictDecompress decompressor = new ZstdDictDecompress(DICT)) {
            assertThat(Zstd.decompress(bytes.toByteArray(), decompressor, INPUT.length)).isEqualTo(INPUT);
        }

        exerciseOutputConstructor(0);
        exerciseOutputConstructor(1);
        exerciseOutputConstructor(2);
        exerciseOutputConstructor(3);
        exerciseOutputConstructor(4);
        exerciseOutputConstructor(5);
    }

    @Test
    void noFinalizerOutputStreamSupportsParentOwnershipAndDictionary() throws Exception {
        ByteArrayOutputStream parent = new ByteArrayOutputStream();
        ZstdOutputStreamNoFinalizer stream = new ZstdOutputStreamNoFinalizer(parent, NoPool.INSTANCE, 3);
        assertThat(stream.setChecksum(true)).isSameAs(stream);
        assertThat(stream.setLevel(3)).isSameAs(stream);
        assertThat(stream.setLong(18)).isSameAs(stream);
        assertThat(stream.setWorkers(0)).isSameAs(stream);
        assertThat(stream.setCloseFrameOnFlush(false)).isSameAs(stream);
        stream.setDict(DICT);
        stream.write(INPUT, 0, INPUT.length);
        stream.write('!');
        stream.flush();
        stream.closeWithoutClosingParentStream();
        parent.write(7);
        assertThat(parent.toByteArray()).isNotEmpty();

        try (ZstdOutputStreamNoFinalizer second = new ZstdOutputStreamNoFinalizer(
                new ByteArrayOutputStream())) {
            ZstdDictCompress dictionary = new ZstdDictCompress(DICT, 0, DICT.length, 3);
            try {
                assertThat(second.setDict(dictionary)).isSameAs(second);
                second.write(INPUT, 0, INPUT.length);
            } finally {
                dictionary.close();
            }
        }
        exerciseNoFinalizerOutputConstructor(0);
        exerciseNoFinalizerOutputConstructor(1);
        exerciseNoFinalizerOutputConstructor(2);
        exerciseNoFinalizerOutputConstructor(3);
    }

    @Test
    void inputStreamsReadSkipAndConfigureFrames() throws Exception {
        byte[] compressed = Zstd.compress(INPUT);
        assertThat(ZstdInputStream.recommendedDInSize()).isPositive();
        assertThat(ZstdInputStream.recommendedDOutSize()).isPositive();

        try (ZstdInputStream stream = new ZstdInputStream(new ByteArrayInputStream(compressed), NoPool.INSTANCE)) {
            assertThat(stream.setContinuous(true)).isSameAs(stream);
            assertThat(stream.getContinuous()).isTrue();
            stream.setFinalize(false);
            assertThat(stream.setLongMax(20)).isSameAs(stream);
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            try (ZstdDictDecompress dictionary = new ZstdDictDecompress(DICT)) {
                assertThat(stream.setDict(dictionary)).isSameAs(stream);
            }
            assertThat(stream.available()).isGreaterThanOrEqualTo(0);
            assertThat(stream.markSupported()).isFalse();
            assertThat(stream.read()).isEqualTo(INPUT[0] & 0xff);
            byte[] rest = stream.readAllBytes();
            assertThat(concat((byte) INPUT[0], rest)).isEqualTo(INPUT);
        }

        try (ZstdInputStream skipped = new ZstdInputStream(new ByteArrayInputStream(compressed))) {
            assertThat(skipped.skip(7)).isEqualTo(7);
            byte[] rest = skipped.readAllBytes();
            assertThat(rest).isEqualTo(Arrays.copyOfRange(INPUT, 7, INPUT.length));
        }
        exerciseInputBulkRead(compressed);
        exerciseInputConstructor(new ByteArrayInputStream(compressed), 0);
        exerciseInputConstructor(new ByteArrayInputStream(compressed), 1);
    }

    @Test
    void noFinalizerInputStreamsMirrorInputBehavior() throws Exception {
        byte[] compressed = Zstd.compress(INPUT);
        assertThat(ZstdInputStreamNoFinalizer.recommendedDInSize()).isPositive();
        assertThat(ZstdInputStreamNoFinalizer.recommendedDOutSize()).isPositive();
        try (ZstdInputStreamNoFinalizer stream = new ZstdInputStreamNoFinalizer(
                new ByteArrayInputStream(compressed), NoPool.INSTANCE)) {
            assertThat(stream.setContinuous(true)).isSameAs(stream);
            assertThat(stream.getContinuous()).isTrue();
            assertThat(stream.setLongMax(20)).isSameAs(stream);
            assertThat(stream.setDict(DICT)).isSameAs(stream);
            try (ZstdDictDecompress dictionary = new ZstdDictDecompress(DICT)) {
                assertThat(stream.setDict(dictionary)).isSameAs(stream);
            }
            assertThat(stream.available()).isGreaterThanOrEqualTo(0);
            assertThat(stream.markSupported()).isFalse();
            assertThat(stream.read()).isEqualTo(INPUT[0] & 0xff);
            byte[] rest = new byte[INPUT.length - 1];
            int read = stream.read(rest, 0, rest.length);
            assertThat(read).isEqualTo(rest.length);
            assertThat(concat((byte) INPUT[0], rest)).isEqualTo(INPUT);
        }
        try (ZstdInputStreamNoFinalizer skipped = new ZstdInputStreamNoFinalizer(
                new ByteArrayInputStream(compressed))) {
            assertThat(skipped.skip(5)).isEqualTo(5);
            assertThat(skipped.readAllBytes()).isEqualTo(Arrays.copyOfRange(INPUT, 5, INPUT.length));
        }
        exerciseNoFinalizerInputConstructor(new ByteArrayInputStream(compressed), 0);
        exerciseNoFinalizerInputConstructor(new ByteArrayInputStream(compressed), 1);
    }

    private static void exerciseOutputConstructor(int kind) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZstdOutputStream stream;
        switch (kind) {
            case 0 -> stream = new ZstdOutputStream(bytes);
            case 1 -> stream = new ZstdOutputStream(bytes, 3);
            case 2 -> stream = new ZstdOutputStream(bytes, 3, false);
            case 3 -> stream = new ZstdOutputStream(bytes, 3, false, false);
            case 4 -> stream = new ZstdOutputStream(bytes, NoPool.INSTANCE);
            default -> stream = new ZstdOutputStream(bytes, NoPool.INSTANCE, 3);
        }
        try (stream) {
            stream.write(INPUT, 0, INPUT.length);
        }
        assertThat(Zstd.decompress(bytes.toByteArray(), INPUT.length)).isEqualTo(INPUT);
    }

    private static void exerciseNoFinalizerOutputConstructor(int kind) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZstdOutputStreamNoFinalizer stream;
        switch (kind) {
            case 0 -> stream = new ZstdOutputStreamNoFinalizer(bytes);
            case 1 -> stream = new ZstdOutputStreamNoFinalizer(bytes, 3);
            case 2 -> stream = new ZstdOutputStreamNoFinalizer(bytes, NoPool.INSTANCE);
            default -> stream = new ZstdOutputStreamNoFinalizer(bytes, NoPool.INSTANCE, 3);
        }
        try (stream) {
            stream.write(INPUT, 0, INPUT.length);
        }
        assertThat(Zstd.decompress(bytes.toByteArray(), INPUT.length)).isEqualTo(INPUT);
    }

    private static void exerciseInputConstructor(ByteArrayInputStream input, int kind) throws IOException {
        ZstdInputStream stream = kind == 0 ? new ZstdInputStream(input) : new ZstdInputStream(input, NoPool.INSTANCE);
        try (stream) {
            byte[] output = new byte[INPUT.length];
            assertThat(stream.read(output, 0, output.length)).isEqualTo(output.length);
            assertThat(output).isEqualTo(INPUT);
        }
    }

    private static void exerciseNoFinalizerInputConstructor(ByteArrayInputStream input, int kind) throws IOException {
        ZstdInputStreamNoFinalizer stream = kind == 0
                ? new ZstdInputStreamNoFinalizer(input)
                : new ZstdInputStreamNoFinalizer(input, NoPool.INSTANCE);
        try (stream) {
            byte[] output = new byte[INPUT.length];
            assertThat(stream.read(output, 0, output.length)).isEqualTo(output.length);
            assertThat(output).isEqualTo(INPUT);
        }
    }

    private static void exerciseInputBulkRead(byte[] compressed) throws IOException {
        try (ZstdInputStream stream = new ZstdInputStream(new ByteArrayInputStream(compressed))) {
            byte[] output = new byte[INPUT.length + 4];
            assertThat(stream.read(output, 2, INPUT.length)).isEqualTo(INPUT.length);
            assertThat(Arrays.copyOfRange(output, 2, 2 + INPUT.length)).isEqualTo(INPUT);
        }
    }

    private static byte[] concat(byte first, byte[] rest) {
        byte[] result = new byte[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }
}
