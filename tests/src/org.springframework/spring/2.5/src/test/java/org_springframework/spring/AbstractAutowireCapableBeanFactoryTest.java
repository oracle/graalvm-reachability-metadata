/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractAutowireCapableBeanFactoryTest {
    @Test
    void invokesConfiguredInitializationMethod() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(TestBeans.Lifecycle.class);
        definition.setInitMethodName("start");
        factory.registerBeanDefinition("worker", definition);
        try {
            TestBeans.Lifecycle bean = (TestBeans.Lifecycle) factory.configureBean(new TestBeans.Lifecycle(), "worker");
            assertTrue(bean.isStarted());
        } finally {
            factory.destroySingletons();
        }
    }
}
