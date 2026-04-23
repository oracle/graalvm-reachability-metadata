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
import java.lang.reflect.Constructor;

import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotatedConstructorTest {

    @Test
    void annotatedConstructorCreatesInstancesAndResolvesAfterSerialization() throws Exception {
        Constructor<ConstructorTarget> defaultConstructor = ConstructorTarget.class.getDeclaredConstructor();
        AnnotatedConstructor annotatedDefault = new AnnotatedConstructor(defaultConstructor, new AnnotationMap(), null);
        ConstructorTarget defaultValue = (ConstructorTarget) annotatedDefault.call();
        assertThat(defaultValue.value).isEqualTo("default");

        Constructor<ConstructorTarget> stringConstructor = ConstructorTarget.class.getDeclaredConstructor(String.class);
        AnnotatedConstructor annotatedString = new AnnotatedConstructor(stringConstructor, new AnnotationMap(), null);
        assertThat(((ConstructorTarget) annotatedString.call(new Object[] { "array" })).value).isEqualTo("array");
        assertThat(((ConstructorTarget) annotatedString.call1("single")).value).isEqualTo("single");

        AnnotatedConstructor restored = reserialize(annotatedString);
        assertThat(((ConstructorTarget) restored.call1("restored")).value).isEqualTo("restored");
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

    static class ConstructorTarget {

        final String value;

        ConstructorTarget() {
            this("default");
        }

        ConstructorTarget(String value) {
            this.value = value;
        }
    }
}
