/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.DefaultEventListenerFactory__TestContext001_BeanDefinitions;
import org.springframework.context.event.EventListenerMethodProcessor__TestContext001_BeanDefinitions;
import org.springframework.test.context.support.DynamicPropertyRegistrarBeanInitializer__TestContext001_BeanDefinitions;

/**
 * Register bean definitions for the bean factory.
 */
@Generated
public class TestBeanOverrideHandlerTest_FactoryMethodTestCase__TestContext001_BeanFactoryRegistrations {
    /**
     * Register the bean definitions.
     */
    public void registerBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        beanFactory.registerBeanDefinition("org.springframework.context.event.internalEventListenerProcessor",
                EventListenerMethodProcessor__TestContext001_BeanDefinitions
                        .getInternalEventListenerProcessorBeanDefinition());
        beanFactory.registerBeanDefinition("org.springframework.context.event.internalEventListenerFactory",
                DefaultEventListenerFactory__TestContext001_BeanDefinitions
                        .getInternalEventListenerFactoryBeanDefinition());
        beanFactory.registerBeanDefinition("testBeanOverrideHandlerTest.GreetingConfiguration",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.GreetingConfiguration
                        .getGreetingConfigurationBeanDefinition());
        beanFactory.registerBeanDefinition("org.springframework.test.context.support.internalDynamicPropertyRegistrarBeanInitializer",
                DynamicPropertyRegistrarBeanInitializer__TestContext001_BeanDefinitions
                        .getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition());
        beanFactory.registerBeanDefinition("greetingService",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.GreetingConfiguration
                        .getGreetingServiceBeanDefinition());
    }

    /**
     * Register the aliases.
     */
    public void registerAliases(DefaultListableBeanFactory beanFactory) {
    }
}
