/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.net.MalformedURLException;

import com.caucho.hessian.client.HessianProxyFactory;
import org.junit.jupiter.api.Test;

import org.springframework.remoting.caucho.HessianProxyFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class HessianClientInterceptorTest {

    @Test
    void proxyFactoryBeanInvokesPreparedHessianProxy() throws Exception {
        HessianProxyFactoryBean factoryBean = new HessianProxyFactoryBean();
        factoryBean.setServiceInterface(GreetingService.class);
        factoryBean.setServiceUrl("https://example.test/hessian/greeting");
        factoryBean.setProxyFactory(new StaticHessianProxyFactory(new LocalGreetingService()));
        factoryBean.afterPropertiesSet();

        GreetingService service = (GreetingService) factoryBean.getObject();
        String greeting = service.greet("Spring");

        assertThat(greeting).isEqualTo("Hello Spring");
    }

    public interface GreetingService {

        String greet(String name);
    }

    private static final class LocalGreetingService implements GreetingService {

        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    private static final class StaticHessianProxyFactory extends HessianProxyFactory {

        private final GreetingService service;

        private StaticHessianProxyFactory(GreetingService service) {
            this.service = service;
        }

        @Override
        public Object create(Class<?> api, String urlName, ClassLoader loader) throws MalformedURLException {
            assertThat(api).isEqualTo(GreetingService.class);
            assertThat(urlName).isEqualTo("https://example.test/hessian/greeting");
            assertThat(loader).isNotNull();
            return this.service;
        }
    }
}
