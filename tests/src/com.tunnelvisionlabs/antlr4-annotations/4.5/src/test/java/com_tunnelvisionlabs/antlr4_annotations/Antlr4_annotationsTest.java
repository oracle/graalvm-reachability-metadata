/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4_annotations;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ServiceLoader;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
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

import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.NullUsageProcessor;
import org.antlr.v4.runtime.misc.Nullable;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Antlr4_annotationsTest {

    private static final String NOT_NULL_SOURCE = """
            package org.antlr.v4.runtime.misc;

            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Documented
            @Retention(RetentionPolicy.CLASS)
            @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
            public @interface NotNull {
            }
            """;

    private static final String NULLABLE_SOURCE = """
            package org.antlr.v4.runtime.misc;

            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Documented
            @Retention(RetentionPolicy.CLASS)
            @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
            public @interface Nullable {
            }
            """;

    @TempDir
    Path outputDirectory;

    @Test
    void annotationsCanBeUsedOnSupportedProgramElements() {
        AnnotatedApiSurface sample = new AnnotatedApiSurface("antlr");

        assertThat(sample.field).isEqualTo("antlr");
        assertThat(sample.findValue("suffix")).isEqualTo("antlr-suffix");
        assertThat(sample.findValue("")).isNull();
    }

    @Test
    void processorAdvertisesAnnotationNamesAndSupportedSourceVersion() {
        NullUsageProcessor processor = new NullUsageProcessor();

        assertThat(NullUsageProcessor.NotNullClassName).isEqualTo("org.antlr.v4.runtime.misc.NotNull");
        assertThat(NullUsageProcessor.NullableClassName).isEqualTo("org.antlr.v4.runtime.misc.Nullable");
        assertThat(processor.getSupportedAnnotationTypes())
                .containsExactlyInAnyOrder(NullUsageProcessor.NotNullClassName, NullUsageProcessor.NullableClassName);
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(expectedSupportedSourceVersion());
    }

    @Test
    void serviceLoaderDiscoversAnnotationProcessor() {
        ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class);

        assertThat(processors).anySatisfy(processor -> assertThat(processor).isInstanceOf(NullUsageProcessor.class));
    }

    @Test
    void processorAcceptsConsistentNullabilityContracts() throws IOException {
        try {
            CompilationResult result = compileWithNullUsageProcessor("sample.ValidSample", """
                    package sample;

                    import org.antlr.v4.runtime.misc.NotNull;
                    import org.antlr.v4.runtime.misc.Nullable;

                    interface ValidParent {
                        @Nullable String find(@NotNull String query);

                        void receive(@Nullable String value);
                    }

                    class ValidSample implements ValidParent {
                        @NotNull String field = "antlr";

                        @Override
                        @Nullable
                        public String find(@NotNull String query) {
                            @Nullable String result = query.isEmpty() ? null : query;
                            return result;
                        }

                        @Override
                        public void receive(@Nullable String value) {
                        }

                        @NotNull
                        String name() {
                            return this.field;
                        }
                    }
                    """);

            assertThat(result.success()).as(result.formattedDiagnostics()).isTrue();
            assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
        } catch (Error error) {
            verifyUnsupportedDynamicCompilationError(error);
        }
    }

    @Test
    void processorReportsNotNullPrimitiveFieldsAsWarnings() {
        TestElements elements = new TestElements();
        CapturingMessager messager = new CapturingMessager();
        NullUsageProcessor processor = new NullUsageProcessor();
        processor.init(new TestProcessingEnvironment(elements, messager));

        boolean processed = processor.process(Set.of(), new PrimitiveFieldRoundEnvironment(elements));

        assertThat(processed).isTrue();
        assertThat(messager.messages()).extracting(ProcessorMessage::kind)
                .containsExactly(Diagnostic.Kind.WARNING);
        assertThat(messager.messages()).extracting(ProcessorMessage::message)
                .containsExactly("field with a primitive type should not be annotated with NotNull");
    }

    @Test
    void processorReportsNullablePrimitiveLocalVariablesAsErrors() {
        TestElements elements = new TestElements();
        CapturingMessager messager = new CapturingMessager();
        NullUsageProcessor processor = new NullUsageProcessor();
        processor.init(new TestProcessingEnvironment(elements, messager));

        boolean processed = processor.process(Set.of(), new PrimitiveLocalVariableRoundEnvironment(elements));

        assertThat(processed).isTrue();
        assertThat(messager.messages()).extracting(ProcessorMessage::kind)
                .containsExactly(Diagnostic.Kind.ERROR);
        assertThat(messager.messages()).extracting(ProcessorMessage::message)
                .containsExactly("local variable with a primitive type cannot be annotated with Nullable");
    }

    @Test
    void processorReportsInvalidNullabilityContracts() throws IOException {
        try {
            CompilationResult result = compileWithNullUsageProcessor("sample.InvalidSample", """
                    package sample;

                    import org.antlr.v4.runtime.misc.NotNull;
                    import org.antlr.v4.runtime.misc.Nullable;

                    interface InvalidParent {
                        @NotNull String value();

                        void consume(@Nullable String value);

                        String plain();
                    }

                    class InvalidSample implements InvalidParent {
                        @NotNull @Nullable String both;

                        @Nullable int primitiveField;

                        @NotNull int primitiveMethod() {
                            return 1;
                        }

                        @Nullable void voidMethod() {
                        }

                        @Override
                        @Nullable
                        public String value() {
                            return null;
                        }

                        @Override
                        public void consume(@NotNull String value) {
                        }

                        @Override
                        @Nullable
                        public String plain() {
                            return null;
                        }
                    }
                    """);

            assertThat(result.success()).as(result.formattedDiagnostics()).isFalse();
            assertThat(result.formattedDiagnostics())
                    .contains("field cannot be annotated with both NotNull and Nullable")
                    .contains("field with a primitive type cannot be annotated with Nullable")
                    .contains("method with a primitive type should not be annotated with NotNull")
                    .contains("void method cannot be annotated with Nullable")
                    .contains("method annotated with Nullable cannot override or implement a method annotated "
                            + "with NotNull")
                    .contains("parameter value annotated with NotNull cannot override or implement a parameter "
                            + "annotated with Nullable")
                    .contains("method annotated with Nullable overrides a method that is not annotated");
        } catch (Error error) {
            verifyUnsupportedDynamicCompilationError(error);
        }
    }

    private static void verifyUnsupportedDynamicCompilationError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private CompilationResult compileWithNullUsageProcessor(String className, String source) throws IOException {
        configureJavaHomeForDynamicCompilation();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK compiler is required to exercise the annotation processor").isNotNull();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(this.outputDirectory));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, List.of("-proc:only"),
                    null, List.of(new SourceFile("org.antlr.v4.runtime.misc.NotNull", NOT_NULL_SOURCE),
                            new SourceFile("org.antlr.v4.runtime.misc.Nullable", NULLABLE_SOURCE),
                            new SourceFile(className, source)));
            task.setProcessors(List.of(new NullUsageProcessor()));

            boolean success = task.call();
            return new CompilationResult(success, diagnostics.getDiagnostics());
        }
    }

    private static void configureJavaHomeForDynamicCompilation() {
        String environmentJavaHome = System.getenv("JAVA_HOME");
        if (System.getProperty("java.home") == null && environmentJavaHome != null) {
            System.setProperty("java.home", environmentJavaHome);
        }
    }

    private static SourceVersion expectedSupportedSourceVersion() {
        SourceVersion latest = SourceVersion.latestSupported();
        if (latest.ordinal() <= SourceVersion.RELEASE_6.ordinal()) {
            return SourceVersion.RELEASE_6;
        }
        if (latest.ordinal() <= SourceVersion.RELEASE_8.ordinal()) {
            return latest;
        }

        return SourceVersion.RELEASE_8;
    }

    private static final class CompilationResult {

        private final boolean success;

        private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

        private CompilationResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
            this.success = success;
            this.diagnostics = List.copyOf(diagnostics);
        }

        private boolean success() {
            return this.success;
        }

        private List<Diagnostic<? extends JavaFileObject>> diagnostics() {
            return this.diagnostics;
        }

        private String formattedDiagnostics() {
            StringBuilder result = new StringBuilder("Compilation diagnostics");
            for (Diagnostic<? extends JavaFileObject> diagnostic : this.diagnostics) {
                result.append(System.lineSeparator()).append(diagnostic.getKind()).append(": ")
                        .append(diagnostic.getMessage(Locale.ROOT));
            }

            return result.toString();
        }

    }

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String content;

        private SourceFile(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.content;
        }

    }

    private static final class TestProcessingEnvironment implements ProcessingEnvironment {

        private final Elements elements;

        private final Messager messager;

        private TestProcessingEnvironment(Elements elements, Messager messager) {
            this.elements = elements;
            this.messager = messager;
        }

        @Override
        public Map<String, String> getOptions() {
            return Map.of();
        }

        @Override
        public Messager getMessager() {
            return this.messager;
        }

        @Override
        public Filer getFiler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Elements getElementUtils() {
            return this.elements;
        }

        @Override
        public Types getTypeUtils() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

    }

    private static final class PrimitiveFieldRoundEnvironment implements RoundEnvironment {

        private final TypeElement notNullType;

        private final VariableElement notNullPrimitiveField;

        private PrimitiveFieldRoundEnvironment(TestElements elements) {
            this.notNullType = elements.notNullType();
            this.notNullPrimitiveField = new TestVariableElement(this.notNullType);
        }

        @Override
        public boolean processingOver() {
            return false;
        }

        @Override
        public boolean errorRaised() {
            return false;
        }

        @Override
        public Set<? extends Element> getRootElements() {
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotation) {
            if (annotation == this.notNullType) {
                return Set.of(this.notNullPrimitiveField);
            }

            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends java.lang.annotation.Annotation> annotation) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class PrimitiveLocalVariableRoundEnvironment implements RoundEnvironment {

        private final TypeElement nullableType;

        private final VariableElement nullablePrimitiveLocalVariable;

        private PrimitiveLocalVariableRoundEnvironment(TestElements elements) {
            this.nullableType = elements.nullableType();
            this.nullablePrimitiveLocalVariable = new TestVariableElement(this.nullableType,
                    ElementKind.LOCAL_VARIABLE, "primitiveLocal");
        }

        @Override
        public boolean processingOver() {
            return false;
        }

        @Override
        public boolean errorRaised() {
            return false;
        }

        @Override
        public Set<? extends Element> getRootElements() {
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotation) {
            if (annotation == this.nullableType) {
                return Set.of(this.nullablePrimitiveLocalVariable);
            }

            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends java.lang.annotation.Annotation> annotation) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class CapturingMessager implements Messager {

        private final List<ProcessorMessage> messages = new ArrayList<>();

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message) {
            this.messages.add(new ProcessorMessage(kind, message.toString()));
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
            this.messages.add(new ProcessorMessage(kind, message.toString()));
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element,
                AnnotationMirror annotation) {
            this.messages.add(new ProcessorMessage(kind, message.toString()));
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element,
                AnnotationMirror annotation, AnnotationValue value) {
            this.messages.add(new ProcessorMessage(kind, message.toString()));
        }

        private List<ProcessorMessage> messages() {
            return List.copyOf(this.messages);
        }

    }

    private record ProcessorMessage(Diagnostic.Kind kind, String message) {
    }

    private static final class TestElements implements Elements {

        private final TypeElement notNullType = new TestTypeElement(NullUsageProcessor.NotNullClassName);

        private final TypeElement nullableType = new TestTypeElement(NullUsageProcessor.NullableClassName);

        private TypeElement notNullType() {
            return this.notNullType;
        }

        private TypeElement nullableType() {
            return this.nullableType;
        }

        @Override
        public TypeElement getTypeElement(CharSequence name) {
            if (NullUsageProcessor.NotNullClassName.contentEquals(name)) {
                return this.notNullType;
            }
            if (NullUsageProcessor.NullableClassName.contentEquals(name)) {
                return this.nullableType;
            }

            return null;
        }

        @Override
        public PackageElement getPackageElement(CharSequence name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
                AnnotationMirror annotation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDocComment(Element element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDeprecated(Element element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name getBinaryName(TypeElement type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PackageElement getPackageOf(Element type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends Element> getAllMembers(TypeElement type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hides(Element hider, Element hidden) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getConstantExpression(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void printElements(java.io.Writer writer, Element... elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name getName(CharSequence cs) {
            return new TestName(cs.toString());
        }

        @Override
        public boolean isFunctionalInterface(TypeElement type) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class TestVariableElement implements VariableElement {

        private final TypeElement annotationType;

        private final ElementKind kind;

        private final String name;

        private TestVariableElement(TypeElement annotationType) {
            this(annotationType, ElementKind.FIELD, "primitiveField");
        }

        private TestVariableElement(TypeElement annotationType, ElementKind kind, String name) {
            this.annotationType = annotationType;
            this.kind = kind;
            this.name = name;
        }

        @Override
        public TypeMirror asType() {
            return new TestPrimitiveType();
        }

        @Override
        public Object getConstantValue() {
            return 1;
        }

        @Override
        public ElementKind getKind() {
            return this.kind;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of();
        }

        @Override
        public Name getSimpleName() {
            return new TestName(this.name);
        }

        @Override
        public Element getEnclosingElement() {
            return null;
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return List.of();
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of(new TestAnnotationMirror(this.annotationType));
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            return visitor.visitVariable(this, parameter);
        }

    }

    private static final class TestTypeElement implements TypeElement {

        private final String qualifiedName;

        private TestTypeElement(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        @Override
        public TypeMirror asType() {
            return new TestDeclaredType(this);
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.ANNOTATION_TYPE;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of();
        }

        @Override
        public Name getSimpleName() {
            int lastDot = this.qualifiedName.lastIndexOf('.');
            return new TestName(this.qualifiedName.substring(lastDot + 1));
        }

        @Override
        public Element getEnclosingElement() {
            return null;
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return List.of();
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            return visitor.visitType(this, parameter);
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public Name getQualifiedName() {
            return new TestName(this.qualifiedName);
        }

        @Override
        public TypeMirror getSuperclass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends TypeMirror> getInterfaces() {
            return List.of();
        }

        @Override
        public List<? extends TypeParameterElement> getTypeParameters() {
            return List.of();
        }

    }

    private static final class TestAnnotationMirror implements AnnotationMirror {

        private final TypeElement annotationType;

        private TestAnnotationMirror(TypeElement annotationType) {
            this.annotationType = annotationType;
        }

        @Override
        public DeclaredType getAnnotationType() {
            return new TestDeclaredType(this.annotationType);
        }

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
            return Map.of();
        }

    }

    private static final class TestDeclaredType implements DeclaredType {

        private final TypeElement element;

        private TestDeclaredType(TypeElement element) {
            this.element = element;
        }

        @Override
        public Element asElement() {
            return this.element;
        }

        @Override
        public TypeMirror getEnclosingType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return List.of();
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            return visitor.visitDeclared(this, parameter);
        }

    }

    private static final class TestPrimitiveType implements PrimitiveType {

        @Override
        public TypeKind getKind() {
            return TypeKind.INT;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            return visitor.visitPrimitive(this, parameter);
        }

    }

    private static final class TestName implements Name {

        private final String value;

        private TestName(String value) {
            this.value = value;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return this.value.contentEquals(cs);
        }

        @Override
        public int length() {
            return this.value.length();
        }

        @Override
        public char charAt(int index) {
            return this.value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return this.value.subSequence(start, end);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Name other)) {
                return false;
            }

            return this.contentEquals(other);
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }

        @Override
        public String toString() {
            return this.value;
        }

    }

    private static final class AnnotatedApiSurface {

        @NotNull
        private final String field;

        private AnnotatedApiSurface(@NotNull String field) {
            this.field = field;
        }

        @Nullable
        private String findValue(@NotNull String suffix) {
            @Nullable String local = suffix.isEmpty() ? null : this.field + "-" + suffix;
            return local;
        }

    }

}
