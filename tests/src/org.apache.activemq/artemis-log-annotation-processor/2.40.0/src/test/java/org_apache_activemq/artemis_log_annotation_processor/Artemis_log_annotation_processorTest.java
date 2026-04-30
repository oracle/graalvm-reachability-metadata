/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_log_annotation_processor;

import org.apache.activemq.artemis.logs.annotation.GetLogger;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.annotation.Message;
import org.apache.activemq.artemis.logs.annotation.processor.LogAnnotationProcessor;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.AnnotatedConstruct;
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
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class Artemis_log_annotation_processorTest {

    private static final FakeTypeElement LOG_BUNDLE_ANNOTATION_TYPE = FakeTypeElement.annotationType(
            "org.apache.activemq.artemis.logs.annotation.LogBundle"
    );

    @Test
    void generatesImplementationSourceForMessagesLoggersAndGetLogger() {
        FakeTypeElement bundle = FakeTypeElement.interfaceType(
                "example.logs.SampleBundle",
                new LogBundleLiteral("TST", "^[12]0\\d$"),
                new FakeExecutableElement(
                        "simple",
                        declaredType("java.lang.String"),
                        List.of(),
                        new MessageLiteral(101, "Simple value")
                ),
                new FakeExecutableElement(
                        "formatted",
                        declaredType("java.lang.String"),
                        List.of(
                                new FakeVariableElement("user", declaredType("java.lang.String")),
                                new FakeVariableElement("city", declaredType("java.lang.String"))
                        ),
                        new MessageLiteral(102, "Hello {} from {}")
                ),
                new FakeExecutableElement(
                        "failure",
                        declaredType("java.lang.IllegalStateException"),
                        List.of(
                                new FakeVariableElement("operation", declaredType("java.lang.String")),
                                new FakeVariableElement("cause", declaredType("java.lang.IllegalArgumentException"))
                        ),
                        new MessageLiteral(103, "Failure while processing {}")
                ),
                new FakeExecutableElement(
                        "logger",
                        declaredType("org.slf4j.Logger"),
                        List.of(),
                        new GetLoggerLiteral()
                ),
                new FakeExecutableElement(
                        "warnUser",
                        voidType(),
                        List.of(new FakeVariableElement("user", declaredType("java.lang.String"))),
                        new LogMessageLiteral(201, "Warn {}", LogMessage.Level.WARN)
                ),
                new FakeExecutableElement(
                        "infoStarted",
                        voidType(),
                        List.of(new FakeVariableElement("service", declaredType("java.lang.String"))),
                        new LogMessageLiteral(202, "Info {}", LogMessage.Level.INFO)
                ),
                new FakeExecutableElement(
                        "errorFailed",
                        voidType(),
                        List.of(
                                new FakeVariableElement("task", declaredType("java.lang.String")),
                                new FakeVariableElement("failure", declaredType("java.lang.RuntimeException"))
                        ),
                        new LogMessageLiteral(203, "Error {}", LogMessage.Level.ERROR)
                ),
                new FakeExecutableElement(
                        "warnState",
                        voidType(),
                        List.of(new FakeVariableElement("state", declaredType("java.lang.String"))),
                        new LogMessageLiteral(204, "Warn state {}", LogMessage.Level.WARN)
                ),
                new FakeExecutableElement(
                        "infoTick",
                        voidType(),
                        List.of(),
                        new LogMessageLiteral(205, "Info tick", LogMessage.Level.INFO)
                )
        );

        ProcessingResult result = process(bundle);

        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.sources()).containsOnlyKeys("example.logs.SampleBundle_impl");

        String generatedSource = result.sources().get("example.logs.SampleBundle_impl");
        assertThat(generatedSource).contains(
                "package example.logs;",
                "public class SampleBundle_impl implements SampleBundle",
                "private final Logger logger;",
                "private static void _copyStackTraceMinusOne(final Throwable e)",
                "public SampleBundle_impl(Logger logger)",
                "public Logger logger()",
                "return logger;",
                "public java.lang.String simple()",
                "String returnString = \"TST101: Simple value\";",
                "return returnString;",
                "public java.lang.String formatted(java.lang.String user, java.lang.String city)",
                "String returnString = MessageFormatter.arrayFormat(\"TST102: Hello {} from {}\", new Object[]{user, city}).getMessage();",
                "public java.lang.IllegalStateException failure(java.lang.String operation, java.lang.IllegalArgumentException cause)",
                "String returnString = MessageFormatter.arrayFormat(\"TST103: Failure while processing {}\", new Object[]{operation, cause}).getMessage();",
                "java.lang.IllegalStateException objReturn_failure = new java.lang.IllegalStateException(returnString);",
                "objReturn_failure.initCause(cause);",
                "_copyStackTraceMinusOne(objReturn_failure);",
                "return objReturn_failure;",
                "if (logger.isWarnEnabled()) {",
                "logger.warn(\"TST201: Warn {}\", user);",
                "if (logger.isInfoEnabled()) {",
                "logger.info(\"TST202: Info {}\", service);",
                "if (logger.isErrorEnabled()) {",
                "logger.error(\"TST203: Error {}\", task, failure);",
                "if (logger.isWarnEnabled()) {",
                "logger.warn(\"TST204: Warn state {}\", state);",
                "if (logger.isInfoEnabled()) {",
                "logger.info(\"TST205: Info tick\");"
        );
    }

    @Test
    void generatesMessageImplementationsForCustomThrowableHierarchies() {
        ProcessingResult result = process(FakeTypeElement.interfaceType(
                "example.logs.CustomThrowableBundle",
                new LogBundleLiteral("CTM", ""),
                new FakeExecutableElement(
                        "customFailure",
                        declaredType("example.logs.OperationFailure", declaredType("java.lang.RuntimeException")),
                        List.of(
                                new FakeVariableElement("operation", declaredType("java.lang.String")),
                                new FakeVariableElement("cause", declaredType("example.logs.ProblemCause", declaredType("java.lang.Throwable")))
                        ),
                        new MessageLiteral(401, "Could not complete {}")
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.sources()).containsOnlyKeys("example.logs.CustomThrowableBundle_impl");

        String generatedSource = result.sources().get("example.logs.CustomThrowableBundle_impl");
        assertThat(generatedSource).contains(
                "public example.logs.OperationFailure customFailure(java.lang.String operation, example.logs.ProblemCause cause)",
                "String returnString = MessageFormatter.arrayFormat(\"CTM401: Could not complete {}\", new Object[]{operation, cause}).getMessage();",
                "example.logs.OperationFailure objReturn_customFailure = new example.logs.OperationFailure(returnString);",
                "objReturn_customFailure.initCause(cause);",
                "_copyStackTraceMinusOne(objReturn_customFailure);"
        );
    }

    @Test
    void escapesQuotesAndNewlinesInGeneratedSource() {
        ProcessingResult result = process(FakeTypeElement.interfaceType(
                "example.logs.EscapedCharactersBundle",
                new LogBundleLiteral("ENC", ""),
                new FakeExecutableElement(
                        "messageWithEscapes",
                        declaredType("java.lang.String"),
                        List.of(),
                        new MessageLiteral(301, "First line\nSecond \"quoted\" line")
                ),
                new FakeExecutableElement(
                        "logWithEscapes",
                        voidType(),
                        List.of(),
                        new LogMessageLiteral(302, "Logged\nmessage with \"quotes\"", LogMessage.Level.INFO)
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.sources()).containsOnlyKeys("example.logs.EscapedCharactersBundle_impl");

        String generatedSource = result.sources().get("example.logs.EscapedCharactersBundle_impl");
        assertThat(generatedSource).contains(
                "String returnString = \"ENC301: First line\\nSecond \\\"quoted\\\" line\";",
                "logger.info(\"ENC302: Logged\\nmessage with \\\"quotes\\\"\");"
        );
    }

    @Test
    void rejectsInvalidPlaceholders() {
        ProcessingResult namedPlaceholderResult = process(FakeTypeElement.interfaceType(
                "example.logs.NamedPlaceholderBundle",
                new LogBundleLiteral("TST", ""),
                new FakeExecutableElement(
                        "namedPlaceholder",
                        declaredType("java.lang.String"),
                        List.of(new FakeVariableElement("user", declaredType("java.lang.String"))),
                        new MessageLiteral(10, "Bad {user}")
                )
        ));

        assertProcessingError(
                namedPlaceholderResult,
                "Invalid placeholder argument {user} on message 'Bad {user}' as part of java.lang.String namedPlaceholder(java.lang.String user)\nreplace it by {}"
        );

        ProcessingResult printfPlaceholderResult = process(FakeTypeElement.interfaceType(
                "example.logs.PrintfPlaceholderBundle",
                new LogBundleLiteral("TST", ""),
                new FakeExecutableElement(
                        "printfPlaceholder",
                        declaredType("java.lang.String"),
                        List.of(new FakeVariableElement("count", primitiveType("int", TypeKind.INT))),
                        new MessageLiteral(11, "Bad %d")
                )
        ));

        assertProcessingError(
                printfPlaceholderResult,
                "Cannot use %s or %d in loggers. Please use {} on message 'Bad %d'"
        );
    }

    @Test
    void rejectsDuplicateIdsAndRegexMismatches() {
        ProcessingResult duplicateIdResult = process(FakeTypeElement.interfaceType(
                "example.logs.DuplicateIdsBundle",
                new LogBundleLiteral("TST", ""),
                new FakeExecutableElement(
                        "first",
                        declaredType("java.lang.String"),
                        List.of(),
                        new MessageLiteral(20, "First")
                ),
                new FakeExecutableElement(
                        "second",
                        voidType(),
                        List.of(),
                        new LogMessageLiteral(20, "Second", LogMessage.Level.INFO)
                )
        ));

        assertProcessingError(
                duplicateIdResult,
                "example.logs.DuplicateIdsBundle: ID 20 with message 'Second' was previously used already, to define message 'First'. Consider trying ID 21 which is the next unused value."
        );

        ProcessingResult regexMismatchResult = process(FakeTypeElement.interfaceType(
                "example.logs.RegexBundle",
                new LogBundleLiteral("TST", "^99\\d$"),
                new FakeExecutableElement(
                        "badRegex",
                        declaredType("java.lang.String"),
                        List.of(),
                        new MessageLiteral(10, "Wrong id")
                )
        ));

        assertProcessingError(
                regexMismatchResult,
                "example.logs.RegexBundle: Code 10 does not match regular expression specified on the LogBundle: ^99\\d$"
        );
    }

    @Test
    void rejectsMisplacedExceptionArgumentsAndCombinedAnnotations() {
        ProcessingResult misplacedExceptionResult = process(FakeTypeElement.interfaceType(
                "example.logs.MisplacedExceptionBundle",
                new LogBundleLiteral("TST", ""),
                new FakeExecutableElement(
                        "misplaced",
                        declaredType("java.lang.String"),
                        List.of(
                                new FakeVariableElement("cause", declaredType("java.lang.IllegalArgumentException")),
                                new FakeVariableElement("value", declaredType("java.lang.String"))
                        ),
                        new MessageLiteral(30, "Broken {}")
                )
        ));

        assertProcessingError(
                misplacedExceptionResult,
                "Exception argument java.lang.IllegalArgumentException cause has to be the last argument on the list. Look at: java.lang.String misplaced(java.lang.IllegalArgumentException cause, java.lang.String value)"
        );

        ProcessingResult combinedAnnotationResult = process(FakeTypeElement.interfaceType(
                "example.logs.CombinedAnnotationsBundle",
                new LogBundleLiteral("TST", ""),
                new FakeExecutableElement(
                        "combined",
                        declaredType("java.lang.String"),
                        List.of(),
                        new MessageLiteral(31, "Combined"),
                        new GetLoggerLiteral()
                )
        ));

        assertProcessingError(
                combinedAnnotationResult,
                "Cannot use combined annotations  on java.lang.String combined()"
        );
    }

    private static void assertProcessingError(ProcessingResult result, String expectedMessage) {
        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .singleElement()
                .satisfies(message -> assertThat(message).isEqualTo(expectedMessage));
    }

    private static ProcessingResult process(FakeTypeElement bundleType) {
        CapturingFiler filer = new CapturingFiler();
        CapturingMessager messager = new CapturingMessager();
        LogAnnotationProcessor processor = new LogAnnotationProcessor();
        processor.init(new FakeProcessingEnvironment(filer, messager));

        boolean success = processor.process(
                Set.of(LOG_BUNDLE_ANNOTATION_TYPE),
                new FakeRoundEnvironment(Set.of(bundleType))
        );

        return new ProcessingResult(success, filer.generatedSources(), messager.errorMessages());
    }

    private static FakeDeclaredType declaredType(String displayName) {
        return new FakeDeclaredType(displayName);
    }

    private static FakeDeclaredType declaredType(String displayName, TypeMirror superClass) {
        return new FakeDeclaredType(displayName, FakeTypeElement.classType(displayName, superClass));
    }

    private static FakeTypeMirror primitiveType(String displayName, TypeKind kind) {
        return new FakeTypeMirror(displayName, kind);
    }

    private static FakeNoType voidType() {
        return new FakeNoType("void", TypeKind.VOID);
    }

    private record ProcessingResult(boolean success, Map<String, String> sources, List<String> errors) {
    }

    private static final class FakeProcessingEnvironment implements ProcessingEnvironment {
        private final Filer filer;
        private final Messager messager;

        private FakeProcessingEnvironment(Filer filer, Messager messager) {
            this.filer = filer;
            this.messager = messager;
        }

        @Override
        public Map<String, String> getOptions() {
            return Collections.emptyMap();
        }

        @Override
        public Messager getMessager() {
            return messager;
        }

        @Override
        public Filer getFiler() {
            return filer;
        }

        @Override
        public Elements getElementUtils() {
            throw new UnsupportedOperationException();
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

    private static final class FakeRoundEnvironment implements RoundEnvironment {
        private final Set<? extends Element> rootElements;

        private FakeRoundEnvironment(Set<? extends Element> rootElements) {
            this.rootElements = rootElements;
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
            return rootElements;
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotation) {
            return isLogBundleType(annotation) ? rootElements : Collections.emptySet();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> annotation) {
            return annotation == LogBundle.class ? rootElements : Collections.emptySet();
        }

        private boolean isLogBundleType(TypeElement annotation) {
            return annotation != null
                    && Objects.equals(annotation.getQualifiedName().toString(), LogBundle.class.getName());
        }
    }

    private static final class CapturingFiler implements Filer {
        private final Map<String, CapturingJavaFileObject> generatedSources = new LinkedHashMap<>();

        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) {
            CapturingJavaFileObject fileObject = new CapturingJavaFileObject(name.toString());
            generatedSources.put(name.toString(), fileObject);
            return fileObject;
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location,
                                         CharSequence pkg,
                                         CharSequence relativeName,
                                         Element... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject getResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName) {
            throw new UnsupportedOperationException();
        }

        private Map<String, String> generatedSources() {
            Map<String, String> sources = new LinkedHashMap<>();
            for (Map.Entry<String, CapturingJavaFileObject> entry : generatedSources.entrySet()) {
                sources.put(entry.getKey(), entry.getValue().content());
            }
            return sources;
        }
    }

    private static final class CapturingJavaFileObject extends SimpleJavaFileObject {
        private final StringWriter writer = new StringWriter();

        private CapturingJavaFileObject(String className) {
            super(URI.create("memory:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
        }

        @Override
        public Writer openWriter() {
            return writer;
        }

        private String content() {
            return writer.toString();
        }
    }

    private static final class CapturingMessager implements Messager {
        private final List<String> errorMessages = new ArrayList<>();

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message) {
            if (kind == Diagnostic.Kind.ERROR) {
                errorMessages.add(message.toString());
            }
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
            printMessage(kind, message);
        }

        @Override
        public void printMessage(Diagnostic.Kind kind,
                                 CharSequence message,
                                 Element element,
                                 AnnotationMirror annotationMirror) {
            printMessage(kind, message);
        }

        @Override
        public void printMessage(Diagnostic.Kind kind,
                                 CharSequence message,
                                 Element element,
                                 AnnotationMirror annotationMirror,
                                 AnnotationValue annotationValue) {
            printMessage(kind, message);
        }

        private List<String> errorMessages() {
            return errorMessages;
        }
    }

    private abstract static class FakeAnnotatedConstruct implements AnnotatedConstruct {
        private final Map<Class<? extends Annotation>, Annotation> annotations;

        private FakeAnnotatedConstruct(Map<Class<? extends Annotation>, Annotation> annotations) {
            this.annotations = annotations;
        }

        public final List<? extends AnnotationMirror> getAnnotationMirrors() {
            return Collections.emptyList();
        }

        @Override
        public final <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return annotationType.cast(annotations.get(annotationType));
        }

        @Override
        @SuppressWarnings("unchecked")
        public final <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            Annotation annotation = annotations.get(annotationType);
            if (annotation == null) {
                return (A[]) new Annotation[0];
            }
            return (A[]) new Annotation[]{annotation};
        }
    }

    private abstract static class FakeElement extends FakeAnnotatedConstruct implements Element {
        private final ElementKind kind;
        private final SimpleName simpleName;
        private Element enclosingElement;
        private List<? extends Element> enclosedElements;

        private FakeElement(ElementKind kind,
                            String simpleName,
                            Element enclosingElement,
                            List<? extends Element> enclosedElements,
                            Map<Class<? extends Annotation>, Annotation> annotations) {
            super(annotations);
            this.kind = kind;
            this.simpleName = new SimpleName(simpleName);
            this.enclosingElement = enclosingElement;
            this.enclosedElements = enclosedElements;
        }

        @Override
        public final ElementKind getKind() {
            return kind;
        }

        @Override
        public final Set<Modifier> getModifiers() {
            return Collections.emptySet();
        }

        @Override
        public final Name getSimpleName() {
            return simpleName;
        }

        @Override
        public final Element getEnclosingElement() {
            return enclosingElement;
        }

        @Override
        public final List<? extends Element> getEnclosedElements() {
            return enclosedElements;
        }

        @Override
        public final <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean equals(Object other) {
            return this == other;
        }

        @Override
        public final int hashCode() {
            return System.identityHashCode(this);
        }

        final void setEnclosingElement(Element enclosingElement) {
            this.enclosingElement = enclosingElement;
        }

        final void setEnclosedElements(List<? extends Element> enclosedElements) {
            this.enclosedElements = enclosedElements;
        }
    }

    private static class FakeTypeElement extends FakeElement implements TypeElement {
        private final String qualifiedName;
        private final TypeMirror superClass;

        private FakeTypeElement(String qualifiedName,
                                ElementKind kind,
                                Element enclosingElement,
                                List<? extends Element> enclosedElements,
                                Map<Class<? extends Annotation>, Annotation> annotations,
                                TypeMirror superClass) {
            super(kind, simpleNameOf(qualifiedName), enclosingElement, enclosedElements, annotations);
            this.qualifiedName = qualifiedName;
            this.superClass = superClass;
        }

        private static FakeTypeElement annotationType(String qualifiedName) {
            String packageName = packageNameOf(qualifiedName);
            return new FakeTypeElement(
                    qualifiedName,
                    ElementKind.ANNOTATION_TYPE,
                    new FakePackageElement(packageName),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    new FakeNoType("none", TypeKind.NONE)
            );
        }

        private static FakeTypeElement classType(String qualifiedName, TypeMirror superClass) {
            return new FakeTypeElement(
                    qualifiedName,
                    ElementKind.CLASS,
                    new FakePackageElement(packageNameOf(qualifiedName)),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    superClass
            );
        }

        private static FakeTypeElement interfaceType(String qualifiedName,
                                                     LogBundleLiteral logBundle,
                                                     FakeExecutableElement... methods) {
            FakePackageElement packageElement = new FakePackageElement(packageNameOf(qualifiedName));
            Map<Class<? extends Annotation>, Annotation> annotations = Collections.singletonMap(LogBundle.class, logBundle);
            List<FakeExecutableElement> executableElements = List.of(methods);
            FakeTypeElement typeElement = new FakeTypeElement(
                    qualifiedName,
                    ElementKind.INTERFACE,
                    packageElement,
                    executableElements,
                    annotations,
                    new FakeNoType("none", TypeKind.NONE)
            );
            for (FakeExecutableElement executableElement : executableElements) {
                executableElement.setEnclosingElement(typeElement);
                for (FakeVariableElement parameter : executableElement.parameters()) {
                    parameter.setEnclosingElement(executableElement);
                }
            }
            typeElement.setEnclosedElements(executableElements);
            return typeElement;
        }

        @Override
        public TypeMirror asType() {
            return new FakeDeclaredType(qualifiedName, this);
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public Name getQualifiedName() {
            return new SimpleName(qualifiedName);
        }

        @Override
        public TypeMirror getSuperclass() {
            return superClass;
        }

        @Override
        public List<? extends TypeMirror> getInterfaces() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends TypeParameterElement> getTypeParameters() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends RecordComponentElement> getRecordComponents() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends TypeMirror> getPermittedSubclasses() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return qualifiedName;
        }
    }

    private static final class FakePackageElement extends FakeElement implements PackageElement {
        private final String qualifiedName;

        private FakePackageElement(String qualifiedName) {
            super(ElementKind.PACKAGE, simpleNameOf(qualifiedName), null, Collections.emptyList(), Collections.emptyMap());
            this.qualifiedName = qualifiedName;
        }

        @Override
        public TypeMirror asType() {
            return new FakeNoType("package", TypeKind.PACKAGE);
        }

        @Override
        public Name getQualifiedName() {
            return new SimpleName(qualifiedName);
        }

        @Override
        public boolean isUnnamed() {
            return qualifiedName.isEmpty();
        }

        @Override
        public String toString() {
            return qualifiedName;
        }
    }

    private static final class FakeExecutableElement extends FakeElement implements ExecutableElement {
        private final TypeMirror returnType;
        private final List<FakeVariableElement> parameters;

        private FakeExecutableElement(String name,
                                      TypeMirror returnType,
                                      List<FakeVariableElement> parameters,
                                      Annotation... annotations) {
            super(ElementKind.METHOD, name, null, Collections.emptyList(), annotationMap(annotations));
            this.returnType = returnType;
            this.parameters = parameters;
        }

        @Override
        public TypeMirror asType() {
            return returnType;
        }

        @Override
        public List<? extends TypeParameterElement> getTypeParameters() {
            return Collections.emptyList();
        }

        @Override
        public TypeMirror getReturnType() {
            return returnType;
        }

        @Override
        public List<? extends VariableElement> getParameters() {
            return parameters;
        }

        private List<FakeVariableElement> parameters() {
            return parameters;
        }

        @Override
        public TypeMirror getReceiverType() {
            return new FakeNoType("none", TypeKind.NONE);
        }

        @Override
        public boolean isVarArgs() {
            return false;
        }

        @Override
        public boolean isDefault() {
            return false;
        }

        @Override
        public List<? extends TypeMirror> getThrownTypes() {
            return Collections.emptyList();
        }

        @Override
        public AnnotationValue getDefaultValue() {
            return null;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(returnType).append(' ').append(getSimpleName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(parameters.get(i));
            }
            builder.append(')');
            return builder.toString();
        }
    }

    private static final class FakeVariableElement extends FakeElement implements VariableElement {
        private final TypeMirror type;

        private FakeVariableElement(String name, TypeMirror type) {
            super(ElementKind.PARAMETER, name, null, Collections.emptyList(), Collections.emptyMap());
            this.type = type;
        }

        @Override
        public TypeMirror asType() {
            return type;
        }

        @Override
        public Object getConstantValue() {
            return null;
        }

        @Override
        public String toString() {
            return type + " " + getSimpleName();
        }
    }

    private static class FakeTypeMirror extends FakeAnnotatedConstruct implements TypeMirror {
        private final String displayName;
        private final TypeKind kind;

        private FakeTypeMirror(String displayName, TypeKind kind) {
            super(Collections.emptyMap());
            this.displayName = displayName;
            this.kind = kind;
        }

        @Override
        public TypeKind getKind() {
            return kind;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> visitor, P parameter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FakeTypeMirror)) {
                return false;
            }
            FakeTypeMirror that = (FakeTypeMirror) other;
            return kind == that.kind && displayName.equals(that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(displayName, kind);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class FakeDeclaredType extends FakeTypeMirror implements DeclaredType {
        private final TypeElement element;

        private FakeDeclaredType(String displayName) {
            this(displayName, new FakeTypeElement(
                    displayName,
                    ElementKind.CLASS,
                    new FakePackageElement(packageNameOf(displayName)),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    new FakeDeclaredType("java.lang.Object", null)
            ));
        }

        private FakeDeclaredType(String displayName, TypeElement element) {
            super(displayName, TypeKind.DECLARED);
            this.element = element;
        }

        @Override
        public Element asElement() {
            return element;
        }

        @Override
        public TypeMirror getEnclosingType() {
            return new FakeNoType("none", TypeKind.NONE);
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return Collections.emptyList();
        }
    }

    private static final class FakeNoType extends FakeTypeMirror implements NoType {
        private FakeNoType(String displayName, TypeKind kind) {
            super(displayName, kind);
        }
    }

    private static final class SimpleName implements Name {
        private final String value;

        private SimpleName(String value) {
            this.value = value;
        }

        @Override
        public boolean contentEquals(CharSequence other) {
            return value.contentEquals(other);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Name && value.contentEquals((Name) other);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private abstract static class AnnotationLiteral implements Annotation {
        @Override
        public final boolean equals(Object other) {
            return this == other;
        }

        @Override
        public final int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private static final class LogBundleLiteral extends AnnotationLiteral implements LogBundle {
        private final String projectCode;
        private final String regexId;

        private LogBundleLiteral(String projectCode, String regexId) {
            this.projectCode = projectCode;
            this.regexId = regexId;
        }

        @Override
        public String projectCode() {
            return projectCode;
        }

        @Override
        public String regexID() {
            return regexId;
        }

        @Override
        public int[] retiredIDs() {
            return new int[0];
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return LogBundle.class;
        }

        @Override
        public String toString() {
            return "@LogBundle(projectCode=\"" + projectCode + "\", regexID=\"" + regexId + "\")";
        }
    }

    private static final class MessageLiteral extends AnnotationLiteral implements Message {
        private final int id;
        private final String value;

        private MessageLiteral(int id, String value) {
            this.id = id;
            this.value = value;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Message.class;
        }

        @Override
        public String toString() {
            return "@Message(id=" + id + ", value=\"" + value + "\")";
        }
    }

    private static final class LogMessageLiteral extends AnnotationLiteral implements LogMessage {
        private final int id;
        private final String value;
        private final Level level;

        private LogMessageLiteral(int id, String value, Level level) {
            this.id = id;
            this.value = value;
            this.level = level;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public String loggerName() {
            return "";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return LogMessage.class;
        }

        @Override
        public String toString() {
            return "@LogMessage(id=" + id + ", value=\"" + value + "\", level=" + level + ")";
        }
    }

    private static final class GetLoggerLiteral extends AnnotationLiteral implements GetLogger {
        @Override
        public Class<? extends Annotation> annotationType() {
            return GetLogger.class;
        }

        @Override
        public String toString() {
            return "@GetLogger";
        }
    }

    private static Map<Class<? extends Annotation>, Annotation> annotationMap(Annotation... annotations) {
        Map<Class<? extends Annotation>, Annotation> map = new LinkedHashMap<>();
        for (Annotation annotation : annotations) {
            map.put(annotation.annotationType(), annotation);
        }
        return map;
    }

    private static String simpleNameOf(String qualifiedName) {
        int separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? qualifiedName.substring(separatorIndex + 1) : qualifiedName;
    }

    private static String packageNameOf(String qualifiedName) {
        int separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? qualifiedName.substring(0, separatorIndex) : "";
    }
}
