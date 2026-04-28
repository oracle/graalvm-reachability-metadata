/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import java.nio.charset.StandardCharsets;

import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XXHashFactoryTest {

    @Test
    void shouldInitializeSafeFactoryAndReuseCachedInstance() {
        XXHashFactory factory = XXHashFactory.safeInstance();

        assertThat(factory).isSameAs(XXHashFactory.safeInstance());
        assertThat(factory.toString()).contains("JavaSafe");
        assertThat(factory.hash32()).isNotNull();
        assertThat(factory.hash64()).isNotNull();
    }

    @Test
    void shouldProduceMatchingDirectAndStreamingHashes() throws Exception {
        XXHashFactory factory = XXHashFactory.safeInstance();
        byte[] payload = ("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                + "cccccccccccccccccccccccccccccccc"
                + "xxhash factory dynamic access coverage")
                .getBytes(StandardCharsets.UTF_8);
        int seed32 = 0x1F2E3D4C;
        long seed64 = 0x0102030405060708L;

        int direct32 = factory.hash32().hash(payload, 0, payload.length, seed32);
        long direct64 = factory.hash64().hash(payload, 0, payload.length, seed64);

        try (StreamingXXHash32 streaming32 = factory.newStreamingHash32(seed32);
             StreamingXXHash64 streaming64 = factory.newStreamingHash64(seed64)) {
            streaming32.update(payload, 0, 19);
            streaming32.update(payload, 19, payload.length - 19);
            streaming64.update(payload, 0, 23);
            streaming64.update(payload, 23, payload.length - 23);

            assertThat(streaming32.getValue()).isEqualTo(direct32);
            assertThat(streaming64.getValue()).isEqualTo(direct64);
        }
    }
}
