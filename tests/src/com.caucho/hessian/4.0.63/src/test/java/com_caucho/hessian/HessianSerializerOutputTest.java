/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.HessianSerializerOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class HessianSerializerOutputTest {
    @Test
    void writesFieldsAndReplacementThroughObjectWriter() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianSerializerOutput output = new HessianSerializerOutput(bytes);
        Value value = new Value();
        value.text = "snapshot";
        output.writeObjectImpl(value);
        output.writeObjectImpl(new Replaced());
        output.flush();
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            Value restored = (Value) input.readObject();
            assertEquals("snapshot", restored.text);
            assertEquals("replacement", input.readObject());
        } finally {
            input.close();
            output.close();
        }
    }

    public static class Value implements Serializable {
        private String text;
    }

    public static class Replaced {
        public Object writeReplace() {
            return "replacement";
        }
    }
}
