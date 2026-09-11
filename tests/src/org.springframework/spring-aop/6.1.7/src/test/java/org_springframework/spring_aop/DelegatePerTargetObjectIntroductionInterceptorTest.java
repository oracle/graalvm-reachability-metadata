/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatePerTargetObjectIntroductionInterceptor;

public class DelegatePerTargetObjectIntroductionInterceptorTest {
    @Test
    void createsSeparateDelegateForEachTargetObject() {
        DelegatePerTargetObjectIntroductionInterceptor interceptor =
                new DelegatePerTargetObjectIntroductionInterceptor(CountingMixin.class, CountingOperations.class);
        DefaultIntroductionAdvisor advisor = new DefaultIntroductionAdvisor(interceptor, CountingOperations.class);

        CountingOperations firstProxy = createProxy(new NamedServiceImpl("first"), advisor);
        CountingOperations secondProxy = createProxy(new NamedServiceImpl("second"), advisor);

        assertThat(firstProxy.incrementAndGet()).isEqualTo(1);
        assertThat(firstProxy.incrementAndGet()).isEqualTo(2);
        assertThat(secondProxy.incrementAndGet()).isEqualTo(1);
        assertThat(firstProxy.currentValue()).isEqualTo(2);
        assertThat(secondProxy.currentValue()).isEqualTo(1);
    }

    @Test
    void returnsProxyWhenIntroducedMethodReturnsDelegateItself() {
        DelegatePerTargetObjectIntroductionInterceptor interceptor =
                new DelegatePerTargetObjectIntroductionInterceptor(CountingMixin.class, CountingOperations.class);
        DefaultIntroductionAdvisor advisor = new DefaultIntroductionAdvisor(interceptor, CountingOperations.class);

        CountingOperations proxy = createProxy(new NamedServiceImpl("self"), advisor);

        assertThat(proxy.self()).isSameAs(proxy);
    }

    private static CountingOperations createProxy(NamedService target, DefaultIntroductionAdvisor advisor) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(advisor);
        Object proxy = proxyFactory.getProxy();
        assertThat(proxy).isInstanceOf(NamedService.class);
        assertThat(((NamedService) proxy).name()).isEqualTo(target.name());
        return (CountingOperations) proxy;
    }

    public interface NamedService {
        String name();
    }

    public interface CountingOperations {
        int incrementAndGet();

        int currentValue();

        CountingOperations self();
    }

    public static class NamedServiceImpl implements NamedService {
        private final String name;

        NamedServiceImpl(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return this.name;
        }
    }

    public static class CountingMixin implements CountingOperations {
        private int value;

        @Override
        public int incrementAndGet() {
            this.value++;
            return this.value;
        }

        @Override
        public int currentValue() {
            return this.value;
        }

        @Override
        public CountingOperations self() {
            return this;
        }
    }
}
