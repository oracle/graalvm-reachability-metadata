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

public class RuntimeTestWalkerInnerInstanceOfResidueTestVisitorTest {

    @Test
    void targetResidueComparesAspectJReferenceTypeWithConcreteTargetClass() throws Exception {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* *(..)) && target(" + RuntimeWalkerMarker.class.getName() + ")");
        Method method = RuntimeWalkerService.class.getMethod("process");

        boolean matches = pointcut.matches(method, RuntimeWalkerService.class, false);

        assertThat(matches).isFalse();
    }
}

interface RuntimeWalkerMarker {
}

class RuntimeWalkerService {

    public void process() {
    }
}
