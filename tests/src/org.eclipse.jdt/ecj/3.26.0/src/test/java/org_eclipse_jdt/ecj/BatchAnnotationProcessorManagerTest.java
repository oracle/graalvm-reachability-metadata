/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BatchAnnotationProcessorManagerTest {
    private static final String COMMAND_LINE_PROCESSOR_MESSAGE = "command line processor invoked";
    private static final String SERVICE_PROCESSOR_MESSAGE = "service processor invoked";

    @TempDir
    Path temporaryDirectory;

    @Test
    void commandLineProcessorIsLoadedAndInstantiatedByName() throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        assertThat(compileFixture(diagnostics, List.of("-processor", CommandLineProcessor.class.getName()), null))
                .describedAs(formatDiagnostics(diagnostics))
                .isTrue();
        assertThat(diagnosticMessages(diagnostics)).contains(COMMAND_LINE_PROCESSOR_MESSAGE);
    }

    @Test
    void serviceDiscoveredProcessorPrintsItsLocation() throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter compilerOutput = new StringWriter();

        assertThat(compileFixture(diagnostics, List.of("-XprintProcessorInfo"), compilerOutput))
                .describedAs(formatDiagnostics(diagnostics))
                .isTrue();
        assertThat(diagnosticMessages(diagnostics)).contains(SERVICE_PROCESSOR_MESSAGE);
        assertThat(compilerOutput.toString())
                .contains("Discovered processor service")
                .contains(ServiceDiscoveredProcessor.class.getName())
                .contains(" in ");
    }

    private boolean compileFixture(DiagnosticCollector<JavaFileObject> diagnostics, List<String> extraOptions,
            StringWriter compilerOutput) throws IOException {
        JavaCompiler compiler = new EclipseCompiler();
        Path outputDirectory = this.temporaryDirectory.resolve("classes");
        Files.createDirectories(outputDirectory);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(compilerOutput, fileManager, diagnostics,
                    compilerOptions(extraOptions), null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(fixtureSourceFile())));
            return task.call();
        }
    }

    private Path fixtureSourceFile() throws IOException {
        Path packageDirectory = this.temporaryDirectory.resolve("src/org_eclipse_jdt/ecj/fixture");
        Files.createDirectories(packageDirectory);
        Path sourceFile = packageDirectory.resolve("ProcessorFixture.java");
        Files.writeString(sourceFile, """
                package org_eclipse_jdt.ecj.fixture;

                @ProcessorFixtureMarker
                public class ProcessorFixture {
                }

                @interface ProcessorFixtureMarker {
                }
                """, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private static List<String> compilerOptions(List<String> extraOptions) {
        String classPath = System.getProperty("java.class.path", "");
        List<String> options = new ArrayList<>(List.of("-proc:only", "-classpath", classPath,
                "-processorpath", classPath));
        options.addAll(extraOptions);
        return options;
    }

    private static List<String> diagnosticMessages(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(BatchAnnotationProcessorManagerTest::formatDiagnostic)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getKind() + " line " + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT);
    }

    public static final class CommandLineProcessor extends AbstractProcessor {
        public CommandLineProcessor() {
        }

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
            if (!roundEnv.processingOver()) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, COMMAND_LINE_PROCESSOR_MESSAGE);
            }
            return false;
        }
    }

    public static final class ServiceDiscoveredProcessor extends AbstractProcessor {
        public ServiceDiscoveredProcessor() {
        }

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
            if (!roundEnv.processingOver()) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, SERVICE_PROCESSOR_MESSAGE);
            }
            return false;
        }
    }
}
