/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.core.convert.CouchbasePropertyValueConverterFactory;
import org.springframework.data.couchbase.core.convert.JsonValueConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentProperty;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mapping.PersistentProperty;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class CouchbasePropertyValueConverterFactoryTest {

    @Test
    void instantiatesPropertyAwareConverter() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentProperty property = mappingContext.getPersistentEntity(PropertyAwareDocument.class)
                .getPersistentProperty("value");
        Map<Class<? extends Annotation>, Class<?>> converterTypes = new HashMap<>();
        converterTypes.put(JsonValue.class, PropertyAwareConverter.class);
        CouchbasePropertyValueConverterFactory factory = new CouchbasePropertyValueConverterFactory(null,
                converterTypes, new ObjectMapper());

        PropertyAwareConverter converter = (PropertyAwareConverter) factory.getConverter(property);

        assertThat(converter.getProperty()).isSameAs(property);
    }

    @Test
    void discoversJsonValuePropertyConverter() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentProperty property = mappingContext.getPersistentEntity(JsonBackedDocument.class)
                .getPersistentProperty("value");
        Map<Class<? extends Annotation>, Class<?>> converterTypes = new HashMap<>();
        converterTypes.put(JsonValue.class, JsonValueConverter.class);
        CouchbasePropertyValueConverterFactory factory = new CouchbasePropertyValueConverterFactory(null,
                converterTypes, new ObjectMapper());

        PropertyValueConverter<?, ?, ?> converter = factory.getConverter(property);

        assertThat(converter).isInstanceOf(JsonValueConverter.class);
    }

    public static class PropertyAwareConverter
            implements PropertyValueConverter<Object, Object, ValueConversionContext<? extends PersistentProperty<?>>> {

        private final PersistentProperty<?> property;

        public PropertyAwareConverter(PersistentProperty<?> property) {
            this.property = property;
        }

        public PersistentProperty<?> getProperty() {
            return property;
        }

        @Override
        public Object read(Object value, ValueConversionContext<? extends PersistentProperty<?>> context) {
            return value;
        }

        @Override
        public Object write(Object value, ValueConversionContext<? extends PersistentProperty<?>> context) {
            return value;
        }
    }

    public static class PropertyAwareDocument {

        public PropertyAwareValue value;
    }

    public static class PropertyAwareValue {

        @JsonValue
        public String asString() {
            return "value";
        }
    }

    public static class JsonBackedDocument {

        public JsonBackedValue value;
    }

    public static class JsonBackedValue {

        private final String value;

        public JsonBackedValue(String value) {
            this.value = value;
        }

        @JsonValue
        public String asString() {
            return value;
        }
    }
}
