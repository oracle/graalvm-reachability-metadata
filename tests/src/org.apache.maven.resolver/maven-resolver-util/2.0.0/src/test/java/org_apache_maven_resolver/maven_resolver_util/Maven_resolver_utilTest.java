/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.collection.VersionFilter.VersionFilterContext;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.aether.util.StringDigestUtil;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser;
import org.eclipse.aether.util.graph.version.ChainedVersionFilter;
import org.eclipse.aether.util.graph.version.HighestVersionFilter;
import org.eclipse.aether.util.graph.version.SnapshotVersionFilter;
import org.eclipse.aether.util.graph.visitor.CloningDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.PostorderNodeListGenerator;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_resolver_utilTest {
    @TempDir
    private Path tempDir;

    @Test
    public void artifactUtilitiesCreateStableIdentifiersAndSubArtifacts() {
        Artifact artifact = new DefaultArtifact("com.example", "demo", "tests", "jar", "1.0.0");
        Artifact sameCoordinates = new DefaultArtifact("com.example", "demo", "tests", "jar", "1.0.0");
        Artifact differentClassifier = new DefaultArtifact("com.example", "demo", "sources", "jar", "1.0.0");

        assertThat(ArtifactIdUtils.toId(artifact)).isEqualTo("com.example:demo:jar:tests:1.0.0");
        assertThat(ArtifactIdUtils.toBaseId(artifact)).isEqualTo("com.example:demo:jar:tests:1.0.0");
        assertThat(ArtifactIdUtils.toVersionlessId(artifact)).isEqualTo("com.example:demo:jar:tests");
        assertThat(ArtifactIdUtils.equalsId(artifact, sameCoordinates)).isTrue();
        assertThat(ArtifactIdUtils.equalsVersionlessId(artifact, differentClassifier)).isFalse();

        Artifact subArtifact = new SubArtifact(artifact, "sources", "jar", Map.of("language", "java"));

        assertThat(subArtifact.getGroupId()).isEqualTo("com.example");
        assertThat(subArtifact.getArtifactId()).isEqualTo("demo");
        assertThat(subArtifact.getVersion()).isEqualTo("1.0.0");
        assertThat(subArtifact.getClassifier()).isEqualTo("sources");
        assertThat(subArtifact.getExtension()).isEqualTo("jar");
        assertThat(subArtifact.getProperties()).containsEntry("language", "java");
    }

    @Test
    public void artifactTypeRegistryCombinesDefaultAndCustomArtifactTypes() {
        DefaultArtifactTypeRegistry registry = new DefaultArtifactTypeRegistry();
        ArtifactType jarType = new DefaultArtifactType("jar");
        ArtifactType nativeType = new DefaultArtifactType("native", "so", "linux-x86_64", JavaScopes.RUNTIME);

        registry.add(jarType).add(nativeType);

        assertThat(registry.get("jar").getExtension()).isEqualTo("jar");
        assertThat(registry.get("jar").getClassifier()).isEmpty();
        assertThat(registry.get("native").getExtension()).isEqualTo("so");
        assertThat(registry.get("native").getClassifier()).isEqualTo("linux-x86_64");
        assertThat(registry.get("native").getProperties()).containsEntry("type", "native");
    }

    @Test
    public void genericVersionSchemeOrdersVersionsAndEvaluatesRanges() throws Exception {
        GenericVersionScheme versionScheme = new GenericVersionScheme();
        Version previous = versionScheme.parseVersion("1.9.24");
        Version current = versionScheme.parseVersion("1.9.25");
        Version nextMajor = versionScheme.parseVersion("2.0.0");
        Version snapshot = versionScheme.parseVersion("1.9.25-SNAPSHOT");
        VersionRange range = versionScheme.parseVersionRange("[1.9.0,2.0.0)");
        VersionConstraint exactConstraint = versionScheme.parseVersionConstraint("1.9.25");
        VersionConstraint rangedConstraint = versionScheme.parseVersionConstraint("[1.9.20,1.9.30]");

        assertThat(previous.compareTo(current)).isNegative();
        assertThat(current.compareTo(snapshot)).isPositive();
        assertThat(range.containsVersion(current)).isTrue();
        assertThat(range.containsVersion(nextMajor)).isFalse();
        assertThat(exactConstraint.getVersion()).isEqualTo(current);
        assertThat(exactConstraint.containsVersion(current)).isTrue();
        assertThat(rangedConstraint.containsVersion(previous)).isTrue();
        assertThat(rangedConstraint.containsVersion(nextMajor)).isFalse();
    }

    @Test
    public void configAndDigestUtilitiesCoerceValuesDeterministically() {
        Map<String, Object> configuration = Map.of(
                "threads", "8",
                "timeout", 1500L,
                "enabled", "true",
                "names", Arrays.asList("central", "snapshots"),
                "nested", Map.of("checksum", "sha1"));

        assertThat(ConfigUtils.getInteger(configuration, 1, "threads")).isEqualTo(8);
        assertThat(ConfigUtils.getLong(configuration, 0L, "timeout")).isEqualTo(1500L);
        assertThat(ConfigUtils.getBoolean(configuration, false, "enabled")).isTrue();
        assertThat(ConfigUtils.getString(configuration, "fallback", "missing", "threads")).isEqualTo("8");
        assertThat(ConfigUtils.getList(configuration, List.of(), "names")).isEqualTo(List.of("central", "snapshots"));
        assertThat(ConfigUtils.getMap(configuration, Map.of(), "nested")).isEqualTo(Map.of("checksum", "sha1"));
        assertThat(ConfigUtils.parseCommaSeparatedNames("alpha,beta,,gamma"))
                .containsExactly("alpha", "beta", "gamma");
        assertThat(ConfigUtils.parseCommaSeparatedUniqueNames("alpha,beta,alpha,gamma"))
                .containsExactly("alpha", "beta", "gamma");
        assertThat(StringDigestUtil.sha1("resolver-util"))
                .isEqualTo(StringDigestUtil.sha1().update("resolver-util").digest());
    }

    @Test
    public void checksumUtilitiesCalculateAndRoundTripHexDigests() throws Exception {
        byte[] content = "resolver".getBytes(StandardCharsets.UTF_8);
        byte[] expectedSha1 = MessageDigest.getInstance("SHA-1").digest(content);
        String expectedSha1Hex = ChecksumUtils.toHexString(expectedSha1);

        Map<String, Object> checksums = ChecksumUtils.calc(content, Arrays.asList("SHA-1", "MD5"));

        assertThat(checksums).containsKeys("SHA-1", "MD5");
        assertThat(checksums.get("SHA-1")).isEqualTo(expectedSha1Hex);
        assertThat(ChecksumUtils.fromHexString(expectedSha1Hex)).containsExactly(expectedSha1);
    }

    @Test
    public void fileUtilitiesWriteAtomicallyAndMoveCollocatedTemporaryFiles() throws IOException {
        Path target = tempDir.resolve("metadata.txt");

        FileUtils.writeFile(target, path -> Files.writeString(path, "first", StandardCharsets.UTF_8));
        FileUtils.writeFileWithBackup(target, path -> Files.writeString(path, "second", StandardCharsets.UTF_8));
        Path movedTarget = tempDir.resolve("moved.txt");
        try (FileUtils.CollocatedTempFile temporaryFile = FileUtils.newTempFile(movedTarget)) {
            Files.writeString(temporaryFile.getPath(), "third", StandardCharsets.UTF_8);
            temporaryFile.move();
        }

        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("second");
        assertThat(Files.readString(movedTarget, StandardCharsets.UTF_8)).isEqualTo("third");
    }

    @Test
    public void dependencyFiltersComposeScopeAndExclusionRules() {
        DependencyNode compileNode = node(dependency("compile-artifact", JavaScopes.COMPILE));
        DependencyNode runtimeNode = node(dependency("runtime-artifact", JavaScopes.RUNTIME));
        DependencyNode testNode = node(dependency("test-artifact", JavaScopes.TEST));
        DependencyFilter runtimeClasspath = DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME);
        DependencyFilter notRuntimeClasspath = DependencyFilterUtils.notFilter(runtimeClasspath);
        DependencyFilter namedRuntime = (node, parents) -> node.getArtifact().getArtifactId().startsWith("runtime");
        DependencyFilter runtimeAndNamed = DependencyFilterUtils.andFilter(runtimeClasspath, namedRuntime);
        DependencyFilter testOrNamed = DependencyFilterUtils.orFilter(
                DependencyFilterUtils.classpathFilter(JavaScopes.TEST), namedRuntime);

        assertThat(runtimeClasspath.accept(compileNode, Collections.emptyList())).isTrue();
        assertThat(runtimeClasspath.accept(runtimeNode, Collections.emptyList())).isTrue();
        assertThat(runtimeClasspath.accept(testNode, Collections.emptyList())).isFalse();
        assertThat(notRuntimeClasspath.accept(testNode, Collections.emptyList())).isTrue();
        assertThat(runtimeAndNamed.accept(runtimeNode, Collections.emptyList())).isTrue();
        assertThat(runtimeAndNamed.accept(compileNode, Collections.emptyList())).isFalse();
        assertThat(testOrNamed.accept(testNode, Collections.emptyList())).isTrue();
        assertThat(testOrNamed.accept(runtimeNode, Collections.emptyList())).isTrue();
    }

    @Test
    public void dependencySelectorsApplyOptionalAndScopeRulesToTransitiveDependencies() {
        Dependency optionalDependency = dependency("optional-artifact", JavaScopes.COMPILE).setOptional(Boolean.TRUE);
        Dependency runtimeDependency = dependency("runtime-artifact", JavaScopes.RUNTIME);
        Dependency testDependency = dependency("test-artifact", JavaScopes.TEST);
        DependencySelector optionalSelector = new OptionalDependencySelector();
        DependencySelector transitiveOptionalSelector = optionalSelector
                .deriveChildSelector(collectionContext(runtimeDependency))
                .deriveChildSelector(collectionContext(runtimeDependency));
        DependencySelector scopeSelector = new ScopeDependencySelector(null, List.of(JavaScopes.TEST))
                .deriveChildSelector(collectionContext(runtimeDependency));

        assertThat(optionalSelector.selectDependency(optionalDependency)).isTrue();
        assertThat(transitiveOptionalSelector.selectDependency(optionalDependency)).isFalse();
        assertThat(transitiveOptionalSelector.selectDependency(runtimeDependency)).isTrue();
        assertThat(scopeSelector.selectDependency(runtimeDependency)).isTrue();
        assertThat(scopeSelector.selectDependency(testDependency)).isFalse();
    }

    @Test
    public void exclusionDependencySelectorPropagatesParentExclusionsToChildren() {
        Exclusion blockedArtifact = new Exclusion("com.example", "blocked-artifact", "*", "jar");
        Exclusion testFixture = new Exclusion("com.example", "fixtures", "tests", "jar");
        Dependency parentDependency = new Dependency(
                artifact("parent-artifact"), JavaScopes.COMPILE, null, List.of(blockedArtifact, testFixture));
        DependencySelector rootSelector = new ExclusionDependencySelector();
        DependencySelector childSelector = rootSelector.deriveChildSelector(collectionContext(parentDependency));
        Dependency blockedDependency = dependency("blocked-artifact", JavaScopes.RUNTIME);
        Dependency regularFixtureDependency = dependency("fixtures", JavaScopes.RUNTIME);
        Dependency testFixtureDependency = new Dependency(
                new DefaultArtifact("com.example", "fixtures", "tests", "jar", "1.0.0"), JavaScopes.RUNTIME);
        Dependency allowedDependency = dependency("allowed-artifact", JavaScopes.RUNTIME);

        assertThat(rootSelector.selectDependency(blockedDependency)).isTrue();
        assertThat(childSelector.selectDependency(blockedDependency)).isFalse();
        assertThat(childSelector.selectDependency(testFixtureDependency)).isFalse();
        assertThat(childSelector.selectDependency(regularFixtureDependency)).isTrue();
        assertThat(childSelector.selectDependency(allowedDependency)).isTrue();
    }

    @Test
    public void fatArtifactTraverserStopsAtArtifactsThatAlreadyIncludeDependencies() {
        Dependency normalDependency = new Dependency(
                artifact("normal", Map.of(ArtifactProperties.INCLUDES_DEPENDENCIES, "false")),
                JavaScopes.RUNTIME);
        Dependency shadedDependency = new Dependency(
                artifact("shaded", Map.of(ArtifactProperties.INCLUDES_DEPENDENCIES, "true")),
                JavaScopes.RUNTIME);
        DependencyTraverser traverser = new FatArtifactTraverser();

        assertThat(traverser.traverseDependency(normalDependency)).isTrue();
        assertThat(traverser.traverseDependency(shadedDependency)).isFalse();
        assertThat(traverser.deriveChildTraverser(collectionContext(normalDependency))).isSameAs(traverser);
    }

    @Test
    public void graphVisitorsTraverseCloneAndRecordMatchingPaths() {
        DependencyNode root = node(dependency("root", JavaScopes.COMPILE));
        DependencyNode firstChild = node(dependency("first-child", JavaScopes.RUNTIME));
        DependencyNode secondChild = node(dependency("second-child", JavaScopes.TEST));
        root.setChildren(Arrays.asList(firstChild, secondChild));

        PreorderNodeListGenerator preorder = new PreorderNodeListGenerator();
        PostorderNodeListGenerator postorder = new PostorderNodeListGenerator();
        PathRecordingDependencyVisitor pathRecorder = new PathRecordingDependencyVisitor(
                (node, parents) -> "second-child".equals(node.getArtifact().getArtifactId()));
        CloningDependencyVisitor cloningVisitor = new CloningDependencyVisitor();

        root.accept(preorder);
        root.accept(postorder);
        root.accept(pathRecorder);
        root.accept(cloningVisitor);

        assertThat(preorder.getNodes()).containsExactly(root, firstChild, secondChild);
        assertThat(postorder.getNodes()).containsExactly(firstChild, secondChild, root);
        assertThat(pathRecorder.getPaths()).hasSize(1);
        assertThat(pathRecorder.getPaths().get(0)).containsExactly(root, secondChild);
        assertThat(cloningVisitor.getRootNode()).isNotSameAs(root);
        assertThat(cloningVisitor.getRootNode().getArtifact()).isEqualTo(root.getArtifact());
        assertThat(cloningVisitor.getRootNode().getChildren()).hasSize(2);
    }

    @Test
    public void dependencyManagerUtilitiesExposePremanagedNodeData() {
        DefaultDependencyNode node = node(dependency("managed", JavaScopes.RUNTIME));
        node.setManagedBits(DependencyNode.MANAGED_VERSION
                | DependencyNode.MANAGED_SCOPE
                | DependencyNode.MANAGED_OPTIONAL
                | DependencyNode.MANAGED_PROPERTIES);
        node.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION, "1.0.0");
        node.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_SCOPE, JavaScopes.COMPILE);
        node.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_OPTIONAL, Boolean.TRUE);
        node.setData(DependencyManagerUtils.NODE_DATA_PREMANAGED_PROPERTIES, Map.of("classifier", "tests"));

        assertThat(DependencyManagerUtils.getPremanagedVersion(node)).isEqualTo("1.0.0");
        assertThat(DependencyManagerUtils.getPremanagedScope(node)).isEqualTo(JavaScopes.COMPILE);
        assertThat(DependencyManagerUtils.getPremanagedOptional(node)).isTrue();
        assertThat(DependencyManagerUtils.getPremanagedProperties(node)).containsEntry("classifier", "tests");
    }

    @Test
    public void repositorySelectorsChooseMirrorsProxiesAndAuthentication() {
        RemoteRepository central = repository("central", "https://repo.maven.apache.org/maven2");
        RemoteRepository local = repository("local", "file:/tmp/repository");
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector()
                .add("mirror", "https://mirror.example.org/maven2", "default", false, "external:*", "*");
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.org", 8080);
        DefaultProxySelector proxySelector = new DefaultProxySelector().add(proxy, "repo.maven.apache.org");
        Authentication authentication = new AuthenticationBuilder()
                .addUsername("alice")
                .addPassword("secret")
                .addString("realm", "integration")
                .build();
        DefaultAuthenticationSelector authenticationSelector = new DefaultAuthenticationSelector()
                .add("central", authentication);

        RemoteRepository mirror = mirrorSelector.getMirror(central);
        RemoteRepository proxied = repository("proxied", "https://downloads.example.org/releases");

        assertThat(mirror.getId()).isEqualTo("mirror");
        assertThat(mirror.getUrl()).isEqualTo("https://mirror.example.org/maven2");
        assertThat(mirror.getMirroredRepositories()).containsExactly(central);
        assertThat(mirrorSelector.getMirror(local)).isNull();
        assertThat(proxySelector.getProxy(central)).isNull();
        assertThat(proxySelector.getProxy(proxied)).isEqualTo(proxy);
        assertThat(authenticationSelector.getAuthentication(central)).isSameAs(authentication);

        RemoteRepository secured = new RemoteRepository.Builder(central).setAuthentication(authentication).build();
        AuthenticationContext context = AuthenticationContext.forRepository(new DefaultRepositorySystemSession(), secured);
        try {
            assertThat(context.get(AuthenticationContext.USERNAME)).isEqualTo("alice");
            assertThat(context.get(AuthenticationContext.PASSWORD)).isEqualTo("secret");
            assertThat(context.get("realm")).isEqualTo("integration");
        } finally {
            AuthenticationContext.close(context);
        }
    }

    @Test
    public void chainedVersionFilterComposesReusableFilters() {
        VersionFilter filter = ChainedVersionFilter.newInstance(new ArrayList<VersionFilter>());

        assertThat(filter).isNull();
    }

    @Test
    public void versionFiltersPruneSnapshotAndSelectHighestReleaseCandidates() throws Exception {
        GenericVersionScheme versionScheme = new GenericVersionScheme();
        TestVersionFilterContext context = versionFilterContext(
                versionScheme.parseVersionConstraint("[1.0,2.0)"),
                versionScheme.parseVersion("1.0.0"),
                versionScheme.parseVersion("1.1.0-SNAPSHOT"),
                versionScheme.parseVersion("1.1.0"));

        new SnapshotVersionFilter().filterVersions(context);

        assertThat(context.getVersions()).extracting(Object::toString).containsExactly("1.0.0", "1.1.0");

        new HighestVersionFilter().filterVersions(context);

        assertThat(context.getVersions()).extracting(Object::toString).containsExactly("1.1.0");
        assertThat(context.getCount()).isOne();
    }

    private static Artifact artifact(String artifactId) {
        return artifact(artifactId, Map.of());
    }

    private static Artifact artifact(String artifactId, Map<String, String> properties) {
        return new DefaultArtifact(
                "com.example",
                artifactId,
                "",
                "jar",
                "1.0.0",
                properties,
                (File) null);
    }

    private static Dependency dependency(String artifactId, String scope) {
        return new Dependency(artifact(artifactId), scope);
    }

    private static DefaultDependencyNode node(Dependency dependency) {
        return new DefaultDependencyNode(dependency);
    }

    private static RemoteRepository repository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }

    private static DependencyCollectionContext collectionContext(Dependency dependency) {
        return new TestDependencyCollectionContext(dependency);
    }

    private static TestVersionFilterContext versionFilterContext(
            VersionConstraint versionConstraint, Version... versions) {
        return new TestVersionFilterContext(
                dependency("version-candidate", JavaScopes.COMPILE),
                versionConstraint,
                Arrays.asList(versions));
    }

    private static final class TestDependencyCollectionContext implements DependencyCollectionContext {
        private final Dependency dependency;

        private TestDependencyCollectionContext(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public RepositorySystemSession getSession() {
            return null;
        }

        @Override
        public Artifact getArtifact() {
            return dependency.getArtifact();
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

    private static final class TestVersionFilterContext implements VersionFilterContext {
        private final Dependency dependency;
        private final VersionConstraint versionConstraint;
        private final List<Version> versions;

        private TestVersionFilterContext(
                Dependency dependency,
                VersionConstraint versionConstraint,
                List<Version> versions) {
            this.dependency = dependency;
            this.versionConstraint = versionConstraint;
            this.versions = new ArrayList<>(versions);
        }

        @Override
        public RepositorySystemSession getSession() {
            return null;
        }

        @Override
        public Dependency getDependency() {
            return dependency;
        }

        @Override
        public int getCount() {
            return versions.size();
        }

        @Override
        public Iterator<Version> iterator() {
            return versions.iterator();
        }

        @Override
        public VersionConstraint getVersionConstraint() {
            return versionConstraint;
        }

        @Override
        public ArtifactRepository getRepository(Version version) {
            return null;
        }

        @Override
        public List<RemoteRepository> getRepositories() {
            return Collections.emptyList();
        }

        private List<Version> getVersions() {
            return Collections.unmodifiableList(versions);
        }
    }
}
