/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        String result = ReflectionToStringBuilder.toString(new Value(7));
        assertTrue(result.contains("number=7"));
    }

    public static class Value {
        private final int number;

        public Value(int number) {
            this.number = number;
        }
    }
}
