/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.io.Serializable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class UnsafeSerializerTest {
    @Test
    void roundTripsPrivateFields() throws Exception {
        Value restored = (Value) SerializationSupport.roundTrip(new Value("leader", 42));
        assertEquals("leader", restored.name);
        assertEquals(42, restored.term);
    }

    public static class Value implements Serializable {
        private final String name;
        private final int term;

        public Value(String name, int term) {
            this.name = name;
            this.term = term;
        }
    }
}
