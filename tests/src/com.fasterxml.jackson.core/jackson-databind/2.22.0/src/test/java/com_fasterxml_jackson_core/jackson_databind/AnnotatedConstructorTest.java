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
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedConstructorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void callsSingleArgumentConstructorDiscoveredByJacksonIntrospection() throws Exception {
        AnnotatedConstructor constructor = stringConstructorFor(SingleArgumentCreator.class);

        SingleArgumentCreator created = (SingleArgumentCreator) constructor.call1("espresso");

        assertThat(created.value()).isEqualTo("espresso");
    }

    @Test
    void jdkSerializationRestoresConstructorDiscoveredByJacksonIntrospection() throws Exception {
        AnnotatedConstructor original = stringConstructorFor(SerializableCreator.class);

        AnnotatedConstructor restored = roundTrip(original);
        SerializableCreator created = (SerializableCreator) restored.call1("latte");

        assertThat(restored).isEqualTo(original);
        assertThat(created.value()).isEqualTo("latte");
    }

    private static AnnotatedConstructor stringConstructorFor(Class<?> type) {
        BeanDescription beanDescription = MAPPER.getDeserializationConfig()
                .introspect(MAPPER.constructType(type));
        return beanDescription.getConstructors().stream()
                .filter(constructor -> constructor.getParameterCount() == 1)
                .filter(constructor -> constructor.getRawParameterType(0) == String.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No single String constructor found for " + type.getName()));
    }

    private static AnnotatedConstructor roundTrip(AnnotatedConstructor constructor)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(constructor);
        }
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (AnnotatedConstructor) objectInput.readObject();
        }
    }

    public static final class SingleArgumentCreator {
        private final String value;

        public SingleArgumentCreator(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static final class SerializableCreator {
        private final String value;

        public SerializableCreator(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
