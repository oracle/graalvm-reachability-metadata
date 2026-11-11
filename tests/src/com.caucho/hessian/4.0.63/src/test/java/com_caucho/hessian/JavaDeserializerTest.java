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
public class JavaDeserializerTest {
    @Test
    void constructsAndResolvesFieldBasedValue() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            assertEquals("resolved:snapshot", SerializationSupport.roundTrip(new Value("snapshot"),
                    SerializationSupport.reflectionFactory(Value.class), hessian2));
        }
    }

    public static class Value implements Serializable {
        private String text;

        public Value(String text) {
            this.text = text;
        }

        private Object readResolve() {
            return "resolved:" + text;
        }
    }
}
