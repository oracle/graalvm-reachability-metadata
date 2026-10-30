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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

/** Verifies restoration of method- and constructor-backed resolvable types. */
public class SerializableTypeWrapperInnerMethodParameterTypeProviderTest {
    @Test
    void restoresMethodParameterProviderDuringDeserialization() throws Exception {
        Method method = ParameterFixture.class.getDeclaredMethod("accept", CharSequence.class);
        ResolvableType type = ResolvableType.forMethodParameter(method, 0);

        ResolvableType restored = roundTrip(type);

        assertThat(restored.resolve()).isEqualTo(CharSequence.class);
        MethodParameter source = (MethodParameter) restored.getSource();
        assertThat(source.getMethod()).isEqualTo(method);
        assertThat(source.getParameterIndex()).isZero();
    }

    @Test
    void restoresConstructorParameterProviderDuringDeserialization() throws Exception {
        Constructor<ParameterFixture> constructor =
                ParameterFixture.class.getDeclaredConstructor(Number.class);
        ResolvableType type = ResolvableType.forConstructorParameter(constructor, 0);

        ResolvableType restored = roundTrip(type);

        assertThat(restored.resolve()).isEqualTo(Number.class);
        MethodParameter source = (MethodParameter) restored.getSource();
        assertThat(source.getConstructor()).isEqualTo(constructor);
        assertThat(source.getParameterIndex()).isZero();
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

    private static final class ParameterFixture {
        private ParameterFixture(Number number) {
        }

        private void accept(CharSequence value) {
        }
    }
}
