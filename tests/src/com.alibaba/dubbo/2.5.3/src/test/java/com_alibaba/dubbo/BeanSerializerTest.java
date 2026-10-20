/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.com.caucho.hessian.io.BeanSerializer;
import com.alibaba.com.caucho.hessian.io.BeanSerializerFactory;
import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import com.alibaba.com.caucho.hessian.io.SerializerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

public class BeanSerializerTest {
    @Test
    void serializesBeanPropertiesThroughAccessors() throws Exception {
        Bean source = new Bean();
        source.setName("Ada");
        source.setCount(3);

        Bean copy = (Bean) HessianTestSupport.roundTripBean(source);

        assertThat(copy.getName()).isEqualTo("Ada");
        assertThat(copy.getCount()).isEqualTo(3);

    }

    @Test
    void honorsObjectAndCompanionWriteReplacement() throws Exception {
        Replacement objectReplacement =
                (Replacement)
                        roundTripWithHessian2BeanSerializer(
                                new SelfReplacingBean("object"), Replacement.class);
        Replacement factoryReplacement =
                (Replacement)
                        HessianTestSupport.roundTripBean(
                                new FactoryValue("factory"), Replacement.class);

        assertThat(objectReplacement.getValue()).isEqualTo("object");
        assertThat(factoryReplacement.getValue()).isEqualTo("factory");
    }

    private static Object roundTripWithHessian2BeanSerializer(
            Object value, Class<?> expectedType) throws Exception {
        BeanSerializerFactory outputFactory = new BeanSerializerFactory();
        outputFactory.setAllowNonSerializable(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.setSerializerFactory(outputFactory);
        BeanSerializer serializer =
                new BeanSerializer(value.getClass(), outputFactory.getClassLoader());
        serializer.writeObject(value, output);
        output.flush();

        SerializerFactory inputFactory = new SerializerFactory();
        inputFactory.setAllowNonSerializable(true);
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        input.setSerializerFactory(inputFactory);
        return input.readObject(expectedType);
    }

    public static class Bean {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class SelfReplacingBean {
        private String value;

        public SelfReplacingBean() { }

        SelfReplacingBean(String value) {
            this.value = value;
        }

        public Replacement writeReplace() {
            return new Replacement(value);
        }
    }

    public static class FactoryValue {
        private String value;

        public FactoryValue() { }

        FactoryValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class FactoryValueHessianSerializer {
        public Replacement writeReplace(FactoryValue value) {
            return new Replacement(value.getValue());
        }
    }

    public static class Replacement {
        private String value;

        public Replacement() { }

        Replacement(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
