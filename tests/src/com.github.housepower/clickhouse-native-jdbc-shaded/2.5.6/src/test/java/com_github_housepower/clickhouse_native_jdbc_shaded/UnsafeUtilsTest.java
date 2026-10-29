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

public class UnsafeUtilsTest {

    @Test
    void unsafeFastCompressionInteroperatesWithSafeDecompression() {
        LZ4Factory factory = LZ4Factory.unsafeInstance();

        assertCompressionRoundTrip(factory, factory.fastCompressor());
    }

    @Test
    void unsafeHighCompressionInteroperatesWithSafeDecompression() {
        LZ4Factory factory = LZ4Factory.unsafeInstance();

        assertCompressionRoundTrip(factory, factory.highCompressor());
    }

    private static void assertCompressionRoundTrip(LZ4Factory factory, LZ4Compressor compressor) {
        byte[] input = "row=42;city=Reykjavik;measurement=104.625;status=ready\n"
                .repeat(80).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = compressor.compress(input);

        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < input.length);
        assertArrayEquals(input, LZ4Factory.safeInstance().safeDecompressor()
                .decompress(compressed, input.length));

        byte[] fastRestored = new byte[input.length];
        int consumed = factory.fastDecompressor().decompress(compressed, 0,
                fastRestored, 0, input.length);
        assertEquals(compressed.length, consumed);
        assertArrayEquals(input, fastRestored);

        byte[] safeRestored = new byte[input.length];
        int produced = factory.safeDecompressor().decompress(compressed, 0,
                compressed.length, safeRestored, 0);
        assertEquals(input.length, produced);
        assertArrayEquals(input, safeRestored);
    }
}
