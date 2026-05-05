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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class TypeWrapperInnerMethodParameterTypeProviderTest {

    @Test
    void deserializesMethodParameterTypeProviderBackedByMethod() throws Exception {
        Method method = ParameterFixtures.class.getMethod("accept", String.class);
        ResolvableType parameterType = ResolvableType.forMethodParameter(method, 0);

        ResolvableType restored = serializeAndDeserialize(parameterType);

        assertThat(restored.resolve()).isEqualTo(String.class);
        assertThat(restored.getSource()).isInstanceOfSatisfying(MethodParameter.class, source -> {
            assertThat(source.getMethod()).isEqualTo(method);
            assertThat(source.getParameterIndex()).isZero();
            assertThat(source.getGenericParameterType()).isEqualTo(String.class);
        });
    }

    @Test
    void deserializesMethodParameterTypeProviderBackedByConstructor() throws Exception {
        Constructor<ParameterFixtures> constructor = ParameterFixtures.class.getConstructor(Integer.class);
        MethodParameter constructorParameter = new MethodParameter(constructor, 0);
        ResolvableType parameterType = ResolvableType.forMethodParameter(constructorParameter);

        ResolvableType restored = serializeAndDeserialize(parameterType);

        assertThat(restored.resolve()).isEqualTo(Integer.class);
        assertThat(restored.getSource()).isInstanceOfSatisfying(MethodParameter.class, source -> {
            assertThat(source.getConstructor()).isEqualTo(constructor);
            assertThat(source.getParameterIndex()).isZero();
            assertThat(source.getGenericParameterType()).isEqualTo(Integer.class);
        });
    }

    private static ResolvableType serializeAndDeserialize(ResolvableType type)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(type);
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ResolvableType) input.readObject();
        }
    }

    public static final class ParameterFixtures {

        public ParameterFixtures(Integer value) {
        }

        public void accept(String value) {
        }
    }
}
