/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.event.DefaultEventListenerFactory__TestContext001_BeanDefinitions;
import org.springframework.context.event.EventListenerMethodProcessor__TestContext001_BeanDefinitions;
import org.springframework.test.context.support.DynamicPropertyRegistrarBeanInitializer__TestContext001_BeanDefinitions;

public final class TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_BeanFactoryRegistrations {
    public void registerBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        beanFactory.registerBeanDefinition(
                "org.springframework.context.event.internalEventListenerProcessor",
                EventListenerMethodProcessor__TestContext001_BeanDefinitions
                        .getInternalEventListenerProcessorBeanDefinition());
        beanFactory.registerBeanDefinition(
                "org.springframework.context.event.internalEventListenerFactory",
                DefaultEventListenerFactory__TestContext001_BeanDefinitions
                        .getInternalEventListenerFactoryBeanDefinition());
        beanFactory.registerBeanDefinition(
                "testBeanOverrideHandlerTest.TestConfiguration",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.TestConfiguration
                        .getTestConfigurationBeanDefinition());
        beanFactory.registerBeanDefinition(
                "org.springframework.test.context.support.internalDynamicPropertyRegistrarBeanInitializer",
                DynamicPropertyRegistrarBeanInitializer__TestContext001_BeanDefinitions
                        .getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition());
        beanFactory.registerBeanDefinition(
                "greetingService",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.TestConfiguration
                        .getGreetingServiceBeanDefinition());
    }

    public void registerAliases(DefaultListableBeanFactory beanFactory) {
    }
}
