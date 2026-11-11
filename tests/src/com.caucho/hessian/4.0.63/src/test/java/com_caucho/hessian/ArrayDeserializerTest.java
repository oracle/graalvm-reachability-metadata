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
public class ArrayDeserializerTest {
    @Test
    void restoresTypedObjectArray() throws Exception {
        Value[] values = {new Value("first"), new Value("second")};
        Value[] restored = (Value[]) SerializationSupport.roundTrip(values);
        assertEquals(2, restored.length);
        assertEquals("first", restored[0].text);
        assertEquals("second", restored[1].text);
    }

    public static class Value implements Serializable {
        private String text;

        public Value(String text) {
            this.text = text;
        }
    }
}
