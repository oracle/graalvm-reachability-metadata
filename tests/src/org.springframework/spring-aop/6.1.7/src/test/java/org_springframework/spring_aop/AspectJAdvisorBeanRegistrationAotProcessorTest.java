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
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.TypeHint;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.javapoet.ClassName;

public class AspectJAdvisorBeanRegistrationAotProcessorTest {
    @Test
    void aotFactoriesContributeForBeanWithAspectjCompilerFields() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("compiledByAjc", new RootBeanDefinition(CompiledByAjc.class));
        RegisteredBean registeredBean = RegisteredBean.of(beanFactory, "compiledByAjc");

        List<BeanRegistrationAotContribution> contributions = AotServices
                .factories(beanFactory.getBeanClassLoader())
                .load(BeanRegistrationAotProcessor.class)
                .stream()
                .map(processor -> processor.processAheadOfTime(registeredBean))
                .filter(Objects::nonNull)
                .toList();

        assertThat(contributions).hasSize(1);

        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get("org_springframework.spring_aop", "AspectJAdvisorAot")),
                new InMemoryGeneratedFiles());
        contributions.get(0).applyTo(generationContext, new UnsupportedBeanRegistrationCode());

        TypeHint typeHint = generationContext.getRuntimeHints().reflection().getTypeHint(CompiledByAjc.class);
        assertThat(typeHint).isNotNull();
        assertThat(typeHint.getMemberCategories()).contains(MemberCategory.DECLARED_FIELDS);
    }

    public static class CompiledByAjc {
        private static final Object ajc$perSingletonInstance = new Object();
    }

    private static class UnsupportedBeanRegistrationCode implements BeanRegistrationCode {
        @Override
        public ClassName getClassName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public GeneratedMethods getMethods() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addInstancePostProcessor(MethodReference methodReference) {
            throw new UnsupportedOperationException();
        }
    }
}
