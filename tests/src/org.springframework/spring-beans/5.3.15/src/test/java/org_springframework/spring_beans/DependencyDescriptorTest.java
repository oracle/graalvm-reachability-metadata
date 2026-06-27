/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.MethodParameter;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyDescriptorTest {

    @Test
    void deserializesFieldDescriptor() throws Exception {
        Field field = DependencyTarget.class.getDeclaredField("fieldDependency");
        DependencyDescriptor descriptor = new DependencyDescriptor(field, true);

        DependencyDescriptor deserialized = serializeAndDeserialize(descriptor);

        assertThat(deserialized.getField()).isEqualTo(field);
        assertThat(deserialized.getDependencyName()).isEqualTo("fieldDependency");
        assertThat(deserialized.getDependencyType()).isEqualTo(String.class);
        assertThat(deserialized.isRequired()).isTrue();
    }

    @Test
    void deserializesMethodParameterDescriptor() throws Exception {
        Method method = DependencyTarget.class.getDeclaredMethod("configure", String.class, Integer.class);
        DependencyDescriptor descriptor = new DependencyDescriptor(new MethodParameter(method, 1), true);

        DependencyDescriptor deserialized = serializeAndDeserialize(descriptor);

        assertThat(deserialized.getMethodParameter()).isNotNull();
        assertThat(deserialized.getMethodParameter().getMethod()).isEqualTo(method);
        assertThat(deserialized.getMethodParameter().getParameterIndex()).isEqualTo(1);
        assertThat(deserialized.getDependencyType()).isEqualTo(Integer.class);
        assertThat(deserialized.isRequired()).isTrue();
    }

    @Test
    void deserializesConstructorParameterDescriptor() throws Exception {
        Constructor<DependencyTarget> constructor = DependencyTarget.class.getDeclaredConstructor(String.class);
        DependencyDescriptor descriptor = new DependencyDescriptor(new MethodParameter(constructor, 0), true);

        DependencyDescriptor deserialized = serializeAndDeserialize(descriptor);

        assertThat(deserialized.getMethodParameter()).isNotNull();
        assertThat(deserialized.getMethodParameter().getConstructor()).isEqualTo(constructor);
        assertThat(deserialized.getMethodParameter().getParameterIndex()).isZero();
        assertThat(deserialized.getDependencyType()).isEqualTo(String.class);
        assertThat(deserialized.isRequired()).isTrue();
    }

    private static DependencyDescriptor serializeAndDeserialize(DependencyDescriptor descriptor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(descriptor);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DependencyDescriptor) input.readObject();
        }
    }

    public static class DependencyTarget {
        private String fieldDependency;

        public DependencyTarget(String constructorDependency) {
        }

        public void configure(String name, Integer count) {
        }
    }
}
