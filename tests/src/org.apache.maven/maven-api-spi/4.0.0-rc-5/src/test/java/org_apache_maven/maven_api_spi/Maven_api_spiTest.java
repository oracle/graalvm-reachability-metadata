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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.ExtensibleEnum;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_api_spiTest {
    @Test
    void extensibleEnumProvidersExposeContributedValuesAsSpiServices() {
        LanguageProvider languageProvider = () -> List.of(new TestLanguage("kotlin"), new TestLanguage("scala"));
        ProjectScopeProvider projectScopeProvider = () -> List.of(new TestProjectScope("integration-test"));
        PathScopeProvider pathScopeProvider = () -> List.of(new TestPathScope(
                "integration-runtime",
                new TestProjectScope("integration-test"),
                Set.of(DependencyScope.TEST, DependencyScope.TEST_RUNTIME)));
        TypeProvider typeProvider = () -> List.of(new TestType(
                "native-image-config", "json", "metadata", Language.RESOURCES, Set.of(PathType.UNRESOLVED)));
        PackagingProvider packagingProvider = () -> List.of(new TestPackaging(
                "native-image-metadata",
                new TestType("native-image-config", "json", "metadata", Language.RESOURCES, Set.of()),
                Map.of()));

        assertProvider(languageProvider);
        assertProvider(projectScopeProvider);
        assertProvider(pathScopeProvider);
        assertProvider(typeProvider);
        assertProvider(packagingProvider);

        assertThat(languageProvider.provides()).extracting(Language::id).containsExactly("kotlin", "scala");
        assertThat(projectScopeProvider.provides()).extracting(ProjectScope::id).containsExactly("integration-test");
        assertThat(pathScopeProvider.provides()).singleElement().satisfies(scope -> {
            assertThat(scope.id()).isEqualTo("integration-runtime");
            assertThat(scope.projectScope().id()).isEqualTo("integration-test");
            assertThat(scope.dependencyScopes())
                    .containsExactlyInAnyOrder(DependencyScope.TEST, DependencyScope.TEST_RUNTIME);
        });
        assertThat(typeProvider.provides()).singleElement().satisfies(type -> {
            assertThat(type.id()).isEqualTo("native-image-config");
            assertThat(type.getExtension()).isEqualTo("json");
            assertThat(type.getClassifier()).isEqualTo("metadata");
            assertThat(type.getLanguage()).isSameAs(Language.RESOURCES);
            assertThat(type.getPathTypes()).containsExactly(PathType.UNRESOLVED);
            assertThat(type.isIncludesDependencies()).isFalse();
        });
        assertThat(packagingProvider.provides()).singleElement().satisfies(packaging -> {
            assertThat(packaging.id()).isEqualTo("native-image-metadata");
            assertThat(packaging.language()).isSameAs(Language.RESOURCES);
            assertThat(packaging.plugins()).isEmpty();
        });
    }

    @Test
    void lifecycleProviderSupportsAliasesAndRecursivePhaseTraversal() {
        TestPhase compile = new TestPhase("compile", List.of(), List.of(), List.of());
        TestPhase verify = new TestPhase("verify", List.of(), List.of(), List.of(compile));
        TestLifecycle lifecycle = new TestLifecycle(
                "custom-default", List.of(verify), List.of(new TestAlias("test", "unit-test")));
        LifecycleProvider provider = () -> List.of(lifecycle);

        assertProvider(provider);
        assertThat(provider.provides()).containsExactly(lifecycle);
        assertThat(lifecycle.phases()).containsExactly(verify);
        assertThat(lifecycle.v3phases()).containsExactly(verify);
        assertThat(lifecycle.allPhases()).map(Lifecycle.Phase::name).containsExactly("verify", "compile");
        assertThat(lifecycle.aliases()).singleElement().satisfies(alias -> {
            assertThat(alias.v3Phase()).isEqualTo("test");
            assertThat(alias.v4Phase()).isEqualTo("unit-test");
        });
    }

    @Test
    void modelParserLocateAndParseUsesLocatedSourceAndOptions() {
        Path directory = Path.of("sample-project");
        Map<String, Object> options = Map.of(ModelParser.STRICT, Boolean.TRUE, "profile", "native");
        Model expectedModel = newModel("located-artifact");
        InMemorySource source = new InMemorySource(directory.resolve("pom.xml"), "<project />");
        RecordingModelParser parser = new RecordingModelParser(Optional.of(source), expectedModel);

        Optional<Model> parsedModel = parser.locateAndParse(directory, options);

        assertThat(parsedModel).containsSame(expectedModel);
        assertThat(parser.locatedDirectory).isEqualTo(directory);
        assertThat(parser.parsedSource).isSameAs(source);
        assertThat(parser.parsedOptions).isSameAs(options);
        assertThat(parser.parseCount).isEqualTo(1);
        assertThat(ModelParser.STRICT).isEqualTo("strict");
    }

    @Test
    void modelParserLocateAndParseAcceptsNullOptions() {
        Path directory = Path.of("project-with-default-options");
        Model expectedModel = newModel("default-options");
        InMemorySource source = new InMemorySource(directory.resolve("pom.xml"), "<project />");
        RecordingModelParser parser = new RecordingModelParser(Optional.of(source), expectedModel);

        Optional<Model> parsedModel = parser.locateAndParse(directory, null);

        assertThat(parsedModel).containsSame(expectedModel);
        assertThat(parser.parsedSource).isSameAs(source);
        assertThat(parser.parsedOptions).isNull();
        assertThat(parser.parseCount).isEqualTo(1);
    }

    @Test
    void modelParserLocateAndParseReturnsEmptyWithoutParsingWhenNoSourceIsFound() {
        RecordingModelParser parser = new RecordingModelParser(Optional.empty(), newModel("unused"));

        Optional<Model> parsedModel = parser.locateAndParse(Path.of("project-without-pom"), Map.of());

        assertThat(parsedModel).isEmpty();
        assertThat(parser.parseCount).isZero();
    }

    @Test
    void modelParserLocateAndParsePropagatesParserFailuresWithLocationDetails() {
        Path directory = Path.of("invalid-project");
        Map<String, Object> options = Map.of(ModelParser.STRICT, Boolean.TRUE);
        InMemorySource source = new InMemorySource(directory.resolve("pom.xml"), "<project>");
        IOException cause = new IOException("unclosed input");
        ModelParserException failure = new ModelParserException("invalid model", 3, 15, cause);
        AtomicReference<Source> parsedSource = new AtomicReference<>();
        AtomicReference<Map<String, ?>> parsedOptions = new AtomicReference<>();
        ModelParser parser = new ModelParser() {
            @Override
            public Optional<Source> locate(Path dir) {
                assertThat(dir).isEqualTo(directory);
                return Optional.of(source);
            }

            @Override
            public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
                parsedSource.set(source);
                parsedOptions.set(options);
                throw failure;
            }
        };

        assertThatThrownBy(() -> parser.locateAndParse(directory, options))
                .isSameAs(failure)
                .isInstanceOf(ModelParserException.class)
                .hasMessage("invalid model")
                .hasCause(cause)
                .satisfies(throwable -> {
                    ModelParserException exception = (ModelParserException) throwable;
                    assertThat(exception.getLineNumber()).isEqualTo(3);
                    assertThat(exception.getColumnNumber()).isEqualTo(15);
                });
        assertThat(parsedSource.get()).isSameAs(source);
        assertThat(parsedOptions.get()).isSameAs(options);
    }

    @Test
    void sourceImplementationsCanOpenResolveAndDescribeModelLocations() throws IOException {
        InMemorySource source = new InMemorySource(Path.of("project/pom.xml"), "model-text");

        try (InputStream input = source.openStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("model-text");
        }
        assertThat(source.getPath()).isEqualTo(Path.of("project/pom.xml"));
        assertThat(source.getLocation()).isEqualTo("memory:project/pom.xml");
        assertThat(source.resolve("child.xml").getPath()).isEqualTo(Path.of("project/child.xml"));
    }

    @Test
    void propertyContributorSessionDefaultCopiesUserPropertiesAndAppliesMapContribution() {
        ProtoSession protoSession = ProtoSession.newBuilder()
                .withUserProperties(Map.of("existing", "user", "overridden", "old"))
                .withSystemProperties(Map.of("system", "value", "overridden", "system"))
                .withStartTime(Instant.EPOCH)
                .withTopDirectory(Path.of("."))
                .withRootDirectory(Path.of("."))
                .build();
        PropertyContributor contributor = new PropertyContributor() {
            @Override
            public void contribute(Map<String, String> userProperties) {
                userProperties.put("added", "contributed");
                userProperties.put("overridden", "new");
            }
        };

        Map<String, String> contributed = contributor.contribute(protoSession);

        assertThat(contributed)
                .containsEntry("existing", "user")
                .containsEntry("added", "contributed")
                .containsEntry("overridden", "new")
                .doesNotContainEntry("system", "value");
        assertThat(protoSession.getUserProperties())
                .containsEntry("overridden", "old")
                .doesNotContainKey("added");
        contributed.put("later", "still-mutable");
        assertThat(contributed).containsEntry("later", "still-mutable");
    }

    @Test
    void propertyContributorNoOpDefaultsPreserveInputProperties() {
        PropertyContributor contributor = new PropertyContributor() {};
        Map<String, String> directProperties = new HashMap<>(Map.of("key", "value"));
        ProtoSession protoSession = ProtoSession.newBuilder()
                .withUserProperties(Map.of("key", "value"))
                .withSystemProperties(Map.of())
                .withStartTime(Instant.EPOCH)
                .withTopDirectory(Path.of("."))
                .withRootDirectory(Path.of("."))
                .build();

        contributor.contribute(directProperties);
        Map<String, String> contributed = contributor.contribute(protoSession);

        assertThat(directProperties).containsExactly(Map.entry("key", "value"));
        assertThat(contributed).containsExactly(Map.entry("key", "value"));
        assertThat(contributed).isNotSameAs(protoSession.getUserProperties());
    }

    @Test
    void modelTransformerDefaultsReturnInputModelAndOverridesCanTransformSpecificStages() {
        Model input = newModel("original");
        ModelTransformer identityTransformer = new ModelTransformer() {};
        ModelTransformer rawTransformer = new ModelTransformer() {
            @Override
            public Model transformRawModel(Model model) throws ModelTransformerException {
                return model.withArtifactId("raw-transformed");
            }
        };

        assertThat(identityTransformer.transformFileModel(input)).isSameAs(input);
        assertThat(identityTransformer.transformRawModel(input)).isSameAs(input);
        assertThat(identityTransformer.transformEffectiveModel(input)).isSameAs(input);
        assertThat(rawTransformer.transformFileModel(input)).isSameAs(input);
        assertThat(rawTransformer.transformRawModel(input).getArtifactId()).isEqualTo("raw-transformed");
        assertThat(rawTransformer.transformEffectiveModel(input)).isSameAs(input);
    }

    @Test
    void spiExceptionsPreserveMessagesCausesAndParserLocations() {
        IllegalArgumentException cause = new IllegalArgumentException("bad input");

        ModelParserException located = new ModelParserException("cannot parse", 12, 7, cause);
        ModelParserException messageOnly = new ModelParserException("message only");
        ModelTransformerException transformer = new ModelTransformerException("cannot transform", cause);

        assertThat(located)
                .hasMessage("cannot parse")
                .hasCause(cause);
        assertThat(located.getLineNumber()).isEqualTo(12);
        assertThat(located.getColumnNumber()).isEqualTo(7);
        assertThat(messageOnly.getLineNumber()).isEqualTo(-1);
        assertThat(messageOnly.getColumnNumber()).isEqualTo(-1);
        assertThat(new ModelParserException(cause)).hasCause(cause);
        assertThat(new ModelTransformerException(cause)).hasCause(cause);
        assertThat(transformer)
                .hasMessage("cannot transform")
                .hasCause(cause);
        assertThatThrownBy(() -> {
            throw located;
        }).isInstanceOf(ModelParserException.class).hasMessage("cannot parse");
    }

    private static void assertProvider(ExtensibleEnumProvider<? extends ExtensibleEnum> provider) {
        assertThat(provider).isInstanceOf(SpiService.class);
        assertThat(provider.provides()).isNotNull();
    }

    private static Model newModel(String artifactId) {
        return Model.newBuilder()
                .modelVersion("4.0.0")
                .groupId("org.example")
                .artifactId(artifactId)
                .version("1.0.0")
                .build();
    }

    private record TestLanguage(String id) implements Language {}

    private record TestProjectScope(String id) implements ProjectScope {}

    private record TestPathScope(String id, ProjectScope projectScope, Set<DependencyScope> dependencyScopes)
            implements PathScope {}

    private record TestType(
            String id,
            String getExtension,
            String getClassifier,
            Language getLanguage,
            Set<PathType> getPathTypes)
            implements Type {
        @Override
        public boolean isIncludesDependencies() {
            return false;
        }
    }

    private record TestPackaging(String id, Type type, Map<String, PluginContainer> plugins) implements Packaging {}

    private record TestLifecycle(String id, Collection<Lifecycle.Phase> phases, Collection<Lifecycle.Alias> aliases)
            implements Lifecycle {}

    private record TestAlias(String v3Phase, String v4Phase) implements Lifecycle.Alias {}

    private record TestPhase(
            String name, List<Plugin> plugins, Collection<Lifecycle.Link> links, List<Lifecycle.Phase> phases)
            implements Lifecycle.Phase {
        @Override
        public Stream<Lifecycle.Phase> allPhases() {
            return Stream.concat(Stream.of(this), phases.stream().flatMap(Lifecycle.Phase::allPhases));
        }
    }

    private static final class InMemorySource implements Source {
        private final Path path;
        private final String content;

        private InMemorySource(Path path, String content) {
            this.path = path;
            this.content = content;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String getLocation() {
            return "memory:" + path;
        }

        @Override
        public Source resolve(String relative) {
            return new InMemorySource(path.resolveSibling(relative), content);
        }
    }

    private static final class RecordingModelParser implements ModelParser {
        private final Optional<Source> locatedSource;
        private final Model parsedModel;
        private Path locatedDirectory;
        private Source parsedSource;
        private Map<String, ?> parsedOptions;
        private int parseCount;

        private RecordingModelParser(Optional<Source> locatedSource, Model parsedModel) {
            this.locatedSource = locatedSource;
            this.parsedModel = parsedModel;
        }

        @Override
        public Optional<Source> locate(Path dir) {
            locatedDirectory = dir;
            return locatedSource;
        }

        @Override
        public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
            parseCount++;
            parsedSource = source;
            parsedOptions = options;
            return parsedModel;
        }
    }
}
