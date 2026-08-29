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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** Verifies CGLIB reflection utilities against Spring Core types. */
public class ReflectUtilsTest {
    @Test
    void createsInstanceThroughDeclaredConstructor() {
        byte[] content = new byte[] {4, 5, 6};

        ByteArrayResource resource = (ByteArrayResource) ReflectUtils.newInstance(
                ByteArrayResource.class,
                new Class<?>[] {byte[].class, String.class},
                new Object[] {content, "reflective resource"});

        assertThat(resource.getByteArray()).isSameAs(content);
        assertThat(resource.getDescription()).contains("reflective resource");
    }

    @Test
    void findsConstructorWithPrimitiveArrayDescriptor() {
        byte[] content = new byte[] {7, 8};

        Constructor<?> constructor = ReflectUtils.findConstructor(
                "org.springframework.core.io.ByteArrayResource(byte[], String)");
        ByteArrayResource resource = (ByteArrayResource) ReflectUtils.newInstance(
                constructor, new Object[] {content, "descriptor resource"});

        assertThat(resource.getByteArray()).isSameAs(content);
        assertThat(resource.getDescription()).contains("descriptor resource");
    }

    @Test
    void findsMethodsFromDescriptorAndClassHierarchy() throws NoSuchMethodException {
        Method equalsMethod = ReflectUtils.findMethod(
                "org.springframework.core.io.ByteArrayResource.equals(Object)");
        Method inheritedMethod = ReflectUtils.findDeclaredMethod(
                ClassPathResource.class, "isOpen", new Class<?>[0]);

        assertThat(equalsMethod.getDeclaringClass()).isEqualTo(ByteArrayResource.class);
        assertThat(equalsMethod.getParameterTypes()).containsExactly(Object.class);
        assertThat(inheritedMethod.getDeclaringClass()).isEqualTo(AbstractResource.class);
    }

    @Test
    void collectsClassHierarchyMethodsAndFindsSingleInterfaceMethod() {
        List<Method> methods = new ArrayList<>();

        ReflectUtils.addAllMethods(ByteArrayResource.class, methods);
        Method interfaceMethod = ReflectUtils.findInterfaceMethod(DecoratingProxy.class);

        assertThat(methodNames(methods)).contains("getByteArray", "getDescription", "isReadable", "toString");
        assertThat(interfaceMethod.getName()).isEqualTo("getDecoratedClass");
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(DecoratingProxy.class);
    }

    private static List<String> methodNames(List<Method> methods) {
        List<String> names = new ArrayList<>();
        for (Method method : methods) {
            names.add(method.getName());
        }
        return names;
    }
}
