/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_configuration_processor;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.configurationprocessor.fieldvalues.javac.JavaCompilerFieldValuesParser;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableTreeTest {

    @TempDir
    Path outputDirectory;

    @Test
    void javaCompilerFieldValueParsingVisitsVariableTreeAccessors() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK compiler is required to exercise javac tree parsing").isNotNull();

        FieldValuesProcessor processor = new FieldValuesProcessor();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(this.outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:only"),
                    null, List.of(new SourceFile("sample.FieldValuesSample", """
                            package sample;

                            public class FieldValuesSample {

                                private static final int DEFAULT_PORT = 8080;

                                private int port = DEFAULT_PORT;

                                private boolean enabled;

                                private String name = "sample";

                            }
                            """)));
            task.setProcessors(List.of(processor));

            assertThat(task.call()).as(formatDiagnostics(diagnostics)).isTrue();
        }

        assertThat(processor.failure).isNull();
        assertThat(processor.fieldValues).containsEntry("port", 8080)
                .containsEntry("enabled", false)
                .containsEntry("name", "sample")
                .doesNotContainKey("DEFAULT_PORT");
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder result = new StringBuilder("Compilation diagnostics");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            result.append(System.lineSeparator()).append(diagnostic.getKind()).append(": ")
                    .append(diagnostic.getMessage(null));
        }
        return result.toString();
    }

    private static final class FieldValuesProcessor extends AbstractProcessor {

        private Map<String, Object> fieldValues = Map.of();

        private Throwable failure;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (Element rootElement : roundEnv.getRootElements()) {
                if (rootElement instanceof TypeElement typeElement
                        && typeElement.getQualifiedName().contentEquals("sample.FieldValuesSample")) {
                    collectFieldValues(typeElement);
                }
            }
            return false;
        }

        private void collectFieldValues(TypeElement typeElement) {
            try {
                this.fieldValues = new JavaCompilerFieldValuesParser(this.processingEnv).getFieldValues(typeElement);
            } catch (Throwable ex) {
                this.failure = ex;
            }
        }

    }

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String content;

        private SourceFile(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.content;
        }

    }

}
