/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot.fixtures;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.javapoet.ClassName;

/// Regenerates the reader's serialized configuration through Spring's public AOT pipeline.
/// Run with the coordinate's test runtime classpath, passing the test project directory.
/// Only logging configuration resources are saved; no reachability metadata is written.
public class GenerateLogbackModel {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        String previous = System.getProperty(AbstractAotProcessor.AOT_PROCESSING);
        LogbackLoggingSystem logging = new LogbackLoggingSystem(GenerateLogbackModel.class.getClassLoader());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            System.setProperty(AbstractAotProcessor.AOT_PROCESSING, "true");
            logging.beforeInitialize();
            logging.initialize(new LoggingInitializationContext(new StandardEnvironment()),
                    project.resolve("fixtures/logback-aot.xml").toUri().toString(), null);
            context.registerBean("loggingSystem", LogbackLoggingSystem.class, () -> logging);
            InMemoryGeneratedFiles files = new InMemoryGeneratedFiles();
            DefaultGenerationContext generation = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get(GenerateLogbackModel.class)), files);
            new ApplicationContextAotGenerator().processAheadOfTime(context, generation);
            for (String name : new String[] { "META-INF/spring/logback-model",
                    "META-INF/spring/logback-pattern-rules" }) {
                Path output = project.resolve("src/test/resources").resolve(name);
                Files.createDirectories(output.getParent());
                try (InputStream input = files.getGeneratedFile(Kind.RESOURCE, name).getInputStream()) {
                    Files.write(output, input.readAllBytes());
                }
            }
        } finally {
            logging.cleanUp();
            logging.getShutdownHandler().run();
            if (previous == null) {
                System.clearProperty(AbstractAotProcessor.AOT_PROCESSING);
            } else {
                System.setProperty(AbstractAotProcessor.AOT_PROCESSING, previous);
            }
        }
    }
}
