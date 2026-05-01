/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_di;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.MojoExecutionScoped;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Qualifier;
import org.apache.maven.api.di.Scope;
import org.apache.maven.api.di.SessionScoped;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.di.Typed;
import org.apache.maven.di.tool.DiIndexProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Maven_api_diTest {
    @Test
    void annotationTypesCanBeReferencedFromExecutableCode() {
        List<Class<?>> annotationTypes = List.of(
                Inject.class,
                MojoExecutionScoped.class,
                Named.class,
                Priority.class,
                Provides.class,
                Qualifier.class,
                Scope.class,
                SessionScoped.class,
                Singleton.class,
                Typed.class,
                Offline.class,
                BuildScoped.class);

        assertThat(annotationTypes)
                .containsExactly(
                        Inject.class,
                        MojoExecutionScoped.class,
                        Named.class,
                        Priority.class,
                        Provides.class,
                        Qualifier.class,
                        Scope.class,
                        SessionScoped.class,
                        Singleton.class,
                        Typed.class,
                        Offline.class,
                        BuildScoped.class)
                .doesNotHaveDuplicates();
    }

    @Test
    void constructorFieldAndMethodInjectionCanDescribeAComponentGraph() {
        SessionState sessionState = new SessionState("session-1");
        MojoState mojoState = new MojoState("compile");
        RepositoryClient repositoryClient = new RepositoryClient("central", true);
        BuildComponentFactory factory = new BuildComponentFactory(sessionState, mojoState, repositoryClient);

        ArtifactResolver resolver = factory.createResolver("org.example:demo:1.0.0");

        assertThat(resolver.resolve()).isEqualTo("session-1/compile/central/org.example:demo:1.0.0/offline");
        assertThat(resolver.repositoryClient()).isSameAs(repositoryClient);
        assertThat(resolver.repositoryClient().offline()).isTrue();
    }

    @Test
    void providerMethodsCanModelNamedBindingsAndPrioritizedServices() {
        BuildModule module = new BuildModule("demo-project");
        List<LifecycleParticipant> participants = List.of(
                module.packageParticipant(),
                module.validateParticipant(),
                module.compileParticipant());

        List<String> orderedExecutions = participants.stream()
                .sorted(Comparator.comparingInt(LifecycleParticipant::priority))
                .map(LifecycleParticipant::execute)
                .toList();

        assertThat(module.projectName()).isEqualTo("demo-project");
        assertThat(orderedExecutions)
                .containsExactly("validate:demo-project", "compile:demo-project", "package:demo-project");
    }

    @Test
    void scopesCanModelSingletonSessionAndMojoLifetimes() {
        ScopedBuildContainer container = new ScopedBuildContainer();

        GlobalSettings firstGlobalSettings = container.globalSettings();
        GlobalSettings secondGlobalSettings = container.globalSettings();
        SessionState firstSession = container.sessionState("session-a");
        SessionState reusedSession = container.sessionState("session-a");
        SessionState secondSession = container.sessionState("session-b");
        MojoState compileMojo = container.mojoState("session-a", "compile");
        MojoState reusedCompileMojo = container.mojoState("session-a", "compile");
        MojoState testMojo = container.mojoState("session-a", "test");

        assertThat(firstGlobalSettings).isSameAs(secondGlobalSettings);
        assertThat(firstSession).isSameAs(reusedSession);
        assertThat(firstSession).isNotSameAs(secondSession);
        assertThat(compileMojo).isSameAs(reusedCompileMojo);
        assertThat(compileMojo).isNotSameAs(testMojo);
        assertThat(firstGlobalSettings.id()).isEqualTo(1);
        assertThat(firstSession.id()).isEqualTo("session-a");
        assertThat(compileMojo.goal()).isEqualTo("compile");
    }

    @Test
    void typedBindingsCanExposeOnlySelectedContracts() {
        ComponentRegistry registry = new ComponentRegistry();
        DefaultGoalExecutor executor = new DefaultGoalExecutor("native-image");

        registry.register("default-goal", executor, Set.of(GoalExecutor.class, BuildStep.class));

        assertThat(registry.lookup("default-goal", GoalExecutor.class))
                .hasValueSatisfying(goalExecutor -> assertThat(goalExecutor.execute())
                        .isEqualTo("execute:native-image"));
        assertThat(registry.lookup("default-goal", BuildStep.class))
                .hasValueSatisfying(buildStep -> assertThat(buildStep.name()).isEqualTo("native-image"));
        assertThat(registry.lookup("default-goal", DefaultGoalExecutor.class)).isEmpty();
    }

    @Test
    void customQualifierCanSelectOfflineAndOnlineRepositoryClients() {
        RepositoryCatalog catalog = new RepositoryCatalog(
                List.of(new RepositoryClient("central", false), new RepositoryClient("local-cache", true)));

        assertThat(catalog.online().coordinatePath("org.example:api:1.0.0"))
                .isEqualTo("central/org.example:api:1.0.0/online");
        assertThat(catalog.offline().coordinatePath("org.example:api:1.0.0"))
                .isEqualTo("local-cache/org.example:api:1.0.0/offline");
    }

    @Test
    void diIndexProcessorPublishesItsSupportedAnnotationContract() {
        DiIndexProcessor processor = new DiIndexProcessor();

        assertThat(processor.getSupportedAnnotationTypes()).containsExactly(Named.class.getName());
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.RELEASE_17);
        assertThat(processor.getSupportedOptions()).isEmpty();
    }

    @Test
    void diIndexProcessorWritesSortedNamedTypeIndex() {
        DiIndexProcessor processor = new DiIndexProcessor();
        RecordingFiler filer = new RecordingFiler("# generated by previous compile\ncom.example.Existing\n");
        processor.init(new SimpleProcessingEnvironment(filer));

        PackageElement packageElement = new SimplePackageElement("org.example.build");
        TypeElement alpha = new SimpleTypeElement("AlphaExtension", packageElement);
        TypeElement outer = new SimpleTypeElement("OuterExtension", packageElement);
        TypeElement nested = new SimpleTypeElement("NestedExtension", outer);

        boolean firstRoundClaimed = processor.process(Set.of(), new SimpleRoundEnvironment(false, Set.of(alpha, nested)));
        boolean finalRoundClaimed = processor.process(Set.of(), new SimpleRoundEnvironment(true, Set.of()));

        assertThat(firstRoundClaimed).isTrue();
        assertThat(finalRoundClaimed).isTrue();
        assertThat(filer.writtenContent())
                .isEqualTo("""
                        com.example.Existing
                        org.example.build.AlphaExtension
                        org.example.build.OuterExtension$NestedExtension
                        """);
    }

    @Test
    void diIndexProcessorSkipsWritingWhenNamedTypeIndexIsUnchanged() {
        DiIndexProcessor processor = new DiIndexProcessor();
        RecordingFiler filer = new RecordingFiler("""
                org.example.build.AlphaExtension
                org.example.build.BetaExtension
                """);
        processor.init(new SimpleProcessingEnvironment(filer));

        PackageElement packageElement = new SimplePackageElement("org.example.build");
        TypeElement alpha = new SimpleTypeElement("AlphaExtension", packageElement);
        TypeElement beta = new SimpleTypeElement("BetaExtension", packageElement);

        boolean firstRoundClaimed = processor.process(Set.of(), new SimpleRoundEnvironment(false, Set.of(beta, alpha)));
        boolean finalRoundClaimed = processor.process(Set.of(), new SimpleRoundEnvironment(true, Set.of()));

        assertThat(firstRoundClaimed).isTrue();
        assertThat(finalRoundClaimed).isTrue();
        assertThat(filer.createdResources()).isZero();
        assertThat(filer.writtenContent()).isEmpty();
    }

    @Test
    void diIndexProcessorIsRegisteredAsAServiceProvider() {
        List<Processor> processors = ServiceLoader.load(Processor.class).stream()
                .filter(provider -> provider.type().equals(DiIndexProcessor.class))
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(processors)
                .singleElement()
                .isInstanceOf(DiIndexProcessor.class);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
    @interface Offline {
    }

    @Scope
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface BuildScoped {
    }

    interface BuildStep {
        String name();
    }

    interface GoalExecutor {
        String execute();
    }

    @Named("default-goal")
    @Typed({GoalExecutor.class, BuildStep.class})
    static final class DefaultGoalExecutor implements GoalExecutor, BuildStep {
        private final String name;

        @Inject
        DefaultGoalExecutor(@Named("goal") String name) {
            this.name = name;
        }

        @Override
        public String execute() {
            return "execute:" + name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @Singleton
    static final class GlobalSettings {
        private final int id;

        GlobalSettings(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }
    }

    @SessionScoped
    static final class SessionState {
        private final String id;

        SessionState(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    @MojoExecutionScoped
    static final class MojoState {
        private final String goal;

        MojoState(String goal) {
            this.goal = goal;
        }

        String goal() {
            return goal;
        }
    }

    @BuildScoped
    static final class RepositoryClient {
        private final String repositoryId;
        private final boolean offline;

        RepositoryClient(String repositoryId, boolean offline) {
            this.repositoryId = repositoryId;
            this.offline = offline;
        }

        String coordinatePath(String coordinates) {
            String mode = offline ? "offline" : "online";
            return repositoryId + "/" + coordinates + "/" + mode;
        }

        boolean offline() {
            return offline;
        }
    }

    static final class ArtifactResolver {
        private final SessionState sessionState;
        private final String coordinates;

        @Inject
        @Offline
        private RepositoryClient repositoryClient;

        private MojoState mojoState;

        @Inject
        ArtifactResolver(SessionState sessionState, @Named("coordinates") String coordinates) {
            this.sessionState = sessionState;
            this.coordinates = coordinates;
        }

        @Inject
        void setMojoState(MojoState mojoState) {
            this.mojoState = mojoState;
        }

        String resolve() {
            return sessionState.id() + "/" + mojoState.goal() + "/" + repositoryClient.coordinatePath(coordinates);
        }

        RepositoryClient repositoryClient() {
            return repositoryClient;
        }
    }

    static final class BuildComponentFactory {
        private final SessionState sessionState;
        private final MojoState mojoState;
        private final RepositoryClient repositoryClient;

        BuildComponentFactory(SessionState sessionState, MojoState mojoState, RepositoryClient repositoryClient) {
            this.sessionState = sessionState;
            this.mojoState = mojoState;
            this.repositoryClient = repositoryClient;
        }

        ArtifactResolver createResolver(String coordinates) {
            ArtifactResolver resolver = new ArtifactResolver(sessionState, coordinates);
            resolver.repositoryClient = repositoryClient;
            resolver.setMojoState(mojoState);
            return resolver;
        }
    }

    static final class LifecycleParticipant {
        private final String phase;
        private final String projectName;
        private final int priority;

        LifecycleParticipant(String phase, String projectName, int priority) {
            this.phase = phase;
            this.projectName = projectName;
            this.priority = priority;
        }

        int priority() {
            return priority;
        }

        String execute() {
            return phase + ":" + projectName;
        }
    }

    static final class BuildModule {
        private final String projectName;

        BuildModule(String projectName) {
            this.projectName = projectName;
        }

        @Provides
        @Named("projectName")
        @Singleton
        String projectName() {
            return projectName;
        }

        @Provides
        @Named("validate")
        @Priority(10)
        LifecycleParticipant validateParticipant() {
            return new LifecycleParticipant("validate", projectName, 10);
        }

        @Provides
        @Named("compile")
        @Priority(20)
        LifecycleParticipant compileParticipant() {
            return new LifecycleParticipant("compile", projectName, 20);
        }

        @Provides
        @Named("package")
        @Priority(30)
        LifecycleParticipant packageParticipant() {
            return new LifecycleParticipant("package", projectName, 30);
        }
    }

    static final class ScopedBuildContainer {
        private final AtomicInteger globalSettingIds = new AtomicInteger();
        private final Map<String, SessionState> sessions = new LinkedHashMap<>();
        private final Map<String, MojoState> mojoExecutions = new LinkedHashMap<>();
        private GlobalSettings globalSettings;

        @Provides
        @Singleton
        GlobalSettings globalSettings() {
            if (globalSettings == null) {
                globalSettings = new GlobalSettings(globalSettingIds.incrementAndGet());
            }
            return globalSettings;
        }

        @Provides
        @SessionScoped
        SessionState sessionState(String id) {
            return sessions.computeIfAbsent(id, SessionState::new);
        }

        @Provides
        @MojoExecutionScoped
        MojoState mojoState(String sessionId, String goal) {
            return mojoExecutions.computeIfAbsent(sessionId + ":" + goal, ignored -> new MojoState(goal));
        }
    }

    static final class ComponentRegistry {
        private final Map<String, RegisteredComponent> components = new LinkedHashMap<>();

        void register(String name, Object component, Set<Class<?>> exposedTypes) {
            components.put(name, new RegisteredComponent(component, exposedTypes));
        }

        <T> Optional<T> lookup(String name, Class<T> type) {
            RegisteredComponent registeredComponent = components.get(name);
            if (registeredComponent == null || !registeredComponent.exposedTypes().contains(type)) {
                return Optional.empty();
            }
            return Optional.of(type.cast(registeredComponent.component()));
        }
    }

    static final class RegisteredComponent {
        private final Object component;
        private final Set<Class<?>> exposedTypes;

        RegisteredComponent(Object component, Set<Class<?>> exposedTypes) {
            this.component = component;
            this.exposedTypes = Set.copyOf(exposedTypes);
        }

        Object component() {
            return component;
        }

        Set<Class<?>> exposedTypes() {
            return exposedTypes;
        }
    }

    static final class RepositoryCatalog {
        private final List<RepositoryClient> clients;

        RepositoryCatalog(List<RepositoryClient> clients) {
            this.clients = new ArrayList<>(clients);
        }

        @Named("online")
        RepositoryClient online() {
            return clients.stream()
                    .filter(client -> !client.offline())
                    .findFirst()
                    .orElseThrow();
        }

        @Named("offline")
        @Offline
        RepositoryClient offline() {
            return clients.stream()
                    .filter(RepositoryClient::offline)
                    .findFirst()
                    .orElseThrow();
        }
    }

    static final class SimpleProcessingEnvironment implements ProcessingEnvironment {
        private final Filer filer;
        private final Messager messager = new RecordingMessager();

        SimpleProcessingEnvironment(Filer filer) {
            this.filer = filer;
        }

        @Override
        public Map<String, String> getOptions() {
            return Map.of();
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
            return null;
        }

        @Override
        public Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.RELEASE_17;
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    static final class RecordingMessager implements Messager {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void printMessage(Kind kind, CharSequence msg) {
            messages.add(kind + ":" + msg);
        }

        @Override
        public void printMessage(Kind kind, CharSequence msg, Element element) {
            printMessage(kind, msg);
        }

        @Override
        public void printMessage(Kind kind, CharSequence msg, Element element, AnnotationMirror annotationMirror) {
            printMessage(kind, msg);
        }

        @Override
        public void printMessage(
                Kind kind,
                CharSequence msg,
                Element element,
                AnnotationMirror annotationMirror,
                AnnotationValue annotationValue) {
            printMessage(kind, msg);
        }
    }

    static final class RecordingFiler implements Filer {
        private final String existingContent;
        private final StringWriter writtenContent = new StringWriter();
        private int createdResources;

        RecordingFiler(String existingContent) {
            this.existingContent = existingContent;
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
            throw new FilerException("source files are not used by this test");
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
            throw new FilerException("class files are not used by this test");
        }

        @Override
        public FileObject createResource(
                Location location, CharSequence moduleAndPkg, CharSequence relativeName, Element... originatingElements) {
            createdResources++;
            return new InMemoryFileObject(relativeName.toString(), writtenContent);
        }

        @Override
        public FileObject getResource(Location location, CharSequence moduleAndPkg, CharSequence relativeName) {
            return new InMemoryFileObject(relativeName.toString(), existingContent);
        }

        int createdResources() {
            return createdResources;
        }

        String writtenContent() {
            return writtenContent.toString();
        }
    }

    static final class InMemoryFileObject extends SimpleJavaFileObject {
        private final String content;
        private final StringWriter output;

        InMemoryFileObject(String name, String content) {
            super(URI.create("mem:///" + name), JavaFileObject.Kind.OTHER);
            this.content = content;
            this.output = null;
        }

        InMemoryFileObject(String name, StringWriter output) {
            super(URI.create("mem:///" + name), JavaFileObject.Kind.OTHER);
            this.content = null;
            this.output = output;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            throw new IOException("binary output is not used by this test");
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) {
            return new StringReader(content);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }

        @Override
        public Writer openWriter() {
            return output;
        }
    }

    static final class SimpleRoundEnvironment implements RoundEnvironment {
        private final boolean processingOver;
        private final Set<? extends Element> namedElements;

        SimpleRoundEnvironment(boolean processingOver, Set<? extends Element> namedElements) {
            this.processingOver = processingOver;
            this.namedElements = namedElements;
        }

        @Override
        public boolean processingOver() {
            return processingOver;
        }

        @Override
        public boolean errorRaised() {
            return false;
        }

        @Override
        public Set<? extends Element> getRootElements() {
            return namedElements;
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotationType) {
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> annotationType) {
            if (annotationType.equals(Named.class)) {
                return namedElements;
            }
            return Set.of();
        }
    }

    static final class SimplePackageElement implements PackageElement {
        private final Name qualifiedName;

        SimplePackageElement(String qualifiedName) {
            this.qualifiedName = new SimpleName(qualifiedName);
        }

        @Override
        public TypeMirror asType() {
            return null;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of();
        }

        @Override
        public Name getSimpleName() {
            String packageName = qualifiedName.toString();
            int lastSeparator = packageName.lastIndexOf('.');
            if (lastSeparator < 0) {
                return qualifiedName;
            }
            return new SimpleName(packageName.substring(lastSeparator + 1));
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
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException("annotations are not used by this test");
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            return visitor.visitPackage(this, parameter);
        }

        @Override
        public Name getQualifiedName() {
            return qualifiedName;
        }

        @Override
        public boolean isUnnamed() {
            return qualifiedName.length() == 0;
        }
    }

    static final class SimpleTypeElement implements TypeElement {
        private final Name simpleName;
        private final Element enclosingElement;

        SimpleTypeElement(String simpleName, Element enclosingElement) {
            this.simpleName = new SimpleName(simpleName);
            this.enclosingElement = enclosingElement;
        }

        @Override
        public TypeMirror asType() {
            return null;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.CLASS;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of();
        }

        @Override
        public Name getSimpleName() {
            return simpleName;
        }

        @Override
        public Element getEnclosingElement() {
            return enclosingElement;
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
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new UnsupportedOperationException("annotations are not used by this test");
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            return visitor.visitType(this, parameter);
        }

        @Override
        public NestingKind getNestingKind() {
            if (enclosingElement instanceof TypeElement) {
                return NestingKind.MEMBER;
            }
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public Name getQualifiedName() {
            List<String> names = new ArrayList<>();
            names.add(simpleName.toString());
            Element current = enclosingElement;
            while (current instanceof TypeElement typeElement) {
                names.add(typeElement.getSimpleName().toString());
                current = typeElement.getEnclosingElement();
            }
            Collections.reverse(names);
            String qualifiedName = String.join(".", names);
            if (current instanceof PackageElement packageElement) {
                return new SimpleName(packageElement.getQualifiedName() + "." + qualifiedName);
            }
            return new SimpleName(qualifiedName);
        }

        @Override
        public TypeMirror getSuperclass() {
            return null;
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

    static final class SimpleName implements Name {
        private final String value;

        SimpleName(String value) {
            this.value = value;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return value.contentEquals(cs);
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
            if (this == other) {
                return true;
            }
            if (other instanceof Name name) {
                return contentEquals(name);
            }
            return false;
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
}
