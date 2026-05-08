/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_spi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.ExtensibleEnum;
import org.apache.maven.api.JavaPathType;
import org.apache.maven.api.Language;
import org.apache.maven.api.Lifecycle;
import org.apache.maven.api.Packaging;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.PathType;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.ProtoSession;
import org.apache.maven.api.Type;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginContainer;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.spi.ExtensibleEnumProvider;
import org.apache.maven.api.spi.LanguageProvider;
import org.apache.maven.api.spi.LifecycleProvider;
import org.apache.maven.api.spi.ModelParser;
import org.apache.maven.api.spi.ModelParserException;
import org.apache.maven.api.spi.ModelTransformer;
import org.apache.maven.api.spi.ModelTransformerException;
import org.apache.maven.api.spi.PackagingProvider;
import org.apache.maven.api.spi.PathScopeProvider;
import org.apache.maven.api.spi.ProjectScopeProvider;
import org.apache.maven.api.spi.PropertyContributor;
import org.apache.maven.api.spi.SpiService;
import org.apache.maven.api.spi.TypeProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_api_spiTest {
    @Test
    void modelParserLocateAndParseUsesLocatedSourceAndOptions() throws Exception {
        RecordingModelParser parser = new RecordingModelParser();
        Path projectDirectory = Path.of("project");
        Map<String, Object> options = Map.of(ModelParser.STRICT, true, "profile", "native");

        Optional<Model> result = parser.locateAndParse(projectDirectory, options);

        assertThat(result).isPresent();
        assertThat(result.get().getGroupId()).isEqualTo("org.example");
        assertThat(result.get().getArtifactId()).isEqualTo("demo");
        assertThat(result.get().getVersion()).isEqualTo("1.0.0");
        assertThat(result.get().getPackaging()).isEqualTo("jar");
        assertThat(result.get().getName()).isEqualTo("strict-native");
        assertThat(parser.locatedDirectories).containsExactly(projectDirectory);
        assertThat(parser.parsedLocations).containsExactly("memory:project/pom.xml");
        assertThat(parser.seenOptions).containsExactly(options);
    }

    @Test
    void modelParserLocateAndParseDoesNotParseWhenLocateReturnsEmpty() throws Exception {
        RecordingModelParser parser = new RecordingModelParser(false);

        Optional<Model> result = parser.locateAndParse(Path.of("without-pom"), Map.of(ModelParser.STRICT, false));

        assertThat(result).isEmpty();
        assertThat(parser.parsedLocations).isEmpty();
        assertThat(parser.seenOptions).isEmpty();
    }

    @Test
    void modelParserExceptionCarriesMessageCauseAndLocation() {
        IllegalArgumentException cause = new IllegalArgumentException("bad xml");
        ModelParserException exception = new ModelParserException("Cannot parse model", 12, 8, cause);

        assertThat(exception)
                .hasMessage("Cannot parse model")
                .hasCause(cause);
        assertThat(exception.getLineNumber()).isEqualTo(12);
        assertThat(exception.getColumnNumber()).isEqualTo(8);

        ModelParserException causeOnly = new ModelParserException(cause);
        assertThat(causeOnly)
                .hasMessage(null)
                .hasCause(cause);
        assertThat(causeOnly.getLineNumber()).isEqualTo(-1);
        assertThat(causeOnly.getColumnNumber()).isEqualTo(-1);
    }

    @Test
    void modelParserCanPropagateCheckedParseFailures() {
        FailingModelParser parser = new FailingModelParser(new ModelParserException("malformed", 3, 2, null));

        assertThatExceptionOfType(ModelParserException.class)
                .isThrownBy(() -> parser.locateAndParse(Path.of("broken"), Map.of(ModelParser.STRICT, true)))
                .withMessage("malformed")
                .satisfies(exception -> {
                    assertThat(exception.getLineNumber()).isEqualTo(3);
                    assertThat(exception.getColumnNumber()).isEqualTo(2);
                });
    }

    @Test
    void modelTransformerDefaultsReturnTheSameModelAtEveryStage() throws Exception {
        Model input = sampleModel().build();
        ModelTransformer transformer = new ModelTransformer() {};

        assertThat(transformer.transformFileModel(input)).isSameAs(input);
        assertThat(transformer.transformRawModel(input)).isSameAs(input);
        assertThat(transformer.transformEffectiveModel(input)).isSameAs(input);
    }

    @Test
    void modelTransformerImplementationsCanCreateImmutableDerivedModels() throws Exception {
        Model input = sampleModel().packaging("pom").build();
        ModelTransformer transformer = new RenamingModelTransformer();

        Model fileModel = transformer.transformFileModel(input);
        Model rawModel = transformer.transformRawModel(fileModel);
        Model effectiveModel = transformer.transformEffectiveModel(rawModel);

        assertThat(fileModel).isNotSameAs(input);
        assertThat(fileModel.getName()).isEqualTo("file-demo");
        assertThat(rawModel.getDescription()).isEqualTo("raw-demo");
        assertThat(effectiveModel.getPackaging()).isEqualTo("jar");
        assertThat(input.getName()).isNull();
        assertThat(input.getDescription()).isNull();
        assertThat(input.getPackaging()).isEqualTo("pom");
    }

    @Test
    void modelTransformerExceptionConstructorsPreserveCauseAndMessage() {
        IOException cause = new IOException("write failed");

        assertThat(new ModelTransformerException("transform failed", cause))
                .hasMessage("transform failed")
                .hasCause(cause);
        assertThat(new ModelTransformerException(cause))
                .hasMessage(null)
                .hasCause(cause);
        assertThat(new ModelTransformerException("message only"))
                .hasMessage("message only")
                .hasNoCause();
    }

    @Test
    void propertyContributorCanMutateCollectedUserProperties() {
        ProtoSession protoSession = ProtoSession.newBuilder()
                .withUserProperties(Map.of("cli", "true", "overridden", "user"))
                .withSystemProperties(Map.of("java.home", "/jdk", "overridden", "system"))
                .withStartTime(Instant.parse("2024-01-01T00:00:00Z"))
                .withTopDirectory(Path.of("/workspace/project"))
                .withRootDirectory(Path.of("/workspace"))
                .build();
        PropertyContributor contributor = new PrefixingPropertyContributor();

        Map<String, String> contributed = contributor.contribute(protoSession);

        assertThat(contributed)
                .containsEntry("cli", "true")
                .containsEntry("overridden", "user")
                .containsEntry("contributed.cli", "true")
                .containsEntry("contributed.overridden", "user");
        assertThat(contributed).doesNotContainKey("java.home");
        assertThat(protoSession.getUserProperties()).doesNotContainKey("contributed.cli");
        assertThat(protoSession.getEffectiveProperties())
                .containsEntry("java.home", "/jdk")
                .containsEntry("overridden", "user");
    }

    @Test
    void propertyContributorDirectMapCallbackEditsCollectedPropertiesInPlace() {
        Map<String, String> userProperties = new LinkedHashMap<>();
        userProperties.put("profile", "integration");
        userProperties.put("internal.token", "secret");
        PropertyContributor contributor = new SanitizingPropertyContributor();

        contributor.contribute(userProperties);

        assertThat(userProperties)
                .containsEntry("profile", "integration")
                .containsEntry("build.profile", "integration")
                .doesNotContainKey("internal.token");
    }

    @Test
    void defaultPropertyContributorReturnsIndependentCopyOfUserProperties() {
        ProtoSession protoSession = ProtoSession.newBuilder()
                .withUserProperties(Map.of("original", "value"))
                .withSystemProperties(Map.of("system", "value"))
                .withStartTime(Instant.parse("2024-01-01T00:00:00Z"))
                .withTopDirectory(Path.of("/workspace/project"))
                .withRootDirectory(null)
                .build();
        PropertyContributor contributor = new PropertyContributor() {};

        Map<String, String> contributed = contributor.contribute(protoSession);
        contributed.put("local", "mutation");

        assertThat(contributed).containsEntry("original", "value");
        assertThat(protoSession.getUserProperties()).containsExactly(Map.entry("original", "value"));
        assertThatThrownBy(protoSession::getRootDirectory)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("root directory not set");
        assertThat(protoSession.toBuilder().withRootDirectory(Path.of("/workspace")).build().getRootDirectory())
                .isEqualTo(Path.of("/workspace"));
    }

    @Test
    void extensibleEnumProvidersExposeCustomEnumsAsSpiServices() {
        CustomLanguageProvider languages = new CustomLanguageProvider();
        CustomProjectScopeProvider projectScopes = new CustomProjectScopeProvider();
        CustomPathScopeProvider pathScopes = new CustomPathScopeProvider(projectScopes.provides().iterator().next());
        CustomTypeProvider types = new CustomTypeProvider(languages.provides().iterator().next());
        CustomPackagingProvider packagings = new CustomPackagingProvider(types.provides().iterator().next());
        CustomLifecycleProvider lifecycles = new CustomLifecycleProvider();

        assertProvider(languages, "kotlin");
        assertProvider(projectScopes, "integration-test");
        assertProvider(pathScopes, "integration-test-runtime");
        assertProvider(types, "kotlin-library");
        assertProvider(packagings, "kotlin-jar");
        assertProvider(lifecycles, "docker");
        assertThat(List.of(languages, projectScopes, pathScopes, types, packagings, lifecycles))
                .allSatisfy(provider -> assertThat(provider).isInstanceOf(SpiService.class));
    }

    @Test
    void pathTypeAndPackagingProvidersExposeSemanticDetails() {
        SimpleLanguage language = new SimpleLanguage("kotlin");
        SimpleProjectScope projectScope = new SimpleProjectScope("integration-test");
        SimplePathScope pathScope = new SimplePathScope(
                "integration-test-runtime", projectScope, Set.of(DependencyScope.TEST, DependencyScope.RUNTIME));
        SimpleType type = new SimpleType(
                "kotlin-library", language, "jar", "kotlin", false, Set.of(JavaPathType.CLASSES));
        SimplePackaging packaging = new SimplePackaging(
                "kotlin-jar", type, Map.of(Lifecycle.DEFAULT, PluginContainer.newInstance()));

        assertThat(pathScope.projectScope()).isSameAs(projectScope);
        assertThat(pathScope.dependencyScopes())
                .containsExactlyInAnyOrder(DependencyScope.TEST, DependencyScope.RUNTIME);
        assertThat(type.getLanguage()).isSameAs(language);
        assertThat(type.getExtension()).isEqualTo("jar");
        assertThat(type.getClassifier()).isEqualTo("kotlin");
        assertThat(type.isIncludesDependencies()).isFalse();
        assertThat(type.getPathTypes()).containsExactly(JavaPathType.CLASSES);
        assertThat(packaging.type()).isSameAs(type);
        assertThat(packaging.language()).isSameAs(language);
        assertThat(packaging.plugins()).containsOnlyKeys(Lifecycle.DEFAULT);
    }

    @Test
    void lifecycleProviderCanDescribeNestedPhasesAndAliases() {
        Lifecycle lifecycle = new CustomLifecycleProvider().provides().iterator().next();

        assertThat(lifecycle.id()).isEqualTo("docker");
        assertThat(lifecycle.phases()).extracting(Lifecycle.Phase::name).containsExactly("image");
        assertThat(lifecycle.allPhases().map(Lifecycle.Phase::name))
                .containsExactly("image", "build-image", "push-image");
        assertThat(lifecycle.aliases())
                .extracting(alias -> alias.v3Phase() + "->" + alias.v4Phase())
                .containsExactly("package->build-image");

        Lifecycle.Phase pushImage = lifecycle.allPhases()
                .filter(phase -> phase.name().equals("push-image"))
                .findFirst()
                .orElseThrow();
        assertThat(pushImage.links())
                .extracting(link -> link.kind() + ":" + link.pointer().phase())
                .containsExactly("AFTER:build-image");
    }

    private static Model.Builder sampleModel() {
        return Model.newBuilder()
                .modelVersion("4.0.0")
                .groupId("org.example")
                .artifactId("demo")
                .version("1.0.0");
    }

    private static void assertProvider(ExtensibleEnumProvider<? extends ExtensibleEnum> provider, String expectedId) {
        assertThat(provider.provides()).extracting(ExtensibleEnum::id).containsExactly(expectedId);
    }

    private static final class RecordingModelParser implements ModelParser {
        private final boolean locateModel;
        private final List<Path> locatedDirectories = new ArrayList<>();
        private final List<String> parsedLocations = new ArrayList<>();
        private final List<Map<String, ?>> seenOptions = new ArrayList<>();

        private RecordingModelParser() {
            this(true);
        }

        private RecordingModelParser(boolean locateModel) {
            this.locateModel = locateModel;
        }

        @Override
        public Optional<Source> locate(Path dir) {
            locatedDirectories.add(dir);
            if (!locateModel) {
                return Optional.empty();
            }
            return Optional.of(new MemorySource(dir.resolve("pom.xml"), "artifactId=demo\n"));
        }

        @Override
        public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
            parsedLocations.add(source.getLocation());
            seenOptions.add(options == null ? null : Map.copyOf(options));
            try (InputStream inputStream = source.openStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(content).contains("artifactId=demo");
            } catch (IOException e) {
                throw new ModelParserException("Cannot read source", e);
            }
            boolean strict = Boolean.TRUE.equals(options.get(ModelParser.STRICT));
            return sampleModel().name((strict ? "strict" : "lenient") + "-" + options.get("profile")).build();
        }
    }

    private static final class FailingModelParser implements ModelParser {
        private final ModelParserException failure;

        private FailingModelParser(ModelParserException failure) {
            this.failure = failure;
        }

        @Override
        public Optional<Source> locate(Path dir) {
            return Optional.of(new MemorySource(dir.resolve("pom.xml"), "not a pom"));
        }

        @Override
        public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
            throw failure;
        }
    }

    private static final class MemorySource implements Source {
        private final Path path;
        private final byte[] content;

        private MemorySource(Path path, String content) {
            this.path = path;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public String getLocation() {
            return "memory:" + path;
        }

        @Override
        public Source resolve(String relative) {
            return new MemorySource(
                    path.getParent().resolve(relative).normalize(), new String(content, StandardCharsets.UTF_8));
        }
    }

    private static final class RenamingModelTransformer implements ModelTransformer {
        @Override
        public Model transformFileModel(Model model) {
            return model.withName("file-" + model.getArtifactId());
        }

        @Override
        public Model transformRawModel(Model model) {
            return model.withDescription("raw-" + model.getArtifactId());
        }

        @Override
        public Model transformEffectiveModel(Model model) {
            return model.withPackaging("jar");
        }
    }

    private static final class PrefixingPropertyContributor implements PropertyContributor {
        @Override
        public void contribute(Map<String, String> userProperties) {
            Map<String, String> snapshot = new LinkedHashMap<>(userProperties);
            snapshot.forEach((key, value) -> userProperties.put("contributed." + key, value));
        }
    }

    private static final class SanitizingPropertyContributor implements PropertyContributor {
        @Override
        public void contribute(Map<String, String> userProperties) {
            userProperties.remove("internal.token");
            userProperties.put("build.profile", userProperties.get("profile"));
        }
    }

    private static final class CustomLanguageProvider implements LanguageProvider {
        @Override
        public Collection<Language> provides() {
            return List.of(new SimpleLanguage("kotlin"));
        }
    }

    private static final class CustomProjectScopeProvider implements ProjectScopeProvider {
        @Override
        public Collection<ProjectScope> provides() {
            return List.of(new SimpleProjectScope("integration-test"));
        }
    }

    private static final class CustomPathScopeProvider implements PathScopeProvider {
        private final ProjectScope projectScope;

        private CustomPathScopeProvider(ProjectScope projectScope) {
            this.projectScope = projectScope;
        }

        @Override
        public Collection<PathScope> provides() {
            return List.of(new SimplePathScope(
                    "integration-test-runtime", projectScope, Set.of(DependencyScope.TEST, DependencyScope.RUNTIME)));
        }
    }

    private static final class CustomTypeProvider implements TypeProvider {
        private final Language language;

        private CustomTypeProvider(Language language) {
            this.language = language;
        }

        @Override
        public Collection<Type> provides() {
            return List.of(new SimpleType(
                    "kotlin-library", language, "jar", "kotlin", false, Set.of(JavaPathType.CLASSES)));
        }
    }

    private static final class CustomPackagingProvider implements PackagingProvider {
        private final Type type;

        private CustomPackagingProvider(Type type) {
            this.type = type;
        }

        @Override
        public Collection<Packaging> provides() {
            return List.of(new SimplePackaging(
                    "kotlin-jar", type, Map.of(Lifecycle.DEFAULT, PluginContainer.newInstance())));
        }
    }

    private static final class CustomLifecycleProvider implements LifecycleProvider {
        @Override
        public Collection<Lifecycle> provides() {
            SimplePhase buildImage = new SimplePhase("build-image");
            SimplePhase pushImage = new SimplePhase(
                    "push-image",
                    List.of(),
                    List.of(new SimpleLink(Lifecycle.Link.Kind.AFTER, new SimplePointer("build-image"))),
                    List.of());
            SimplePhase image = new SimplePhase("image", List.of(), List.of(), List.of(buildImage, pushImage));
            return List.of(new SimpleLifecycle(
                    "docker", List.of(image), List.of(new SimpleAlias("package", "build-image"))));
        }
    }

    private abstract static class SimpleExtensibleEnum implements ExtensibleEnum {
        private final String id;

        private SimpleExtensibleEnum(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            SimpleExtensibleEnum that = (SimpleExtensibleEnum) other;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class SimpleLanguage extends SimpleExtensibleEnum implements Language {
        private SimpleLanguage(String id) {
            super(id);
        }
    }

    private static final class SimpleProjectScope extends SimpleExtensibleEnum implements ProjectScope {
        private SimpleProjectScope(String id) {
            super(id);
        }
    }

    private static final class SimplePathScope extends SimpleExtensibleEnum implements PathScope {
        private final ProjectScope projectScope;
        private final Set<DependencyScope> dependencyScopes;

        private SimplePathScope(String id, ProjectScope projectScope, Set<DependencyScope> dependencyScopes) {
            super(id);
            this.projectScope = projectScope;
            this.dependencyScopes = Set.copyOf(dependencyScopes);
        }

        @Override
        public ProjectScope projectScope() {
            return projectScope;
        }

        @Override
        public Set<DependencyScope> dependencyScopes() {
            return dependencyScopes;
        }
    }

    private static final class SimpleType extends SimpleExtensibleEnum implements Type {
        private final Language language;
        private final String extension;
        private final String classifier;
        private final boolean includesDependencies;
        private final Set<PathType> pathTypes;

        private SimpleType(
                String id,
                Language language,
                String extension,
                String classifier,
                boolean includesDependencies,
                Set<PathType> pathTypes) {
            super(id);
            this.language = language;
            this.extension = extension;
            this.classifier = classifier;
            this.includesDependencies = includesDependencies;
            this.pathTypes = Set.copyOf(pathTypes);
        }

        @Override
        public Language getLanguage() {
            return language;
        }

        @Override
        public String getExtension() {
            return extension;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public boolean isIncludesDependencies() {
            return includesDependencies;
        }

        @Override
        public Set<PathType> getPathTypes() {
            return pathTypes;
        }
    }

    private static final class SimplePackaging extends SimpleExtensibleEnum implements Packaging {
        private final Type type;
        private final Map<String, PluginContainer> plugins;

        private SimplePackaging(String id, Type type, Map<String, PluginContainer> plugins) {
            super(id);
            this.type = type;
            this.plugins = Map.copyOf(plugins);
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public Map<String, PluginContainer> plugins() {
            return plugins;
        }
    }

    private static final class SimpleLifecycle extends SimpleExtensibleEnum implements Lifecycle {
        private final List<Phase> phases;
        private final List<Alias> aliases;

        private SimpleLifecycle(String id, List<Phase> phases, List<Alias> aliases) {
            super(id);
            this.phases = List.copyOf(phases);
            this.aliases = List.copyOf(aliases);
        }

        @Override
        public Collection<Phase> phases() {
            return phases;
        }

        @Override
        public Collection<Alias> aliases() {
            return aliases;
        }
    }

    private static final class SimplePhase implements Lifecycle.Phase {
        private final String name;
        private final List<Plugin> plugins;
        private final List<Lifecycle.Link> links;
        private final List<Lifecycle.Phase> phases;

        private SimplePhase(String name) {
            this(name, List.of(), List.of(), List.of());
        }

        private SimplePhase(
                String name, List<Plugin> plugins, List<Lifecycle.Link> links, List<Lifecycle.Phase> phases) {
            this.name = name;
            this.plugins = List.copyOf(plugins);
            this.links = List.copyOf(links);
            this.phases = List.copyOf(phases);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Plugin> plugins() {
            return plugins;
        }

        @Override
        public Collection<Lifecycle.Link> links() {
            return links;
        }

        @Override
        public List<Lifecycle.Phase> phases() {
            return phases;
        }

        @Override
        public Stream<Lifecycle.Phase> allPhases() {
            return Stream.concat(Stream.of(this), phases.stream().flatMap(Lifecycle.Phase::allPhases));
        }
    }

    private static final class SimpleAlias implements Lifecycle.Alias {
        private final String v3Phase;
        private final String v4Phase;

        private SimpleAlias(String v3Phase, String v4Phase) {
            this.v3Phase = v3Phase;
            this.v4Phase = v4Phase;
        }

        @Override
        public String v3Phase() {
            return v3Phase;
        }

        @Override
        public String v4Phase() {
            return v4Phase;
        }
    }

    private static final class SimpleLink implements Lifecycle.Link {
        private final Kind kind;
        private final Lifecycle.Pointer pointer;

        private SimpleLink(Kind kind, Lifecycle.Pointer pointer) {
            this.kind = kind;
            this.pointer = pointer;
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public Lifecycle.Pointer pointer() {
            return pointer;
        }
    }

    private static final class SimplePointer implements Lifecycle.Pointer {
        private final String phase;

        private SimplePointer(String phase) {
            this.phase = phase;
        }

        @Override
        public Lifecycle.Pointer.Type type() {
            return Lifecycle.Pointer.Type.PROJECT;
        }

        @Override
        public String phase() {
            return phase;
        }
    }
}
