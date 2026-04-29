/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_aether.aether_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.artifact.ArtifactType;
import org.sonatype.aether.collection.DependencyCollectionContext;
import org.sonatype.aether.collection.DependencyGraphTransformationContext;
import org.sonatype.aether.collection.DependencyGraphTransformer;
import org.sonatype.aether.collection.DependencySelector;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.graph.Exclusion;
import org.sonatype.aether.metadata.Metadata;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.util.ChecksumUtils;
import org.sonatype.aether.util.DefaultRepositoryCache;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.DefaultSessionData;
import org.sonatype.aether.util.artifact.ArtifactProperties;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.DefaultArtifactType;
import org.sonatype.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.artifact.SubArtifact;
import org.sonatype.aether.util.filter.AndDependencyFilter;
import org.sonatype.aether.util.filter.ExclusionsDependencyFilter;
import org.sonatype.aether.util.filter.OrDependencyFilter;
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter;
import org.sonatype.aether.util.filter.PatternInclusionsDependencyFilter;
import org.sonatype.aether.util.filter.ScopeDependencyFilter;
import org.sonatype.aether.util.graph.CloningDependencyVisitor;
import org.sonatype.aether.util.graph.DefaultDependencyNode;
import org.sonatype.aether.util.graph.FilteringDependencyVisitor;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;
import org.sonatype.aether.util.graph.TreeDependencyVisitor;
import org.sonatype.aether.util.graph.selector.ExclusionDependencySelector;
import org.sonatype.aether.util.graph.selector.OptionalDependencySelector;
import org.sonatype.aether.util.graph.selector.ScopeDependencySelector;
import org.sonatype.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.sonatype.aether.util.graph.transformer.ConflictMarker;
import org.sonatype.aether.util.graph.transformer.JavaEffectiveScopeCalculator;
import org.sonatype.aether.util.graph.transformer.NearestVersionConflictResolver;
import org.sonatype.aether.util.graph.transformer.TransformationContextKeys;
import org.sonatype.aether.util.layout.MavenDefaultLayout;
import org.sonatype.aether.util.listener.DefaultRepositoryEvent;
import org.sonatype.aether.util.listener.DefaultTransferEvent;
import org.sonatype.aether.util.listener.DefaultTransferResource;
import org.sonatype.aether.util.metadata.DefaultMetadata;
import org.sonatype.aether.util.repository.ChainedWorkspaceReader;
import org.sonatype.aether.util.repository.DefaultAuthenticationSelector;
import org.sonatype.aether.util.repository.DefaultMirrorSelector;
import org.sonatype.aether.util.repository.DefaultProxySelector;
import org.sonatype.aether.util.version.GenericVersionScheme;
import org.sonatype.aether.version.InvalidVersionSpecificationException;
import org.sonatype.aether.version.Version;
import org.sonatype.aether.version.VersionConstraint;

public class Aether_utilTest {
    private static final File ARTIFACT_FILE = new File("target/demo-1.0.jar");
    private static final File SOURCES_FILE = new File("target/demo-1.0-sources.jar");

    @Test
    void artifactsTypesAndSubArtifactsExposeCoordinatesPropertiesAndCopyOnWriteState() {
        ArtifactType javaArchive = new DefaultArtifactType(
                "bundle", "jar", "", "java", true, true);
        DefaultArtifactTypeRegistry registry = new DefaultArtifactTypeRegistry().add(javaArchive);
        Map<String, String> properties = new HashMap<>();
        properties.put("build", "native-ready");

        Artifact artifact = new DefaultArtifact(
                "org.example", "demo", "", "jar", "1.0-SNAPSHOT", properties, javaArchive);
        Artifact timestampedSnapshot = artifact.setVersion("1.0-20240101.123456-7");
        Artifact released = timestampedSnapshot.setVersion("1.0").setFile(ARTIFACT_FILE);
        Artifact changedProperties = released.setProperties(Collections.singletonMap("build", "reproducible"));
        Artifact parsed = new DefaultArtifact("org.example:demo:jar:tests:1.0");
        Artifact sources = new SubArtifact(artifact, "*-sources", "*", SOURCES_FILE);
        Artifact sourceRelease = sources.setVersion("1.0");

        assertThat(registry.get("bundle")).isSameAs(javaArchive);
        assertThat(javaArchive.getProperties())
                .containsEntry(ArtifactProperties.TYPE, "bundle")
                .containsEntry(ArtifactProperties.LANGUAGE, "java")
                .containsEntry(ArtifactProperties.CONSTITUTES_BUILD_PATH, "true")
                .containsEntry(ArtifactProperties.INCLUDES_DEPENDENCIES, "true");
        assertThat(artifact.getGroupId()).isEqualTo("org.example");
        assertThat(artifact.getArtifactId()).isEqualTo("demo");
        assertThat(artifact.getExtension()).isEqualTo("jar");
        assertThat(artifact.getClassifier()).isEmpty();
        assertThat(artifact.getProperty("build", "missing")).isEqualTo("native-ready");
        assertThat(artifact.getProperty("unknown", "fallback")).isEqualTo("fallback");
        assertThat(artifact.isSnapshot()).isTrue();
        assertThat(timestampedSnapshot.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(released).isNotSameAs(timestampedSnapshot);
        assertThat(released.getVersion()).isEqualTo("1.0");
        assertThat(released.getFile()).isEqualTo(ARTIFACT_FILE);
        assertThat(released.isSnapshot()).isFalse();
        assertThat(changedProperties.getProperty("build", "missing")).isEqualTo("reproducible");
        assertThat(released.getProperty("build", "missing")).isEqualTo("native-ready");
        assertThat(parsed.getClassifier()).isEqualTo("tests");
        assertThat(parsed.getExtension()).isEqualTo("jar");
        assertThat(sources.getClassifier()).isEqualTo("sources");
        assertThat(sources.getExtension()).isEqualTo("jar");
        assertThat(sources.getFile()).isEqualTo(SOURCES_FILE);
        assertThat(sources.getProperties()).containsEntry("build", "native-ready");
        assertThat(sourceRelease).isInstanceOf(DefaultArtifact.class);
        assertThat(sourceRelease.getVersion()).isEqualTo("1.0");
        assertThat(sourceRelease.getClassifier()).isEqualTo("sources");
        assertThatThrownBy(() -> new DefaultArtifact("missing-version:demo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bad artifact coordinates");
    }

    @Test
    void mavenLayoutAndMetadataBuildRepositoryPathsForArtifactsAndMetadata() {
        MavenDefaultLayout layout = new MavenDefaultLayout();
        Artifact artifact = new DefaultArtifact("org.example.tools:demo:jar:tests:1.0");
        Metadata groupMetadata = new DefaultMetadata(
                "org.example.tools", "maven-metadata.xml", Metadata.Nature.RELEASE);
        Metadata artifactMetadata = new DefaultMetadata(
                "org.example.tools", "demo", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Metadata versionMetadata = new DefaultMetadata(
                "org.example.tools", "demo", "1.0", "maven-metadata.xml", Metadata.Nature.SNAPSHOT, ARTIFACT_FILE);
        Metadata movedMetadata = versionMetadata.setFile(SOURCES_FILE);

        assertThat(layout.getPath(artifact).toString())
                .isEqualTo("org/example/tools/demo/1.0/demo-1.0-tests.jar");
        assertThat(layout.getPath(groupMetadata).toString())
                .isEqualTo("org/example/tools/maven-metadata.xml");
        assertThat(layout.getPath(artifactMetadata).toString())
                .isEqualTo("org/example/tools/demo/maven-metadata.xml");
        assertThat(layout.getPath(versionMetadata).toString())
                .isEqualTo("org/example/tools/demo/1.0/maven-metadata.xml");
        assertThat(versionMetadata.getNature()).isEqualTo(Metadata.Nature.SNAPSHOT);
        assertThat(versionMetadata.getFile()).isEqualTo(ARTIFACT_FILE);
        assertThat(movedMetadata.getFile()).isEqualTo(SOURCES_FILE);
        assertThat(versionMetadata.getFile()).isEqualTo(ARTIFACT_FILE);
        assertThat(versionMetadata.toString()).contains("org.example.tools", "demo", "1.0", "maven-metadata.xml");
    }

    @Test
    void genericVersionSchemeOrdersQualifiersAndEvaluatesRangesAndConstraints()
            throws InvalidVersionSpecificationException {
        GenericVersionScheme scheme = new GenericVersionScheme();
        Version alpha = scheme.parseVersion("1.0-alpha-1");
        Version beta = scheme.parseVersion("1.0-beta-1");
        Version release = scheme.parseVersion("1.0");
        Version serviceRelease = scheme.parseVersion("1.0.1");
        VersionConstraint fixed = scheme.parseVersionConstraint("1.0");
        VersionConstraint bounded = scheme.parseVersionConstraint("[1.0,2.0)");
        VersionConstraint union = scheme.parseVersionConstraint("(,1.0],[1.2,)");

        assertThat(alpha).isLessThan(beta);
        assertThat(beta).isLessThan(release);
        assertThat(serviceRelease).isGreaterThan(release);
        assertThat(fixed.getVersion()).isEqualTo(release);
        assertThat(fixed.getRanges()).isEmpty();
        assertThat(fixed.containsVersion(release)).isTrue();
        assertThat(bounded.getVersion()).isNull();
        assertThat(bounded.containsVersion(release)).isTrue();
        assertThat(bounded.containsVersion(scheme.parseVersion("2.0"))).isFalse();
        assertThat(union.containsVersion(scheme.parseVersion("0.9"))).isTrue();
        assertThat(union.containsVersion(scheme.parseVersion("1.1"))).isFalse();
        assertThat(scheme.parseVersionRange("[1.0,1.5]").containsVersion(serviceRelease)).isTrue();
        assertThatThrownBy(() -> scheme.parseVersionConstraint("[1.0,2.0"))
                .isInstanceOf(InvalidVersionSpecificationException.class);
        assertThat(new GenericVersionScheme()).isEqualTo(scheme).hasSameHashCodeAs(scheme);
    }

    @Test
    void dependencyNodesTraverseCloneFilterAndGeneratePreorderViews() {
        Artifact rootArtifact = new DefaultArtifact("org.example:root:jar:1.0").setFile(new File("root.jar"));
        Artifact runtimeArtifact = new DefaultArtifact("org.example:runtime:jar:1.0").setFile(new File("runtime.jar"));
        Artifact testArtifact = new DefaultArtifact("org.example:test-helper:jar:1.0")
                .setFile(new File("test-helper.jar"));
        DefaultDependencyNode root = new DefaultDependencyNode(new Dependency(rootArtifact, JavaScopes.COMPILE));
        DefaultDependencyNode runtime = new DefaultDependencyNode(new Dependency(runtimeArtifact, JavaScopes.RUNTIME));
        DefaultDependencyNode test = new DefaultDependencyNode(new Dependency(testArtifact, JavaScopes.TEST));
        root.getChildren().add(runtime);
        root.getChildren().add(test);
        root.setRequestContext("project");
        root.setRelocations(Collections.singletonList(new DefaultArtifact("org.relocated:root:jar:1.0")));
        root.setAliases(Collections.singleton(new DefaultArtifact("org.legacy:root:jar:1.0")));
        root.setRepositories(Collections.singletonList(centralRepository()));
        root.setPremanagedVersion("0.9");
        root.setPremanagedScope(JavaScopes.PROVIDED);

        PreorderNodeListGenerator generator = new PreorderNodeListGenerator();
        root.accept(generator);
        CloningDependencyVisitor cloningVisitor = new CloningDependencyVisitor();
        root.accept(cloningVisitor);
        PreorderNodeListGenerator runtimeOnly = new PreorderNodeListGenerator();
        FilteringDependencyVisitor filteringVisitor = new FilteringDependencyVisitor(
                new TreeDependencyVisitor(runtimeOnly), new ScopeDependencyFilter(JavaScopes.TEST));
        root.accept(filteringVisitor);
        DependencyNode clonedRoot = cloningVisitor.getRootNode();

        assertThat(generator.getNodes()).containsExactly(root, runtime, test);
        assertThat(generator.getDependencies(false))
                .extracting(Dependency::getScope)
                .containsExactly(JavaScopes.COMPILE, JavaScopes.RUNTIME, JavaScopes.TEST);
        assertThat(generator.getArtifacts(false)).containsExactly(rootArtifact, runtimeArtifact, testArtifact);
        assertThat(generator.getFiles())
                .containsExactly(new File("root.jar"), new File("runtime.jar"), new File("test-helper.jar"));
        assertThat(generator.getClassPath()).contains("root.jar").contains(File.pathSeparator).contains("runtime.jar");
        assertThat(runtimeOnly.getArtifacts(false)).containsExactly(rootArtifact, runtimeArtifact);
        assertThat(clonedRoot).isNotSameAs(root);
        assertThat(clonedRoot.getDependency()).isEqualTo(root.getDependency());
        assertThat(clonedRoot.getChildren()).hasSize(2);
        assertThat(clonedRoot.getChildren().get(0)).isNotSameAs(runtime);
        assertThat(root.getRequestContext()).isEqualTo("project");
        assertThat(root.getRelocations()).hasSize(1);
        assertThat(root.getAliases()).hasSize(1);
        assertThat(root.getRepositories()).containsExactly(centralRepository());
        assertThat(root.getPremanagedVersion()).isEqualTo("0.9");
        assertThat(root.getPremanagedScope()).isEqualTo(JavaScopes.PROVIDED);
        root.setArtifact(new DefaultArtifact("org.example:replacement:jar:1.0"));
        root.setScope(JavaScopes.RUNTIME);
        assertThat(root.getDependency().getArtifact().getArtifactId()).isEqualTo("replacement");
        assertThat(root.getDependency().getScope()).isEqualTo(JavaScopes.RUNTIME);
    }

    @Test
    void dependencyGraphTransformersMarkConflictsCalculateScopesAndResolveNearestVersions()
            throws InvalidVersionSpecificationException, RepositoryException {
        DefaultDependencyNode root = resolvedNode("org.example:app:jar:1.0", JavaScopes.COMPILE);
        DefaultDependencyNode directLibrary = resolvedNode("org.example:direct:jar:1.0", JavaScopes.COMPILE);
        DefaultDependencyNode runtimeLibrary = resolvedNode("org.example:runtime:jar:1.0", JavaScopes.RUNTIME);
        DefaultDependencyNode nearestShared = resolvedNode("org.example:shared:jar:1.0", JavaScopes.RUNTIME);
        DefaultDependencyNode fartherShared = resolvedNode("org.example:shared:jar:2.0", JavaScopes.COMPILE);
        DefaultDependencyNode transitiveCompile = resolvedNode("org.example:transitive:jar:1.0", JavaScopes.COMPILE);
        root.getChildren().add(directLibrary);
        root.getChildren().add(runtimeLibrary);
        directLibrary.getChildren().add(nearestShared);
        runtimeLibrary.getChildren().add(fartherShared);
        runtimeLibrary.getChildren().add(transitiveCompile);
        TestTransformationContext context = new TestTransformationContext();
        DependencyGraphTransformer transformer = new ChainedDependencyGraphTransformer(
                new ConflictMarker(), new JavaEffectiveScopeCalculator(), new NearestVersionConflictResolver());

        DependencyNode transformed = transformer.transformGraph(root, context);
        Map<Object, Object> conflictIds = new HashMap<>(
                (Map<?, ?>) context.get(TransformationContextKeys.CONFLICT_IDS));

        assertThat(transformed).isSameAs(root);
        assertThat(root.getChildren()).containsExactly(directLibrary, runtimeLibrary);
        assertThat(directLibrary.getChildren()).containsExactly(nearestShared);
        assertThat(runtimeLibrary.getChildren()).containsExactly(transitiveCompile);
        assertThat(nearestShared.getDependency().getArtifact().getVersion()).isEqualTo("1.0");
        assertThat(transitiveCompile.getDependency().getScope()).isEqualTo(JavaScopes.RUNTIME);
        assertThat(conflictIds)
                .containsKeys(root, directLibrary, runtimeLibrary, nearestShared, fartherShared, transitiveCompile);
        assertThat(conflictIds.get(nearestShared)).isEqualTo(conflictIds.get(fartherShared));
        assertThat(context.get(TransformationContextKeys.SORTED_CONFLICT_IDS)).isNotNull();
    }

    @Test
    void dependencyFiltersMatchScopesArtifactIdsPatternsAndVersionRanges()
            throws InvalidVersionSpecificationException {
        GenericVersionScheme versionScheme = new GenericVersionScheme();
        DefaultDependencyNode compile = node("org.example", "core", "jar", "1.0", JavaScopes.COMPILE);
        DefaultDependencyNode runtime = node("org.example", "runtime", "jar", "1.2", JavaScopes.RUNTIME);
        DefaultDependencyNode test = node("org.acme", "test-helper", "jar", "2.0", JavaScopes.TEST);
        List<DependencyNode> parents = Collections.singletonList(compile);
        DependencyFilter runtimeScope = new ScopeDependencyFilter(JavaScopes.TEST);
        DependencyFilter excludesById = new ExclusionsDependencyFilter(
                Arrays.asList("test-helper", "org.example:core"));
        DependencyFilter includesByPattern = new PatternInclusionsDependencyFilter(
                versionScheme, "org.example:*:jar:[1.1,2.0)");
        DependencyFilter excludesByPattern = new PatternExclusionsDependencyFilter("org.*:*helper");
        DependencyFilter andFilter = AndDependencyFilter.newInstance(includesByPattern, excludesByPattern);
        DependencyFilter orFilter = OrDependencyFilter.newInstance(includesByPattern, excludesById);

        assertThat(runtimeScope.accept(compile, parents)).isTrue();
        assertThat(runtimeScope.accept(test, parents)).isFalse();
        assertThat(excludesById.accept(compile, parents)).isFalse();
        assertThat(excludesById.accept(runtime, parents)).isTrue();
        assertThat(excludesById.accept(test, parents)).isFalse();
        assertThat(includesByPattern.accept(runtime, parents)).isTrue();
        assertThat(includesByPattern.accept(compile, parents)).isFalse();
        assertThat(excludesByPattern.accept(test, parents)).isFalse();
        assertThat(excludesByPattern.accept(runtime, parents)).isTrue();
        assertThat(andFilter.accept(runtime, parents)).isTrue();
        assertThat(andFilter.accept(test, parents)).isFalse();
        assertThat(orFilter.accept(compile, parents)).isFalse();
        assertThat(orFilter.accept(runtime, parents)).isTrue();
        assertThat(new ScopeDependencyFilter(JavaScopes.TEST)).isEqualTo(runtimeScope).hasSameHashCodeAs(runtimeScope);
        assertThat(new PatternInclusionsDependencyFilter(versionScheme, "org.example:*:jar:[1.1,2.0)"))
                .isEqualTo(includesByPattern)
                .hasSameHashCodeAs(includesByPattern);
    }

    @Test
    void dependencySelectorsDeriveTransitiveScopeOptionalAndExclusionRules() {
        Dependency directCompile = dependency("org.example", "direct", "jar", "1.0", JavaScopes.COMPILE, false);
        Dependency transitiveTest = dependency("org.example", "transitive-test", "jar", "1.0", JavaScopes.TEST, false);
        Dependency optionalRuntime = dependency(
                "org.example", "optional-runtime", "jar", "1.0", JavaScopes.RUNTIME, true);
        Exclusion excluded = new Exclusion("org.blocked", "blocked-artifact", "*", "jar");
        Dependency parentWithExclusion = new Dependency(
                new DefaultArtifact("org.example:parent:jar:1.0"),
                JavaScopes.COMPILE,
                false,
                Collections.singleton(excluded));
        Dependency excludedDependency = dependency(
                "org.blocked", "blocked-artifact", "jar", "1.0", JavaScopes.RUNTIME, false);
        Dependency allowedDependency = dependency(
                "org.blocked", "allowed-artifact", "jar", "1.0", JavaScopes.RUNTIME, false);

        DependencySelector scopeSelector = new ScopeDependencySelector(JavaScopes.TEST);
        DependencySelector transitiveScopeSelector = scopeSelector.deriveChildSelector(
                new TestCollectionContext(directCompile));
        DependencySelector optionalSelector = new OptionalDependencySelector()
                .deriveChildSelector(new TestCollectionContext(directCompile))
                .deriveChildSelector(new TestCollectionContext(transitiveTest));
        DependencySelector exclusionSelector = new ExclusionDependencySelector()
                .deriveChildSelector(new TestCollectionContext(parentWithExclusion));

        assertThat(scopeSelector.selectDependency(transitiveTest)).isTrue();
        assertThat(transitiveScopeSelector.selectDependency(directCompile)).isTrue();
        assertThat(transitiveScopeSelector.selectDependency(transitiveTest)).isFalse();
        assertThat(optionalSelector.selectDependency(optionalRuntime)).isFalse();
        assertThat(optionalSelector.selectDependency(transitiveTest)).isTrue();
        assertThat(exclusionSelector.selectDependency(excludedDependency)).isFalse();
        assertThat(exclusionSelector.selectDependency(allowedDependency)).isTrue();
        DependencySelector explicitExclusionSelector = new ExclusionDependencySelector(
                new LinkedHashSet<>(Collections.singleton(excluded)));
        assertThat(explicitExclusionSelector.selectDependency(excludedDependency)).isFalse();
        assertThat(new OptionalDependencySelector()).isEqualTo(new OptionalDependencySelector())
                .hasSameHashCodeAs(new OptionalDependencySelector());
    }

    @Test
    void repositorySelectorsApplyMirrorsProxiesAuthenticationAndSessionConfiguration() {
        RemoteRepository central = centralRepository();
        RemoteRepository local = new RemoteRepository("local", "default", "file:/tmp/repository");
        RepositoryPolicy snapshotPolicy = new RepositoryPolicy(true,
                RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        RepositoryPolicy releasePolicy = new RepositoryPolicy(true,
                RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        central.setPolicy(true, snapshotPolicy).setPolicy(false, releasePolicy);
        Authentication authentication = new Authentication("user", "secret");
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.org", 8080, authentication);
        DefaultAuthenticationSelector authenticationSelector = new DefaultAuthenticationSelector()
                .add("central", authentication);
        DefaultProxySelector proxySelector = new DefaultProxySelector()
                .add(proxy, "localhost|*.internal.example.org");
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector()
                .add("central-mirror", "https://mirror.example.org/maven2", "default", true, "external:*", "default");
        RemoteRepository mirror = mirrorSelector.getMirror(central);
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession()
                .setOffline(true)
                .setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL)
                .setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_NEVER)
                .setAuthenticationSelector(authenticationSelector)
                .setProxySelector(proxySelector)
                .setMirrorSelector(mirrorSelector)
                .setArtifactTypeRegistry(new DefaultArtifactTypeRegistry().add(new DefaultArtifactType("jar")))
                .setSystemProperty("java.version", "test")
                .setUserProperty("profile", "native")
                .setConfigProperty("parallel", Boolean.TRUE)
                .setCache(new DefaultRepositoryCache())
                .setData(new DefaultSessionData());
        DefaultRepositorySystemSession copied = new DefaultRepositorySystemSession(session);

        assertThat(mirror.getId()).isEqualTo("central-mirror");
        assertThat(mirror.getUrl()).isEqualTo("https://mirror.example.org/maven2");
        assertThat(mirror.isRepositoryManager()).isTrue();
        assertThat(mirror.getMirroredRepositories()).containsExactly(central);
        assertThat(mirror.getPolicy(true)).isEqualTo(snapshotPolicy);
        assertThat(mirrorSelector.getMirror(local)).isNull();
        assertThat(proxySelector.getProxy(central)).isEqualTo(proxy);
        RemoteRepository internalRepository = new RemoteRepository(
                "internal", "default", "https://build.internal.example.org/repo");
        assertThat(proxySelector.getProxy(internalRepository)).isNull();
        assertThat(authenticationSelector.getAuthentication(central)).isEqualTo(authentication);
        assertThat(session.isOffline()).isTrue();
        assertThat(session.getChecksumPolicy()).isEqualTo(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        assertThat(session.getUpdatePolicy()).isEqualTo(RepositoryPolicy.UPDATE_POLICY_NEVER);
        assertThat(session.getSystemProperties()).containsEntry("java.version", "test");
        assertThat(session.getUserProperties()).containsEntry("profile", "native");
        assertThat(session.getConfigProperties()).containsEntry("parallel", Boolean.TRUE);
        session.getCache().put(session, "artifact", "cached-value");
        session.getData().set("workspace", "active");
        assertThat(session.getCache().get(session, "artifact")).isEqualTo("cached-value");
        assertThat(session.getData().get("workspace")).isEqualTo("active");
        assertThat(copied.isOffline()).isTrue();
        assertThat(copied.getMirrorSelector()).isSameAs(mirrorSelector);
        assertThat(copied.getProxySelector()).isSameAs(proxySelector);
        assertThat(copied.getAuthenticationSelector()).isSameAs(authenticationSelector);
    }

    @Test
    void chainedWorkspaceReaderFindsArtifactsAndVersionsAcrossReaders() {
        Artifact artifact = new DefaultArtifact("org.example:workspace-demo:jar:1.0");
        File primaryFile = new File("target/workspace/primary-demo.jar");
        File secondaryFile = new File("target/workspace/secondary-demo.jar");
        WorkspaceReader missing = new TestWorkspaceReader("missing", null, Collections.singletonList("0.9"));
        WorkspaceReader primary = new TestWorkspaceReader("primary", primaryFile, Arrays.asList("1.0", "1.1"));
        WorkspaceReader secondary = new TestWorkspaceReader("secondary", secondaryFile, Arrays.asList("1.1", "2.0"));
        ChainedWorkspaceReader chain = new ChainedWorkspaceReader(missing, primary, secondary);
        List<String> versions = chain.findVersions(artifact);

        assertThat(chain.findArtifact(artifact)).isEqualTo(primaryFile);
        assertThat(versions).containsExactly("0.9", "1.0", "1.1", "2.0");
        assertThatThrownBy(() -> versions.add("3.0")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(chain.getRepository().getId()).isEqualTo("workspace");
        assertThat(chain.getRepository().getContentType()).isEqualTo("missing+primary+secondary");
        assertThat(ChainedWorkspaceReader.newInstance(primary, null)).isSameAs(primary);
        assertThat(ChainedWorkspaceReader.newInstance(null, secondary)).isSameAs(secondary);
        assertThat(ChainedWorkspaceReader.newInstance(primary, secondary).findArtifact(artifact)).isEqualTo(primaryFile);
    }

    @Test
    void listenersEventsAndChecksumUtilitiesExposeTransferRepositoryAndDigestState() throws IOException {
        Path payload = Files.createTempFile("aether-util-payload", ".txt");
        Path checksum = Files.createTempFile("aether-util-checksum", ".sha1");
        Files.write(payload, "abc".getBytes(StandardCharsets.UTF_8));
        Files.write(checksum, "SHA1 (payload.txt) = A9993E364706816ABA3E25717850C26C9CD0D89D\n"
                .getBytes(StandardCharsets.UTF_8));
        Map<String, Object> digests = ChecksumUtils.calc(payload.toFile(), Arrays.asList("SHA-1", "MD5", "NO-SUCH"));
        DefaultTransferResource resource = new DefaultTransferResource(
                "https://repo.example.org", "org/example/demo/1.0/demo-1.0.jar", payload.toFile())
                .setContentLength(3L);
        ByteBuffer data = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        IOException failure = new IOException("network failure");
        DefaultTransferEvent transferEvent = new DefaultTransferEvent()
                .setType(TransferEvent.EventType.PROGRESSED)
                .setRequestType(TransferEvent.RequestType.GET)
                .setResource(resource)
                .setTransferredBytes(3L)
                .setDataBuffer(data)
                .setException(failure);
        Artifact artifact = new DefaultArtifact("org.example:demo:jar:1.0").setFile(payload.toFile());
        DefaultRepositoryEvent repositoryEvent = new DefaultRepositoryEvent(
                new DefaultRepositorySystemSession(), artifact)
                .setRepository(centralRepository())
                .setFile(payload.toFile())
                .setException(failure)
                .setExceptions(Collections.singletonList(failure));

        assertThat(ChecksumUtils.read(checksum.toFile())).isEqualTo("A9993E364706816ABA3E25717850C26C9CD0D89D");
        assertThat(digests).containsEntry("SHA-1", "a9993e364706816aba3e25717850c26c9cd0d89d")
                .containsEntry("MD5", "900150983cd24fb0d6963f7d28e17f72");
        assertThat(digests.get("NO-SUCH")).isInstanceOf(Exception.class);
        assertThat(resource.getRepositoryUrl()).isEqualTo("https://repo.example.org/");
        assertThat(resource.getResourceName()).endsWith("demo-1.0.jar");
        assertThat(resource.getFile()).isEqualTo(payload.toFile());
        assertThat(resource.getContentLength()).isEqualTo(3L);
        assertThat(resource.getTransferStartTime()).isPositive();
        assertThat(transferEvent.getType()).isEqualTo(TransferEvent.EventType.PROGRESSED);
        assertThat(transferEvent.getRequestType()).isEqualTo(TransferEvent.RequestType.GET);
        assertThat(transferEvent.getResource()).isEqualTo(resource);
        assertThat(transferEvent.getTransferredBytes()).isEqualTo(3L);
        assertThat(transferEvent.getDataBuffer()).isEqualTo(data.asReadOnlyBuffer());
        assertThat(transferEvent.getDataLength()).isEqualTo(4);
        assertThat(transferEvent.getException()).isEqualTo(failure);
        assertThat(repositoryEvent.getArtifact()).isEqualTo(artifact);
        assertThat(repositoryEvent.getRepository()).isEqualTo(centralRepository());
        assertThat(repositoryEvent.getFile()).isEqualTo(payload.toFile());
        assertThat(repositoryEvent.getException()).isEqualTo(failure);
        assertThat(repositoryEvent.getExceptions()).containsExactly(failure);
    }

    private static DefaultDependencyNode node(
            String groupId, String artifactId, String extension, String version, String scope) {
        return new DefaultDependencyNode(new Dependency(
                new DefaultArtifact(groupId + ':' + artifactId + ':' + extension + ':' + version), scope));
    }

    private static DefaultDependencyNode resolvedNode(String coordinates, String scope)
            throws InvalidVersionSpecificationException {
        Artifact artifact = new DefaultArtifact(coordinates);
        DefaultDependencyNode node = new DefaultDependencyNode(new Dependency(artifact, scope));
        GenericVersionScheme versionScheme = new GenericVersionScheme();
        node.setVersion(versionScheme.parseVersion(artifact.getVersion()));
        node.setVersionConstraint(versionScheme.parseVersionConstraint(artifact.getVersion()));
        return node;
    }

    private static Dependency dependency(
            String groupId, String artifactId, String extension, String version, String scope, boolean optional) {
        Artifact artifact = new DefaultArtifact(groupId + ':' + artifactId + ':' + extension + ':' + version);
        return new Dependency(artifact, scope, optional);
    }

    private static RemoteRepository centralRepository() {
        return new RemoteRepository("central", "default", "https://repo1.maven.org/maven2");
    }

    private static final class TestWorkspaceReader implements WorkspaceReader {
        private final WorkspaceRepository repository;
        private final File artifactFile;
        private final List<String> versions;

        private TestWorkspaceReader(String contentType, File artifactFile, List<String> versions) {
            this.repository = new WorkspaceRepository(contentType, contentType);
            this.artifactFile = artifactFile;
            this.versions = versions;
        }

        @Override
        public WorkspaceRepository getRepository() {
            return repository;
        }

        @Override
        public File findArtifact(Artifact artifact) {
            return artifactFile;
        }

        @Override
        public List<String> findVersions(Artifact artifact) {
            return versions;
        }
    }

    private static final class TestTransformationContext implements DependencyGraphTransformationContext {
        private final Map<Object, Object> values = new HashMap<>();

        @Override
        public RepositorySystemSession getSession() {
            return new DefaultRepositorySystemSession();
        }

        @Override
        public Object get(Object key) {
            return values.get(key);
        }

        @Override
        public Object put(Object key, Object value) {
            return values.put(key, value);
        }
    }

    private static final class TestCollectionContext implements DependencyCollectionContext {
        private final Dependency dependency;

        private TestCollectionContext(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public RepositorySystemSession getSession() {
            return new DefaultRepositorySystemSession();
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
}
