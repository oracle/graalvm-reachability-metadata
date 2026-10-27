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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Verifies restoration of a field-backed resolvable type after serialization. */
public class SerializableTypeWrapperInnerFieldTypeProviderTest {
    @Test
    void restoresFieldProviderDuringDeserialization() throws Exception {
        Field field = FieldFixture.class.getField("value");
        ResolvableType type = ResolvableType.forField(field);

        ResolvableType restored = roundTrip(type);

        assertThat(restored.resolve()).isEqualTo(String.class);
        assertThat(restored.getSource()).isEqualTo(field);
    }

    private static ResolvableType roundTrip(ResolvableType type) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(type);
        }
        try (ObjectInputStream objectInput =
                new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (ResolvableType) objectInput.readObject();
        }
    }

    public static final class FieldFixture {
        public String value;
    }
}
