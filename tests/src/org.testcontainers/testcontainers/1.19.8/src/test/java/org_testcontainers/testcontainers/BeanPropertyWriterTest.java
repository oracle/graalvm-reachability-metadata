/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonSerializer;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.SerializerProvider;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ser.PropertyWriter;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterTest {
    @Test
    void serializesFieldAndMethodPropertiesAsArrayElements() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayBean bean = new ArrayBean("field", "method");
        String json = mapper.writeValueAsString(bean);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        JsonSerializer<Object> serializer = provider.findValueSerializer(ArrayBean.class);
        Iterator<PropertyWriter> properties = ((BeanSerializerBase) serializer).properties();
        List<Object> values = new ArrayList<>();
        while (properties.hasNext()) {
            values.add(((BeanPropertyWriter) properties.next()).get(bean));
        }

        assertThat(json).isEqualTo("[\"field\",\"method\"]");
        assertThat(values).containsExactly("field", "method");
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "fieldValue", "methodValue" })
    public static class ArrayBean {
        public String fieldValue;
        private final String methodValue;

        public ArrayBean(String fieldValue, String methodValue) {
            this.fieldValue = fieldValue;
            this.methodValue = methodValue;
        }

        public String getMethodValue() {
            return methodValue;
        }
    }
}
