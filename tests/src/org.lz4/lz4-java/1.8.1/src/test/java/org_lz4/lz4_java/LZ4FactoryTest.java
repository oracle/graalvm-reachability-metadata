/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import java.nio.charset.StandardCharsets;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LZ4FactoryTest {

    @Test
    void shouldInitializeSafeFactoryAndCacheConstructedCompressors() {
        LZ4Factory factory = LZ4Factory.safeInstance();

        assertThat(factory).isSameAs(LZ4Factory.safeInstance());
        assertThat(factory.toString()).contains("JavaSafe");
        assertThat(factory.highCompressor(0)).isSameAs(factory.highCompressor());
        assertThat(factory.highCompressor(18)).isSameAs(factory.highCompressor(17));
        assertThat(factory.highCompressor(1)).isSameAs(factory.highCompressor(1));
        assertThat(factory.highCompressor(1)).isNotSameAs(factory.highCompressor());
        assertThat(factory.highCompressor(17)).isNotSameAs(factory.highCompressor());
    }

    @Test
    void shouldRoundTripPayloadWithFastAndHighCompressors() {
        LZ4Factory factory = LZ4Factory.safeInstance();
        byte[] payload = ("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                + "cccccccccccccccccccccccccccccccc"
                + "lz4 factory dynamic access coverage")
                .getBytes(StandardCharsets.UTF_8);

        assertRoundTrip(factory, factory.fastCompressor(), payload);
        assertRoundTrip(factory, factory.highCompressor(1), payload);
        assertRoundTrip(factory, factory.highCompressor(17), payload);
    }

    private static void assertRoundTrip(LZ4Factory factory, LZ4Compressor compressor, byte[] payload) {
        byte[] compressed = new byte[compressor.maxCompressedLength(payload.length)];
        int compressedLength = compressor.compress(payload, 0, payload.length, compressed, 0, compressed.length);

        byte[] safeRestored = new byte[payload.length];
        int safeDecompressedLength = factory.safeDecompressor().decompress(compressed, 0, compressedLength, safeRestored, 0);
        assertThat(safeDecompressedLength).isEqualTo(payload.length);
        assertThat(safeRestored).isEqualTo(payload);

        byte[] fastRestored = new byte[payload.length];
        int consumedLength = factory.fastDecompressor().decompress(compressed, 0, fastRestored, 0, payload.length);
        assertThat(consumedLength).isEqualTo(compressedLength);
        assertThat(fastRestored).isEqualTo(payload);
    }
}
