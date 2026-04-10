/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;

import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

class LZ4FactoryTest {

    @Test
    void safeInstanceLoadsReflectiveImplementationsAndRoundTripsData() {
        final LZ4Factory factory = LZ4Factory.safeInstance();
        final byte[] source = ("lz4-java uses reflective lookups for compressor and decompressor implementations. "
                + "This test exercises the safe factory path with repeated content to compress well. "
                + "lz4-java uses reflective lookups for compressor and decompressor implementations.")
                .getBytes(StandardCharsets.UTF_8);
        final byte[] compressed = factory.fastCompressor().compress(source);
        final byte[] restoredByFastDecompressor = factory.fastDecompressor().decompress(compressed, source.length);
        final byte[] restoredBySafeDecompressor = factory.safeDecompressor().decompress(compressed, source.length);

        assertSame(factory, LZ4Factory.safeInstance());
        assertEquals("LZ4Factory:JavaSafe", factory.toString());
        assertArrayEquals(source, restoredByFastDecompressor);
        assertArrayEquals(source, restoredBySafeDecompressor);
    }

    @Test
    void highCompressorClampsInvalidLevelsAndReusesDefaultInstance() {
        final LZ4Factory factory = LZ4Factory.safeInstance();

        assertSame(factory.highCompressor(), factory.highCompressor(0));
        assertSame(factory.highCompressor(), factory.highCompressor(-1));
        assertSame(factory.highCompressor(17), factory.highCompressor(18));
        assertNotNull(factory.highCompressor(1));
    }
}
