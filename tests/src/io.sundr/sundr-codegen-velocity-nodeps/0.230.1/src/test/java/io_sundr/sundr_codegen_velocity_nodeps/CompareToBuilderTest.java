/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.lang.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertTrue(CompareToBuilder.reflectionCompare(new Value(1), new Value(2)) < 0);
        assertEquals(0, CompareToBuilder.reflectionCompare(new Value(2), new Value(2)));
    }

    public static class Value {
        private final int number;

        public Value(int number) {
            this.number = number;
        }
    }
}
