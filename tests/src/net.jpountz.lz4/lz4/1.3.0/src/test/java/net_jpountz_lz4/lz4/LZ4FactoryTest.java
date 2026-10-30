/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_jpountz_lz4.lz4;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LZ4FactoryTest {
    @Test
    void fastCompressorRoundTripsWithBothDecompressors() {
        LZ4Factory factory = LZ4Factory.safeInstance();

        assertSame(factory, LZ4Factory.safeInstance());
        assertRoundTrip(factory, factory.fastCompressor());
    }

    @Test
    void defaultHighCompressorRoundTripsWithBothDecompressors() {
        LZ4Factory factory = LZ4Factory.safeInstance();

        assertRoundTrip(factory, factory.highCompressor());
    }

    @Test
    void customCompressionLevelsRoundTripWithBothDecompressors() {
        LZ4Factory factory = LZ4Factory.safeInstance();

        for (int level = 1; level <= 17; level++) {
            assertRoundTrip(factory, factory.highCompressor(level));
        }
    }

    private static void assertRoundTrip(LZ4Factory factory, LZ4Compressor compressor) {
        byte[] original = "A repeated payload for LZ4 compression and decompression.\n"
                .repeat(64).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[compressor.maxCompressedLength(original.length)];
        int compressedLength = compressor.compress(original, 0, original.length,
                compressed, 0, compressed.length);

        assertTrue(compressedLength > 0);
        assertTrue(compressedLength < original.length);

        byte[] fastRestored = new byte[original.length];
        int consumed = factory.fastDecompressor().decompress(compressed, 0,
                fastRestored, 0, fastRestored.length);
        assertEquals(compressedLength, consumed);
        assertArrayEquals(original, fastRestored);

        byte[] safeRestored = new byte[original.length];
        int restoredLength = factory.safeDecompressor().decompress(compressed, 0,
                compressedLength, safeRestored, 0, safeRestored.length);
        assertEquals(original.length, restoredLength);
        assertArrayEquals(original, safeRestored);
    }
}
