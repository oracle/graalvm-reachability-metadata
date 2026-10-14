/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.auto.value.processor.AutoBuilderProcessor;
import com.google.auto.value.processor.AutoOneOfProcessor;
import com.google.auto.value.processor.AutoValueProcessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public final class ProcessorCompilation {
    private static final Duration COMPILER_TIMEOUT = Duration.ofSeconds(55);
    private static final Map<String, String> SOURCES = Map.of(
            "sample.Person", """
                    package sample;

                    import com.google.auto.value.AutoValue;
                    import com.google.auto.value.extension.memoized.Memoized;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.TYPE)
                    @interface Description {
                        String value();
                    }

                    @AutoValue
                    @AutoValue.CopyAnnotations
                    @Description("person")
                    abstract class Person {
                        abstract String name();
                        abstract int age();

                        @Memoized
                        String displayName() {
                            return name() + " (" + age() + ")";
                        }

                        static Builder builder() {
                            return new AutoValue_Person.Builder();
                        }

                        @AutoValue.Builder
                        abstract static class Builder {
                            abstract Builder name(String name);
                            abstract Builder age(int age);
                            abstract Person build();
                        }
                    }
                    """,
            "sample.Choice", """
                    package sample;

                    import com.google.auto.value.AutoOneOf;

                    @AutoOneOf(Choice.Kind.class)
                    abstract class Choice {
                        enum Kind { TEXT, NUMBER }

                        abstract Kind kind();
                        abstract String text();
                        abstract int number();

                        static Choice text(String value) {
                            return AutoOneOf_Choice.text(value);
                        }
                    }
                    """,
            "sample.Labels", """
                    package sample;

                    import com.google.auto.value.AutoAnnotation;

                    @interface Label {
                        String value();
                        int rank() default 7;
                    }

                    final class Labels {
                        private Labels() {}

                        @AutoAnnotation
                        static Label create(String value) {
                            return new AutoAnnotation_Labels_create(value);
                        }
                    }
                    """,
            "sample.MessageBuilder", """
                    package sample;

                    import com.google.auto.value.AutoBuilder;

                    record Message(String text, int count) {}

                    @AutoBuilder(ofClass = Message.class)
                    interface MessageBuilder {
                        MessageBuilder text(String text);
                        MessageBuilder count(int count);
                        Message build();
                    }
                    """);

    private ProcessorCompilation() {}

    public static Result result() {
        return ResultHolder.RESULT;
    }

    private static Result compile() {
        try {
            Path root = Files.createTempDirectory("auto-value-processors");
            Path classes = Files.createDirectories(root.resolve("classes"));
            Path generated = Files.createDirectories(root.resolve("generated"));
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler != null) {
                return compileInProcess(compiler, classes, generated);
            }
            return compileWithJavac(root, classes, generated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Result compileInProcess(JavaCompiler compiler, Path classes, Path generated)
            throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = List.of(
                "-classpath", System.getProperty("java.class.path", ""),
                "-implicit:none");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generated));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    SOURCES.entrySet().stream()
                            .map(entry -> new SourceFile(entry.getKey(), entry.getValue()))
                            .toList());
            task.setProcessors(List.of(
                    new AutoValueProcessor(),
                    new AutoOneOfProcessor(),
                    new AutoAnnotationProcessor(),
                    new AutoBuilderProcessor()));
            boolean successful = Boolean.TRUE.equals(task.call());
            return new Result(successful, generated, diagnosticText(diagnostics.getDiagnostics()));
        }
    }

    private static Result compileWithJavac(Path root, Path classes, Path generated) throws IOException {
        Path sources = Files.createDirectories(root.resolve("sources"));
        for (Map.Entry<String, String> source : SOURCES.entrySet()) {
            Path file = sources.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
        }
        Path output = root.resolve("javac.log");
        List<String> command = new ArrayList<>();
        command.add(javacExecutable());
        command.add("-classpath");
        command.add(externalClasspath());
        command.add("-processor");
        command.add(String.join(",",
                AutoValueProcessor.class.getName(),
                AutoOneOfProcessor.class.getName(),
                AutoAnnotationProcessor.class.getName(),
                AutoBuilderProcessor.class.getName()));
        command.add("-implicit:none");
        command.add("-d");
        command.add(classes.toString());
        command.add("-s");
        command.add(generated.toString());
        SOURCES.keySet().stream()
                .map(name -> sources.resolve(name.replace('.', '/') + ".java").toString())
                .forEach(command::add);
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            boolean completed = process.waitFor(COMPILER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new Result(false, generated, "External javac process timed out");
            }
            return new Result(process.exitValue() == 0, generated, Files.readString(output));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
        }
    }

    private static String externalClasspath() throws IOException {
        List<String> entries = new ArrayList<>();
        String currentClasspath = System.getProperty("java.class.path", "");
        if (!currentClasspath.isBlank()) {
            entries.add(currentClasspath);
        }
        addCachedArtifacts(entries, "com.google.auto.value", "auto-value");
        addCachedArtifacts(entries, "com.google.auto.value", "auto-value-annotations");
        return String.join(System.getProperty("path.separator"), entries);
    }

    private static void addCachedArtifacts(List<String> entries, String group, String artifact)
            throws IOException {
        Path cache = Path.of(
                System.getProperty("user.home"),
                ".gradle",
                "caches",
                "modules-2",
                "files-2.1",
                group,
                artifact);
        if (!Files.isDirectory(cache)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(cache, 3)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().toString())
                    .forEach(entries::add);
        }
    }

    private static String javacExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "javac.exe"
                : "javac";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
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

    public record Result(boolean successful, Path generatedSources, String diagnostics) {
        public Path generatedSource(String simpleName) {
            return generatedSources.resolve("sample").resolve(simpleName + ".java");
        }
    }

    private static final class ResultHolder {
        private static final Result RESULT = compile();
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
