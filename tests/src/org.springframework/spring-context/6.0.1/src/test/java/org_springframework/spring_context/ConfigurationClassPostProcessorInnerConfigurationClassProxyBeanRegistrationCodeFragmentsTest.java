/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
            DefaultGenerationContext generationContext = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get(EnhancedConfigurationApplication.class)), generatedFiles);
            try (GenericApplicationContext applicationContext = createApplicationContext()) {
                ClassName initializerClassName = new ApplicationContextAotGenerator()
                        .processAheadOfTime(applicationContext, generationContext);

                generationContext.writeGeneratedContent();

                assertNotNull(initializerClassName);
                assertFalse(generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE).isEmpty());
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static GenericApplicationContext createApplicationContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        new AnnotatedBeanDefinitionReader(context).register(EnhancedConfigurationApplication.class);
        return context;
    }

    @Configuration
    public static class EnhancedConfigurationApplication {

        @Bean
        public String sampleBean() {
            return "sample";
        }
    }
}
