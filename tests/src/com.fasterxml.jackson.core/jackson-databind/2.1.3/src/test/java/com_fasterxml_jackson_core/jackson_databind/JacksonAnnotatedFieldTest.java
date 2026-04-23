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
import java.lang.reflect.Field;

import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotatedFieldTest {

    @Test
    void annotatedFieldReadsWritesAndResolvesAfterSerialization() throws Exception {
        Field field = FieldTarget.class.getDeclaredField("value");
        AnnotatedField annotatedField = new AnnotatedField(field, new AnnotationMap());
        FieldTarget target = new FieldTarget();

        annotatedField.setValue(target, "updated");
        assertThat(annotatedField.getValue(target)).isEqualTo("updated");

        AnnotatedField restored = reserialize(annotatedField);
        assertThat(restored.getValue(target)).isEqualTo("updated");
    }

    private static <T> T reserialize(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            T restored = (T) input.readObject();
            return restored;
        }
    }

    public static class FieldTarget {

        public String value = "initial";
    }
}
