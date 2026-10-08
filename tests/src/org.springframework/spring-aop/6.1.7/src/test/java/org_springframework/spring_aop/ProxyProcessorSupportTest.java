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
import org.springframework.aop.framework.ProxyProcessorSupport;

public class ProxyProcessorSupportTest {
    @Test
    void evaluateProxyInterfacesAddsReasonableInterfaceWithMethods() {
        TestProxyProcessorSupport support = new TestProxyProcessorSupport();
        ProxyFactory proxyFactory = new ProxyFactory();

        support.evaluate(ServiceBean.class, proxyFactory);

        assertThat(proxyFactory.isProxyTargetClass()).isFalse();
        assertThat(proxyFactory.getProxiedInterfaces()).contains(ServiceContract.class);
    }

    private static final class TestProxyProcessorSupport extends ProxyProcessorSupport {
        void evaluate(Class<?> beanClass, ProxyFactory proxyFactory) {
            evaluateProxyInterfaces(beanClass, proxyFactory);
        }
    }

    public interface ServiceContract {
        String message();
    }

    public static final class ServiceBean implements ServiceContract {
        @Override
        public String message() {
            return "spring-aop";
        }
    }
}
