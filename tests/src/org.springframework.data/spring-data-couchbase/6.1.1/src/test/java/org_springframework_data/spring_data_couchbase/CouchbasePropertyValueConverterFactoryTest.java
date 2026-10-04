/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.couchbase.core.convert.CouchbasePropertyValueConverterFactory;
import org.springframework.data.couchbase.core.convert.JsonValueConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentEntity;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentProperty;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.mapping.PersistentProperty;

@Timeout(60)
public class CouchbasePropertyValueConverterFactoryTest {

    @Test
    void discoversTypeAndPropertyConverters() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Holder.class);
        CouchbasePropertyValueConverterFactory factory = new CouchbasePropertyValueConverterFactory(
                null,
                Map.of(PropertyConverterMarker.class, PropertyConverter.class,
                        JsonValue.class, JsonValueConverter.class),
                new ObjectMapper());

        PropertyValueConverter<?, ?, ?> propertyConverter = factory.getConverter(
                entity.getRequiredPersistentProperty("converted"));
        PropertyValueConverter<?, ?, ?> typeConverter = factory.getConverter(
                entity.getRequiredPersistentProperty("code"));

        assertThat(propertyConverter).isInstanceOf(PropertyConverter.class);
        assertThat(typeConverter).isInstanceOf(JsonValueConverter.class);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface PropertyConverterMarker {
    }

    public static class PropertyConverter
            implements PropertyValueConverter<Object, Object, ValueConversionContext<CouchbasePersistentProperty>> {
        public PropertyConverter(PersistentProperty<?> property) {
        }

        @Override
        public Object read(Object value, ValueConversionContext<CouchbasePersistentProperty> context) {
            return value;
        }

        @Override
        public Object write(Object value, ValueConversionContext<CouchbasePersistentProperty> context) {
            return value;
        }
    }

    @Document
    private static class Holder {
        @PropertyConverterMarker
        private String converted;
        private Code code;
    }

    public static class Code {
        private final String value;

        @JsonCreator
        public Code(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
