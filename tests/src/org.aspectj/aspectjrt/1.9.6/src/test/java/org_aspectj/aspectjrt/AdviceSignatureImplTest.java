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
    void resolvesAdviceMethodDeclaredOnSignatureDeclaringType() {
        Factory factory = new Factory("AdviceSignatureImplTest.java", AdviceSignatureImplTest.class);
        AdviceSignature signature = factory.makeAdviceSig(
                Modifier.PUBLIC,
                "beforeAdvice",
                AdviceSubject.class,
                new Class[] {String.class},
                new String[] {"message"},
                new Class[0],
                Void.TYPE);

        Method method = signature.getAdvice();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(AdviceSubject.class);
        assertThat(method.getName()).isEqualTo("beforeAdvice");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(Void.TYPE);
    }

    public static class AdviceSubject {
        public void beforeAdvice(String message) {
        }
    }
}
