/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_apt;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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
import org.springframework.modulith.aptk.tools.AnnotationValueUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationValueUtilsTest {
    private static final String EXTERNAL_COMPILER_CLASSPATH_RESOURCE = "/external-compiler-classpath.txt";
    private static final Duration EXTERNAL_COMPILER_TIMEOUT = Duration.ofSeconds(55);
    private static final String SOURCE = """
            package sample;

            @interface ComponentOptions {
                Class<?> implementation();
                String[] aliases();
            }

            @ComponentOptions(
                    implementation = String.class,
                    aliases = {"primary", "secondary"})
            final class ConfiguredComponent {
            }
            """;

    @Test
    void readsClassAndArrayValuesFromCompilerModel() {
        CompilationResult result = compile();

        assertThat(result.successful()).as(result.diagnostics()).isTrue();
        assertThat(result.implementation()).isEqualTo(String.class.getName());
        assertThat(result.aliases()).containsExactly("primary", "secondary");
    }

    private static CompilationResult compile() {
        if (System.getProperty("java.home") != null) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler != null) {
                CompilationResult result = compileInProcess(compiler);
                if (!needsExternalCompiler(result)) {
                    return result;
                }
            }
        }
        return compileInExternalJvm();
    }

    private static CompilationResult compileInProcess(JavaCompiler compiler) {
        AnnotationValuesProcessor processor = new AnnotationValuesProcessor();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Path outputDirectory = Files.createTempDirectory("annotation-value-utils-classes");
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:only", "-implicit:none"),
                    null,
                    List.of(new SourceFile("sample.ConfiguredComponent", SOURCE)));
            task.setProcessors(List.of(processor));
            boolean successful = Boolean.TRUE.equals(task.call());
            return new CompilationResult(
                    successful,
                    diagnosticText(diagnostics.getDiagnostics()),
                    processor.implementation(),
                    processor.aliases());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnostics().contains("Unable to find package java.lang in platform classes");
    }

    private static CompilationResult compileInExternalJvm() {
        try {
            Path outputFile = Files.createTempFile("annotation-value-utils-result", ".properties");
            Path logFile = Files.createTempFile("annotation-value-utils-compiler", ".log");
            Process process = new ProcessBuilder(
                            javaExecutable(),
                            "-cp",
                            externalClasspath(),
                            AnnotationValueUtilsTest.class.getName(),
                            "compile-helper",
                            outputFile.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CompilationResult(false, "External javac process timed out", "", List.of());
            }

            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput, "", List.of());
            }

            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return fromProperties(properties);
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

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        CompilationResult result = compiler == null
                ? new CompilationResult(false, "System Java compiler is not available", "", List.of())
                : compileInProcess(compiler);
        Properties properties = toProperties(result);
        try (OutputStream outputStream = Files.newOutputStream(Path.of(args[1]))) {
            properties.store(outputStream, null);
        }
    }

    private static String externalClasspath() throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        addClasspathEntry(entries, Path.of("build", "classes", "java", "test"));
        addClasspathEntry(entries, Path.of("build", "resources", "test"));

        try (InputStream inputStream = AnnotationValueUtilsTest.class
                .getResourceAsStream(EXTERNAL_COMPILER_CLASSPATH_RESOURCE)) {
            if (inputStream != null) {
                new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .map(String::trim)
                        .filter(entry -> !entry.isBlank())
                        .forEach(entries::add);
            }
        }

        String currentClasspath = System.getProperty("java.class.path", "");
        if (!currentClasspath.isBlank()) {
            for (String entry : currentClasspath.split(System.getProperty("path.separator"))) {
                if (!entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        return String.join(System.getProperty("path.separator"), entries);
    }

    private static void addClasspathEntry(Set<String> entries, Path path) {
        if (Files.exists(path)) {
            entries.add(path.toAbsolutePath().toString());
        }
    }

    private static String javaExecutable() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getenv("GRAALVM_HOME");
        }
        if (javaHome != null && !javaHome.isBlank()) {
            String executableName = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
            Path java = Path.of(javaHome, "bin", executableName);
            if (Files.isRegularFile(java)) {
                return java.toString();
            }
        }
        return "java";
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

    private static Properties toProperties(CompilationResult result) {
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnostics());
        properties.setProperty("implementation", result.implementation());
        properties.setProperty("alias.count", Integer.toString(result.aliases().size()));
        for (int index = 0; index < result.aliases().size(); index++) {
            properties.setProperty("alias." + index, result.aliases().get(index));
        }
        return properties;
    }

    private static CompilationResult fromProperties(Properties properties) {
        int count = Integer.parseInt(properties.getProperty("alias.count", "0"));
        List<String> aliases = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            aliases.add(properties.getProperty("alias." + index));
        }
        return new CompilationResult(
                Boolean.parseBoolean(properties.getProperty("successful")),
                properties.getProperty("diagnostics", ""),
                properties.getProperty("implementation", ""),
                aliases);
    }

    private static final class AnnotationValuesProcessor extends AbstractProcessor {
        private String implementation = "";
        private List<String> aliases = List.of();
        private boolean processed;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("sample.ComponentOptions");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (processed) {
                return false;
            }

            TypeElement component = processingEnv.getElementUtils().getTypeElement("sample.ConfiguredComponent");
            if (component == null) {
                return false;
            }

            processed = true;
            AnnotationMirror options = component.getAnnotationMirrors().stream()
                    .filter(annotation -> annotation.getAnnotationType().toString().equals("sample.ComponentOptions"))
                    .findFirst()
                    .orElseThrow();
            Class<?> implementationClass = AnnotationValueUtils.getClassValue(value(options, "implementation"));
            String[] aliasValues = AnnotationValueUtils.getStringValueArray(value(options, "aliases"));

            implementation = implementationClass == null ? "" : implementationClass.getName();
            aliases = aliasValues == null ? List.of() : List.of(aliasValues);
            return false;
        }

        private AnnotationValue value(AnnotationMirror annotation, String name) {
            return processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals(name))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow();
        }

        private String implementation() {
            return implementation;
        }

        private List<String> aliases() {
            return aliases;
        }
    }

    private record CompilationResult(
            boolean successful, String diagnostics, String implementation, List<String> aliases) {
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
