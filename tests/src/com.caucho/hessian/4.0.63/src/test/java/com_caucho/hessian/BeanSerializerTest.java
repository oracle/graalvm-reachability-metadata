/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.BeanSerializer;
import com.caucho.hessian.io.BeanSerializerFactory;
import com.caucho.hessian.io.ExtSerializerFactory;
import com.caucho.hessian.io.FieldDeserializer2Factory;
import com.caucho.hessian.io.JavaDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class BeanSerializerTest {
    @Test
    void serializesBeanPropertiesInBothProtocols() throws Exception {
        Bean original = new Bean();
        original.setName("leader");
        original.setCount(17);
        for (boolean hessian2 : new boolean[] {false, true}) {
            BeanSerializerFactory factory = new BeanSerializerFactory();
            if (hessian2) {
                ExtSerializerFactory decoders = new ExtSerializerFactory();
                decoders.addDeserializer(Bean.class, new JavaDeserializer(Bean.class, new FieldDeserializer2Factory()));
                factory.addFactory(decoders);
            }
            Bean restored = (Bean) SerializationSupport.roundTrip(original, factory, hessian2);
            assertEquals("leader", restored.getName());
            assertEquals(17, restored.getCount());
        }
    }

    @Test
    void serializesPublicWriteReplacement() throws Exception {
        ExtSerializerFactory serializers = new ExtSerializerFactory();
        serializers.addSerializer(ReplacedBean.class,
                new BeanSerializer(ReplacedBean.class, ReplacedBean.class.getClassLoader()));
        BeanSerializerFactory factory = new BeanSerializerFactory();
        factory.addFactory(serializers);
        for (boolean hessian2 : new boolean[] {false, true}) {
            assertEquals("bean:snapshot", SerializationSupport.roundTrip(new ReplacedBean(), factory, hessian2));
        }
    }

    @Test
    void discoversConventionNamedReplacementFactory() throws Exception {
        ExtSerializerFactory serializers = new ExtSerializerFactory();
        serializers.addSerializer(ExternalBean.class,
                new BeanSerializer(ExternalBean.class, ExternalBean.class.getClassLoader()));
        BeanSerializerFactory factory = new BeanSerializerFactory();
        factory.addFactory(serializers);
        for (boolean hessian2 : new boolean[] {false, true}) {
            assertEquals("external:snapshot", SerializationSupport.roundTrip(new ExternalBean("snapshot"),
                    factory, hessian2));
        }
    }

    public static class ReplacedBean {
        public Object writeReplace() {
            return "bean:snapshot";
        }
    }

    public static class ExternalBean {
        private final String text;

        public ExternalBean(String text) {
            this.text = text;
        }
    }

    public static class ExternalBeanHessianSerializer {
        public Object writeReplace(ExternalBean bean) {
            return "external:" + bean.text;
        }
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
}
