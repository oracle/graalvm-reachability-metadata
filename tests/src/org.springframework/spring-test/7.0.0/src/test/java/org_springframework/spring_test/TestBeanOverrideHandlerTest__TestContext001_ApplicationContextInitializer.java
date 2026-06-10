package org_springframework.spring_test;

import java.lang.Override;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * {@link ApplicationContextInitializer} to restore an application context based on previous AOT
 * processing.
 */
@Generated
public class TestBeanOverrideHandlerTest__TestContext001_ApplicationContextInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
    @Override
    public void initialize(GenericApplicationContext applicationContext) {
        DefaultListableBeanFactory beanFactory = applicationContext.getDefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
        new TestBeanOverrideHandlerTest__TestContext001_BeanFactoryRegistrations().registerBeanDefinitions(beanFactory);
        new TestBeanOverrideHandlerTest__TestContext001_BeanFactoryRegistrations().registerAliases(beanFactory);
    }
}
