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

import org.aspectj.lang.reflect.AdviceSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class AdviceSignatureImplTest {
    @Test
    void resolvesDeclaredAdviceMethodOnDeclaringType() {
        Factory factory = new Factory("AdviceSignatureImplTest.java", AdviceSignatureImplTest.class);
        AdviceSignature signature = factory.makeAdviceSig(
                Modifier.PUBLIC | Modifier.STATIC,
                "beforeAdvice",
                AdviceHost.class,
                new Class[] {String.class},
                new String[] {"value"},
                new Class[0],
                void.class);

        Method method = signature.getAdvice();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(AdviceHost.class);
        assertThat(method.getName()).isEqualTo("beforeAdvice");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    public static class AdviceHost {
        public static void beforeAdvice(String value) {
        }
    }
}
