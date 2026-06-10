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
public class TestClassScannerTest_AbstractSpringClassTemplateTests_InheritedNestedTests__TestContext002_ApplicationContextInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
    @Override
    public void initialize(GenericApplicationContext applicationContext) {
        DefaultListableBeanFactory beanFactory = applicationContext.getDefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
        new TestClassScannerTest_AbstractSpringClassTemplateTests_InheritedNestedTests__TestContext002_BeanFactoryRegistrations()
                .registerBeanDefinitions(beanFactory);
        new TestClassScannerTest_AbstractSpringClassTemplateTests_InheritedNestedTests__TestContext002_BeanFactoryRegistrations()
                .registerAliases(beanFactory);
    }
}
