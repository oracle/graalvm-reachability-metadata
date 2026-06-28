/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ShadowClassLoaderTest {

    private static final String LOMBOK_ANNOTATION_PROCESSOR =
            "lombok.launch.AnnotationProcessorHider$AnnotationProcessor";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void serviceLoaderBootstrapsLombokAnnotationProcessor() {
        try {
            final Processor processor = findLombokAnnotationProcessor();

            final Set<String> supportedAnnotationTypes = processor.getSupportedAnnotationTypes();
            final SourceVersion supportedSourceVersion = processor.getSupportedSourceVersion();

            assertThat(supportedAnnotationTypes).contains("*");
            assertThat(supportedSourceVersion).isNotNull();
        } catch (ServiceConfigurationError error) {
            if (!isUnsupportedNativeClassLoading(error)) {
                throw error;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void javacCanInvokeDisabledLombokAnnotationProcessor() throws IOException {
        final String previousDisableValue = System.getProperty("lombok.disable");
        System.setProperty("lombok.disable", "true");
        try {
            final Path sourceDirectory = Files.createDirectories(
                    this.temporaryDirectory.resolve("src"));
            final Path classesDirectory = Files.createDirectories(
                    this.temporaryDirectory.resolve("classes"));
            final Path sourceFile = sourceDirectory.resolve("Example.java");
            Files.writeString(sourceFile, "public class Example { }\n", StandardCharsets.UTF_8);

            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertThat(compiler).isNotNull();
            final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                    diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                final Iterable<? extends JavaFileObject> compilationUnits =
                        fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));
                final List<String> options = List.of(
                        "-d", classesDirectory.toString(),
                        "-classpath", System.getProperty("java.class.path"),
                        "-processorpath", System.getProperty("java.class.path"),
                        "-processor", LOMBOK_ANNOTATION_PROCESSOR,
                        "-proc:only");
                final StringWriter compilerOutput = new StringWriter();
                final JavaCompiler.CompilationTask task = compiler.getTask(
                        compilerOutput, fileManager, diagnostics, options, null, compilationUnits);

                assertThat(task.call())
                        .withFailMessage(() -> formatCompilerFailure(compilerOutput, diagnostics))
                        .isTrue();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreLombokDisableProperty(previousDisableValue);
        }
    }

    private static Processor findLombokAnnotationProcessor() {
        final ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class);
        for (Processor processor : processors) {
            if (LOMBOK_ANNOTATION_PROCESSOR.equals(processor.getClass().getName())) {
                return processor;
            }
        }
        throw new AssertionError("Lombok annotation processor service was not discovered");
    }

    private static boolean isUnsupportedNativeClassLoading(ServiceConfigurationError error) {
        final Throwable cause = error.getCause();
        return cause instanceof Error causeError
                && NativeImageSupport.isUnsupportedFeatureError(causeError);
    }

    private static void restoreLombokDisableProperty(String previousDisableValue) {
        if (previousDisableValue == null) {
            System.clearProperty("lombok.disable");
        } else {
            System.setProperty("lombok.disable", previousDisableValue);
        }
    }

    private static String formatCompilerFailure(
            StringWriter compilerOutput, DiagnosticCollector<JavaFileObject> diagnostics) {
        final StringBuilder message = new StringBuilder(compilerOutput.toString());
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (message.length() > 0) {
                message.append(System.lineSeparator());
            }
            message.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT));
        }
        return message.toString();
    }
}
