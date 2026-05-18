/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

public class ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest {

    @Test
    void aotProcessingGeneratesInstanceSupplierForEnhancedConfigurationConstructor() {
        try {
            InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
            ClassName targetClassName = ClassName.get(
                    "org_springframework.spring_context.generated", "TestApplication");
            DefaultGenerationContext generationContext = new DefaultGenerationContext(
                    new ClassNameGenerator(targetClassName), generatedFiles);
            ClassName initializerClassName = processAheadOfTime(generationContext);

            generationContext.writeGeneratedContent();

            assertEquals("org_springframework.spring_context.generated", initializerClassName.packageName());
            assertFalse(generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE).isEmpty());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static ClassName processAheadOfTime(DefaultGenerationContext generationContext) {
        GenericApplicationContext context = new GenericApplicationContext();
        try {
            AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(context);
            reader.register(ProxyBackedConfiguration.class);
            return new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
        } finally {
            context.close();
        }
    }

    @Configuration
    public static class ProxyBackedConfiguration {

        public ProxyBackedConfiguration() {
        }

        @Bean
        public SampleBean sampleBean() {
            return new SampleBean("aot");
        }
    }

    public static class SampleBean {

        private final String name;

        public SampleBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
