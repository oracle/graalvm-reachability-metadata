/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AspectjweaverTest {
    @Test
    void appliesAroundAndAfterThrowingAdviceUsingNamedPointcut() {
        GreetingAspect aspect = new GreetingAspect();
        AspectJProxyFactory factory = new AspectJProxyFactory(new GreetingService());
        factory.setInterfaces(Greeting.class);
        factory.addAspect(aspect);
        Greeting greeting = factory.getProxy();

        assertThat(greeting.greet("Ada")).isEqualTo("advised:hello Ada");
        assertThat(aspect.failures).isZero();
        assertThatThrownBy(() -> greeting.greet(""))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("name required");
        assertThat(aspect.failures).isOne();
        assertThat(aspect.lastFailure).hasMessage("name required");
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name required");
            }
            return "hello " + name;
        }
    }

    @Aspect
    public static class GreetingAspect {
        private int failures;
        private IllegalArgumentException lastFailure;

        @Pointcut("execution(* org_aspectj.aspectjweaver.AspectjweaverTest.Greeting.greet(..))")
        public void greetings() {
        }

        @Around("greetings()")
        public Object decorate(ProceedingJoinPoint joinPoint) throws Throwable {
            return "advised:" + joinPoint.proceed();
        }

        @AfterThrowing(pointcut = "greetings()", throwing = "failure", argNames = "failure")
        public void recordFailure(IllegalArgumentException failure) {
            failures++;
            lastFailure = failure;
        }
    }
}
