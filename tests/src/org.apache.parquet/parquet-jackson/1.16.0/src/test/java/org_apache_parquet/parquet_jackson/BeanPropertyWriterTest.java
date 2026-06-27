/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonFilter;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonFormat;
import shaded.parquet.com.fasterxml.jackson.core.JsonGenerator;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.SerializerProvider;
import shaded.parquet.com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import shaded.parquet.com.fasterxml.jackson.databind.ser.PropertyWriter;
import shaded.parquet.com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import shaded.parquet.com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

public class BeanPropertyWriterTest {
    @Test
    void serializesFieldAndGetterPropertiesAsObjectFields() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final CapturingFilter filter = new CapturingFilter();
        final SimpleFilterProvider filters = new SimpleFilterProvider()
                .addFilter("capture", filter);

        final String json = mapper.writer(filters)
                .writeValueAsString(new FilteredBean("field-value", "method-value"));

        assertThat(json).contains("\"fieldValue\":\"field-value\"");
        assertThat(json).contains("\"methodValue\":\"method-value\"");
        assertThat(filter.values()).containsEntry("fieldValue", "field-value")
                .containsEntry("methodValue", "method-value");
    }

    @Test
    void serializesFieldAndGetterPropertiesAsArrayElements() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final String json = mapper.writeValueAsString(new ArrayBean("field-value", "method-value"));

        assertThat(json).contains("\"field-value\"");
        assertThat(json).contains("\"method-value\"");
        assertThat(json).startsWith("[").endsWith("]");
    }

    private static final class CapturingFilter extends SimpleBeanPropertyFilter {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public void serializeAsField(
                Object pojo,
                JsonGenerator generator,
                SerializerProvider provider,
                BeanPropertyWriter writer) throws Exception {
            values.put(writer.getName(), writer.get(pojo));
            writer.serializeAsField(pojo, generator, provider);
        }

        @Override
        public void serializeAsField(
                Object pojo,
                JsonGenerator generator,
                SerializerProvider provider,
                PropertyWriter writer) throws Exception {
            if (writer instanceof BeanPropertyWriter) {
                values.put(writer.getName(), ((BeanPropertyWriter) writer).get(pojo));
            }
            writer.serializeAsField(pojo, generator, provider);
        }

        Map<String, Object> values() {
            return values;
        }
    }

    @JsonFilter("capture")
    public static final class FilteredBean {
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
    public static final class ArrayBean {
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
