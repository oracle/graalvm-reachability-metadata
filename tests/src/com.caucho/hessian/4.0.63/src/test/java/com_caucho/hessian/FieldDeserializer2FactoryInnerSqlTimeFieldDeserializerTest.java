/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.io.Serializable;
import java.sql.Time;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(120)
public class FieldDeserializer2FactoryInnerSqlTimeFieldDeserializerTest {
    @Test
    void restoresPresentAndNullDates() throws Exception {
        Value original = new Value();
        original.value = new Time(1700000000000L);
        for (boolean hessian2 : new boolean[] {false, true}) {
            Value restored = (Value) SerializationSupport.roundTrip(original,
                    SerializationSupport.reflectionFactory(Value.class), hessian2);
            assertEquals(original.value, restored.value);
            Value empty = (Value) SerializationSupport.roundTrip(new Value(),
                    SerializationSupport.reflectionFactory(Value.class), hessian2);
            assertNull(empty.value);
        }
    }

    public static class Value implements Serializable {
        private Time value;
    }
}
