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
import org.junit.jupiter.api.Test;

public class TypeWrapperInnerMethodParameterTypeProviderTest {

    @Test
    void restoresMethodParameterTypeProviderFromSerializedMethodParameter() throws Exception {
        Method method = MethodParameterSamples.class.getDeclaredMethod("acceptName", String.class);
        ResolvableType type = ResolvableType.forMethodParameter(method, 0);

        ResolvableType restored = roundTrip(type);

        assertThat(restored.resolve()).isEqualTo(String.class);
        assertThat(restored.getSource()).isInstanceOf(MethodParameter.class);
        MethodParameter source = (MethodParameter) restored.getSource();
        assertThat(source.getMethod()).isEqualTo(method);
        assertThat(source.getParameterIndex()).isZero();
    }

    @Test
    void restoresMethodParameterTypeProviderFromSerializedConstructorParameter() throws Exception {
        Constructor<MethodParameterSamples> constructor = MethodParameterSamples.class.getDeclaredConstructor(Integer.class);
        ResolvableType type = ResolvableType.forMethodParameter(new MethodParameter(constructor, 0));

        ResolvableType restored = roundTrip(type);

        assertThat(restored.resolve()).isEqualTo(Integer.class);
        assertThat(restored.getSource()).isInstanceOf(MethodParameter.class);
        MethodParameter source = (MethodParameter) restored.getSource();
        assertThat(source.getConstructor()).isEqualTo(constructor);
        assertThat(source.getParameterIndex()).isZero();
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

    public static final class MethodParameterSamples {
        private final Integer identifier;

        public MethodParameterSamples(Integer identifier) {
            this.identifier = identifier;
        }

        public String acceptName(String name) {
            return identifier + ":" + name;
        }
    }
}
