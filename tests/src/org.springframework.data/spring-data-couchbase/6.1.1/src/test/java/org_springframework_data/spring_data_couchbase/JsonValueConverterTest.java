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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.couchbase.core.convert.CouchbasePropertyValueConverterFactory;
import org.springframework.data.couchbase.core.convert.JsonValueConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentProperty;
import org.springframework.data.couchbase.core.mapping.Document;

@Timeout(60)
public class JsonValueConverterTest {

    @Test
    void writesJsonValueAndReadsJsonCreatorValue() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentProperty property = mappingContext.getRequiredPersistentEntity(Holder.class)
                .getRequiredPersistentProperty("code");
        CouchbasePropertyValueConverterFactory factory = new CouchbasePropertyValueConverterFactory(
                null, Map.of(JsonValue.class, JsonValueConverter.class), new ObjectMapper());
        JsonValueConverter converter = (JsonValueConverter) factory.getConverter(property);
        ValueConversionContext<CouchbasePersistentProperty> context = () -> property;

        assertThat(converter.write(new Code("written"), context)).isEqualTo("written");
        assertThat(((Code) converter.read("read", context)).value()).isEqualTo("read");
    }

    @Document
    private static class Holder {
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
