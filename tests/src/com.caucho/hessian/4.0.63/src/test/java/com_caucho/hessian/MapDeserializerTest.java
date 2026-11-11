/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.SerializerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(120)
public class MapDeserializerTest {
    @Test
    void restoresConcreteMap() throws Exception {
        Entries values = new Entries();
        values.put("term", 17);
        values.put("index", 123);
        for (boolean hessian2 : new boolean[] {false, true}) {
            Object restored = SerializationSupport.roundTrip(values, new SerializerFactory(), hessian2);
            assertInstanceOf(Entries.class, restored);
            assertEquals(values, restored);
        }
    }

    @Test
    void readsMapFromStructuredObjectFields() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        try {
            output.writeObjectBegin(Entries.class.getName());
            output.writeClassFieldLength(2);
            output.writeString("term");
            output.writeString("index");
            output.writeObjectBegin(Entries.class.getName());
            output.writeInt(17);
            output.writeInt(123);
            output.writeObjectEnd();
            output.flush();
            Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
            try {
                Entries restored = assertInstanceOf(Entries.class, input.readObject());
                assertEquals(2, restored.size());
                assertEquals(17, restored.get("term"));
                assertEquals(123, restored.get("index"));
            } finally {
                input.close();
            }
        } finally {
            output.close();
        }
    }

    public static class Entries extends LinkedHashMap<String, Integer> {
    }
}
