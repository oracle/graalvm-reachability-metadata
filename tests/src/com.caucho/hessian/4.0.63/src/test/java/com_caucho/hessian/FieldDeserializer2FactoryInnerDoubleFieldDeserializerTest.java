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
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(120)
public class FieldDeserializer2FactoryInnerDoubleFieldDeserializerTest {
    @Test
    void restoresFieldInBothProtocols() throws Exception {
        Value original = new Value();
        original.value = 19.875;
        for (boolean hessian2 : new boolean[] {false, true}) {
            Value restored = (Value) SerializationSupport.roundTrip(original,
                    SerializationSupport.reflectionFactory(Value.class), hessian2);
            assertEquals(original.value, restored.value);
        }
    }

    public static class Value implements Serializable {
        private double value;
    }
}
