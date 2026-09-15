/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Checksum;

import org.junit.jupiter.api.Test;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

class Lz4BlockStreamApiTest {
    private static final byte[] INPUT = createInput();
    private static final int SEED = 0x9747b28c;

    @Test
    void blockOutputConstructorsAndLifecycleWriteValidStream() throws IOException {
        ByteArrayOutputStream defaultBytes = new ByteArrayOutputStream();
        LZ4BlockOutputStream defaultStream = new LZ4BlockOutputStream(defaultBytes);
        defaultStream.write(INPUT);
        defaultStream.close();
        assertThat(readAll(new LZ4BlockInputStream(new ByteArrayInputStream(defaultBytes.toByteArray()))))
                .isEqualTo(INPUT);

        ByteArrayOutputStream sizedBytes = new ByteArrayOutputStream();
        LZ4BlockOutputStream sizedStream = new LZ4BlockOutputStream(sizedBytes, 64);
        sizedStream.write(INPUT[0]);
        sizedStream.write(INPUT, 1, INPUT.length - 1);
        sizedStream.flush();
        assertThat(sizedStream.toString()).contains("blockSize=64");
        sizedStream.finish();
        assertThat(readAll(new LZ4BlockInputStream(new ByteArrayInputStream(sizedBytes.toByteArray()))))
                .isEqualTo(INPUT);

        ByteArrayOutputStream compressorBytes = new ByteArrayOutputStream();
        LZ4BlockOutputStream compressorStream = new LZ4BlockOutputStream(compressorBytes, 64,
                LZ4Factory.safeInstance().fastCompressor());
        compressorStream.write(INPUT, 0, INPUT.length);
        compressorStream.finish();
        assertThat(readAll(new LZ4BlockInputStream(new ByteArrayInputStream(compressorBytes.toByteArray()))))
                .isEqualTo(INPUT);

        ByteArrayOutputStream customBytes = new ByteArrayOutputStream();
        Checksum outputChecksum = checksum();
        LZ4BlockOutputStream customStream = new LZ4BlockOutputStream(customBytes, 64,
                LZ4Factory.safeInstance().fastCompressor(), outputChecksum, true);
        customStream.write(INPUT);
        customStream.flush();
        customStream.close();
        LZ4BlockInputStream customInput = new LZ4BlockInputStream(new ByteArrayInputStream(customBytes.toByteArray()),
                LZ4Factory.safeInstance().fastDecompressor(), checksum(), true);
        assertThat(readAll(customInput)).isEqualTo(INPUT);
        assertThat(customStream.toString()).contains("compressor=");
    }

    @Test
    @SuppressWarnings("deprecation")
    void blockInputConstructorsSupportReadingAndNavigation() throws IOException {
        byte[] encoded = encode(INPUT);
        LZ4BlockInputStream defaultInput = new LZ4BlockInputStream(new ByteArrayInputStream(encoded));
        assertThat(defaultInput.available()).isZero();
        byte[] first = new byte[7];
        assertThat(defaultInput.read(first)).isEqualTo(7);
        assertThat(first).isEqualTo(Arrays.copyOf(INPUT, 7));
        assertThat(defaultInput.markSupported()).isFalse();
        defaultInput.mark(20);
        assertThatThrownBy(defaultInput::reset).isInstanceOf(IOException.class);
        assertThat(defaultInput.toString()).contains("LZ4BlockInputStream");
        byte[] remainder = new byte[INPUT.length - 7];
        int offset = 0;
        while (offset < remainder.length) {
            int read = defaultInput.read(remainder, offset, remainder.length - offset);
            assertThat(read).isPositive();
            offset += read;
        }
        assertThat(concat(first, remainder)).isEqualTo(INPUT);
        assertThat(defaultInput.read()).isEqualTo(-1);

        LZ4BlockInputStream stopOnEmpty = new LZ4BlockInputStream(new ByteArrayInputStream(encoded), true);
        assertThat(readAll(stopOnEmpty)).isEqualTo(INPUT);

        LZ4BlockInputStream fastInput = new LZ4BlockInputStream(new ByteArrayInputStream(encoded),
                LZ4Factory.safeInstance().fastDecompressor());
        assertThat(readAll(fastInput)).isEqualTo(INPUT);

        LZ4BlockInputStream checksumInput = new LZ4BlockInputStream(new ByteArrayInputStream(encoded),
                LZ4Factory.safeInstance().fastDecompressor(), checksum());
        assertThat(readAll(checksumInput)).isEqualTo(INPUT);

        LZ4BlockInputStream configuredInput = new LZ4BlockInputStream(new ByteArrayInputStream(encoded),
                LZ4Factory.safeInstance().fastDecompressor(), checksum(), false);
        assertThat(configuredInput.skip(5)).isEqualTo(5);
        assertThat(configuredInput.read()).isEqualTo(INPUT[5] & 0xff);
        assertThat(readAll(configuredInput)).isEqualTo(Arrays.copyOfRange(INPUT, 6, INPUT.length));
    }

    @Test
    void blockInputBuilderConfiguresSafeFastChecksumAndEndBehavior() throws IOException {
        byte[] encoded = encode(INPUT);
        LZ4BlockInputStream.Builder builder = LZ4BlockInputStream.newBuilder();
        assertThat(builder.withStopOnEmptyBlock(false)).isSameAs(builder);
        assertThat(builder.withDecompressor(LZ4Factory.safeInstance().safeDecompressor())).isSameAs(builder);
        assertThat(builder.withDecompressor(LZ4Factory.safeInstance().fastDecompressor())).isSameAs(builder);
        assertThat(builder.withChecksum(checksum())).isSameAs(builder);
        assertThat(readAll(builder.build(new ByteArrayInputStream(encoded)))).isEqualTo(INPUT);

        LZ4BlockInputStream.Builder safeBuilder = LZ4BlockInputStream.newBuilder();
        safeBuilder.withDecompressor(LZ4Factory.safeInstance().safeDecompressor());
        assertThat(readAll(safeBuilder.build(new ByteArrayInputStream(encoded)))).isEqualTo(INPUT);

        LZ4BlockInputStream.Builder defaultBuilder = LZ4BlockInputStream.newBuilder();
        assertThat(readAll(defaultBuilder.build(new ByteArrayInputStream(encoded)))).isEqualTo(INPUT);
    }

    private static byte[] encode(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (LZ4BlockOutputStream stream = new LZ4BlockOutputStream(output, 64)) {
            stream.write(input);
        }
        return output.toByteArray();
    }

    private static byte[] readAll(LZ4BlockInputStream input) throws IOException {
        try (LZ4BlockInputStream stream = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[11];
            int read;
            while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static Checksum checksum() {
        return XXHashFactory.fastestInstance().newStreamingHash32(SEED).asChecksum();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] createInput() {
        byte[] input = new byte[193];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) ('a' + (i % 5));
        }
        return input;
    }
}
