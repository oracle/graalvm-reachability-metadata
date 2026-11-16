/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleInstantiationStrategyTest {
    @Test
    void createsDefaultBeansAndInvokesConfiguredFactoryMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        factory.registerBeanDefinition("component", new RootBeanDefinition(Component.class));
        assertEquals("ready", factory.getBean("component", Component.class).status);
        RootBeanDefinition created = new RootBeanDefinition(ComponentFactory.class);
        created.setFactoryMethodName("create");
        factory.registerBeanDefinition("created", created);
        assertEquals("factory", factory.getBean("created", Component.class).status);
    }

    @Test
    void representsNullFactoryResultsAsStableNullBeans() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(ComponentFactory.class);
        definition.setFactoryMethodName("optional");
        factory.registerBeanDefinition("optional", definition);
        Object result = factory.getBean("optional");
        assertTrue(result.equals(null));
        assertEquals("null", result.toString());
        assertSame(result, factory.getBean("optional"));
        assertEquals(Component.class, factory.getType("optional"));
    }

    public static class Component {
        private String status = "ready";
    }

    public static class ComponentFactory {
        public static Component create() {
            Component component = new Component();
            component.status = "factory";
            return component;
        }

        public static Component optional() {
            return null;
        }
    }
}
