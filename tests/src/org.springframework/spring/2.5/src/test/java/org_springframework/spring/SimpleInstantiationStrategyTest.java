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

public class SimpleInstantiationStrategyTest {
    public static class PersonFactory {
        public static TestBeans.Person create() {
            TestBeans.Person person = new TestBeans.Person();
            person.setName("factory-created");
            return person;
        }
    }

    @Test
    void constructsBeansUsingDefaultConstructorAndStaticFactory() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        factory.registerBeanDefinition("plain", new RootBeanDefinition(TestBeans.Person.class));
        RootBeanDefinition definition = new RootBeanDefinition(PersonFactory.class);
        definition.setFactoryMethodName("create");
        factory.registerBeanDefinition("made", definition);
        try {
            assertEquals("initial", ((TestBeans.Person) factory.getBean("plain")).getName());
            assertEquals("factory-created", ((TestBeans.Person) factory.getBean("made")).getName());
        } finally {
            factory.destroySingletons();
        }
    }
}
