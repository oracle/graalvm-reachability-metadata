/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

public class StrongInternPoolTest {
    private static final String STRONG_INTERN_POOL_CLASS = "org.jboss.jandex.StrongInternPool";

    @Test
    void serializesAndDeserializesInternedEntries() throws Exception {
        Object restoredPool = deserialize(serializedPoolWithTwoStrings());

        assertThat(restoredPool.getClass().getName()).isEqualTo(STRONG_INTERN_POOL_CLASS);
        assertThat(restoredPool.toString()).contains("alpha", "beta");

        Object roundTrippedPool = deserialize(serialize(restoredPool));

        assertThat(roundTrippedPool.getClass().getName()).isEqualTo(STRONG_INTERN_POOL_CLASS);
        assertThat(roundTrippedPool.toString()).contains("alpha", "beta");
    }

    private static byte[] serialize(Object pool) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pool);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static byte[] serializedPoolWithTwoStrings() {
        return HexFormat.of().parseHex("""
                aced0005
                73
                  72 0021 6f72672e6a626f73732e6a616e6465782e5374726f6e67496e7465726e506f6f6c
                     000009f0bd13703a 03 0001
                       46 000a 6c6f6164466163746f72
                     78 70
                  3f2b851f
                  77 04 00000002
                  74 0005 616c706861
                  74 0004 62657461
                  78
                """.replaceAll("\\s", ""));
    }
}
