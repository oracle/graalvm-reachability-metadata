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

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.javapoet.ClassName;

public class AspectJAdvisorBeanRegistrationAotProcessorTest {

    @Test
    void registersDeclaredFieldHintsForAspectJCompiledBean() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("aspectJCompiledBean", new RootBeanDefinition(AspectJCompiledBean.class));
        RegisteredBean registeredBean = RegisteredBean.of(beanFactory, "aspectJCompiledBean");
        List<BeanRegistrationAotProcessor> processors = AotServices.factories()
                .load(BeanRegistrationAotProcessor.class).asList();

        List<BeanRegistrationAotContribution> contributions = processors.stream()
                .map(processor -> processor.processAheadOfTime(registeredBean))
                .filter(Objects::nonNull)
                .toList();

        assertThat(contributions).hasSize(1);
        RuntimeHints runtimeHints = new RuntimeHints();
        GenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(getClass())), new InMemoryGeneratedFiles(), runtimeHints);
        contributions.get(0).applyTo(generationContext, null);
        TypeHint typeHint = runtimeHints.reflection().getTypeHint(AspectJCompiledBean.class);
        assertThat(typeHint).isNotNull();
        assertThat(typeHint.getMemberCategories()).contains(MemberCategory.DECLARED_FIELDS);
    }

    public static class AspectJCompiledBean {
        private Object ajc$state;
    }
}
