/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.AdviceSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class AdviceSignatureImplTest {
    @Test
    void resolvesDeclaredAdviceMethodFromSignature() {
        AdviceSignature signature = adviceSignature(AdviceFixture.class, "beforeGreeting",
                String.class.getName() + ":int", "message:count", IllegalStateException.class.getName(),
                void.class.getName());

        Method advice = signature.getAdvice();

        assertThat(advice).isNotNull();
        assertThat(advice.getDeclaringClass()).isEqualTo(AdviceFixture.class);
        assertThat(advice.getName()).isEqualTo("beforeGreeting");
        assertThat(advice.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(advice.getExceptionTypes()).containsExactly(IllegalStateException.class);
        assertThat(signature.getReturnType()).isEqualTo(void.class);
    }

    private static AdviceSignature adviceSignature(Class<?> declaringType, String name, String parameterTypes,
            String parameterNames, String exceptionTypes, String returnType) {
        Factory factory = new Factory("AdviceSignatureImplTest.java", AdviceSignatureImplTest.class);
        return factory.makeAdviceSig(Integer.toHexString(Modifier.PRIVATE), name, declaringType.getName(),
                parameterTypes, parameterNames, exceptionTypes, returnType);
    }

    public static class AdviceFixture {
        private void beforeGreeting(String message, int count) throws IllegalStateException {
            if (message.length() < count) {
                throw new IllegalStateException(message);
            }
        }
    }
}
