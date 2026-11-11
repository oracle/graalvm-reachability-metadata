/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.javapoet.ClassName;

/// Recreates the checked-in Logback resources through Boot's build-time AOT API.
public class LogbackModelFixture {
    public static void main(String[] args) throws Exception {
        Path resources = Path.of(args[0]);
        System.setProperty(AbstractAotProcessor.AOT_PROCESSING, "true");
        LogbackLoggingSystem system = new LogbackLoggingSystem(LogbackModelFixture.class.getClassLoader());
        try {
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(new StandardEnvironment()),
                    resources.resolve("aot-logging.xml").toUri().toString(), null);
            BeanFactoryInitializationAotContribution contribution =
                    system.processAheadOfTime(new DefaultListableBeanFactory());
            InMemoryGeneratedFiles files = new InMemoryGeneratedFiles();
            DefaultGenerationContext generation = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get("consumer", "LoggingApplication")), files);
            contribution.applyTo(generation, null);
            for (var entry : files.getGeneratedFiles(Kind.RESOURCE).entrySet()) {
                Path destination = resources.resolve(entry.getKey());
                Files.createDirectories(destination.getParent());
                try (var input = entry.getValue().getInputStream()) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            system.getShutdownHandler().run();
            system.cleanUp();
        }
    }
}
