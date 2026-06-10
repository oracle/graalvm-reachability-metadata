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
public class TestBeanOverrideHandlerTest__TestContext001_BeanFactoryRegistrations {
    /**
     * Register the bean definitions.
     */
    public void registerBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        beanFactory.registerBeanDefinition("org.springframework.context.event.internalEventListenerProcessor",
                EventListenerMethodProcessor__TestContext001_BeanDefinitions.getInternalEventListenerProcessorBeanDefinition());
        beanFactory.registerBeanDefinition("org.springframework.context.event.internalEventListenerFactory",
                DefaultEventListenerFactory__TestContext001_BeanDefinitions.getInternalEventListenerFactoryBeanDefinition());
        beanFactory.registerBeanDefinition("testBeanOverrideHandlerTest.Config",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.Config.getConfigBeanDefinition());
        beanFactory.registerBeanDefinition("org.springframework.test.context.support.internalDynamicPropertyRegistrarBeanInitializer",
                DynamicPropertyRegistrarBeanInitializer__TestContext001_BeanDefinitions.getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition());
        beanFactory.registerBeanDefinition("greetingService",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.Config.getGreetingServiceBeanDefinition());
    }

    /**
     * Register the aliases.
     */
    public void registerAliases(DefaultListableBeanFactory beanFactory) {
    }
}
