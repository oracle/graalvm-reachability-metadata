/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.AbstractSerializer;
import com.caucho.hessian.io.AbstractMapDeserializer;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class ContextSerializerFactoryTest {
    @Test
    void discoversConventionNamedCodecs() throws Exception {
        Value restored = (Value) SerializationSupport.roundTrip(new Value("snapshot"));
        assertEquals("snapshot", restored.text);
    }

    public static class Value {
        private final String text;

        public Value(String text) {
            this.text = text;
        }
    }

    public static class ValueHessianSerializer extends AbstractSerializer {
        @Override
        public void writeObject(Object object, AbstractHessianOutput output) throws IOException {
            output.writeMapBegin(Value.class.getName());
            output.writeString("text");
            output.writeString(((Value) object).text);
            output.writeMapEnd();
        }
    }

    public static class ValueHessianDeserializer extends AbstractMapDeserializer {
        @Override
        public Class<?> getType() {
            return Value.class;
        }

        @Override
        public Object readMap(AbstractHessianInput input) throws IOException {
            String text = null;
            while (!input.isEnd()) {
                String key = input.readString();
                String value = input.readString();
                if ("text".equals(key)) {
                    text = value;
                }
            }
            input.readMapEnd();
            return new Value(text);
        }
    }
}
