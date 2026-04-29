/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.spring.util.SpringProxyUtils;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.ProxyFactory;

public class SpringProxyUtilsTest {
    @Test
    void findsInterfacesFromSpringJdkProxyAdvisedSupport() throws Exception {
        ProxyFactory proxyFactory = new ProxyFactory(new GreetingServiceImpl());
        proxyFactory.addInterface(GreetingService.class);

        Object proxy = proxyFactory.getProxy();

        assertThat(SpringProxyUtils.findInterfaces(proxy)).containsExactly(GreetingService.class);
    }

    @Test
    void extractsAdvisedSupportFromCglibStyleProxyCallback() throws Exception {
        AdvisedSupport advisedSupport = new AdvisedSupport(GreetingService.class);
        CglibStyleProxy proxy = new CglibStyleProxy(advisedSupport);

        assertThat(SpringProxyUtils.getAdvisedSupport(proxy)).isSameAs(advisedSupport);
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    private static class CglibStyleProxy {
        // Checkstyle: stop field name check
        private final CglibStyleCallback CGLIB$CALLBACK_0;
        // Checkstyle: resume field name check

        CglibStyleProxy(AdvisedSupport advisedSupport) {
            CGLIB$CALLBACK_0 = new CglibStyleCallback(advisedSupport);
        }
    }

    private static class CglibStyleCallback {
        private final AdvisedSupport advised;

        CglibStyleCallback(AdvisedSupport advised) {
            this.advised = advised;
        }
    }
}
