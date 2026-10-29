/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_housepower.clickhouse_native_jdbc_shaded;

import java.nio.charset.StandardCharsets;

import com.github.housepower.repackaged.net.jpountz.xxhash.StreamingXXHash32;
import com.github.housepower.repackaged.net.jpountz.xxhash.StreamingXXHash64;
import com.github.housepower.repackaged.net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XXHashFactoryTest {

    @Test
    void safeFactoryProducesKnownHashes() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] empty = new byte[0];
        byte[] greeting = "hello".getBytes(StandardCharsets.UTF_8);

        assertEquals(0x02cc5d05, factory.hash32().hash(empty, 0, empty.length, 0));
        assertEquals(0xef46db3751d8e999L, factory.hash64().hash(empty, 0, empty.length, 0L));
        assertEquals(0xfb0077f9, factory.hash32().hash(greeting, 0, greeting.length, 0));
        assertEquals(0x26c7827d889f6da3L, factory.hash64().hash(greeting, 0, greeting.length, 0L));
    }

    @Test
    void streaming32MatchesBlockHashAcrossChunksAndReset() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] payload = "station=west;temperature=18.25;humidity=63\n"
                .repeat(12).getBytes(StandardCharsets.UTF_8);
        int seed = 0x13579bdf;
        int expected = factory.hash32().hash(payload, 0, payload.length, seed);

        try (StreamingXXHash32 streaming = factory.newStreamingHash32(seed)) {
            for (int offset = 0; offset < payload.length; offset += 7) {
                streaming.update(payload, offset, Math.min(7, payload.length - offset));
            }
            assertEquals(expected, streaming.getValue());
            assertEquals(expected, streaming.getValue());

            streaming.reset();
            assertEquals(factory.hash32().hash(payload, 0, 0, seed), streaming.getValue());
            streaming.update(payload, 0, payload.length);
            assertEquals(expected, streaming.getValue());
        }
    }

    @Test
    void streaming64MatchesBlockHashAcrossChunksAndReset() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] payload = "device=weather-sensor;status=active;pressure=1013.25\n"
                .repeat(12).getBytes(StandardCharsets.UTF_8);
        long seed = 0x123456789abcdef0L;
        long expected = factory.hash64().hash(payload, 0, payload.length, seed);

        try (StreamingXXHash64 streaming = factory.newStreamingHash64(seed)) {
            for (int offset = 0; offset < payload.length; offset += 11) {
                streaming.update(payload, offset, Math.min(11, payload.length - offset));
            }
            assertEquals(expected, streaming.getValue());
            assertEquals(expected, streaming.getValue());

            streaming.reset();
            assertEquals(factory.hash64().hash(payload, 0, 0, seed), streaming.getValue());
            streaming.update(payload, 0, payload.length);
            assertEquals(expected, streaming.getValue());
        }
    }
}
