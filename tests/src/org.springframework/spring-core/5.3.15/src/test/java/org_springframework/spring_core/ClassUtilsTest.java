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
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.OrderComparator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class ClassUtilsTest {

    @Test
    void resolvesConcreteNestedAndArrayClassNames() throws Exception {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        assertThat(ClassUtils.forName(NamedThreadLocal.class.getName(), classLoader))
                .isSameAs(NamedThreadLocal.class);
        assertThat(ClassUtils.forName(
                "org.springframework.core.OrderComparator.OrderSourceProvider",
                classLoader
        )).isSameAs(OrderComparator.OrderSourceProvider.class);
        assertThat(ClassUtils.forName(NamedThreadLocal.class.getName() + "[]", classLoader))
                .isSameAs(NamedThreadLocal[].class);
        assertThat(ClassUtils.forName("[L" + NamedThreadLocal.class.getName() + ";", classLoader))
                .isSameAs(NamedThreadLocal[].class);
        assertThat(ClassUtils.forName("[[L" + NamedThreadLocal.class.getName() + ";", classLoader))
                .isSameAs(NamedThreadLocal[][].class);
    }

    @Test
    void checksLoadabilityThroughProvidedClassLoader() {
        ClassLoader childClassLoader = new ClassLoader(ClassUtilsTest.class.getClassLoader()) {
        };

        assertThat(ClassUtils.isVisible(NamedThreadLocal.class, childClassLoader)).isTrue();
    }

    @Test
    void findsConstructorsAndStaticMethods() {
        Constructor<?> constructor = ClassUtils.getConstructorIfAvailable(
                NamedThreadLocal.class,
                String.class
        );
        Method staticMethod = ClassUtils.getStaticMethod(StringUtils.class, "hasText", CharSequence.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(NamedThreadLocal.class);
        assertThat(staticMethod).isNotNull();
        assertThat(staticMethod.getDeclaringClass()).isEqualTo(StringUtils.class);
        assertThat(Modifier.isStatic(staticMethod.getModifiers())).isTrue();
    }

    @Test
    void inspectsPublicAndDeclaredMethodsByName() {
        Method method = ClassUtils.getMethod(OrderComparator.class, "compare", Object.class, Object.class);
        Method availableMethod = ClassUtils.getMethodIfAvailable(
                OrderComparator.class,
                "compare",
                Object.class,
                Object.class
        );
        Method uniqueMethodByName = ClassUtils.getMethodIfAvailable(
                OrderComparator.class,
                "getPriority",
                (Class<?>[]) null
        );

        assertThat(method.getDeclaringClass()).isEqualTo(OrderComparator.class);
        assertThat(availableMethod).isEqualTo(method);
        assertThat(uniqueMethodByName).isNotNull();
        assertThat(uniqueMethodByName.getName()).isEqualTo("getPriority");
        assertThat(ClassUtils.getMethodCountForName(OrderComparator.class, "compare"))
                .isGreaterThanOrEqualTo(1);
        assertThat(ClassUtils.hasAtLeastOneMethodWithName(OrderComparator.class, "compare"))
                .isTrue();
    }

    @Test
    void createsCompositeInterfaceUsingJdkProxyClass() {
        Class<?> compositeInterface = ClassUtils.createCompositeInterface(
                new Class<?>[] {TaskExecutor.class, OrderComparator.OrderSourceProvider.class},
                ClassUtilsTest.class.getClassLoader()
        );

        assertThat(TaskExecutor.class.isAssignableFrom(compositeInterface)).isTrue();
        assertThat(OrderComparator.OrderSourceProvider.class.isAssignableFrom(compositeInterface))
                .isTrue();
    }
}
