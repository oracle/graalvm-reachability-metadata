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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

public class HessianSerializerInputTest {
    @Test
    void restoresFieldsAndHonorsReadResolution() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianSerializerOutput output = new HessianSerializerOutput(bytes);
        output.writeObject(new ResolvingValue("dubbo"));
        output.flush();

        HessianSerializerInput input =
                new HessianSerializerInput(new ByteArrayInputStream(bytes.toByteArray()));
        ResolvingValue value = (ResolvingValue) input.readObject(ResolvingValue.class);

        assertThat(value.value).isEqualTo("DUBBO");
    }

    public static class ResolvingValue {
        String value;

        public ResolvingValue() {}

        ResolvingValue(String value) {
            this.value = value;
        }

        public Object readResolve() {
            return new ResolvingValue(value.toUpperCase());
        }
    }
}
