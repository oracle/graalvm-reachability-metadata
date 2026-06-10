/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

public final class TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_ApplicationContextInitializer
        implements ApplicationContextInitializer<GenericApplicationContext> {

    @Override
    public void initialize(GenericApplicationContext applicationContext) {
        DefaultListableBeanFactory beanFactory = applicationContext.getDefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
        new TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_BeanFactoryRegistrations()
                .registerBeanDefinitions(beanFactory);
        new TestBeanOverrideHandlerTest_TestBeanTestCase__TestContext001_BeanFactoryRegistrations()
                .registerAliases(beanFactory);
    }
}
