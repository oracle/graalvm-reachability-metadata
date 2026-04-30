/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.BeanDescription;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedField;

public class AnnotatedFieldTest {
    @Test
    void getsAndSetsBeanValuesThroughAnnotatedField() {
        FieldTarget target = new FieldTarget("initial");
        AnnotatedField field = annotatedField("name");

        assertThat(field.getValue(target)).isEqualTo("initial");
        field.setValue(target, "updated");

        assertThat(field.getValue(target)).isEqualTo("updated");
    }

    @Test
    void resolvesFieldAfterJavaSerializationRoundTrip() throws Exception {
        AnnotatedField original = annotatedField("name");

        AnnotatedField restored = serializeAndDeserialize(original);
        FieldTarget target = new FieldTarget("restored");

        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getDeclaringClass()).isEqualTo(FieldTarget.class);
        assertThat(restored.getValue(target)).isEqualTo("restored");
    }

    private static AnnotatedField annotatedField(String name) {
        for (AnnotatedField field : beanDescription().getClassInfo().fields()) {
            if (name.equals(field.getName())) {
                return field;
            }
        }
        throw new AssertionError("Missing field " + name);
    }

    private static BeanDescription beanDescription() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.getSerializationConfig().introspect(mapper.constructType(FieldTarget.class));
    }

    private static AnnotatedField serializeAndDeserialize(AnnotatedField field) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(field);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (AnnotatedField) objectInput.readObject();
        }
    }

    public static final class FieldTarget {
        public String name;

        public FieldTarget(String name) {
            this.name = name;
        }
    }
}
