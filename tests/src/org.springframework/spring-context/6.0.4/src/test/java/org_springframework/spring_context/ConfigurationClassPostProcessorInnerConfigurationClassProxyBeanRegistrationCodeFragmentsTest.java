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
    void generatesConstructorSupplierForEnhancedConfiguration() throws IOException {
        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(ConstructorConfiguredApplication.class)), generatedFiles);
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.registerBean(ConfigurationCollaborator.class);
            new AnnotatedBeanDefinitionReader(applicationContext).register(ConstructorConfiguredApplication.class);

            new ApplicationContextAotGenerator().processAheadOfTime(applicationContext, generationContext);
            generationContext.writeGeneratedContent();
        }

        String source = generatedSource(generatedFiles);
        assertTrue(source.contains("ConstructorConfiguredApplication$$SpringCGLIB$$"));
        assertTrue(source.contains("initializeConfigurationClass"));
        assertTrue(source.contains("forConstructor"));
    }

    private static String generatedSource(InMemoryGeneratedFiles generatedFiles) throws IOException {
        StringBuilder source = new StringBuilder();
        for (Map.Entry<String, InputStreamSource> entry : generatedFiles.getGeneratedFiles(Kind.SOURCE).entrySet()) {
            source.append(generatedFiles.getGeneratedFileContent(Kind.SOURCE, entry.getKey()));
        }
        return source.toString();
    }

    @Configuration
    public static class ConstructorConfiguredApplication {

        private final ConfigurationCollaborator collaborator;

        public ConstructorConfiguredApplication(ConfigurationCollaborator collaborator) {
            this.collaborator = collaborator;
        }

        @Bean
        public String configuredValue() {
            return this.collaborator.value();
        }
    }

    public static class ConfigurationCollaborator {

        public String value() {
            return "configured";
        }
    }
}
