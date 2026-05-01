/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.htrace.fasterxml.jackson.annotation.JsonProperty;
import org.apache.htrace.fasterxml.jackson.databind.BeanDescription;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.apache.htrace.fasterxml.jackson.databind.introspect.AnnotatedField;
import org.apache.htrace.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedFieldTest {
    @Test
    void readsAndWritesFieldThroughAnnotatedFieldAccessor() {
        AnnotatedField field = findField("value");
        FieldBackedBean bean = new FieldBackedBean("initial-value");

        Object initialValue = field.getValue(bean);
        field.setValue(bean, "updated-value");

        assertThat(field.getName()).isEqualTo("value");
        assertThat(initialValue).isEqualTo("initial-value");
        assertThat(field.getValue(bean)).isEqualTo("updated-value");
        assertThat(bean.value).isEqualTo("updated-value");
    }

    @Test
    void resolvesSerializedAnnotatedFieldBackToDeclaredField() throws Exception {
        AnnotatedField field = findField("value");

        AnnotatedField restored = roundTripThroughJavaSerialization(field);
        FieldBackedBean bean = new FieldBackedBean("resolved-value");

        assertThat(restored.getName()).isEqualTo("value");
        assertThat(restored.getDeclaringClass()).isEqualTo(FieldBackedBean.class);
        assertThat(restored.getValue(bean)).isEqualTo("resolved-value");
    }

    private static AnnotatedField findField(String propertyName) {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(FieldBackedBean.class));
        BeanPropertyDefinition property = findProperty(description, propertyName);
        AnnotatedField field = property.getField();

        assertThat(field).isNotNull();
        return field;
    }

    private static BeanPropertyDefinition findProperty(BeanDescription description, String propertyName) {
        for (BeanPropertyDefinition property : description.findProperties()) {
            if (propertyName.equals(property.getName())) {
                return property;
            }
        }
        throw new IllegalArgumentException("Missing property: " + propertyName);
    }

    private static AnnotatedField roundTripThroughJavaSerialization(AnnotatedField field) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(field);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (AnnotatedField) input.readObject();
        }
    }

    public static class FieldBackedBean {
        @JsonProperty("value")
        public String value;

        public FieldBackedBean(String value) {
            this.value = value;
        }
    }
}
