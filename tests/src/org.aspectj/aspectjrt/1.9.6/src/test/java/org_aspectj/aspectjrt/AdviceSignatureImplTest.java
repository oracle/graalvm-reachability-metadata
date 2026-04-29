/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.AdviceSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AdviceSignatureImplTest {

    @Test
    void resolvesDeclaredAdviceMethodFromSignature() {
        AdviceSignature signature = createSignature(
                "beforeAdvice",
                AdviceHost.class,
                new Class<?>[] {String.class, int.class},
                void.class
        );

        Method advice = signature.getAdvice();

        assertThat(advice).isNotNull();
        assertThat(advice.getDeclaringClass()).isEqualTo(AdviceHost.class);
        assertThat(advice.getName()).isEqualTo("beforeAdvice");
        assertThat(advice.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(advice.getReturnType()).isEqualTo(void.class);
    }

    private static AdviceSignature createSignature(
            String name,
            Class<?> declaringType,
            Class<?>[] parameterTypes,
            Class<?> returnType) {
        Factory factory = new Factory("AdviceSignatureImplTest.java", AdviceSignatureImplTest.class);
        return factory.makeAdviceSig(
                Modifier.PUBLIC,
                name,
                declaringType,
                parameterTypes,
                new String[] {"message", "count"},
                new Class<?>[0],
                returnType
        );
    }

    public static class AdviceHost {
        public void beforeAdvice(String message, int count) {
            String normalizedMessage = message.trim();
            if (normalizedMessage.length() < count) {
                throw new IllegalArgumentException(normalizedMessage);
            }
        }
    }
}
