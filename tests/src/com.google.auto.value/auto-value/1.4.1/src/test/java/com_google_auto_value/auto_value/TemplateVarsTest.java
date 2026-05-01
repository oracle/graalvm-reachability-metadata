/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auto.value.processor.AutoValueProcessor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
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

public class TemplateVarsTest {
    private static final String AUTO_VALUE_ANNOTATION = "com.google.auto.value.AutoValue";
    private static final String AUTOVALUE_TEMPLATE = "com/google/auto/value/processor/autovalue.vm";

    @TempDir
    Path temporaryDirectory;

    @Test
    void autoValueProcessorExpandsTemplateVariablesLoadedFromResourceUrlFallback() throws Exception {
        try {
            final Path autoValueJar = autoValueJar();
            try (BrokenTemplateResourceClassLoader classLoader = new BrokenTemplateResourceClassLoader(autoValueJar)) {
                final Processor processor = autoValueProcessorFrom(classLoader);

                final CompilationResult result = compileAutoValueType(processor, autoValueJar, this.temporaryDirectory);

                assertThat(result.isSuccessful()).as(result.diagnostics()).isTrue();
                assertThat(Files.readString(result.generatedSource(), StandardCharsets.UTF_8))
                        .contains("final class AutoValue_Animal extends Animal")
                        .contains("private final String name")
                        .contains("private final int numberOfLegs");
            }
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static Path autoValueJar() throws Exception {
        final CodeSource codeSource = AutoValueProcessor.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            final Path codeSourcePath = Path.of(codeSource.getLocation().toURI());
            if (Files.isRegularFile(codeSourcePath)) {
                return codeSourcePath;
            }
        }
        for (String classPathEntry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (classPathEntry.isBlank()) {
                continue;
            }
            final Path path = Path.of(classPathEntry);
            final String fileName = path.getFileName().toString();
            if (Files.isRegularFile(path) && fileName.startsWith("auto-value-") && fileName.endsWith(".jar")) {
                return path;
            }
        }
        throw new IllegalStateException("Could not locate the AutoValue processor jar on the classpath");
    }

    private static Processor autoValueProcessorFrom(ClassLoader classLoader) {
        final ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class, classLoader);
        for (Processor processor : processors) {
            if (processor.getSupportedAnnotationTypes().contains(AUTO_VALUE_ANNOTATION)) {
                return processor;
            }
        }
        throw new IllegalStateException("AutoValueProcessor service provider was not found");
    }

    private static CompilationResult compileAutoValueType(
            Processor processor,
            Path autoValueJar,
            Path temporaryDirectory
    ) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
        final Path generatedSourcesDirectory = Files.createDirectories(temporaryDirectory.resolve("generated-sources"));
        final JavaFileObject source = new StringSource("example.Animal", autoValueSource());
        final List<String> options = List.of(
                "-proc:only",
                "-classpath", autoValueJar.toString(),
                "-d", classesDirectory.toString(),
                "-s", generatedSourcesDirectory.toString()
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
            final Path generatedSource = generatedSourcesDirectory.resolve("example/AutoValue_Animal.java");
            return new CompilationResult(Boolean.TRUE.equals(successful), diagnostics, compilerOutput.toString(),
                    generatedSource);
        }
    }

    private static String autoValueSource() {
        return """
                package example;

                import com.google.auto.value.AutoValue;

                @AutoValue
                public abstract class Animal {
                    public abstract String name();

                    public abstract int numberOfLegs();
                }
                """;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class BrokenTemplateResourceClassLoader extends URLClassLoader {
        private BrokenTemplateResourceClassLoader(Path autoValueJar) throws IOException {
            super(new URL[] {autoValueJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (AUTOVALUE_TEMPLATE.equals(name)) {
                return new FailingInputStream();
            }
            return super.getResourceAsStream(name);
        }
    }

    private static final class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("Stream closed");
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
        private final Path generatedSource;

        private CompilationResult(
                boolean successful,
                DiagnosticCollector<JavaFileObject> diagnostics,
                String compilerOutput,
                Path generatedSource
        ) {
            this.successful = successful;
            this.diagnostics = diagnostics;
            this.compilerOutput = compilerOutput;
            this.generatedSource = generatedSource;
        }

        private boolean isSuccessful() {
            return this.successful;
        }

        private Path generatedSource() {
            return this.generatedSource;
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
