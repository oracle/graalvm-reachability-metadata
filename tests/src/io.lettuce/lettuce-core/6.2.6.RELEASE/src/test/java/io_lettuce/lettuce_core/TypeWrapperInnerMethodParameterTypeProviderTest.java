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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TypeWrapperInnerMethodParameterTypeProviderTest {

    @Test
    void deserializesMethodParameterResolvableTypeFromMethod() throws Exception {
        Method method = GenericParameterFixture.class.getMethod("storeNames", List.class);
        ResolvableType parameterType = ResolvableType.forMethodParameter(method, 0);

        ResolvableType restored = serializeAndDeserialize(parameterType);

        assertThat(restored.resolve()).isEqualTo(List.class);
        assertThat(restored.resolveGeneric()).isEqualTo(String.class);
        assertThat(restored.getSource()).isInstanceOfSatisfying(MethodParameter.class, source -> {
            assertThat(source.getMethod()).isEqualTo(method);
            assertThat(source.getParameterIndex()).isZero();
        });
    }

    @Test
    void deserializesMethodParameterResolvableTypeFromConstructor() throws Exception {
        Constructor<GenericParameterFixture> constructor = GenericParameterFixture.class.getConstructor(Map.class);
        ResolvableType parameterType = ResolvableType.forMethodParameter(new MethodParameter(constructor, 0));

        ResolvableType restored = serializeAndDeserialize(parameterType);

        assertThat(restored.resolve()).isEqualTo(Map.class);
        assertThat(restored.resolveGenerics()).containsExactly(String.class, Integer.class);
        assertThat(restored.getSource()).isInstanceOfSatisfying(MethodParameter.class, source -> {
            assertThat(source.getConstructor()).isEqualTo(constructor);
            assertThat(source.getParameterIndex()).isZero();
        });
    }

    private static ResolvableType serializeAndDeserialize(ResolvableType resolvableType)
            throws IOException, ClassNotFoundException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(resolvableType);
        }

        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ResolvableType) inputStream.readObject();
        }
    }

    public static final class GenericParameterFixture {

        public GenericParameterFixture(Map<String, Integer> values) {
        }

        public void storeNames(List<String> names) {
        }
    }
}
