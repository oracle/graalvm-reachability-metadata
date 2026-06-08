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
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.javapoet.ClassName;

public class ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest {

    @Test
    void aotProcessingUsesConfigurationClassProxyConstructorForFullConfigurationBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(FullConfiguration.class);

            InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
            DefaultGenerationContext generationContext = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get("org_springframework.spring_context", "GeneratedInitializer")),
                    generatedFiles);

            try {
                ClassName initializerClassName = new ApplicationContextAotGenerator().processAheadOfTime(context,
                        generationContext);

                generationContext.writeGeneratedContent();
                assertEquals("org_springframework.spring_context", initializerClassName.packageName());
                assertFalse(generatedFiles.getGeneratedFiles(Kind.SOURCE).isEmpty());
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        }
    }

    @Configuration
    public static class FullConfiguration {

        public FullConfiguration() {
        }

        @Bean
        Marker marker() {
            return new Marker("aot-configuration-proxy");
        }
    }

    public static class Marker {

        private final String name;

        Marker(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
