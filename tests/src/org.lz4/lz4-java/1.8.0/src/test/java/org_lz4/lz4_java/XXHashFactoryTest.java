/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XXHashFactoryTest {
    @Test
    void shouldCreateSafeFactoryAndMatchStreaming32BitHash() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] input = "xxhash factory safe instance".getBytes(StandardCharsets.UTF_8);
        int seed = 0x9E3779B1;

        int blockHash = factory.hash32().hash(input, 0, input.length, seed);
        StreamingXXHash32 streamingHash = factory.newStreamingHash32(seed);
        streamingHash.update(input, 0, 7);
        streamingHash.update(input, 7, input.length - 7);

        assertThat(streamingHash.getValue()).isEqualTo(blockHash);
        assertThat(factory.hash32().hash(ByteBuffer.wrap(input), seed)).isEqualTo(blockHash);
        assertThat(factory.toString()).contains("JavaSafe");
    }

    @Test
    void shouldCreateSafeFactoryAndMatchStreaming64BitHash() {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] input = "xxhash factory 64-bit verification".getBytes(StandardCharsets.UTF_8);
        long seed = 0x1234ABCDL;

        long blockHash = factory.hash64().hash(input, 0, input.length, seed);
        StreamingXXHash64 streamingHash = factory.newStreamingHash64(seed);
        streamingHash.update(input, 0, 11);
        streamingHash.update(input, 11, input.length - 11);

        assertThat(streamingHash.getValue()).isEqualTo(blockHash);
        assertThat(factory.hash64().hash(ByteBuffer.wrap(input), seed)).isEqualTo(blockHash);
        assertThat(streamingHash.toString()).contains("seed=").contains(Long.toString(seed));
    }
}
