/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonFormat;
import shaded.parquet.com.fasterxml.jackson.databind.JsonSerializer;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import shaded.parquet.com.fasterxml.jackson.databind.ser.PropertyWriter;
import shaded.parquet.com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;

public class BeanPropertyWriterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void readsFieldAndMethodValuesThroughBeanPropertyWriterGet() throws Exception {
        BeanPropertyWriter fieldWriter = propertyWriterFor(FieldBackedBean.class, "fieldValue");
        BeanPropertyWriter methodWriter = propertyWriterFor(MethodBackedBean.class, "methodValue");

        assertThat(fieldWriter.get(new FieldBackedBean("field read"))).isEqualTo("field read");
        assertThat(methodWriter.get(new MethodBackedBean("method read"))).isEqualTo("method read");
    }

    @Test
    void serializesFieldAndMethodPropertiesAsObjectFields() throws Exception {
        assertThat(MAPPER.writeValueAsString(new FieldBackedBean("field serialized")))
                .isEqualTo("{\"fieldValue\":\"field serialized\"}");
        assertThat(MAPPER.writeValueAsString(new MethodBackedBean("method serialized")))
                .isEqualTo("{\"methodValue\":\"method serialized\"}");
    }

    @Test
    void serializesFieldAndMethodPropertiesAsArrayElements() throws Exception {
        assertThat(MAPPER.writeValueAsString(new ArrayFieldBackedBean("field element")))
                .isEqualTo("[\"field element\"]");
        assertThat(MAPPER.writeValueAsString(new ArrayMethodBackedBean("method element")))
                .isEqualTo("[\"method element\"]");
    }

    private static BeanPropertyWriter propertyWriterFor(Class<?> beanClass, String propertyName) throws Exception {
        JsonSerializer<Object> serializer = MAPPER.getSerializerProviderInstance().findValueSerializer(beanClass);

        assertThat(serializer).isInstanceOf(BeanSerializerBase.class);
        BeanSerializerBase beanSerializer = (BeanSerializerBase) serializer;
        Iterator<PropertyWriter> properties = beanSerializer.properties();
        while (properties.hasNext()) {
            PropertyWriter property = properties.next();
            if (propertyName.equals(property.getName())) {
                assertThat(property).isInstanceOf(BeanPropertyWriter.class);
                return (BeanPropertyWriter) property;
            }
        }
        throw new AssertionError("Missing bean property writer for " + beanClass.getName() + "." + propertyName);
    }

    public static final class FieldBackedBean {
        public String fieldValue;

        public FieldBackedBean(String fieldValue) {
            this.fieldValue = fieldValue;
        }
    }

    public static final class MethodBackedBean {
        private final String methodValue;

        public MethodBackedBean(String methodValue) {
            this.methodValue = methodValue;
        }

        public String getMethodValue() {
            return methodValue;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class ArrayFieldBackedBean {
        public String fieldValue;

        public ArrayFieldBackedBean(String fieldValue) {
            this.fieldValue = fieldValue;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class ArrayMethodBackedBean {
        private final String methodValue;

        public ArrayMethodBackedBean(String methodValue) {
            this.methodValue = methodValue;
        }

        public String getMethodValue() {
            return methodValue;
        }
    }
}
