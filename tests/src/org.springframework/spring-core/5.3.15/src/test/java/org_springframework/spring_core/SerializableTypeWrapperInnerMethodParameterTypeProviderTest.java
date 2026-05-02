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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

public class SerializableTypeWrapperInnerMethodParameterTypeProviderTest {

    @Test
    void restoresMethodParameterProviderForMethodDuringDeserialization() throws Exception {
        Method method = SampleType.class.getDeclaredMethod("accept", CharSequence.class);
        ResolvableType resolvableType = ResolvableType.forMethodParameter(method, 0);

        ResolvableType restoredType = serializeAndDeserialize(resolvableType);

        assertThat(restoredType.resolve()).isEqualTo(CharSequence.class);
        assertThat(restoredType.getSource()).isInstanceOf(MethodParameter.class);
        MethodParameter source = (MethodParameter) restoredType.getSource();
        assertThat(source.getMethod()).isEqualTo(method);
        assertThat(source.getParameterIndex()).isEqualTo(0);
    }

    @Test
    void restoresMethodParameterProviderForConstructorDuringDeserialization() throws Exception {
        Constructor<SampleType> constructor = SampleType.class.getDeclaredConstructor(Number.class);
        ResolvableType resolvableType = ResolvableType.forConstructorParameter(constructor, 0);

        ResolvableType restoredType = serializeAndDeserialize(resolvableType);

        assertThat(restoredType.resolve()).isEqualTo(Number.class);
        assertThat(restoredType.getSource()).isInstanceOf(MethodParameter.class);
        MethodParameter source = (MethodParameter) restoredType.getSource();
        assertThat(source.getConstructor()).isEqualTo(constructor);
        assertThat(source.getParameterIndex()).isEqualTo(0);
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

    private static final class SampleType {

        private SampleType(Number number) {
        }

        private void accept(CharSequence value) {
        }
    }
}
