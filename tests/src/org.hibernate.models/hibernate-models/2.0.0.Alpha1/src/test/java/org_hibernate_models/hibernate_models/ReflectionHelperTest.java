/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.reflect.Method;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.internal.jdk.JdkClassDetails;
import org.hibernate.models.spi.MethodDetails;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {
    @Test
    public void resolvesGetterOnConcreteDeclaringClass() {
        final MethodDetails getter = methodDetails(AttributeContract.class, "getName");

        final Method resolvedMethod = getter.toJavaMember(
                GetterBackedEntity.class,
                SimpleClassLoading.SIMPLE_CLASS_LOADING,
                newModelsContext()
        );

        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(GetterBackedEntity.class);
        assertThat(resolvedMethod.getName()).isEqualTo("getName");
        assertThat(resolvedMethod.getParameterCount()).isZero();
    }

    @Test
    public void resolvesSetterOnConcreteDeclaringClass() {
        final MethodDetails setter = methodDetails(MutationContract.class, "setName");

        final Method resolvedMethod = setter.toJavaMember(
                SetterBackedEntity.class,
                SimpleClassLoading.SIMPLE_CLASS_LOADING,
                newModelsContext()
        );

        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(SetterBackedEntity.class);
        assertThat(resolvedMethod.getName()).isEqualTo("setName");
        assertThat(resolvedMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    public void resolvesOtherMethodOnConcreteDeclaringClass() {
        final MethodDetails operation = methodDetails(OperationContract.class, "describe");

        final Method resolvedMethod = operation.toJavaMember(
                OperationBackedEntity.class,
                SimpleClassLoading.SIMPLE_CLASS_LOADING,
                newModelsContext()
        );

        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(OperationBackedEntity.class);
        assertThat(resolvedMethod.getName()).isEqualTo("describe");
        assertThat(resolvedMethod.getParameterTypes()).containsExactly(String.class, Integer.class);
    }

    private static MethodDetails methodDetails(Class<?> javaClass, String methodName) {
        return classDetails(javaClass).getMethods()
                .stream()
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private static JdkClassDetails classDetails(Class<?> javaClass) {
        return new JdkClassDetails(javaClass, newModelsContext());
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private interface AttributeContract {
        String getName();
    }

    private static final class GetterBackedEntity implements AttributeContract {
        private final String name = "hibernate";

        @Override
        public String getName() {
            return name;
        }
    }

    private interface MutationContract {
        void setName(String name);
    }

    private static final class SetterBackedEntity implements MutationContract {
        private String name;

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }

    private interface OperationContract {
        String describe(String prefix, Integer count);
    }

    private static final class OperationBackedEntity implements OperationContract {
        @Override
        public String describe(String prefix, Integer count) {
            return prefix + count;
        }
    }
}
