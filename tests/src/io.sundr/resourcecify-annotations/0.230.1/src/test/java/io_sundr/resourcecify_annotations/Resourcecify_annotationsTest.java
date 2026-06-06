/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.resourcecify_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Resourcecify_annotationsTest {
    private static final String PROCESSOR_CLASS_NAME =
            "io.sundr.resourcecify.internal.processor.ResourcecifyProcessor";
    private static final long EXTERNAL_COMPILER_TIMEOUT_SECONDS = 30;

    @Test
    void resourcecifyAnnotationIsRejectedOutsideTypes(@TempDir Path sourceDirectory) throws IOException {
        String invalidSource = """
                package sample.resourcecify;

                import io.sundr.resourcecify.annotations.Resourcecify;

                public class InvalidResourcecifyTarget {
                    @Resourcecify
                    public String value() {
                        return "not-a-type";
                    }
                }
                """;
        writeSource(sourceDirectory, "sample.resourcecify.InvalidResourcecifyTarget", invalidSource);

        CompilationResult result = compileWithDiscoveredProcessor(sourceDirectory);

        assertThat(result.successful()).isFalse();
        assertThat(result.diagnosticText()).contains("not applicable to this kind of declaration");
    }

    @Test
    void annotationProcessorIsRegisteredAsAJavaService() {
        Processor processor = findResourcecifyProcessor();

        assertThat(processor.getClass().getName()).isEqualTo(PROCESSOR_CLASS_NAME);
        assertThat(processor.getSupportedAnnotationTypes())
                .containsExactly("io.sundr.resourcecify.annotations.Resourcecify");
    }

    @Test
    void resourcecifyProcessorCopiesAnnotatedSourcesToClassOutput(@TempDir Path sourceDirectory) throws IOException {
        String annotatedSource = """
                package sample.resourcecify;

                import io.sundr.resourcecify.annotations.Resourcecify;

                @Resourcecify
                public class AnnotatedSource {
                    public String value() {
                        return "copied";
                    }
                }
                """;
        String plainSource = """
                package sample.resourcecify;

                public class PlainSource {
                    public String value() {
                        return "not-copied";
                    }
                }
                """;
        writeSource(sourceDirectory, "sample.resourcecify.AnnotatedSource", annotatedSource);
        writeSource(sourceDirectory, "sample.resourcecify.PlainSource", plainSource);

        CompilationResult result = compileWithDiscoveredProcessor(sourceDirectory);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.generatedResources())
                .containsEntry("sample/resourcecify/AnnotatedSource.java", annotatedSource)
                .doesNotContainKey("sample/resourcecify/PlainSource.java");
    }

    private static Processor findResourcecifyProcessor() {
        for (Processor processor : ServiceLoader.load(Processor.class)) {
            if (PROCESSOR_CLASS_NAME.equals(processor.getClass().getName())) {
                return processor;
            }
        }
        throw new AssertionError("Resourcecify annotation processor service was not discovered");
    }

    private static CompilationResult compileWithDiscoveredProcessor(Path sourceDirectory) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return compileInExternalJvm(sourceDirectory);
        }

        CompilationResult result = compileInProcess(sourceDirectory, System.getProperty("java.class.path", ""));
        if (needsExternalCompiler(result)) {
            return compileInExternalJvm(sourceDirectory);
        }
        return result;
    }

    private static CompilationResult compileInProcess(Path sourceDirectory, String classpath) {
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
            Path classOutputDirectory = Files.createTempDirectory("resourcecify-class-output");
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(sourceDirectory));
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles(sourceDirectory)));
            task.setProcessors(List.of(findResourcecifyProcessor()));
            Boolean successful = task.call();
            return new CompilationResult(
                    Boolean.TRUE.equals(successful),
                    diagnosticText(diagnostics.getDiagnostics()),
                    generatedResources(classOutputDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CompilationResult compileInExternalJvm(Path sourceDirectory) {
        try {
            Path outputFile = Files.createTempFile("resourcecify-compiler-result", ".properties");
            Path logFile = Files.createTempFile("resourcecify-compiler", ".log");

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(externalClasspath());
            command.add(Resourcecify_annotationsTest.class.getName());
            command.add("compile-helper");
            command.add(sourceDirectory.toString());
            command.add(outputFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CompilationResult(false, "External javac process timed out", Map.of());
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput, Map.of());
            }

            Properties properties = new Properties();
            try (var inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return new CompilationResult(
                    Boolean.parseBoolean(properties.getProperty("successful")),
                    properties.getProperty("diagnostics", ""),
                    generatedResources(properties));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || !"compile-helper".equals(args[0])) {
            return;
        }

        CompilationResult result = compileInProcess(Path.of(args[1]), externalClasspath());
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnosticText());
        int index = 0;
        for (Map.Entry<String, String> resource : result.generatedResources().entrySet()) {
            properties.setProperty("resource.path." + index, resource.getKey());
            properties.setProperty("resource.content." + index, resource.getValue());
            index++;
        }
        properties.setProperty("resource.count", Integer.toString(index));
        try (OutputStream outputStream = Files.newOutputStream(Path.of(args[2]))) {
            properties.store(outputStream, null);
        }
    }

    private static void writeSource(Path sourceDirectory, String className, String source) throws IOException {
        Path sourceFile = sourceDirectory.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
    }

    private static List<Path> sourceFiles(Path sourceDirectory) throws IOException {
        try (var paths = Files.walk(sourceDirectory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }

    private static Map<String, String> generatedResources(Path classOutputDirectory) throws IOException {
        Map<String, String> resources = new HashMap<>();
        if (!Files.isDirectory(classOutputDirectory)) {
            return resources;
        }
        try (var paths = Files.walk(classOutputDirectory)) {
            for (Path resource : paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String relativePath = classOutputDirectory.relativize(resource).toString()
                        .replace(resource.getFileSystem().getSeparator(), "/");
                resources.put(relativePath, Files.readString(resource, StandardCharsets.UTF_8));
            }
        }
        return resources;
    }

    private static Map<String, String> generatedResources(Properties properties) {
        int count = Integer.parseInt(properties.getProperty("resource.count", "0"));
        Map<String, String> resources = new HashMap<>();
        for (int index = 0; index < count; index++) {
            resources.put(
                    properties.getProperty("resource.path." + index),
                    properties.getProperty("resource.content." + index));
        }
        return resources;
    }

    private static String diagnosticText(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            builder.append(diagnostic.getKind())
                    .append(':')
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnosticText().contains("Unable to find package java.lang in platform classes");
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
        addCachedArtifacts(entries, "io.sundr", "resourcecify-annotations");
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
        if (userHome != null && !userHome.isBlank()) {
            addCachedArtifacts(entries, Path.of(userHome, ".gradle"), group, artifact);
        }
        String gradleUserHome = System.getenv("GRADLE_USER_HOME");
        if (gradleUserHome != null && !gradleUserHome.isBlank()) {
            addCachedArtifacts(entries, Path.of(gradleUserHome), group, artifact);
        }

        Path forgeGradleCaches = Path.of(System.getProperty("java.io.tmpdir"), "metadata-forge-gradle");
        if (Files.isDirectory(forgeGradleCaches)) {
            Path expectedSuffix = Path.of("caches", "modules-2", "files-2.1", group, artifact);
            try (var paths = Files.walk(forgeGradleCaches, 8)) {
                for (Path artifactCache : paths.filter(path -> path.endsWith(expectedSuffix)).toList()) {
                    addJarPaths(entries, artifactCache);
                }
            }
        }
    }

    private static void addCachedArtifacts(
            List<String> entries, Path gradleUserHome, String group, String artifact) throws IOException {
        Path artifactCache = gradleUserHome.resolve(Path.of("caches", "modules-2", "files-2.1", group, artifact));
        addJarPaths(entries, artifactCache);
    }

    private static void addJarPaths(List<String> entries, Path artifactCache) throws IOException {
        if (!Files.isDirectory(artifactCache)) {
            return;
        }
        try (var paths = Files.walk(artifactCache, 3)) {
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

    private record CompilationResult(
            boolean successful, String diagnosticText, Map<String, String> generatedResources) {
    }
}
