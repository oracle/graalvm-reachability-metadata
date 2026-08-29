/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.SerializationUtils;

/** Verifies restoration of generic method and constructor parameter types. */
public class SerializableTypeWrapperInnerMethodParameterTypeProviderTest {
    @Test
    void restoresGenericMethodParameter() throws Exception {
        Method method = GenericParameters.class.getDeclaredMethod("accept", List.class);
        ResolvableType original =
                ResolvableType.forMethodParameter(new MethodParameter(method, 0));

        ResolvableType restored = roundTrip(original);

        assertThat(restored.resolve()).isEqualTo(List.class);
        assertThat(restored.resolveGeneric()).isEqualTo(String.class);
    }

    @Test
    void restoresGenericConstructorParameter() throws Exception {
        Constructor<GenericParameters> constructor =
                GenericParameters.class.getDeclaredConstructor(List.class);
        ResolvableType original =
                ResolvableType.forMethodParameter(new MethodParameter(constructor, 0));

        ResolvableType restored = roundTrip(original);

        assertThat(restored.resolve()).isEqualTo(List.class);
        assertThat(restored.resolveGeneric()).isEqualTo(Integer.class);
    }

    private static ResolvableType roundTrip(ResolvableType type) {
        return (ResolvableType) SerializationUtils.deserialize(SerializationUtils.serialize(type));
    }

    public static final class GenericParameters {
        public GenericParameters(List<Integer> values) {}

        public void accept(List<String> values) {}
    }
}
