/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto.auto_common;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.AnnotationValues;
import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.GeneratedAnnotationSpecs;
import com.google.auto.common.GeneratedAnnotations;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreStreams;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.SimpleAnnotationMirror;
import com.google.auto.common.SimpleTypeAnnotationValue;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.common.Visibility;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Auto_commonTest {
    private static final Duration EXTERNAL_COMPILER_TIMEOUT = Duration.ofSeconds(55);

    @Test
    void immutableCollectorsPreserveExpectedCollectionSemantics() {
        ImmutableList<String> list = Stream.of("first", "second", "first")
                .collect(MoreStreams.toImmutableList());
        ImmutableSet<String> set = Stream.of("first", "second", "first")
                .collect(MoreStreams.toImmutableSet());
        ImmutableMap<Character, Integer> map = Stream.of("alpha", "beta")
                .collect(MoreStreams.toImmutableMap(value -> value.charAt(0), String::length));
        ImmutableBiMap<Integer, String> biMap = Stream.of("one", "three")
                .collect(MoreStreams.toImmutableBiMap(String::length, value -> value));

        assertThat(list).containsExactly("first", "second", "first");
        assertThat(set).containsExactly("first", "second");
        assertThat(map).containsExactly(Map.entry('a', 5), Map.entry('b', 4));
        assertThat(biMap.inverse()).containsEntry("three", 5);
        assertThatThrownBy(() -> Stream.of("one", "two")
                .collect(MoreStreams.toImmutableBiMap(String::length, value -> value)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple entries");
    }

    @Test
    void languageModelUtilitiesReadAnnotationsTypesMethodsAndGeneratedAnnotations() {
        CompilationResult result = compileScenario("model-utilities", modelUtilitySources());

        assertThat(result.successful()).as(result.diagnostics()).isTrue();
        Properties records = result.records();
        assertThat(records.getProperty("package")).isEqualTo("sample");
        assertThat(records.getProperty("annotation.present")).isEqualTo("true:true:true");
        assertThat(records.getProperty("annotated.annotations")).isEqualTo("sample.Info|sample.Info");
        assertThat(records.getProperty("annotation.name.count"))
                .isEqualTo("explicit:42:sample.Info.count:17");
        assertThat(records.getProperty("annotation.scalars"))
                .isEqualTo("8:2:3:1.5:2.5:true:z");
        assertThat(records.getProperty("annotation.complex"))
                .contains("java.util.Map")
                .contains("SECOND")
                .contains("inner")
                .contains("\"left\"")
                .contains("java.lang.String.class")
                .contains("java.util.List.class");
        assertThat(records.getProperty("simple.annotation"))
                .contains("@sample.Info(name = \"explicit\")")
                .contains("sample.Info")
                .contains("true");
        assertThat(records.getProperty("simple.type.value")).isEqualTo("java.lang.String.class:java.lang.String");
        assertThat(records.getProperty("elements"))
                .contains("type=true")
                .contains("typeParam=T")
                .contains("field=field")
                .contains("method=acceptsList")
                .contains("publicPredicate=true");
        assertThat(records.getProperty("visibility")).isEqualTo("PUBLIC:PROTECTED:PRIVATE");
        assertThat(records.getProperty("type.casts"))
                .contains("declared=sample.Subject")
                .contains("array=sample.Subject")
                .contains("primitive=INT")
                .contains("void=VOID")
                .contains("typeVariable=T")
                .contains("wildcard=? extends java.lang.Number")
                .contains("null=NULL")
                .contains("java.lang.Number")
                .contains("java.lang.Runnable");
        assertThat(records.getProperty("type.predicates")).isEqualTo("true:true:true:true:false");
        assertThat(records.getProperty("referenced.types"))
                .contains("java.util.List")
                .contains("java.lang.String")
                .contains("java.lang.Number");
        assertThat(records.getProperty("super.member"))
                .contains("sample.Parent<java.lang.String>")
                .contains("java.lang.String");
        assertThat(records.getProperty("methods"))
                .contains("one")
                .contains("two")
                .contains("duplicate")
                .contains("inherited")
                .contains("acceptsList")
                .doesNotContain("parentPrivate")
                .doesNotContain("parentStatic");
        assertThat(records.getProperty("override.validation"))
                .isEqualTo("true:true:true:true");
        assertThat(records.getProperty("unchecked.conversions")).isEqualTo("true:false:true");
        assertThat(records.getProperty("generated.annotation"))
                .isEqualTo("javax.annotation.processing.Generated");
        assertThat(records.getProperty("generated.spec"))
                .contains("@javax.annotation.processing.Generated")
                .contains("com_google_auto.auto_common.Auto_commonTest")
                .contains("auto-common");
    }

    @Test
    void basicAnnotationProcessorDispatchesAnnotatedElementsToSteps() {
        CompilationResult result = compileScenario("basic-processor", basicProcessorSources());

        assertThat(result.successful()).as(result.diagnostics()).isTrue();
        assertThat(result.records().getProperty("basic.keys")).contains("step.Marker", "step.Other");
        assertThat(result.records().getProperty("basic.elements"))
                .contains("step.Marker=Target")
                .contains("step.Other=value");
        assertThat(result.records().getProperty("basic.postRounds")).isEqualTo("2");
    }

    @Test
    void annotationMirrorAndValueEquivalencesCompareAnnotationContents() {
        CompilationResult result = compileScenario("equivalence", equivalenceSources());

        assertThat(result.successful()).as(result.diagnostics()).isTrue();
        assertThat(result.records().getProperty("equivalence.mirrors")).isEqualTo("true:false:true");
        assertThat(result.records().getProperty("equivalence.values")).isEqualTo("true:false:true:true");
        assertThat(result.records().getProperty("equivalence.nested"))
                .contains("@equivalent.Nested")
                .contains("deep");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "compile-helper".equals(args[0])) {
            runCompileHelper(args);
        }
    }

    private static Source[] modelUtilitySources() {
        return new Source[] {
                new Source("sample.Subject", """
                        package sample;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;
                        import java.util.List;

                        @Retention(RetentionPolicy.RUNTIME)
                        @Target(ElementType.ANNOTATION_TYPE)
                        @interface Stereotype {
                        }

                        enum Mode {
                            FIRST,
                            SECOND
                        }

                        @interface Nested {
                            String value();
                        }

                        @Stereotype
                        @interface Info {
                            String name();
                            int count() default 7;
                            long longValue() default 8L;
                            byte byteValue() default 2;
                            short shortValue() default 3;
                            float floatValue() default 1.5f;
                            double doubleValue() default 2.5;
                            boolean enabled() default true;
                            char letter() default 'z';
                            Class<?> type() default java.util.Map.class;
                            Mode mode() default Mode.SECOND;
                            Nested nested() default @Nested("inner");
                            String[] tags() default {"a", "b"};
                            int[] numbers() default {1, 2};
                            Class<?>[] types() default {String.class, java.util.List.class};
                            Mode[] modes() default {Mode.FIRST, Mode.SECOND};
                            Nested[] nestedArray() default {@Nested("x"), @Nested("y")};
                        }

                        class Parent<E> {
                            public E inherited(E value) {
                                return value;
                            }

                            public Object overridden() {
                                return null;
                            }

                            private void parentPrivate() {
                            }

                            static void parentStatic() {
                            }
                        }

                        interface One {
                            void one();

                            default void duplicate() {
                            }
                        }

                        interface Two {
                            void two();

                            default void duplicate() {
                            }
                        }

                        @Info(name = "explicit", count = 42, tags = {"left", "right"})
                        public class Subject<T extends Number & Runnable> extends Parent<String> implements One, Two {
                            public T field;
                            List<String> stringsField;
                            List<?> wildcardOnly;
                            List<? extends Number> numbers;

                            @Override
                            public String inherited(String value) {
                                return value;
                            }

                            @Override
                            public void one() {
                            }

                            @Override
                            public void two() {
                            }

                            @Override
                            public void duplicate() {
                            }

                            public List<String> strings() {
                                return List.of();
                            }

                            public void acceptsList(List<String> input, T value) {
                            }

                            protected class ProtectedNested {
                            }

                            private class PrivateNested {
                            }
                        }
                        """)
        };
    }

    private static Source[] basicProcessorSources() {
        return new Source[] {
                new Source("step.Target", """
                        package step;

                        @interface Marker {
                        }

                        @interface Other {
                        }

                        @Marker
                        public class Target {
                            @Other String value;
                        }
                        """)
        };
    }

    private static Source[] equivalenceSources() {
        return new Source[] {
                new Source("equivalent.First", """
                        package equivalent;

                        @interface Nested {
                            String value();
                        }

                        @interface Label {
                            String name();
                            int[] numbers();
                            Nested nested();
                        }

                        @Label(name = "same", numbers = {3, 5}, nested = @Nested("deep"))
                        public class First {
                        }
                        """),
                new Source("equivalent.Second", """
                        package equivalent;

                        @Label(name = "same", numbers = {3, 5}, nested = @Nested("deep"))
                        public class Second {
                        }
                        """),
                new Source("equivalent.Different", """
                        package equivalent;

                        @Label(name = "different", numbers = {3, 8}, nested = @Nested("other"))
                        public class Different {
                        }
                        """)
        };
    }

    private static CompilationResult compileScenario(String scenario, Source... sources) {
        if (System.getProperty("java.home") == null) {
            return compileInExternalJvm(scenario, sources);
        }
        ensureJavaHomeProperty();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return compileInExternalJvm(scenario, sources);
        }

        CompilationResult result = compileInProcess(compiler, scenario, sources);
        if (needsExternalCompiler(result)) {
            return compileInExternalJvm(scenario, sources);
        }
        return result;
    }

    private static CompilationResult compileInProcess(JavaCompiler compiler, String scenario, Source... sources) {
        RecordingProcessorAccess processor = processorForScenario(scenario);
        try {
            Path tempDirectory = Files.createTempDirectory("auto-common-test");
            Path classOutput = tempDirectory.resolve("classes");
            Files.createDirectories(classOutput);
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            List<String> options = new ArrayList<>();
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
                task.setProcessors(List.of(processor));
                Boolean successful = task.call();
                return new CompilationResult(
                        Boolean.TRUE.equals(successful),
                        diagnosticText(diagnostics.getDiagnostics()),
                        processor.records());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnostics().contains("Unable to find package java.lang in platform classes");
    }

    private static CompilationResult compileInExternalJvm(String scenario, Source... sources) {
        try {
            Path tempDirectory = Files.createTempDirectory("auto-common-test-external");
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
            command.add(Auto_commonTest.class.getName());
            command.add("compile-helper");
            command.add(scenario);
            command.add(outputFile.toString());
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
                Properties records = new Properties();
                return new CompilationResult(false, "External javac process timed out", records);
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                Properties records = new Properties();
                return new CompilationResult(false, processOutput, records);
            }
            Properties properties = new Properties();
            try (java.io.InputStream inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return compilationResultFromProperties(properties);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
        }
    }

    private static void runCompileHelper(String[] args) throws IOException {
        int position = 1;
        String scenario = args[position++];
        Path outputFile = Path.of(args[position++]);
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
            result = new CompilationResult(false, "System Java compiler is not available", new Properties());
        } else {
            result = compileInProcess(compiler, scenario, sources.toArray(Source[]::new));
        }
        Properties properties = propertiesFromCompilationResult(result);
        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            properties.store(outputStream, null);
        }
    }

    private static RecordingProcessorAccess processorForScenario(String scenario) {
        if ("model-utilities".equals(scenario)) {
            return new ModelUtilitiesProcessor();
        }
        if ("basic-processor".equals(scenario)) {
            return new BasicProcessorHarness();
        }
        if ("equivalence".equals(scenario)) {
            return new EquivalenceProcessor();
        }
        throw new IllegalArgumentException("Unknown compiler scenario: " + scenario);
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
        addCachedArtifacts(entries, "com.google.auto", "auto-common");
        addCachedArtifacts(entries, "com.google.guava", "guava");
        addCachedArtifacts(entries, "com.google.guava", "failureaccess");
        addCachedArtifacts(entries, "com.google.guava", "listenablefuture");
        addCachedArtifacts(entries, "com.google.code.findbugs", "jsr305");
        addCachedArtifacts(entries, "org.checkerframework", "checker-qual");
        addCachedArtifacts(entries, "com.google.errorprone", "error_prone_annotations");
        addCachedArtifacts(entries, "com.google.j2objc", "j2objc-annotations");
        addCachedArtifacts(entries, "com.squareup", "javapoet");
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
        try (Stream<Path> paths = Files.walk(artifactCache, 4)) {
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

    private static Properties propertiesFromCompilationResult(CompilationResult result) {
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnostics());
        for (String key : result.records().stringPropertyNames()) {
            properties.setProperty("record." + key, result.records().getProperty(key));
        }
        return properties;
    }

    private static CompilationResult compilationResultFromProperties(Properties properties) {
        Properties records = new Properties();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("record.")) {
                records.setProperty(key.substring("record.".length()), properties.getProperty(key));
            }
        }
        return new CompilationResult(
                Boolean.parseBoolean(properties.getProperty("successful")),
                properties.getProperty("diagnostics", ""),
                records);
    }

    private abstract static class RecordingProcessor extends AbstractProcessor implements RecordingProcessorAccess {
        private final Properties records = new Properties();

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public final Properties records() {
            return records;
        }

        final void record(String key, Object value) {
            records.setProperty(key, String.valueOf(value));
        }
    }

    private static final class ModelUtilitiesProcessor extends RecordingProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("sample.Info");
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver() || annotations.isEmpty()) {
                return false;
            }
            Elements elements = processingEnv.getElementUtils();
            Types types = processingEnv.getTypeUtils();
            TypeElement subject = elements.getTypeElement("sample.Subject");
            TypeElement info = elements.getTypeElement("sample.Info");
            TypeElement stereotype = elements.getTypeElement("sample.Stereotype");
            AnnotationMirror infoMirror = MoreElements.getAnnotationMirror(subject, info).get();

            recordAnnotationUtilities(elements, subject, info, stereotype, infoMirror);
            recordElementUtilities(subject);
            recordTypeUtilities(elements, types, subject);
            recordGeneratedAnnotationUtilities(elements);
            return false;
        }

        private void recordAnnotationUtilities(
                Elements elements,
                TypeElement subject,
                TypeElement info,
                TypeElement stereotype,
                AnnotationMirror infoMirror) {
            record("package", MoreElements.getPackage(subject).getQualifiedName());
            record("annotation.present", MoreElements.isAnnotationPresent(subject, info)
                    + ":" + MoreElements.isAnnotationPresent(subject, "sample.Info")
                    + ":" + MoreElements.getAnnotationMirror(subject, "sample.Info").isPresent());
            String annotatedByType = joinAnnotationNames(
                    AnnotationMirrors.getAnnotatedAnnotations(subject, stereotype));
            String annotatedByName = joinAnnotationNames(
                    AnnotationMirrors.getAnnotatedAnnotations(subject, "sample.Stereotype"));
            record("annotated.annotations", annotatedByType + "|" + annotatedByName);

            AnnotationValue name = AnnotationMirrors.getAnnotationValue(infoMirror, "name");
            AnnotationValue count = AnnotationMirrors.getAnnotationValue(infoMirror, "count");
            Map.Entry<ExecutableElement, AnnotationValue> countEntry = AnnotationMirrors.getAnnotationElementAndValue(
                    infoMirror, "count");
            record("annotation.name.count", AnnotationValues.getString(name)
                    + ":" + AnnotationValues.getInt(count)
                    + ":" + MoreElements.asType(countEntry.getKey().getEnclosingElement()).getQualifiedName()
                    + "." + countEntry.getKey().getSimpleName()
                    + ":" + AnnotationMirrors.getAnnotationValuesWithDefaults(infoMirror).size());
            record("annotation.scalars", AnnotationValues.getLong(value(infoMirror, "longValue"))
                    + ":" + AnnotationValues.getByte(value(infoMirror, "byteValue"))
                    + ":" + AnnotationValues.getShort(value(infoMirror, "shortValue"))
                    + ":" + AnnotationValues.getFloat(value(infoMirror, "floatValue"))
                    + ":" + AnnotationValues.getDouble(value(infoMirror, "doubleValue"))
                    + ":" + AnnotationValues.getBoolean(value(infoMirror, "enabled"))
                    + ":" + AnnotationValues.getChar(value(infoMirror, "letter")));
            record("annotation.complex", AnnotationValues.getTypeMirror(value(infoMirror, "type"))
                    + ":" + AnnotationValues.getEnum(value(infoMirror, "mode")).getSimpleName()
                    + ":" + AnnotationValues.toString(value(infoMirror, "nested"))
                    + ":" + AnnotationValues.getStrings(value(infoMirror, "tags"))
                    + ":" + AnnotationValues.getInts(value(infoMirror, "numbers"))
                    + ":" + AnnotationValues.getTypeMirrors(value(infoMirror, "types"))
                    + ":" + AnnotationValues.toString(value(infoMirror, "types"))
                    + ":" + simpleNames(AnnotationValues.getEnums(value(infoMirror, "modes")))
                    + ":" + AnnotationValues.getAnnotationMirrors(value(infoMirror, "nestedArray"))
                    + ":" + AnnotationValues.getAnnotationValues(value(infoMirror, "tags"))
                    + ":" + AnnotationMirrors.toString(infoMirror));

            AnnotationMirror simpleInfo = SimpleAnnotationMirror.of(info, ImmutableMap.of("name", name));
            AnnotationMirror simpleStereotype = SimpleAnnotationMirror.of(stereotype);
            record("simple.annotation", simpleInfo + ":" + simpleInfo.getAnnotationType().asElement()
                    + ":" + simpleStereotype.equals(SimpleAnnotationMirror.of(stereotype))
                    + ":" + AnnotationMirrors.getAnnotationValuesWithDefaults(simpleInfo).size());
        }

        private void recordElementUtilities(TypeElement subject) {
            VariableElement field = fields(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("field"))
                    .findFirst()
                    .orElseThrow();
            ExecutableElement acceptsList = methods(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("acceptsList"))
                    .findFirst()
                    .orElseThrow();
            TypeElement protectedNested = nestedType(subject, "ProtectedNested");
            TypeElement privateNested = nestedType(subject, "PrivateNested");

            record("elements", "type=" + MoreElements.isType(subject)
                    + ":typeParam=" + MoreElements.asTypeParameter(subject.getTypeParameters().get(0)).getSimpleName()
                    + ":field=" + MoreElements.asVariable(field).getSimpleName()
                    + ":method=" + MoreElements.asExecutable(acceptsList).getSimpleName()
                    + ":publicPredicate=" + MoreElements.hasModifiers(Modifier.PUBLIC).apply(subject));
            record("visibility", Visibility.ofElement(subject)
                    + ":" + Visibility.effectiveVisibilityOfElement(protectedNested)
                    + ":" + Visibility.effectiveVisibilityOfElement(privateNested));
        }

        private void recordTypeUtilities(Elements elements, Types types, TypeElement subject) {
            DeclaredType subjectType = MoreTypes.asDeclared(subject.asType());
            VariableElement field = fields(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("field"))
                    .findFirst()
                    .orElseThrow();
            VariableElement numbers = fields(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("numbers"))
                    .findFirst()
                    .orElseThrow();
            WildcardType numberWildcard = MoreTypes.asWildcard(
                    MoreTypes.asDeclared(numbers.asType()).getTypeArguments().get(0));
            TypeVariable typeVariable = MoreTypes.asTypeVariable(subject.getTypeParameters().get(0).asType());
            ExecutableElement acceptsList = methods(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("acceptsList"))
                    .findFirst()
                    .orElseThrow();
            TypeMirror stringList = acceptsList.getParameters().get(0).asType();
            TypeMirror wildcardOnly = fields(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("wildcardOnly"))
                    .findFirst()
                    .orElseThrow()
                    .asType();

            AnnotationValue simpleStringType = SimpleTypeAnnotationValue.of(
                    elements.getTypeElement("java.lang.String").asType());
            record("simple.type.value", simpleStringType + ":" + simpleStringType.getValue());
            record("type.casts", "declared=" + MoreTypes.asTypeElement(subjectType).getQualifiedName()
                    + ":array=" + MoreTypes.asArray(types.getArrayType(subject.asType()))
                    + ":primitive=" + MoreTypes.asPrimitiveType(types.getPrimitiveType(TypeKind.INT)).getKind()
                    + ":void=" + MoreTypes.asNoType(types.getNoType(TypeKind.VOID)).getKind()
                    + ":typeVariable=" + MoreTypes.asElement(typeVariable.asElement().asType())
                    + ":executable=" + MoreTypes.asExecutable(acceptsList.asType()).getParameterTypes().size()
                    + ":wildcard=" + numberWildcard
                    + ":null=" + MoreTypes.asNullType(types.getNullType()).getKind()
                    + ":intersection=" + MoreTypes.asIntersection(typeVariable.getUpperBound()));
            record("type.predicates", MoreTypes.isType(subjectType)
                    + ":" + MoreTypes.isTypeOf(String.class, elements.getTypeElement("java.lang.String").asType())
                    + ":" + MoreTypes.isTypeOf(int.class, types.getPrimitiveType(TypeKind.INT))
                    + ":" + MoreTypes.isTypeOf(int[].class, types.getArrayType(types.getPrimitiveType(TypeKind.INT)))
                    + ":" + MoreTypes.isTypeOf(String.class, subject.asType()));
            record("referenced.types", sortedQualifiedNames(MoreTypes.referencedTypes(stringList))
                    + ":" + sortedQualifiedNames(MoreTypes.referencedTypes(numberWildcard)));

            com.google.common.base.Optional<DeclaredType> superclass = MoreTypes.nonObjectSuperclass(
                    types, elements, subjectType);
            ExecutableElement parentInherited = methods(MoreTypes.asTypeElement(superclass.get())).stream()
                    .filter(element -> element.getSimpleName().contentEquals("inherited"))
                    .findFirst()
                    .orElseThrow();
            record("super.member", superclass.get()
                    + ":" + MoreTypes.asMemberOf(types, superclass.get(), parentInherited.getParameters().get(0)));

            ImmutableSet<ExecutableElement> localAndInherited = MoreElements.getLocalAndInheritedMethods(
                    subject, types, elements);
            ExecutableElement subjectInherited = methods(subject).stream()
                    .filter(element -> element.getSimpleName().contentEquals("inherited"))
                    .findFirst()
                    .orElseThrow();
            record("methods", sortedMethodNames(localAndInherited));
            record("override.validation", MoreElements.overrides(subjectInherited, parentInherited, subject, types)
                    + ":" + MoreTypes.equivalence().equivalent(subject.asType(), subject.asType())
                    + ":" + SuperficialValidation.validateElement(subject)
                    + ":" + SuperficialValidation.validateElements(List.of(field)));
            record("unchecked.conversions", MoreTypes.isConversionFromObjectUnchecked(stringList)
                    + ":" + MoreTypes.isConversionFromObjectUnchecked(wildcardOnly)
                    + ":" + MoreTypes.isConversionFromObjectUnchecked(field.asType()));
        }

        private void recordGeneratedAnnotationUtilities(Elements elements) {
            java.util.Optional<TypeElement> generated = GeneratedAnnotations.generatedAnnotation(
                    elements, SourceVersion.latestSupported());
            record("generated.annotation", generated.map(TypeElement::getQualifiedName).orElseThrow());
            AnnotationSpec annotationSpec = GeneratedAnnotationSpecs.generatedAnnotationSpec(
                    elements, SourceVersion.latestSupported(), Auto_commonTest.class, "auto-common")
                    .orElseThrow();
            record("generated.spec", annotationSpec);
        }
    }

    private static final class EquivalenceProcessor extends RecordingProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("equivalent.Label");
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver() || annotations.isEmpty()) {
                return false;
            }
            Elements elements = processingEnv.getElementUtils();
            AnnotationMirror first = labelAnnotation(elements, "equivalent.First");
            AnnotationMirror second = labelAnnotation(elements, "equivalent.Second");
            AnnotationMirror different = labelAnnotation(elements, "equivalent.Different");

            Equivalence<AnnotationMirror> mirrorEquivalence = AnnotationMirrors.equivalence();
            Equivalence<AnnotationValue> valueEquivalence = AnnotationValues.equivalence();
            AnnotationValue firstName = value(first, "name");
            AnnotationValue secondName = value(second, "name");
            AnnotationValue differentName = value(different, "name");
            AnnotationValue firstNumbers = value(first, "numbers");
            AnnotationValue secondNumbers = value(second, "numbers");
            AnnotationValue firstNested = value(first, "nested");
            AnnotationValue secondNested = value(second, "nested");

            record("equivalence.mirrors", mirrorEquivalence.equivalent(first, second)
                    + ":" + mirrorEquivalence.equivalent(first, different)
                    + ":" + (mirrorEquivalence.hash(first) == mirrorEquivalence.hash(second)));
            record("equivalence.values", valueEquivalence.equivalent(firstName, secondName)
                    + ":" + valueEquivalence.equivalent(firstName, differentName)
                    + ":" + valueEquivalence.equivalent(firstNumbers, secondNumbers)
                    + ":" + valueEquivalence.equivalent(firstNested, secondNested));
            record("equivalence.nested", AnnotationMirrors.toString(AnnotationValues.getAnnotationMirror(firstNested)));
            return false;
        }

        private AnnotationMirror labelAnnotation(Elements elements, String className) {
            TypeElement type = elements.getTypeElement(className);
            TypeElement label = elements.getTypeElement("equivalent.Label");
            return MoreElements.getAnnotationMirror(type, label).get();
        }
    }

    private static final class BasicProcessorHarness extends BasicAnnotationProcessor
            implements RecordingProcessorAccess {
        private final Properties records = new Properties();
        private int postRounds;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        protected Iterable<? extends Step> steps() {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("step.Marker", "step.Other");
                }

                @Override
                public Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation) {
                    records.setProperty("basic.keys", String.join(",", elementsByAnnotation.keySet()));
                    StringJoiner joiner = new StringJoiner(",");
                    for (Map.Entry<String, Element> entry : elementsByAnnotation.entries()) {
                        joiner.add(entry.getKey() + "=" + entry.getValue().getSimpleName());
                    }
                    records.setProperty("basic.elements", joiner.toString());
                    return Set.of();
                }
            });
        }

        @Override
        protected void postRound(RoundEnvironment roundEnv) {
            postRounds++;
            records.setProperty("basic.postRounds", Integer.toString(postRounds));
        }

        @Override
        public Properties records() {
            return records;
        }
    }

    private interface RecordingProcessorAccess extends Processor {
        Properties records();
    }

    private static AnnotationValue value(AnnotationMirror annotationMirror, String name) {
        return AnnotationMirrors.getAnnotationValue(annotationMirror, name);
    }

    private static List<VariableElement> fields(TypeElement type) {
        return ElementFilter.fieldsIn(type.getEnclosedElements());
    }

    private static List<ExecutableElement> methods(TypeElement type) {
        return ElementFilter.methodsIn(type.getEnclosedElements());
    }

    private static TypeElement nestedType(TypeElement owner, String simpleName) {
        return ElementFilter.typesIn(owner.getEnclosedElements()).stream()
                .filter(element -> element.getSimpleName().contentEquals(simpleName))
                .findFirst()
                .orElseThrow();
    }

    private static String joinAnnotationNames(Iterable<? extends AnnotationMirror> annotations) {
        List<String> names = new ArrayList<>();
        for (AnnotationMirror annotation : annotations) {
            names.add(MoreTypes.asTypeElement(annotation.getAnnotationType()).getQualifiedName().toString());
        }
        names.sort(Comparator.naturalOrder());
        return String.join(",", names);
    }

    private static String simpleNames(Iterable<? extends VariableElement> elements) {
        List<String> names = new ArrayList<>();
        for (VariableElement element : elements) {
            names.add(element.getSimpleName().toString());
        }
        return names.toString();
    }

    private static String sortedQualifiedNames(Iterable<? extends TypeElement> elements) {
        List<String> names = new ArrayList<>();
        for (TypeElement element : elements) {
            names.add(element.getQualifiedName().toString());
        }
        names.sort(Comparator.naturalOrder());
        return names.toString();
    }

    private static String sortedMethodNames(Iterable<? extends ExecutableElement> methods) {
        List<String> names = new ArrayList<>();
        for (ExecutableElement method : methods) {
            if (!method.getEnclosingElement().getKind().equals(ElementKind.ENUM)) {
                names.add(method.getSimpleName().toString());
            }
        }
        names.sort(Comparator.naturalOrder());
        return names.toString();
    }

    private record Source(String className, String source) {
    }

    private record CompilationResult(boolean successful, String diagnostics, Properties records) {
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        SourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
