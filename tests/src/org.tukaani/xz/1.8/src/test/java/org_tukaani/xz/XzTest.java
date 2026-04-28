/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tukaani.xz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.tukaani.xz.ARMOptions;
import org.tukaani.xz.ArrayCache;
import org.tukaani.xz.BasicArrayCache;
import org.tukaani.xz.DeltaOptions;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.FinishableOutputStream;
import org.tukaani.xz.FinishableWrapperOutputStream;
import org.tukaani.xz.LZMA2InputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;
import org.tukaani.xz.MemoryLimitException;
import org.tukaani.xz.PowerPCOptions;
import org.tukaani.xz.SeekableInputStream;
import org.tukaani.xz.SeekableXZInputStream;
import org.tukaani.xz.SingleXZInputStream;
import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.X86Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;
import org.tukaani.xz.check.Check;

public class XzTest {
    @Test
    void xzStreamRoundTripSupportsMultipleBlocksAndFilterUpdates() throws Exception {
        byte[] firstBlock = patternedBytes(4096);
        byte[] secondBlock = textBytes("second-block", 3000);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        LZMA2Options firstOptions = new LZMA2Options(1);
        firstOptions.setDictSize(1 << 16);
        firstOptions.setNiceLen(32);
        firstOptions.setMode(LZMA2Options.MODE_FAST);

        try (XZOutputStream output = new XZOutputStream(
                compressed,
                firstOptions,
                XZ.CHECK_CRC64,
                BasicArrayCache.getInstance())) {
            output.write(firstBlock, 0, 777);
            for (int i = 777; i < firstBlock.length; i++) {
                output.write(firstBlock[i] & 0xFF);
            }
            output.endBlock();

            DeltaOptions delta = new DeltaOptions(3);
            LZMA2Options secondOptions = new LZMA2Options(2);
            secondOptions.setDictSize(1 << 15);
            output.updateFilters(new FilterOptions[] {delta, secondOptions});
            output.write(secondBlock);
            output.finish();
        }

        byte[] compressedBytes = compressed.toByteArray();
        assertThat(Arrays.copyOf(compressedBytes, XZ.HEADER_MAGIC.length)).containsExactly(XZ.HEADER_MAGIC);
        assertThat(Arrays.copyOfRange(
                compressedBytes,
                compressedBytes.length - XZ.FOOTER_MAGIC.length,
                compressedBytes.length)).containsExactly(XZ.FOOTER_MAGIC);

        byte[] decompressed;
        try (XZInputStream input = new XZInputStream(
                new ByteArrayInputStream(compressedBytes),
                -1,
                BasicArrayCache.getInstance())) {
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            decompressed = readWithSmallBuffers(input);
            assertThat(input.read()).isEqualTo(-1);
        }

        assertThat(decompressed).containsExactly(concat(firstBlock, secondBlock));
    }

    @Test
    void xzInputStreamReadsConcatenatedStreamsWhileSingleStreamStopsAfterFirstStream() throws Exception {
        byte[] first = textBytes("first xz stream ", 512);
        byte[] second = textBytes("second xz stream ", 512);
        byte[] firstCompressed = compressXz(first, XZ.CHECK_CRC32, new LZMA2Options(0));
        byte[] secondCompressed = compressXz(second, XZ.CHECK_SHA256, new LZMA2Options(1));
        byte[] concatenated = concat(firstCompressed, secondCompressed);

        try (XZInputStream input = new XZInputStream(new ByteArrayInputStream(concatenated), -1, true)) {
            assertThat(readWithSmallBuffers(input)).containsExactly(concat(first, second));
        }

        ByteArrayInputStream source = new ByteArrayInputStream(concatenated);
        try (SingleXZInputStream input = new SingleXZInputStream(source)) {
            assertThat(input.getCheckType()).isEqualTo(XZ.CHECK_CRC32);
            assertThat(input.getCheckName()).isEqualTo("CRC32");
            assertThat(readWithSmallBuffers(input)).containsExactly(first);
        }
        assertThat(source.available()).isEqualTo(secondCompressed.length);
    }

    @Test
    void seekableXzInputStreamExposesIndexAndSupportsRandomAccess() throws Exception {
        byte[] firstBlock = textBytes("alpha", 1024);
        byte[] secondBlock = textBytes("beta", 1536);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();

        try (XZOutputStream output = new XZOutputStream(compressed, new LZMA2Options(1), XZ.CHECK_CRC64)) {
            output.write(firstBlock);
            output.endBlock();
            output.write(secondBlock);
            output.finish();
        }

        byte[] expected = concat(firstBlock, secondBlock);
        try (SeekableXZInputStream input = new SeekableXZInputStream(
                new ByteArraySeekableInputStream(compressed.toByteArray()))) {
            assertThat(input.length()).isEqualTo(expected.length);
            assertThat(input.getStreamCount()).isEqualTo(1);
            assertThat(input.getBlockCount()).isEqualTo(2);
            assertThat(input.getCheckTypes() & (1 << XZ.CHECK_CRC64)).isNotZero();
            assertThat(input.getBlockPos(0)).isEqualTo(0L);
            assertThat(input.getBlockPos(1)).isEqualTo(firstBlock.length);
            assertThat(input.getBlockNumber(0)).isEqualTo(0);
            assertThat(input.getBlockNumber(firstBlock.length)).isEqualTo(1);
            assertThat(input.getLargestBlockSize()).isGreaterThanOrEqualTo(secondBlock.length);
            assertThat(input.getIndexMemoryUsage()).isPositive();
            assertThat(input.getBlockCompPos(0)).isGreaterThanOrEqualTo(0L);
            assertThat(input.getBlockCompSize(0)).isPositive();
            assertThat(input.getBlockSize(1)).isEqualTo(secondBlock.length);
            assertThat(input.getBlockCheckType(1)).isEqualTo(XZ.CHECK_CRC64);

            input.seek(firstBlock.length + 17L);
            assertThat(input.position()).isEqualTo(firstBlock.length + 17L);
            byte[] slice = new byte[64];
            assertThat(input.read(slice)).isEqualTo(slice.length);
            assertThat(slice).containsExactly(Arrays.copyOfRange(
                    expected,
                    firstBlock.length + 17,
                    firstBlock.length + 17 + slice.length));

            input.seekToBlock(0);
            assertThat(input.position()).isZero();
            assertThat(readWithSmallBuffers(input)).containsExactly(expected);
        }
    }

    @Test
    void standaloneLzmaRoundTripPreservesPropertiesAndUncompressedSize() throws Exception {
        byte[] data = textBytes("lzma-alone-format", 2048);
        LZMA2Options options = new LZMA2Options();
        options.setDictSize(1 << 16);
        options.setLcLp(3, 0);
        options.setPb(2);
        options.setMode(LZMA2Options.MODE_NORMAL);
        options.setMatchFinder(LZMA2Options.MF_BT4);
        options.setDepthLimit(32);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();

        int props;
        try (LZMAOutputStream output = new LZMAOutputStream(
                compressed,
                options,
                data.length,
                ArrayCache.getDummyCache())) {
            output.write(data, 0, 31);
            output.write(data, 31, data.length - 31);
            props = output.getProps();
            assertThat(output.getUncompressedSize()).isEqualTo(data.length);
            output.finish();
        }

        assertThat(props).isBetween(0, 224);
        assertThat(LZMAInputStream.getMemoryUsage(options.getDictSize(), (byte) props)).isPositive();
        try (LZMAInputStream input = new LZMAInputStream(
                new ByteArrayInputStream(compressed.toByteArray()),
                ArrayCache.getDummyCache())) {
            assertThat(readWithSmallBuffers(input)).containsExactly(data);
        }
    }

    @Test
    void rawDeltaAndLzma2FilterStreamsRoundTripWithoutContainer() throws Exception {
        byte[] original = patternedBytes(3072);
        DeltaOptions delta = new DeltaOptions(5);
        LZMA2Options lzma2 = new LZMA2Options(1);
        lzma2.setDictSize(1 << 15);

        byte[] deltaEncoded = encodeWithFilter(delta, original);
        try (InputStream decoded = delta.getInputStream(
                new ByteArrayInputStream(deltaEncoded),
                ArrayCache.getDummyCache())) {
            assertThat(readWithSmallBuffers(decoded)).containsExactly(original);
        }

        byte[] lzma2Encoded = encodeWithFilter(lzma2, original);
        assertThat(LZMA2InputStream.getMemoryUsage(lzma2.getDictSize())).isPositive();
        try (LZMA2InputStream decoded = new LZMA2InputStream(
                new ByteArrayInputStream(lzma2Encoded),
                lzma2.getDictSize())) {
            assertThat(readWithSmallBuffers(decoded)).containsExactly(original);
        }
    }

    @Test
    void powerPcBcjFilterTranslatesBranchTargetsAndRoundTrips() throws Exception {
        byte[] machineCode = new byte[] {
                0x60, 0x00, 0x00, 0x00,
                0x48, 0x00, 0x00, 0x01,
                0x48, 0x00, 0x00, 0x01,
                0x4E, (byte) 0x80, 0x00, 0x20
        };
        PowerPCOptions options = new PowerPCOptions();
        options.setStartOffset(16);

        byte[] encoded = encodeWithFilter(options, machineCode);

        assertThat(encoded).isNotEqualTo(machineCode);
        assertThat(encoded).containsExactly(
                0x60, 0x00, 0x00, 0x00,
                0x48, 0x00, 0x00, 0x15,
                0x48, 0x00, 0x00, 0x19,
                0x4E, (byte) 0x80, 0x00, 0x20);
        try (InputStream decoded = options.getInputStream(
                new ByteArrayInputStream(encoded),
                ArrayCache.getDummyCache())) {
            assertThat(readWithSmallBuffers(decoded)).containsExactly(machineCode);
        }
    }

    @Test
    void standaloneIntegrityChecksProduceStandardValuesAndCanBeReused() throws Exception {
        byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

        Check noCheck = Check.getInstance(XZ.CHECK_NONE);
        assertThat(noCheck.getName()).isEqualTo("None");
        assertThat(noCheck.getSize()).isZero();
        noCheck.update(data);
        assertThat(noCheck.finish()).isEmpty();

        Check crc32 = Check.getInstance(XZ.CHECK_CRC32);
        assertThat(crc32.getName()).isEqualTo("CRC32");
        assertThat(crc32.getSize()).isEqualTo(4);
        crc32.update(data, 0, 4);
        crc32.update(data, 4, data.length - 4);
        assertThat(crc32.finish()).containsExactly(0x26, 0x39, (byte) 0xF4, (byte) 0xCB);
        crc32.update(data);
        assertThat(crc32.finish()).containsExactly(0x26, 0x39, (byte) 0xF4, (byte) 0xCB);

        Check crc64 = Check.getInstance(XZ.CHECK_CRC64);
        assertThat(crc64.getName()).isEqualTo("CRC64");
        assertThat(crc64.getSize()).isEqualTo(8);
        crc64.update(data, 0, 5);
        crc64.update(data, 5, data.length - 5);
        assertThat(crc64.finish()).containsExactly(
                (byte) 0xFA, 0x39, 0x19, (byte) 0xDF,
                (byte) 0xBB, (byte) 0xC9, 0x5D, (byte) 0x99);

        Check sha256 = Check.getInstance(XZ.CHECK_SHA256);
        assertThat(sha256.getName()).isEqualTo("SHA-256");
        assertThat(sha256.getSize()).isEqualTo(32);
        sha256.update(data, 0, 6);
        sha256.update(data, 6, data.length - 6);
        assertThat(sha256.finish()).containsExactly(
                0x15, (byte) 0xE2, (byte) 0xB0, (byte) 0xD3,
                (byte) 0xC3, 0x38, (byte) 0x91, (byte) 0xEB,
                (byte) 0xB0, (byte) 0xF1, (byte) 0xEF, 0x60,
                (byte) 0x9E, (byte) 0xC4, 0x19, 0x42,
                0x0C, 0x20, (byte) 0xE3, 0x20,
                (byte) 0xCE, (byte) 0x94, (byte) 0xC6, 0x5F,
                (byte) 0xBC, (byte) 0x8C, 0x33, 0x12,
                0x44, (byte) 0x8E, (byte) 0xB2, 0x25);
    }

    @Test
    void optionValidationAndMemoryLimitFailuresExposePublicExceptions() throws Exception {
        LZMA2Options options = new LZMA2Options(1);
        options.setPresetDict("dictionary seed".getBytes(StandardCharsets.UTF_8));
        LZMA2Options cloned = (LZMA2Options) options.clone();
        assertThat(cloned.getPresetDict()).containsExactly(options.getPresetDict());
        FilterOptions[] filterChain = new FilterOptions[] {new X86Options(), new DeltaOptions(), cloned};
        assertThat(FilterOptions.getEncoderMemoryUsage(filterChain)).isPositive();
        assertThat(FilterOptions.getDecoderMemoryUsage(filterChain)).isPositive();

        assertThatThrownBy(() -> new LZMA2Options(LZMA2Options.PRESET_MAX + 1))
                .isInstanceOf(UnsupportedOptionsException.class);
        assertThatThrownBy(() -> new DeltaOptions(DeltaOptions.DISTANCE_MAX + 1))
                .isInstanceOf(UnsupportedOptionsException.class);
        X86Options x86Options = new X86Options();
        x86Options.setStartOffset(-1);
        assertThat(x86Options.getStartOffset()).isEqualTo(-1);
        ARMOptions armOptions = new ARMOptions();
        assertThatThrownBy(() -> armOptions.setStartOffset(2)).isInstanceOf(UnsupportedOptionsException.class);

        LZMA2Options highMemoryOptions = new LZMA2Options(1);
        highMemoryOptions.setDictSize(1 << 20);
        byte[] compressed = compressXz(textBytes("memory-limit", 2048), XZ.CHECK_CRC64, highMemoryOptions);
        assertThatThrownBy(() -> {
            try (XZInputStream input = new XZInputStream(new ByteArrayInputStream(compressed), 1)) {
                readWithSmallBuffers(input);
            }
        }).isInstanceOf(MemoryLimitException.class)
                .extracting(exception -> ((MemoryLimitException) exception).getMemoryLimit())
                .isEqualTo(1);
    }

    private static byte[] compressXz(byte[] data, int checkType, FilterOptions options) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (XZOutputStream output = new XZOutputStream(compressed, options, checkType, ArrayCache.getDummyCache())) {
            output.write(data);
            output.finish();
        }
        return compressed.toByteArray();
    }

    private static byte[] encodeWithFilter(FilterOptions options, byte[] data) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        FinishableOutputStream output = options.getOutputStream(
                new FinishableWrapperOutputStream(encoded),
                ArrayCache.getDummyCache());
        output.write(data);
        output.finish();
        return encoded.toByteArray();
    }

    private static byte[] readWithSmallBuffers(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[113];
        int read;
        while ((read = input.read(buffer, 0, buffer.length)) != -1) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static byte[] patternedBytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 31 + (i >>> 3) + 17) & 0xFF);
        }
        return data;
    }

    private static byte[] textBytes(String prefix, int repetitions) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < repetitions; i++) {
            byte[] part = (prefix + " #" + i + "\n").getBytes(StandardCharsets.UTF_8);
            output.write(part, 0, part.length);
        }
        return output.toByteArray();
    }

    private static final class ByteArraySeekableInputStream extends SeekableInputStream {
        private final byte[] data;
        private int position;

        private ByteArraySeekableInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            if (position == data.length) {
                return -1;
            }
            return data[position++] & 0xFF;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position == data.length) {
                return -1;
            }
            int count = Math.min(length, data.length - position);
            System.arraycopy(data, position, target, offset, count);
            position += count;
            return count;
        }

        @Override
        public long length() {
            return data.length;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public void seek(long newPosition) throws IOException {
            if (newPosition < 0 || newPosition > data.length) {
                throw new IOException("Position is outside the in-memory stream: " + newPosition);
            }
            position = (int) newPosition;
        }
    }
}
