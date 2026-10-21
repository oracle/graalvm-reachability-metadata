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

public class HessianSerializerOutputTest {
    @Test
    void writesFieldsAndHonorsWriteReplacement() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SerializerFactory factory = new SerializerFactory();
        factory.setAllowNonSerializable(true);
        HessianSerializerOutput output = new HessianSerializerOutput(bytes);
        output.setSerializerFactory(factory);
        output.writeObjectImpl(new FieldValue("field"));
        output.writeObjectImpl(new ReplacingValue("replacement"));
        output.flush();

        HessianSerializerInput input =
                new HessianSerializerInput(new ByteArrayInputStream(bytes.toByteArray()));
        input.setSerializerFactory(factory);
        FieldValue fieldValue = (FieldValue) input.readObject(FieldValue.class);
        FieldValue replacement = (FieldValue) input.readObject(FieldValue.class);

        assertThat(fieldValue.value).isEqualTo("field");
        assertThat(replacement.value).isEqualTo("replacement");
    }

    public static class FieldValue {
        String value;

        public FieldValue() { }

        FieldValue(String value) {
            this.value = value;
        }
    }

    public static class ReplacingValue {
        private String value;

        public ReplacingValue() { }

        ReplacingValue(String value) {
            this.value = value;
        }

        public Object writeReplace() {
            return new FieldValue(value);
        }
    }
}
