/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto.auto_common;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
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
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

public class Auto_commonTest {
    private static final long EXTERNAL_COMPILER_TIMEOUT_SECONDS = 30;

    @Test
    void annotationMirrorsExposeDefaultsTypedValuesAndSourceForm() throws IOException {
        compile("test.Subject", """
                package test;

                import java.lang.annotation.Documented;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                import java.util.Map;

                @Documented
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                @interface Marker {}

                enum Level { LOW, HIGH }

                @interface Nested {
                    String value();
                }

                @interface Sample {
                    String name() default "default";
                    int[] numbers() default {1, 2};
                    boolean enabled() default true;
                    char initial() default 'A';
                    double ratio() default 2.5;
                    long large() default 7L;
                    String[] tags() default {"red", "blue"};
                    Class<?> type();
                    Class<?>[] types() default {String.class, Integer.class};
                    Level level() default Level.HIGH;
                    Level[] levels() default {Level.LOW, Level.HIGH};
                    Nested nested() default @Nested("inner");
                }

                @Marker
                @Sample(type = Map.class, name = "custom", numbers = {3, 5})
                final class Subject<T extends Number> {
                    @Deprecated public String field;
                    public void method(String value) {}
                }
                """, new AnnotationModelProcessor());
    }

    @Test
    void elementUtilitiesTraversePackagesMembersOverridesAndVisibility() throws IOException {
        compile("test.Child", """
                package test;

                @interface Flag {}

                interface Named {
                    void inherited();
                }

                class Parent {
                    public void inherited() {}
                    protected void protectedOnly() {}
                    private void hidden() {}
                    public static void utility() {}
                }

                @Flag
                public class Child<T> extends Parent implements Named {
                    @Override public void inherited() {}
                    private String secret;
                    protected class ProtectedInner {}
                }

                class PackagePrivate {
                    public void visibleOnlyByPackage() {}
                }
                """, new ElementModelProcessor());
    }

    @Test
    void typeUtilitiesResolveDeclaredGenericWildcardExecutableAndMemberTypes() throws IOException {
        compile("test.Holder", """
                package test;

                import java.util.List;
                import java.util.Map;

                class Base<T> {
                    T value;
                    T convert(T input) throws Exception { return input; }
                }

                class Middle extends Base<String> {}

                class Holder<U extends Number & Comparable<U>> {
                    List<String>[] names;
                    List<?> wildcards;
                    List<? super String> lower;
                    Map<String, Integer> map;
                    int count;
                    void consume(U number, List<String> strings) {}
                }
                """, new TypeModelProcessor());
    }

    @Test
    void generatedAnnotationsAndJavaPoetSpecsUseRequestedSourceVersion() throws IOException {
        compile("test.Empty", """
                package test;

                final class Empty {}
                """, new GeneratedAnnotationProcessor());
    }

    @Test
    void basicAnnotationProcessorDispatchesAnnotatedElementsByAnnotationName() throws IOException {
        TrackingBasicProcessor processor = new TrackingBasicProcessor();
        compile("test.One", """
                package test;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @Target({ElementType.TYPE, ElementType.METHOD})
                @interface Track {}

                @Track
                final class One {
                    @Track void marked() {}
                    void unmarked() {}
                }
                """, processor);
    }

    @Test
    void immutableCollectorsProduceExpectedGuavaCollectionTypes() {
        ImmutableList<String> list = Arrays.stream(new String[] {"alpha", "beta"})
                .collect(MoreStreams.toImmutableList());
        ImmutableSet<Integer> set = Arrays.stream(new Integer[] {1, 1, 2})
                .collect(MoreStreams.toImmutableSet());
        ImmutableMap<String, Integer> map = list.stream()
                .collect(MoreStreams.toImmutableMap(value -> value.substring(0, 1), String::length));
        ImmutableBiMap<String, Integer> biMap = list.stream()
                .collect(MoreStreams.toImmutableBiMap(value -> value, String::length));

        assertThat(list).containsExactly("alpha", "beta");
        assertThat(set).containsExactlyInAnyOrder(1, 2);
        assertThat(map).containsEntry("a", 5).containsEntry("b", 4);
        assertThat(biMap.inverse()).containsEntry(5, "alpha").containsEntry(4, "beta");
    }

    private static void compile(String primaryClassName, String source, AbstractProcessor processor)
            throws IOException {
        CompilationResult result;
        if (System.getProperty("java.home") == null) {
            result = compileInExternalJvm(primaryClassName, source, processor);
        } else {
            result = compileInProcess(primaryClassName, source, processor);
            if (needsExternalCompiler(result)) {
                result = compileInExternalJvm(primaryClassName, source, processor);
            }
        }
        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
    }

    private static CompilationResult compileInProcess(
            String primaryClassName, String source, AbstractProcessor processor) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompilationResult(false, "No system Java compiler");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path classesDirectory = Files.createTempDirectory("auto-common-classes");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classesDirectory));
            JavaFileObject sourceFile = new InMemoryJavaFileObject(primaryClassName, source);
            List<String> options = List.of("-proc:only", "-implicit:none");
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, List.of(sourceFile));
            task.setProcessors(List.of(processor));

            Boolean successful = task.call();
            return new CompilationResult(
                    Boolean.TRUE.equals(successful), formatDiagnostics(diagnostics.getDiagnostics()));
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnosticText().contains("No system Java compiler")
                || result.diagnosticText().contains("Unable to find package java.lang in platform classes");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "compile-helper".equals(args[0])) {
            runCompileHelper(args);
        }
    }

    private static void runCompileHelper(String[] args) throws IOException {
        AbstractProcessor processor = processorForKind(args[1]);
        CompilationResult result = compileInProcess(
                args[2], Files.readString(Path.of(args[3]), StandardCharsets.UTF_8), processor);
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnosticText());
        try (OutputStream outputStream = Files.newOutputStream(Path.of(args[4]))) {
            properties.store(outputStream, null);
        }
    }

    private static CompilationResult compileInExternalJvm(
            String primaryClassName, String source, AbstractProcessor processor) {
        try {
            Path tempDirectory = Files.createTempDirectory("auto-common-compiler");
            Path sourceFile = tempDirectory.resolve("Source.java");
            Path outputFile = tempDirectory.resolve("result.properties");
            Path logFile = tempDirectory.resolve("compiler.log");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(externalClasspath());
            command.add(Auto_commonTest.class.getName());
            command.add("compile-helper");
            command.add(processorKind(processor));
            command.add(primaryClassName);
            command.add(sourceFile.toString());
            command.add(outputFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CompilationResult(false, "External javac process timed out");
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput);
            }

            Properties properties = new Properties();
            try (var inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            return new CompilationResult(
                    Boolean.parseBoolean(properties.getProperty("successful")),
                    properties.getProperty("diagnostics", "") + processOutput);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("External javac process was interrupted", e);
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
        addCachedArtifacts(entries, "com.google.auto", "auto-common");
        addCachedArtifacts(entries, "com.google.guava", "guava");
        addCachedArtifacts(entries, "com.google.guava", "failureaccess");
        addCachedArtifacts(entries, "com.squareup", "javapoet");
        addCachedArtifacts(entries, "org.assertj", "assertj-core");
        addCachedArtifacts(entries, "org.checkerframework", "checker-qual");
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
        if (processor instanceof AnnotationModelProcessor) {
            return "annotation";
        }
        if (processor instanceof ElementModelProcessor) {
            return "element";
        }
        if (processor instanceof TypeModelProcessor) {
            return "type";
        }
        if (processor instanceof GeneratedAnnotationProcessor) {
            return "generated";
        }
        if (processor instanceof TrackingBasicProcessor) {
            return "basic";
        }
        throw new AssertionError("Unknown processor: " + processor.getClass().getName());
    }

    private static AbstractProcessor processorForKind(String processorKind) {
        return switch (processorKind) {
            case "annotation" -> new AnnotationModelProcessor();
            case "element" -> new ElementModelProcessor();
            case "type" -> new TypeModelProcessor();
            case "generated" -> new GeneratedAnnotationProcessor();
            case "basic" -> new TrackingBasicProcessor();
            default -> throw new AssertionError("Unknown processor kind: " + processorKind);
        };
    }

    private static String formatDiagnostics(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder result = new StringBuilder("Compilation diagnostics:");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            result.append(System.lineSeparator())
                    .append(diagnostic.getKind())
                    .append(" line ")
                    .append(diagnostic.getLineNumber())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT));
        }
        return result.toString();
    }

    private record CompilationResult(boolean successful, String diagnosticText) {}

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private InMemoryJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private abstract static class InspectingProcessor extends AbstractProcessor {
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver()) {
                inspect(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
            }
            return false;
        }

        protected abstract void inspect(Elements elements, Types types);
    }

    private static final class AnnotationModelProcessor extends InspectingProcessor {
        @Override
        protected void inspect(Elements elements, Types types) {
            TypeElement subject = elements.getTypeElement("test.Subject");
            TypeElement markerType = elements.getTypeElement("test.Marker");
            TypeElement documentedType = elements.getTypeElement("java.lang.annotation.Documented");
            VariableElement field = ElementFilter.fieldsIn(subject.getEnclosedElements()).get(0);

            assertThat(MoreElements.isAnnotationPresent(subject, "test.Sample")).isTrue();
            assertThat(MoreElements.isAnnotationPresent(subject, markerType)).isTrue();
            assertThat(MoreElements.isAnnotationPresent(field, Deprecated.class)).isTrue();

            com.google.common.base.Optional<AnnotationMirror> sampleMirrorOptional =
                    MoreElements.getAnnotationMirror(subject, "test.Sample");
            assertThat(sampleMirrorOptional.isPresent()).isTrue();
            AnnotationMirror sampleMirror = sampleMirrorOptional.get();
            ImmutableMap<ExecutableElement, AnnotationValue> values =
                    AnnotationMirrors.getAnnotationValuesWithDefaults(sampleMirror);
            assertThat(values.keySet()).extracting(method -> method.getSimpleName().toString())
                    .containsExactly(
                            "name", "numbers", "enabled", "initial", "ratio", "large", "tags", "type", "types",
                            "level", "levels", "nested");

            assertThat(AnnotationValues.getString(AnnotationMirrors.getAnnotationValue(sampleMirror, "name")))
                    .isEqualTo("custom");
            assertThat(AnnotationValues.getInts(AnnotationMirrors.getAnnotationValue(sampleMirror, "numbers")))
                    .containsExactly(3, 5);
            assertThat(AnnotationValues.getBoolean(AnnotationMirrors.getAnnotationValue(sampleMirror, "enabled")))
                    .isTrue();
            assertThat(AnnotationValues.getChar(AnnotationMirrors.getAnnotationValue(sampleMirror, "initial")))
                    .isEqualTo('A');
            assertThat(AnnotationValues.getDouble(AnnotationMirrors.getAnnotationValue(sampleMirror, "ratio")))
                    .isEqualTo(2.5D);
            assertThat(AnnotationValues.getLong(AnnotationMirrors.getAnnotationValue(sampleMirror, "large")))
                    .isEqualTo(7L);
            assertThat(AnnotationValues.getStrings(AnnotationMirrors.getAnnotationValue(sampleMirror, "tags")))
                    .containsExactly("red", "blue");
            AnnotationValue typeAnnotationValue = AnnotationMirrors.getAnnotationValue(sampleMirror, "type");
            DeclaredType typeValue = AnnotationValues.getTypeMirror(typeAnnotationValue);
            assertThat(MoreTypes.asTypeElement(typeValue).getQualifiedName().toString())
                    .isEqualTo("java.util.Map");
            AnnotationValue simpleTypeValue = SimpleTypeAnnotationValue.of(typeValue);
            assertThat(AnnotationValues.getTypeMirror(simpleTypeValue).toString()).isEqualTo("java.util.Map");
            assertThat(AnnotationValues.equivalence().equivalent(simpleTypeValue, typeAnnotationValue)).isTrue();
            assertThat(AnnotationValues.getTypeMirrors(AnnotationMirrors.getAnnotationValue(sampleMirror, "types"))
                    .stream()
                    .map(type -> MoreTypes.asTypeElement(type).getQualifiedName().toString()))
                    .containsExactly("java.lang.String", "java.lang.Integer");
            AnnotationValue levelValue = AnnotationMirrors.getAnnotationValue(sampleMirror, "level");
            VariableElement level = AnnotationValues.getEnum(levelValue);
            assertThat(level.getSimpleName().toString()).isEqualTo("HIGH");
            assertThat(AnnotationValues.getEnums(AnnotationMirrors.getAnnotationValue(sampleMirror, "levels"))
                    .stream()
                    .map(enumValue -> enumValue.getSimpleName().toString()))
                    .containsExactly("LOW", "HIGH");
            AnnotationMirror nested = AnnotationValues.getAnnotationMirror(
                    AnnotationMirrors.getAnnotationValue(sampleMirror, "nested"));
            assertThat(AnnotationValues.getString(AnnotationMirrors.getAnnotationValue(nested, "value")))
                    .isEqualTo("inner");

            Map.Entry<ExecutableElement, AnnotationValue> nameEntry =
                    AnnotationMirrors.getAnnotationElementAndValue(sampleMirror, "name");
            assertThat(nameEntry.getKey().getSimpleName().toString()).isEqualTo("name");
            assertThat(AnnotationValues.toString(nameEntry.getValue())).contains("custom");
            assertThat(AnnotationMirrors.toString(sampleMirror))
                    .contains("@test.Sample")
                    .contains("java.util.Map.class")
                    .contains("3")
                    .contains("5");

            AnnotationMirror simpleMarker = SimpleAnnotationMirror.of(markerType);
            AnnotationMirror actualMarker = MoreElements.getAnnotationMirror(subject, markerType).get();
            assertThat(AnnotationMirrors.equivalence().equivalent(simpleMarker, actualMarker)).isTrue();
            assertThat(simpleMarker).isEqualTo(actualMarker);
            assertThat(AnnotationMirrors.getAnnotatedAnnotations(subject, documentedType).stream()
                    .map(annotation -> MoreTypes.asTypeElement(annotation.getAnnotationType())
                            .getQualifiedName().toString())
                    .collect(MoreStreams.toImmutableSet()))
                    .containsExactly("test.Marker");
            assertThat(AnnotationMirrors.getAnnotatedAnnotations(subject, "java.lang.annotation.Documented"))
                    .hasSize(1);
            assertThat(AnnotationMirrors.getAnnotatedAnnotations(subject, DocumentedForLookup.class)).isEmpty();
        }
    }

    private static final class ElementModelProcessor extends InspectingProcessor {
        @Override
        protected void inspect(Elements elements, Types types) {
            TypeElement child = elements.getTypeElement("test.Child");
            TypeElement parent = elements.getTypeElement("test.Parent");
            TypeElement packagePrivate = elements.getTypeElement("test.PackagePrivate");
            PackageElement packageElement = elements.getPackageElement("test");

            assertThat(MoreElements.getPackage(child)).isEqualTo(packageElement);
            assertThat(MoreElements.asPackage(packageElement)).isEqualTo(packageElement);
            assertThat(MoreElements.asType(child)).isEqualTo(child);
            assertThat(MoreElements.isType(child)).isTrue();
            TypeParameterElement typeParameter = child.getTypeParameters().get(0);
            assertThat(MoreElements.asTypeParameter(typeParameter)).isEqualTo(typeParameter);

            VariableElement secret = findField(child, "secret");
            ExecutableElement childInherited = findMethod(child, "inherited");
            ExecutableElement parentInherited = findMethod(parent, "inherited");
            ExecutableElement utility = findMethod(parent, "utility");
            assertThat(MoreElements.asVariable(secret)).isEqualTo(secret);
            assertThat(MoreElements.asExecutable(childInherited)).isEqualTo(childInherited);
            assertThat(MoreElements.hasModifiers(Modifier.PUBLIC).apply(childInherited)).isTrue();
            assertThat(MoreElements.hasModifiers(Set.of(Modifier.PUBLIC, Modifier.STATIC)).apply(utility)).isTrue();
            assertThat(MoreElements.overrides(childInherited, parentInherited, child, types)).isTrue();

            ImmutableSet<String> localAndInheritedMethodNames = MoreElements
                    .getLocalAndInheritedMethods(child, types, elements)
                    .stream()
                    .map(method -> method.getSimpleName().toString())
                    .collect(MoreStreams.toImmutableSet());
            assertThat(localAndInheritedMethodNames)
                    .contains("inherited", "protectedOnly")
                    .doesNotContain("hidden", "utility");

            ImmutableSet<String> allMethodNames = MoreElements.getAllMethods(child, types, elements)
                    .stream()
                    .map(method -> method.getSimpleName().toString())
                    .collect(MoreStreams.toImmutableSet());
            assertThat(allMethodNames).contains("inherited", "protectedOnly", "hidden", "utility");

            TypeElement protectedInner = ElementFilter.typesIn(child.getEnclosedElements()).get(0);
            ExecutableElement visibleByPackage = findMethod(packagePrivate, "visibleOnlyByPackage");
            assertThat(Visibility.ofElement(packageElement)).isEqualTo(Visibility.PUBLIC);
            assertThat(Visibility.ofElement(secret)).isEqualTo(Visibility.PRIVATE);
            assertThat(Visibility.ofElement(protectedInner)).isEqualTo(Visibility.PROTECTED);
            assertThat(Visibility.effectiveVisibilityOfElement(secret)).isEqualTo(Visibility.PRIVATE);
            assertThat(Visibility.effectiveVisibilityOfElement(visibleByPackage)).isEqualTo(Visibility.DEFAULT);

            assertThat(SuperficialValidation.validateElement(child)).isTrue();
            assertThat(SuperficialValidation.validateElements(List.of(child, parent, packagePrivate))).isTrue();
            assertThat(SuperficialValidation.validateType(child.asType())).isTrue();
        }
    }

    private static final class TypeModelProcessor extends InspectingProcessor {
        @Override
        protected void inspect(Elements elements, Types types) {
            TypeElement holder = elements.getTypeElement("test.Holder");
            TypeElement middle = elements.getTypeElement("test.Middle");
            TypeElement base = elements.getTypeElement("test.Base");
            VariableElement names = findField(holder, "names");
            VariableElement wildcards = findField(holder, "wildcards");
            VariableElement lower = findField(holder, "lower");
            VariableElement map = findField(holder, "map");
            VariableElement count = findField(holder, "count");
            ExecutableElement consume = findMethod(holder, "consume");
            ExecutableElement convert = findMethod(base, "convert");
            VariableElement baseValue = findField(base, "value");

            ArrayType namesArray = MoreTypes.asArray(names.asType());
            DeclaredType listOfString = MoreTypes.asDeclared(namesArray.getComponentType());
            assertThat(MoreTypes.asElement(listOfString).getSimpleName().toString()).isEqualTo("List");
            assertThat(MoreTypes.asTypeElement(listOfString).getQualifiedName().toString())
                    .isEqualTo("java.util.List");
            assertThat(MoreTypes.isType(names.asType())).isTrue();
            assertThat(MoreTypes.isTypeOf(java.util.List[].class, names.asType())).isTrue();
            assertThat(MoreTypes.isTypeOf(String.class, listOfString.getTypeArguments().get(0))).isTrue();
            assertThat(MoreTypes.referencedTypes(names.asType()).stream()
                    .map(type -> type.getQualifiedName().toString())
                    .collect(MoreStreams.toImmutableSet()))
                    .contains("java.util.List", "java.lang.String");
            assertThat(MoreTypes.asTypeElements(List.of(listOfString.getTypeArguments().get(0))).stream()
                    .map(type -> type.getQualifiedName().toString()))
                    .containsExactly("java.lang.String");

            TypeVariable typeVariable = MoreTypes.asTypeVariable(holder.getTypeParameters().get(0).asType());
            IntersectionType intersection = MoreTypes.asIntersection(typeVariable.getUpperBound());
            assertThat(intersection.getBounds()).hasSize(2);
            assertThat(MoreTypes.isType(typeVariable)).isFalse();
            PrimitiveType primitive = MoreTypes.asPrimitiveType(count.asType());
            assertThat(MoreTypes.isTypeOf(int.class, primitive)).isTrue();
            ExecutableType executable = MoreTypes.asExecutable(consume.asType());
            NoType noType = MoreTypes.asNoType(executable.getReturnType());
            assertThat(MoreTypes.isTypeOf(void.class, noType)).isTrue();
            NullType nullType = MoreTypes.asNullType(types.getNullType());
            assertThat(nullType.toString()).isEqualTo("<nulltype>");

            DeclaredType wildcardList = MoreTypes.asDeclared(wildcards.asType());
            WildcardType wildcard = MoreTypes.asWildcard(wildcardList.getTypeArguments().get(0));
            assertThat(wildcard.getSuperBound()).isNull();
            DeclaredType lowerList = MoreTypes.asDeclared(lower.asType());
            WildcardType lowerWildcard = MoreTypes.asWildcard(lowerList.getTypeArguments().get(0));
            assertThat(lowerWildcard.getSuperBound().toString()).isEqualTo("java.lang.String");
            assertThat(MoreTypes.isConversionFromObjectUnchecked(listOfString)).isTrue();
            assertThat(MoreTypes.isConversionFromObjectUnchecked(wildcardList)).isFalse();
            assertThat(MoreTypes.isConversionFromObjectUnchecked(lowerList)).isTrue();
            assertThat(MoreTypes.isConversionFromObjectUnchecked(typeVariable)).isTrue();

            DeclaredType middleType = MoreTypes.asDeclared(middle.asType());
            com.google.common.base.Optional<DeclaredType> superclass =
                    MoreTypes.nonObjectSuperclass(types, elements, middleType);
            assertThat(superclass.isPresent()).isTrue();
            assertThat(superclass.get().toString()).isEqualTo("test.Base<java.lang.String>");
            TypeMirror resolvedField = MoreTypes.asMemberOf(types, superclass.get(), baseValue);
            VariableElement inputParameter = convert.getParameters().get(0);
            TypeMirror resolvedParameter = MoreTypes.asMemberOf(types, superclass.get(), inputParameter);
            assertThat(resolvedField.toString()).isEqualTo("java.lang.String");
            assertThat(resolvedParameter.toString()).isEqualTo("java.lang.String");
            assertThat(MoreTypes.equivalence().equivalent(resolvedField, resolvedParameter)).isTrue();
            int fieldHash = MoreTypes.equivalence().hash(resolvedField);
            int parameterHash = MoreTypes.equivalence().hash(resolvedParameter);
            assertThat(fieldHash).isEqualTo(parameterHash);
            assertThat(MoreTypes.equivalence().equivalent(listOfString, wildcardList)).isFalse();
            assertThat(MoreTypes.nonObjectSuperclass(types, elements, MoreTypes.asDeclared(
                    elements.getTypeElement("java.lang.Object").asType())).isPresent()).isFalse();

            DeclaredType mapType = MoreTypes.asDeclared(map.asType());
            assertThat(MoreTypes.referencedTypes(mapType).stream()
                    .map(type -> type.getQualifiedName().toString())
                    .collect(MoreStreams.toImmutableSet()))
                    .contains("java.util.Map", "java.lang.String", "java.lang.Integer");
        }
    }

    private static final class GeneratedAnnotationProcessor extends InspectingProcessor {
        @Override
        protected void inspect(Elements elements, Types types) {
            Optional<TypeElement> generated = GeneratedAnnotations.generatedAnnotation(
                    elements, SourceVersion.RELEASE_9);
            assertThat(generated).isPresent();
            assertThat(generated.get().getQualifiedName().toString())
                    .isEqualTo("javax.annotation.processing.Generated");

            Optional<AnnotationSpec> spec = GeneratedAnnotationSpecs.generatedAnnotationSpec(
                    elements, SourceVersion.RELEASE_9, Auto_commonTest.class, "created during tests");
            assertThat(spec).isPresent();
            assertThat(spec.get().toString())
                    .contains("@javax.annotation.processing.Generated")
                    .contains("com_google_auto.auto_common.Auto_commonTest")
                    .contains("created during tests");
        }
    }

    private static final class TrackingBasicProcessor extends BasicAnnotationProcessor {
        private final List<String> processedElements = new ArrayList<>();
        private boolean seenProcessingOverRound;
        private int normalRoundCount;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        protected Iterable<? extends Step> steps() {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("test.Track");
                }

                @Override
                public Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation) {
                    for (Element element : elementsByAnnotation.get("test.Track")) {
                        if (element instanceof TypeElement) {
                            processedElements.add(((TypeElement) element).getQualifiedName().toString());
                        } else {
                            processedElements.add(element.getSimpleName().toString());
                        }
                    }
                    return Set.of();
                }
            });
        }

        @Override
        protected void postRound(RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                seenProcessingOverRound = true;
                assertThat(processedElements).containsExactlyInAnyOrder("test.One", "marked");
                assertThat(seenProcessingOverRound).isTrue();
                assertThat(normalRoundCount).isGreaterThanOrEqualTo(1);
            } else {
                normalRoundCount++;
            }
        }
    }

    private static VariableElement findField(TypeElement type, String name) {
        return ElementFilter.fieldsIn(type.getEnclosedElements()).stream()
                .filter(field -> field.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field: " + name));
    }

    private static ExecutableElement findMethod(TypeElement type, String name) {
        return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + name));
    }

    private @interface DocumentedForLookup {}
}
