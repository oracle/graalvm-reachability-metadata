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
import java.lang.reflect.Proxy;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

/** Verifies Spring's public class loading and introspection utilities. */
public class ClassUtilsTest {
    @Test
    void resolvesRegularNestedAndArrayClassNames() throws ClassNotFoundException {
        ClassLoader classLoader = ByteArrayResource.class.getClassLoader();
        String resourceName = ByteArrayResource.class.getName();
        String descriptorName = "[L" + resourceName + ";";

        assertThat(ClassUtils.forName(resourceName, classLoader)).isEqualTo(ByteArrayResource.class);
        assertThat(ClassUtils.forName(Map.Entry.class.getCanonicalName(), classLoader)).isEqualTo(Map.Entry.class);
        assertThat(ClassUtils.forName(resourceName + "[]", classLoader)).isEqualTo(ByteArrayResource[].class);
        assertThat(ClassUtils.forName(descriptorName, classLoader)).isEqualTo(ByteArrayResource[].class);
        assertThat(ClassUtils.forName("[" + descriptorName, classLoader)).isEqualTo(ByteArrayResource[][].class);
    }

    @Test
    void checksClassLoaderVisibilityAndCreatesCompositeInterface() {
        assertThat(ClassUtils.isVisible(String.class, ClassLoader.getSystemClassLoader())).isTrue();

        Class<?> compositeInterface = ClassUtils.createCompositeInterface(
                new Class<?>[] {Resource.class, Ordered.class}, ClassUtils.class.getClassLoader());

        assertThat(Proxy.isProxyClass(compositeInterface)).isTrue();
        assertThat(Resource.class.isAssignableFrom(compositeInterface)).isTrue();
        assertThat(Ordered.class.isAssignableFrom(compositeInterface)).isTrue();
    }

    @Test
    void findsConstructorsAndMethods() {
        Constructor<ByteArrayResource> constructor = ClassUtils.getConstructorIfAvailable(
                ByteArrayResource.class, byte[].class, String.class);
        Method requiredMethod = ClassUtils.getMethod(ByteArrayResource.class, "getDescription");
        Method availableMethod = ClassUtils.getMethodIfAvailable(ByteArrayResource.class, "getByteArray");
        Method signatureIndependentMethod = ClassUtils.getMethod(
                InputStreamSource.class, "getInputStream", (Class<?>[]) null);
        Method staticMethod = ClassUtils.getStaticMethod(
                ClassUtils.class, "isPresent", String.class, ClassLoader.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(byte[].class, String.class);
        assertThat(requiredMethod.getDeclaringClass()).isEqualTo(ByteArrayResource.class);
        assertThat(availableMethod).isNotNull();
        assertThat(availableMethod.getReturnType()).isEqualTo(byte[].class);
        assertThat(signatureIndependentMethod.getDeclaringClass()).isEqualTo(InputStreamSource.class);
        assertThat(staticMethod).isNotNull();
        assertThat(staticMethod.getDeclaringClass()).isEqualTo(ClassUtils.class);
        assertThat(ClassUtils.getMethodCountForName(InputStreamSource.class, "getInputStream")).isEqualTo(1);
        assertThat(ClassUtils.hasAtLeastOneMethodWithName(Resource.class, "getDescription")).isTrue();
    }

    @Test
    void mapsMethodsBetweenResourceInterfaceAndImplementation() {
        Method interfaceMethod = ClassUtils.getMethod(Resource.class, "getDescription");
        Method implementationMethod = ClassUtils.getMostSpecificMethod(interfaceMethod, ByteArrayResource.class);
        Method publicImplementationMethod = ClassUtils.getMethod(ByteArrayResource.class, "getDescription");
        Method mappedInterfaceMethod = ClassUtils.getInterfaceMethodIfPossible(publicImplementationMethod);

        assertThat(implementationMethod.getDeclaringClass()).isEqualTo(ByteArrayResource.class);
        assertThat(mappedInterfaceMethod.getDeclaringClass()).isEqualTo(Resource.class);
        assertThat(mappedInterfaceMethod.getName()).isEqualTo(publicImplementationMethod.getName());
    }
}
