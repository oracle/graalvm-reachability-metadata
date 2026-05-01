/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google$.auto.common.$MoreTypes;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AutovalueShadedComGoogleInnerAutoCommonInnerMoreTypesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void equivalenceComparesIntersectionTypeBounds() throws Exception {
        try {
            final IntersectionTypeEquivalenceProcessor processor = new IntersectionTypeEquivalenceProcessor();
            final CompilationResult result = compileIntersectionTypeSource(processor, this.temporaryDirectory);

            assertThat(result.isSuccessful()).as(result.diagnostics()).isTrue();
            assertThat(processor.isProcessed()).isTrue();
            assertThat(processor.firstUpperBoundKind()).isEqualTo("INTERSECTION");
            assertThat(processor.secondUpperBoundKind()).isEqualTo("INTERSECTION");
            assertThat(processor.intersectionBoundsEquivalent()).isTrue();
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static CompilationResult compileIntersectionTypeSource(
            IntersectionTypeEquivalenceProcessor processor,
            Path temporaryDirectory
    ) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
        final JavaFileObject source = new StringSource("example.IntersectionHolder", intersectionTypeSource());
        final List<String> options = List.of(
                "-proc:only",
                "-d", classesDirectory.toString()
        );

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null,
                StandardCharsets.UTF_8)) {
            final StringWriter compilerOutput = new StringWriter();
            final JavaCompiler.CompilationTask task = compiler.getTask(
                    compilerOutput,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(source)
            );
            task.setProcessors(List.of(processor));
            final Boolean successful = task.call();
            return new CompilationResult(Boolean.TRUE.equals(successful), diagnostics, compilerOutput.toString());
        }
    }

    private static String intersectionTypeSource() {
        return """
                package example;

                @interface Probe {
                }

                @Probe
                final class IntersectionHolder<
                        T extends Runnable & AutoCloseable,
                        U extends Runnable & AutoCloseable> {
                }
                """;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class IntersectionTypeEquivalenceProcessor extends AbstractProcessor {
        private boolean processed;
        private String firstUpperBoundKind;
        private String secondUpperBoundKind;
        private boolean intersectionBoundsEquivalent;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (roundEnvironment.processingOver() || this.processed) {
                return false;
            }
            final TypeElement holder = this.processingEnv.getElementUtils()
                    .getTypeElement("example.IntersectionHolder");
            assertThat(holder).isNotNull();
            final List<? extends TypeParameterElement> typeParameters = holder.getTypeParameters();
            assertThat(typeParameters).hasSize(2);
            final TypeMirror firstUpperBound = ((TypeVariable) typeParameters.get(0).asType()).getUpperBound();
            final TypeMirror secondUpperBound = ((TypeVariable) typeParameters.get(1).asType()).getUpperBound();

            this.firstUpperBoundKind = firstUpperBound.getKind().name();
            this.secondUpperBoundKind = secondUpperBound.getKind().name();
            this.intersectionBoundsEquivalent = $MoreTypes.equivalence().equivalent(firstUpperBound, secondUpperBound);
            this.processed = true;
            return false;
        }

        private boolean isProcessed() {
            return this.processed;
        }

        private String firstUpperBoundKind() {
            return this.firstUpperBoundKind;
        }

        private String secondUpperBoundKind() {
            return this.secondUpperBoundKind;
        }

        private boolean intersectionBoundsEquivalent() {
            return this.intersectionBoundsEquivalent;
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String source;

        private StringSource(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.source;
        }
    }

    private static final class CompilationResult {
        private final boolean successful;
        private final DiagnosticCollector<JavaFileObject> diagnostics;
        private final String compilerOutput;

        private CompilationResult(
                boolean successful,
                DiagnosticCollector<JavaFileObject> diagnostics,
                String compilerOutput
        ) {
            this.successful = successful;
            this.diagnostics = diagnostics;
            this.compilerOutput = compilerOutput;
        }

        private boolean isSuccessful() {
            return this.successful;
        }

        private String diagnostics() {
            final StringBuilder builder = new StringBuilder(this.compilerOutput);
            for (Diagnostic<? extends JavaFileObject> diagnostic : this.diagnostics.getDiagnostics()) {
                builder.append(System.lineSeparator()).append(diagnostic);
            }
            return builder.toString();
        }
    }
}
