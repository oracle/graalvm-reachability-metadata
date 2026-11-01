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
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;

import static org.junit.jupiter.api.Assertions.*;

public class DisposableBeanAdapterTest {
    @Test
    void invokesConfiguredDestructionMethod() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        RootBeanDefinition definition = new RootBeanDefinition(TestBeans.Lifecycle.class);
        definition.setDestroyMethodName("stop");
        factory.registerBeanDefinition("worker", definition);
        TestBeans.Lifecycle bean;
        try {
            bean = (TestBeans.Lifecycle) factory.getBean("worker");
            assertFalse(bean.isStopped());
        } finally {
            factory.destroySingletons();
        }
        assertTrue(bean.isStopped());
    }
}
