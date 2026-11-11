/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.javapoet.ClassName;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImportRuntimeHintsTest {
    @Test
    void invokesImportedRegistrarDuringAheadOfTimeProcessing() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(HintConfiguration.class);
            DefaultGenerationContext generation = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get("org_springframework.spring_context", "CatalogApplication")),
                    new InMemoryGeneratedFiles());
            new ApplicationContextAotGenerator().processAheadOfTime(context, generation);
            assertTrue(generation.getRuntimeHints().resources().resourceBundleHints()
                    .anyMatch(hint -> hint.getBaseName().equals("org_springframework.spring_context.messages")));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportRuntimeHints(CatalogHints.class)
    public static class HintConfiguration {
    }

    public static class CatalogHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerResourceBundle("org_springframework.spring_context.messages");
        }
    }
}
