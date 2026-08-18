/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.InputStreamSource;
import org.springframework.javapoet.ClassName;

public class ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest {

    @Test
    void aotGenerationUsesProxyConstructorForConstructorInjectedConfigurationClass() throws IOException {
        try {
            InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
            DefaultGenerationContext generationContext = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get("org_springframework.spring_context", "GeneratedApplication")),
                    generatedFiles);
            GenericApplicationContext applicationContext = new GenericApplicationContext();
            try {
                applicationContext.registerBean(Collaborator.class);
                AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(applicationContext);
                reader.register(ConstructorInjectedConfiguration.class);

                new ApplicationContextAotGenerator().processAheadOfTime(applicationContext, generationContext);
                generationContext.writeGeneratedContent();
            } finally {
                applicationContext.close();
            }

            String source = generatedSource(generatedFiles);
            assertTrue(source.contains("initializeConfigurationClass"));
            assertTrue(source.contains("forConstructor"));
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String generatedSource(InMemoryGeneratedFiles generatedFiles) throws IOException {
        StringBuilder source = new StringBuilder();
        for (Map.Entry<String, InputStreamSource> entry : generatedFiles.getGeneratedFiles(Kind.SOURCE).entrySet()) {
            source.append(generatedFiles.getGeneratedFileContent(Kind.SOURCE, entry.getKey()));
        }
        return source.toString();
    }

    @Configuration
    public static class ConstructorInjectedConfiguration {

        private final Collaborator collaborator;

        public ConstructorInjectedConfiguration(Collaborator collaborator) {
            this.collaborator = collaborator;
        }

        @Bean
        public String collaboratorName() {
            return this.collaborator.name();
        }
    }

    public static class Collaborator {

        public String name() {
            return "configured";
        }
    }
}
