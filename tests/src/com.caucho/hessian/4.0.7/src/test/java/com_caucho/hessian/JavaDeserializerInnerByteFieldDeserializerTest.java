/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.JavaDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class JavaDeserializerInnerByteFieldDeserializerTest {
    @Test
    void restoresFieldFromBothWireFormats() throws Exception {
        Payload original = new Payload();
        original.value = (byte) 73;
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(Payload.class, null, new JavaDeserializer(Payload.class));
            Payload restored = HessianStreams.roundTrip(original, factory, hessian2);
            assertNotSame(original, restored);
            assertEquals(original.value, restored.value);
        }
    }

    public static class Payload implements Serializable {
        private byte value;
    }
}
