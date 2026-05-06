/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.MethodParameter;

public class DependencyDescriptorTest {

    @Test
    public void deserializesFieldDescriptorByResolvingDeclaredField() throws Exception {
        Field field = InjectionTarget.class.getDeclaredField("fieldDependency");
        DependencyDescriptor descriptor = new DependencyDescriptor(field, true);

        DependencyDescriptor deserialized = serializeAndDeserialize(descriptor);

        assertThat(deserialized.getDependencyName()).isEqualTo("fieldDependency");
        assertThat(deserialized.getDependencyType()).isEqualTo(String.class);
        assertThat(deserialized.getField()).isEqualTo(field);
    }

    @Test
    public void deserializesMethodParameterDescriptorByResolvingDeclaredMethod() throws Exception {
        Method method = InjectionTarget.class.getDeclaredMethod("configure", Number.class);
        DependencyDescriptor descriptor = new DependencyDescriptor(new MethodParameter(method, 0), true);

        DependencyDescriptor deserialized = serializeAndDeserialize(descriptor);

        assertThat(deserialized.getDependencyType()).isEqualTo(Number.class);
        assertThat(deserialized.getMethodParameter().getMethod()).isEqualTo(method);
        assertThat(deserialized.getMethodParameter().getParameterIndex()).isZero();
    }

    @Test
    public void deserializesConstructorParameterDescriptorByResolvingDeclaredConstructor() throws Exception {
        Constructor<InjectionTarget> constructor = InjectionTarget.class.getDeclaredConstructor(Integer.class);
        DependencyDescriptor descriptor = new DependencyDescriptor(new MethodParameter(constructor, 0), true);

        DependencyDescriptor deserialized = serializeAndDeserialize(descriptor);

        assertThat(deserialized.getDependencyType()).isEqualTo(Integer.class);
        assertThat(deserialized.getMethodParameter().getConstructor()).isEqualTo(constructor);
        assertThat(deserialized.getMethodParameter().getParameterIndex()).isZero();
    }

    private static DependencyDescriptor serializeAndDeserialize(DependencyDescriptor descriptor)
            throws IOException, ClassNotFoundException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(descriptor);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DependencyDescriptor) input.readObject();
        }
    }

    public static class InjectionTarget {
        private String fieldDependency;

        private InjectionTarget(Integer constructorDependency) {
            this.fieldDependency = constructorDependency.toString();
        }

        private void configure(Number methodDependency) {
            this.fieldDependency = methodDependency.toString();
        }
    }
}
