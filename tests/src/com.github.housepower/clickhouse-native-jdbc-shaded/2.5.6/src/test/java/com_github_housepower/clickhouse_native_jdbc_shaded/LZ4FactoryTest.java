/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_housepower.clickhouse_native_jdbc_shaded;

import java.nio.charset.StandardCharsets;

import com.github.housepower.repackaged.net.jpountz.lz4.LZ4Compressor;
import com.github.housepower.repackaged.net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LZ4FactoryTest {

    @Test
    void fastCompressionRoundTripsThroughBothDecompressors() {
        LZ4Factory factory = LZ4Factory.safeInstance();

        assertRoundTrip(factory, factory.fastCompressor());
    }

    @Test
    void highCompressionRoundTripsAtEverySupportedLevel() {
        LZ4Factory factory = LZ4Factory.safeInstance();

        assertRoundTrip(factory, factory.highCompressor());
        for (int level = 1; level <= 17; level++) {
            assertRoundTrip(factory, factory.highCompressor(level));
        }
    }

    private static void assertRoundTrip(LZ4Factory factory, LZ4Compressor compressor) {
        byte[] original = "sensor=temperature;location=warehouse;reading=21.75\n"
                .repeat(64).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[compressor.maxCompressedLength(original.length)];
        int compressedLength = compressor.compress(original, 0, original.length,
                compressed, 0, compressed.length);

        assertTrue(compressedLength > 0);
        assertTrue(compressedLength < original.length);

        byte[] fastRestored = new byte[original.length];
        int consumed = factory.fastDecompressor().decompress(compressed, 0,
                fastRestored, 0, original.length);
        assertEquals(compressedLength, consumed);
        assertArrayEquals(original, fastRestored);

        byte[] safeRestored = new byte[original.length];
        int restoredLength = factory.safeDecompressor().decompress(compressed, 0,
                compressedLength, safeRestored, 0);
        assertEquals(original.length, restoredLength);
        assertArrayEquals(original, safeRestored);
    }
}
