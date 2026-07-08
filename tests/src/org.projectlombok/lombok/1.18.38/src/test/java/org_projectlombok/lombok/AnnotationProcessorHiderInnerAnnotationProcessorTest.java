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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AnnotationProcessorHiderInnerAnnotationProcessorTest {
    private static final String PROCESSOR_NAME =
            "lombok.launch.AnnotationProcessorHider$AnnotationProcessor";

    @Test
    void annotationProcessorIsLoadedThroughStandardServiceLoader() {
        Set<String> processorClassNames = ServiceLoader.load(Processor.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(processor -> processor.getClass().getName())
                .collect(Collectors.toSet());

        assertThat(processorClassNames).contains(PROCESSOR_NAME);
    }

    @Test
    void javacInitializesAnnotationProcessor(@TempDir Path tempDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();

        Path sourceFile = tempDir.resolve("LombokProcessorBootstrap.java");
        Files.writeString(
                sourceFile, "public class LombokProcessorBootstrap {}", StandardCharsets.UTF_8);
        Path outputDirectory = Files.createDirectory(tempDir.resolve("classes"));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        String previousDisableValue = System.setProperty("lombok.disable", "true");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(
                    StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));

            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", PROCESSOR_NAME,
                    "-proc:only");
            Boolean compiled = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits).call();

            assertThat(compiled)
                    .as("diagnostics: %s", formatDiagnostics(diagnostics))
                    .isTrue();
        } finally {
            restoreSystemProperty("lombok.disable", previousDisableValue);
        }
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(AnnotationProcessorHiderInnerAnnotationProcessorTest::formatDiagnostic)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getKind() + " line " + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(null);
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
