/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.core.JsonParser;
import shaded.parquet.com.fasterxml.jackson.databind.DeserializationContext;
import shaded.parquet.com.fasterxml.jackson.databind.JavaType;
import shaded.parquet.com.fasterxml.jackson.databind.JsonDeserializer;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import shaded.parquet.com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import shaded.parquet.com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import shaded.parquet.com.fasterxml.jackson.databind.deser.impl.FieldProperty;

public class FieldPropertyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesAndSetsFieldBackedProperty() throws Exception {
        SettableBeanProperty property = fieldPropertyFor(FieldBackedBean.class, "value");
        FieldBackedBean target = new FieldBackedBean();

        try (JsonParser parser = parserFor("\"from deserializeAndSet\"")) {
            property.deserializeAndSet(parser, deserializationContext(parser), target);
        }

        assertThat(target.value).isEqualTo("from deserializeAndSet");
    }

    @Test
    void deserializesSetsAndReturnsSameFieldBackedInstance() throws Exception {
        SettableBeanProperty property = fieldPropertyFor(FieldBackedBean.class, "value");
        FieldBackedBean target = new FieldBackedBean();

        Object returned;
        try (JsonParser parser = parserFor("\"from deserializeSetAndReturn\"")) {
            returned = property.deserializeSetAndReturn(parser, deserializationContext(parser), target);
        }

        assertThat(returned).isSameAs(target);
        assertThat(target.value).isEqualTo("from deserializeSetAndReturn");
    }

    @Test
    void setsFieldBackedPropertyValue() throws Exception {
        SettableBeanProperty property = fieldPropertyFor(FieldBackedBean.class, "value");
        FieldBackedBean target = new FieldBackedBean();

        property.set(target, "from set");

        assertThat(target.value).isEqualTo("from set");
    }

    @Test
    void setsFieldBackedPropertyValueAndReturnsSameInstance() throws Exception {
        SettableBeanProperty property = fieldPropertyFor(FieldBackedBean.class, "value");
        FieldBackedBean target = new FieldBackedBean();

        Object returned = property.setAndReturn(target, "from setAndReturn");

        assertThat(returned).isSameAs(target);
        assertThat(target.value).isEqualTo("from setAndReturn");
    }

    private static SettableBeanProperty fieldPropertyFor(Class<?> beanClass, String propertyName) throws Exception {
        JavaType type = MAPPER.constructType(beanClass);
        JsonDeserializer<Object> deserializer = deserializationContext(null).findRootValueDeserializer(type);

        assertThat(deserializer).isInstanceOf(BeanDeserializerBase.class);
        BeanDeserializerBase beanDeserializer = (BeanDeserializerBase) deserializer;
        SettableBeanProperty property = beanDeserializer.findProperty(propertyName);

        assertThat(property).isInstanceOf(FieldProperty.class);
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

    public static final class FieldBackedBean {
        public String value;
    }
}
