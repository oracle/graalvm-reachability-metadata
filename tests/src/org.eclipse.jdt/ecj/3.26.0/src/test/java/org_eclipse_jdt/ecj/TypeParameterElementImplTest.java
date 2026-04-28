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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
import javax.lang.model.element.TypeParameterElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TypeParameterElementImplTest {
    private static final String FIXTURE_NAME = "org_eclipse_jdt.ecj.TypeParameterElementImplFixture";

    @TempDir
    Path temporaryDirectory;

    @Test
    void repeatedTypeParameterAnnotationReturnsEmptyArrayForJavacCompatibility() throws IOException {
        JavaCompiler compiler = new EclipseCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        TypeParameterElementImplProcessor processor = new TypeParameterElementImplProcessor();
        Path outputDirectory = this.temporaryDirectory.resolve("classes");
        Files.createDirectories(outputDirectory);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, compilerOptions(),
                    null, fileManager.getJavaFileObjectsFromPaths(List.of(fixtureSourceFile())));
            task.setProcessors(List.of(processor));

            assertThat(task.call()).describedAs(formatDiagnostics(diagnostics)).isTrue();
        }

        assertThat(processor.getFailure()).isNull();
        assertThat(processor.hasProcessedFixture()).isTrue();
    }

    private Path fixtureSourceFile() throws IOException {
        Path packageDirectory = this.temporaryDirectory.resolve("src/org_eclipse_jdt/ecj");
        Files.createDirectories(packageDirectory);
        Path sourceFile = packageDirectory.resolve("TypeParameterElementImplFixture.java");
        Files.writeString(sourceFile, """
                package org_eclipse_jdt.ecj;

                public class TypeParameterElementImplFixture<
                        @TypeParameterElementImplTest.TypeParameterElementImplMarker("first")
                        @TypeParameterElementImplTest.TypeParameterElementImplMarker("second") T> {
                }
                """, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private static List<String> compilerOptions() {
        return List.of("-proc:only", "-source", "8", "-target", "8", "-classpath",
                System.getProperty("java.class.path", ""));
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(TypeParameterElementImplTest::formatDiagnostic)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getKind() + " line " + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_PARAMETER)
    @Repeatable(TypeParameterElementImplMarkers.class)
    public @interface TypeParameterElementImplMarker {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_PARAMETER)
    public @interface TypeParameterElementImplMarkers {
        TypeParameterElementImplMarker[] value();
    }

    private static final class TypeParameterElementImplProcessor extends AbstractProcessor {
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
                TypeParameterElement typeParameter = fixture.getTypeParameters().get(0);
                TypeParameterElementImplMarker[] markers =
                        typeParameter.getAnnotationsByType(TypeParameterElementImplMarker.class);
                require(markers != null, "Expected ECJ to return an annotation array instead of null");
                require(markers.length == 0, "Expected duplicate type parameter annotations to be hidden");
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
