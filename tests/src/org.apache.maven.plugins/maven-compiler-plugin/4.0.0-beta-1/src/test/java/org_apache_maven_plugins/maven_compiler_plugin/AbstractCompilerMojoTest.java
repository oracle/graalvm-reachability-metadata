/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_compiler_plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ArtifactCoordinate;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinate;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.Language;
import org.apache.maven.api.Listener;
import org.apache.maven.api.LocalRepository;
import org.apache.maven.api.Node;
import org.apache.maven.api.Packaging;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.PathType;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Service;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.Toolchain;
import org.apache.maven.api.Type;
import org.apache.maven.api.Version;
import org.apache.maven.api.VersionConstraint;
import org.apache.maven.api.VersionRange;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.plugin.compiler.AbstractCompilerMojo;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.junit.jupiter.api.Test;

public class AbstractCompilerMojoTest {
    @Test
    void requestValuesAreReadFromMavenSession() throws Exception {
        Instant startTime = Instant.ofEpochMilli(123456789L);
        TestCompilerMojo mojo = new TestCompilerMojo();
        MojoExtension.setVariableValueToObject(mojo, "session", new TestSession(4, startTime));

        assertEquals(4, mojo.requestThreadCount());
        assertEquals(startTime, mojo.buildStartTime());
    }

    @Test
    void toolchainRequirementsUseMavenFourToolchainManager() throws Exception {
        TestToolchain expectedToolchain = new TestToolchain();
        TestToolchainManager toolchainManager = new TestToolchainManager(expectedToolchain);
        Map<String, String> requirements = Map.of("version", "21");

        TestCompilerMojo mojo = new TestCompilerMojo();
        MojoExtension.setVariableValueToObject(mojo, "session", new TestSession(1, Instant.EPOCH));
        MojoExtension.setVariableValueToObject(mojo, "toolchainManager", toolchainManager);
        MojoExtension.setVariableValueToObject(mojo, "jdkToolchain", requirements);

        Optional<Toolchain> toolchain = mojo.toolchain();

        assertSame(expectedToolchain, toolchain.orElseThrow());
        assertEquals("jdk", toolchainManager.requestedType);
        assertSame(requirements, toolchainManager.requestedRequirements);
    }

    static final class TestCompilerMojo extends AbstractCompilerMojo {
        int requestThreadCount() {
            return getRequestThreadCount();
        }

        Instant buildStartTime() {
            return getBuildStartTime();
        }

        Optional<Toolchain> toolchain() {
            return getToolchain();
        }

        @Override
        protected SourceInclusionScanner getSourceInclusionScanner(int staleMillis) {
            return null;
        }

        @Override
        protected SourceInclusionScanner getSourceInclusionScanner(String inputFileEnding) {
            return null;
        }

        @Override
        protected List<String> getClasspathElements() {
            return Collections.emptyList();
        }

        @Override
        protected List<String> getModulepathElements() {
            return Collections.emptyList();
        }

        @Override
        protected Map<String, JavaModuleDescriptor> getPathElements() {
            return Collections.emptyMap();
        }

        @Override
        protected List<Path> getCompileSourceRoots() {
            return Collections.emptyList();
        }

        @Override
        protected void preparePaths(Set<Path> sourceFiles) {
        }

        @Override
        protected Path getOutputDirectory() {
            return Path.of("target", "classes");
        }

        @Override
        protected String getSource() {
            return "1.8";
        }

        @Override
        protected String getTarget() {
            return "1.8";
        }

        @Override
        protected String getRelease() {
            return null;
        }

        @Override
        protected String getCompilerArgument() {
            return null;
        }

        @Override
        protected Path getGeneratedSourcesDirectory() {
            return Path.of("target", "generated-sources", "annotations");
        }

        @Override
        protected String getDebugFileName() {
            return null;
        }

        @Override
        protected Set<String> getIncludes() {
            return Collections.emptySet();
        }

        @Override
        protected Set<String> getExcludes() {
            return Collections.emptySet();
        }
    }

    public static final class TestToolchainManager implements ToolchainManager {
        private final Toolchain toolchain;
        private String requestedType;
        private Map<String, String> requestedRequirements;

        TestToolchainManager(Toolchain toolchain) {
            this.toolchain = toolchain;
        }

        @Override
        public List<Toolchain> getToolchains(Session session, String type, Map<String, String> requirements) {
            requestedType = type;
            requestedRequirements = requirements;
            return List.of(toolchain);
        }

        @Override
        public Optional<Toolchain> getToolchainFromBuildContext(Session session, String type) {
            return Optional.empty();
        }

        @Override
        public List<Toolchain> getToolchainsForType(Session session, String type) {
            return Collections.emptyList();
        }

        @Override
        public void storeToolchainToBuildContext(Session session, Toolchain toolchain) {
        }
    }

    static final class TestToolchain implements Toolchain {
        @Override
        public String getType() {
            return "jdk";
        }

        @Override
        public String findTool(String toolName) {
            return toolName;
        }

        @Override
        public boolean matchesRequirements(Map<String, String> requirements) {
            return true;
        }
    }

    static final class TestSession implements Session {
        private final int degreeOfConcurrency;
        private final Instant startTime;

        TestSession(int degreeOfConcurrency, Instant startTime) {
            this.degreeOfConcurrency = degreeOfConcurrency;
            this.startTime = startTime;
        }

        @Override
        public int getDegreeOfConcurrency() {
            return degreeOfConcurrency;
        }

        @Override
        public Instant getStartTime() {
            return startTime;
        }

        @Override
        public Settings getSettings() {
            throw unsupported();
        }

        @Override
        public LocalRepository getLocalRepository() {
            throw unsupported();
        }

        @Override
        public List<RemoteRepository> getRemoteRepositories() {
            throw unsupported();
        }

        @Override
        public SessionData getData() {
            throw unsupported();
        }

        @Override
        public Map<String, String> getUserProperties() {
            throw unsupported();
        }

        @Override
        public Map<String, String> getSystemProperties() {
            throw unsupported();
        }

        @Override
        public Map<String, String> getEffectiveProperties(Project project) {
            throw unsupported();
        }

        @Override
        public Version getMavenVersion() {
            throw unsupported();
        }

        @Override
        public Path getTopDirectory() {
            throw unsupported();
        }

        @Override
        public Path getRootDirectory() {
            throw unsupported();
        }

        @Override
        public List<Project> getProjects() {
            throw unsupported();
        }

        @Override
        public Map<String, Object> getPluginContext(Project project) {
            throw unsupported();
        }

        @Override
        public <T extends Service> T getService(Class<T> serviceType) {
            throw unsupported();
        }

        @Override
        public Session withLocalRepository(LocalRepository localRepository) {
            throw unsupported();
        }

        @Override
        public Session withRemoteRepositories(List<RemoteRepository> repositories) {
            throw unsupported();
        }

        @Override
        public void registerListener(Listener listener) {
            throw unsupported();
        }

        @Override
        public void unregisterListener(Listener listener) {
            throw unsupported();
        }

        @Override
        public Collection<Listener> getListeners() {
            throw unsupported();
        }

        @Override
        public LocalRepository createLocalRepository(Path path) {
            throw unsupported();
        }

        @Override
        public RemoteRepository createRemoteRepository(String id, String url) {
            throw unsupported();
        }

        @Override
        public RemoteRepository createRemoteRepository(Repository repository) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinate createArtifactCoordinate(String coordinates) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinate createArtifactCoordinate(String groupId, String artifactId, String version,
                String extension) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinate createArtifactCoordinate(String groupId, String artifactId, String version,
                String classifier, String extension, String type) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinate createArtifactCoordinate(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public DependencyCoordinate createDependencyCoordinate(ArtifactCoordinate coordinate) {
            throw unsupported();
        }

        @Override
        public DependencyCoordinate createDependencyCoordinate(Dependency dependency) {
            throw unsupported();
        }

        @Override
        public Artifact createArtifact(String groupId, String artifactId, String version, String extension) {
            throw unsupported();
        }

        @Override
        public Artifact createArtifact(String groupId, String artifactId, String version, String classifier,
                String extension, String type) {
            throw unsupported();
        }

        @Override
        public Map.Entry<Artifact, Path> resolveArtifact(ArtifactCoordinate coordinate) {
            throw unsupported();
        }

        @Override
        public Map<Artifact, Path> resolveArtifacts(ArtifactCoordinate... coordinates) {
            throw unsupported();
        }

        @Override
        public Map<Artifact, Path> resolveArtifacts(Collection<? extends ArtifactCoordinate> coordinates) {
            throw unsupported();
        }

        @Override
        public Map.Entry<Artifact, Path> resolveArtifact(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public Map<Artifact, Path> resolveArtifacts(Artifact... artifacts) {
            throw unsupported();
        }

        @Override
        public void installArtifacts(Artifact... artifacts) {
            throw unsupported();
        }

        @Override
        public void installArtifacts(Collection<Artifact> artifacts) {
            throw unsupported();
        }

        @Override
        public void deployArtifact(RemoteRepository repository, Artifact... artifacts) {
            throw unsupported();
        }

        @Override
        public void setArtifactPath(Artifact artifact, Path path) {
            throw unsupported();
        }

        @Override
        public Optional<Path> getArtifactPath(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public Path getPathForLocalArtifact(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public Path getPathForRemoteArtifact(RemoteRepository repository, Artifact artifact) {
            throw unsupported();
        }

        @Override
        public boolean isVersionSnapshot(String version) {
            throw unsupported();
        }

        @Override
        public Node collectDependencies(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public Node collectDependencies(Project project) {
            throw unsupported();
        }

        @Override
        public Node collectDependencies(DependencyCoordinate dependency) {
            throw unsupported();
        }

        @Override
        public List<Node> flattenDependencies(Node node, PathScope scope) {
            throw unsupported();
        }

        @Override
        public List<Path> resolveDependencies(DependencyCoordinate dependency) {
            throw unsupported();
        }

        @Override
        public List<Path> resolveDependencies(List<DependencyCoordinate> dependencies) {
            throw unsupported();
        }

        @Override
        public List<Path> resolveDependencies(Project project, PathScope scope) {
            throw unsupported();
        }

        @Override
        public Map<PathType, List<Path>> resolveDependencies(DependencyCoordinate dependency, PathScope scope,
                Collection<PathType> pathTypes) {
            throw unsupported();
        }

        @Override
        public Map<PathType, List<Path>> resolveDependencies(Project project, PathScope scope,
                Collection<PathType> pathTypes) {
            throw unsupported();
        }

        @Override
        public Version resolveVersion(ArtifactCoordinate coordinate) {
            throw unsupported();
        }

        @Override
        public List<Version> resolveVersionRange(ArtifactCoordinate coordinate) {
            throw unsupported();
        }

        @Override
        public Version parseVersion(String version) {
            throw unsupported();
        }

        @Override
        public VersionRange parseVersionRange(String version) {
            throw unsupported();
        }

        @Override
        public VersionConstraint parseVersionConstraint(String versionConstraint) {
            throw unsupported();
        }

        @Override
        public Type requireType(String id) {
            throw unsupported();
        }

        @Override
        public Language requireLanguage(String id) {
            throw unsupported();
        }

        @Override
        public Packaging requirePackaging(String id) {
            throw unsupported();
        }

        @Override
        public ProjectScope requireProjectScope(String id) {
            throw unsupported();
        }

        @Override
        public DependencyScope requireDependencyScope(String id) {
            throw unsupported();
        }

        @Override
        public PathScope requirePathScope(String id) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("This test session implements only the methods under test.");
        }
    }
}
