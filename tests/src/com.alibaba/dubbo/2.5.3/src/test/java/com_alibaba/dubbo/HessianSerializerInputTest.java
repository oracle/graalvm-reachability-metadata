/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.com.caucho.hessian.io.HessianSerializerInput;
import com.alibaba.com.caucho.hessian.io.HessianSerializerOutput;
import com.alibaba.com.caucho.hessian.io.SerializerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

public class HessianSerializerInputTest {
    @Test
    void restoresFieldsAndAppliesReadResolution() throws Exception {
        SerializerFactory factory = new SerializerFactory();
        factory.setAllowNonSerializable(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianSerializerOutput output = new HessianSerializerOutput(bytes);
        output.setSerializerFactory(factory);
        output.writeObjectImpl(new SerializedValue("restored", 7));
        output.flush();

        HessianSerializerInput input =
                new HessianSerializerInput(new ByteArrayInputStream(bytes.toByteArray()));
        input.setSerializerFactory(factory);
        ResolvedValue value = (ResolvedValue) input.readObject(SerializedValue.class);

        assertThat(value.name).isEqualTo("restored");
        assertThat(value.count).isEqualTo(7);
    }

    public static class SerializedValue {
        String name;
        int count;

        public SerializedValue() { }

        SerializedValue(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public Object readResolve() {
            return new ResolvedValue(name, count);
        }
    }

    public static class ResolvedValue {
        final String name;
        final int count;

        ResolvedValue(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
