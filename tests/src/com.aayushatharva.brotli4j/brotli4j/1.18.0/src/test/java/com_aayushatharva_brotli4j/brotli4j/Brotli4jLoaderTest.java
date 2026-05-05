/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_aayushatharva_brotli4j.brotli4j;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.encoder.Encoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class Brotli4jLoaderTest {
    @Test
    void nativeLibraryLoadsAndRoundTripsBrotliData() throws IOException {
        Brotli4jLoader.ensureAvailability();

        assertThat(Brotli4jLoader.isAvailable()).isTrue();
        assertThat(Brotli4jLoader.getUnavailabilityCause()).isNull();

        byte[] original = """
                Brotli4j should load its native provider and perform a real compression round trip.
                Native-image support is only proven when both encoding and decoding execute successfully.
                """.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = Encoder.compress(original, new Encoder.Parameters().setQuality(4));
        byte[] decompressed = Decoder.decompress(compressed, 0, compressed.length);

        assertThat(compressed).isNotEmpty();
        assertThat(decompressed).isEqualTo(original);
    }
}
