/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.AbstractSerializer;
import com.caucho.hessian.io.ExtSerializerFactory;
import com.caucho.hessian.io.JavaSerializer;
import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.WriteReplaceSerializer;
import java.io.IOException;
import java.io.Serializable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class WriteReplaceSerializerTest {
    @Test
    void replacesValueWithWireRepresentation() throws Exception {
        assertEquals("wire:snapshot", SerializationSupport.roundTrip(new Value("snapshot")));
    }

    @Test
    void explicitlySelectsReplacementInsteadOfConventionCodec() throws Exception {
        EmbeddedValue original = new EmbeddedValue("snapshot");
        ExtSerializerFactory extensions = new ExtSerializerFactory();
        extensions.addSerializer(EmbeddedValue.class,
                new WriteReplaceSerializer(EmbeddedValue.class, EmbeddedValue.class.getClassLoader(),
                        new JavaSerializer(EmbeddedValue.class)));
        SerializerFactory factory = new SerializerFactory();
        factory.addFactory(extensions);

        assertEquals("codec:snapshot", SerializationSupport.roundTrip(original));
        assertEquals("replacement:snapshot", SerializationSupport.roundTrip(original, factory, true));
    }

    public static class EmbeddedValue implements Serializable {
        private final String text;

        public EmbeddedValue(String text) {
            this.text = text;
        }

        private Object writeReplace() {
            return "replacement:" + text;
        }
    }

    public static class EmbeddedValueHessianSerializer extends AbstractSerializer {
        @Override
        public void writeObject(Object object, AbstractHessianOutput output) throws IOException {
            output.writeString("codec:" + ((EmbeddedValue) object).text);
        }
    }

    public static class Value implements Serializable {
        private String text;

        public Value(String text) {
            this.text = text;
        }

        private Object writeReplace() {
            return "wire:" + text;
        }
    }
}
