/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.BeanSerializer;
import com.caucho.hessian.io.JavaDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanSerializerTest {
    @Test
    void serializesPropertiesInBothWireFormats() throws Exception {
        Message original = new Message();
        original.setText("outbound");
        original.setCount(7);
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(Message.class,
                    new BeanSerializer(Message.class, getClass().getClassLoader()), new JavaDeserializer(Message.class));
            Message restored = HessianStreams.roundTrip(original, factory, hessian2);
            assertEquals("outbound", restored.getText());
            assertEquals(7, restored.getCount());
        }
    }

    @Test
    void appliesObjectReplacement() throws Exception {
        SerializerFactory factory = HessianStreams.codecs(Replacement.class,
                new BeanSerializer(Replacement.class, getClass().getClassLoader()), null);
        assertEquals("replacement", HessianStreams.roundTrip((Object) new Replacement(), factory, false));
    }

    @Test
    void appliesNamedSerializationAdapter() throws Exception {
        SerializerFactory factory = HessianStreams.codecs(Adapted.class,
                new BeanSerializer(Adapted.class, getClass().getClassLoader()), null);
        assertEquals("adapted:parcel", HessianStreams.roundTrip((Object) new Adapted("parcel"), factory, true));
    }

    public static class Message {
        private String text;
        private int count;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class Replacement {
        public Object writeReplace() {
            return "replacement";
        }
    }

    public static class Adapted {
        private final String value;

        public Adapted(String value) {
            this.value = value;
        }
    }

    public static class AdaptedHessianSerializer {
        public Object writeReplace(Adapted value) {
            return "adapted:" + value.value;
        }
    }
}
