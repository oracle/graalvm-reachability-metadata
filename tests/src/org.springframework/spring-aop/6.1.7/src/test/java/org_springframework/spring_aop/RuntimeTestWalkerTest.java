/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;

public class RuntimeTestWalkerTest {

    @Test
    void staticPointcutMatchInspectsRuntimeResidueForArgumentTypeTest() throws Exception {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* *(..)) && args(java.lang.String)");
        Method method = RuntimeArgumentService.class.getMethod("process", Object.class);

        boolean matches = pointcut.matches(method, RuntimeArgumentService.class, false);

        assertThat(matches).isTrue();
        assertThat(pointcut.isRuntime()).isTrue();
    }

    public static class RuntimeArgumentService {

        public void process(Object value) {
        }
    }
}
