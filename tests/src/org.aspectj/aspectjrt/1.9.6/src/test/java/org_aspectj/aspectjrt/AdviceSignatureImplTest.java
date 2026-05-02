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
import org.aspectj.lang.reflect.AdviceSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class AdviceSignatureImplTest {
    private static final Class<?>[] NO_PARAMETER_TYPES = new Class<?>[0];
    private static final String[] NO_PARAMETER_NAMES = new String[0];
    private static final Class<?>[] NO_EXCEPTION_TYPES = new Class<?>[0];

    @Test
    void resolvesDeclaredAdviceMethodFromSignatureType() {
        Factory factory = new Factory("AdviceSignatureImplTest.java", AdviceSignatureImplTest.class);
        AdviceSignature signature = factory.makeAdviceSig(
                Modifier.PUBLIC | Modifier.ABSTRACT,
                "getKind",
                JoinPoint.class,
                NO_PARAMETER_TYPES,
                NO_PARAMETER_NAMES,
                NO_EXCEPTION_TYPES,
                String.class);

        Method adviceMethod = signature.getAdvice();

        assertThat(adviceMethod).isNotNull();
        assertThat(adviceMethod.getDeclaringClass()).isEqualTo(JoinPoint.class);
        assertThat(adviceMethod.getName()).isEqualTo("getKind");
        assertThat(adviceMethod.getParameterTypes()).isEmpty();
        assertThat(adviceMethod.getExceptionTypes()).isEmpty();
        assertThat(adviceMethod.getReturnType()).isEqualTo(String.class);
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }
}
