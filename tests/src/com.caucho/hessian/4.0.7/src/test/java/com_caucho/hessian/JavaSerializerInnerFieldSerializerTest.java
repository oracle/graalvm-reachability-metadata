/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.JavaSerializer;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class JavaSerializerInnerFieldSerializerTest {
    @Test
    void writesFieldInBothWireFormats() throws Exception {
        Payload original = new Payload();
        original.value = new String[] {"one", "two"};
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(Payload.class, new JavaSerializer(Payload.class), null);
            Payload restored = HessianStreams.roundTrip(original, factory, hessian2);
            assertArrayEquals((String[]) original.value, (String[]) restored.value);
        }
    }

    public static class Payload implements Serializable {
        private Object value;
    }
}
