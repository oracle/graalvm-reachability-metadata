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
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.test.context.support.DynamicPropertyRegistrarBeanInitializer;

@Generated
public final class TestClassScannerTest__TestContext002_ApplicationContextInitializer
        implements ApplicationContextInitializer<GenericApplicationContext> {

    @Override
    public void initialize(GenericApplicationContext applicationContext) {
        DefaultListableBeanFactory beanFactory = applicationContext.getDefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
        applicationContext.registerBean(
                "org.springframework.context.event.internalEventListenerProcessor",
                EventListenerMethodProcessor.class,
                EventListenerMethodProcessor::new,
                beanDefinition -> beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE));
        applicationContext.registerBean(
                "org.springframework.context.event.internalEventListenerFactory",
                DefaultEventListenerFactory.class,
                DefaultEventListenerFactory::new,
                beanDefinition -> beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE));
        applicationContext.registerBean(
                "testClassScannerTest.TestConfiguration",
                TestClassScannerTest.TestConfiguration.class,
                TestClassScannerTest.TestConfiguration::new);
        applicationContext.registerBean(
                "org.springframework.test.context.support.internalDynamicPropertyRegistrarBeanInitializer",
                DynamicPropertyRegistrarBeanInitializer.class,
                DynamicPropertyRegistrarBeanInitializer::new,
                beanDefinition -> beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE));
    }
}
