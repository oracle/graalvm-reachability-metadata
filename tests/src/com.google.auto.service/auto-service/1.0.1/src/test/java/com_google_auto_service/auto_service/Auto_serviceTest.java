/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_service.auto_service;

import com.google.auto.service.processor.AutoServiceProcessor;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Auto_serviceTest {
    private static final String AUTO_SERVICE_TYPE = "com.google.auto.service.AutoService";
    private static final String SERVICE_FILE_PREFIX = "META-INF/services/";
    private static final String LIST_SEPARATOR = "\u001F";
    private static final Duration EXTERNAL_COMPILER_TIMEOUT = Duration.ofSeconds(55);

    @Test
    void processorAdvertisesAutoServiceAnnotationAndLatestSourceVersion() {
        AutoServiceProcessor processor = new AutoServiceProcessor();

        Set<String> supportedAnnotationTypes = processor.getSupportedAnnotationTypes();

        assertThat(supportedAnnotationTypes).containsExactly(AUTO_SERVICE_TYPE);
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latestSupported());
    }

    @Test
    void packagedServiceLoaderDescriptorDiscoversProcessor() {
        List<Processor> processors = new ArrayList<>();
        ServiceLoader<Processor> serviceLoader = ServiceLoader.load(Processor.class);
        for (Processor processor : serviceLoader) {
            if (processor instanceof AutoServiceProcessor) {
                processors.add(processor);
            }
        }

        assertThat(processors).hasSize(1);
        assertThat(processors.get(0).getSupportedAnnotationTypes()).containsExactly(AUTO_SERVICE_TYPE);
    }

    @Test
    void generatesServiceDescriptorForSingleProvider() {
        CompilationResult result = compile(List.of("java.lang.Runnable"), new Source("test.SimpleRunnableService", """
                package test;

                import com.google.auto.service.AutoService;

                @AutoService(Runnable.class)
                final class SimpleRunnableService implements Runnable {
                    @Override
                    public void run() {
                    }
                }
                """));

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.serviceEntries("java.lang.Runnable")).containsExactly("test.SimpleRunnableService");
    }

    @Test
    void generatesSeparateDescriptorsForEveryDeclaredServiceType() {
        CompilationResult result = compile(
                List.of("java.lang.Runnable", "java.util.concurrent.Callable"),
                new Source("test.MultiServiceProvider", """
                        package test;

                        import com.google.auto.service.AutoService;
                        import java.util.concurrent.Callable;

                        @AutoService({Runnable.class, Callable.class})
                        final class MultiServiceProvider implements Runnable, Callable<String> {
                            @Override
                            public void run() {
                            }

                            @Override
                            public String call() {
                                return "called";
                            }
                        }
                        """));

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.serviceEntries("java.lang.Runnable")).containsExactly("test.MultiServiceProvider");
        assertThat(result.serviceEntries("java.util.concurrent.Callable")).containsExactly("test.MultiServiceProvider");
    }

    @Test
    void mergesProvidersForSameServiceUsingSortedBinaryNames() {
        CompilationResult result = compile(List.of("java.lang.Runnable"), new Source("test.ServiceContainer", """
                package test;

                import com.google.auto.service.AutoService;

                final class ServiceContainer {
                    @AutoService(Runnable.class)
                    static final class NestedRunnable implements Runnable {
                        @Override
                        public void run() {
                        }
                    }

                    @AutoService(Runnable.class)
                    static final class AnotherRunnable implements Runnable {
                        @Override
                        public void run() {
                        }
                    }
                }
                """));

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.serviceEntries("java.lang.Runnable")).containsExactly(
                "test.ServiceContainer$AnotherRunnable",
                "test.ServiceContainer$NestedRunnable");
    }

    @Test
    void reportsErrorWhenAnnotatedProviderDoesNotImplementServiceType() {
        CompilationResult result = compile(
                List.of("java.util.concurrent.Callable"),
                new Source("test.InvalidService", """
                        package test;

                        import com.google.auto.service.AutoService;
                        import java.util.concurrent.Callable;

                        @AutoService(Callable.class)
                        final class InvalidService implements Runnable {
                            @Override
                            public void run() {
                            }
                        }
                        """));

        assertThat(result.successful()).as(result.diagnosticText()).isFalse();
        assertThat(result.diagnosticText())
                .contains("ServiceProviders must implement their service provider interface")
                .contains("test.InvalidService")
                .contains("java.util.concurrent.Callable");
        assertThat(result.serviceEntries("java.util.concurrent.Callable")).isEmpty();
    }

    @Test
    void reportsErrorWhenNoServiceTypesAreProvided() {
        CompilationResult result = compile(List.of("java.lang.Runnable"), new Source("test.EmptyServices", """
                package test;

                import com.google.auto.service.AutoService;

                @AutoService({})
                final class EmptyServices implements Runnable {
                    @Override
                    public void run() {
                    }
                }
                """));

        assertThat(result.successful()).as(result.diagnosticText()).isFalse();
        assertThat(result.diagnosticText()).contains("No service interfaces provided for element");
        assertThat(result.serviceEntries("java.lang.Runnable")).isEmpty();
    }

    private static CompilationResult compile(List<String> requestedServiceTypes, Source... sources) {
        if (System.getProperty("java.home") == null) {
            return compileInExternalJvm(requestedServiceTypes, sources);
        }
        ensureJavaHomeProperty();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return compileInExternalJvm(requestedServiceTypes, sources);
        }

        CompilationResult result = compileInProcess(compiler, requestedServiceTypes, sources);
        if (needsExternalCompiler(result)) {
            return compileInExternalJvm(requestedServiceTypes, sources);
        }
        return result;
    }

    private static CompilationResult compileInProcess(
            JavaCompiler compiler, List<String> requestedServiceTypes, Source... sources) {
        try {
            Path tempDirectory = Files.createTempDirectory("auto-service-test");
            Path classOutput = tempDirectory.resolve("classes");
            Files.createDirectories(classOutput);

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            List<String> options = new ArrayList<>();
            options.add("-proc:only");
            options.add("-implicit:none");
            options.add("-Averify=true");
            options.add("-classpath");
            options.add(System.getProperty("java.class.path", ""));
            options.add("-d");
            options.add(classOutput.toString());

            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                    diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                List<JavaFileObject> sourceFiles = new ArrayList<>();
                for (Source source : sources) {
                    sourceFiles.add(new SourceFile(source.className(), source.source()));
                }
                JavaCompiler.CompilationTask task = compiler.getTask(
                        null, fileManager, diagnostics, options, null, sourceFiles);
                task.setProcessors(List.of(new AutoServiceProcessor()));
                Boolean successful = task.call();
                return new CompilationResult(
                        Boolean.TRUE.equals(successful),
                        diagnosticText(diagnostics.getDiagnostics()),
                        readServiceFiles(classOutput, requestedServiceTypes));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnosticText().contains("Unable to find package java.lang in platform classes");
    }

    private static CompilationResult compileInExternalJvm(List<String> requestedServiceTypes, Source... sources) {
        try {
            Path tempDirectory = Files.createTempDirectory("auto-service-test-external");
            Path outputFile = tempDirectory.resolve("result.properties");
            Path logFile = tempDirectory.resolve("compiler.log");

            List<Path> sourceFiles = new ArrayList<>();
            for (int index = 0; index < sources.length; index++) {
                Source source = sources[index];
                Path sourceFile = tempDirectory.resolve("source-" + index).resolve(
                        source.className().replace('.', '/') + JavaFileObject.Kind.SOURCE.extension);
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(sourceFile, source.source(), StandardCharsets.UTF_8);
                sourceFiles.add(sourceFile);
            }

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(externalClasspath());
            command.add(Auto_serviceTest.class.getName());
            command.add("compile-helper");
            command.add(outputFile.toString());
            command.add(Integer.toString(requestedServiceTypes.size()));
            command.addAll(requestedServiceTypes);
            command.add(Integer.toString(sources.length));
            for (int index = 0; index < sources.length; index++) {
                command.add(sources[index].className());
                command.add(sourceFiles.get(index).toString());
            }

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CompilationResult(
                        false, "External javac process timed out" + System.lineSeparator(), Map.of());
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput, Map.of());
            }

            Properties properties = new Properties();
            try (var inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return compilationResultFromProperties(properties, requestedServiceTypes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "compile-helper".equals(args[0])) {
            runCompileHelper(args);
        }
    }

    private static void runCompileHelper(String[] args) throws IOException {
        int position = 1;
        Path outputFile = Path.of(args[position++]);
        int serviceCount = Integer.parseInt(args[position++]);
        List<String> requestedServiceTypes = new ArrayList<>();
        for (int index = 0; index < serviceCount; index++) {
            requestedServiceTypes.add(args[position++]);
        }
        int sourceCount = Integer.parseInt(args[position++]);
        List<Source> sources = new ArrayList<>();
        for (int index = 0; index < sourceCount; index++) {
            String className = args[position++];
            Path sourceFile = Path.of(args[position++]);
            sources.add(new Source(className, Files.readString(sourceFile, StandardCharsets.UTF_8)));
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        CompilationResult result;
        if (compiler == null) {
            result = new CompilationResult(false, "System Java compiler is not available", Map.of());
        } else {
            result = compileInProcess(compiler, requestedServiceTypes, sources.toArray(Source[]::new));
        }

        Properties properties = propertiesFromCompilationResult(result, requestedServiceTypes);
        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            properties.store(outputStream, null);
        }
    }

    private static String externalClasspath() throws IOException {
        List<String> entries = new ArrayList<>();
        entries.add(Path.of("build", "classes", "java", "test").toAbsolutePath().toString());
        entries.add(Path.of("build", "resources", "test").toAbsolutePath().toString());
        Path libs = Path.of("build", "libs");
        if (Files.isDirectory(libs)) {
            try (var paths = Files.list(libs)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .map(path -> path.toAbsolutePath().toString())
                        .forEach(entries::add);
            }
        }
        addCachedArtifacts(entries, "com.google.auto.service", "auto-service");
        addCachedArtifacts(entries, "com.google.auto.service", "auto-service-annotations");
        addCachedArtifacts(entries, "com.google.auto", "auto-common");
        addCachedArtifacts(entries, "com.google.guava", "guava");
        addCachedArtifacts(entries, "com.google.guava", "failureaccess");
        addCachedArtifacts(entries, "com.google.guava", "listenablefuture");
        addCachedArtifacts(entries, "com.google.code.findbugs", "jsr305");
        addCachedArtifacts(entries, "org.checkerframework", "checker-qual");
        addCachedArtifacts(entries, "com.google.errorprone", "error_prone_annotations");
        addCachedArtifacts(entries, "com.google.j2objc", "j2objc-annotations");
        addCachedArtifacts(entries, "org.assertj", "assertj-core");
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
        try (var paths = Files.walk(artifactCache, 4)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
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

    private static void ensureJavaHomeProperty() {
        if (System.getProperty("java.home") == null) {
            String javaHome = System.getenv("JAVA_HOME");
            assertThat(javaHome).as("JAVA_HOME").isNotBlank();
            System.setProperty("java.home", javaHome);
        }
    }

    private static Map<String, List<String>> readServiceFiles(Path classOutput, List<String> serviceTypes)
            throws IOException {
        Map<String, List<String>> serviceFiles = new HashMap<>();
        for (String serviceType : serviceTypes) {
            serviceFiles.put(serviceType, readServiceEntries(classOutput.resolve(SERVICE_FILE_PREFIX + serviceType)));
        }
        return serviceFiles;
    }

    private static List<String> readServiceEntries(Path serviceFile) throws IOException {
        if (!Files.isRegularFile(serviceFile)) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String line : Files.readAllLines(serviceFile, StandardCharsets.UTF_8)) {
            int commentStart = line.indexOf('#');
            String entry = commentStart >= 0 ? line.substring(0, commentStart) : line;
            entry = entry.trim();
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static String diagnosticText(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            text.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return text.toString();
    }

    private static Properties propertiesFromCompilationResult(
            CompilationResult result, List<String> requestedServiceTypes) {
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnosticText());
        for (String serviceType : requestedServiceTypes) {
            properties.setProperty("service." + serviceType, joinList(result.serviceEntries(serviceType)));
        }
        return properties;
    }

    private static CompilationResult compilationResultFromProperties(
            Properties properties, List<String> requestedServiceTypes) {
        Map<String, List<String>> serviceFiles = new HashMap<>();
        for (String serviceType : requestedServiceTypes) {
            serviceFiles.put(serviceType, splitList(properties.getProperty("service." + serviceType, "")));
        }
        return new CompilationResult(
                Boolean.parseBoolean(properties.getProperty("successful")),
                properties.getProperty("diagnostics", ""),
                serviceFiles);
    }

    private static String joinList(List<String> values) {
        return String.join(LIST_SEPARATOR, values);
    }

    private static List<String> splitList(String value) {
        if (value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split(LIST_SEPARATOR, -1));
    }

    private record Source(String className, String source) {
    }

    private record CompilationResult(
            boolean successful, String diagnosticText, Map<String, List<String>> serviceFiles) {
        private List<String> serviceEntries(String serviceType) {
            return serviceFiles.getOrDefault(serviceType, List.of());
        }
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
