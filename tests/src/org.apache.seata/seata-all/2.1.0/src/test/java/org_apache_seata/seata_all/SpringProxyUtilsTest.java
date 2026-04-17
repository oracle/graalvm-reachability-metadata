/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import org.aopalliance.intercept.MethodInterceptor;
import org.apache.seata.spring.util.SpringProxyUtils;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.ProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringProxyUtilsTest {
    private static final MethodInterceptor PASSTHROUGH_ADVICE = invocation -> invocation.proceed();

    @Test
    void getAdvisedSupportReadsTheAdvisedFieldFromJdkDynamicProxies() throws Exception {
        EchoServiceImpl target = new EchoServiceImpl();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(EchoService.class);
        proxyFactory.addAdvice(PASSTHROUGH_ADVICE);

        Object proxy = proxyFactory.getProxy();
        AdvisedSupport advisedSupport = SpringProxyUtils.getAdvisedSupport(proxy);

        assertThat(SpringProxyUtils.findInterfaces(proxy)).containsExactly(EchoService.class);
        assertThat(advisedSupport.getProxiedInterfaces()).containsExactly(EchoService.class);
        assertThat(advisedSupport.getTargetSource().getTarget()).isSameAs(target);
    }

    @Test
    void getAdvisedSupportReadsCglibCallbackStateFromClassBasedProxies() throws Exception {
        ClassBasedService target = new ClassBasedService();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(PASSTHROUGH_ADVICE);

        Object proxy = proxyFactory.getProxy();
        AdvisedSupport advisedSupport = SpringProxyUtils.getAdvisedSupport(proxy);

        assertThat(SpringProxyUtils.findTargetClass(proxy)).isEqualTo(ClassBasedService.class);
        assertThat(advisedSupport.getTargetSource().getTarget()).isSameAs(target);
        assertThat(advisedSupport.getTargetSource().getTargetClass()).isEqualTo(ClassBasedService.class);
    }

    public interface EchoService {
        String echo(String value);
    }

    public static final class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String value) {
            return value;
        }
    }

    public static class ClassBasedService {
        public String greet() {
            return "hello";
        }
    }
}
