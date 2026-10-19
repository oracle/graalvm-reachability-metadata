/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pulsar.shade.com.fasterxml.jackson.core.JsonGenerator;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ser.PropertyWriter;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.junit.jupiter.api.Test;

public class BeanPropertyWriterTest {
    @Test
    void serializesFieldAndGetterPropertiesAsFieldsAndArrayElements() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CapturingFilter filter = new CapturingFilter();
        SimpleFilterProvider filters = new SimpleFilterProvider().addFilter("capture", filter);

        String objectJson = mapper.writer(filters).writeValueAsString(new FilteredBean("field", "getter"));
        String arrayJson = mapper.writeValueAsString(new ArrayBean("field", "getter"));

        assertThat(objectJson).contains("\"fieldValue\":\"field\"").contains("\"methodValue\":\"getter\"");
        assertThat(arrayJson).isEqualTo("[\"field\",\"getter\"]");
        assertThat(filter.values).containsEntry("fieldValue", "field").containsEntry("methodValue", "getter");
    }

    public static class CapturingFilter extends SimpleBeanPropertyFilter {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public void serializeAsField(
                Object bean, JsonGenerator generator, SerializerProvider provider, PropertyWriter writer)
                throws Exception {
            if (writer instanceof BeanPropertyWriter beanWriter) {
                values.put(writer.getName(), beanWriter.get(bean));
            }
            writer.serializeAsField(bean, generator, provider);
        }
    }

    @JsonFilter("capture")
    public static class FilteredBean {
        public final String fieldValue;
        private final String methodValue;

        public FilteredBean(String fieldValue, String methodValue) {
            this.fieldValue = fieldValue;
            this.methodValue = methodValue;
        }

        public String getMethodValue() {
            return methodValue;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class ArrayBean {
        public final String fieldValue;
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
