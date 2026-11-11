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
public class FieldDeserializer2FactoryUnsafeTest {
    @Test
    void restoresPrimitiveFields() throws Exception {
        Value original = new Value();
        original.flag = true;
        original.ratio = 1.25;
        original.letter = 'Q';
        Value restored = (Value) SerializationSupport.roundTrip(original);
        assertEquals(original.flag, restored.flag);
        assertEquals(original.ratio, restored.ratio);
        assertEquals(original.letter, restored.letter);
    }

    public static class Value implements Serializable {
        private boolean flag;
        private double ratio;
        private char letter;
    }
}
