/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class MethodSignatureImplTest {
    private static final Class<?>[] NO_PARAMETER_TYPES = new Class<?>[0];
    private static final String[] NO_PARAMETER_NAMES = new String[0];
    private static final Class<?>[] NO_EXCEPTION_TYPES = new Class<?>[0];

    @Test
    void resolvesMethodDeclaredOnSignatureType() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC | Modifier.ABSTRACT,
                "getKind",
                JoinPoint.class,
                NO_PARAMETER_TYPES,
                NO_PARAMETER_NAMES,
                NO_EXCEPTION_TYPES,
                String.class);

        Method method = signature.getMethod();

        assertThat(method.getDeclaringClass()).isEqualTo(JoinPoint.class);
        assertThat(method.getName()).isEqualTo("getKind");
        assertThat(method.getParameterTypes()).isEmpty();
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void searchesInheritedInterfacesWhenMethodIsNotDeclaredOnSignatureType() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC | Modifier.ABSTRACT,
                "getThis",
                ProceedingJoinPoint.class,
                NO_PARAMETER_TYPES,
                NO_PARAMETER_NAMES,
                NO_EXCEPTION_TYPES,
                Object.class);

        Method method = signature.getMethod();

        assertThat(method.getDeclaringClass()).isEqualTo(JoinPoint.class);
        assertThat(method.getName()).isEqualTo("getThis");
        assertThat(method.getParameterTypes()).isEmpty();
        assertThat(method.getReturnType()).isEqualTo(Object.class);
    }
}
