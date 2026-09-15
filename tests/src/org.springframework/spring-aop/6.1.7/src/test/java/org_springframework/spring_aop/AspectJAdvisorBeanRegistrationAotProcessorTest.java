/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AspectJAdvisorBeanRegistrationAotProcessorTest {

    @Test
    void detectsAjcCompiledAspectDuringBeanRegistrationAotProcessing() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("compiledAspect", new RootBeanDefinition(AjcCompiledAspect.class));
        RegisteredBean registeredBean = RegisteredBean.of(beanFactory, "compiledAspect");

        List<BeanRegistrationAotContribution> contributions = AotServices.factories()
                .load(BeanRegistrationAotProcessor.class)
                .stream()
                .map(processor -> processor.processAheadOfTime(registeredBean))
                .filter(Objects::nonNull)
                .toList();

        assertThat(contributions).hasSize(1);
    }

    @Aspect
    public static class AjcCompiledAspect {
        private Object ajc$perSingletonInstance;
    }
}
