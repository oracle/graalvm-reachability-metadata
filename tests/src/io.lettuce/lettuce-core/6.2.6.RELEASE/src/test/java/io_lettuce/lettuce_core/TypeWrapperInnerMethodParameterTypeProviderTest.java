/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.MethodParameter;
import io.lettuce.core.dynamic.support.ResolvableType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TypeWrapperInnerMethodParameterTypeProviderTest {
    public static final class GenericInput {
        public GenericInput(List<String> values) {
        }

        public void accept(List<String> values) {
        }
    }

    @Test
    void restoresMethodParameterTypeAfterSerialization() throws Exception {
        Method method = GenericInput.class.getDeclaredMethod("accept", List.class);
        ResolvableType original = ResolvableType.forMethodParameter(method, 0);

        ResolvableType restored = roundTrip(original);

        assertThat(restored.resolve()).isEqualTo(List.class);
        assertThat(restored.getGeneric(0).resolve()).isEqualTo(String.class);
        assertThat(((MethodParameter) restored.getSource()).getMethod()).isEqualTo(method);
    }

    @Test
    void restoresConstructorParameterTypeAfterSerialization() throws Exception {
        Constructor<GenericInput> constructor = GenericInput.class.getDeclaredConstructor(List.class);
        ResolvableType original = ResolvableType.forMethodParameter(new MethodParameter(constructor, 0));

        ResolvableType restored = roundTrip(original);

        assertThat(restored.resolve()).isEqualTo(List.class);
        assertThat(restored.getGeneric(0).resolve()).isEqualTo(String.class);
        assertThat(((MethodParameter) restored.getSource()).getConstructor()).isEqualTo(constructor);
    }

    private static ResolvableType roundTrip(ResolvableType type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(type);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ResolvableType) input.readObject();
        }
    }
}
