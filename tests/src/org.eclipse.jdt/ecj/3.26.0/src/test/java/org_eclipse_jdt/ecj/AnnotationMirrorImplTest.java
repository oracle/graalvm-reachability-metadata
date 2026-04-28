/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AnnotationMirrorImplTest {
    private static final String FIXTURE_NAME = "org_eclipse_jdt.ecj.AnnotationMirrorImplFixture";

    @TempDir
    Path temporaryDirectory;

    @Test
    void annotationProxyConvertsEnumAnnotationAndArrayMembers() throws IOException {
        JavaCompiler compiler = new EclipseCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        AnnotationMirrorImplProcessor processor = new AnnotationMirrorImplProcessor();
        Path outputDirectory = this.temporaryDirectory.resolve("classes");
        Files.createDirectories(outputDirectory);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, compilerOptions(),
                    null, List.of(source(FIXTURE_NAME, """
                            package org_eclipse_jdt.ecj;

                            @AnnotationMirrorImplTest.AnnotationMirrorImplSample(
                                    number = 42,
                                    numbers = 7,
                                    mode = AnnotationMirrorImplTest.AnnotationMirrorImplMode.SECOND,
                                    modes = AnnotationMirrorImplTest.AnnotationMirrorImplMode.SECOND,
                                    nested = @AnnotationMirrorImplTest.AnnotationMirrorImplNested("single"),
                                    nestedArray = @AnnotationMirrorImplTest.AnnotationMirrorImplNested("array"))
                            public class AnnotationMirrorImplFixture {
                            }
                            """)));
            task.setProcessors(List.of(processor));

            assertThat(task.call()).describedAs(formatDiagnostics(diagnostics)).isTrue();
        }

        assertThat(processor.getFailure()).isNull();
        assertThat(processor.hasProcessedFixture()).isTrue();
    }

    private static List<String> compilerOptions() {
        return List.of("-proc:only", "-classpath", System.getProperty("java.class.path", ""));
    }

    private static JavaFileObject source(String className, String source) {
        return new StringJavaFileObject(className, source);
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(AnnotationMirrorImplTest::formatDiagnostic)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getKind() + " line " + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT);
    }

    public enum AnnotationMirrorImplMode {
        FIRST,
        SECOND
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnnotationMirrorImplNested {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface AnnotationMirrorImplSample {
        int number();

        int[] numbers();

        AnnotationMirrorImplMode mode();

        AnnotationMirrorImplMode[] modes();

        AnnotationMirrorImplNested nested();

        AnnotationMirrorImplNested[] nestedArray();
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.source;
        }
    }

    private static final class AnnotationMirrorImplProcessor extends AbstractProcessor {
        private boolean processedFixture;
        private AssertionError failure;

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
            if (roundEnv.processingOver() || this.processedFixture) {
                return false;
            }
            TypeElement fixture = this.processingEnv.getElementUtils().getTypeElement(FIXTURE_NAME);
            if (fixture == null) {
                return false;
            }

            this.processedFixture = true;
            AnnotationMirrorImplSample sample = fixture.getAnnotation(AnnotationMirrorImplSample.class);
            try {
                require(sample != null, "Expected ECJ to expose the fixture annotation as a proxy");
                require(sample.number() == 42, "Expected primitive annotation member to be converted");
                require(sample.numbers().length == 1,
                        "Expected single shorthand array element to become one array entry");
                require(sample.numbers()[0] == 7, "Expected primitive array member to be converted");
                require(sample.mode() == AnnotationMirrorImplMode.SECOND,
                        "Expected enum member to resolve by field name");
                require(sample.modes().length == 1,
                        "Expected single enum shorthand array element to become one array entry");
                require(sample.modes()[0] == AnnotationMirrorImplMode.SECOND,
                        "Expected enum array member to resolve by field name");
                require("single".equals(sample.nested().value()), "Expected nested annotation member to be proxied");
                require(sample.nestedArray().length == 1, "Expected single nested annotation shorthand array element");
                require("array".equals(sample.nestedArray()[0].value()),
                        "Expected nested annotation array member to be proxied");
            } catch (AssertionError ex) {
                this.failure = ex;
            }
            return false;
        }

        boolean hasProcessedFixture() {
            return this.processedFixture;
        }

        AssertionError getFailure() {
            return this.failure;
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
