/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_aether.aether_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.selector.StaticDependencySelector;
import org.eclipse.aether.util.graph.traverser.AndDependencyTraverser;
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser;
import org.eclipse.aether.util.graph.traverser.StaticDependencyTraverser;
import org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.PostorderNodeListGenerator;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.ChainedWorkspaceReader;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.junit.jupiter.api.Test;

public class Aether_utilTest {
    @Test
    void artifactUtilitiesCreateStableCoordinatesAndDerivedArtifacts() throws IOException {
        File binary = File.createTempFile("aether-util", ".jar");
        Artifact artifact = new DefaultArtifact("com.acme", "demo", "tests", "jar", "1.0.0-SNAPSHOT")
                .setFile(binary);

        assertThat(ArtifactIdUtils.toId(artifact)).isEqualTo("com.acme:demo:jar:tests:1.0.0-SNAPSHOT");
        assertThat(ArtifactIdUtils.toBaseId(artifact)).isEqualTo("com.acme:demo:jar:tests:1.0.0-SNAPSHOT");
        assertThat(ArtifactIdUtils.toVersionlessId(artifact)).isEqualTo("com.acme:demo:jar:tests");
        assertThat(ArtifactIdUtils.toId("com.acme", "demo", "jar", "", "1.0.0"))
                .isEqualTo("com.acme:demo:jar:1.0.0");

        Artifact sameCoordinates = new DefaultArtifact("com.acme", "demo", "tests", "jar", "1.0.0-SNAPSHOT");
        Artifact newVersion = new DefaultArtifact("com.acme", "demo", "tests", "jar", "1.0.1");
        assertThat(ArtifactIdUtils.equalsId(artifact, sameCoordinates)).isTrue();
        assertThat(ArtifactIdUtils.equalsVersionlessId(artifact, newVersion)).isTrue();
        assertThat(ArtifactIdUtils.equalsId(artifact, newVersion)).isFalse();

        Map<String, String> properties = Collections.singletonMap("purpose", "verification");
        Artifact sources = new SubArtifact(artifact, "sources", "jar", properties, binary);
        assertThat(sources.getGroupId()).isEqualTo("com.acme");
        assertThat(sources.getArtifactId()).isEqualTo("demo");
        assertThat(sources.getVersion()).isEqualTo("1.0.0-SNAPSHOT");
        assertThat(sources.getClassifier()).isEqualTo("sources");
        assertThat(sources.getExtension()).isEqualTo("jar");
        assertThat(sources.getProperties()).containsEntry("purpose", "verification");
        assertThat(sources.getFile()).isEqualTo(binary);
    }

    @Test
    void artifactTypeRegistryAppliesExtensionClassifierAndProperties() {
        DefaultArtifactTypeRegistry registry = new DefaultArtifactTypeRegistry();
        registry.add(new DefaultArtifactType(
                "test-jar", "jar", "tests", Collections.singletonMap(ArtifactProperties.TYPE, "test-jar")));

        assertThat(registry.get("test-jar").getExtension()).isEqualTo("jar");
        assertThat(registry.get("test-jar").getClassifier()).isEqualTo("tests");
        assertThat(registry.get("test-jar").getProperties()).containsEntry(ArtifactProperties.TYPE, "test-jar");
        assertThat(registry.get("missing")).isNull();
        assertThat(registry.toString()).contains("test-jar");
    }

    @Test
    void configUtilsReturnsTypedValuesFromFirstAvailableKey() {
        Map<String, Object> configuration = new LinkedHashMap<String, Object>();
        configuration.put("string", "value");
        configuration.put("integer", "17");
        configuration.put("long", Long.valueOf(42L));
        configuration.put("float", "1.25");
        configuration.put("boolean", "true");
        configuration.put("list", Arrays.asList("a", "b"));
        configuration.put("map", Collections.singletonMap("nested", "present"));

        assertThat(ConfigUtils.getString(configuration, "fallback", "missing", "string")).isEqualTo("value");
        assertThat(ConfigUtils.getInteger(configuration, -1, "integer")).isEqualTo(17);
        assertThat(ConfigUtils.getLong(configuration, -1L, "long")).isEqualTo(42L);
        assertThat(ConfigUtils.getFloat(configuration, -1.0f, "float")).isEqualTo(1.25f);
        assertThat(ConfigUtils.getBoolean(configuration, false, "boolean")).isTrue();
        assertThat(ConfigUtils.getList(configuration, Collections.emptyList(), "list"))
                .isEqualTo(Arrays.asList("a", "b"));
        assertThat(ConfigUtils.getMap(configuration, Collections.emptyMap(), "map"))
                .isEqualTo(Collections.singletonMap("nested", "present"));
        assertThat(ConfigUtils.getObject(configuration, "fallback", "unknown")).isEqualTo("fallback");
    }

    @Test
    void checksumUtilitiesReadAndCalculateCommonDigestFormats() throws IOException {
        File data = File.createTempFile("aether-checksum-data", ".txt");
        Files.write(data.toPath(), "abc".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> checksums = ChecksumUtils.calc(data, Arrays.asList("MD5", "SHA-1"));
        assertThat(checksums).containsEntry("MD5", "900150983cd24fb0d6963f7d28e17f72");
        assertThat(checksums).containsEntry("SHA-1", "a9993e364706816aba3e25717850c26c9cd0d89d");
        assertThat(ChecksumUtils.toHexString(new byte[] {0, 15, 16, -1})).isEqualTo("000f10ff");

        File checksumFile = File.createTempFile("aether-checksum", ".sha1");
        Files.write(
                checksumFile.toPath(),
                Arrays.asList("", "SHA1(data.txt)= A9993E364706816ABA3E25717850C26C9CD0D89D"),
                StandardCharsets.UTF_8);
        assertThat(ChecksumUtils.read(checksumFile)).isEqualTo("A9993E364706816ABA3E25717850C26C9CD0D89D");
    }

    @Test
    void dependencyFiltersComposeScopesAndExclusions() {
        DependencyNode compileNode = node("org.example", "core", JavaScopes.COMPILE);
        DependencyNode runtimeNode = node("org.example", "runtime", JavaScopes.RUNTIME);
        DependencyNode testNode = node("org.example", "test-helper", JavaScopes.TEST);

        DependencyFilter compileOrRuntime = new ScopeDependencyFilter(
                Arrays.asList(JavaScopes.COMPILE, JavaScopes.RUNTIME), Collections.singleton(JavaScopes.TEST));
        assertThat(compileOrRuntime.accept(compileNode, Collections.<DependencyNode>emptyList())).isTrue();
        assertThat(compileOrRuntime.accept(runtimeNode, Collections.<DependencyNode>emptyList())).isTrue();
        assertThat(compileOrRuntime.accept(testNode, Collections.<DependencyNode>emptyList())).isFalse();

        DependencyFilter exclusions = new ExclusionsDependencyFilter(
                Arrays.asList("test-helper", "org.example:runtime"));
        assertThat(exclusions.accept(compileNode, Collections.<DependencyNode>emptyList())).isTrue();
        assertThat(exclusions.accept(runtimeNode, Collections.<DependencyNode>emptyList())).isFalse();
        assertThat(exclusions.accept(testNode, Collections.<DependencyNode>emptyList())).isFalse();

        DependencyFilter acceptedByBoth = DependencyFilterUtils.andFilter(compileOrRuntime, exclusions);
        DependencyFilter acceptedByEither = DependencyFilterUtils.orFilter(compileOrRuntime, exclusions);
        assertThat(acceptedByBoth.accept(compileNode, Collections.<DependencyNode>emptyList())).isTrue();
        assertThat(acceptedByBoth.accept(runtimeNode, Collections.<DependencyNode>emptyList())).isFalse();
        assertThat(acceptedByEither.accept(testNode, Collections.<DependencyNode>emptyList())).isFalse();
        boolean rejectedRuntimeIsAcceptedByNegatedFilter = DependencyFilterUtils.notFilter(exclusions)
                .accept(runtimeNode, Collections.<DependencyNode>emptyList());
        assertThat(rejectedRuntimeIsAcceptedByNegatedFilter).isTrue();
    }

    @Test
    void dependencySelectorsHandleScopesOptionalDependenciesAndTransitiveExclusions() {
        Dependency compileDependency = dependency("org.example", "core", JavaScopes.COMPILE, false);
        Dependency providedDependency = dependency("org.example", "provided-api", JavaScopes.PROVIDED, false);
        Dependency optionalDependency = dependency("org.example", "optional", JavaScopes.RUNTIME, true);

        DependencySelector staticSelector = new StaticDependencySelector(true);
        assertThat(staticSelector.selectDependency(optionalDependency)).isTrue();
        assertThat(new StaticDependencySelector(false).selectDependency(compileDependency)).isFalse();

        DependencySelector rootScopeSelector = new ScopeDependencySelector(JavaScopes.PROVIDED, JavaScopes.TEST);
        DependencySelector transitiveScopeSelector = rootScopeSelector.deriveChildSelector(
                new CollectionContext(compileDependency));
        assertThat(rootScopeSelector.selectDependency(providedDependency)).isTrue();
        assertThat(transitiveScopeSelector.selectDependency(compileDependency)).isTrue();
        assertThat(transitiveScopeSelector.selectDependency(providedDependency)).isFalse();

        DependencySelector optionalSelector = new OptionalDependencySelector();
        DependencySelector firstLevelSelector = optionalSelector.deriveChildSelector(new CollectionContext(null));
        DependencySelector transitiveSelector = firstLevelSelector.deriveChildSelector(
                new CollectionContext(compileDependency));
        assertThat(optionalSelector.selectDependency(optionalDependency)).isTrue();
        assertThat(firstLevelSelector.selectDependency(optionalDependency)).isTrue();
        assertThat(transitiveSelector.selectDependency(optionalDependency)).isFalse();
        assertThat(transitiveSelector.selectDependency(compileDependency)).isTrue();

        Exclusion exclusion = new Exclusion("org.blocked", "blocked-artifact", "*", "*");
        Dependency dependencyWithExclusion = new Dependency(
                artifact("org.parent", "parent"), JavaScopes.COMPILE, Boolean.FALSE, Collections.singleton(exclusion));
        DependencySelector exclusionSelector = new ExclusionDependencySelector()
                .deriveChildSelector(new CollectionContext(dependencyWithExclusion));
        Dependency blockedDependency = dependency("org.blocked", "blocked-artifact", JavaScopes.RUNTIME, false);
        Dependency allowedDependency = dependency("org.allowed", "blocked-artifact", JavaScopes.RUNTIME, false);
        assertThat(exclusionSelector.selectDependency(blockedDependency)).isFalse();
        assertThat(exclusionSelector.selectDependency(allowedDependency)).isTrue();
    }

    @Test
    void dependencyManagerAppliesManagedTransitiveDependencyAttributes() {
        Exclusion inheritedExclusion = new Exclusion("org.blocked", "blocked-transitive", "*", "*");
        Dependency managedDependency = new Dependency(
                artifact("org.example", "managed-module"),
                JavaScopes.RUNTIME,
                Boolean.TRUE,
                Collections.singleton(inheritedExclusion));

        DependencyManager dependencyManager = new ClassicDependencyManager()
                .deriveChildManager(new ManagedDependenciesContext(Collections.singletonList(managedDependency)))
                .deriveChildManager(new ManagedDependenciesContext(Collections.<Dependency>emptyList()));

        Exclusion requestedExclusion = new Exclusion("org.request", "request-specific", "*", "*");
        Dependency requestedDependency = new Dependency(
                artifact("org.example", "managed-module"),
                JavaScopes.COMPILE,
                Boolean.FALSE,
                Collections.singleton(requestedExclusion));
        DependencyManagement management = dependencyManager.manageDependency(requestedDependency);

        assertThat(management.getVersion()).isEqualTo("1.0.0");
        assertThat(management.getScope()).isEqualTo(JavaScopes.RUNTIME);
        assertThat(management.getOptional()).isTrue();
        assertThat(management.getExclusions()).containsExactlyInAnyOrder(requestedExclusion, inheritedExclusion);
        DependencyManagement unmanaged = dependencyManager.manageDependency(
                dependency("org.example", "unmanaged-module", JavaScopes.COMPILE, false));
        assertThat(unmanaged).isNull();
    }

    @Test
    void dependencyTraversersSkipFatArtifactsAndComposeTraversalRules() {
        Dependency thinDependency = new Dependency(artifact("org.example", "thin-module"), JavaScopes.COMPILE);
        Artifact fatArtifact = artifact("org.example", "fat-module")
                .setProperties(Collections.singletonMap(ArtifactProperties.INCLUDES_DEPENDENCIES, "true"));
        Dependency fatDependency = new Dependency(fatArtifact, JavaScopes.RUNTIME);

        DependencyTraverser fatArtifactTraverser = new FatArtifactTraverser();
        assertThat(fatArtifactTraverser.traverseDependency(thinDependency)).isTrue();
        assertThat(fatArtifactTraverser.traverseDependency(fatDependency)).isFalse();

        DependencyTraverser allowingChain = AndDependencyTraverser.newInstance(
                fatArtifactTraverser, new StaticDependencyTraverser(true));
        assertThat(allowingChain.traverseDependency(thinDependency)).isTrue();
        assertThat(allowingChain.traverseDependency(fatDependency)).isFalse();
        assertThat(allowingChain.deriveChildTraverser(new CollectionContext(thinDependency))).isSameAs(allowingChain);

        DependencyTraverser blockingChain = AndDependencyTraverser.newInstance(
                fatArtifactTraverser, new StaticDependencyTraverser(false));
        assertThat(blockingChain.traverseDependency(thinDependency)).isFalse();
        assertThat(AndDependencyTraverser.newInstance(fatArtifactTraverser, null)).isSameAs(fatArtifactTraverser);
    }

    @Test
    void graphVisitorsProduceTraversalListsAndMatchingPaths() throws IOException {
        DefaultDependencyNode root = new DefaultDependencyNode(artifact("org.example", "root"));
        DependencyNode childA = node("org.example", "child-a", JavaScopes.COMPILE);
        DependencyNode childB = node("org.example", "child-b", JavaScopes.RUNTIME);
        DependencyNode grandChild = node("org.example", "grand-child", JavaScopes.RUNTIME);
        childA.setArtifact(childA.getArtifact().setFile(File.createTempFile("child-a", ".jar")));
        grandChild.setArtifact(grandChild.getArtifact().setFile(File.createTempFile("grand-child", ".jar")));
        childA.setChildren(Collections.singletonList(grandChild));
        root.setChildren(Arrays.asList(childA, childB));

        PreorderNodeListGenerator preorder = new PreorderNodeListGenerator();
        root.accept(preorder);
        assertThat(artifactIds(preorder.getArtifacts(true)))
                .containsExactly("child-a", "grand-child", "child-b");
        assertThat(artifactIds(preorder.getArtifacts(false))).containsExactly("child-a", "grand-child");
        assertThat(scopes(preorder.getDependencies(true)))
                .containsExactly(JavaScopes.COMPILE, JavaScopes.RUNTIME, JavaScopes.RUNTIME);

        PostorderNodeListGenerator postorder = new PostorderNodeListGenerator();
        root.accept(postorder);
        assertThat(artifactIds(postorder.getArtifacts(true)))
                .containsExactly("grand-child", "child-a", "child-b");

        DependencyFilter grandChildFilter = (dependencyNode, parents) ->
                "grand-child".equals(dependencyNode.getArtifact().getArtifactId());
        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(grandChildFilter);
        root.accept(visitor);
        assertThat(visitor.getPaths()).hasSize(1);
        assertThat(artifactIdsFromPath(visitor.getPaths().get(0))).containsExactly("root", "child-a", "grand-child");
    }

    @Test
    void repositorySelectorsApplyMirrorsProxiesAndAuthentication() {
        RemoteRepository central = new RemoteRepository.Builder(
                "central", "default", "https://repo.maven.apache.org/maven2").build();
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector()
                .add("internal", "https://repo.example.test/maven", "default", false, "*", "*");

        RemoteRepository mirror = mirrorSelector.getMirror(central);
        assertThat(mirror.getId()).isEqualTo("internal");
        assertThat(mirror.getUrl()).isEqualTo("https://repo.example.test/maven");
        assertThat(mirror.getMirroredRepositories()).containsExactly(central);

        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.test", 8080);
        DefaultProxySelector proxySelector = new DefaultProxySelector().add(proxy, "localhost|*.internal.test");
        RemoteRepository externalHttp = new RemoteRepository.Builder(
                "external", "default", "http://downloads.example.test/repo").build();
        RemoteRepository localHttp = new RemoteRepository.Builder("local", "default", "http://localhost/repo").build();
        assertThat(proxySelector.getProxy(externalHttp)).isEqualTo(proxy);
        assertThat(proxySelector.getProxy(localHttp)).isNull();

        Authentication authentication = new AuthenticationBuilder()
                .addUsername("scott")
                .addPassword("tiger")
                .addNtlm("WORKSTATION", "DOMAIN")
                .build();
        RemoteRepository authenticated = new RemoteRepository.Builder(central)
                .setAuthentication(authentication)
                .build();
        AuthenticationContext context = AuthenticationContext.forRepository(
                new DefaultRepositorySystemSession(), authenticated);
        try {
            assertThat(context.get(AuthenticationContext.USERNAME)).isEqualTo("scott");
            assertThat(context.get(AuthenticationContext.PASSWORD)).isEqualTo("tiger");
            assertThat(context.get(AuthenticationContext.NTLM_DOMAIN)).isEqualTo("DOMAIN");
            assertThat(context.get(AuthenticationContext.NTLM_WORKSTATION)).isEqualTo("WORKSTATION");
        } finally {
            AuthenticationContext.close(context);
        }
    }

    @Test
    void chainedWorkspaceReaderDelegatesFindsAndMergesVersions() throws IOException {
        Artifact release = new DefaultArtifact("org.example", "workspace-artifact", "jar", "1.0.0");
        File releaseFile = File.createTempFile("workspace-artifact", ".jar");
        WorkspaceReader first = new TestWorkspaceReader("first", Collections.singletonMap(release, releaseFile),
                Collections.singletonMap("workspace-artifact", Arrays.asList("1.0.0", "1.1.0")));
        WorkspaceReader second = new TestWorkspaceReader("second", Collections.<Artifact, File>emptyMap(),
                Collections.singletonMap("workspace-artifact", Arrays.asList("1.1.0", "2.0.0")));

        WorkspaceReader chained = ChainedWorkspaceReader.newInstance(first, second);
        assertThat(chained.findArtifact(release)).isEqualTo(releaseFile);
        assertThat(chained.findVersions(release)).containsExactly("1.0.0", "1.1.0", "2.0.0");
        assertThat(chained.getRepository().getContentType()).isEqualTo("first+second");
        assertThat(chained.getRepository().getId()).isEqualTo("workspace");
    }

    @Test
    void genericVersionSchemeComparesVersionsAndEvaluatesConstraints()
            throws InvalidVersionSpecificationException {
        GenericVersionScheme scheme = new GenericVersionScheme();
        Version alpha = scheme.parseVersion("1.0-alpha-1");
        Version release = scheme.parseVersion("1.0");
        Version patch = scheme.parseVersion("1.0.1");
        assertThat(alpha.compareTo(release)).isLessThan(0);
        assertThat(release.compareTo(patch)).isLessThan(0);
        assertThat(scheme.parseVersion("1.0.0").compareTo(release)).isEqualTo(0);

        VersionConstraint range = scheme.parseVersionConstraint("[1.0,2.0)");
        assertThat(range.getVersion()).isNull();
        assertThat(range.containsVersion(release)).isTrue();
        assertThat(range.containsVersion(scheme.parseVersion("2.0"))).isFalse();

        VersionConstraint exact = scheme.parseVersionConstraint("1.5");
        assertThat(exact.getVersion().toString()).isEqualTo("1.5");
        assertThat(exact.containsVersion(scheme.parseVersion("1.5"))).isTrue();
        assertThatThrownBy(() -> scheme.parseVersionRange("[1.0,2.0"))
                .isInstanceOf(InvalidVersionSpecificationException.class);
    }

    private static Artifact artifact(String groupId, String artifactId) {
        return new DefaultArtifact(groupId, artifactId, "jar", "1.0.0");
    }

    private static Dependency dependency(String groupId, String artifactId, String scope, boolean optional) {
        return new Dependency(artifact(groupId, artifactId), scope, Boolean.valueOf(optional));
    }

    private static DependencyNode node(String groupId, String artifactId, String scope) {
        return new DefaultDependencyNode(dependency(groupId, artifactId, scope, false));
    }

    private static List<String> artifactIds(List artifacts) {
        List<String> artifactIds = new ArrayList<String>();
        for (Object object : artifacts) {
            Artifact artifact = (Artifact) object;
            artifactIds.add(artifact.getArtifactId());
        }
        return artifactIds;
    }

    private static List<String> scopes(List dependencies) {
        List<String> scopes = new ArrayList<String>();
        for (Object object : dependencies) {
            Dependency dependency = (Dependency) object;
            scopes.add(dependency.getScope());
        }
        return scopes;
    }

    private static List<String> artifactIdsFromPath(List<DependencyNode> path) {
        List<String> artifactIds = new ArrayList<String>();
        for (DependencyNode node : path) {
            artifactIds.add(node.getArtifact().getArtifactId());
        }
        return artifactIds;
    }

    private static final class CollectionContext implements DependencyCollectionContext {
        private final Dependency dependency;

        private CollectionContext(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public RepositorySystemSession getSession() {
            return null;
        }

        @Override
        public Artifact getArtifact() {
            return dependency != null ? dependency.getArtifact() : null;
        }

        @Override
        public Dependency getDependency() {
            return dependency;
        }

        @Override
        public List<Dependency> getManagedDependencies() {
            return Collections.emptyList();
        }
    }

    private static final class ManagedDependenciesContext implements DependencyCollectionContext {
        private final List<Dependency> managedDependencies;

        private ManagedDependenciesContext(List<Dependency> managedDependencies) {
            this.managedDependencies = managedDependencies;
        }

        @Override
        public RepositorySystemSession getSession() {
            return null;
        }

        @Override
        public Artifact getArtifact() {
            return null;
        }

        @Override
        public Dependency getDependency() {
            return null;
        }

        @Override
        public List<Dependency> getManagedDependencies() {
            return managedDependencies;
        }
    }

    private static final class TestWorkspaceReader implements WorkspaceReader {
        private final WorkspaceRepository repository;
        private final Map<Artifact, File> artifacts;
        private final Map<String, List<String>> versions;

        private TestWorkspaceReader(String id, Map<Artifact, File> artifacts, Map<String, List<String>> versions) {
            this.repository = new WorkspaceRepository(id);
            this.artifacts = artifacts;
            this.versions = versions;
        }

        @Override
        public WorkspaceRepository getRepository() {
            return repository;
        }

        @Override
        public File findArtifact(Artifact artifact) {
            return artifacts.get(artifact);
        }

        @Override
        public List<String> findVersions(Artifact artifact) {
            List<String> result = versions.get(artifact.getArtifactId());
            return result != null ? result : Collections.<String>emptyList();
        }
    }
}
