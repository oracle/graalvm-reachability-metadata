/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceLocatorFactoryBeanInnerServiceLocatorInvocationHandlerTest {
    public interface Locator {
        TestBeans.Greeting service();
    }

    @Test
    void dispatchesTypeLookupAndObjectMethods() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        TestBeans.GreetingService service = new TestBeans.GreetingService();
        beans.addBean("service", service);
        ServiceLocatorFactoryBean factory = new ServiceLocatorFactoryBean();
        factory.setBeanFactory(beans);
        factory.setServiceLocatorInterface(Locator.class);
        factory.afterPropertiesSet();
        Locator locator = (Locator) factory.getObject();
        assertSame(service, locator.service());
        assertTrue(locator.toString().contains(Locator.class.getName()));
    }
}
