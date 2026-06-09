/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_maven_plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.maven.FileLocator;
import com.diffplug.spotless.maven.FormatterConfig;
import com.diffplug.spotless.maven.SpotlessApplyMojo;
import com.diffplug.spotless.maven.SpotlessCheckMojo;
import com.diffplug.spotless.maven.generic.EndWithNewline;
import com.diffplug.spotless.maven.generic.Format;
import com.diffplug.spotless.maven.generic.ReplaceRegex;
import com.diffplug.spotless.maven.generic.TrimTrailingWhitespace;
import com.diffplug.spotless.maven.java.Java;
import com.diffplug.spotless.maven.incremental.UpToDateChecking;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.resource.PlexusResource;
import org.codehaus.plexus.resource.ResourceManager;
import org.codehaus.plexus.resource.loader.FileResourceCreationException;
import org.codehaus.plexus.resource.loader.ResourceNotFoundException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

public class SpotlessMavenPluginTest {
    @TempDir
    private Path projectDirectory;

    @Test
    void applyMojoFormatsJavaSourcesUsingBuiltInSteps() throws Exception {
        Path sourceFile = writeJavaSource("package demo;   \npublic class App { }   ");
        SpotlessApplyMojo mojo = configuredApplyMojo(javaFormatter(), project());

        mojo.execute();

        assertThat(Files.readString(sourceFile)).isEqualTo("package demo;\npublic class App { }\n");
    }

    @Test
    void checkMojoReportsFormattingViolationsWithoutChangingSources() throws Exception {
        Path sourceFile = writeJavaSource("package demo;   \npublic class App { }   ");
        MavenProject project = project();
        SpotlessCheckMojo checkMojo = configuredCheckMojo(javaFormatter(), project);

        assertThatThrownBy(checkMojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(sourceFile.getFileName().toString());
        assertThat(Files.readString(sourceFile)).isEqualTo("package demo;   \npublic class App { }   ");

        configuredApplyMojo(javaFormatter(), project).execute();
        assertThatCode(() -> configuredCheckMojo(javaFormatter(), project).execute()).doesNotThrowAnyException();
    }

    @Test
    void javaFormatterFactoryUsesMavenProjectSourceDirectories() {
        Java formatterFactory = javaFormatter();

        assertThat(formatterFactory.defaultIncludes(project()))
                .contains(javaFileMask("src", "main", "java"), javaFileMask("src", "test", "java"));
        assertThat(formatterFactory.licenseHeaderDelimiter())
                .contains("package", "import", "public", "class", "module");
    }

    @Test
    void formatterFactoryCreatesFormatterThatCanFormatSuppliedFiles() throws Exception {
        Path sourceFile = writeJavaSource("package demo;   \npublic class App { }   ");
        Java formatterFactory = javaFormatter();
        FormatterConfig config = formatterConfig();

        try (Formatter formatter = formatterFactory.newFormatter(
                () -> Collections.singletonList(sourceFile.toFile()), config)) {
            assertThat(formatter.isClean(sourceFile.toFile())).isFalse();
            formatter.applyTo(sourceFile.toFile());
            assertThat(formatter.isClean(sourceFile.toFile())).isTrue();
        }

        assertThat(Files.readString(sourceFile)).isEqualTo("package demo;\npublic class App { }\n");
    }

    @Test
    void applyMojoFormatsArbitraryFilesUsingGenericFormatReplaceRegex() throws Exception {
        Path documentDirectory = projectDirectory.resolve("docs");
        Files.createDirectories(documentDirectory);
        Path document = documentDirectory.resolve("status.txt");
        Files.writeString(document, "status: draft\n", StandardCharsets.UTF_8);
        Format textFormat = genericTextFormat(
                "docs/*.txt", replaceRegex("normalize-status", "status: draft", "status: ready"));

        SpotlessApplyMojo mojo = configuredApplyMojo(javaFormatter(), project(), Collections.singletonList(textFormat));
        mojo.execute();

        assertThat(Files.readString(document)).isEqualTo("status: ready\n");
    }

    private Path writeJavaSource(String content) throws IOException {
        Path sourceDirectory = projectDirectory.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Path sourceFile = sourceDirectory.resolve("App.java");
        Files.writeString(sourceFile, content, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private Java javaFormatter() {
        Java formatter = new Java();
        formatter.addTrimTrailingWhitespace(new TrimTrailingWhitespace());
        formatter.addEndWithNewline(new EndWithNewline());
        return formatter;
    }

    private Format genericTextFormat(String include, ReplaceRegex replaceRegex) throws ComponentConfigurationException {
        Format format = new Format();
        DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("format");
        DefaultPlexusConfiguration includes = new DefaultPlexusConfiguration("includes");
        includes.addChild("include", include);
        configuration.addChild(includes);

        BasicComponentConfigurator configurator = new BasicComponentConfigurator();
        configurator.configureComponent(
                format,
                configuration,
                new MapExpressionEvaluator(Collections.emptyMap(), projectDirectory.toFile()),
                testClassRealm(),
                (ConfigurationListener) null);
        format.addReplaceRegex(replaceRegex);
        return format;
    }

    private ReplaceRegex replaceRegex(String name, String searchRegex, String replacement)
            throws ComponentConfigurationException {
        ReplaceRegex replaceRegex = new ReplaceRegex();
        DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("replaceRegex");
        configuration.addChild("name", name);
        configuration.addChild("searchRegex", searchRegex);
        configuration.addChild("replacement", replacement);

        BasicComponentConfigurator configurator = new BasicComponentConfigurator();
        configurator.configureComponent(
                replaceRegex,
                configuration,
                new MapExpressionEvaluator(Collections.emptyMap(), projectDirectory.toFile()),
                (ClassRealm) null,
                (ConfigurationListener) null);
        return replaceRegex;
    }

    private ClassRealm testClassRealm() {
        return new ClassRealm(new ClassWorld(), "spotless-test", getClass().getClassLoader());
    }

    private String javaFileMask(String first, String second, String third) {
        return String.join(File.separator, first, second, third) + File.separator + "**" + File.separator + "*.java";
    }

    private MavenProject project() {
        MavenProject project = new MavenProject();
        project.setGroupId("example");
        project.setArtifactId("spotless-sample");
        project.setVersion("1");
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        project.setBuild(build());
        return project;
    }

    private Build build() {
        Build build = new Build();
        build.setDirectory("target");
        build.setSourceDirectory(projectDirectory.resolve("src/main/java").toString());
        build.setTestSourceDirectory(projectDirectory.resolve("src/test/java").toString());
        return build;
    }

    private FormatterConfig formatterConfig() throws IOException {
        Path buildDirectory = projectDirectory.resolve("target");
        Files.createDirectories(buildDirectory);
        return new FormatterConfig(
                projectDirectory.toFile(),
                StandardCharsets.UTF_8.name(),
                LineEnding.UNIX,
                Optional.empty(),
                new EmptyProvisioner(),
                new FileLocator(
                        new TestResourceManager(projectDirectory), projectDirectory.toFile(), buildDirectory.toFile()),
                Collections.emptyList(),
                Optional.empty());
    }

    private SpotlessApplyMojo configuredApplyMojo(Java javaFormatter, MavenProject project)
            throws ComponentConfigurationException, IOException {
        SpotlessApplyMojo mojo = new SpotlessApplyMojo();
        configureMojo(mojo, "apply", javaFormatter, project);
        return mojo;
    }

    private SpotlessApplyMojo configuredApplyMojo(
            Java javaFormatter, MavenProject project, List<Format> genericFormats)
            throws ComponentConfigurationException, IOException {
        SpotlessApplyMojo mojo = new SpotlessApplyMojo();
        configureMojo(mojo, "apply", javaFormatter, project, genericFormats);
        return mojo;
    }

    private SpotlessCheckMojo configuredCheckMojo(Java javaFormatter, MavenProject project)
            throws ComponentConfigurationException, IOException {
        SpotlessCheckMojo mojo = new SpotlessCheckMojo();
        configureMojo(mojo, "check", javaFormatter, project);
        return mojo;
    }

    private void configureMojo(Object mojo, String goal, Java javaFormatter, MavenProject project)
            throws ComponentConfigurationException, IOException {
        configureMojo(mojo, goal, javaFormatter, project, new ArrayList<>());
    }

    private void configureMojo(
            Object mojo, String goal, Java javaFormatter, MavenProject project, List<Format> genericFormats)
            throws ComponentConfigurationException, IOException {
        Path buildDirectory = projectDirectory.resolve(project.getBuild().getDirectory());
        Files.createDirectories(buildDirectory);

        Map<String, Object> values = new HashMap<>();
        values.put("project", project);
        values.put("baseDir", projectDirectory.toFile());
        values.put("buildDir", buildDirectory.toFile());
        values.put("javaFormatter", javaFormatter);
        values.put("lineEndings", LineEnding.UNIX);
        values.put("formats", genericFormats);
        values.put("repositories", new ArrayList<>());
        values.put("upToDateChecking", new UpToDateChecking());
        values.put("repositorySystem", new UnusedRepositorySystem());
        values.put("repositorySystemSession", new DefaultRepositorySystemSession());
        values.put("resourceManager", new TestResourceManager(projectDirectory));
        values.put("buildContext", new DefaultBuildContext());

        DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("configuration");
        configuration.addChild("project", "${project}");
        configuration.addChild("baseDir", "${baseDir}");
        configuration.addChild("buildDir", "${buildDir}");
        configuration.addChild("goal", goal);
        configuration.addChild("encoding", StandardCharsets.UTF_8.name());
        configuration.addChild("lineEndings", "${lineEndings}");
        configuration.addChild("java", "${javaFormatter}");
        configuration.addChild("formats", "${formats}");
        configuration.addChild("repositories", "${repositories}");
        configuration.addChild("upToDateChecking", "${upToDateChecking}");
        configuration.addChild("repositorySystem", "${repositorySystem}");
        configuration.addChild("repositorySystemSession", "${repositorySystemSession}");
        configuration.addChild("resourceManager", "${resourceManager}");
        configuration.addChild("buildContext", "${buildContext}");

        BasicComponentConfigurator configurator = new BasicComponentConfigurator();
        configurator.configureComponent(
                mojo, configuration, new MapExpressionEvaluator(values, projectDirectory.toFile()), (ClassRealm) null,
                (ConfigurationListener) null);
    }

    private static final class UnusedRepositorySystem implements RepositorySystem {
        @Override
        public VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request)
                throws VersionRangeResolutionException {
            throw unusedRepositorySystem();
        }

        @Override
        public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
                throws VersionResolutionException {
            throw unusedRepositorySystem();
        }

        @Override
        public ArtifactDescriptorResult readArtifactDescriptor(
                RepositorySystemSession session, ArtifactDescriptorRequest request)
                throws ArtifactDescriptorException {
            throw unusedRepositorySystem();
        }

        @Override
        public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
                throws DependencyCollectionException {
            throw unusedRepositorySystem();
        }

        @Override
        public DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)
                throws DependencyResolutionException {
            throw unusedRepositorySystem();
        }

        @Override
        public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
                throws ArtifactResolutionException {
            throw unusedRepositorySystem();
        }

        @Override
        public List<ArtifactResult> resolveArtifacts(
                RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
                throws ArtifactResolutionException {
            throw unusedRepositorySystem();
        }

        @Override
        public List<MetadataResult> resolveMetadata(
                RepositorySystemSession session, Collection<? extends MetadataRequest> requests) {
            throw unusedRepositorySystem();
        }

        @Override
        public InstallResult install(RepositorySystemSession session, InstallRequest request)
                throws InstallationException {
            throw unusedRepositorySystem();
        }

        @Override
        public DeployResult deploy(RepositorySystemSession session, DeployRequest request) throws DeploymentException {
            throw unusedRepositorySystem();
        }

        @Override
        public LocalRepositoryManager newLocalRepositoryManager(
                RepositorySystemSession session, LocalRepository localRepository) {
            throw unusedRepositorySystem();
        }

        @Override
        public SyncContext newSyncContext(RepositorySystemSession session, boolean shared) {
            throw unusedRepositorySystem();
        }

        @Override
        public List<RemoteRepository> newResolutionRepositories(
                RepositorySystemSession session, List<RemoteRepository> repositories) {
            return repositories;
        }

        @Override
        public RemoteRepository newDeploymentRepository(RepositorySystemSession session, RemoteRepository repository) {
            return repository;
        }

        private AssertionError unusedRepositorySystem() {
            return new AssertionError("No formatter step in this test should resolve Maven artifacts");
        }
    }

    private static final class EmptyProvisioner implements Provisioner {
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return Collections.emptySet();
        }
    }

    private static final class MapExpressionEvaluator implements ExpressionEvaluator {
        private final Map<String, Object> values;
        private final File baseDirectory;

        private MapExpressionEvaluator(Map<String, Object> values, File baseDirectory) {
            this.values = values;
            this.baseDirectory = baseDirectory;
        }

        @Override
        public Object evaluate(String expression) throws ExpressionEvaluationException {
            if (expression == null || !expression.startsWith("${") || !expression.endsWith("}")) {
                return expression;
            }
            String key = expression.substring(2, expression.length() - 1);
            if (!values.containsKey(key)) {
                throw new ExpressionEvaluationException("Unsupported test expression: " + expression);
            }
            return values.get(key);
        }

        @Override
        public File alignToBaseDirectory(File file) {
            if (file.isAbsolute()) {
                return file;
            }
            return new File(baseDirectory, file.getPath());
        }
    }

    private static final class TestResourceManager implements ResourceManager {
        private final List<File> searchPaths = new ArrayList<>();
        private File outputDirectory;

        private TestResourceManager(Path baseDirectory) {
            searchPaths.add(baseDirectory.toFile());
        }

        @Override
        public InputStream getResourceAsInputStream(String name) throws ResourceNotFoundException {
            try {
                return Files.newInputStream(getResourceAsFile(name).toPath());
            } catch (IOException ex) {
                throw new ResourceNotFoundException(name, ex);
            }
        }

        @Override
        public File getResourceAsFile(String name) throws ResourceNotFoundException {
            return resolveRequiredFile(name);
        }

        @Override
        public File getResourceAsFile(String name, String outputName)
                throws ResourceNotFoundException, FileResourceCreationException {
            File resource = resolveRequiredFile(name);
            if (outputName == null) {
                return resource;
            }
            File outputFile = new File(outputDirectory, outputName);
            try {
                Path parent = outputFile.toPath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(resource.toPath(), outputFile.toPath());
            } catch (IOException ex) {
                throw new FileResourceCreationException("Unable to copy resource " + name, ex);
            }
            return outputFile;
        }

        @Override
        public File resolveLocation(String name, String outputName) {
            try {
                return getResourceAsFile(name, outputName);
            } catch (ResourceNotFoundException | FileResourceCreationException ex) {
                return null;
            }
        }

        @Override
        public File resolveLocation(String name) {
            try {
                return getResourceAsFile(name);
            } catch (ResourceNotFoundException ex) {
                return null;
            }
        }

        @Override
        public void setOutputDirectory(File outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Override
        public void addSearchPath(String id, String searchPath) {
            if ("file".equals(id)) {
                searchPaths.add(new File(searchPath));
            }
        }

        @Override
        public PlexusResource getResource(String name) throws ResourceNotFoundException {
            return new FileBackedPlexusResource(resolveRequiredFile(name));
        }

        @Override
        public File getResourceAsFile(PlexusResource resource) throws FileResourceCreationException {
            try {
                return resource.getFile();
            } catch (IOException ex) {
                throw new FileResourceCreationException("Unable to access resource " + resource.getName(), ex);
            }
        }

        @Override
        public void createResourceAsFile(PlexusResource resource, File destination)
                throws FileResourceCreationException {
            try (InputStream input = resource.getInputStream();
                    OutputStream output = Files.newOutputStream(destination.toPath())) {
                input.transferTo(output);
            } catch (IOException ex) {
                throw new FileResourceCreationException("Unable to create resource " + destination, ex);
            }
        }

        private File resolveRequiredFile(String name) throws ResourceNotFoundException {
            File requestedFile = new File(name);
            if (requestedFile.isAbsolute() && requestedFile.isFile()) {
                return requestedFile;
            }
            for (File searchPath : searchPaths) {
                File candidate = new File(searchPath, name);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
            throw new ResourceNotFoundException(name);
        }
    }

    private static final class FileBackedPlexusResource implements PlexusResource {
        private final File file;

        private FileBackedPlexusResource(File file) {
            this.file = file;
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public URL getURL() throws IOException {
            return file.toURI().toURL();
        }

        @Override
        public URI getURI() {
            return file.toURI();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(file.toPath());
        }
    }
}
