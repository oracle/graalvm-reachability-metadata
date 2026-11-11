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
public class UnsafeDeserializerTest {
    @Test
    void restoresAndResolvesImmutableValue() throws Exception {
        assertEquals("restored:term-7", SerializationSupport.roundTrip(new Value("term-7")));
    }

    public static class Value implements Serializable {
        private final String text;

        public Value(String text) {
            this.text = text;
        }

        private Object readResolve() {
            return "restored:" + text;
        }
    }
}
