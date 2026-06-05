/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedFieldTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void setsFieldDiscoveredByJacksonIntrospection() {
        AnnotatedField field = fieldFor(FieldTarget.class, "name");
        FieldTarget target = new FieldTarget();

        field.setValue(target, "coffee");

        assertThat(target.name).isEqualTo("coffee");
    }

    @Test
    void jdkSerializationRestoresFieldDiscoveredByJacksonIntrospection() throws Exception {
        AnnotatedField original = fieldFor(SerializableFieldTarget.class, "name");

        AnnotatedField restored = roundTrip(original);
        SerializableFieldTarget target = new SerializableFieldTarget();
        restored.setValue(target, "latte");

        assertThat(restored).isEqualTo(original);
        assertThat(target.name).isEqualTo("latte");
    }

    private static AnnotatedField fieldFor(Class<?> type, String name) {
        BeanDescription beanDescription = MAPPER.getDeserializationConfig()
                .introspect(MAPPER.constructType(type));
        return beanDescription.findProperties().stream()
                .filter(property -> property.getName().equals(name))
                .map(BeanPropertyDefinition::getField)
                .filter(field -> field != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field found for property " + name));
    }

    private static AnnotatedField roundTrip(AnnotatedField field) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(field);
        }
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (AnnotatedField) objectInput.readObject();
        }
    }

    public static final class FieldTarget {
        public String name;
    }

    public static final class SerializableFieldTarget {
        public String name;
    }
}
