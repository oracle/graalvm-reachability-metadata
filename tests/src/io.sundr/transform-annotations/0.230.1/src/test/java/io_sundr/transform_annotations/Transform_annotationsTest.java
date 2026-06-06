/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.transform_annotations;

import io.sundr.transform.annotations.AnnotationSelector;
import io.sundr.transform.annotations.PackageSelector;
import io.sundr.transform.annotations.ResourceSelector;
import io.sundr.transform.annotations.TemplateTransformation;
import io.sundr.transform.annotations.TemplateTransformations;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
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

public class Transform_annotationsTest {
    private static final Duration EXTERNAL_COMPILER_TIMEOUT = Duration.ofSeconds(55);
    private static final String TEMPLATE_TRANSFORMATION = TemplateTransformation.class.getName();
    private static final String TEMPLATE_TRANSFORMATIONS = TemplateTransformations.class.getName();
    private static final String PACKAGE_SELECTOR = PackageSelector.class.getName();
    private static final String ANNOTATION_SELECTOR = AnnotationSelector.class.getName();
    private static final String RESOURCE_SELECTOR = ResourceSelector.class.getName();

    @Test
    void processorSeesTransformationSelectorsAndExplicitValues() {
        UsageProcessor processor = new UsageProcessor();
        CompilationResult result = compile(Map.of(
                "sample.Marker", """
                        package sample;

                        public @interface Marker {
                        }
                        """,
                "sample.OtherMarker", """
                        package sample;

                        public @interface OtherMarker {
                        }
                        """,
                "sample.TransformationPlan", """
                        package sample;

                        import io.sundr.transform.annotations.AnnotationSelector;
                        import io.sundr.transform.annotations.PackageSelector;
                        import io.sundr.transform.annotations.ResourceSelector;
                        import io.sundr.transform.annotations.TemplateTransformation;
                        import io.sundr.transform.annotations.TemplateTransformations;

                        @TemplateTransformation(
                                value = "/templates/direct.vm",
                                outputPath = "generated/Direct.txt",
                                gather = true)
                        @TemplateTransformations(
                                value = {
                                    @TemplateTransformation("templates/first.vm"),
                                    @TemplateTransformation(
                                            value = "/templates/gathered.vm",
                                            outputPath = "generated/Gathered.txt",
                                            gather = true)
                                },
                                packages = {
                                    @PackageSelector("sample.targets"),
                                    @PackageSelector(value = "sample.more", pattern = ".*Service")
                                },
                                annotations = {
                                    @AnnotationSelector(Marker.class),
                                    @AnnotationSelector(
                                            value = OtherMarker.class,
                                            packages = @PackageSelector(
                                                    value = "sample.targets",
                                                    pattern = "Selected.*"))
                                },
                                resources = @ResourceSelector("META-INF/sundr-types.list"))
                        final class TransformationPlan {
                        }
                        """), processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.records()).contains(
                "direct:sample.TransformationPlan:/templates/direct.vm:generated/Direct.txt:true",
                "container:sample.TransformationPlan:transformations=2:packages=2:annotations=2:resources=1",
                "container.transformation:sample.TransformationPlan:0:templates/first.vm::false",
                "container.transformation:sample.TransformationPlan:1:/templates/gathered.vm:"
                        + "generated/Gathered.txt:true",
                "container.package:sample.TransformationPlan:0:sample.targets:[a-zA-Z_$][a-zA-Z0-9_$]*",
                "container.package:sample.TransformationPlan:1:sample.more:.*Service",
                "container.annotation:sample.TransformationPlan:0:sample.Marker:packages=0",
                "container.annotation:sample.TransformationPlan:1:sample.OtherMarker:packages=1",
                "container.annotation.package:sample.TransformationPlan:1:0:sample.targets:Selected.*",
                "container.resource:sample.TransformationPlan:0:META-INF/sundr-types.list");
    }

    @Test
    void processorSeesDefaultValuesOnNestedAnnotations() {
        UsageProcessor processor = new UsageProcessor();
        CompilationResult result = compile(Map.of(
                "sample.Marker", """
                        package sample;

                        public @interface Marker {
                        }
                        """,
                "sample.DefaultPlan", """
                        package sample;

                        import io.sundr.transform.annotations.AnnotationSelector;
                        import io.sundr.transform.annotations.PackageSelector;
                        import io.sundr.transform.annotations.TemplateTransformation;
                        import io.sundr.transform.annotations.TemplateTransformations;

                        @TemplateTransformation("templates/default-direct.vm")
                        @TemplateTransformations(
                                value = @TemplateTransformation("templates/default-nested.vm"),
                                packages = @PackageSelector("sample.defaults"),
                                annotations = @AnnotationSelector(Marker.class))
                        final class DefaultPlan {
                        }
                        """), processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.records()).contains(
                "direct:sample.DefaultPlan:templates/default-direct.vm::false",
                "container:sample.DefaultPlan:transformations=1:packages=1:annotations=1:resources=0",
                "container.transformation:sample.DefaultPlan:0:templates/default-nested.vm::false",
                "container.package:sample.DefaultPlan:0:sample.defaults:[a-zA-Z_$][a-zA-Z0-9_$]*",
                "container.annotation:sample.DefaultPlan:0:sample.Marker:packages=0");
    }

    @Test
    void javacDiscoversProcessorAndGeneratesSourceFromPackageRelativeVelocityTemplate() {
        GenerationResult result = generateWithDiscoveredProcessor(Map.of("sample.Subject", """
                package sample;

                import io.sundr.transform.annotations.TemplateTransformation;

                @TemplateTransformation("transformation.vm")
                public class Subject {
                }
                """), Map.of("sample/transformation.vm", """
                package ${model.packageName};

                public class Generated${model.name} {
                    public String sourceType() {
                        return "${model.name}";
                    }
                }
                """));

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.generatedSources())
                .containsKey("sample/GeneratedSubject.java");
        assertThat(result.generatedSources().get("sample/GeneratedSubject.java"))
                .contains("package sample;")
                .contains("public class GeneratedSubject")
                .contains("return \"Subject\";");
    }

    @Test
    void annotationTypesPublishExpectedCompileTimeContracts() {
        ContractProcessor processor = new ContractProcessor();
        CompilationResult result = compile(Map.of("sample.ContractProbe", """
                package sample;

                final class ContractProbe {
                }
                """), processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(result.records()).contains(
                "type:io.sundr.transform.annotations.TemplateTransformation:ANNOTATION_TYPE",
                "meta:io.sundr.transform.annotations.TemplateTransformation:Retention:SOURCE",
                "meta:io.sundr.transform.annotations.TemplateTransformation:Target:TYPE",
                "method:io.sundr.transform.annotations.TemplateTransformation:value:java.lang.String:<required>",
                "method:io.sundr.transform.annotations.TemplateTransformation:outputPath:java.lang.String:\"\"",
                "method:io.sundr.transform.annotations.TemplateTransformation:gather:boolean:false",
                "type:io.sundr.transform.annotations.TemplateTransformations:ANNOTATION_TYPE",
                "meta:io.sundr.transform.annotations.TemplateTransformations:Retention:SOURCE",
                "meta:io.sundr.transform.annotations.TemplateTransformations:Target:TYPE",
                "method:" + TEMPLATE_TRANSFORMATIONS + ":value:"
                        + "io.sundr.transform.annotations.TemplateTransformation[]:<required>",
                "method:" + TEMPLATE_TRANSFORMATIONS + ":packages:"
                        + "io.sundr.transform.annotations.PackageSelector[]:{}",
                "method:" + TEMPLATE_TRANSFORMATIONS + ":annotations:"
                        + "io.sundr.transform.annotations.AnnotationSelector[]:{}",
                "method:" + TEMPLATE_TRANSFORMATIONS + ":resources:"
                        + "io.sundr.transform.annotations.ResourceSelector[]:{}",
                "type:io.sundr.transform.annotations.PackageSelector:ANNOTATION_TYPE",
                "method:io.sundr.transform.annotations.PackageSelector:value:java.lang.String:<required>",
                "method:" + PACKAGE_SELECTOR + ":pattern:java.lang.String:"
                        + "\"[a-zA-Z_$][a-zA-Z0-9_$]*\"",
                "type:io.sundr.transform.annotations.AnnotationSelector:ANNOTATION_TYPE",
                "method:" + ANNOTATION_SELECTOR + ":value:"
                        + "java.lang.Class<? extends java.lang.annotation.Annotation>:<required>",
                "method:" + ANNOTATION_SELECTOR + ":packages:"
                        + "io.sundr.transform.annotations.PackageSelector[]:{}",
                "type:io.sundr.transform.annotations.ResourceSelector:ANNOTATION_TYPE",
                "method:io.sundr.transform.annotations.ResourceSelector:value:java.lang.String:<required>");
    }

    @Test
    void compilerRejectsSelectorsOutsideTypeDeclarations() {
        CompilationResult result = compile(Map.of(
                "sample.Marker", """
                        package sample;

                        public @interface Marker {
                        }
                        """,
                "sample.InvalidTargets", """
                        package sample;

                        import io.sundr.transform.annotations.AnnotationSelector;
                        import io.sundr.transform.annotations.PackageSelector;
                        import io.sundr.transform.annotations.ResourceSelector;
                        import io.sundr.transform.annotations.TemplateTransformation;
                        import io.sundr.transform.annotations.TemplateTransformations;

                        final class InvalidTargets {
                            @TemplateTransformation("templates/method.vm")
                            void transformation() {
                            }

                            @TemplateTransformations(@TemplateTransformation("templates/methods.vm"))
                            void transformations() {
                            }

                            @PackageSelector("sample")
                            void packageSelector() {
                            }

                            @AnnotationSelector(Marker.class)
                            void annotationSelector() {
                            }

                            @ResourceSelector("META-INF/types.list")
                            void resourceSelector() {
                            }
                        }
                        """), new NoopProcessor());

        assertThat(result.successful()).as(result.diagnosticText()).isFalse();
        assertThat(result.diagnosticText()).contains("not applicable");
    }

    @Test
    void compilerRejectsMissingRequiredElementsAndInvalidAnnotationSelectorBound() {
        Map<String, String> invalidSources = new HashMap<>();
        invalidSources.put("MissingTemplateTransformationValue", """
                package sample;

                import io.sundr.transform.annotations.TemplateTransformation;

                @TemplateTransformation
                final class MissingTemplateTransformationValue {
                }
                """);
        invalidSources.put("MissingTemplateTransformationsValue", """
                package sample;

                import io.sundr.transform.annotations.TemplateTransformations;

                @TemplateTransformations
                final class MissingTemplateTransformationsValue {
                }
                """);
        invalidSources.put("MissingPackageSelectorValue", """
                package sample;

                import io.sundr.transform.annotations.PackageSelector;

                @PackageSelector
                final class MissingPackageSelectorValue {
                }
                """);
        invalidSources.put("MissingAnnotationSelectorValue", """
                package sample;

                import io.sundr.transform.annotations.AnnotationSelector;

                @AnnotationSelector
                final class MissingAnnotationSelectorValue {
                }
                """);
        invalidSources.put("MissingResourceSelectorValue", """
                package sample;

                import io.sundr.transform.annotations.ResourceSelector;

                @ResourceSelector
                final class MissingResourceSelectorValue {
                }
                """);
        invalidSources.put("InvalidAnnotationSelectorBound", """
                package sample;

                import io.sundr.transform.annotations.AnnotationSelector;

                @AnnotationSelector(String.class)
                final class InvalidAnnotationSelectorBound {
                }
                """);

        for (Map.Entry<String, String> invalidSource : invalidSources.entrySet()) {
            CompilationResult result = compile(
                    Map.of("sample." + invalidSource.getKey(), invalidSource.getValue()), new NoopProcessor());
            String description = invalidSource.getKey() + System.lineSeparator() + result.diagnosticText();
            assertThat(result.successful()).as(description).isFalse();
        }
    }

    private static GenerationResult generateWithDiscoveredProcessor(
            Map<String, String> sources, Map<String, String> sourceResources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return generateInExternalJvm(sources, sourceResources);
        }

        GenerationResult result = generateInProcess(
                sources, sourceResources, System.getProperty("java.class.path", ""));
        if (needsExternalCompiler(result.compilation())) {
            return generateInExternalJvm(sources, sourceResources);
        }
        return result;
    }

    private static GenerationResult generateInProcess(
            Map<String, String> sources, Map<String, String> sourceResources, String classpath) {
        try {
            Path resourceDirectory = Files.createTempDirectory("sundr-transform-annotations-resources");
            writeFiles(resourceDirectory, sourceResources);
            return generateInProcess(sources, Map.of(), classpath, resourceDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GenerationResult generateInProcess(
            Map<String, String> sources,
            Map<String, String> sourceResources,
            String classpath,
            Path resourceDirectory) {
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
            writeFiles(resourceDirectory, sourceResources);
            Path tempDirectory = Files.createTempDirectory("sundr-transform-annotations-generation");
            Path classOutputDirectory = tempDirectory.resolve("classes");
            Path sourceOutputDirectory = tempDirectory.resolve("generated-sources");
            Files.createDirectories(classOutputDirectory);
            Files.createDirectories(sourceOutputDirectory);
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(resourceDirectory));
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutputDirectory));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(sourceOutputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    sources.entrySet().stream()
                            .map(entry -> new SourceFile(entry.getKey(), entry.getValue()))
                            .toList());
            Boolean successful = task.call();
            CompilationResult compilation = new CompilationResult(
                    Boolean.TRUE.equals(successful), diagnosticText(diagnostics.getDiagnostics()), List.of());
            return new GenerationResult(
                    compilation.successful(), compilation.diagnosticText(), generatedSources(sourceOutputDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GenerationResult generateInExternalJvm(
            Map<String, String> sources, Map<String, String> sourceResources) {
        try {
            Path tempDirectory = Files.createTempDirectory("sundr-transform-annotations-generation-test");
            Path sourceDirectory = tempDirectory.resolve("sources");
            Path resourceDirectory = tempDirectory.resolve("resources");
            Path outputFile = tempDirectory.resolve("result.properties");
            Path logFile = tempDirectory.resolve("compiler.log");
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path sourceFile = sourceDirectory.resolve(source.getKey().replace('.', '/') + ".java");
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(sourceFile, source.getValue(), StandardCharsets.UTF_8);
            }
            writeFiles(resourceDirectory, sourceResources);

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(externalClasspath());
            command.add(Transform_annotationsTest.class.getName());
            command.add("generate-helper");
            command.add(sourceDirectory.toString());
            command.add(resourceDirectory.toString());
            command.add(outputFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                String diagnosticText = "External javac process timed out" + System.lineSeparator();
                return new GenerationResult(false, diagnosticText, Map.of());
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new GenerationResult(false, processOutput, Map.of());
            }

            Properties properties = new Properties();
            try (var inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return new GenerationResult(
                    Boolean.parseBoolean(properties.getProperty("successful")),
                    properties.getProperty("diagnostics", ""),
                    generatedSources(properties));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
        }
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
            Path outputDirectory = Files.createTempDirectory("sundr-transform-annotations-classes");
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
            Properties properties = new Properties();
            if (processor instanceof RecordingProcessor recordingProcessor) {
                recordingProcessor.writeTo(properties);
            }
            return new CompilationResult(
                    Boolean.TRUE.equals(successful), diagnosticText(diagnostics.getDiagnostics()), records(properties));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnosticText().contains("Unable to find package java.lang in platform classes");
    }

    private static CompilationResult compileInExternalJvm(Map<String, String> sources, AbstractProcessor processor) {
        try {
            Path tempDirectory = Files.createTempDirectory("sundr-transform-annotations-test");
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
            command.add(Transform_annotationsTest.class.getName());
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
                String diagnosticText = "External javac process timed out" + System.lineSeparator();
                return new CompilationResult(false, diagnosticText, List.of());
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput, List.of());
            }

            Properties properties = new Properties();
            try (var inputStream = Files.newInputStream(outputFile)) {
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

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "compile-helper".equals(args[0])) {
            runCompileHelper(args);
        } else if (args.length > 0 && "generate-helper".equals(args[0])) {
            runGenerateHelper(args);
        }
    }

    private static void runGenerateHelper(String[] args) throws IOException {
        Map<String, String> sources = readSources(Path.of(args[1]));
        GenerationResult result = generateInProcess(sources, Map.of(), externalClasspath(), Path.of(args[2]));
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnosticText());
        int index = 0;
        for (Map.Entry<String, String> generatedSource : result.generatedSources().entrySet()) {
            properties.setProperty("generated.path." + index, generatedSource.getKey());
            properties.setProperty("generated.content." + index, generatedSource.getValue());
            index++;
        }
        properties.setProperty("generated.count", Integer.toString(index));
        try (OutputStream outputStream = Files.newOutputStream(Path.of(args[3]))) {
            properties.store(outputStream, null);
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
        try (var paths = Files.walk(sourceDirectory)) {
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

    private static void writeFiles(Path rootDirectory, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = rootDirectory.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> generatedSources(Path sourceOutputDirectory) throws IOException {
        Map<String, String> generatedSources = new HashMap<>();
        if (!Files.isDirectory(sourceOutputDirectory)) {
            return generatedSources;
        }
        try (var paths = Files.walk(sourceOutputDirectory)) {
            for (Path source : paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String relativePath = sourceOutputDirectory.relativize(source).toString()
                        .replace(source.getFileSystem().getSeparator(), "/");
                generatedSources.put(relativePath, Files.readString(source, StandardCharsets.UTF_8));
            }
        }
        return generatedSources;
    }

    private static Map<String, String> generatedSources(Properties properties) {
        int count = Integer.parseInt(properties.getProperty("generated.count", "0"));
        Map<String, String> generatedSources = new HashMap<>();
        for (int index = 0; index < count; index++) {
            generatedSources.put(
                    properties.getProperty("generated.path." + index),
                    properties.getProperty("generated.content." + index));
        }
        return generatedSources;
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
        addCachedArtifacts(entries, "io.sundr", "transform-annotations");
        addCachedArtifacts(entries, "io.sundr", "sundr-codegen-velocity-nodeps");
        addCachedArtifacts(entries, "io.sundr", "sundr-codegen-template");
        addCachedArtifacts(entries, "io.sundr", "sundr-codegen-apt");
        addCachedArtifacts(entries, "io.sundr", "sundr-codegen-api");
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

    private static String processorKind(AbstractProcessor processor) {
        if (processor instanceof UsageProcessor) {
            return "usage";
        }
        if (processor instanceof ContractProcessor) {
            return "contract";
        }
        if (processor instanceof NoopProcessor) {
            return "noop";
        }
        throw new AssertionError("Unknown processor: " + processor.getClass().getName());
    }

    private static AbstractProcessor processorForKind(String processorKind) {
        return switch (processorKind) {
            case "usage" -> new UsageProcessor();
            case "contract" -> new ContractProcessor();
            case "noop" -> new NoopProcessor();
            default -> throw new AssertionError("Unknown processor kind: " + processorKind);
        };
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

    private static List<String> records(Properties properties) {
        int count = Integer.parseInt(properties.getProperty("record.count", "0"));
        List<String> records = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            records.add(properties.getProperty("record." + index));
        }
        return records;
    }

    private interface RecordingProcessor {
        void writeTo(Properties properties);
    }

    private static final class UsageProcessor extends AbstractProcessor implements RecordingProcessor {
        private final Set<String> records = new TreeSet<>();

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TEMPLATE_TRANSFORMATION, TEMPLATE_TRANSFORMATIONS);
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (Element element : roundEnv.getElementsAnnotatedWith(TemplateTransformation.class)) {
                TemplateTransformation transformation = element.getAnnotation(TemplateTransformation.class);
                records.add("direct:" + qualifiedName(element) + ':' + transformation.value() + ':'
                        + transformation.outputPath() + ':' + transformation.gather());
            }
            for (Element element : roundEnv.getElementsAnnotatedWith(TemplateTransformations.class)) {
                TemplateTransformations transformations = element.getAnnotation(TemplateTransformations.class);
                recordContainer(qualifiedName(element), transformations);
            }
            return false;
        }

        private void recordContainer(String qualifiedName, TemplateTransformations transformations) {
            records.add("container:" + qualifiedName + ":transformations=" + transformations.value().length
                    + ":packages=" + transformations.packages().length
                    + ":annotations=" + transformations.annotations().length
                    + ":resources=" + transformations.resources().length);
            for (int index = 0; index < transformations.value().length; index++) {
                TemplateTransformation transformation = transformations.value()[index];
                records.add("container.transformation:" + qualifiedName + ':' + index + ':' + transformation.value()
                        + ':' + transformation.outputPath() + ':' + transformation.gather());
            }
            for (int index = 0; index < transformations.packages().length; index++) {
                PackageSelector selector = transformations.packages()[index];
                records.add("container.package:" + qualifiedName + ':' + index + ':' + selector.value()
                        + ':' + selector.pattern());
            }
            for (int index = 0; index < transformations.annotations().length; index++) {
                AnnotationSelector selector = transformations.annotations()[index];
                String annotationType = annotationSelectorType(selector);
                records.add("container.annotation:" + qualifiedName + ':' + index + ':' + annotationType
                        + ":packages=" + selector.packages().length);
                for (int packageIndex = 0; packageIndex < selector.packages().length; packageIndex++) {
                    PackageSelector packageSelector = selector.packages()[packageIndex];
                    records.add("container.annotation.package:" + qualifiedName + ':' + index + ':' + packageIndex
                            + ':' + packageSelector.value() + ':' + packageSelector.pattern());
                }
            }
            for (int index = 0; index < transformations.resources().length; index++) {
                ResourceSelector selector = transformations.resources()[index];
                records.add("container.resource:" + qualifiedName + ':' + index + ':' + selector.value());
            }
        }

        private String annotationSelectorType(AnnotationSelector selector) {
            try {
                return selector.value().getName();
            } catch (MirroredTypeException e) {
                return e.getTypeMirror().toString();
            }
        }

        @Override
        public void writeTo(Properties properties) {
            int index = 0;
            for (String record : records) {
                properties.setProperty("record." + index, record);
                index++;
            }
            properties.setProperty("record.count", Integer.toString(index));
        }
    }

    private static final class ContractProcessor extends AbstractProcessor implements RecordingProcessor {
        private final Set<String> records = new TreeSet<>();

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
            if (roundEnv.processingOver()) {
                return false;
            }
            for (String annotationType : List.of(
                    TEMPLATE_TRANSFORMATION,
                    TEMPLATE_TRANSFORMATIONS,
                    PACKAGE_SELECTOR,
                    ANNOTATION_SELECTOR,
                    RESOURCE_SELECTOR)) {
                recordContract(annotationType);
            }
            return false;
        }

        private void recordContract(String annotationType) {
            TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(annotationType);
            records.add("type:" + annotationType + ':' + typeElement.getKind());
            for (AnnotationMirror mirror : typeElement.getAnnotationMirrors()) {
                String metaAnnotation = mirror.getAnnotationType().toString();
                if ("java.lang.annotation.Retention".equals(metaAnnotation)) {
                    records.add("meta:" + annotationType + ":Retention:"
                            + onlyAnnotationValue(mirror).getValue());
                } else if ("java.lang.annotation.Target".equals(metaAnnotation)) {
                    records.add("meta:" + annotationType + ":Target:"
                            + onlyAnnotationValue(mirror).getValue().toString().replace("[", "").replace("]", ""));
                }
            }
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement method) {
                    AnnotationValue defaultValue = method.getDefaultValue();
                    records.add("method:" + annotationType + ':' + method.getSimpleName() + ':'
                            + method.getReturnType() + ':'
                            + (defaultValue == null ? "<required>" : defaultValue.toString()));
                }
            }
        }

        private AnnotationValue onlyAnnotationValue(AnnotationMirror mirror) {
            return mirror.getElementValues().values().iterator().next();
        }

        @Override
        public void writeTo(Properties properties) {
            int index = 0;
            for (String record : records) {
                properties.setProperty("record." + index, record);
                index++;
            }
            properties.setProperty("record.count", Integer.toString(index));
        }
    }

    private static final class NoopProcessor extends AbstractProcessor {
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
            return false;
        }
    }

    private static String qualifiedName(Element element) {
        if (element instanceof TypeElement typeElement) {
            return typeElement.getQualifiedName().toString();
        }
        return element.toString();
    }

    private record GenerationResult(boolean successful, String diagnosticText, Map<String, String> generatedSources) {
        private CompilationResult compilation() {
            return new CompilationResult(successful, diagnosticText, List.of());
        }
    }

    private record CompilationResult(boolean successful, String diagnosticText, List<String> records) {
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
