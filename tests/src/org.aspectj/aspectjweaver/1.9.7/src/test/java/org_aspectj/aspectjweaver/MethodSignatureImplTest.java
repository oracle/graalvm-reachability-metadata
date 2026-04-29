/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class MethodSignatureImplTest {
    @Test
    void resolvesDeclaredMethodFromSignature() {
        MethodSignature signature = methodSignature(DeclaredMethodTarget.class, "declaredMethod",
                String.class.getName(), "value", IllegalArgumentException.class.getName(), int.class.getName());

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(DeclaredMethodTarget.class);
        assertThat(method.getName()).isEqualTo("declaredMethod");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }

    @Test
    void searchesHierarchyWhenSignatureDeclaringTypeInheritsMethod() {
        MethodSignature signature = methodSignature(InheritedMethodTarget.class, "inheritedMethod",
                CharSequence.class.getName(), "text", IllegalStateException.class.getName(), String.class.getName());

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(MethodSource.class);
        assertThat(method.getName()).isEqualTo("inheritedMethod");
        assertThat(method.getParameterTypes()).containsExactly(CharSequence.class);
        assertThat(method.getExceptionTypes()).containsExactly(IllegalStateException.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    private static MethodSignature methodSignature(Class<?> declaringType, String name, String parameterTypes,
            String parameterNames, String exceptionTypes, String returnType) {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        return factory.makeMethodSig("1", name, declaringType.getName(), parameterTypes, parameterNames, exceptionTypes,
                returnType);
    }

    public static class DeclaredMethodTarget {
        public int declaredMethod(String value) throws IllegalArgumentException {
            return value.length();
        }
    }

    public static class MethodSource {
        public String inheritedMethod(CharSequence text) throws IllegalStateException {
            return text.toString();
        }
    }

    public static class InheritedMethodTarget extends MethodSource {
    }
}
