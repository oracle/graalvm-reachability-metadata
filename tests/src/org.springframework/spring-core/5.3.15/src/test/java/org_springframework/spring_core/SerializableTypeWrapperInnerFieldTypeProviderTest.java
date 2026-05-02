/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

public class SerializableTypeWrapperInnerFieldTypeProviderTest {

    @Test
    void restoresFieldProviderDuringDeserialization() throws Exception {
        Field field = SampleType.class.getField("value");
        ResolvableType resolvableType = ResolvableType.forField(field);

        ResolvableType restoredType = serializeAndDeserialize(resolvableType);

        assertThat(restoredType.resolve()).isEqualTo(String.class);
        assertThat(restoredType.getSource()).isInstanceOf(Field.class);
        Field restoredField = (Field) restoredType.getSource();
        assertThat(restoredField).isEqualTo(field);
        assertThat(restoredField.getDeclaringClass()).isEqualTo(SampleType.class);
        assertThat(restoredField.getName()).isEqualTo("value");
    }

    private static ResolvableType serializeAndDeserialize(ResolvableType resolvableType)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(resolvableType);
        }
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (ResolvableType) objectInput.readObject();
        }
    }

    public static final class SampleType {

        public String value;
    }
}
