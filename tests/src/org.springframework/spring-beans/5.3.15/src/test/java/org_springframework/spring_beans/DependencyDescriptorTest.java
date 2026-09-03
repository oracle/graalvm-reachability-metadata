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
    void restoresFieldMethodAndConstructorInjectionPointsAfterSerialization() throws Exception {
        Field field = InjectionPoints.class.getDeclaredField("fieldValue");
        Method method = InjectionPoints.class.getDeclaredMethod("setMethodValue", Integer.class);
        Constructor<InjectionPoints> constructor = InjectionPoints.class.getDeclaredConstructor(Long.class);

        DependencyDescriptor fieldDescriptor = copy(new DependencyDescriptor(field, true));
        DependencyDescriptor methodDescriptor =
                copy(new DependencyDescriptor(new MethodParameter(method, 0), true));
        DependencyDescriptor constructorDescriptor =
                copy(new DependencyDescriptor(new MethodParameter(constructor, 0), true));

        assertThat(fieldDescriptor.getDependencyType()).isEqualTo(String.class);
        assertThat(methodDescriptor.getDependencyType()).isEqualTo(Integer.class);
        assertThat(constructorDescriptor.getDependencyType()).isEqualTo(Long.class);
    }

    private static DependencyDescriptor copy(DependencyDescriptor descriptor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(descriptor);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DependencyDescriptor) input.readObject();
        }
    }

    public static class InjectionPoints {
        private String fieldValue;

        public InjectionPoints(Long constructorValue) {
        }

        public void setMethodValue(Integer methodValue) {
        }
    }
}
