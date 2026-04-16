/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$generator$;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class $AnnotationMirrors$GetTypeAnnotationsTest {
    @Test
    void compilesTypeUseAnnotatedImmutableSource(@TempDir Path tempDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "A system Java compiler is required for this test");

        Path sourceDirectory = tempDir.resolve("src");
        Path generatedSourcesDirectory = tempDir.resolve("generated-sources");
        Path classesDirectory = tempDir.resolve("classes");
        Files.createDirectories(sourceDirectory.resolve("sample"));
        Files.createDirectories(generatedSourcesDirectory);
        Files.createDirectories(classesDirectory);

        Path annotationSource = sourceDirectory.resolve("sample/TypeUseMarker.java");
        Files.writeString(annotationSource, annotationSourceText(), StandardCharsets.UTF_8);

        Path immutableSource = sourceDirectory.resolve("sample/GetTypeAnnotationsValue.java");
        Files.writeString(immutableSource, immutableSourceText(), StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(
                    annotationSource.toFile(),
                    immutableSource.toFile());
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", "org.immutables.processor.ProxyProcessor",
                    "-d", classesDirectory.toString(),
                    "-s", generatedSourcesDirectory.toString());

            Boolean compilationSuccessful = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits)
                    .call();

            assertThat(compilationSuccessful)
                    .withFailMessage("Compilation failed:%n%s", formatDiagnostics(diagnostics.getDiagnostics()))
                    .isTrue();
        }

        Path generatedImmutableSource = generatedSourcesDirectory.resolve("sample/ImmutableGetTypeAnnotationsValue.java");
        assertThat(generatedImmutableSource).exists();
    }

    private static String annotationSourceText() {
        return """
                package sample;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE_USE)
                @Retention(RetentionPolicy.CLASS)
                public @interface TypeUseMarker {
                }
                """;
    }

    private static String immutableSourceText() {
        return """
                package sample;

                import org.immutables.value.Value;

                @Value.Immutable
                interface GetTypeAnnotationsValue {
                    @TypeUseMarker
                    String value();
                }
                """;
    }

    private static String formatDiagnostics(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        return diagnostics.stream()
                .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT))
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
