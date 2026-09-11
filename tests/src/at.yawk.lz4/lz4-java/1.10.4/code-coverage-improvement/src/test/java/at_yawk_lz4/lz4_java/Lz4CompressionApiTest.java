/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4CompressorWithLength;
import net.jpountz.lz4.LZ4DecompressorWithLength;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.lz4.LZ4SafeDecompressor;

class Lz4CompressionApiTest {
    private static final byte[] INPUT = ("lz4-java compression API coverage with repeated repeated "
            + "content and a non-zero source offset").getBytes(StandardCharsets.UTF_8);

    @Test
    void compressorConvenienceMethodsRoundTripArrayAndBuffers() {
        LZ4Compressor compressor = LZ4Factory.safeInstance().fastCompressor();
        byte[] source = new byte[INPUT.length + 6];
        System.arraycopy(INPUT, 0, source, 3, INPUT.length);
        byte[] destination = new byte[compressor.maxCompressedLength(INPUT.length) + 8];

        byte[] compressed = compressor.compress(INPUT);
        assertThat(roundTrip(compressor, compressed, INPUT.length)).isEqualTo(INPUT);
        assertThat(roundTrip(compressor, compressor.compress(source, 3, INPUT.length), INPUT.length))
                .isEqualTo(INPUT);

        int compressedLength = compressor.compress(source, 3, INPUT.length, destination, 4);
        assertThat(roundTrip(compressor, Arrays.copyOfRange(destination, 4, 4 + compressedLength), INPUT.length))
                .isEqualTo(INPUT);
        byte[] exactDestination = new byte[compressor.maxCompressedLength(INPUT.length)];
        int exactLength = compressor.compress(INPUT, exactDestination);
        assertThat(roundTrip(compressor, Arrays.copyOf(exactDestination, exactLength), INPUT.length))
                .isEqualTo(INPUT);
        assertThat(compressor.toString()).contains("Compressor");

        ByteBuffer sourceBuffer = ByteBuffer.wrap(INPUT);
        ByteBuffer compressedBuffer = ByteBuffer.allocate(compressor.maxCompressedLength(INPUT.length));
        compressor.compress(sourceBuffer, compressedBuffer);
        assertThat(sourceBuffer.position()).isEqualTo(sourceBuffer.limit());
        compressedBuffer.flip();
        ByteBuffer restored = ByteBuffer.allocate(INPUT.length);
        LZ4Factory.safeInstance().fastDecompressor().decompress(compressedBuffer, restored);
        assertThat(restored.array()).isEqualTo(INPUT);
    }

    @Test
    void fastAndSafeDecompressorsSupportTheirPublicConvenienceForms() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        LZ4Compressor compressor = factory.fastCompressor();
        byte[] compressed = compressor.compress(INPUT);
        LZ4FastDecompressor fast = factory.fastDecompressor();
        LZ4SafeDecompressor safe = factory.safeDecompressor();

        assertThat(fast.decompress(compressed, INPUT.length)).isEqualTo(INPUT);
        byte[] fastDestination = new byte[INPUT.length];
        assertThat(fast.decompress(compressed, fastDestination)).isEqualTo(compressed.length);
        assertThat(fast.decompress(compressed, fastDestination, INPUT.length)).isEqualTo(compressed.length);
        assertThat(fast.decompress(compressed, 0, INPUT.length)).isEqualTo(INPUT);
        assertThat(fast.decompress(compressed, 0, INPUT.length)).isEqualTo(INPUT);
        assertThat(fast.toString()).contains("Decompressor");

        assertThat(safe.decompress(compressed, INPUT.length)).isEqualTo(INPUT);
        assertThat(safe.decompress(compressed, new byte[INPUT.length])).isEqualTo(INPUT.length);
        assertThat(safe.decompress(compressed, 0, compressed.length, INPUT.length)).isEqualTo(INPUT);
        ByteBuffer compressedBuffer = ByteBuffer.wrap(compressed);
        ByteBuffer restored = ByteBuffer.allocate(INPUT.length);
        safe.decompress(compressedBuffer, restored);
        assertThat(restored.array()).isEqualTo(INPUT);
        assertThat(safe.toString()).contains("Decompressor");
    }

    @Test
    void compressorWithLengthConvenienceMethodRoundTripsPayload() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        LZ4CompressorWithLength compressor = new LZ4CompressorWithLength(factory.fastCompressor());

        byte[] encoded = compressor.compress(INPUT);
        byte[] restored = new LZ4DecompressorWithLength(factory.safeDecompressor()).decompress(encoded);

        assertThat(restored).isEqualTo(INPUT);
    }

    @Test
    @SuppressWarnings("deprecation")
    void directBuffersDriveJavaUnsafeJavaSafeAndNativeCompressionPaths() {
        byte[] shortInput = repeatedInput(4096, 29);
        byte[] longInput = repeatedInput(70000, 257);
        LZ4Factory[] factories = new LZ4Factory[]{
                LZ4Factory.safeInstance(),
                LZ4Factory.unsafeInsecureInstance(),
                LZ4Factory.nativeInsecureInstance()
        };

        for (LZ4Factory factory : factories) {
            assertDirectCompressionRoundTrip(factory.fastCompressor(), shortInput);
            assertDirectCompressionRoundTrip(factory.fastCompressor(), longInput);
            assertDirectCompressionRoundTrip(factory.highCompressor(17), longInput);
        }

        LZ4Compressor unsafeCompressor = LZ4Factory.unsafeInsecureInstance().fastCompressor();
        byte[] unsafeCompressed = unsafeCompressor.compress(longInput);
        assertThat(LZ4Factory.unsafeInsecureInstance().safeDecompressor().decompress(unsafeCompressed, longInput.length))
                .isEqualTo(longInput);
    }

    @Test
    @SuppressWarnings("deprecation")
    void highCompressionExploresOverlappingMatchesThroughDirectBuffers() {
        byte[] input = overlappingMatchInput();
        assertDirectCompressionRoundTrip(LZ4Factory.safeInstance().highCompressor(17), input);
        assertDirectCompressionRoundTrip(LZ4Factory.unsafeInsecureInstance().highCompressor(17), input);
    }

    @Test
    @SuppressWarnings("deprecation")
    void highCompressionExploresOverlappingMatchesThroughByteArrays() {
        byte[] input = overlappingMatchInput();
        LZ4Compressor compressor = LZ4Factory.unsafeInsecureInstance().highCompressor(17);
        byte[] compressed = new byte[compressor.maxCompressedLength(input.length) + 5];
        int compressedLength = compressor.compress(input, 0, input.length, compressed, 2, compressed.length - 2);

        byte[] restored = new byte[input.length];
        int consumed = LZ4Factory.safeInstance().fastDecompressor().decompress(compressed, 2, restored, 0,
                input.length);
        assertThat(consumed).isEqualTo(compressedLength);
        assertThat(restored).isEqualTo(input);
    }

    @Test
    void lengthDecompressorUsesWildIncrementalCopyThroughPublicByteBufferApi() {
        ByteBuffer encoded = ByteBuffer.allocateDirect(29);
        encoded.put(new byte[]{32, 0, 0, 0, 0x48, 'A', 'B', 'C', 'D', 4, 0, (byte) 0xF0, 1});
        for (int i = 0; i < 16; i++) {
            encoded.put((byte) (0x10 + i));
        }
        encoded.flip();

        ByteBuffer restored = ByteBuffer.allocateDirect(32);
        new LZ4DecompressorWithLength(LZ4Factory.safeInstance().safeDecompressor())
                .decompress(encoded, restored);

        byte[] expected = new byte[32];
        for (int i = 0; i < 16; i++) {
            expected[i] = (byte) ('A' + i % 4);
        }
        for (int i = 0; i < 16; i++) {
            expected[16 + i] = (byte) (0x10 + i);
        }
        assertThat(restored.position()).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(restored.get(i)).isEqualTo(expected[i]);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void lengthDecompressorsDriveJavaUnsafeAndNativeByteBufferPaths() {
        byte[] input = repeatedInput(70000, 257);
        ByteBuffer encoded = encodedWithLength(input);
        LZ4Factory[] factories = new LZ4Factory[]{
                LZ4Factory.safeInstance(),
                LZ4Factory.unsafeInsecureInstance(),
                LZ4Factory.nativeInsecureInstance()
        };

        for (LZ4Factory factory : factories) {
            assertLengthDecompressionRoundTrip(factory.fastDecompressor(), encoded, input);
            assertLengthDecompressionRoundTrip(factory.safeDecompressor(), encoded, input);
        }

        byte[] arrayBlock = LZ4Factory.safeInstance().fastCompressor().compress(input);
        byte[] arrayEncoded = new byte[arrayBlock.length + 4];
        arrayEncoded[0] = (byte) input.length;
        arrayEncoded[1] = (byte) (input.length >>> 8);
        arrayEncoded[2] = (byte) (input.length >>> 16);
        arrayEncoded[3] = (byte) (input.length >>> 24);
        System.arraycopy(arrayBlock, 0, arrayEncoded, 4, arrayBlock.length);
        assertThat(new LZ4DecompressorWithLength(LZ4Factory.unsafeInsecureInstance().safeDecompressor())
                .decompress(arrayEncoded)).isEqualTo(input);

        ByteBuffer zeroEncoded = ByteBuffer.allocateDirect(15);
        zeroEncoded.put(new byte[]{32, 0, 0, 0, 31, 97, 0, 0, 7, 80, 97, 97, 97, 97, 97}).flip();
        ByteBuffer zeroOutput = ByteBuffer.allocateDirect(32);
        new LZ4DecompressorWithLength(LZ4Factory.safeInstance().safeDecompressor())
                .decompress(zeroEncoded, zeroOutput);
        assertThat(zeroOutput.get(0)).isEqualTo((byte) 97);
        for (int i = 1; i < 27; i++) {
            assertThat(zeroOutput.get(i)).isZero();
        }
        for (int i = 27; i < 32; i++) {
            assertThat(zeroOutput.get(i)).isEqualTo((byte) 97);
        }
    }

    @Test
    void compressorAndDecompressorWithLengthPreserveOffsetsAndBufferPositions() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        LZ4CompressorWithLength compressor = new LZ4CompressorWithLength(factory.fastCompressor());
        byte[] source = new byte[INPUT.length + 5];
        System.arraycopy(INPUT, 0, source, 2, INPUT.length);
        byte[] encoded = compressor.compress(source, 2, INPUT.length);
        assertThat(compressor.maxCompressedLength(INPUT.length)).isGreaterThan(encoded.length - 1);
        assertThat(LZ4DecompressorWithLength.getDecompressedLength(encoded)).isEqualTo(INPUT.length);
        assertThat(new LZ4DecompressorWithLength(factory.fastDecompressor()).decompress(encoded)).isEqualTo(INPUT);
        assertThat(new LZ4DecompressorWithLength(factory.safeDecompressor()).decompress(encoded, 0, encoded.length))
                .isEqualTo(INPUT);

        byte[] output = new byte[encoded.length + 4];
        int outputLength = compressor.compress(source, 2, INPUT.length, output, 3);
        int explicitOutputLength = compressor.compress(source, 2, INPUT.length, output, 3,
                output.length - 3);
        assertThat(explicitOutputLength).isEqualTo(outputLength);
        LZ4DecompressorWithLength fast = new LZ4DecompressorWithLength(factory.fastDecompressor());
        assertThat(fast.decompress(encoded, new byte[INPUT.length])).isEqualTo(encoded.length);
        assertThat(fast.decompress(encoded, 0)).isEqualTo(INPUT);
        assertThat(fast.decompress(output, 3, new byte[INPUT.length], 0)).isEqualTo(outputLength);
        LZ4DecompressorWithLength safe = new LZ4DecompressorWithLength(factory.safeDecompressor());
        byte[] restored = new byte[INPUT.length];
        assertThat(safe.decompress(output, 3, outputLength, restored, 0)).isEqualTo(INPUT.length);
        assertThat(restored).isEqualTo(INPUT);

        byte[] exactOutput = new byte[compressor.maxCompressedLength(INPUT.length)];
        int exactLength = compressor.compress(INPUT, exactOutput);
        assertThat(fast.decompress(exactOutput, 0, new byte[INPUT.length], 0)).isEqualTo(exactLength);

        ByteBuffer sourceBuffer = ByteBuffer.wrap(INPUT);
        ByteBuffer encodedBuffer = ByteBuffer.allocate(compressor.maxCompressedLength(INPUT.length));
        compressor.compress(sourceBuffer, encodedBuffer);
        assertThat(sourceBuffer.position()).isEqualTo(sourceBuffer.limit());
        encodedBuffer.flip();
        assertThat(LZ4DecompressorWithLength.getDecompressedLength(encodedBuffer)).isEqualTo(INPUT.length);
        ByteBuffer restoredBuffer = ByteBuffer.allocate(INPUT.length);
        new LZ4DecompressorWithLength(factory.safeDecompressor()).decompress(encodedBuffer, restoredBuffer);
        assertThat(restoredBuffer.array()).isEqualTo(INPUT);
        ByteBuffer fastEncodedBuffer = ByteBuffer.wrap(encoded);
        ByteBuffer fastRestoredBuffer = ByteBuffer.allocate(INPUT.length);
        new LZ4DecompressorWithLength(factory.fastDecompressor()).decompress(fastEncodedBuffer, fastRestoredBuffer);
        assertThat(fastRestoredBuffer.array()).isEqualTo(INPUT);

        ByteBuffer sourceWithOffset = ByteBuffer.allocate(INPUT.length + 8);
        sourceWithOffset.position(2);
        sourceWithOffset.put(INPUT).flip();
        sourceWithOffset.position(2);
        ByteBuffer encodedWithOffset = ByteBuffer.allocate(compressor.maxCompressedLength(INPUT.length) + 8);
        int explicitLength = compressor.compress(sourceWithOffset, 2, INPUT.length, encodedWithOffset, 3,
                encodedWithOffset.capacity() - 3);
        assertThat(LZ4DecompressorWithLength.getDecompressedLength(encodedWithOffset, 3)).isEqualTo(INPUT.length);
        ByteBuffer restoredWithOffset = ByteBuffer.allocate(INPUT.length + 4);
        assertThat(new LZ4DecompressorWithLength(factory.fastDecompressor()).decompress(encodedWithOffset, 3,
                restoredWithOffset, 1)).isEqualTo(explicitLength);
        restoredWithOffset.position(1);
        byte[] restoredBytes = new byte[INPUT.length];
        restoredWithOffset.get(restoredBytes);
        assertThat(restoredBytes).isEqualTo(INPUT);

        ByteBuffer explicitDestination = ByteBuffer.allocate(INPUT.length + 3);
        int bytesRead = new LZ4DecompressorWithLength(factory.safeDecompressor()).decompress(encodedWithOffset, 3,
                explicitLength, explicitDestination, 2);
        assertThat(bytesRead).isEqualTo(INPUT.length);
        explicitDestination.position(2);
        byte[] explicitBytes = new byte[INPUT.length];
        explicitDestination.get(explicitBytes);
        assertThat(explicitBytes).isEqualTo(INPUT);
    }

    private static void assertDirectCompressionRoundTrip(LZ4Compressor compressor, byte[] input) {
        ByteBuffer source = ByteBuffer.allocateDirect(input.length + 6);
        source.position(2);
        source.put(input);
        ByteBuffer compressed = ByteBuffer.allocateDirect(compressor.maxCompressedLength(input.length) + 8);
        int compressedLength = compressor.compress(source, 2, input.length, compressed, 3, compressed.capacity() - 3);

        ByteBuffer encoded = ByteBuffer.allocateDirect(compressedLength + 4);
        encoded.put((byte) input.length);
        encoded.put((byte) (input.length >>> 8));
        encoded.put((byte) (input.length >>> 16));
        encoded.put((byte) (input.length >>> 24));
        for (int i = 0; i < compressedLength; i++) {
            encoded.put(compressed.get(3 + i));
        }
        encoded.flip();
        ByteBuffer restored = ByteBuffer.allocateDirect(input.length + 2);
        restored.position(1);
        new LZ4DecompressorWithLength(LZ4Factory.safeInstance().safeDecompressor()).decompress(encoded, restored);
        for (int i = 0; i < input.length; i++) {
            assertThat(restored.get(1 + i)).isEqualTo(input[i]);
        }
    }

    private static void assertLengthDecompressionRoundTrip(LZ4FastDecompressor decompressor, ByteBuffer encoded,
            byte[] input) {
        ByteBuffer restored = ByteBuffer.allocateDirect(input.length);
        new LZ4DecompressorWithLength(decompressor).decompress(encoded.duplicate(), restored);
        assertBufferEquals(input, restored);
    }

    private static void assertLengthDecompressionRoundTrip(LZ4SafeDecompressor decompressor, ByteBuffer encoded,
            byte[] input) {
        ByteBuffer restored = ByteBuffer.allocateDirect(input.length);
        new LZ4DecompressorWithLength(decompressor).decompress(encoded.duplicate(), restored);
        assertBufferEquals(input, restored);
    }

    private static ByteBuffer encodedWithLength(byte[] input) {
        LZ4CompressorWithLength compressor = new LZ4CompressorWithLength(LZ4Factory.safeInstance().fastCompressor());
        ByteBuffer source = ByteBuffer.allocateDirect(input.length);
        source.put(input).flip();
        ByteBuffer encoded = ByteBuffer.allocateDirect(compressor.maxCompressedLength(input.length));
        compressor.compress(source, encoded);
        encoded.flip();
        return encoded;
    }

    private static void assertBufferEquals(byte[] expected, ByteBuffer actual) {
        assertThat(actual.position()).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(actual.get(i)).isEqualTo(expected[i]);
        }
    }

    private static byte[] repeatedInput(int length, int period) {
        byte[] input = new byte[length];
        for (int i = 0; i < input.length; i++) {
            int value = i % period;
            input[i] = (byte) (value * 31 + value / 7);
        }
        return input;
    }

    private static byte[] overlappingMatchInput() {
        byte[] input = new byte[20000];
        new Random(12345).nextBytes(input);
        int first = 1000;
        int second = 3000;
        int shifted = 2000;
        byte[] match = new byte[]{0x30, 0x41, 0x52, 0x63, 0x74, (byte) 0x85, (byte) 0x96, (byte) 0xA7};
        System.arraycopy(match, 0, input, first, match.length);
        System.arraycopy(match, 0, input, second, match.length);
        for (int i = 0; i < 16; i++) {
            input[shifted - 3 + i] = input[second + 3 + i];
        }
        return input;
    }

    private static byte[] roundTrip(LZ4Compressor compressor, byte[] compressed, int length) {
        byte[] restored = new byte[length];
        int read = LZ4Factory.safeInstance().fastDecompressor().decompress(compressed, restored);
        assertThat(read).isEqualTo(compressed.length);
        return restored;
    }
}
