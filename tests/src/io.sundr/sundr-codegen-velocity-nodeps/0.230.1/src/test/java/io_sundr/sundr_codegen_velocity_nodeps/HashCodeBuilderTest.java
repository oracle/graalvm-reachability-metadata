/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.sundr.deps.org.apache.commons.lang.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals(new HashCodeBuilder().append(7).toHashCode(), HashCodeBuilder.reflectionHashCode(new Value(7)));
        assertNotEquals(HashCodeBuilder.reflectionHashCode(new Value(7)), HashCodeBuilder.reflectionHashCode(new Value(8)));
    }

    public static class Value {
        private final int number;

        public Value(int number) {
            this.number = number;
        }
    }
}
