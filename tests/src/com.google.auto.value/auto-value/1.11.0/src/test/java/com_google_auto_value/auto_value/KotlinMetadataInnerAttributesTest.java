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

public class KotlinMetadataInnerAttributesTest {
    @TempDir
    Path outputDirectory;

    @Test
    void readsKotlinConstructorParameterAttributesWhileGeneratingBuilder() throws Exception {
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
                        List.of(new KotlinAnnotatedSourceFile()));
                task.setProcessors(List.of(new AutoBuilderProcessor()));

                assertThat(task.call()).as(formatDiagnostics(diagnostics)).isTrue();
            }

            Path generatedBuilder = sourceOutput.resolve("sample/AutoBuilder_WidgetBuilder.java");
            assertThat(generatedBuilder).isRegularFile();
            assertThat(Files.readString(generatedBuilder))
                    .contains("class AutoBuilder_WidgetBuilder implements WidgetBuilder")
                    .contains("return new Widget(")
                    .contains("this.className,")
                    .contains("this.arguments);");
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

    private static final class KotlinAnnotatedSourceFile extends SimpleJavaFileObject {
        private KotlinAnnotatedSourceFile() {
            super(URI.create("string:///sample/WidgetBuilder.java"), Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return """
                    package sample;

                    import com.google.auto.value.AutoBuilder;
                    import java.util.Map;
                    import kotlin.Metadata;

                    @Metadata(
                        mv = {1, 9, 0},
                        k = 1,
                        xi = 48,
                        d1 = {"\\0000\\n\\002\\030\\002\\n\\002\\020\\000\\n\\000\\n"
                            + "\\002\\020\\016\\n\\002\\030\\002\\n\\000\\n\\002\\020$\\n"
                            + "\\002\\030\\002\\n\\002\\b\\006\\n\\002\\020\\013\\n"
                            + "\\002\\b\\002\\n\\002\\020\\b\\n\\002\\b\\002\\030\\000"
                            + "2\\0020\\001B%\\022\\n\\020\\002\\032\\0060\\003j\\002`"
                            + "\\004\\022\\022\\020\\005\\032\\016\\022\\004\\022"
                            + "\\0020\\003\\022\\004\\022\\0020\\0070\\006\\242"
                            + "\\006\\002\\020\\bJ\\023\\020\\r\\032\\0020\\0162"
                            + "\\b\\020\\017\\032\\004\\030\\0010\\001H\\226\\002J"
                            + "\\b\\020\\020\\032\\0020\\021H\\026J\\b\\020\\022"
                            + "\\032\\0020\\003H\\026R\\035\\020\\005\\032\\016"
                            + "\\022\\004\\022\\0020\\003\\022\\004\\022\\0020"
                            + "\\0070\\006\\242\\006\\b\\n\\000\\032\\004\\b\\t"
                            + "\\020\\nR\\025\\020\\002\\032\\0060\\003j\\002`\\004"
                            + "\\242\\006\\b\\n\\000\\032\\004\\b\\013\\020\\f"
                            + "\\250\\006\\023"},
                        d2 = {"Lkotlinx/metadata/KmAnnotation;", "", "className", "",
                            "Lkotlinx/metadata/ClassName;", "arguments", "",
                            "Lkotlinx/metadata/KmAnnotationArgument;",
                            "(Ljava/lang/String;Ljava/util/Map;)V", "getArguments",
                            "()Ljava/util/Map;", "getClassName", "()Ljava/lang/String;", "equals",
                            "", "other", "hashCode", "", "toString", "kotlinx-metadata"})
                    final class Widget {
                        Widget(String className, Map<String, Object> arguments) {}
                    }

                    @AutoBuilder(ofClass = Widget.class)
                    interface WidgetBuilder {
                        WidgetBuilder className(String className);
                        WidgetBuilder arguments(Map<String, Object> arguments);
                        Widget build();
                    }
                    """;
        }
    }
}
