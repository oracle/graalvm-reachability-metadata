/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.support.SpringFactoriesLoader;

public class AspectJAdvisorBeanRegistrationAotProcessorTest {

    private static final String AOT_FACTORIES_RESOURCE_LOCATION = "META-INF/spring/aot.factories";

    private static final String PROCESSOR_CLASS_NAME =
            "org.springframework.aop.aspectj.annotation.AspectJAdvisorBeanRegistrationAotProcessor";

    @Test
    void detectsAjcCompiledBeanClass() {
        BeanRegistrationAotProcessor processor = loadAspectJAdvisorProcessor();
        RegisteredBean registeredBean = registerBean(AjcCompiledAspect.class);

        BeanRegistrationAotContribution contribution = processor.processAheadOfTime(registeredBean);

        assertThat(contribution).isNotNull();
    }

    private static BeanRegistrationAotProcessor loadAspectJAdvisorProcessor() {
        ClassLoader classLoader = AspectJAdvisorBeanRegistrationAotProcessorTest.class.getClassLoader();
        List<BeanRegistrationAotProcessor> processors = SpringFactoriesLoader
                .forResourceLocation(AOT_FACTORIES_RESOURCE_LOCATION, classLoader)
                .load(BeanRegistrationAotProcessor.class);

        return processors.stream()
                .filter(processor -> processor.getClass().getName().equals(PROCESSOR_CLASS_NAME))
                .findFirst()
                .orElseThrow();
    }

    private static RegisteredBean registerBean(Class<?> beanClass) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        String beanName = beanClass.getName();
        beanFactory.registerBeanDefinition(beanName, new RootBeanDefinition(beanClass));
        return RegisteredBean.of(beanFactory, beanName);
    }

    public static class AjcCompiledAspect {
        private Object ajc$perSingletonInstance;
    }
}
