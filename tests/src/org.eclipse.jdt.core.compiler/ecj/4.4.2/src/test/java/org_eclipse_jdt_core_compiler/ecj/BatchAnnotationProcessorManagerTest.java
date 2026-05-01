/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt_core_compiler.ecj;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BatchAnnotationProcessorManagerTest {
    private static final String COMMAND_LINE_MARKER = "command-line-processor.marker";
    private static final String COMMAND_LINE_PROCESSOR =
            "org_eclipse_jdt_core_compiler.ecj.BatchAnnotationProcessorManagerTest$CommandLineProcessor";
    private static final String SERVICE_LOADER_MARKER = "service-loader-processor.marker";
    private static final String SERVICE_DISCOVERED_PROCESSOR =
            "org_eclipse_jdt_core_compiler.ecj.BatchAnnotationProcessorManagerTest$ServiceDiscoveredProcessor";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsCommandLineProcessorByName() throws IOException {
        try {
            final Path processorPath = Files.createDirectories(this.temporaryDirectory.resolve("command-processor-path"));
            final CompilationResult result = compileWithProcessorPath(processorPath,
                    List.of("-processor", COMMAND_LINE_PROCESSOR));

            assertThat(result.isSuccessful()).isTrue();
            assertThat(Files.readString(result.getClassesDirectory().resolve(COMMAND_LINE_MARKER), UTF_8))
                    .isEqualTo(COMMAND_LINE_PROCESSOR);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @Test
    void reportsServiceLoadedProcessorLocation() throws IOException {
        try {
            final Path processorPath = writeServiceProviderConfiguration(
                    SERVICE_DISCOVERED_PROCESSOR,
                    this.temporaryDirectory.resolve("service-processor-path"));
            final CompilationResult result = compileWithProcessorPath(processorPath, List.of("-XprintProcessorInfo"));

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getCompilerOutput()).contains("Discovered processor service");
            assertThat(result.getCompilerOutput()).contains(SERVICE_DISCOVERED_PROCESSOR);
            assertThat(Files.readString(result.getClassesDirectory().resolve(SERVICE_LOADER_MARKER), UTF_8))
                    .isEqualTo(SERVICE_DISCOVERED_PROCESSOR);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static Path writeServiceProviderConfiguration(String processorClassName, Path processorPath)
            throws IOException {
        final Path serviceDirectory = Files.createDirectories(processorPath.resolve("META-INF/services"));
        Files.writeString(serviceDirectory.resolve(Processor.class.getName()), processorClassName + System.lineSeparator(),
                UTF_8);
        return processorPath;
    }

    private static CompilationResult compileWithProcessorPath(Path processorPath, List<String> processorOptions)
            throws IOException {
        final Path sourceDirectory = Files.createDirectories(processorPath.getParent().resolve("sources"));
        final Path classesDirectory = Files.createDirectories(processorPath.getParent().resolve("classes"));
        final List<Path> sourceFiles = writeSourceFiles(sourceDirectory);
        final JavaCompiler compiler = new EclipseCompiler();
        final StringWriter compilerOutput = new StringWriter();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(classesDirectory.toFile()));
            fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, processorPathEntries(processorPath));
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    sourceFiles.stream().map(Path::toFile).toList());
            final List<String> options = new ArrayList<>(List.of("-proc:only", "-source", "1.6", "-target", "1.6"));
            options.addAll(processorOptions);
            final JavaCompiler.CompilationTask task = compiler.getTask(compilerOutput, fileManager, null, options, null,
                    compilationUnits);
            final Boolean success = callWithRecognizedClassFileVersion(task);
            return new CompilationResult(Boolean.TRUE.equals(success), compilerOutput.toString(), classesDirectory);
        }
    }

    private static List<File> processorPathEntries(Path processorPath) throws IOException {
        final List<File> entries = new ArrayList<>();
        entries.add(processorPath.toFile());
        final CodeSource codeSource = BatchAnnotationProcessorManagerTest.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            try {
                entries.add(Path.of(codeSource.getLocation().toURI()).toFile());
            } catch (URISyntaxException exception) {
                throw new IOException("Cannot resolve test class location", exception);
            }
        }
        return entries;
    }

    private static List<Path> writeSourceFiles(Path sourceDirectory) throws IOException {
        final Path javaLangDirectory = Files.createDirectories(sourceDirectory.resolve("java/lang"));
        final Path objectSource = javaLangDirectory.resolve("Object.java");
        final Path classSource = javaLangDirectory.resolve("Class.java");
        final Path stringSource = javaLangDirectory.resolve("String.java");
        final Path testSource = sourceDirectory.resolve("ManagedSource.java");
        Files.writeString(objectSource, javaLangObjectSource(), UTF_8);
        Files.writeString(classSource, javaLangClassSource(), UTF_8);
        Files.writeString(stringSource, javaLangStringSource(), UTF_8);
        Files.writeString(testSource, source(), UTF_8);
        return List.of(objectSource, classSource, stringSource, testSource);
    }

    private static Boolean callWithRecognizedClassFileVersion(JavaCompiler.CompilationTask task) {
        final String previousVersion = System.getProperty("java.class.version");
        System.setProperty("java.class.version", "52.0");
        try {
            return task.call();
        } finally {
            if (previousVersion == null) {
                System.clearProperty("java.class.version");
            } else {
                System.setProperty("java.class.version", previousVersion);
            }
        }
    }

    private static String javaLangObjectSource() {
        return """
                package java.lang;

                public class Object {
                    public Object() {
                    }
                }
                """;
    }

    private static String javaLangClassSource() {
        return """
                package java.lang;

                public final class Class<T> {
                }
                """;
    }

    private static String javaLangStringSource() {
        return """
                package java.lang;

                public final class String {
                }
                """;
    }

    private static String source() {
        return """
                package org_eclipse_jdt_core_compiler.ecj.coverage;

                public final class ManagedSource {
                }
                """;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public abstract static class MarkerProcessor extends AbstractProcessor {
        private final String markerName;
        private boolean generated;

        protected MarkerProcessor(String markerName) {
            this.markerName = markerName;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_6;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (!this.generated) {
                writeMarker();
                this.generated = true;
            }
            return false;
        }

        private void writeMarker() {
            try {
                final FileObject marker = this.processingEnv.getFiler()
                        .createResource(StandardLocation.CLASS_OUTPUT, "", this.markerName);
                try (Writer writer = marker.openWriter()) {
                    writer.write(getClass().getName());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    public static class CommandLineProcessor extends MarkerProcessor {
        public CommandLineProcessor() {
            super("command-line-processor.marker");
        }
    }

    public static class ServiceDiscoveredProcessor extends MarkerProcessor {
        public ServiceDiscoveredProcessor() {
            super("service-loader-processor.marker");
        }
    }

    private static final class CompilationResult {
        private final boolean successful;
        private final String compilerOutput;
        private final Path classesDirectory;

        private CompilationResult(boolean successful, String compilerOutput, Path classesDirectory) {
            this.successful = successful;
            this.compilerOutput = compilerOutput;
            this.classesDirectory = classesDirectory;
        }

        private boolean isSuccessful() {
            return this.successful;
        }

        private String getCompilerOutput() {
            return this.compilerOutput;
        }

        private Path getClassesDirectory() {
            return this.classesDirectory;
        }
    }
}
