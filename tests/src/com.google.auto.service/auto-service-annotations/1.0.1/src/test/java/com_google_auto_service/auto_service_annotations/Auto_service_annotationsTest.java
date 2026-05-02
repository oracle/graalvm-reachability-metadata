/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_service.auto_service_annotations;

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
import java.util.HashSet;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Auto_service_annotationsTest {
    private static final String AUTO_SERVICE_TYPE = "com.google.auto.service.AutoService";
    private static final String LIST_SEPARATOR = "\u001F";
    private static final Duration EXTERNAL_COMPILER_TIMEOUT = Duration.ofSeconds(55);

    @Test
    void processorSeesServiceTypesInDeclarationOrder() {
        AutoServiceUseProcessor processor = new AutoServiceUseProcessor();

        CompilationResult result = compile("test.MultiService", """
                package test;

                import com.google.auto.service.AutoService;
                import java.util.concurrent.Callable;

                @AutoService({Runnable.class, Callable.class})
                final class MultiService implements Runnable, Callable<String> {
                    @Override
                    public void run() {
                    }

                    @Override
                    public String call() {
                        return "called";
                    }
                }
                """, processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(processor.recordsBySimpleName()).containsOnlyKeys("MultiService");
        assertThat(processor.recordsBySimpleName().get("MultiService").kind()).isEqualTo(ElementKind.CLASS);
        assertThat(processor.recordsBySimpleName().get("MultiService").serviceTypes())
                .containsExactly("java.lang.Runnable", "java.util.concurrent.Callable");
    }

    @Test
    void processorSeesExplicitEmptyServiceTypeArray() {
        AutoServiceUseProcessor processor = new AutoServiceUseProcessor();

        CompilationResult result = compile("test.EmptyServiceList", """
                package test;

                import com.google.auto.service.AutoService;

                @AutoService({})
                final class EmptyServiceList {
                }
                """, processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(processor.recordsBySimpleName()).containsOnlyKeys("EmptyServiceList");
        assertThat(processor.recordsBySimpleName().get("EmptyServiceList").serviceTypes()).isEmpty();
    }

    @Test
    void autoServiceCanAnnotateEveryJavaTypeKindAllowedByTypeTarget() {
        AutoServiceUseProcessor processor = new AutoServiceUseProcessor();

        CompilationResult result = compile("test.TypeKinds", """
                package test;

                import com.google.auto.service.AutoService;

                @AutoService(Runnable.class)
                interface RunnableService extends Runnable {
                }

                @AutoService(Enum.class)
                enum EnumService {
                    ONE
                }

                @AutoService(java.lang.annotation.Annotation.class)
                @interface AnnotationService {
                }
                """, processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(processor.recordsBySimpleName()).containsOnlyKeys(
                "RunnableService", "EnumService", "AnnotationService");
        assertThat(processor.recordsBySimpleName().get("RunnableService").kind()).isEqualTo(ElementKind.INTERFACE);
        assertThat(processor.recordsBySimpleName().get("RunnableService").serviceTypes())
                .containsExactly("java.lang.Runnable");
        assertThat(processor.recordsBySimpleName().get("EnumService").kind()).isEqualTo(ElementKind.ENUM);
        assertThat(processor.recordsBySimpleName().get("EnumService").serviceTypes())
                .containsExactly("java.lang.Enum");
        assertThat(processor.recordsBySimpleName().get("AnnotationService").kind())
                .isEqualTo(ElementKind.ANNOTATION_TYPE);
        assertThat(processor.recordsBySimpleName().get("AnnotationService").serviceTypes())
                .containsExactly("java.lang.annotation.Annotation");
    }

    @Test
    void processorDiscoversAutoServiceOnStaticMemberClass() {
        NestedAutoServiceUseProcessor processor = new NestedAutoServiceUseProcessor();

        CompilationResult result = compile("test.EnclosingServiceContainer", """
                package test;

                import com.google.auto.service.AutoService;

                final class EnclosingServiceContainer {
                    @AutoService(Runnable.class)
                    static final class NestedRunnableService implements Runnable {
                        @Override
                        public void run() {
                        }
                    }
                }
                """, processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        NestedAnnotationUse annotationUse = processor.annotationUse();
        assertThat(annotationUse.qualifiedName())
                .isEqualTo("test.EnclosingServiceContainer.NestedRunnableService");
        assertThat(annotationUse.enclosingQualifiedName()).isEqualTo("test.EnclosingServiceContainer");
        assertThat(annotationUse.nestingKind()).isEqualTo(NestingKind.MEMBER);
        assertThat(annotationUse.serviceTypes()).containsExactly("java.lang.Runnable");
    }

    @Test
    void processorCanReadServiceTypesThroughTypedAnnotationProxy() {
        AutoServiceTypedUseProcessor processor = new AutoServiceTypedUseProcessor();

        CompilationResult result = compile("test.TypedProxyService", """
                package test;

                import com.google.auto.service.AutoService;
                import java.util.Comparator;

                @AutoService(Comparator.class)
                final class TypedProxyService implements Comparator<String> {
                    @Override
                    public int compare(String first, String second) {
                        return first.compareTo(second);
                    }
                }
                """, processor);

        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(processor.serviceTypesBySimpleName())
                .containsOnlyKeys("TypedProxyService");
        assertThat(processor.serviceTypesBySimpleName().get("TypedProxyService"))
                .containsExactly("java.util.Comparator");
    }

    @Test
    void annotationTypePublishesExpectedCompileTimeContract() {
        AutoServiceContractProcessor processor = new AutoServiceContractProcessor();

        CompilationResult result = compile("test.ContractProbe", """
                package test;

                final class ContractProbe {
                }
                """, processor);

        AutoServiceContract contract = processor.contract();
        assertThat(result.successful()).as(result.diagnosticText()).isTrue();
        assertThat(contract.qualifiedName()).isEqualTo(AUTO_SERVICE_TYPE);
        assertThat(contract.annotationKind()).isEqualTo(ElementKind.ANNOTATION_TYPE);
        assertThat(contract.metaAnnotationTypes()).contains(
                "java.lang.annotation.Documented",
                "java.lang.annotation.Retention",
                "java.lang.annotation.Target");
        assertThat(contract.retentionPolicy()).isEqualTo("CLASS");
        assertThat(contract.targets()).containsExactly("TYPE");
        assertThat(contract.valueMethodReturnType()).isEqualTo("java.lang.Class<?>[]");
        assertThat(contract.valueMethodDefault()).isNull();
    }

    @Test
    void compilerRejectsMissingRequiredValue() {
        CompilationResult result = compile("test.MissingValue", """
                package test;

                import com.google.auto.service.AutoService;

                @AutoService
                final class MissingValue {
                }
                """, new NoopProcessor());

        assertThat(result.successful()).as(result.diagnosticText()).isFalse();
        assertThat(result.diagnosticText()).contains("value");
    }

    @Test
    void compilerRejectsUseOutsideTypeDeclarations() {
        CompilationResult result = compile("test.InvalidTarget", """
                package test;

                import com.google.auto.service.AutoService;

                final class InvalidTarget {
                    @AutoService(Runnable.class)
                    void method() {
                    }
                }
                """, new NoopProcessor());

        assertThat(result.successful()).as(result.diagnosticText()).isFalse();
        assertThat(result.diagnosticText()).contains("not applicable");
    }

    private static CompilationResult compile(String className, String source, AbstractProcessor processor) {
        if (System.getProperty("java.home") == null) {
            return compileInExternalJvm(className, source, processor);
        }
        ensureJavaHomeProperty();

        CompilationResult result = compileInProcess(className, source, processor);
        if (needsExternalCompiler(result)) {
            return compileInExternalJvm(className, source, processor);
        }
        return result;
    }

    private static CompilationResult compileInProcess(String className, String source, AbstractProcessor processor) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("system Java compiler").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = new ArrayList<>();
        options.add("-proc:only");
        options.add("-implicit:none");
        options.add("-classpath");
        options.add(System.getProperty("java.class.path", ""));

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(new SourceFile(className, source)));
            task.setProcessors(List.of(processor));
            Boolean successful = task.call();
            return new CompilationResult(Boolean.TRUE.equals(successful), diagnosticText(diagnostics.getDiagnostics()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean needsExternalCompiler(CompilationResult result) {
        return result.diagnosticText().contains("Unable to find package java.lang in platform classes");
    }

    private static CompilationResult compileInExternalJvm(String className, String source, AbstractProcessor processor) {
        try {
            Path tempDirectory = Files.createTempDirectory("auto-service-annotations-test");
            Path sourceFile = tempDirectory.resolve("Source.java");
            Path outputFile = tempDirectory.resolve("result.properties");
            Path logFile = tempDirectory.resolve("compiler.log");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(externalClasspath());
            command.add(Auto_service_annotationsTest.class.getName());
            command.add("compile-helper");
            command.add(processorKind(processor));
            command.add(className);
            command.add(sourceFile.toString());
            command.add(outputFile.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean completed = process.waitFor(EXTERNAL_COMPILER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CompilationResult(false, "External javac process timed out" + System.lineSeparator());
            }
            String processOutput = Files.readString(logFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new CompilationResult(false, processOutput);
            }

            Properties properties = new Properties();
            try (var inputStream = Files.newInputStream(outputFile)) {
                properties.load(inputStream);
            }
            applyProcessorOutput(processor, properties);
            return new CompilationResult(
                    Boolean.parseBoolean(properties.getProperty("successful")),
                    properties.getProperty("diagnostics", ""));
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
        AbstractProcessor processor = processorForKind(args[1]);
        CompilationResult result = compileInProcess(args[2], Files.readString(Path.of(args[3]), StandardCharsets.UTF_8), processor);
        Properties properties = new Properties();
        properties.setProperty("successful", Boolean.toString(result.successful()));
        properties.setProperty("diagnostics", result.diagnosticText());
        writeProcessorOutput(processor, properties);
        try (OutputStream outputStream = Files.newOutputStream(Path.of(args[4]))) {
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
        addCachedArtifacts(entries, "com.google.auto.service", "auto-service-annotations");
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
        if (processor instanceof AutoServiceUseProcessor) {
            return "use";
        }
        if (processor instanceof NestedAutoServiceUseProcessor) {
            return "nested";
        }
        if (processor instanceof AutoServiceTypedUseProcessor) {
            return "typed";
        }
        if (processor instanceof AutoServiceContractProcessor) {
            return "contract";
        }
        if (processor instanceof NoopProcessor) {
            return "noop";
        }
        throw new AssertionError("Unknown processor: " + processor.getClass().getName());
    }

    private static AbstractProcessor processorForKind(String processorKind) {
        return switch (processorKind) {
            case "use" -> new AutoServiceUseProcessor();
            case "nested" -> new NestedAutoServiceUseProcessor();
            case "typed" -> new AutoServiceTypedUseProcessor();
            case "contract" -> new AutoServiceContractProcessor();
            case "noop" -> new NoopProcessor();
            default -> throw new AssertionError("Unknown processor kind: " + processorKind);
        };
    }

    private static void writeProcessorOutput(AbstractProcessor processor, Properties properties) {
        if (processor instanceof AutoServiceUseProcessor useProcessor) {
            properties.setProperty("record.count", Integer.toString(useProcessor.recordsBySimpleName.size()));
            int index = 0;
            for (Map.Entry<String, AnnotationUse> entry : useProcessor.recordsBySimpleName.entrySet()) {
                properties.setProperty("record." + index + ".name", entry.getKey());
                properties.setProperty("record." + index + ".kind", entry.getValue().kind().name());
                properties.setProperty("record." + index + ".serviceTypes", joinList(entry.getValue().serviceTypes()));
                index++;
            }
        } else if (processor instanceof NestedAutoServiceUseProcessor nestedUseProcessor
                && nestedUseProcessor.annotationUse != null) {
            NestedAnnotationUse annotationUse = nestedUseProcessor.annotationUse;
            properties.setProperty("nested.qualifiedName", annotationUse.qualifiedName());
            properties.setProperty("nested.enclosingQualifiedName", annotationUse.enclosingQualifiedName());
            properties.setProperty("nested.nestingKind", annotationUse.nestingKind().name());
            properties.setProperty("nested.serviceTypes", joinList(annotationUse.serviceTypes()));
        } else if (processor instanceof AutoServiceTypedUseProcessor typedUseProcessor) {
            properties.setProperty("typed.count", Integer.toString(typedUseProcessor.serviceTypesBySimpleName.size()));
            int index = 0;
            for (Map.Entry<String, List<String>> entry : typedUseProcessor.serviceTypesBySimpleName.entrySet()) {
                properties.setProperty("typed." + index + ".name", entry.getKey());
                properties.setProperty("typed." + index + ".serviceTypes", joinList(entry.getValue()));
                index++;
            }
        } else if (processor instanceof AutoServiceContractProcessor contractProcessor && contractProcessor.contract != null) {
            AutoServiceContract contract = contractProcessor.contract;
            properties.setProperty("contract.qualifiedName", contract.qualifiedName());
            properties.setProperty("contract.annotationKind", contract.annotationKind().name());
            properties.setProperty("contract.metaAnnotationTypes", joinList(new ArrayList<>(contract.metaAnnotationTypes())));
            properties.setProperty("contract.retentionPolicy", contract.retentionPolicy());
            properties.setProperty("contract.targets", joinList(contract.targets()));
            properties.setProperty("contract.valueMethodReturnType", contract.valueMethodReturnType());
        }
    }

    private static void applyProcessorOutput(AbstractProcessor processor, Properties properties) {
        if (processor instanceof AutoServiceUseProcessor useProcessor) {
            int count = Integer.parseInt(properties.getProperty("record.count", "0"));
            useProcessor.recordsBySimpleName.clear();
            for (int index = 0; index < count; index++) {
                String name = properties.getProperty("record." + index + ".name");
                ElementKind kind = ElementKind.valueOf(properties.getProperty("record." + index + ".kind"));
                List<String> serviceTypes = splitList(properties.getProperty("record." + index + ".serviceTypes", ""));
                useProcessor.recordsBySimpleName.put(name, new AnnotationUse(kind, serviceTypes));
            }
        } else if (processor instanceof NestedAutoServiceUseProcessor nestedUseProcessor
                && properties.containsKey("nested.qualifiedName")) {
            nestedUseProcessor.annotationUse = new NestedAnnotationUse(
                    properties.getProperty("nested.qualifiedName"),
                    properties.getProperty("nested.enclosingQualifiedName"),
                    NestingKind.valueOf(properties.getProperty("nested.nestingKind")),
                    splitList(properties.getProperty("nested.serviceTypes", "")));
        } else if (processor instanceof AutoServiceTypedUseProcessor typedUseProcessor) {
            int count = Integer.parseInt(properties.getProperty("typed.count", "0"));
            typedUseProcessor.serviceTypesBySimpleName.clear();
            for (int index = 0; index < count; index++) {
                String name = properties.getProperty("typed." + index + ".name");
                List<String> serviceTypes = splitList(properties.getProperty("typed." + index + ".serviceTypes", ""));
                typedUseProcessor.serviceTypesBySimpleName.put(name, serviceTypes);
            }
        } else if (processor instanceof AutoServiceContractProcessor contractProcessor
                && properties.containsKey("contract.qualifiedName")) {
            contractProcessor.contract = new AutoServiceContract(
                    properties.getProperty("contract.qualifiedName"),
                    ElementKind.valueOf(properties.getProperty("contract.annotationKind")),
                    new HashSet<>(splitList(properties.getProperty("contract.metaAnnotationTypes", ""))),
                    properties.getProperty("contract.retentionPolicy"),
                    splitList(properties.getProperty("contract.targets", "")),
                    properties.getProperty("contract.valueMethodReturnType"),
                    null);
        }
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

    private static void ensureJavaHomeProperty() {
        if (System.getProperty("java.home") == null) {
            String javaHome = System.getenv("JAVA_HOME");
            assertThat(javaHome).as("JAVA_HOME").isNotBlank();
            System.setProperty("java.home", javaHome);
        }
    }

    private static List<String> readClassArrayValue(AnnotationValue annotationValue) {
        Object rawValue = annotationValue.getValue();
        assertThat(rawValue).isInstanceOf(List.class);
        List<?> values = (List<?>) rawValue;
        List<String> typeNames = new ArrayList<>();
        for (Object value : values) {
            assertThat(value).isInstanceOf(AnnotationValue.class);
            Object classValue = ((AnnotationValue) value).getValue();
            assertThat(classValue).isInstanceOf(TypeMirror.class);
            typeNames.add(classValue.toString());
        }
        return typeNames;
    }

    private static String annotationTypeName(AnnotationMirror mirror) {
        Element annotationElement = mirror.getAnnotationType().asElement();
        assertThat(annotationElement).isInstanceOf(TypeElement.class);
        return ((TypeElement) annotationElement).getQualifiedName().toString();
    }

    private static String valueElementName(ExecutableElement element) {
        return element.getSimpleName().toString();
    }

    // Checkstyle: allow direct annotation access
    @SuppressWarnings("annotationAccess")
    private static com.google.auto.service.AutoService autoServiceAnnotation(Element element) {
        Element annotationAccess = element;
        return annotationAccess.getAnnotation(com.google.auto.service.AutoService.class);
    }
    // Checkstyle: disallow direct annotation access

    private record CompilationResult(boolean successful, String diagnosticText) {
    }

    private record AnnotationUse(ElementKind kind, List<String> serviceTypes) {
    }

    private record NestedAnnotationUse(
            String qualifiedName,
            String enclosingQualifiedName,
            NestingKind nestingKind,
            List<String> serviceTypes) {
    }

    private record AutoServiceContract(
            String qualifiedName,
            ElementKind annotationKind,
            Set<String> metaAnnotationTypes,
            String retentionPolicy,
            List<String> targets,
            String valueMethodReturnType,
            AnnotationValue valueMethodDefault) {
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

    private abstract static class BaseProcessor extends AbstractProcessor {
        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        protected final TypeElement autoServiceType() {
            TypeElement type = processingEnv.getElementUtils().getTypeElement(AUTO_SERVICE_TYPE);
            assertThat(type).as(AUTO_SERVICE_TYPE).isNotNull();
            return type;
        }
    }

    private static final class NoopProcessor extends BaseProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }
    }

    private static final class AutoServiceUseProcessor extends BaseProcessor {
        private final Map<String, AnnotationUse> recordsBySimpleName = new HashMap<>();

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                return false;
            }

            for (Element element : roundEnv.getElementsAnnotatedWith(autoServiceType())) {
                AnnotationUse annotationUse = new AnnotationUse(element.getKind(), serviceTypes(element));
                recordsBySimpleName.put(element.getSimpleName().toString(), annotationUse);
            }
            return false;
        }

        private Map<String, AnnotationUse> recordsBySimpleName() {
            return recordsBySimpleName;
        }

        private static List<String> serviceTypes(Element element) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                if (AUTO_SERVICE_TYPE.equals(annotationTypeName(mirror))) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                            mirror.getElementValues().entrySet()) {
                        if ("value".equals(valueElementName(entry.getKey()))) {
                            return readClassArrayValue(entry.getValue());
                        }
                    }
                }
            }
            return List.of();
        }
    }

    private static final class NestedAutoServiceUseProcessor extends BaseProcessor {
        private NestedAnnotationUse annotationUse;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                return false;
            }

            for (Element element : roundEnv.getElementsAnnotatedWith(autoServiceType())) {
                assertThat(element).isInstanceOf(TypeElement.class);
                TypeElement typeElement = (TypeElement) element;
                assertThat(typeElement.getEnclosingElement()).isInstanceOf(TypeElement.class);
                TypeElement enclosingElement = (TypeElement) typeElement.getEnclosingElement();
                annotationUse = new NestedAnnotationUse(
                        typeElement.getQualifiedName().toString(),
                        enclosingElement.getQualifiedName().toString(),
                        typeElement.getNestingKind(),
                        AutoServiceUseProcessor.serviceTypes(element));
            }
            return false;
        }

        private NestedAnnotationUse annotationUse() {
            assertThat(annotationUse).isNotNull();
            return annotationUse;
        }
    }

    private static final class AutoServiceTypedUseProcessor extends BaseProcessor {
        private final Map<String, List<String>> serviceTypesBySimpleName = new HashMap<>();

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                return false;
            }

            for (Element element : roundEnv.getElementsAnnotatedWith(autoServiceType())) {
                com.google.auto.service.AutoService annotation = autoServiceAnnotation(element);
                assertThat(annotation).isNotNull();
                serviceTypesBySimpleName.put(element.getSimpleName().toString(), serviceTypes(annotation));
            }
            return false;
        }

        private Map<String, List<String>> serviceTypesBySimpleName() {
            return serviceTypesBySimpleName;
        }

        private static List<String> serviceTypes(com.google.auto.service.AutoService annotation) {
            try {
                Class<?>[] serviceTypes = annotation.value();
                List<String> names = new ArrayList<>();
                for (Class<?> serviceType : serviceTypes) {
                    names.add(serviceType.getName());
                }
                return names;
            } catch (MirroredTypesException e) {
                List<String> names = new ArrayList<>();
                for (TypeMirror typeMirror : e.getTypeMirrors()) {
                    names.add(typeMirror.toString());
                }
                return names;
            }
        }
    }

    private static final class AutoServiceContractProcessor extends BaseProcessor {
        private AutoServiceContract contract;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (contract == null) {
                TypeElement autoServiceType = autoServiceType();
                contract = new AutoServiceContract(
                        autoServiceType.getQualifiedName().toString(),
                        autoServiceType.getKind(),
                        metaAnnotationTypes(autoServiceType),
                        retentionPolicy(autoServiceType),
                        targets(autoServiceType),
                        valueMethodReturnType(autoServiceType),
                        valueMethodDefault(autoServiceType));
            }
            return false;
        }

        private AutoServiceContract contract() {
            assertThat(contract).isNotNull();
            return contract;
        }

        private static Set<String> metaAnnotationTypes(TypeElement type) {
            Set<String> names = new HashSet<>();
            for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
                names.add(annotationTypeName(mirror));
            }
            return names;
        }

        private static String retentionPolicy(TypeElement type) {
            for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
                if ("java.lang.annotation.Retention".equals(annotationTypeName(mirror))) {
                    return singleAnnotationValue(mirror).getValue().toString();
                }
            }
            return null;
        }

        private static List<String> targets(TypeElement type) {
            for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
                if ("java.lang.annotation.Target".equals(annotationTypeName(mirror))) {
                    Object rawValue = singleAnnotationValue(mirror).getValue();
                    assertThat(rawValue).isInstanceOf(List.class);
                    List<?> values = (List<?>) rawValue;
                    List<String> targetNames = new ArrayList<>();
                    for (Object value : values) {
                        assertThat(value).isInstanceOf(AnnotationValue.class);
                        targetNames.add(((AnnotationValue) value).getValue().toString());
                    }
                    return targetNames;
                }
            }
            return List.of();
        }

        private static String valueMethodReturnType(TypeElement type) {
            return valueMethod(type).getReturnType().toString();
        }

        private static AnnotationValue valueMethodDefault(TypeElement type) {
            return valueMethod(type).getDefaultValue();
        }

        private static ExecutableElement valueMethod(TypeElement type) {
            for (Element enclosedElement : type.getEnclosedElements()) {
                if (enclosedElement.getKind() == ElementKind.METHOD
                        && "value".equals(enclosedElement.getSimpleName().toString())) {
                    return (ExecutableElement) enclosedElement;
                }
            }
            throw new AssertionError("AutoService.value() method was not found");
        }

        private static AnnotationValue singleAnnotationValue(AnnotationMirror mirror) {
            assertThat(mirror.getElementValues()).hasSize(1);
            return mirror.getElementValues().values().iterator().next();
        }
    }
}
