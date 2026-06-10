package org_springframework.spring_test;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.DefaultEventListenerFactory__TestContext002_BeanDefinitions;
import org.springframework.context.event.EventListenerMethodProcessor__TestContext002_BeanDefinitions;
import org.springframework.test.context.support.DynamicPropertyRegistrarBeanInitializer__TestContext002_BeanDefinitions;

/**
 * Register bean definitions for the bean factory.
 */
@Generated
public class TestClassScannerTest_AbstractSpringClassTemplateTests_InheritedNestedTests__TestContext002_BeanFactoryRegistrations {
    /**
     * Register the bean definitions.
     */
    public void registerBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        beanFactory.registerBeanDefinition("org.springframework.context.event.internalEventListenerProcessor",
                EventListenerMethodProcessor__TestContext002_BeanDefinitions.getInternalEventListenerProcessorBeanDefinition());
        beanFactory.registerBeanDefinition("org.springframework.context.event.internalEventListenerFactory",
                DefaultEventListenerFactory__TestContext002_BeanDefinitions.getInternalEventListenerFactoryBeanDefinition());
        beanFactory.registerBeanDefinition("org.springframework.test.context.support.internalDynamicPropertyRegistrarBeanInitializer",
                DynamicPropertyRegistrarBeanInitializer__TestContext002_BeanDefinitions.getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition());
    }

    /**
     * Register the aliases.
     */
    public void registerAliases(DefaultListableBeanFactory beanFactory) {
    }
}
