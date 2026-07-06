/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ShadowClassLoaderTest {
    @Test
    void buildsImmutableValueUsingGeneratedBuilder() {
        Person person = Person.builder()
                .name("Ada")
                .age(36)
                .build();

        assertThat(person.getName()).isEqualTo("Ada");
        assertThat(person.getAge()).isEqualTo(36);
    }

    @Test
    void discoversLombokAnnotationProcessor() {
        Processor lombokProcessor = null;
        for (Processor processor : ServiceLoader.load(Processor.class)) {
            if (processor.getClass().getName().equals("lombok.launch.AnnotationProcessorHider$AnnotationProcessor")) {
                lombokProcessor = processor;
                break;
            }
        }

        assertThat(lombokProcessor).isNotNull();
        assertThat(lombokProcessor.getSupportedAnnotationTypes()).isNotEmpty();
    }

    @Test
    void processesLombokAnnotationThroughJavaCompiler() throws IOException {
        Processor lombokProcessor = null;
        for (Processor processor : ServiceLoader.load(Processor.class)) {
            if (processor.getClass().getName().equals("lombok.launch.AnnotationProcessorHider$AnnotationProcessor")) {
                lombokProcessor = processor;
                break;
            }
        }
        assertThat(lombokProcessor).isNotNull();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        Path sourceFile = Files.createTempFile("RuntimeProcessed", ".java");
        Files.writeString(sourceFile, """
                import lombok.Getter;

                @Getter
                class RuntimeProcessed {
                    private final String name = "Ada";
                }
                """);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            Iterable<? extends JavaFileObject> sourceFiles = fileManager.getJavaFileObjects(sourceFile.toFile());
            JavaCompiler.CompilationTask compilation = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:only", "-classpath", System.getProperty("java.class.path")),
                    null,
                    sourceFiles);
            compilation.setProcessors(List.of(lombokProcessor));

            assertThat(compilation.call()).as(diagnostics.getDiagnostics().toString()).isTrue();
        } finally {
            Files.deleteIfExists(sourceFile);
        }
    }

    @Value
    @Builder
    static class Person {
        String name;
        int age;
    }
}
