/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringPointcutTest {
    @Test
    void filtersSubtypeSensitiveTargetResidueAndRuntimeArguments() throws Exception {
        Method method = Operation.class.getMethod("apply", Object.class);
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("target(" + SpecializedOperation.class.getName() + ") && args(java.lang.String)");

        assertThat(pointcut.isRuntime()).isTrue();
        assertThat(pointcut.matches(method, Operation.class)).isFalse();
        assertThat(pointcut.matches(method, SpecializedOperation.class)).isTrue();
        assertThat(pointcut.matches(method, SpecializedOperation.class, "payload")).isTrue();
        assertThat(pointcut.matches(method, SpecializedOperation.class, Integer.valueOf(7))).isFalse();
    }

    public interface Operation {
        Object apply(Object value);
    }

    public static class SpecializedOperation implements Operation {
        @Override
        public Object apply(Object value) {
            return value;
        }
    }
}
