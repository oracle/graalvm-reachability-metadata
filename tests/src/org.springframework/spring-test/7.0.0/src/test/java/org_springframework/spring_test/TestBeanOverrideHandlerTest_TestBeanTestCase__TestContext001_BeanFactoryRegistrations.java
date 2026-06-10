/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.test.context.support.DynamicPropertyRegistrarBeanInitializer;

@Generated
public class TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_BeanFactoryRegistrations {
    public void registerBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        beanFactory.registerBeanDefinition(
                "org.springframework.context.event.internalEventListenerProcessor",
                getInternalEventListenerProcessorBeanDefinition()
        );
        beanFactory.registerBeanDefinition(
                "org.springframework.context.event.internalEventListenerFactory",
                getInternalEventListenerFactoryBeanDefinition()
        );
        beanFactory.registerBeanDefinition(
                "testBeanOverrideHandlerTest.TestConfiguration",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.TestConfiguration
                        .getTestConfigurationBeanDefinition()
        );
        beanFactory.registerBeanDefinition(
                "org.springframework.test.context.support.internalDynamicPropertyRegistrarBeanInitializer",
                getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition()
        );
        beanFactory.registerBeanDefinition(
                "messageService",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.TestConfiguration
                        .getMessageServiceBeanDefinition()
        );
        beanFactory.registerBeanDefinition(
                "messageClient",
                TestBeanOverrideHandlerTest__TestContext001_BeanDefinitions.TestConfiguration
                        .getMessageClientBeanDefinition()
        );
    }

    public void registerAliases(DefaultListableBeanFactory beanFactory) {
    }

    private static BeanDefinition getInternalEventListenerProcessorBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(EventListenerMethodProcessor.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(EventListenerMethodProcessor::new);
        return beanDefinition;
    }

    private static BeanDefinition getInternalEventListenerFactoryBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(DefaultEventListenerFactory.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(DefaultEventListenerFactory::new);
        return beanDefinition;
    }

    private static BeanDefinition getInternalDynamicPropertyRegistrarBeanInitializerBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(DynamicPropertyRegistrarBeanInitializer.class);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        beanDefinition.setInstanceSupplier(DynamicPropertyRegistrarBeanInitializer::new);
        return beanDefinition;
    }
}
