/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.AdviceSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AdviceSignatureImplTest {
    @Test
    void resolvesDeclaredAdviceMethodFromStaticPartSignature() {
        Factory factory = new Factory("AdviceSignatureImplTest.java", AdviceSignatureImplTest.class);
        JoinPoint.StaticPart staticPart = factory.makeAdviceSJP(
                JoinPoint.ADVICE_EXECUTION,
                Modifier.PUBLIC,
                "beforeAdvice",
                AdviceFixture.class,
                new Class[] {String.class, int.class},
                new String[] {"message", "count"},
                new Class[0],
                Void.TYPE,
                23
        );

        AdviceSignature signature = (AdviceSignature) staticPart.getSignature();
        Method advice = signature.getAdvice();

        assertThat(advice).isNotNull();
        assertThat(advice.getDeclaringClass()).isEqualTo(AdviceFixture.class);
        assertThat(advice.getName()).isEqualTo("beforeAdvice");
        assertThat(advice.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(advice.getReturnType()).isEqualTo(Void.TYPE);
    }

    public static final class AdviceFixture {
        public void beforeAdvice(String message, int count) {
            assertThat(message).hasSize(count);
        }
    }
}
