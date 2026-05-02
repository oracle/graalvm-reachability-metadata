/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_service.auto_service_annotations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
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
            return new CompilationResult(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private record CompilationResult(boolean successful, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        private String diagnosticText() {
            StringBuilder text = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                text.append(diagnostic.getKind())
                        .append(": ")
                        .append(diagnostic.getMessage(Locale.ROOT))
                        .append(System.lineSeparator());
            }
            return text.toString();
        }
    }

    private record AnnotationUse(ElementKind kind, List<String> serviceTypes) {
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
