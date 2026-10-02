/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory;
import org.springframework.aop.aspectj.annotation.SingletonMetadataAwareAspectInstanceFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;

public class AbstractAspectJAdviceTest {

    @Test
    void invokesAspectJAdviceMethodThroughSpringProxy() {
        CountingAspect aspect = new CountingAspect();
        GreetingService proxy = createProxy(new DefaultGreetingService(), aspect);

        String greeting = proxy.greet("GraalVM");

        assertThat(greeting).isEqualTo("Hello GraalVM");
        assertThat(aspect.invocations()).isEqualTo(1);
    }

    private static GreetingService createProxy(GreetingService target, CountingAspect aspect) {
        ReflectiveAspectJAdvisorFactory advisorFactory = new ReflectiveAspectJAdvisorFactory();
        SingletonMetadataAwareAspectInstanceFactory aspectInstanceFactory =
                new SingletonMetadataAwareAspectInstanceFactory(aspect, "countingAspect");
        List<Advisor> advisors = advisorFactory.getAdvisors(aspectInstanceFactory);
        assertThat(advisors).hasSize(1);

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(ExposeInvocationInterceptor.ADVISOR);
        advisors.forEach(proxyFactory::addAdvisor);
        return (GreetingService) proxyFactory.getProxy();
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class DefaultGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    @Aspect
    public static class CountingAspect {
        private final AtomicInteger invocations = new AtomicInteger();

        @Before("execution(* greet(..))")
        public void beforeInvocation() {
            this.invocations.incrementAndGet();
        }

        int invocations() {
            return this.invocations.get();
        }
    }
}
