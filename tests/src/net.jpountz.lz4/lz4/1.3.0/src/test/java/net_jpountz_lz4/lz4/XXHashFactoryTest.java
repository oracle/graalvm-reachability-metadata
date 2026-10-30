/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_jpountz_lz4.lz4;

import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class XXHashFactoryTest {
    @Test
    void safeFactoryHashesKnownInputs() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] empty = new byte[0];
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);

        assertSame(factory, XXHashFactory.safeInstance());
        assertEquals(0x02cc5d05, factory.hash32().hash(empty, 0, empty.length, 0));
        assertEquals(0xef46db3751d8e999L, factory.hash64().hash(empty, 0, empty.length, 0));
        assertEquals(0x32d153ff, factory.hash32().hash(input, 0, input.length, 0));
        assertEquals(0x44bc2cf5ad770999L, factory.hash64().hash(input, 0, input.length, 0));
    }

    @Test
    void streamingHash32MatchesBlockHashAndResetsWithItsSeed() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] input = "Streaming a payload in several chunks.\n"
                .repeat(32).getBytes(StandardCharsets.UTF_8);
        int seed = 0x13579bdf;
        int expected = factory.hash32().hash(input, 0, input.length, seed);
        StreamingXXHash32 streaming = factory.newStreamingHash32(seed);

        for (int offset = 0; offset < input.length; offset += 13) {
            streaming.update(input, offset, Math.min(13, input.length - offset));
        }
        assertEquals(expected, streaming.getValue());

        streaming.reset();
        assertEquals(factory.hash32().hash(input, 0, 0, seed), streaming.getValue());
        streaming.update(input, 0, input.length);
        assertEquals(expected, streaming.getValue());
    }

    @Test
    void streamingHash64MatchesBlockHashAndResetsWithItsSeed() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] input = "Streaming a payload in several chunks.\n"
                .repeat(32).getBytes(StandardCharsets.UTF_8);
        long seed = 0x123456789abcdef0L;
        long expected = factory.hash64().hash(input, 0, input.length, seed);
        StreamingXXHash64 streaming = factory.newStreamingHash64(seed);

        for (int offset = 0; offset < input.length; offset += 19) {
            streaming.update(input, offset, Math.min(19, input.length - offset));
        }
        assertEquals(expected, streaming.getValue());

        streaming.reset();
        assertEquals(factory.hash64().hash(input, 0, 0, seed), streaming.getValue());
        streaming.update(input, 0, input.length);
        assertEquals(expected, streaming.getValue());
    }
}
