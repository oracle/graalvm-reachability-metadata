/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ErrorTypeElementTest {
    private static final String FIXTURE_NAME = "org_eclipse_jdt.ecj.ErrorTypeElementFixture";

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFieldTypeElementReturnsEmptyAnnotationArray() throws IOException {
        JavaCompiler compiler = new EclipseCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        ErrorTypeElementProcessor processor = new ErrorTypeElementProcessor();
        Path outputDirectory = this.temporaryDirectory.resolve("classes");
        Files.createDirectories(outputDirectory);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, compilerOptions(),
                    null, fileManager.getJavaFileObjectsFromPaths(fixtureSourceFiles()));
            task.setProcessors(List.of(processor));
            task.call();
        }

        assertThat(processor.getFailure()).isNull();
        assertThat(processor.hasProcessedFixture())
                .describedAs(formatDiagnostics(diagnostics))
                .isTrue();
    }

    private List<Path> fixtureSourceFiles() throws IOException {
        return List.of(sourceFile("ErrorTypeElementFixture.java", """
                package org_eclipse_jdt.ecj;

                public class ErrorTypeElementFixture {
                    private MissingDependency missingDependency;
                }
                """));
    }

    private Path sourceFile(String fileName, String source) throws IOException {
        Path packageDirectory = this.temporaryDirectory.resolve("src/org_eclipse_jdt/ecj");
        Files.createDirectories(packageDirectory);
        Path sourceFile = packageDirectory.resolve(fileName);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private static List<String> compilerOptions() {
        return List.of("-proc:only", "-classpath", System.getProperty("java.class.path", ""));
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(ErrorTypeElementTest::formatDiagnostic)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getKind() + " line " + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ErrorTypeElementProbe {
    }

    private static final class ErrorTypeElementProcessor extends AbstractProcessor {
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
            try {
                TypeMirror missingType = ElementFilter.fieldsIn(fixture.getEnclosedElements()).get(0).asType();
                require(missingType.getKind() == TypeKind.ERROR, "Expected unresolved field type to be an error type");

                Element missingElement = ((DeclaredType) missingType).asElement();
                ErrorTypeElementProbe[] probeAnnotations =
                        missingElement.getAnnotationsByType(ErrorTypeElementProbe.class);
                require(probeAnnotations.length == 0, "Expected missing type element to have no probe annotations");
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
