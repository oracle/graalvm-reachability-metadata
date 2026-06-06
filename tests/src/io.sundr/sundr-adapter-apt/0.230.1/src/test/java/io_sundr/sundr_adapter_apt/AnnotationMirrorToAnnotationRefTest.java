/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_adapter_apt;

import io.sundr.adapter.apt.AnnotationMirrorToAnnotationRef;
import io.sundr.adapter.apt.AptContext;
import io.sundr.adapter.apt.TypeMirrorToTypeRef;
import io.sundr.model.AnnotationRef;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
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

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationMirrorToAnnotationRefTest {
    private static final Duration EXTERNAL_COMPILER_TIMEOUT = Duration.ofSeconds(55);

    @Test
    void convertsArrayValuedAnnotationParameters() {
        ArrayAnnotationProcessor processor = new ArrayAnnotationProcessor();
        CompilationResult result = compile(Map.of(
                "sample.ArrayValues", """
                        package sample;

                        public @interface ArrayValues {
                            String[] names();
                            int[] numbers();
                        }
                        """,
                "sample.AnnotatedSubject", """
                        package sample;

                        @ArrayValues(names = {"alpha", "beta"}, numbers = {1, 2, 3})
                        public class AnnotatedSubject {
                        }
                        """), processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.records()).contains(
                "annotation:sample.ArrayValues",
                "names:[alpha, beta]",
                "numbers:[1, 2, 3]");
    }

    private static CompilationResult compile(Map<String, String> sources, AbstractProcessor processor) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return compileInExternalJvm(sources, processor);
        }

        CompilationResult result = compileInProcess(sources, processor, System.getProperty("java.class.path", ""));
        if (needsExternalCompiler(result)) {
            return compileInExternalJvm(sources, processor);
        }
        return result;
    }

    private static CompilationResult compileInProcess(
            Map<String, String> sources, AbstractProcessor processor, String classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("system Java compiler").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = new ArrayList<>();
        options.add("-proc:only");
        options.add("-implicit:none");
        if (!classpath.isBlank()) {
            options.add("-classpath");
            options.add(classpath);
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Path outputDirectory = Files.createTempDirectory("sundr-adapter-apt-classes");
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    sources.entrySet().stream()
                            .map(entry -> new SourceFile(entry.getKey(), entry.getValue()))
                            .toList());
            task.setProcessors(List.of(processor));
            Boolean successful = task.call();
            return new CompilationResult(
                    Boolean.TRUE.equals(successful),
                    diagnosticText(diagnostics.getDiagnostics()),
                    processor instanceof RecordingProcessor recordingProcessor
                            ? recordingProcessor.records()
                            : List.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnosticText().contains("Unable to find package java.lang in platform classes");
    }

    private static CompilationResult compileInExternalJvm(Map<String, String> sources, AbstractProcessor processor) {
        try {
            Path tempDirectory = Files.createTempDirectory("sundr-adapter-apt-test");
            Path sourceDirectory = tempDirectory.resolve("sources");
            Path outputFile = tempDirectory.resolve("result.properties");
            Path logFile = tempDirectory.resolve("compiler.log");
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path sourceFile = sourceDirectory.resolve(source.getKey().replace('.', '/') + ".java");
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(sourceFile, source.getValue(), StandardCharsets.UTF_8);
            }

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(externalClasspath());
            command.add(AnnotationMirrorToAnnotationRefTest.class.getName());
            command.add("compile-helper");
            command.add(processorKind(processor));
            command.add(sourceDirectory.toString());
            command.add(outputFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CompilationResult(false, "External javac process timed out", List.of());
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput, List.of());
            }

            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return new CompilationResult(
                    Boolean.parseBoolean(properties.getProperty("successful")),
                    properties.getProperty("diagnostics", ""),
                    records(properties));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "compile-helper".equals(args[0])) {
            runCompileHelper(args);
        }
    }

    private static void runCompileHelper(String[] args) throws IOException {
        AbstractProcessor processor = processorForKind(args[1]);
        Map<String, String> sources = readSources(Path.of(args[2]));
        CompilationResult result = compileInProcess(sources, processor, externalClasspath());
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnosticText());
        int index = 0;
        for (String record : result.records()) {
            properties.setProperty("record." + index, record);
            index++;
        }
        properties.setProperty("record.count", Integer.toString(index));
        try (OutputStream outputStream = Files.newOutputStream(Path.of(args[3]))) {
            properties.store(outputStream, null);
        }
    }

    private static Map<String, String> readSources(Path sourceDirectory) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            for (Path source : paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                Path relative = sourceDirectory.relativize(source);
                String className = relative.toString()
                        .replace(source.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.java$", "");
                sources.put(className, Files.readString(source, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static String diagnosticText(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            builder.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static List<String> records(Properties properties) {
        int count = Integer.parseInt(properties.getProperty("record.count", "0"));
        List<String> records = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            records.add(properties.getProperty("record." + index));
        }
        return records;
    }

    private static String externalClasspath() throws IOException {
        List<String> entries = new ArrayList<>();
        entries.add(Path.of("build", "classes", "java", "test").toAbsolutePath().toString());
        entries.add(Path.of("build", "resources", "test").toAbsolutePath().toString());
        Path libs = Path.of("build", "libs");
        if (Files.isDirectory(libs)) {
            try (Stream<Path> paths = Files.list(libs)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .map(path -> path.toAbsolutePath().toString())
                        .forEach(entries::add);
            }
        }
        addCachedArtifacts(entries, "io.sundr", "sundr-adapter-apt");
        addCachedArtifacts(entries, "io.sundr", "sundr-adapter-api");
        addCachedArtifacts(entries, "io.sundr", "sundr-model-utils");
        addCachedArtifacts(entries, "io.sundr", "sundr-model-repo");
        addCachedArtifacts(entries, "io.sundr", "sundr-model");
        addCachedArtifacts(entries, "io.sundr", "sundr-model-base");
        addCachedArtifacts(entries, "io.sundr", "sundr-core");
        addCachedArtifacts(entries, "org.assertj", "assertj-core");
        addCachedArtifacts(entries, "org.junit.jupiter", "junit-jupiter-api");
        addCachedArtifacts(entries, "org.junit.platform", "junit-platform-commons");
        addCachedArtifacts(entries, "org.opentest4j", "opentest4j");
        addCachedArtifacts(entries, "org.apiguardian", "apiguardian-api");
        String currentClasspath = System.getProperty("java.class.path", "");
        if (!currentClasspath.isBlank()) {
            entries.add(currentClasspath);
        }
        return String.join(System.getProperty("path.separator"), entries);
    }

    private static void addCachedArtifacts(List<String> entries, String group, String artifact) throws IOException {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return;
        }
        Path artifactCache = Path.of(userHome, ".gradle", "caches", "modules-2", "files-2.1", group, artifact);
        if (!Files.isDirectory(artifactCache)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(artifactCache, 3)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().toString())
                    .forEach(entries::add);
        }
    }

    private static String javaExecutable() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getenv("GRAALVM_HOME");
        }
        if (javaHome != null && !javaHome.isBlank()) {
            Path java = Path.of(javaHome, "bin", "java");
            if (Files.isRegularFile(java)) {
                return java.toString();
            }
        }
        return "java";
    }

    private static String processorKind(AbstractProcessor processor) {
        if (processor instanceof ArrayAnnotationProcessor) {
            return "array-annotation";
        }
        throw new IllegalArgumentException("Unknown processor: " + processor.getClass().getName());
    }

    private static AbstractProcessor processorForKind(String processorKind) {
        if ("array-annotation".equals(processorKind)) {
            return new ArrayAnnotationProcessor();
        }
        throw new IllegalArgumentException("Unknown processor kind: " + processorKind);
    }

    private interface RecordingProcessor {
        List<String> records();
    }

    private static final class ArrayAnnotationProcessor extends AbstractProcessor implements RecordingProcessor {
        private final List<String> records = new ArrayList<>();
        private boolean processed;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("sample.ArrayValues");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (processed) {
                return false;
            }
            TypeElement subject = processingEnv.getElementUtils().getTypeElement("sample.AnnotatedSubject");
            if (subject == null) {
                return false;
            }
            processed = true;

            AptContext context = AptContext.create(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
            TypeMirrorToTypeRef referenceAdapter = new TypeMirrorToTypeRef(context);
            AnnotationMirrorToAnnotationRef adapter = new AnnotationMirrorToAnnotationRef(context, referenceAdapter);
            AnnotationMirror annotationMirror = subject.getAnnotationMirrors().stream()
                    .filter(mirror -> mirror.getAnnotationType().toString().equals("sample.ArrayValues"))
                    .findFirst()
                    .orElseThrow();

            AnnotationRef annotationRef = adapter.apply(annotationMirror);
            records.add("annotation:" + annotationRef.getClassRef().getFullyQualifiedName());
            records.add("names:" + Arrays.toString((String[]) annotationRef.getParameters().get("names")));
            records.add("numbers:" + Arrays.toString((int[]) annotationRef.getParameters().get("numbers")));
            return false;
        }

        @Override
        public List<String> records() {
            return List.copyOf(records);
        }
    }

    private record CompilationResult(boolean successful, String diagnosticText, List<String> records) {
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
