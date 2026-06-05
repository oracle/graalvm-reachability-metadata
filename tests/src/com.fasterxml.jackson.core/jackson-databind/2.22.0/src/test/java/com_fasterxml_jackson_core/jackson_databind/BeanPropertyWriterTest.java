/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterTest {

    @Test
    void serializesFieldBackedPropertyAsArrayElement() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new FieldBackedArrayBean("coffee"));

        assertThat(json).isEqualTo("[\"coffee\"]");
    }

    @Test
    void serializesGetterBackedPropertyAsArrayElement() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new GetterBackedArrayBean("espresso"));

        assertThat(json).isEqualTo("[\"espresso\"]");
    }

    @Test
    void readsFieldBackedPropertyThroughBeanPropertyWriter() throws JsonProcessingException {
        ValueCheckingModifier modifier = new ValueCheckingModifier(FieldBackedObjectBean.class, "coffee");
        ObjectMapper mapper = mapperWith(modifier);

        String json = mapper.writeValueAsString(new FieldBackedObjectBean("coffee"));

        assertThat(json).isEqualTo("{\"name\":\"coffee\"}");
        assertThat(modifier.values()).containsExactly("coffee");
    }

    @Test
    void invokesGetterBackedPropertyThroughBeanPropertyWriter() throws JsonProcessingException {
        ValueCheckingModifier modifier = new ValueCheckingModifier(GetterBackedObjectBean.class, "espresso");
        ObjectMapper mapper = mapperWith(modifier);

        String json = mapper.writeValueAsString(new GetterBackedObjectBean("espresso"));

        assertThat(json).isEqualTo("{\"name\":\"espresso\"}");
        assertThat(modifier.values()).containsExactly("espresso");
    }

    private static ObjectMapper mapperWith(BeanSerializerModifier modifier) {
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(modifier);
        return new ObjectMapper().registerModule(module);
    }

    private static final class ValueCheckingModifier extends BeanSerializerModifier {
        private final Class<?> beanClass;
        private final Object expectedValue;
        private final List<Object> values = new ArrayList<>();

        private ValueCheckingModifier(Class<?> beanClass, Object expectedValue) {
            this.beanClass = beanClass;
            this.expectedValue = expectedValue;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                List<BeanPropertyWriter> beanProperties) {
            if (beanDesc.getBeanClass() != beanClass) {
                return beanProperties;
            }
            List<BeanPropertyWriter> writers = new ArrayList<>(beanProperties.size());
            for (BeanPropertyWriter writer : beanProperties) {
                writers.add(new ValueCheckingBeanPropertyWriter(writer, expectedValue, values));
            }
            return writers;
        }

        private List<Object> values() {
            return values;
        }
    }

    private static final class ValueCheckingBeanPropertyWriter extends BeanPropertyWriter {
        private final Object expectedValue;
        private final List<Object> values;

        private ValueCheckingBeanPropertyWriter(BeanPropertyWriter base, Object expectedValue, List<Object> values) {
            super(base);
            this.expectedValue = expectedValue;
            this.values = values;
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            Object value = get(bean);
            assertThat(value).isEqualTo(expectedValue);
            values.add(value);
            super.serializeAsField(bean, gen, prov);
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class FieldBackedArrayBean {
        public final String name;

        public FieldBackedArrayBean(String name) {
            this.name = name;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class GetterBackedArrayBean {
        private final String name;

        public GetterBackedArrayBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class FieldBackedObjectBean {
        public final String name;

        public FieldBackedObjectBean(String name) {
            this.name = name;
        }
    }

    public static final class GetterBackedObjectBean {
        private final String name;

        public GetterBackedObjectBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
