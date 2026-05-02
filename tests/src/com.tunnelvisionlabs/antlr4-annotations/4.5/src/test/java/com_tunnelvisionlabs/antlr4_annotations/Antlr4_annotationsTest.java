/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4_annotations;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.NullUsageProcessor;
import org.antlr.v4.runtime.misc.Nullable;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Antlr4_annotationsTest {

    private static final String NOT_NULL_SOURCE = """
            package org.antlr.v4.runtime.misc;

            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Documented
            @Retention(RetentionPolicy.CLASS)
            @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
            public @interface NotNull {
            }
            """;

    private static final String NULLABLE_SOURCE = """
            package org.antlr.v4.runtime.misc;

            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Documented
            @Retention(RetentionPolicy.CLASS)
            @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
            public @interface Nullable {
            }
            """;

    @TempDir
    Path outputDirectory;

    @Test
    void annotationsCanBeUsedOnSupportedProgramElements() {
        AnnotatedApiSurface sample = new AnnotatedApiSurface("antlr");

        assertThat(sample.field).isEqualTo("antlr");
        assertThat(sample.findValue("suffix")).isEqualTo("antlr-suffix");
        assertThat(sample.findValue("")).isNull();
    }

    @Test
    void processorAdvertisesAnnotationNamesAndSupportedSourceVersion() {
        NullUsageProcessor processor = new NullUsageProcessor();

        assertThat(NullUsageProcessor.NotNullClassName).isEqualTo("org.antlr.v4.runtime.misc.NotNull");
        assertThat(NullUsageProcessor.NullableClassName).isEqualTo("org.antlr.v4.runtime.misc.Nullable");
        assertThat(processor.getSupportedAnnotationTypes())
                .containsExactlyInAnyOrder(NullUsageProcessor.NotNullClassName, NullUsageProcessor.NullableClassName);
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(expectedSupportedSourceVersion());
    }

    @Test
    void serviceLoaderDiscoversAnnotationProcessor() {
        ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class);

        assertThat(processors).anySatisfy(processor -> assertThat(processor).isInstanceOf(NullUsageProcessor.class));
    }

    @Test
    void processorAcceptsConsistentNullabilityContracts() throws IOException {
        try {
            CompilationResult result = compileWithNullUsageProcessor("sample.ValidSample", """
                    package sample;

                    import org.antlr.v4.runtime.misc.NotNull;
                    import org.antlr.v4.runtime.misc.Nullable;

                    interface ValidParent {
                        @Nullable String find(@NotNull String query);

                        void receive(@Nullable String value);
                    }

                    class ValidSample implements ValidParent {
                        @NotNull String field = "antlr";

                        @Override
                        @Nullable
                        public String find(@NotNull String query) {
                            @Nullable String result = query.isEmpty() ? null : query;
                            return result;
                        }

                        @Override
                        public void receive(@Nullable String value) {
                        }

                        @NotNull
                        String name() {
                            return this.field;
                        }
                    }
                    """);

            assertThat(result.success()).as(result.formattedDiagnostics()).isTrue();
            assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
        } catch (Error error) {
            verifyUnsupportedDynamicCompilationError(error);
        }
    }

    @Test
    void processorReportsInvalidNullabilityContracts() throws IOException {
        try {
            CompilationResult result = compileWithNullUsageProcessor("sample.InvalidSample", """
                    package sample;

                    import org.antlr.v4.runtime.misc.NotNull;
                    import org.antlr.v4.runtime.misc.Nullable;

                    interface InvalidParent {
                        @NotNull String value();

                        void consume(@Nullable String value);

                        String plain();
                    }

                    class InvalidSample implements InvalidParent {
                        @NotNull @Nullable String both;

                        @Nullable int primitiveField;

                        @NotNull int primitiveMethod() {
                            return 1;
                        }

                        @Nullable void voidMethod() {
                        }

                        @Override
                        @Nullable
                        public String value() {
                            return null;
                        }

                        @Override
                        public void consume(@NotNull String value) {
                        }

                        @Override
                        @Nullable
                        public String plain() {
                            return null;
                        }
                    }
                    """);

            assertThat(result.success()).as(result.formattedDiagnostics()).isFalse();
            assertThat(result.formattedDiagnostics())
                    .contains("field cannot be annotated with both NotNull and Nullable")
                    .contains("field with a primitive type cannot be annotated with Nullable")
                    .contains("method with a primitive type should not be annotated with NotNull")
                    .contains("void method cannot be annotated with Nullable")
                    .contains("method annotated with Nullable cannot override or implement a method annotated "
                            + "with NotNull")
                    .contains("parameter value annotated with NotNull cannot override or implement a parameter "
                            + "annotated with Nullable")
                    .contains("method annotated with Nullable overrides a method that is not annotated");
        } catch (Error error) {
            verifyUnsupportedDynamicCompilationError(error);
        }
    }

    private static void verifyUnsupportedDynamicCompilationError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private CompilationResult compileWithNullUsageProcessor(String className, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK compiler is required to exercise the annotation processor").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(this.outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:only"),
                    null, List.of(new SourceFile("org.antlr.v4.runtime.misc.NotNull", NOT_NULL_SOURCE),
                            new SourceFile("org.antlr.v4.runtime.misc.Nullable", NULLABLE_SOURCE),
                            new SourceFile(className, source)));
            task.setProcessors(List.of(new NullUsageProcessor()));

            boolean success = task.call();
            return new CompilationResult(success, diagnostics.getDiagnostics());
        }
    }

    private static SourceVersion expectedSupportedSourceVersion() {
        SourceVersion latest = SourceVersion.latestSupported();
        if (latest.ordinal() <= SourceVersion.RELEASE_6.ordinal()) {
            return SourceVersion.RELEASE_6;
        }
        if (latest.ordinal() <= SourceVersion.RELEASE_8.ordinal()) {
            return latest;
        }

        return SourceVersion.RELEASE_8;
    }

    private static final class CompilationResult {

        private final boolean success;

        private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

        private CompilationResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
            this.success = success;
            this.diagnostics = List.copyOf(diagnostics);
        }

        private boolean success() {
            return this.success;
        }

        private List<Diagnostic<? extends JavaFileObject>> diagnostics() {
            return this.diagnostics;
        }

        private String formattedDiagnostics() {
            StringBuilder result = new StringBuilder("Compilation diagnostics");
            for (Diagnostic<? extends JavaFileObject> diagnostic : this.diagnostics) {
                result.append(System.lineSeparator()).append(diagnostic.getKind()).append(": ")
                        .append(diagnostic.getMessage(Locale.ROOT));
            }

            return result.toString();
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

    private static final class AnnotatedApiSurface {

        @NotNull
        private final String field;

        private AnnotatedApiSurface(@NotNull String field) {
            this.field = field;
        }

        @Nullable
        private String findValue(@NotNull String suffix) {
            @Nullable String local = suffix.isEmpty() ? null : this.field + "-" + suffix;
            return local;
        }

    }

}
