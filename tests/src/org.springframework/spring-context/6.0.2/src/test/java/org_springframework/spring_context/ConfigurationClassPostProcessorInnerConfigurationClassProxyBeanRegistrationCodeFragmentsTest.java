/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Executable;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GeneratedClasses;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.core.Conventions;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;

public class ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest {

    private static final String CONFIGURATION_CLASS_ATTRIBUTE =
            Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

    @Test
    void fullConfigurationAotContributionUsesMatchingProxyConstructor() {
        final Executable userConstructor = BeanUtils.getResolvableConstructor(UserConfiguration.class);
        final BeanRegistrationAotContribution contribution = createContributionForProxyConfiguration();
        final CapturingBeanRegistrationCodeFragments delegate = new CapturingBeanRegistrationCodeFragments();
        final SimpleGenerationContext generationContext = new SimpleGenerationContext();
        final BeanRegistrationCodeFragments fragments = contribution.customizeBeanRegistrationCodeFragments(
                generationContext, delegate);

        fragments.generateInstanceSupplierCode(generationContext, new SimpleBeanRegistrationCode(),
                userConstructor, false);

        assertNotNull(delegate.executable);
        assertEquals(ProxyConfiguration.class, delegate.executable.getDeclaringClass());
    }

    private static BeanRegistrationAotContribution createContributionForProxyConfiguration() {
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        final RootBeanDefinition beanDefinition = new RootBeanDefinition(ProxyConfiguration.class);
        beanDefinition.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, "full");
        beanFactory.registerBeanDefinition("proxyConfiguration", beanDefinition);
        final RegisteredBean registeredBean = RegisteredBean.of(beanFactory, "proxyConfiguration");
        final BeanRegistrationAotContribution contribution = new ConfigurationClassPostProcessor()
                .processAheadOfTime(registeredBean);
        assertNotNull(contribution);
        return contribution;
    }

    private static final class CapturingBeanRegistrationCodeFragments implements BeanRegistrationCodeFragments {

        private Executable executable;

        @Override
        public ClassName getTarget(RegisteredBean registeredBean, Executable constructorOrFactoryMethod) {
            return ClassName.get(ProxyConfiguration.class);
        }

        @Override
        public CodeBlock generateNewBeanDefinitionCode(GenerationContext generationContext, ResolvableType beanType,
                BeanRegistrationCode beanRegistrationCode) {
            return CodeBlock.of("");
        }

        @Override
        public CodeBlock generateSetBeanDefinitionPropertiesCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition,
                Predicate<String> attributeFilter) {
            return CodeBlock.of("");
        }

        @Override
        public CodeBlock generateSetBeanInstanceSupplierCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode, CodeBlock instanceSupplierCode,
                List<MethodReference> postProcessors) {
            return CodeBlock.of("");
        }

        @Override
        public CodeBlock generateInstanceSupplierCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode, Executable constructorOrFactoryMethod,
                boolean allowDirectSupplierShortcut) {
            this.executable = constructorOrFactoryMethod;
            return CodeBlock.of("");
        }

        @Override
        public CodeBlock generateReturnCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode) {
            return CodeBlock.of("");
        }
    }

    private static final class SimpleGenerationContext implements GenerationContext {

        private final RuntimeHints runtimeHints = new RuntimeHints();

        @Override
        public GeneratedClasses getGeneratedClasses() {
            throw new UnsupportedOperationException("Generated classes are not used by this test");
        }

        @Override
        public GeneratedFiles getGeneratedFiles() {
            throw new UnsupportedOperationException("Generated files are not used by this test");
        }

        @Override
        public RuntimeHints getRuntimeHints() {
            return this.runtimeHints;
        }

        @Override
        public GenerationContext withName(String name) {
            return this;
        }
    }

    private static final class SimpleBeanRegistrationCode implements BeanRegistrationCode {

        @Override
        public ClassName getClassName() {
            return ClassName.get(ProxyConfiguration.class);
        }

        @Override
        public GeneratedMethods getMethods() {
            throw new UnsupportedOperationException("Generated methods are not used by this test");
        }

        @Override
        public void addInstancePostProcessor(MethodReference methodReference) {
            throw new UnsupportedOperationException("Instance post-processors are not used by this test");
        }
    }

    public static class UserConfiguration {

        private final String name;

        public UserConfiguration(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    public static class ProxyConfiguration extends UserConfiguration {

        public ProxyConfiguration(String name) {
            super(name);
        }
    }
}
