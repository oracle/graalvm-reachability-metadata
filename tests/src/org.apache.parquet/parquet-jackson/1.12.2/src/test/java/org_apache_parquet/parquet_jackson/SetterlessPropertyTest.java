/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonAutoDetect;
import shaded.parquet.com.fasterxml.jackson.core.JsonParser;
import shaded.parquet.com.fasterxml.jackson.databind.DeserializationContext;
import shaded.parquet.com.fasterxml.jackson.databind.JavaType;
import shaded.parquet.com.fasterxml.jackson.databind.JsonDeserializer;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import shaded.parquet.com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import shaded.parquet.com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import shaded.parquet.com.fasterxml.jackson.databind.deser.impl.SetterlessProperty;

public class SetterlessPropertyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesIntoGetterReturnedCollection() throws Exception {
        SettableBeanProperty property = setterlessPropertyFor(SetterlessBean.class, "values");
        SetterlessBean target = new SetterlessBean();

        try (JsonParser parser = parserFor("[\"alpha\",\"bravo\"]")) {
            property.deserializeAndSet(parser, deserializationContext(parser), target);
        }

        assertThat(target.getValues()).containsExactly("alpha", "bravo");
    }

    private static SettableBeanProperty setterlessPropertyFor(Class<?> beanClass, String propertyName) throws Exception {
        JavaType type = MAPPER.constructType(beanClass);
        JsonDeserializer<Object> deserializer = deserializationContext(null).findRootValueDeserializer(type);

        assertThat(deserializer).isInstanceOf(BeanDeserializerBase.class);
        BeanDeserializerBase beanDeserializer = (BeanDeserializerBase) deserializer;
        SettableBeanProperty property = beanDeserializer.findProperty(propertyName);

        assertThat(property).isInstanceOf(SetterlessProperty.class);
        return property;
    }

    private static JsonParser parserFor(String json) throws Exception {
        JsonParser parser = MAPPER.getFactory().createParser(json);
        parser.nextToken();
        return parser;
    }

    private static DeserializationContext deserializationContext(JsonParser parser) {
        DefaultDeserializationContext context = (DefaultDeserializationContext) MAPPER.getDeserializationContext();
        return context.createInstance(MAPPER.getDeserializationConfig(), parser, MAPPER.getInjectableValues());
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class SetterlessBean {
        private final List<String> entries = new ArrayList<>();

        public List<String> getValues() {
            return entries;
        }
    }
}
