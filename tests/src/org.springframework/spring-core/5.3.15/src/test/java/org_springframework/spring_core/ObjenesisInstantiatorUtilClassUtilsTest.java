/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.objenesis.instantiator.util.ClassUtils;

public class ObjenesisInstantiatorUtilClassUtilsTest {

    @Test
    void resolvesExistingClassWithProvidedClassLoader() {
        ClassLoader classLoader = ObjenesisInstantiatorUtilClassUtilsTest.class.getClassLoader();

        Class<AnnotationAttributes> resolvedClass = ClassUtils.getExistingClass(
                classLoader,
                AnnotationAttributes.class.getName()
        );

        assertThat(resolvedClass).isSameAs(AnnotationAttributes.class);
    }

    @Test
    void returnsNullWhenClassCannotBeResolved() {
        ClassLoader classLoader = ObjenesisInstantiatorUtilClassUtilsTest.class.getClassLoader();

        Class<Object> resolvedClass = ClassUtils.getExistingClass(
                classLoader,
                "org.springframework.core.annotation.DoesNotExist"
        );

        assertThat(resolvedClass).isNull();
    }

    @Test
    void createsInstanceUsingPublicNoArgConstructor() {
        AnnotationAttributes instance = ClassUtils.newInstance(AnnotationAttributes.class);

        assertThat(instance).isEmpty();
    }
}
