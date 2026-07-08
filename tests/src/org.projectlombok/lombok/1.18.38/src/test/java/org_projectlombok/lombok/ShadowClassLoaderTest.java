/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ShadowClassLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void serviceLoaderDiscoversLombokAnnotationProcessor() {
        List<String> processorClassNames = ServiceLoader.load(Processor.class).stream()
                .map(provider -> provider.get().getClass().getName())
                .toList();

        assertThat(processorClassNames)
                .contains("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
    }

    @Test
    void javaCompilerRunsLombokAnnotationProcessorFromClasspath() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();

        Path sourceDirectory = temporaryDirectory.resolve("src/example");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(classesDirectory);

        Path source = sourceDirectory.resolve("Person.java");
        Files.writeString(source, """
                package example;

                import lombok.Getter;

                public class Person {
                    @Getter
                    private final String name;

                    public Person(String name) {
                        this.name = name;
                    }
                }

                class UsesGeneratedGetter {
                    String read(Person person) {
                        return person.getName();
                    }
                }
                """, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(source);
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
                    "-d", classesDirectory.toString());

            Boolean successful = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();

            assertThat(successful)
                    .describedAs(formatDiagnostics(diagnostics))
                    .isTrue();
        }
        assertThat(classesDirectory.resolve("example/Person.class")).exists();
        assertThat(classesDirectory.resolve("example/UsesGeneratedGetter.class")).exists();
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            message.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return message.toString();
    }
}
