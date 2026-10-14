/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auto.value.processor.AutoBuilderProcessor;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KotlinMetadataTest {
    @TempDir
    Path outputDirectory;

    @Test
    void generatesAutoBuilderWithKotlinMetadataApiAvailable() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertThat(compiler).as("A JDK compiler is required to exercise AutoBuilder").isNotNull();

            Path classOutput = Files.createDirectories(this.outputDirectory.resolve("classes"));
            Path sourceOutput = Files.createDirectories(this.outputDirectory.resolve("generated"));
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                    diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
                fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(sourceOutput));
                JavaCompiler.CompilationTask task = compiler.getTask(
                        null,
                        fileManager,
                        diagnostics,
                        List.of("-proc:only", "-classpath", System.getProperty("java.class.path")),
                        null,
                        List.of(new SourceFile()));
                task.setProcessors(List.of(new AutoBuilderProcessor()));

                assertThat(task.call()).as(formatDiagnostics(diagnostics)).isTrue();
            }

            Path generatedBuilder = sourceOutput.resolve("sample/AutoBuilder_WidgetBuilder.java");
            assertThat(generatedBuilder).isRegularFile();
            assertThat(Files.readString(generatedBuilder))
                    .contains("class AutoBuilder_WidgetBuilder implements WidgetBuilder")
                    .contains("return new Widget(");
        });
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder result = new StringBuilder("Compilation diagnostics");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            result.append(System.lineSeparator())
                    .append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT));
        }
        return result.toString();
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private SourceFile() {
            super(URI.create("string:///sample/WidgetBuilder.java"), Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return """
                    package sample;

                    import com.google.auto.value.AutoBuilder;

                    final class Widget {
                        private final String name;
                        private final int count;

                        Widget(String name, int count) {
                            this.name = name;
                            this.count = count;
                        }

                        String name() {
                            return name;
                        }

                        int count() {
                            return count;
                        }
                    }

                    @AutoBuilder(ofClass = Widget.class)
                    interface WidgetBuilder {
                        WidgetBuilder name(String name);
                        WidgetBuilder count(int count);
                        Widget build();
                    }
                    """;
        }
    }
}
